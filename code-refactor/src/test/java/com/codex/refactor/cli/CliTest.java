package com.codex.refactor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void helpReturnsSuccessAndDocumentsCommands() {
        CliRun run = run("--help");

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        assertTrue(run.stdout().contains("analyze-complexity"));
        assertTrue(run.stdout().contains("detect-smells"));
        assertTrue(run.stdout().contains("--history-analysis off|git"));
        assertTrue(!run.stdout().contains("--semantic-review"));
        assertEquals("", run.stderr());
    }

    @Test
    void missingCommandReturnsInvalidInvocation() {
        CliRun run = run();

        assertEquals(1, run.exitCode());
        assertTrue(run.stderr().contains("Missing command."));
    }

    @Test
    void complexityJsonReportsUnsupportedFileWithSchemaVersion() throws Exception {
        CliRun run = run(
                "analyze-complexity",
                "--json",
                "src/test/resources/fixtures/unsupported/sample.txt"
        );

        assertEquals(0, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("1.0", report.get("schema_version").asText());
        assertEquals("analyze-complexity", report.get("tool").asText());
        assertEquals("partial", report.get("status").asText());
        assertEquals(1, report.path("summary").path("files_total").asInt());
        assertEquals(0, report.path("summary").path("files_analyzed").asInt());
        assertEquals("unsupported_language", report.path("files").get(0).path("status").asText());
        assertEquals("unknown", report.path("files").get(0).path("language").asText());
        assertTrue(report.path("errors").isArray());
    }

    @Test
    void smellsAliasJsonReportsForcedJavaParseErrorWithoutFailing() throws Exception {
        CliRun run = run(
                "smells",
                "--json",
                "--language",
                "java",
                "src/test/resources/fixtures/unsupported/sample.txt"
        );

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("detect-smells", report.get("tool").asText());
        assertEquals("java", report.path("invocation").path("language").asText());
        assertEquals(0, report.path("summary").path("total_smells").asInt());
        assertEquals("parse_error", report.path("files").get(0).path("status").asText());
        assertTrue(report.path("files").get(0).path("smells").isArray());
    }

    @Test
    void failOnParseErrorReturnsNonZeroAfterWritingJsonReport() throws Exception {
        CliRun run = run(
                "detect-smells",
                "--json",
                "--language",
                "java",
                "--fail-on-parse-error",
                "src/test/resources/fixtures/unsupported/sample.txt"
        );

        assertEquals(3, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("partial", report.get("status").asText());
        assertEquals("parse_error", report.path("files").get(0).path("status").asText());
        assertEquals("", run.stderr());
    }

    @Test
    void jsonMissingPathReturnsErrorReportAndInvalidInvocationExitCode() throws Exception {
        CliRun run = run(
                "detect-smells",
                "--json",
                "src/test/resources/fixtures/unsupported/does-not-exist.java"
        );

        assertEquals(1, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("error", report.get("status").asText());
        assertTrue(report.path("errors").get(0).path("message").asText().contains("does not exist"));
        assertEquals("", run.stderr());
    }

    @Test
    void javaComplexityJsonReportsParserBackedMetrics() throws Exception {
        CliRun run = run(
                "analyze-complexity",
                "--json",
                "src/test/resources/fixtures/java/Simple.java"
        );

        assertEquals(0, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("ok", report.get("status").asText());
        assertEquals("java", report.path("files").get(0).path("language").asText());
        assertEquals("jdk-compiler", report.path("files").get(0).path("parser").asText());
        assertEquals(1, report.path("summary").path("functions_total").asInt());
        assertEquals(1, report.path("summary").path("classes_total").asInt());
    }

    @Test
    void smellReportDoesNotIncludeSemanticReview() throws Exception {
        Path source = tempDir.resolve("Divergent.java");
        Files.writeString(source, divergentSource());

        CliRun run = run("detect-smells", "--json", source.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = JSON.readTree(run.stdout());
        assertTrue(report.path("invocation").path("semantic_review").isMissingNode());
        JsonNode divergent = firstSmell(report, "divergent-change");
        assertTrue(divergent.isObject());
        assertTrue(divergent.path("semantic_review").isMissingNode());
    }

    @Test
    void planRefactorJsonBuildsRankedStepsFromSmellReport() throws Exception {
        Path smellReport = tempDir.resolve("smells.json");
        Files.writeString(smellReport, sampleSmellReport(
                tempDir.resolve("LongMethod.java").toString(),
                tempDir.resolve("RiskReport.java").toString()
        ));

        CliRun run = run("plan-refactor", "--json", "--max-findings", "1", smellReport.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("plan-refactor", report.path("tool").asText());
        assertEquals(2, report.path("summary").path("candidate_findings").asInt());
        assertEquals(1, report.path("summary").path("planned_findings").asInt());
        JsonNode step = report.path("plan").get(0);
        assertEquals("feature-envy", step.path("smell_id").asText());
        assertEquals("Move Function", step.path("primary_refactoring").path("name").asText());
        assertEquals("Move score calculation to Customer.", step.path("primary_refactoring").path("first_safe_step").asText());
        assertTrue(step.path("rerun_command").asText().contains("detect-smells"));
        assertTrue(step.path("rerun_command").asText().contains("RiskReport.java"));
    }

    @Test
    void planRefactorDefaultsToFileDiversePlan() throws Exception {
        Path smellReport = tempDir.resolve("multi-file-smells.json");
        Path alpha = tempDir.resolve("AlphaHotspot.java");
        Path beta = tempDir.resolve("BetaHotspot.java");
        Path gamma = tempDir.resolve("GammaHotspot.java");
        Files.writeString(smellReport, sampleMultiFileSmellReport(
                alpha.toString(),
                beta.toString(),
                gamma.toString()
        ));

        CliRun run = run(
                "plan-refactor",
                "--json",
                "--max-findings",
                "3",
                "--max-findings-per-file",
                "1",
                smellReport.toString()
        );

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("file", report.path("invocation").path("group_by").asText());
        assertEquals(3, report.path("summary").path("planned_findings").asInt());
        assertEquals(3, report.path("summary").path("planned_files").asInt());
        assertEquals(alpha.toString(), report.path("plan").get(0).path("file_path").asText());
        assertEquals(beta.toString(), report.path("plan").get(1).path("file_path").asText());
        assertEquals(gamma.toString(), report.path("plan").get(2).path("file_path").asText());
    }

    @Test
    void planRefactorCanUseFindingOrderWhenRequested() throws Exception {
        Path smellReport = tempDir.resolve("multi-file-smells.json");
        Path alpha = tempDir.resolve("AlphaHotspot.java");
        Path beta = tempDir.resolve("BetaHotspot.java");
        Path gamma = tempDir.resolve("GammaHotspot.java");
        Files.writeString(smellReport, sampleMultiFileSmellReport(
                alpha.toString(),
                beta.toString(),
                gamma.toString()
        ));

        CliRun run = run(
                "plan-refactor",
                "--json",
                "--group-by",
                "finding",
                "--max-findings",
                "2",
                smellReport.toString()
        );

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("finding", report.path("invocation").path("group_by").asText());
        assertEquals(2, report.path("summary").path("planned_findings").asInt());
        assertEquals(1, report.path("summary").path("planned_files").asInt());
        assertEquals(alpha.toString(), report.path("plan").get(0).path("file_path").asText());
        assertEquals(alpha.toString(), report.path("plan").get(1).path("file_path").asText());
    }

    @Test
    void planRefactorTextEmitsMarkdownPlan() throws Exception {
        Path smellReport = tempDir.resolve("smells.json");
        Files.writeString(smellReport, sampleSmellReport(
                tempDir.resolve("LongMethod.java").toString(),
                tempDir.resolve("RiskReport.java").toString()
        ));

        CliRun run = run("plan", smellReport.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        assertTrue(run.stdout().contains("# Refactoring Plan"));
        assertTrue(run.stdout().contains("Feature Envy -> Move Function"));
        assertTrue(run.stdout().contains("First safe step: Move score calculation to Customer."));
        assertEquals("", run.stderr());
    }

    @Test
    void semanticReviewOptionIsNoLongerSupported() {
        CliRun run = run(
                "detect-smells",
                "--semantic-review",
                "removed",
                "src/test/resources/fixtures/java/Simple.java"
        );

        assertEquals(1, run.exitCode());
        assertTrue(run.stderr().contains("Unknown option"));
        assertTrue(run.stderr().contains("--semantic-review"));
    }

    @Test
    void detectSmellsUsesProjectIndexForCrossFileRefusedBequest() throws Exception {
        Path animal = tempDir.resolve("Animal.java");
        Path dog = tempDir.resolve("Dog.java");
        Files.writeString(animal, """
                class Animal {
                  void fly() {}
                }
                """);
        Files.writeString(dog, """
                class Dog extends Animal {
                  void fly() { throw new UnsupportedOperationException(); }
                }
                """);

        CliRun run = run("detect-smells", "--json", animal.toString(), dog.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode dogFile = fileEndingWith(run.report(), "Dog.java");
        JsonNode finding = firstSmellInFile(dogFile, "refused-bequest");
        assertEquals("rejected_inherited_contract", finding.path("evidence").path("signal").asText());
        assertEquals(1, finding.path("evidence").path("verified_override_count").asInt());
    }

    @Test
    void detectSmellsUsesProjectIndexForCrossFileShotgunSurgery() throws Exception {
        Path billing = tempDir.resolve("BillingUpdater.java");
        Path shipping = tempDir.resolve("ShippingUpdater.java");
        Path reporting = tempDir.resolve("ReportingUpdater.java");
        Files.writeString(billing, """
                class BillingUpdater {
                  void updatePriceRules() { recalculateBilling(); }
                  void recalculateBilling() {}
                }
                """);
        Files.writeString(shipping, """
                class ShippingUpdater {
                  void updatePriceRules() { recalculateShipping(); }
                  void recalculateShipping() {}
                }
                """);
        Files.writeString(reporting, """
                class ReportingUpdater {
                  void updatePriceRules() { rebuildReport(); }
                  void rebuildReport() {}
                }
                """);

        CliRun run = run("detect-smells", "--json", billing.toString(), shipping.toString(), reporting.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode billingFile = fileEndingWith(run.report(), "BillingUpdater.java");
        JsonNode finding = firstSmellInFile(billingFile, "shotgun-surgery");
        assertEquals("update_price_rules/0", finding.path("evidence").path("change_key").asText());
        assertEquals(3, finding.path("evidence").path("owner_count").asInt());
        assertEquals(3, finding.path("evidence").path("file_count").asInt());
        assertEquals("updatePriceRules", finding.path("evidence").path("current_file_methods").get(0).asText());
    }

    @Test
    void detectSmellsUsesProjectIndexForResolvedFeatureEnvyTarget() throws Exception {
        Path customer = tempDir.resolve("Customer.java");
        Path report = tempDir.resolve("RiskReport.java");
        Files.writeString(customer, """
                class Customer {
                  int loyalty;
                  int overdue;
                  int risk;
                }
                """);
        Files.writeString(report, """
                class RiskReport {
                  int score(Customer customer) {
                    return customer.loyalty
                        + customer.overdue
                        + customer.risk;
                  }
                }
                """);

        CliRun run = run("detect-smells", "--json", customer.toString(), report.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode reportFile = fileEndingWith(run.report(), "RiskReport.java");
        JsonNode finding = firstSmellInFile(reportFile, "feature-envy");
        assertEquals("Customer", finding.path("evidence").path("resolved_foreign_type").asText());
        assertEquals(3, finding.path("evidence").path("matching_foreign_member_count").asInt());
        assertEquals("high", finding.path("confidence").asText());
    }

    @Test
    void detectSmellsCanFilterByMinimumConfidence() throws Exception {
        Path customer = tempDir.resolve("Customer.java");
        Path report = tempDir.resolve("RiskReport.java");
        Files.writeString(customer, """
                class Customer {
                  int loyalty;
                  int overdue;
                  int risk;
                }
                """);
        Files.writeString(report, """
                class RiskReport {
                  int score(Customer customer) {
                    return customer.loyalty
                        + customer.overdue
                        + customer.risk;
                  }
                }
                """);

        CliRun run = run(
                "detect-smells",
                "--json",
                "--min-confidence",
                "high",
                customer.toString(),
                report.toString()
        );

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        assertEquals("high", run.report().path("invocation").path("min_confidence").asText());
        JsonNode reportFile = fileEndingWith(run.report(), "RiskReport.java");
        assertTrue(containsSmell(reportFile, "feature-envy"));
        for (JsonNode file : run.report().path("files")) {
            for (JsonNode smell : file.path("smells")) {
                assertEquals("high", smell.path("confidence").asText());
            }
        }
    }

    @Test
    void smellLocationsIncludeLineAliasForLegacyConsumers() throws Exception {
        Path source = tempDir.resolve("LongMethod.java");
        StringBuilder code = new StringBuilder("""
                class LongMethod {
                  void work() {
                """);
        for (int index = 0; index < 60; index++) {
            code.append("    System.out.println(").append(index).append(");\n");
        }
        code.append("""
                  }
                }
                """);
        Files.writeString(source, code.toString());

        CliRun run = run("detect-smells", "--json", "--min-confidence", "high", source.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode finding = firstSmell(run.report(), "long-function");
        assertEquals(finding.path("location").path("start_line").asInt(),
                finding.path("location").path("line").asInt());
    }

    @Test
    void detectSmellsReportsPartitionedProjectIndexForLargeConfiguredBatch() throws Exception {
        String previous = System.getProperty("codeRefactor.smell.projectIndexBatchSize");
        System.setProperty("codeRefactor.smell.projectIndexBatchSize", "2");
        try {
            Files.writeString(tempDir.resolve("First.java"), "class First { void work() {} }\n");
            Files.writeString(tempDir.resolve("Second.java"), "class Second { void work() {} }\n");
            Files.writeString(tempDir.resolve("Third.java"), "class Third { void work() {} }\n");

            CliRun run = run("detect-smells", "--json", tempDir.toString());

            assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
            JsonNode scope = run.report().path("analysis_scope");
            assertEquals("partitioned", scope.path("project_index_mode").asText());
            assertEquals(2, scope.path("project_index_batch_size").asInt());
            assertEquals(2, scope.path("project_index_batch_count").asInt());
            assertEquals(3, run.report().path("summary").path("files_total").asInt());
        } finally {
            if (previous == null) {
                System.clearProperty("codeRefactor.smell.projectIndexBatchSize");
            } else {
                System.setProperty("codeRefactor.smell.projectIndexBatchSize", previous);
            }
        }
    }

    @Test
    void detectSmellsUsesCallGraphForResolvedMiddleManDelegate() throws Exception {
        Path service = tempDir.resolve("RealService.java");
        Path proxy = tempDir.resolve("ServiceProxy.java");
        Files.writeString(service, """
                class RealService {
                  String load(String id) { return id; }
                  void save(String id) {}
                  boolean delete(String id) { return true; }
                }
                """);
        Files.writeString(proxy, """
                class ServiceProxy {
                  private RealService service;
                  String load(String id) { return service.load(id); }
                  void save(String id) { service.save(id); }
                  boolean delete(String id) { return service.delete(id); }
                }
                """);

        CliRun run = run("detect-smells", "--json", service.toString(), proxy.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode proxyFile = fileEndingWith(run.report(), "ServiceProxy.java");
        JsonNode finding = firstSmellInFile(proxyFile, "middle-man");
        assertEquals("RealService", finding.path("evidence").path("resolved_delegate_type").asText());
        assertEquals(3, finding.path("evidence").path("resolved_forwarding_call_count").asInt());
        assertTrue(finding.path("evidence").path("resolved_forwarding_targets").toString().contains("RealService.load"));
        assertTrue(finding.path("recommended_refactorings").toString().contains("Remove Middle Man"));
        assertTrue(finding.path("related_symbols").toString().contains("RealService"));
    }

    @Test
    void detectSmellsUsesProjectIndexToAvoidSpeculativeGeneralityFalsePositive() throws Exception {
        Path abstraction = tempDir.resolve("FutureExtension.java");
        Path firstImplementation = tempDir.resolve("RealExtension.java");
        Path secondImplementation = tempDir.resolve("OtherExtension.java");
        Files.writeString(abstraction, """
                interface FutureExtension {
                  void extensionPoint();
                }
                """);
        Files.writeString(firstImplementation, """
                class RealExtension implements FutureExtension {
                  public void extensionPoint() {}
                }
                """);
        Files.writeString(secondImplementation, """
                class OtherExtension implements FutureExtension {
                  public void extensionPoint() {}
                }
                """);

        CliRun run = run(
                "detect-smells",
                "--json",
                abstraction.toString(),
                firstImplementation.toString(),
                secondImplementation.toString()
        );

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode abstractionFile = fileEndingWith(run.report(), "FutureExtension.java");
        assertTrue(!containsSmell(abstractionFile, "speculative-generality"));
    }

    @Test
    void directoryInputExpandsFilesAndAppliesDefaultExcludes() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Path excludedDir = tempDir.resolve("node_modules");
        Files.createDirectories(sourceDir);
        Files.createDirectories(excludedDir);
        Files.writeString(sourceDir.resolve("Simple.java"), """
                class Simple {
                  int sum(int left, int right) {
                    return left + right;
                  }
                }
                """);
        Files.writeString(excludedDir.resolve("Ignored.java"), "class Ignored {}");

        CliRun run = run("analyze-complexity", "--json", tempDir.toString());

        assertEquals(0, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals(1, report.path("summary").path("files_total").asInt());
        assertEquals(1, report.path("summary").path("files_analyzed").asInt());
        assertTrue(report.path("files").get(0).path("path").asText().endsWith("Simple.java"));
    }

    @Test
    void directoryInputDefaultsToSupportedSourceLanguages() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Path buildDir = tempDir.resolve(".jdtls-build/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(buildDir);
        Files.writeString(sourceDir.resolve("Simple.java"), """
                class Simple {
                  int sum(int left, int right) {
                    return left + right;
                  }
                }
                """);
        Files.writeString(tempDir.resolve("README.md"), "# docs\n");
        Files.writeString(tempDir.resolve(".classpath"), "<classpath />\n");
        Files.writeString(buildDir.resolve("Simple.class"), "compiled");

        CliRun run = run("detect-smells", "--json", tempDir.toString());

        assertEquals(0, run.exitCode(), () -> "stderr=" + run.stderr() + "\nstdout=" + run.stdout());
        JsonNode report = run.report();
        assertEquals("ok", report.path("status").asText());
        assertEquals(1, report.path("summary").path("files_total").asInt());
        assertEquals(1, report.path("summary").path("files_analyzed").asInt());
        assertTrue(report.path("files").get(0).path("path").asText().endsWith("Simple.java"));
    }

    @Test
    void unsupportedFormatReturnsInvalidInvocation() {
        CliRun run = run(
                "detect-smells",
                "--format",
                "yaml",
                "src/test/resources/fixtures/unsupported/sample.txt"
        );

        assertEquals(1, run.exitCode());
        assertTrue(run.stderr().contains("Unsupported output format"));
    }

    @Test
    void unsupportedHistoryAnalysisReturnsInvalidInvocation() {
        CliRun run = run(
                "detect-smells",
                "--history-analysis",
                "svn",
                "src/test/resources/fixtures/java/Simple.java"
        );

        assertEquals(1, run.exitCode());
        assertTrue(run.stderr().contains("Unsupported history analysis provider"));
    }

    private static CliRun run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Cli cli = new Cli(
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );
        int exitCode = cli.run(args);
        return new CliRun(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
        private JsonNode report() throws Exception {
            return JSON.readTree(stdout);
        }
    }

    private static JsonNode firstSmell(JsonNode report, String smellId) {
        for (JsonNode smell : report.path("files").get(0).path("smells")) {
            if (smellId.equals(smell.path("id").asText())) {
                return smell;
            }
        }
        return JSON.createObjectNode().missingNode();
    }

    private static JsonNode firstSmellInFile(JsonNode file, String smellId) {
        for (JsonNode smell : file.path("smells")) {
            if (smellId.equals(smell.path("id").asText())) {
                return smell;
            }
        }
        return JSON.createObjectNode().missingNode();
    }

    private static JsonNode fileEndingWith(JsonNode report, String suffix) {
        for (JsonNode file : report.path("files")) {
            if (file.path("path").asText().endsWith(suffix)) {
                return file;
            }
        }
        return JSON.createObjectNode().missingNode();
    }

    private static boolean containsSmell(JsonNode file, String smellId) {
        for (JsonNode smell : file.path("smells")) {
            if (smellId.equals(smell.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String divergentSource() {
        return """
                class OrderCoordinator {
                  private Repository repository;
                  private ViewRenderer viewRenderer;
                  private OrderValidator validator;
                  private PriceCalculator priceCalculator;

                  void saveOrder(Order order) { repository.save(order); }
                  String renderReceipt(Order order) { return viewRenderer.render(order); }
                  void validateOrder(Order order) { validator.check(order); }
                  Money calculateTotal(Order order) { return priceCalculator.calculate(order); }
                }
                """;
    }

    private static String sampleSmellReport(String longMethodPath, String riskReportPath) {
        return """
                {
                  "schema_version": "1.0",
                  "tool": "detect-smells",
                  "status": "ok",
                  "summary": {
                    "files_total": 2,
                    "files_analyzed": 2,
                    "total_smells": 2,
                    "critical": 0,
                    "high": 1,
                    "medium": 1,
                    "low": 0
                  },
                  "files": [
                    {
                      "path": "%s",
                      "language": "java",
                      "status": "ok",
                      "smells": [
                        {
                          "id": "long-function",
                          "type": "Long Function",
                          "book_chapter": "3.3",
                          "severity": "medium",
                          "confidence": "high",
                          "location": {"symbol": "render", "line": 10, "start_line": 10, "end_line": 70},
                          "evidence": {"metric": "physical_lines", "actual": 61, "threshold": 50},
                          "description": "Function is long.",
                          "suggestion": "Extract cohesive blocks.",
                          "recommended_refactorings": ["Extract Function"],
                          "recommended_refactoring_details": [
                            {"name": "Extract Function", "chapter": "6.1"}
                          ],
                          "recommended_refactoring_rationale": [
                            {
                              "name": "Extract Function",
                              "reason": "The method is long.",
                              "applies_when": "A cohesive block exists.",
                              "preconditions": ["Behavior is characterized."],
                              "first_safe_step": "Extract the validation block.",
                              "steps": ["Extract the validation block."],
                              "test_focus": ["Return values and branches."],
                              "risks": ["Changing evaluation order."]
                            }
                          ],
                          "related_symbols": ["render"]
                        }
                      ],
                      "parse_errors": [],
                      "warnings": []
                    },
                    {
                      "path": "%s",
                      "language": "java",
                      "status": "ok",
                      "smells": [
                        {
                          "id": "feature-envy",
                          "type": "Feature Envy",
                          "book_chapter": "3.9",
                          "severity": "high",
                          "confidence": "high",
                          "location": {"symbol": "score", "line": 4, "start_line": 4, "end_line": 9},
                          "evidence": {"resolved_foreign_type": "Customer", "matching_foreign_member_count": 3},
                          "description": "Method uses more Customer data than its own class.",
                          "suggestion": "Move behavior toward Customer.",
                          "recommended_refactorings": ["Move Function", "Extract Function"],
                          "recommended_refactoring_details": [
                            {"name": "Move Function", "chapter": "8.1"},
                            {"name": "Extract Function", "chapter": "6.1"}
                          ],
                          "recommended_refactoring_rationale": [
                            {
                              "name": "Move Function",
                              "reason": "The dominant data owner is Customer.",
                              "applies_when": "The target owner already has the data and behavior belongs there.",
                              "preconditions": ["Callers can still reach the behavior."],
                              "first_safe_step": "Move score calculation to Customer.",
                              "steps": ["Add Customer.score helper.", "Redirect the caller.", "Run focused tests."],
                              "test_focus": ["Risk score edge cases."],
                              "risks": ["Moving across a public API boundary."]
                            },
                            {
                              "name": "Extract Function",
                              "reason": "A smaller helper may make the move safer.",
                              "first_safe_step": "Extract the score expression."
                            }
                          ],
                          "related_symbols": ["score", "Customer"]
                        }
                      ],
                      "parse_errors": [],
                      "warnings": []
                    }
                  ],
                  "errors": []
                }
                """.formatted(longMethodPath, riskReportPath);
    }

    private static String sampleMultiFileSmellReport(String alphaPath, String betaPath, String gammaPath) {
        return """
                {
                  "schema_version": "1.0",
                  "tool": "detect-smells",
                  "status": "ok",
                  "summary": {"files_total": 3, "files_analyzed": 3, "total_smells": 4},
                  "files": [
                    {
                      "path": "%s",
                      "language": "java",
                      "status": "ok",
                      "smells": [
                        {
                          "id": "long-function",
                          "type": "Long Function",
                          "book_chapter": "3.3",
                          "severity": "high",
                          "confidence": "high",
                          "location": {"symbol": "first", "line": 10, "start_line": 10, "end_line": 80},
                          "evidence": {"metric": "physical_lines", "actual": 71},
                          "description": "Function is long.",
                          "suggestion": "Extract a cohesive block.",
                          "recommended_refactorings": ["Extract Function"]
                        },
                        {
                          "id": "message-chains",
                          "type": "Message Chains",
                          "book_chapter": "3.17",
                          "severity": "high",
                          "confidence": "high",
                          "location": {"symbol": "second", "line": 90, "start_line": 90, "end_line": 110},
                          "evidence": {"chain_depth": 5},
                          "description": "Message chain is deep.",
                          "suggestion": "Hide the delegate.",
                          "recommended_refactorings": ["Hide Delegate"]
                        }
                      ]
                    },
                    {
                      "path": "%s",
                      "language": "java",
                      "status": "ok",
                      "smells": [
                        {
                          "id": "large-class",
                          "type": "Large Class",
                          "book_chapter": "3.20",
                          "severity": "high",
                          "confidence": "high",
                          "location": {"symbol": "BetaHotspot", "line": 1, "start_line": 1, "end_line": 400},
                          "evidence": {"physical_lines": 400},
                          "description": "Class is large.",
                          "suggestion": "Extract a cohesive class.",
                          "recommended_refactorings": ["Extract Class"]
                        }
                      ]
                    },
                    {
                      "path": "%s",
                      "language": "java",
                      "status": "ok",
                      "smells": [
                        {
                          "id": "feature-envy",
                          "type": "Feature Envy",
                          "book_chapter": "3.9",
                          "severity": "high",
                          "confidence": "high",
                          "location": {"symbol": "score", "line": 20, "start_line": 20, "end_line": 35},
                          "evidence": {"resolved_foreign_type": "Customer"},
                          "description": "Method uses another owner heavily.",
                          "suggestion": "Move behavior toward the owner.",
                          "recommended_refactorings": ["Move Function"]
                        }
                      ]
                    }
                  ],
                  "errors": []
                }
                """.formatted(alphaPath, betaPath, gammaPath);
    }
}
