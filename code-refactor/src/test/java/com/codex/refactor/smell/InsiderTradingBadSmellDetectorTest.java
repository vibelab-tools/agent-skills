package com.codex.refactor.smell;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.history.HistoryAnalysis;
import com.codex.refactor.smell.detectors.InsiderTradingBadSmellDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsiderTradingBadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsBidirectionalIntimateAccessBetweenKnownProjectTypes() throws Exception {
        SourceFileAnalysis analysis = analyze("""
                class BillingPolicy {
                  int assess(Account account) {
                    return account.customer.profile.risk.currentScore
                        + account.customer.billingAddress.zone.code
                        + account.customer.segment.rank.value;
                  }
                }

                class Account {
                  int expose(BillingPolicy policy) {
                    return policy.rules.currentWindow.limit.daily
                        + policy.rules.currentWindow.threshold.absolute
                        + policy.rules.currentWindow.weight.current;
                  }
                }
                """);

        List<SmellFinding> findings = new InsiderTradingBadSmellDetector()
                .detect(new SmellAnalysisContext(
                        analysis,
                        HistoryAnalysis.off(),
                        SourceProjectIndex.from(List.of(analysis))));

        assertEquals(2, findings.size());
        SmellFinding finding = findings.stream()
                .filter(candidate -> "assess".equals(candidate.location().get("symbol")))
                .findFirst()
                .orElseThrow();
        assertEquals("bidirectional_intimate_collaborator_access", finding.evidence().get("signal"));
        assertEquals("high", finding.confidence());
        assertTrue((int) finding.evidence().get("known_project_type_count") >= 1);
        assertTrue((int) finding.evidence().get("reciprocal_access_count") >= 3);
    }

    private SourceFileAnalysis analyze(String source) throws Exception {
        Path path = tempDir.resolve("Sample.java");
        Files.writeString(path, source);
        SourceFileAnalysis analysis = new JavaSourceAnalyzer().analyze(path);
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());
        return analysis;
    }
}
