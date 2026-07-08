package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LazyElementBadSmellDetector extends BookBadSmellDetector {
    public LazyElementBadSmellDetector() {
        super(BadSmell.LAZY_ELEMENT);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        context.analysis().classes().stream()
                .map(LazyElementBadSmellDetector::classCandidate)
                .flatMap(java.util.Optional::stream)
                .forEach(candidate -> findings.add(DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.classInfo().name(),
                        candidate.classInfo().startLine(), candidate.classInfo().endLine(),
                        candidate.evidence(),
                        "Class is too small to justify its own abstraction yet.",
                        "Inline the class or wait until it carries meaningful behavior."
                )));
        context.analysis().methods().stream()
                .map(LazyElementBadSmellDetector::methodCandidate)
                .flatMap(java.util.Optional::stream)
                .forEach(candidate -> findings.add(DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.method().name(),
                        candidate.method().startLine(), candidate.method().endLine(),
                        candidate.evidence(),
                        "Method is a very small abstraction with no meaningful branching.",
                        "Inline the method unless its name adds important domain meaning."
                )));
        if (!findings.isEmpty()) {
            return findings;
        }
        if ("java".equals(context.analysis().language())
                && (!context.analysis().classes().isEmpty() || !context.analysis().methods().isEmpty())) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<ClassCandidate> classCandidate(JavaClassInfo classInfo) {
        if (classInfo.interfaceType() || classInfo.abstractType()) {
            return java.util.Optional.empty();
        }
        List<JavaMethodInfo> methods = classInfo.methods().stream()
                .filter(method -> !method.constructor())
                .toList();
        List<String> signals = new ArrayList<>();
        if (classInfo.fields().isEmpty() && methods.isEmpty() && classInfo.physicalLines() <= 8) {
            signals.add("empty_class");
        }
        if (placeholderName(classInfo.name()) && classInfo.physicalLines() <= 16 && methods.size() <= 1) {
            signals.add("placeholder_named_type");
        }
        boolean allForwardingOrAccessor = !methods.isEmpty()
                && methods.stream().allMatch(method -> method.accessorMethod() || method.simpleDelegation());
        if (classInfo.fields().size() <= 1 && methods.size() <= 2 && allForwardingOrAccessor) {
            signals.add("thin_wrapper_type");
        }
        if (classInfo.fields().isEmpty() && methods.size() == 1 && methods.getFirst().physicalLines() <= 3
                && genericElementName(classInfo.name())) {
            signals.add("stateless_single_method_type");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ClassCandidate(classInfo, signals, methods.size()));
    }

    private static java.util.Optional<MethodCandidate> methodCandidate(JavaMethodInfo method) {
        if (method.constructor() || method.accessorMethod()) {
            return java.util.Optional.empty();
        }
        List<String> signals = new ArrayList<>();
        String body = method.normalizedBody() == null ? "" : method.normalizedBody().toLowerCase(Locale.ROOT);
        if (emptyOrNoOpBody(body)) {
            signals.add("empty_or_noop_method");
        }
        if (placeholderName(method.name()) && method.physicalLines() <= 5 && method.cyclomaticComplexity() <= 1) {
            signals.add("placeholder_named_method");
        }
        if (method.simpleDelegation()
                && method.physicalLines() <= 4
                && method.delegations().stream().anyMatch(delegation -> delegation.passThroughRatio() >= 0.80)) {
            signals.add("thin_forwarding_method");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MethodCandidate(method, signals));
    }

    private static boolean emptyOrNoOpBody(String body) {
        String normalized = body.replaceAll("(?s)^.*?\\{", "")
                .replaceAll("\\}.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank()
                || normalized.matches("(?i)^(return\\s*;?|return\\s+null\\s*;?|return\\s+0\\s*;?|pass|todo\\(\\)\\s*;?)$");
    }

    private static boolean placeholderName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("placeholder")
                || lower.contains("future")
                || lower.contains("temporary")
                || lower.contains("stub")
                || lower.contains("dummy");
    }

    private static boolean genericElementName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("helper")
                || lower.endsWith("util")
                || lower.endsWith("utils")
                || lower.endsWith("wrapper")
                || lower.endsWith("manager");
    }

    private record ClassCandidate(JavaClassInfo classInfo, List<String> signals, int methodCount) {
        String severity() {
            return signals.contains("empty_class") || signals.contains("placeholder_named_type") ? "medium" : "low";
        }

        String confidence() {
            return signals.size() >= 2 || signals.contains("empty_class") ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "physical_lines", classInfo.physicalLines(),
                    "field_count", classInfo.fields().size(),
                    "method_count", methodCount
            );
        }
    }

    private record MethodCandidate(JavaMethodInfo method, List<String> signals) {
        String severity() {
            return signals.contains("empty_or_noop_method") ? "medium" : "low";
        }

        String confidence() {
            return signals.size() >= 2 || signals.contains("empty_or_noop_method") ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "physical_lines", method.physicalLines(),
                    "cyclomatic_complexity", method.cyclomaticComplexity(),
                    "simple_delegation", method.simpleDelegation()
            );
        }
    }
}
