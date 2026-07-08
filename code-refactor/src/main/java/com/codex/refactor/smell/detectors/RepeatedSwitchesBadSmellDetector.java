package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.BranchDispatchInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RepeatedSwitchesBadSmellDetector extends BookBadSmellDetector {
    public RepeatedSwitchesBadSmellDetector() {
        super(BadSmell.REPEATED_SWITCHES);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        List<DispatchCandidate> dispatches = context.analysis().methods().stream()
                .flatMap(method -> dispatches(method).stream())
                .toList();
        Set<String> reportedMethodSets = new LinkedHashSet<>();

        Map<String, List<DispatchCandidate>> bySelector = dispatches.stream()
                .filter(dispatch -> !dispatch.selectorKey().isBlank())
                .collect(Collectors.groupingBy(
                        DispatchCandidate::selectorKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        bySelector.forEach((selectorKey, group) -> {
            List<DispatchCandidate> uniqueMethods = uniqueMethods(group);
            if (uniqueMethods.size() >= 2) {
                reportedMethodSets.add(methodSetKey(uniqueMethods));
                findings.add(finding(
                        "repeated_switch_selector",
                        selectorKey,
                        uniqueMethods,
                        "The same branch selector is dispatched in more than one method."
                ));
            }
        });

        Map<String, List<DispatchCandidate>> byLabelSet = dispatches.stream()
                .filter(DispatchCandidate::hasReportableLabelSet)
                .collect(Collectors.groupingBy(
                        DispatchCandidate::labelSignature,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        byLabelSet.forEach((labelSignature, group) -> {
            List<DispatchCandidate> uniqueMethods = uniqueMethods(group);
            if (uniqueMethods.size() >= 2 && !reportedMethodSets.contains(methodSetKey(uniqueMethods))) {
                findings.add(finding(
                        "repeated_branch_label_set",
                        labelSignature,
                        uniqueMethods,
                        "The same set of branch labels is repeated across different dispatch sites."
                ));
            }
        });
        if (!dispatches.isEmpty()) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(
            String signal,
            String groupKey,
            List<DispatchCandidate> dispatches,
            String description
    ) {
        DispatchCandidate first = dispatches.getFirst();
        Set<String> commonLabels = commonLabels(dispatches);
        boolean includesIfElse = dispatches.stream().anyMatch(dispatch -> dispatch.kind().contains("if_else"));
        boolean highConfidence = !commonLabels.isEmpty() || dispatches.stream().allMatch(DispatchCandidate::structured);
        return DetectorSupport.finding(
                smell(),
                dispatches.size() >= 3 || commonLabels.size() >= 3 ? "high" : "medium",
                highConfidence ? "high" : "medium",
                first.methodSymbol(),
                first.startLine(),
                first.endLine(),
                DetectorSupport.evidence(
                        "signal", signal,
                        "group_key", groupKey,
                        "dispatch_count", dispatches.size(),
                        "methods", dispatches.stream().map(DispatchCandidate::methodSymbol).toList(),
                        "switch_selectors", dispatches.stream().map(DispatchCandidate::selector).distinct().toList(),
                        "selector_keys", dispatches.stream().map(DispatchCandidate::selectorKey).distinct().toList(),
                        "shared_labels", List.copyOf(commonLabels),
                        "includes_if_else_dispatch", includesIfElse,
                        "dispatches", dispatches.stream().map(DispatchCandidate::toJson).toList()
                ),
                description,
                "Consider Replace Conditional with Polymorphism, optionally after Replace Type Code with Subclasses."
        );
    }

    private static List<DispatchCandidate> dispatches(JavaMethodInfo method) {
        if (!method.branchDispatches().isEmpty()) {
            return method.branchDispatches().stream()
                    .map(dispatch -> DispatchCandidate.from(method, dispatch))
                    .toList();
        }
        return method.switchSelectors().stream()
                .map(selector -> DispatchCandidate.from(method, new BranchDispatchInfo(
                        "legacy_switch",
                        selector,
                        List.of(),
                        false,
                        method.startLine(),
                        method.endLine()
                )))
                .toList();
    }

    private static List<DispatchCandidate> uniqueMethods(List<DispatchCandidate> dispatches) {
        Map<String, DispatchCandidate> byMethod = new LinkedHashMap<>();
        dispatches.stream()
                .sorted(Comparator.comparingInt(DispatchCandidate::startLine))
                .forEach(dispatch -> byMethod.putIfAbsent(dispatch.methodSymbol(), dispatch));
        return List.copyOf(byMethod.values());
    }

    private static Set<String> commonLabels(List<DispatchCandidate> dispatches) {
        List<Set<String>> labelSets = dispatches.stream()
                .map(DispatchCandidate::labels)
                .filter(labels -> !labels.isEmpty())
                .toList();
        if (labelSets.size() < 2) {
            return Set.of();
        }
        LinkedHashSet<String> common = new LinkedHashSet<>(labelSets.getFirst());
        labelSets.stream().skip(1).forEach(common::retainAll);
        return common;
    }

    private static String methodSetKey(List<DispatchCandidate> dispatches) {
        return dispatches.stream()
                .map(DispatchCandidate::methodSymbol)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private record DispatchCandidate(
            String kind,
            String methodSymbol,
            String selector,
            String selectorKey,
            Set<String> labels,
            boolean hasDefault,
            int startLine,
            int endLine
    ) {
        static DispatchCandidate from(JavaMethodInfo method, BranchDispatchInfo dispatch) {
            Set<String> labels = dispatch.labels().stream()
                    .map(RepeatedSwitchesBadSmellDetector::normalizeLabel)
                    .filter(label -> !label.isBlank())
                    .filter(label -> !"default".equalsIgnoreCase(label))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new DispatchCandidate(
                    dispatch.kind(),
                    method.ownerClass() + "." + method.name(),
                    dispatch.selector(),
                    RepeatedSwitchesBadSmellDetector.selectorKey(dispatch.selector()),
                    labels,
                    dispatch.hasDefault(),
                    dispatch.startLine(),
                    dispatch.endLine()
            );
        }

        boolean structured() {
            return !"legacy_switch".equals(kind);
        }

        boolean hasReportableLabelSet() {
            return labels.size() >= 3
                    || (labels.size() >= 2 && labels.stream().anyMatch(RepeatedSwitchesBadSmellDetector::domainLikeLabel));
        }

        String labelSignature() {
            return labels.stream().sorted().collect(Collectors.joining("|"));
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "kind", kind,
                    "method", methodSymbol,
                    "selector", selector,
                    "selector_key", selectorKey,
                    "labels", List.copyOf(labels),
                    "has_default", hasDefault,
                    "start_line", startLine,
                    "end_line", endLine
            );
        }
    }

    private static String selectorKey(String selector) {
        if (selector == null || selector.isBlank()) {
            return "";
        }
        String normalized = selector.replaceAll("\\s+", "")
                .replaceFirst("^this\\.", "")
                .replaceFirst("^self\\.", "");
        if (normalized.endsWith("()")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        int delimiter = Math.max(
                Math.max(normalized.lastIndexOf('.'), normalized.lastIndexOf("->")),
                normalized.lastIndexOf("::")
        );
        if (delimiter >= 0 && delimiter + 1 < normalized.length()) {
            normalized = normalized.substring(delimiter + (normalized.charAt(delimiter) == '-' ? 2 : normalized.startsWith("::", delimiter) ? 2 : 1));
        }
        if (normalized.matches("get[A-Z].*")) {
            normalized = Character.toLowerCase(normalized.charAt(3)) + normalized.substring(4);
        }
        return normalized.toLowerCase();
    }

    private static String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.trim().toUpperCase();
    }

    private static boolean domainLikeLabel(String label) {
        return label.matches("[A-Z][A-Z0-9_]{2,}") && !Set.of("TRUE", "FALSE", "NULL").contains(label);
    }
}
