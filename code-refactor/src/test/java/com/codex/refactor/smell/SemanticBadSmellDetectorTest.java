package com.codex.refactor.smell;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.history.ChangedSymbol;
import com.codex.refactor.history.HistoryAnalysis;
import com.codex.refactor.history.ShotgunSurgeryHistoryEvidence;
import com.codex.refactor.smell.detectors.AlternativeClassesWithDifferentInterfacesBadSmellDetector;
import com.codex.refactor.smell.detectors.CommentsBadSmellDetector;
import com.codex.refactor.smell.detectors.DataClassBadSmellDetector;
import com.codex.refactor.smell.detectors.DataClumpsBadSmellDetector;
import com.codex.refactor.smell.detectors.DuplicatedCodeBadSmellDetector;
import com.codex.refactor.smell.detectors.FeatureEnvyBadSmellDetector;
import com.codex.refactor.smell.detectors.GlobalDataBadSmellDetector;
import com.codex.refactor.smell.detectors.InsiderTradingBadSmellDetector;
import com.codex.refactor.smell.detectors.LargeClassBadSmellDetector;
import com.codex.refactor.smell.detectors.LazyElementBadSmellDetector;
import com.codex.refactor.smell.detectors.LongFunctionBadSmellDetector;
import com.codex.refactor.smell.detectors.LongParameterListBadSmellDetector;
import com.codex.refactor.smell.detectors.LoopsBadSmellDetector;
import com.codex.refactor.smell.detectors.MessageChainsBadSmellDetector;
import com.codex.refactor.smell.detectors.MiddleManBadSmellDetector;
import com.codex.refactor.smell.detectors.MutableDataBadSmellDetector;
import com.codex.refactor.smell.detectors.MysteriousNameBadSmellDetector;
import com.codex.refactor.smell.detectors.PrimitiveObsessionBadSmellDetector;
import com.codex.refactor.smell.detectors.RefusedBequestBadSmellDetector;
import com.codex.refactor.smell.detectors.RepeatedSwitchesBadSmellDetector;
import com.codex.refactor.smell.detectors.ShotgunSurgeryBadSmellDetector;
import com.codex.refactor.smell.detectors.SpeculativeGeneralityBadSmellDetector;
import com.codex.refactor.smell.detectors.TemporaryFieldBadSmellDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticBadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void duplicatedCodeReportsRenamedLocalStatementShape() throws Exception {
        List<SmellFinding> findings = detect(new DuplicatedCodeBadSmellDetector(), """
                class DuplicateShape {
                  int first(int value) {
                    int result = value + 1;
                    result = result * 2;
                    return result;
                  }
                  int second(int amount) {
                    int total = amount + 1;
                    total = total * 2;
                    return total;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("normalized_statement_shape", findings.getFirst().evidence().get("signal"));
    }

    @Test
    void duplicatedCodeDoesNotReportDifferentOperationsWithSameLength() throws Exception {
        List<SmellFinding> findings = detect(new DuplicatedCodeBadSmellDetector(), """
                class DifferentShape {
                  int first(int value) {
                    int result = value + 1;
                    result = result * 2;
                    return result;
                  }
                  int second(int amount) {
                    int total = amount - 1;
                    total = total / 2;
                    return total;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void loopsReportsScalarAccumulationSignal() throws Exception {
        List<SmellFinding> findings = detect(new LoopsBadSmellDetector(), """
                class LoopCase {
                  int sum(int[] values) {
                    int total = 0;
                    for (int value : values) {
                      total += value;
                    }
                    return total;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("scalar_accumulation_loop", findings.getFirst().evidence().get("signal"));
    }

    @Test
    void loopsDoNotReportStreamingControlLoop() throws Exception {
        List<SmellFinding> findings = detect(new LoopsBadSmellDetector(), """
                class ServerLoop {
                  void serve(Socket socket) {
                    while (socket.accept()) {
                      poll();
                    }
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void longFunctionReportsShortButComplexMethod() throws Exception {
        List<SmellFinding> findings = detect(new LongFunctionBadSmellDetector(), """
                class ComplexMethod {
                  int score(int value) {
                    int result = 0;
                    if (value > 1) result++;
                    if (value > 2) result++;
                    if (value > 3) result++;
                    if (value > 4) result++;
                    if (value > 5) result++;
                    if (value > 6) result++;
                    if (value > 7) result++;
                    if (value > 8) result++;
                    if (value > 9) result++;
                    return result;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertTrue(((List<?>) findings.getFirst().evidence().get("signals")).contains("high_cyclomatic_complexity"));
    }

    @Test
    void longParameterListReportsNamedDataGroupAndBooleanFlags() throws Exception {
        List<SmellFinding> findings = detect(new LongParameterListBadSmellDetector(), """
                class RangeExporter {
                  void export(int start, int end, String unit, boolean includeHeader, boolean dryRun) {}
                }
                """);

        assertEquals(1, findings.size());
        List<?> signals = (List<?>) findings.getFirst().evidence().get("signals");
        assertTrue(signals.contains("named_data_group"));
        assertTrue(signals.contains("boolean_flag_cluster"));
    }

    @Test
    void globalAndMutableDataReportStaticFinalMutableContainersButIgnoreConstants() throws Exception {
        SourceFileAnalysis analysis = analyze("""
                class Registry {
                  public static final int MAX_SIZE = 10;
                  public static final List<String> USERS = new ArrayList<>();
                }
                """);

        List<SmellFinding> globalFindings = new GlobalDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));
        List<SmellFinding> mutableFindings = new MutableDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertTrue(globalFindings.stream().noneMatch(finding -> "MAX_SIZE".equals(finding.location().get("symbol"))));
        assertTrue(globalFindings.stream().anyMatch(finding -> "USERS".equals(finding.location().get("symbol"))
                && "globally_reachable_mutable_container".equals(finding.evidence().get("signal"))));
        assertTrue(mutableFindings.stream().anyMatch(finding -> "USERS".equals(finding.location().get("symbol"))
                && ((List<?>) finding.evidence().get("signals")).contains("final_reference_to_mutable_container")));
    }

    @Test
    void mysteriousNameReportsGenericParameterAndLocalNames() throws Exception {
        List<SmellFinding> findings = detect(new MysteriousNameBadSmellDetector(), """
                class NamingCase {
                  int calculate(int value, int tmp) {
                    int data = value + tmp;
                    return data;
                  }
                }
                """);

        assertTrue(findings.stream().anyMatch(finding -> "parameter".equals(finding.evidence().get("kind"))
                && "tmp".equals(finding.location().get("symbol"))));
        assertTrue(findings.stream().anyMatch(finding -> "local_variable".equals(finding.evidence().get("kind"))
                && "data".equals(finding.location().get("symbol"))));
    }

    @Test
    void lazyElementReportsEmptyOrPlaceholderElementsButNotMeaningfulSmallMethod() throws Exception {
        List<SmellFinding> positiveFindings = detect(new LazyElementBadSmellDetector(), """
                class FuturePlaceholder {
                  void todo() {}
                }
                """);

        assertTrue(positiveFindings.stream()
                .anyMatch(finding -> ((List<?>) finding.evidence().get("signals")).contains("placeholder_named_type")));

        List<SmellFinding> negativeFindings = detect(new LazyElementBadSmellDetector(), """
                class TaxRate {
                  int amount;
                  int cents() { return amount; }
                }
                """);

        assertTrue(negativeFindings.isEmpty());
    }

    @Test
    void temporaryFieldReportsNarrowTemporaryStateButIgnoresDependencyFields() throws Exception {
        List<SmellFinding> positiveFindings = detect(new TemporaryFieldBadSmellDetector(), """
                class Calculator {
                  int temporaryTotal;
                  int total(int value) {
                    temporaryTotal = value + 1;
                    return temporaryTotal;
                  }
                }
                """);

        assertEquals(1, positiveFindings.size());
        assertTrue(((List<?>) positiveFindings.getFirst().evidence().get("signals")).contains("temporary_name"));

        List<SmellFinding> negativeFindings = detect(new TemporaryFieldBadSmellDetector(), """
                class Controller {
                  Service service;
                  int handle(int value) {
                    return service.handle(value);
                  }
                }
                """);

        assertTrue(negativeFindings.isEmpty());
    }

    @Test
    void largeClassReportsMultipleResponsibilityClustersWhenMethodCountIsHigh() throws Exception {
        List<SmellFinding> findings = detect(new LargeClassBadSmellDetector(), """
                class WorkflowGodClass {
                  void saveOrder() {}
                  void loadOrder() {}
                  void renderInvoice() {}
                  void formatReceipt() {}
                  void validateOrder() {}
                  void checkRules() {}
                  int calculateTax() { return 1; }
                  int totalPrice() { return 1; }
                  void sendEmail() {}
                  void notifyQueue() {}
                }
                """);

        assertEquals(1, findings.size());
        assertTrue(((List<?>) findings.getFirst().evidence().get("signals"))
                .contains("multiple_responsibility_clusters"));
    }

    @Test
    void largeClassReportsDisconnectedMethodFieldClusters() throws Exception {
        List<SmellFinding> findings = detect(new LargeClassBadSmellDetector(), """
                class OrderCoordinator {
                  int invoiceAmount;
                  int invoiceTax;
                  int paymentStatus;
                  String shippingStreet;
                  String shippingZip;
                  int deliveryStatus;
                  int invoiceTotal() { return invoiceAmount + invoiceTax; }
                  boolean paymentReady() { return paymentStatus > 0 && invoiceAmount > 0; }
                  void markInvoicePaid() { paymentStatus = 2; }
                  void adjustTax(int value) { invoiceTax = value; invoiceAmount = invoiceAmount + value; }
                  String shippingLabel() { return shippingStreet + shippingZip; }
                  boolean deliveryReady() { return deliveryStatus > 0 && shippingZip.length() > 0; }
                  void updateAddress(String street, String zip) { shippingStreet = street; shippingZip = zip; }
                  void markDelivered() { deliveryStatus = 2; }
                }
                """);

        assertEquals(1, findings.size());
        assertTrue(((List<?>) findings.getFirst().evidence().get("signals"))
                .contains("disconnected_method_field_clusters"));
        Map<?, ?> graph = (Map<?, ?>) findings.getFirst().evidence().get("method_field_graph");
        assertEquals(2, graph.get("extraction_cluster_count"));
    }

    @Test
    void shotgunSurgeryIgnoresCommonMethodNamesButReportsRepeatedChangeOperation() throws Exception {
        assertTrue(detect(new ShotgunSurgeryBadSmellDetector(), """
                class A { public String toString() { return "a"; } }
                class B { public String toString() { return "b"; } }
                class C { public String toString() { return "c"; } }
                """).isEmpty());

        List<SmellFinding> findings = detect(new ShotgunSurgeryBadSmellDetector(), """
                class A { void refreshCache() {} }
                class B { void reloadCache() {} }
                class C { void invalidateCache() {} }
                """);

        assertEquals(1, findings.size());
        assertEquals("medium", findings.getFirst().confidence());
    }

    @Test
    void shotgunSurgeryUsesHistoryConfirmedCoChangeEvidence() throws Exception {
        Path path = tempDir.resolve("A.java");
        Files.writeString(path, """
                class A {
                  void refreshCache() {}
                }
                """);
        SourceFileAnalysis analysis = new JavaSourceAnalyzer().analyze(path);

        ChangedSymbol a = new ChangedSymbol("A.java", "method", "A", "refreshCache", 0, 2, 2, "refresh_cache/0");
        ChangedSymbol b = new ChangedSymbol("B.java", "method", "B", "reloadCache", 0, 2, 2, "refresh_cache/0");
        ChangedSymbol c = new ChangedSymbol("C.java", "method", "C", "invalidateCache", 0, 2, 2, "refresh_cache/0");
        Map<String, ChangedSymbol> symbols = new LinkedHashMap<>();
        symbols.put(a.symbolKey(), a);
        symbols.put(b.symbolKey(), b);
        symbols.put(c.symbolKey(), c);
        Map<String, Integer> counts = Map.of(a.symbolKey(), 3, b.symbolKey(), 3, c.symbolKey(), 3);
        ShotgunSurgeryHistoryEvidence evidence = new ShotgunSurgeryHistoryEvidence(
                "refresh_cache/0",
                200,
                List.of("c1", "c2", "c3"),
                List.of("A", "B", "C"),
                symbols,
                counts
        );
        HistoryAnalysis history = new HistoryAnalysis(
                true,
                "ok",
                tempDir.toAbsolutePath().normalize(),
                3,
                1,
                Map.of("A.java", List.of(evidence)),
                List.of()
        );

        List<SmellFinding> findings = new ShotgunSurgeryBadSmellDetector()
                .detect(new SmellAnalysisContext(analysis, history));

        assertEquals(1, findings.size());
        assertEquals("high", findings.getFirst().confidence());
        assertEquals("history_confirmed", findings.getFirst().evidence().get("signal"));
    }

    @Test
    void featureEnvyRequiresDominantForeignRootOverOwnData() throws Exception {
        List<SmellFinding> findings = detect(new FeatureEnvyBadSmellDetector(), """
                class Report {
                  int own;
                  int build(Customer customer) {
                    return customer.profile.address.zip
                        + customer.profile.address.code
                        + customer.account.level
                        + customer.account.score;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("customer", findings.getFirst().evidence().get("foreign_root"));
    }

    @Test
    void featureEnvyDoesNotReportFormattingCollaborationRole() throws Exception {
        List<SmellFinding> findings = detect(new FeatureEnvyBadSmellDetector(), """
                class CustomerFormatter {
                  String format(Customer customer) {
                    return customer.firstName()
                        + customer.lastName()
                        + customer.email()
                        + customer.phone();
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void featureEnvyIgnoresStaticLikeClassAccess() throws Exception {
        List<SmellFinding> findings = detect(new FeatureEnvyBadSmellDetector(), """
                class ConfigUser {
                  int calculate() {
                    return Config.alpha
                        + Config.beta
                        + Config.gamma
                        + Config.delta;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void featureEnvyDoesNotReportWhenOwnDataIsEquallyImportant() throws Exception {
        List<SmellFinding> findings = detect(new FeatureEnvyBadSmellDetector(), """
                class AccountService {
                  int base;
                  int rate;
                  int limit;
                  int offset;
                  int score(Customer customer) {
                    return base
                        + rate
                        + limit
                        + offset
                        + customer.level
                        + customer.score
                        + customer.segment
                        + customer.status;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void insiderTradingReportsDeepAndMultiCollaboratorAccess() throws Exception {
        List<SmellFinding> findings = detect(new InsiderTradingBadSmellDetector(), """
                class FraudCheck {
                  int inspect(Order order, Customer customer) {
                    return order.customer.address.city.code
                        + order.customer.account.status
                        + customer.profile.manager.level
                        + customer.profile.manager.region;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertTrue((int) findings.getFirst().evidence().get("max_message_chain_depth") >= 4);
        assertEquals("multi_collaborator_intimate_access", findings.getFirst().evidence().get("signal"));
    }

    @Test
    void insiderTradingReportsInternalNamedCollaboratorAccess() throws Exception {
        List<SmellFinding> findings = detect(new InsiderTradingBadSmellDetector(), """
                class SessionRiskPolicy {
                  boolean risky(Session session) {
                    return session.account.internalFlags.admin
                        && session.account.privateProfile.highRisk;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("internal_named_collaborator_access", findings.getFirst().evidence().get("signal"));
        assertTrue((int) findings.getFirst().evidence().get("internal_selector_count") >= 1);
        assertEquals("high", findings.getFirst().confidence());
    }

    @Test
    void insiderTradingDoesNotReportMapperProjectionRole() throws Exception {
        List<SmellFinding> findings = detect(new InsiderTradingBadSmellDetector(), """
                class CustomerMapper {
                  CustomerDto map(Customer customer) {
                    return new CustomerDto(
                        customer.profile.address.city.code,
                        customer.profile.address.zip,
                        customer.account.status
                    );
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void insiderTradingDoesNotReportShallowMultiCollaboratorReads() throws Exception {
        List<SmellFinding> findings = detect(new InsiderTradingBadSmellDetector(), """
                class Summary {
                  String summarize(Order order, Customer customer, Product product) {
                    return order.id + ":" + customer.id + ":" + product.id;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void messageChainsReportObjectNavigationWithChainDetails() throws Exception {
        List<SmellFinding> findings = detect(new MessageChainsBadSmellDetector(), """
                class ChainCase {
                  int read(Order order) {
                    return order.customer.address.city.code;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("long_object_navigation_chain", findings.getFirst().evidence().get("signal"));
        assertTrue((int) findings.getFirst().evidence().get("max_chain_depth") >= 5);
        assertTrue(findings.getFirst().evidence().containsKey("chains"));
    }

    @Test
    void messageChainsIgnoreFluentApiChains() throws Exception {
        List<SmellFinding> findings = detect(new MessageChainsBadSmellDetector(), """
                import java.util.List;
                class StreamCase {
                  List<String> names(List<User> users) {
                    return users.stream()
                        .filter(user -> user.active)
                        .map(user -> user.name)
                        .toList();
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void messageChainsIgnoreStaticAndPackageChains() throws Exception {
        List<SmellFinding> findings = detect(new MessageChainsBadSmellDetector(), """
                class StaticCase {
                  int read() {
                    return java.time.LocalDate.now().getYear()
                        + Config.Database.Timeout.value;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void messageChainsReportRepeatedMediumDepthPrefix() throws Exception {
        List<SmellFinding> findings = detect(new MessageChainsBadSmellDetector(), """
                class RepeatedCase {
                  Object read(Order order) {
                    Object first = order.customer.address;
                    Object second = order.customer.address;
                    return order.customer.address;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_chain_prefix", findings.getFirst().evidence().get("signal"));
        assertEquals("order.customer.address", findings.getFirst().evidence().get("repeated_prefix"));
    }

    @Test
    void messageChainsReportRepeatedMediumDepthPrefixOnSameLine() throws Exception {
        List<SmellFinding> findings = detect(new MessageChainsBadSmellDetector(), """
                class SameLineRepeatedCase {
                  Object read(Order order) {
                    return choose(order.customer.address, order.customer.address, order.customer.address);
                  }
                  Object choose(Object first, Object second, Object third) { return first; }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_chain_prefix", findings.getFirst().evidence().get("signal"));
        assertTrue((int) findings.getFirst().evidence().get("repeated_prefix_occurrences") >= 3);
    }

    @Test
    void dataClumpsReportRepeatedSemanticParameterGroup() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                class RangeService {
                  void createRange(int start, int end, String unit) {}
                  void updateRange(int from, int to, String unit) {}
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_parameter_group", findings.getFirst().evidence().get("signal"));
        assertEquals("range", findings.getFirst().evidence().get("theme"));
        assertEquals("Range", findings.getFirst().evidence().get("suggested_object_name"));
    }

    @Test
    void dataClumpsReportRepeatedArgumentGroupAtCallSites() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                class RangePublisher {
                  void publish(Range range) {
                    save(range.start, range.end, range.unit);
                    audit(range.start, range.end, range.unit);
                  }
                  void save(int start, int end, String unit) {}
                  void audit(int start, int end, String unit) {}
                }
                """);

        assertTrue(findings.stream().anyMatch(finding ->
                "repeated_argument_group".equals(finding.evidence().get("signal"))
                        && "range".equals(finding.evidence().get("theme"))));
    }

    @Test
    void dataClumpsDoNotReportUnrelatedStringTriples() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                class LogService {
                  void log(String level, String message, String traceId) {}
                  void audit(String action, String actor, String ip) {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void dataClumpsDoNotReportInfrastructureDependencyGroups() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                import java.io.InputStream;
                import java.io.PrintStream;
                class Cli {
                  private final PrintStream out;
                  private final PrintStream err;
                  private final InputStream in;
                  Cli(PrintStream out, PrintStream err, InputStream in) {
                    this.out = out;
                    this.err = err;
                    this.in = in;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void dataClumpsReportRepeatedFieldGroup() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                class Customer {
                  String street;
                  String city;
                  String zip;
                }
                class Supplier {
                  String street;
                  String city;
                  String postalCode;
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_field_group", findings.getFirst().evidence().get("signal"));
        assertEquals("address", findings.getFirst().evidence().get("theme"));
    }

    @Test
    void dataClumpsCombineParameterAndFieldEvidence() throws Exception {
        List<SmellFinding> findings = detect(new DataClumpsBadSmellDetector(), """
                class Customer {
                  String street;
                  String city;
                  String zip;
                }
                class AddressService {
                  void updateAddress(String street, String city, String zip) {}
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("mixed_data_group", findings.getFirst().evidence().get("signal"));
        assertEquals("high", findings.getFirst().confidence());
    }

    @Test
    void primitiveObsessionReportsCodedPrimitiveBranching() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class OrderRules {
                  int status;
                  int score(String typeCode) {
                    switch (status) {
                      case 1: return 10;
                      default: break;
                    }
                    if ("VIP".equals(typeCode)) {
                      return 20;
                    }
                    return 0;
                  }
                }
                """);

        assertTrue(findings.stream()
                .anyMatch(finding -> "coded_primitive_branching".equals(finding.evidence().get("signal"))));
    }

    @Test
    void primitiveObsessionReportsLocalCodedPrimitiveBranching() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class StatusPolicy {
                  int score(Order order) {
                    String status = order.status;
                    if ("new".equals(status)) return 1;
                    if ("paid".equals(status)) return 2;
                    return 0;
                  }
                }
                """);

        assertTrue(findings.stream()
                .anyMatch(finding -> "coded_primitive_branching".equals(finding.evidence().get("signal"))
                        && ((List<?>) finding.evidence().get("primitive_names")).contains("status")));
    }

    @Test
    void primitiveObsessionReportsBooleanFlagParameters() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class Booking {
                  void book(String userId, boolean express, boolean notifyUser, boolean skipValidation) {}
                }
                """);

        assertTrue(findings.stream()
                .anyMatch(finding -> "boolean_flag_parameters".equals(finding.evidence().get("signal"))));
    }

    @Test
    void primitiveObsessionReportsDomainNamedPrimitiveCluster() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class AccountRecord {
                  String userId;
                  String tenantId;
                  String countryCode;
                  String email;
                }
                """);

        assertTrue(findings.stream()
                .anyMatch(finding -> "domain_named_primitive_cluster".equals(finding.evidence().get("signal"))));
    }

    @Test
    void primitiveObsessionReportsRepeatedDomainPrimitiveConcept() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class OrderRecord {
                  String orderStatus;
                }
                class OrderApi {
                  void create(String orderStatus) {}
                  void update(String orderStatus) {}
                }
                """);

        assertTrue(findings.stream()
                .anyMatch(finding -> "repeated_domain_primitive".equals(finding.evidence().get("signal"))));
    }

    @Test
    void primitiveObsessionDoesNotReportPlainMathParameters() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class MathOps {
                  int sum(int left, int right) {
                    if (left == 0) {
                      return right;
                    }
                    return left + right;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void primitiveObsessionDoesNotReportPlainTextPair() throws Exception {
        List<SmellFinding> findings = detect(new PrimitiveObsessionBadSmellDetector(), """
                class TextCase {
                  void update(String name, String description) {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void repeatedSwitchesReportSameSelectorWithCaseLabels() throws Exception {
        List<SmellFinding> findings = detect(new RepeatedSwitchesBadSmellDetector(), """
                class OrderRules {
                  int price(String status) {
                    switch (status) {
                      case "NEW": return 1;
                      case "PAID": return 2;
                      default: return 0;
                    }
                  }
                  String label(String status) {
                    switch (status) {
                      case "NEW": return "new";
                      case "PAID": return "paid";
                      default: return "unknown";
                    }
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_switch_selector", findings.getFirst().evidence().get("signal"));
        assertTrue(((List<?>) findings.getFirst().evidence().get("shared_labels")).contains("NEW"));
    }

    @Test
    void repeatedSwitchesReportEquivalentSelectorConceptWithSameLabels() throws Exception {
        List<SmellFinding> findings = detect(new RepeatedSwitchesBadSmellDetector(), """
                class OrderRules {
                  int price(Order order) {
                    switch (order.status) {
                      case NEW: return 1;
                      case PAID: return 2;
                      case CANCELLED: return 3;
                      default: return 0;
                    }
                  }
                  String label(String status) {
                    switch (status) {
                      case NEW: return "new";
                      case PAID: return "paid";
                      case CANCELLED: return "cancelled";
                      default: return "unknown";
                    }
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_switch_selector", findings.getFirst().evidence().get("signal"));
        assertTrue(((List<?>) findings.getFirst().evidence().get("selector_keys")).contains("status"));
    }

    @Test
    void repeatedSwitchesReportIfElseTypeCodeDispatch() throws Exception {
        List<SmellFinding> findings = detect(new RepeatedSwitchesBadSmellDetector(), """
                class OrderRules {
                  int price(String status) {
                    if ("NEW".equals(status)) {
                      return 1;
                    } else if ("PAID".equals(status)) {
                      return 2;
                    } else {
                      return 0;
                    }
                  }
                  String label(String status) {
                    if (status.equals("NEW")) {
                      return "new";
                    } else if (status.equals("PAID")) {
                      return "paid";
                    } else {
                      return "unknown";
                    }
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("repeated_switch_selector", findings.getFirst().evidence().get("signal"));
        assertEquals(true, findings.getFirst().evidence().get("includes_if_else_dispatch"));
    }

    @Test
    void repeatedSwitchesDoNotReportDifferentSelectorsAndLabels() throws Exception {
        List<SmellFinding> findings = detect(new RepeatedSwitchesBadSmellDetector(), """
                class UnrelatedSwitches {
                  int price(String status) {
                    switch (status) {
                      case "NEW": return 1;
                      default: return 0;
                    }
                  }
                  boolean allow(String role) {
                    switch (role) {
                      case "ADMIN": return true;
                      default: return false;
                    }
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void repeatedSwitchesDoNotReportSingleConditionIfs() throws Exception {
        List<SmellFinding> findings = detect(new RepeatedSwitchesBadSmellDetector(), """
                class SingleConditionCase {
                  int first(int value) {
                    if (value == 0) {
                      return 1;
                    }
                    return value;
                  }
                  int second(int score) {
                    if (score == 0) {
                      return 2;
                    }
                    return score;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void alternativeClassesReportSimilarShapeWithDifferentInterfaces() throws Exception {
        List<SmellFinding> findings = detect(new AlternativeClassesWithDifferentInterfacesBadSmellDetector(), """
                class FileSource {
                  String read(String id) { return id; }
                  void write(String id, String value) {}
                }
                class BlobStore {
                  String fetch(String id) { return id; }
                  void put(String id, String value) {}
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("similar_role_different_interface", findings.getFirst().evidence().get("signal"));
        assertTrue((double) findings.getFirst().evidence().get("method_role_similarity") >= 0.80);
    }

    @Test
    void alternativeClassesReportSynonymMethodRolesWithDifferentNames() throws Exception {
        List<SmellFinding> findings = detect(new AlternativeClassesWithDifferentInterfacesBadSmellDetector(), """
                class CacheRepository {
                  String load(String key) { return key; }
                  void store(String key, String value) {}
                  void remove(String key) {}
                }
                class RemoteStorage {
                  String fetch(String id) { return id; }
                  void put(String id, String payload) {}
                  void delete(String id) {}
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("high", findings.getFirst().confidence());
        assertTrue(((List<?>) findings.getFirst().evidence().get("method_matches")).size() >= 3);
    }

    @Test
    void alternativeClassesDoNotReportSameInterface() throws Exception {
        List<SmellFinding> findings = detect(new AlternativeClassesWithDifferentInterfacesBadSmellDetector(), """
                class FileSource {
                  String read(String id) { return id; }
                  void write(String id, String value) {}
                }
                class BlobSource {
                  String read(String id) { return id; }
                  void write(String id, String value) {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void alternativeClassesDoNotReportAccessorOnlyDataHolders() throws Exception {
        List<SmellFinding> findings = detect(new AlternativeClassesWithDifferentInterfacesBadSmellDetector(), """
                class CustomerRecord {
                  String name;
                  String getName() { return name; }
                  void setName(String value) { name = value; }
                }
                class AccountRecord {
                  String label;
                  String getLabel() { return label; }
                  void setLabel(String value) { label = value; }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void alternativeClassesDoNotReportSameShapeDifferentRoles() throws Exception {
        List<SmellFinding> findings = detect(new AlternativeClassesWithDifferentInterfacesBadSmellDetector(), """
                class EmailNotifier {
                  void send(String email, String body) {}
                  void cancel(String email) {}
                }
                class PriceCalculator {
                  int total(String sku, String region) { return 1; }
                  int count(String sku) { return 1; }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void speculativeGeneralityReportsUnusedSmallAbstraction() throws Exception {
        List<SmellFinding> findings = detect(new SpeculativeGeneralityBadSmellDetector(), """
                interface FutureExtension {
                  void extensionPoint();
                }
                class RealThing {
                  void work() {}
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("FutureExtension", findings.getFirst().location().get("symbol"));
    }

    @Test
    void speculativeGeneralityDoesNotReportAbstractionWithMultipleImplementers() throws Exception {
        List<SmellFinding> findings = detect(new SpeculativeGeneralityBadSmellDetector(), """
                interface PaymentMethod {
                  void pay();
                }
                class CardPayment implements PaymentMethod {
                  public void pay() {}
                }
                class WalletPayment implements PaymentMethod {
                  public void pay() {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void middleManReportsMostlyForwardingOwner() throws Exception {
        List<SmellFinding> findings = detect(new MiddleManBadSmellDetector(), """
                class ServiceFacade {
                  Delegate delegate;
                  int first() { return delegate.first(); }
                  int second() { return delegate.second(); }
                  int third() { return delegate.third(); }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("mostly_forwarding_class", findings.getFirst().evidence().get("signal"));
        assertEquals("delegate", findings.getFirst().evidence().get("dominant_delegate"));
        assertEquals("high", findings.getFirst().confidence());
    }

    @Test
    void middleManRecognizesThisQualifiedDelegateForwarding() throws Exception {
        List<SmellFinding> findings = detect(new MiddleManBadSmellDetector(), """
                class ServiceProxy {
                  Delegate delegate;
                  int first() { return this.delegate.first(); }
                  int second() { return this.delegate.second(); }
                  int third() { return this.delegate.third(); }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("delegate", findings.getFirst().evidence().get("dominant_delegate"));
    }

    @Test
    void middleManDoesNotReportFacadeWithCoordinationLogic() throws Exception {
        List<SmellFinding> findings = detect(new MiddleManBadSmellDetector(), """
                class CheckoutFacade {
                  Payment payment;
                  Audit audit;
                  Receipt pay(Order order) {
                    audit.record(order);
                    return payment.charge(order);
                  }
                  void refund(Order order) {
                    if (order.locked) {
                      return;
                    }
                    payment.refund(order.id);
                  }
                  int status(String id) { return payment.status(id); }
                  String label(String id) { return payment.label(id); }
                  boolean exists(String id) { return payment.exists(id); }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void middleManDoesNotReportParameterForwardingHelpers() throws Exception {
        List<SmellFinding> findings = detect(new MiddleManBadSmellDetector(), """
                class ForwardingHelper {
                  int first(Delegate delegate) { return delegate.first(); }
                  int second(Delegate delegate) { return delegate.second(); }
                  int third(Delegate delegate) { return delegate.third(); }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void middleManDoesNotReportAdapterWithTransformations() throws Exception {
        List<SmellFinding> findings = detect(new MiddleManBadSmellDetector(), """
                class CustomerAdapter {
                  Service service;
                  CustomerDto fetch(String id) { return toDto(service.fetch(id)); }
                  void save(CustomerDto dto) { service.save(toDomain(dto)); }
                  boolean exists(String id) { return service.exists(id); }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void dataClassReportsAccessorHeavyDataHolder() throws Exception {
        List<SmellFinding> findings = detect(new DataClassBadSmellDetector(), """
                class CustomerData {
                  String name;
                  String email;
                  int level;
                  String getName() { return name; }
                  void setName(String value) { name = value; }
                  String getEmail() { return email; }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals(0L, findings.getFirst().evidence().get("behavioral_method_count"));
    }

    @Test
    void dataClassDoesNotReportImmutableValueObject() throws Exception {
        List<SmellFinding> findings = detect(new DataClassBadSmellDetector(), """
                class Money {
                  final int amount;
                  final String currency;
                  Money(int amount, String currency) {
                    this.amount = amount;
                    this.currency = currency;
                  }
                  int amount() { return amount; }
                  String currency() { return currency; }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void refusedBequestReportsUnsupportedOrEmptySubclassBehavior() throws Exception {
        List<SmellFinding> findings = detect(new RefusedBequestBadSmellDetector(), """
                class Animal {
                  void fly() {}
                  Object wingSpan() { return null; }
                }
                class Dog extends Animal {
                  void fly() { throw new UnsupportedOperationException(); }
                  Object wingSpan() { return null; }
                }
                """);

        assertEquals(1, findings.size());
        assertTrue((int) findings.getFirst().evidence().get("rejected_method_count") >= 1);
    }

    @Test
    void refusedBequestRequiresRejectedInheritedContract() throws Exception {
        List<SmellFinding> findings = detect(new RefusedBequestBadSmellDetector(), """
                class BaseTask {
                  void run() {}
                }
                class WorkerTask extends BaseTask {
                  void run() { System.out.println("ok"); }
                  void debugOnly() { throw new UnsupportedOperationException(); }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void refusedBequestReportsInterfaceContractRejection() throws Exception {
        List<SmellFinding> findings = detect(new RefusedBequestBadSmellDetector(), """
                interface Exporter {
                  void export(String id);
                  String format(String id);
                }
                class NullExporter implements Exporter {
                  public void export(String id) { throw new UnsupportedOperationException("not supported"); }
                  public String format(String id) { return null; }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("rejected_inherited_contract", findings.getFirst().evidence().get("signal"));
        assertEquals(2, findings.getFirst().evidence().get("rejected_method_count"));
        assertEquals(2L, findings.getFirst().evidence().get("verified_override_count"));
    }

    @Test
    void refusedBequestReportsExplicitOverrideAgainstUnresolvedParent() throws Exception {
        List<SmellFinding> findings = detect(new RefusedBequestBadSmellDetector(), """
                class ExternalSinkAdapter extends ExternalSink {
                  @Override
                  void flush() { throw new UnsupportedOperationException("not supported"); }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("explicit_override_rejects_unresolved_contract", findings.getFirst().evidence().get("signal"));
        assertEquals(1L, findings.getFirst().evidence().get("unresolved_override_count"));
    }

    @Test
    void refusedBequestDoesNotReportEmptyHookOverride() throws Exception {
        List<SmellFinding> findings = detect(new RefusedBequestBadSmellDetector(), """
                class Workflow {
                  void afterSave() {}
                }
                class CustomerWorkflow extends Workflow {
                  void afterSave() {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void commentsReportDebtMarkerAndCommentedOutCode() throws Exception {
        List<SmellFinding> findings = detect(new CommentsBadSmellDetector(), """
                class CommentCase {
                  void work() {
                    // TODO: replace temporary branch
                    // if (legacyEnabled) { runLegacy(); }
                    System.out.println("ok");
                  }
                }
                """);

        List<String> signals = findings.stream()
                .map(finding -> (String) finding.evidence().get("signal"))
                .toList();
        assertTrue(signals.contains("debt_marker_comment"));
        assertTrue(signals.contains("commented_out_code"));
    }

    @Test
    void commentsReportStructureExplanationsInsideComplexMethod() throws Exception {
        List<SmellFinding> findings = detect(new CommentsBadSmellDetector(), """
                class ReportBuilder {
                  int build(int value) {
                    int result = 0;
                    // validate input
                    if (value < 0) {
                      result = 1;
                    }
                    // calculate score
                    for (int i = 0; i < value; i++) {
                      result += i;
                    }
                    // render output
                    if (result > 10) {
                      return result;
                    }
                    return 0;
                  }
                }
                """);

        assertEquals(1, findings.size());
        assertEquals("structure_explaining_comments", findings.getFirst().evidence().get("signal"));
        assertEquals(3, findings.getFirst().evidence().get("structure_comment_count"));
    }

    @Test
    void commentsDoNotReportExternalConstraintComment() throws Exception {
        List<SmellFinding> findings = detect(new CommentsBadSmellDetector(), """
                class TokenVerifier {
                  boolean verify(String token) {
                    // RFC 7515 requires constant-time comparison with the external identity provider.
                    return token != null;
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void commentsDoNotTreatNarrativeKeywordCommentsAsCommentedOutCode() throws Exception {
        List<SmellFinding> findings = detect(new CommentsBadSmellDetector(), """
                class ApprovalNotes {
                  /**
                   * For PROJECT targets: CREATE -> VALID/APPROVED, DROP -> DROPPED.
                   * Return the best display name: first non-blank element.
                   */
                  String displayName(String name) {
                    // if there is no protocol, the leading slash can be enabled.
                    return name == null ? "" : name;
                  }
                }
                """);

        assertTrue(findings.stream()
                .noneMatch(finding -> "commented_out_code".equals(finding.evidence().get("signal"))));
    }

    @Test
    void commentsDoNotReportShortMethodWithOneOrdinaryExplanation() throws Exception {
        List<SmellFinding> findings = detect(new CommentsBadSmellDetector(), """
                class CustomerSaver {
                  void save(Customer customer) {
                    // save customer
                    repository.save(customer);
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    private List<SmellFinding> detect(BadSmellDetector detector, String source) throws Exception {
        return detector.detect(new SmellAnalysisContext(analyze(source)));
    }

    private SourceFileAnalysis analyze(String source) throws Exception {
        Path path = tempDir.resolve("Sample.java");
        Files.writeString(path, source);
        SourceFileAnalysis analysis = new JavaSourceAnalyzer().analyze(path);
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());
        return analysis;
    }
}
