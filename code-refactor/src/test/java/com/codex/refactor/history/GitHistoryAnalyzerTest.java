package com.codex.refactor.history;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.TreeSitterSourceAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHistoryAnalyzerTest {
    @TempDir
    Path repositoryRoot;

    @Test
    void buildsShotgunSurgeryEvidenceFromRepeatedGitCoChanges() throws Exception {
        Files.writeString(repositoryRoot.resolve("A.java"), source("A", "refreshCache"));
        GitHistoryAnalyzer analyzer = new GitHistoryAnalyzer(
                new FakeGit(repositoryRoot),
                new JavaSourceAnalyzer(),
                new TreeSitterSourceAnalyzer()
        );

        HistoryAnalysis analysis = analyzer.analyze(
                List.of(repositoryRoot.resolve("A.java")),
                new HistoryOptions("git", 10, 3, 3)
        );

        List<ShotgunSurgeryHistoryEvidence> evidence = analysis.shotgunSurgeryFor(repositoryRoot.resolve("A.java"));
        assertEquals("ok", analysis.status());
        assertEquals(1, evidence.size());
        assertEquals("refresh_cache/0", evidence.getFirst().changeKey());
        assertEquals(3, evidence.getFirst().coChangeCommits());
        assertEquals(3, evidence.getFirst().ownerCount());
        assertTrue(evidence.getFirst().owners().containsAll(List.of("A", "B", "C")));
    }

    private static String source(String className, String methodName) {
        return """
                class %s {
                  void %s() {
                    int value = 1;
                  }
                }
                """.formatted(className, methodName);
    }

    private static String diff() {
        return """
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -2 +2 @@
                +  void refreshCache() {
                diff --git a/B.java b/B.java
                --- a/B.java
                +++ b/B.java
                @@ -2 +2 @@
                +  void reloadCache() {
                diff --git a/C.java b/C.java
                --- a/C.java
                +++ b/C.java
                @@ -2 +2 @@
                +  void invalidateCache() {
                """;
    }

    private static final class FakeGit implements GitHistoryAnalyzer.GitCommandRunner {
        private final Path repositoryRoot;

        private FakeGit(Path repositoryRoot) {
            this.repositoryRoot = repositoryRoot;
        }

        @Override
        public GitHistoryAnalyzer.CommandResult run(Path workingDirectory, List<String> arguments) throws IOException {
            if (arguments.equals(List.of("rev-parse", "--show-toplevel"))) {
                return ok(repositoryRoot.toString());
            }
            if (arguments.size() == 5 && arguments.subList(0, 3).equals(List.of("log", "--no-merges", "--format=%H"))) {
                return ok("c1\nc2\nc3\n");
            }
            if (arguments.size() == 5
                    && arguments.get(0).equals("show")
                    && arguments.get(1).equals("--format=")
                    && arguments.get(2).equals("--unified=0")
                    && arguments.get(3).equals("--find-renames")) {
                return ok(diff());
            }
            if (arguments.equals(List.of("show", arguments.getLast()))) {
                return fileAtCommit(arguments.getLast());
            }
            return new GitHistoryAnalyzer.CommandResult(1, "", "unexpected command " + arguments);
        }

        private GitHistoryAnalyzer.CommandResult fileAtCommit(String spec) {
            String path = spec.substring(spec.indexOf(':') + 1);
            return switch (path) {
                case "A.java" -> ok(source("A", "refreshCache"));
                case "B.java" -> ok(source("B", "reloadCache"));
                case "C.java" -> ok(source("C", "invalidateCache"));
                default -> new GitHistoryAnalyzer.CommandResult(1, "", "missing " + path);
            };
        }

        private static GitHistoryAnalyzer.CommandResult ok(String stdout) {
            return new GitHistoryAnalyzer.CommandResult(0, stdout, "");
        }
    }
}
