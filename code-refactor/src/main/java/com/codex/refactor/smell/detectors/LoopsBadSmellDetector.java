package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LoopsBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> STREAMING_OR_EVENT_TOKENS = Set.of(
            "readline", "read_line", "accept", "poll", "sleep", "await", "select", "cursor"
    );

    public LoopsBadSmellDetector() {
        super(BadSmell.LOOPS);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        boolean hasStructuredLoops = context.analysis().methods().stream()
                .anyMatch(method -> !method.loopLines().isEmpty());
        List<SmellFinding> findings = context.analysis().methods().stream()
                .filter(method -> !method.loopLines().isEmpty())
                .filter(method -> !streamingOrEventLoop(method))
                .map(method -> finding(loopCandidate(method)))
                .toList();
        if (hasStructuredLoops) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(LoopCandidate candidate) {
        JavaMethodInfo method = candidate.method();
        return DetectorSupport.finding(
                smell(),
                candidate.severity(),
                candidate.confidence(),
                method.name(),
                method.startLine(),
                method.endLine(),
                candidate.evidence(),
                candidate.description(),
                candidate.suggestion()
        );
    }

    private static LoopCandidate loopCandidate(JavaMethodInfo method) {
        String body = method.normalizedBody();
        String lower = body.toLowerCase(Locale.ROOT);
        String signal;
        if (mutatesResultCollection(lower)) {
            signal = "collection_transformation_loop";
        } else if (accumulatesScalar(lower)) {
            signal = "scalar_accumulation_loop";
        } else if (searchesWithEarlyExit(lower)) {
            signal = "search_loop";
        } else if (method.loopLines().size() >= 2) {
            signal = "multiple_explicit_loops";
        } else {
            signal = "explicit_loop";
        }
        return new LoopCandidate(method, signal);
    }

    private static boolean mutatesResultCollection(String lowerBody) {
        return lowerBody.matches("(?s).*\\.(add|append|push|offer|put)\\s*\\(.*")
                || lowerBody.contains(" << ");
    }

    private static boolean accumulatesScalar(String lowerBody) {
        return lowerBody.matches("(?s).*\\b[a-z_$][a-z0-9_$]*\\s*\\+=\\s*.*")
                || lowerBody.matches("(?s).*\\b[a-z_$][a-z0-9_$]*\\s*=\\s*\\b[a-z_$][a-z0-9_$]*\\s*[+*]\\s*.*");
    }

    private static boolean searchesWithEarlyExit(String lowerBody) {
        return lowerBody.contains("if") && (lowerBody.contains("return") || lowerBody.contains("break"));
    }

    private static boolean streamingOrEventLoop(JavaMethodInfo method) {
        String lower = method.normalizedBody().toLowerCase(Locale.ROOT);
        return STREAMING_OR_EVENT_TOKENS.stream().anyMatch(lower::contains)
                && (lower.contains("while") || lower.contains("for"));
    }

    private record LoopCandidate(JavaMethodInfo method, String signal) {
        String severity() {
            return method.loopLines().size() >= 2 || "multiple_explicit_loops".equals(signal) ? "medium" : "low";
        }

        String confidence() {
            return "explicit_loop".equals(signal) ? "medium" : "high";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", signal,
                    "loop_lines", method.loopLines(),
                    "loop_count", method.loopLines().size(),
                    "cyclomatic_complexity", method.cyclomaticComplexity(),
                    "max_nesting_depth", method.maxNestingDepth()
            );
        }

        String description() {
            return switch (signal) {
                case "collection_transformation_loop" ->
                        "Loop builds or mutates a result collection and may hide collection transformation intent.";
                case "scalar_accumulation_loop" ->
                        "Loop accumulates a scalar result and may be clearer as a named reduction.";
                case "search_loop" ->
                        "Loop searches with an early exit and may be clearer as a named query or pipeline operation.";
                case "multiple_explicit_loops" ->
                        "Method contains multiple explicit loops, which can obscure separate iteration concerns.";
                default ->
                        "Method contains an explicit loop that may obscure iteration intent.";
            };
        }

        String suggestion() {
            return switch (signal) {
                case "collection_transformation_loop" ->
                        "Consider Replace Loop with Pipeline or Extract Function around the transformation.";
                case "scalar_accumulation_loop" ->
                        "Consider a named reduction, query method, or Split Loop if several concerns are mixed.";
                case "search_loop" ->
                        "Consider an intention-revealing query such as any/find/first where the language supports it.";
                default ->
                        "Consider Split Loop or Replace Loop with Pipeline when it keeps behavior clear.";
            };
        }
    }
}
