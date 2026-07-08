package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MutableDataBadSmellDetector extends BookBadSmellDetector {
    public MutableDataBadSmellDetector() {
        super(BadSmell.MUTABLE_DATA);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().fields().stream()
                .map(MutableDataBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(candidate -> DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.field().name(),
                        candidate.field().startLine(), candidate.field().endLine(),
                        candidate.evidence(),
                        "Mutable field can be changed from multiple places.",
                        "Reduce mutation scope, encapsulate writes, or replace derived mutable state with queries."
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<MutableDataCandidate> candidate(JavaFieldInfo field) {
        boolean moduleGlobal = "<module>".equals(field.ownerClass());
        boolean mutableContainer = mutableContainerType(field.type());
        List<String> signals = new ArrayList<>();
        if (!field.finalField()) {
            signals.add("non_final_field");
        }
        if (field.assignedByMethods().size() >= 2) {
            signals.add("multiple_writers");
        }
        if (field.publicField() && !field.finalField()) {
            signals.add(moduleGlobal ? "module_level_mutable_state" : "public_mutable_field");
        }
        if (field.finalField() && field.publicField() && mutableContainer) {
            signals.add("final_reference_to_mutable_container");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!signals.contains("multiple_writers")
                && !signals.contains("public_mutable_field")
                && !signals.contains("module_level_mutable_state")
                && !signals.contains("final_reference_to_mutable_container")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MutableDataCandidate(field, signals, mutableContainer));
    }

    private static boolean mutableContainerType(String type) {
        String lower = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return lower.contains("list")
                || lower.contains("map")
                || lower.contains("set")
                || lower.contains("dict")
                || lower.contains("array")
                || lower.contains("collection")
                || lower.contains("vector")
                || lower.contains("queue")
                || lower.contains("stack");
    }

    private record MutableDataCandidate(JavaFieldInfo field, List<String> signals, boolean mutableContainer) {
        String severity() {
            return signals.contains("multiple_writers") || signals.contains("module_level_mutable_state") ? "high" : "medium";
        }

        String confidence() {
            return signals.size() >= 2 || signals.contains("multiple_writers") ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "assigned_by_methods", field.assignedByMethods(),
                    "read_by_methods", field.readByMethods(),
                    "public", field.publicField(),
                    "static", field.staticField(),
                    "final", field.finalField(),
                    "field_type", field.type(),
                    "mutable_container_type", mutableContainer
            );
        }
    }
}
