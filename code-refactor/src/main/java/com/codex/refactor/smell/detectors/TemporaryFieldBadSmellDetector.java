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

public final class TemporaryFieldBadSmellDetector extends BookBadSmellDetector {
    private static final List<String> TEMPORARY_NAME_TOKENS = List.of(
            "temp", "tmp", "temporary", "current", "scratch", "intermediate", "pending", "working", "buffer"
    );
    private static final List<String> BENIGN_STATE_TOKENS = List.of(
            "logger", "log", "repository", "repo", "client", "service", "dao", "mapper", "factory", "config"
    );

    public TemporaryFieldBadSmellDetector() {
        super(BadSmell.TEMPORARY_FIELD);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().fields().stream()
                .map(TemporaryFieldBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(field -> DetectorSupport.finding(
                        smell(), field.severity(), field.confidence(), field.field().name(), field.field().startLine(), field.field().endLine(),
                        field.evidence(),
                        "Field appears to be meaningful only during a narrow part of the object's lifecycle.",
                        "Move the temporary state into a smaller object or local calculation."
                ))
                .toList();
        return findings;
    }

    private static java.util.Optional<TemporaryFieldCandidate> candidate(JavaFieldInfo field) {
        if (field.finalField() || benignStateName(field.name()) || benignStateName(field.type())) {
            return java.util.Optional.empty();
        }
        int readCount = field.readByMethods().size();
        int writeCount = field.assignedByMethods().size();
        int participantCount = readCount + writeCount;
        List<String> signals = new ArrayList<>();
        if (!temporaryName(field.name()) || participantCount > 2) {
            return java.util.Optional.empty();
        }
        signals.add("temporary_name");
        if (writeCount == 1 && readCount <= 1) {
            signals.add("single_calculation_phase");
        } else if (writeCount == 0 && readCount <= 1) {
            signals.add("sparse_temporary_state");
        }
        if (field.publicField()) {
            signals.add("exposed_temporary_state");
        }
        return java.util.Optional.of(new TemporaryFieldCandidate(field, signals, readCount, writeCount));
    }

    private static boolean temporaryName(String name) {
        String tokenized = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(tokenized.split("[^a-z0-9]+"))
                .anyMatch(TEMPORARY_NAME_TOKENS::contains);
    }

    private static boolean benignStateName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return BENIGN_STATE_TOKENS.stream().anyMatch(lower::contains);
    }

    private record TemporaryFieldCandidate(JavaFieldInfo field, List<String> signals, int readCount, int writeCount) {
        String severity() {
            return signals.contains("temporary_name") && signals.contains("single_calculation_phase") ? "high" : "medium";
        }

        String confidence() {
            return signals.contains("temporary_name") || signals.size() >= 2 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "read_by_methods", field.readByMethods(),
                    "assigned_by_methods", field.assignedByMethods(),
                    "read_method_count", readCount,
                    "write_method_count", writeCount,
                    "field_type", field.type(),
                    "public", field.publicField()
            );
        }
    }
}
