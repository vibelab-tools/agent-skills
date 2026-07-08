package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DataClassBadSmellDetector extends BookBadSmellDetector {
    public DataClassBadSmellDetector() {
        super(BadSmell.DATA_CLASS);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().classes().stream()
                .filter(classInfo -> classInfo.fields().size() >= 2)
                .map(DataClassBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(classInfo -> DetectorSupport.finding(
                        smell(),
                        classInfo.severity(),
                        classInfo.confidence(),
                        classInfo.classInfo().name(),
                        classInfo.classInfo().startLine(),
                        classInfo.classInfo().endLine(),
                        classInfo.evidence(),
                        "Class is dominated by stored data and has little behavior of its own.",
                        "Move behavior that uses this data into the class, or make it an explicit immutable value object."
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<DataClassCandidate> candidate(JavaClassInfo classInfo) {
        if (immutableValueObject(classInfo)) {
            return java.util.Optional.empty();
        }
        long behavioral = classInfo.behavioralMethodCount();
        long totalMethods = classInfo.methods().stream().filter(method -> !method.constructor()).count();
        long accessors = classInfo.methods().stream()
                .filter(method -> !method.constructor())
                .filter(JavaMethodInfo::accessorMethod)
                .count();
        long setters = classInfo.methods().stream()
                .filter(method -> !method.constructor())
                .filter(DataClassBadSmellDetector::setterLike)
                .count();
        long exposedFields = classInfo.fields().stream().filter(JavaFieldInfo::publicField).count();
        long mutableFields = classInfo.fields().stream().filter(field -> !field.finalField()).count();
        List<String> signals = new ArrayList<>();
        if (behavioral == 0 && (accessors > 0 || exposedFields > 0 || mutableFields > 0)) {
            signals.add("data_only_type");
        }
        if (classInfo.fields().size() >= 3 && totalMethods > 0 && accessors >= Math.max(1, totalMethods - 1)) {
            signals.add("accessor_dominated_type");
        }
        if (setters >= 1 || exposedFields >= 1) {
            signals.add("externally_mutable_data");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new DataClassCandidate(
                classInfo,
                signals,
                totalMethods,
                accessors,
                behavioral,
                setters,
                exposedFields,
                mutableFields
        ));
    }

    private static boolean immutableValueObject(JavaClassInfo classInfo) {
        boolean allFinal = classInfo.fields().stream().allMatch(JavaFieldInfo::finalField);
        boolean hasConstructor = classInfo.methods().stream().anyMatch(JavaMethodInfo::constructor);
        boolean noSetters = classInfo.methods().stream().noneMatch(DataClassBadSmellDetector::setterLike);
        return allFinal && hasConstructor && noSetters;
    }

    private static boolean setterLike(JavaMethodInfo method) {
        return method.accessorMethod()
                && method.name().matches("(?i)^set[A-Z_].*");
    }

    private record DataClassCandidate(
            JavaClassInfo classInfo,
            List<String> signals,
            long totalMethods,
            long accessorMethods,
            long behavioralMethods,
            long setterMethods,
            long exposedFields,
            long mutableFields
    ) {
        String severity() {
            return signals.contains("externally_mutable_data") || behavioralMethods == 0 ? "medium" : "low";
        }

        String confidence() {
            return signals.size() >= 2 || accessorMethods >= 2 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "field_count", classInfo.fields().size(),
                    "method_count", totalMethods,
                    "accessor_method_count", accessorMethods,
                    "behavioral_method_count", behavioralMethods,
                    "setter_method_count", setterMethods,
                    "public_field_count", exposedFields,
                    "mutable_field_count", mutableFields
            );
        }
    }
}
