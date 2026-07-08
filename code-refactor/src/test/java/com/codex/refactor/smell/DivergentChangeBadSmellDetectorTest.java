package com.codex.refactor.smell;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.history.HistoryAnalysis;
import com.codex.refactor.smell.detectors.DivergentChangeBadSmellDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivergentChangeBadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsOwnerWithSeveralEvidenceBackedChangeConcerns() throws Exception {
        List<SmellFinding> findings = detect("""
                class OrderCoordinator {
                  private Repository repository;
                  private ViewRenderer viewRenderer;
                  private OrderValidator validator;
                  private PriceCalculator priceCalculator;

                  void saveOrder(Order order) {
                    repository.save(order);
                  }

                  String renderReceipt(Order order) {
                    return viewRenderer.render(order);
                  }

                  void validateOrder(Order order) {
                    validator.check(order);
                  }

                  Money calculateTotal(Order order) {
                    return priceCalculator.calculate(order);
                  }
                }
                """);

        assertEquals(1, findings.size());
        SmellFinding finding = findings.getFirst();
        assertEquals(BadSmell.DIVERGENT_CHANGE, finding.smell());
        assertEquals("high", finding.confidence());

        Map<?, ?> concernMethods = (Map<?, ?>) finding.evidence().get("concern_methods");
        assertTrue(concernMethods.containsKey("persistence"));
        assertTrue(concernMethods.containsKey("presentation"));
        assertTrue(concernMethods.containsKey("validation"));
        assertTrue(concernMethods.containsKey("calculation"));
    }

    @Test
    void ignoresNameOnlyStubsWithoutIndependentConcernEvidence() throws Exception {
        List<SmellFinding> findings = detect("""
                class NameOnlyCoordinator {
                  void saveRecord() {}
                  void renderView() {}
                  void validateInput() {}
                  void calculateTotal() {}
                }
                """);

        assertTrue(findings.isEmpty());
    }

    @Test
    void reportsOwnerWithSeveralTypedCollaboratorConcernClusters() throws Exception {
        SourceFileAnalysis analysis = analyze("""
                class OrderWorkflow {
                  private OrderRepository a;
                  private ReceiptRenderer b;
                  private RuleValidator c;
                  private PriceCalculator d;

                  void first(Order order) {
                    a.store(order);
                  }

                  String second(Order order) {
                    return b.render(order);
                  }

                  void third(Order order) {
                    c.check(order);
                  }

                  Money fourth(Order order) {
                    return d.compute(order);
                  }
                }

                class OrderRepository { void store(Order order) {} }
                class ReceiptRenderer { String render(Order order) { return ""; } }
                class RuleValidator { void check(Order order) {} }
                class PriceCalculator { Money compute(Order order) { return null; } }
                """);

        List<SmellFinding> findings = new DivergentChangeBadSmellDetector()
                .detect(new SmellAnalysisContext(
                        analysis,
                        HistoryAnalysis.off(),
                        SourceProjectIndex.from(List.of(analysis))));

        assertEquals(1, findings.size());
        Map<?, ?> collaboratorClusters = (Map<?, ?>) findings.getFirst().evidence().get("collaborator_clusters");
        assertTrue(collaboratorClusters.containsKey("OrderRepository"));
        assertTrue(collaboratorClusters.containsKey("ReceiptRenderer"));
        assertTrue(collaboratorClusters.containsKey("RuleValidator"));
        assertTrue(collaboratorClusters.containsKey("PriceCalculator"));

        Map<?, ?> concernSignals = (Map<?, ?>) findings.getFirst().evidence().get("concern_signals");
        assertTrue(concernSignals.toString().contains("target_owner:OrderRepository"));
        assertTrue(concernSignals.toString().contains("target_method:render"));
    }

    @Test
    void ignoresCohesiveOwnerEvenWhenItHasSeveralPersistenceMethods() throws Exception {
        List<SmellFinding> findings = detect("""
                class OrderRepository {
                  private Database database;

                  void saveOrder(Order order) {
                    database.insert(order);
                  }

                  Order loadOrder(String id) {
                    return database.select(id);
                  }

                  void deleteOrder(String id) {
                    database.delete(id);
                  }
                }
                """);

        assertTrue(findings.isEmpty());
    }

    private List<SmellFinding> detect(String source) throws Exception {
        SourceFileAnalysis analysis = analyze(source);
        return new DivergentChangeBadSmellDetector().detect(new SmellAnalysisContext(analysis));
    }

    private SourceFileAnalysis analyze(String source) throws Exception {
        Path path = tempDir.resolve("Sample.java");
        Files.writeString(path, source);
        SourceFileAnalysis analysis = new JavaSourceAnalyzer().analyze(path);
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());
        return analysis;
    }
}
