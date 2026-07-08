package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LongFunctionBadSmellDetector extends BookBadSmellDetector {
    private static final int WARNING_LINES = 50;
    private static final int WARNING_CYCLOMATIC = 10;
    private static final int WARNING_COGNITIVE = 15;
    private static final int WARNING_NESTING = 4;

    public LongFunctionBadSmellDetector() {
        super(BadSmell.LONG_FUNCTION);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().methods().stream()
                .filter(method -> !method.constructor())
                .map(LongFunctionBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(candidate -> DetectorSupport.finding(
                        smell(),
                        candidate.severity(),
                        candidate.confidence(),
                        candidate.method().name(),
                        candidate.method().startLine(),
                        candidate.method().endLine(),
                        candidate.evidence(),
                        "Method is long or complex enough to hide multiple responsibilities.",
                        "Extract cohesive chunks into named methods, reduce nesting with guard clauses, and rerun the smell detector."
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<LongFunctionCandidate> candidate(JavaMethodInfo method) {
        List<String> signals = new ArrayList<>();
        if (method.physicalLines() > WARNING_LINES) {
            signals.add("too_many_physical_lines");
        }
        if (method.cyclomaticComplexity() >= WARNING_CYCLOMATIC) {
            signals.add("high_cyclomatic_complexity");
        }
        if (method.cognitiveComplexity() >= WARNING_COGNITIVE) {
            signals.add("high_cognitive_complexity");
        }
        if (method.maxNestingDepth() >= WARNING_NESTING) {
            signals.add("deep_nesting");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new LongFunctionCandidate(method, signals));
    }

    private record LongFunctionCandidate(JavaMethodInfo method, List<String> signals) {
        String severity() {
            return signals.size() >= 2
                    || method.physicalLines() > WARNING_LINES * 2
                    || method.cyclomaticComplexity() >= WARNING_CYCLOMATIC + 5
                    ? "high"
                    : "medium";
        }

        String confidence() {
            return signals.contains("too_many_physical_lines") || signals.size() >= 2 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "physical_lines", method.physicalLines(),
                    "physical_lines_threshold", WARNING_LINES,
                    "cyclomatic_complexity", method.cyclomaticComplexity(),
                    "cyclomatic_complexity_threshold", WARNING_CYCLOMATIC,
                    "cognitive_complexity", method.cognitiveComplexity(),
                    "cognitive_complexity_threshold", WARNING_COGNITIVE,
                    "max_nesting_depth", method.maxNestingDepth(),
                    "max_nesting_depth_threshold", WARNING_NESTING,
                    "parameter_count", method.parameterNames().size(),
                    "local_variable_count", method.localVariables().size(),
                    "loop_count", method.loopLines().size(),
                    "branch_dispatch_count", method.branchDispatches().size(),
                    "switch_selector_count", method.switchSelectors().size()
            );
        }
    }
}
