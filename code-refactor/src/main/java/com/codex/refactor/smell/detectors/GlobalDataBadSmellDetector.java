package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GlobalDataBadSmellDetector extends BookBadSmellDetector {
    public GlobalDataBadSmellDetector() {
        super(BadSmell.GLOBAL_DATA);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().fields().stream()
                .map(GlobalDataBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(candidate -> DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.field().name(),
                        candidate.field().startLine(), candidate.field().endLine(),
                        candidate.evidence(),
                        "Global or process-wide data is reachable outside a narrow owner.",
                        "Encapsulate the variable and restrict mutation behind intention-revealing operations."
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<GlobalDataCandidate> candidate(JavaFieldInfo field) {
        boolean moduleGlobal = "<module>".equals(field.ownerClass());
        boolean publicStatic = field.publicField() && field.staticField();
        if (!moduleGlobal && !publicStatic) {
            return java.util.Optional.empty();
        }
        boolean mutableContainer = mutableContainerType(field.type());
        boolean primitiveConstant = field.finalField()
                && !mutableContainer
                && (field.name().matches("[A-Z][A-Z0-9_]*") || DetectorSupport.primitiveLike(field.type()));
        if (primitiveConstant) {
            return java.util.Optional.empty();
        }
        if (moduleGlobal && field.finalField() && !mutableContainer) {
            return java.util.Optional.empty();
        }
        if (field.finalField() && !mutableContainer && !moduleGlobal) {
            return java.util.Optional.empty();
        }
        String signal;
        if (moduleGlobal && !field.finalField()) {
            signal = "module_level_mutable_data";
        } else if (publicStatic && !field.finalField()) {
            signal = "public_static_mutable_data";
        } else {
            signal = "globally_reachable_mutable_container";
        }
        return java.util.Optional.of(new GlobalDataCandidate(field, signal, mutableContainer, moduleGlobal));
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

    private record GlobalDataCandidate(JavaFieldInfo field, String signal, boolean mutableContainer, boolean moduleGlobal) {
        String severity() {
            return field.finalField() && mutableContainer ? "medium" : "high";
        }

        String confidence() {
            return moduleGlobal || (field.publicField() && field.staticField()) ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", signal,
                    "owner", field.ownerClass(),
                    "public", field.publicField(),
                    "static", field.staticField(),
                    "final", field.finalField(),
                    "field_type", field.type(),
                    "mutable_container_type", mutableContainer
            );
        }
    }
}
