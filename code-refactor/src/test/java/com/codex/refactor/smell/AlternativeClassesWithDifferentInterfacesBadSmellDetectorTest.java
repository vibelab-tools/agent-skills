package com.codex.refactor.smell;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.history.HistoryAnalysis;
import com.codex.refactor.smell.detectors.AlternativeClassesWithDifferentInterfacesBadSmellDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeClassesWithDifferentInterfacesBadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsCrossFileAlternativesWithSharedProjectUsageRoles() throws Exception {
        SourceFileAnalysis legacy = analyze("LegacyCustomerCache.java", """
                class LegacyCustomerCache {
                  String loadCustomer(String id) { return id; }
                  void storeCustomer(String id, String value) {}
                }
                """);
        SourceFileAnalysis remote = analyze("RemoteCustomerStore.java", """
                class RemoteCustomerStore {
                  String fetchCustomer(String id) { return id; }
                  void putCustomer(String id, String payload) {}
                }
                """);
        SourceFileAnalysis caller = analyze("CustomerImporter.java", """
                class CustomerImporter {
                  void sync(LegacyCustomerCache legacy, RemoteCustomerStore remote, String id, String value) {
                    legacy.loadCustomer(id);
                    remote.fetchCustomer(id);
                    legacy.storeCustomer(id, value);
                    remote.putCustomer(id, value);
                  }
                }
                """);
        SourceProjectIndex projectIndex = SourceProjectIndex.from(List.of(legacy, remote, caller));

        List<SmellFinding> findings = new AlternativeClassesWithDifferentInterfacesBadSmellDetector()
                .detect(new SmellAnalysisContext(legacy, HistoryAnalysis.off(), projectIndex));

        assertEquals(1, findings.size());
        SmellFinding finding = findings.getFirst();
        assertEquals("similar_role_different_interface", finding.evidence().get("signal"));
        assertTrue((double) finding.evidence().get("project_usage_similarity") >= 0.75);
        assertTrue(((List<?>) finding.evidence().get("shared_caller_owners")).contains("CustomerImporter"));
    }

    private SourceFileAnalysis analyze(String fileName, String source) throws Exception {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, source);
        SourceFileAnalysis analysis = new JavaSourceAnalyzer().analyze(path);
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());
        return analysis;
    }
}
