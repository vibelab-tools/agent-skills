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
import java.util.Set;
import java.util.stream.Stream;

public final class SpeculativeGeneralityBadSmellDetector extends BookBadSmellDetector {
    public SpeculativeGeneralityBadSmellDetector() {
        super(BadSmell.SPECULATIVE_GENERALITY);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<JavaClassInfo> indexedClasses = context.projectIndex().allClasses().isEmpty()
                ? context.analysis().classes()
                : context.projectIndex().allClasses();
        UsageIndex usageIndex = UsageIndex.from(indexedClasses);
        List<SmellFinding> findings = new ArrayList<>(context.analysis().classes().stream()
                .map(classInfo -> candidate(classInfo, usageIndex))
                .flatMap(java.util.Optional::stream)
                .map(candidate -> DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.classInfo().name(),
                        candidate.classInfo().startLine(), candidate.classInfo().endLine(),
                        candidate.evidence(),
                        "Abstraction appears to anticipate future extension that is not present in the analyzed source.",
                        "Collapse or inline speculative abstraction until real variation appears."
                ))
                .toList());
        if (hasReliableClassModel(context)) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static boolean hasReliableClassModel(SmellAnalysisContext context) {
        return "java".equals(context.analysis().language())
                && "javac-tree-api".equals(context.analysis().parserId())
                && !context.analysis().classes().isEmpty();
    }

    private static java.util.Optional<SpeculativeCandidate> candidate(JavaClassInfo classInfo, UsageIndex usageIndex) {
        long methodCount = classInfo.methods().stream().filter(method -> !method.constructor()).count();
        if (methodCount > 3) {
            return java.util.Optional.empty();
        }
        int subtypeCount = usageIndex.subtypeCount(classInfo.name());
        int typeReferenceCount = usageIndex.typeReferenceCount(classInfo.name());
        boolean abstraction = classInfo.abstractType() || classInfo.interfaceType();
        boolean speculativeName = speculativeName(classInfo.name());
        List<String> signals = new ArrayList<>();
        if (abstraction && subtypeCount == 0 && typeReferenceCount == 0) {
            signals.add("unused_abstraction");
        }
        if (abstraction && subtypeCount == 1 && methodCount <= 2 && typeReferenceCount <= 1) {
            signals.add("single_implementation_abstraction");
        }
        if (speculativeName && subtypeCount <= 1 && typeReferenceCount <= 1) {
            signals.add("future_or_generic_name");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new SpeculativeCandidate(
                classInfo,
                signals,
                subtypeCount,
                typeReferenceCount,
                methodCount
        ));
    }

    private static boolean speculativeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("abstract")
                || lower.startsWith("base")
                || lower.startsWith("generic")
                || lower.startsWith("future")
                || lower.contains("extension")
                || lower.contains("placeholder");
    }

    private record UsageIndex(Map<String, Integer> subtypeCounts, Map<String, Integer> typeReferenceCounts) {
        static UsageIndex from(List<JavaClassInfo> classes) {
            Map<String, Integer> subtypeCounts = new java.util.LinkedHashMap<>();
            Map<String, Integer> typeReferenceCounts = new java.util.LinkedHashMap<>();
            classes.forEach(classInfo -> {
                Stream.concat(
                                classInfo.extendsName() == null ? Stream.empty() : Stream.of(classInfo.extendsName()),
                                classInfo.implementsNames().stream()
                        )
                        .map(SpeculativeGeneralityBadSmellDetector::simpleTypeName)
                        .filter(type -> !type.isBlank())
                        .forEach(type -> subtypeCounts.merge(type, 1, Integer::sum));
                classInfo.fields().stream()
                        .map(field -> simpleTypeName(field.type()))
                        .filter(type -> !type.isBlank())
                        .forEach(type -> typeReferenceCounts.merge(type, 1, Integer::sum));
                classInfo.methods().forEach(method -> {
                    Stream.concat(method.parameterTypes().stream(), Stream.of(method.returnType()))
                            .map(SpeculativeGeneralityBadSmellDetector::simpleTypeName)
                            .filter(type -> !type.isBlank())
                            .forEach(type -> typeReferenceCounts.merge(type, 1, Integer::sum));
                });
            });
            return new UsageIndex(subtypeCounts, typeReferenceCounts);
        }

        int subtypeCount(String name) {
            return subtypeCounts.getOrDefault(simpleTypeName(name), 0);
        }

        int typeReferenceCount(String name) {
            return typeReferenceCounts.getOrDefault(simpleTypeName(name), 0);
        }
    }

    private static String simpleTypeName(String type) {
        if (type == null) {
            return "";
        }
        String cleaned = type.replaceAll("<.*>", "")
                .replace("[]", "")
                .trim();
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < cleaned.length()) {
            cleaned = cleaned.substring(dot + 1);
        }
        return cleaned.replaceAll("[^A-Za-z0-9_$]", "");
    }

    private record SpeculativeCandidate(
            JavaClassInfo classInfo,
            List<String> signals,
            int subtypeCount,
            int typeReferenceCount,
            long methodCount
    ) {
        String severity() {
            return signals.contains("unused_abstraction") ? "medium" : "low";
        }

        String confidence() {
            return signals.contains("unused_abstraction") || signals.size() >= 2 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "abstract", classInfo.abstractType(),
                    "interface", classInfo.interfaceType(),
                    "method_count", methodCount,
                    "known_subtypes_or_implementers", subtypeCount,
                    "type_reference_count", typeReferenceCount,
                    "name", classInfo.name()
            );
        }
    }
}
