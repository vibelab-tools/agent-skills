package com.codex.refactor.smell;

import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.TreeSitterSourceAnalyzer;
import com.codex.refactor.smell.detectors.DuplicatedCodeBadSmellDetector;
import com.codex.refactor.smell.detectors.GlobalDataBadSmellDetector;
import com.codex.refactor.smell.detectors.MiddleManBadSmellDetector;
import com.codex.refactor.smell.detectors.MutableDataBadSmellDetector;
import com.codex.refactor.smell.detectors.RefusedBequestBadSmellDetector;
import com.codex.refactor.smell.detectors.RepeatedSwitchesBadSmellDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterBadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void csharpMiddleManUsesStructuredDelegationEvidence() throws Exception {
        SourceFileAnalysis analysis = analyzeCSharp("""
                class ServiceFacade {
                  Delegate delegateField;
                  public int First(int id) { return delegateField.First(id); }
                  public int Second(int id) { return delegateField.Second(id); }
                  public int Third(int id) { return delegateField.Third(id); }
                }
                """);

        List<SmellFinding> findings = new MiddleManBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertEquals(1, findings.size());
        assertEquals("mostly_forwarding_class", findings.getFirst().evidence().get("signal"));
        assertEquals("delegateField", findings.getFirst().evidence().get("dominant_delegate"));
    }

    @Test
    void csharpRefusedBequestUsesResolvedInheritanceContract() throws Exception {
        SourceFileAnalysis analysis = analyzeCSharp("""
                class Animal {
                  public virtual void Fly() {}
                }
                class Dog : Animal {
                  public override void Fly() { throw new NotImplementedException(); }
                }
                """);

        List<SmellFinding> findings = new RefusedBequestBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertEquals(1, findings.size());
        assertEquals("rejected_inherited_contract", findings.getFirst().evidence().get("signal"));
        assertEquals(1L, findings.getFirst().evidence().get("verified_override_count"));
    }

    @Test
    void csharpGlobalAndMutableDataUseFieldModifiersAndWriteEvidence() throws Exception {
        SourceFileAnalysis analysis = analyzeCSharp("""
                class Counters {
                  public static int Counter;
                  public void First() { Counter = 1; }
                  public void Second() { Counter = 2; }
                }
                """);

        List<SmellFinding> globalFindings = new GlobalDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));
        List<SmellFinding> mutableFindings = new MutableDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertFalse(globalFindings.isEmpty());
        assertTrue(globalFindings.stream().anyMatch(finding -> "Counter".equals(finding.location().get("symbol"))));
        assertFalse(mutableFindings.isEmpty());
        assertTrue(mutableFindings.stream()
                .anyMatch(finding -> ((Collection<?>) finding.evidence().get("assigned_by_methods")).contains("First")
                        && ((Collection<?>) finding.evidence().get("assigned_by_methods")).contains("Second")));
    }

    @Test
    void javascriptGlobalAndMutableDataUseModuleLevelDeclarations() throws Exception {
        Path path = tempDir.resolve("state.js");
        Files.writeString(path, """
                let cache = 0;
                const stable = 1;
                function first(value) { cache = value + stable; }
                function second(value) { cache = cache + value; }
                """);
        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(path, "javascript");
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());

        List<SmellFinding> globalFindings = new GlobalDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));
        List<SmellFinding> mutableFindings = new MutableDataBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertTrue(globalFindings.stream().anyMatch(finding -> "cache".equals(finding.location().get("symbol"))));
        assertTrue(globalFindings.stream().noneMatch(finding -> "stable".equals(finding.location().get("symbol"))));
        assertTrue(mutableFindings.stream()
                .anyMatch(finding -> "cache".equals(finding.location().get("symbol"))
                        && ((Collection<?>) finding.evidence().get("assigned_by_methods")).contains("first")
                        && ((Collection<?>) finding.evidence().get("assigned_by_methods")).contains("second")));
    }

    @Test
    void csharpDuplicatedCodeUsesNormalizedStatementShape() throws Exception {
        SourceFileAnalysis analysis = analyzeCSharp("""
                class DuplicateShape {
                  public int First(int value) {
                    int result = value + 1;
                    result = result * 2;
                    return result;
                  }
                  public int Second(int amount) {
                    int total = amount + 1;
                    total = total * 2;
                    return total;
                  }
                }
                """);

        List<SmellFinding> findings = new DuplicatedCodeBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertEquals(1, findings.size());
        assertEquals("normalized_statement_shape", findings.getFirst().evidence().get("signal"));
    }

    @Test
    void javascriptRepeatedSwitchesUsesIfElseDispatchEvidence() throws Exception {
        Path path = tempDir.resolve("dispatch.js");
        Files.writeString(path, """
                function label(status) {
                  if (status === "new") return "N";
                  else if (status === "paid") return "P";
                  else if (status === "cancelled") return "C";
                  return "?";
                }
                function color(status) {
                  if (status === "new") return "blue";
                  else if (status === "paid") return "green";
                  else if (status === "cancelled") return "red";
                  return "gray";
                }
                """);
        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(path, "javascript");
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());

        List<SmellFinding> findings = new RepeatedSwitchesBadSmellDetector().detect(new SmellAnalysisContext(analysis));

        assertEquals(1, findings.size());
        assertEquals("repeated_switch_selector", findings.getFirst().evidence().get("signal"));
        assertEquals(true, findings.getFirst().evidence().get("includes_if_else_dispatch"));
        assertTrue(((Collection<?>) findings.getFirst().evidence().get("shared_labels")).contains("NEW"));
    }

    private SourceFileAnalysis analyzeCSharp(String source) throws Exception {
        Path path = tempDir.resolve("Sample.cs");
        Files.writeString(path, source);
        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(path, "csharp");
        assertTrue(analysis.parseErrors().isEmpty(), () -> analysis.parseErrors().toString());
        return analysis;
    }
}
