package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LargeClassBadSmellDetector extends BookBadSmellDetector {
    private static final int WARNING_LINES = 300;
    private static final int WARNING_METHODS = 20;
    private static final int WARNING_FIELDS = 15;
    private static final int WARNING_TOTAL_COMPLEXITY = 80;
    private static final int GRAPH_MIN_METHODS = 6;
    private static final int GRAPH_MIN_FIELDS = 4;
    private static final Map<String, Set<String>> RESPONSIBILITY_TERMS = Map.of(
            "persistence", Set.of("save", "load", "persist", "repository", "db", "query", "store", "fetch"),
            "presentation", Set.of("render", "view", "template", "html", "ui", "format", "display"),
            "validation", Set.of("validate", "check", "verify", "rule", "guard"),
            "calculation", Set.of("calculate", "compute", "price", "total", "score", "tax", "sum"),
            "integration", Set.of("send", "notify", "http", "client", "api", "remote", "queue")
    );

    public LargeClassBadSmellDetector() {
        super(BadSmell.LARGE_CLASS);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().classes().stream()
                .map(LargeClassBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .map(candidate -> DetectorSupport.finding(
                        smell(), candidate.severity(), candidate.confidence(), candidate.classInfo().name(),
                        candidate.classInfo().startLine(), candidate.classInfo().endLine(),
                        candidate.evidence(),
                        "Class is large enough to hide multiple responsibilities.",
                        "Extract Class around cohesive field and method groups."
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static java.util.Optional<LargeClassCandidate> candidate(JavaClassInfo classInfo) {
        int methodCount = classInfo.methods().size();
        int fieldCount = classInfo.fields().size();
        int totalComplexity = classInfo.methods().stream().mapToInt(method -> method.cyclomaticComplexity()).sum();
        Set<String> responsibilityClusters = responsibilityClusters(classInfo);
        MethodFieldGraph graph = MethodFieldGraph.from(classInfo);
        List<String> signals = new ArrayList<>();
        if (classInfo.physicalLines() > WARNING_LINES) {
            signals.add("too_many_physical_lines");
        }
        if (methodCount > WARNING_METHODS) {
            signals.add("too_many_methods");
        }
        if (fieldCount > WARNING_FIELDS) {
            signals.add("too_many_fields");
        }
        if (totalComplexity >= WARNING_TOTAL_COMPLEXITY) {
            signals.add("high_total_complexity");
        }
        if (responsibilityClusters.size() >= 3 && methodCount >= 10) {
            signals.add("multiple_responsibility_clusters");
        }
        if (graph.lowCohesion()) {
            signals.add("low_method_field_cohesion");
        }
        if (graph.disconnectedClusters()) {
            signals.add("disconnected_method_field_clusters");
        }
        if (signals.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new LargeClassCandidate(
                classInfo,
                signals,
                totalComplexity,
                responsibilityClusters,
                graph
        ));
    }

    private static Set<String> responsibilityClusters(JavaClassInfo classInfo) {
        Set<String> tokens = Stream.concat(
                        classInfo.methods().stream().map(method -> method.name()),
                        classInfo.fields().stream().map(field -> field.name())
                )
                .flatMap(name -> splitWords(name).stream())
                .collect(java.util.stream.Collectors.toSet());
        return RESPONSIBILITY_TERMS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static List<String> splitWords(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private record LargeClassCandidate(
            JavaClassInfo classInfo,
            List<String> signals,
            int totalComplexity,
            Set<String> responsibilityClusters,
            MethodFieldGraph methodFieldGraph
    ) {
        String severity() {
            return signals.size() >= 2
                    || classInfo.physicalLines() > WARNING_LINES * 2
                    || classInfo.methods().size() > WARNING_METHODS * 2
                    ? "high"
                    : "medium";
        }

        String confidence() {
            return signals.contains("too_many_physical_lines")
                    || signals.contains("too_many_methods")
                    || signals.contains("too_many_fields")
                    ? "high"
                    : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signals", signals,
                    "physical_lines", classInfo.physicalLines(),
                    "physical_lines_threshold", WARNING_LINES,
                    "method_count", classInfo.methods().size(),
                    "method_count_threshold", WARNING_METHODS,
                    "field_count", classInfo.fields().size(),
                    "field_count_threshold", WARNING_FIELDS,
                    "total_cyclomatic_complexity", totalComplexity,
                    "total_cyclomatic_complexity_threshold", WARNING_TOTAL_COMPLEXITY,
                    "responsibility_clusters", List.copyOf(responsibilityClusters),
                    "method_field_graph", methodFieldGraph.toJson()
            );
        }
    }

    private record MethodFieldGraph(
            int behavioralMethodCount,
            int fieldCount,
            double cohesionRatio,
            int fieldTouchingMethodCount,
            List<ExtractionCluster> extractionClusters
    ) {
        static MethodFieldGraph from(JavaClassInfo classInfo) {
            List<JavaMethodInfo> behavioralMethods = classInfo.methods().stream()
                    .filter(method -> !method.constructor())
                    .filter(method -> !method.accessorMethod())
                    .toList();
            Set<String> knownFields = classInfo.fields().stream()
                    .map(field -> field.name())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, Set<String>> fieldsByMethod = behavioralMethods.stream()
                    .collect(Collectors.toMap(
                            JavaMethodInfo::name,
                            method -> referencedKnownFields(method, knownFields),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            double cohesionRatio = cohesionRatio(fieldsByMethod);
            int fieldTouchingMethodCount = (int) fieldsByMethod.values().stream()
                    .filter(fields -> !fields.isEmpty())
                    .count();
            List<ExtractionCluster> extractionClusters = connectedComponents(fieldsByMethod).stream()
                    .filter(ExtractionCluster::substantial)
                    .sorted(Comparator.comparingInt(ExtractionCluster::score).reversed()
                            .thenComparing(ExtractionCluster::label))
                    .toList();
            return new MethodFieldGraph(
                    behavioralMethods.size(),
                    knownFields.size(),
                    cohesionRatio,
                    fieldTouchingMethodCount,
                    extractionClusters
            );
        }

        boolean lowCohesion() {
            return behavioralMethodCount >= GRAPH_MIN_METHODS
                    && fieldCount >= GRAPH_MIN_FIELDS
                    && fieldTouchingMethodCount >= Math.max(4, behavioralMethodCount / 2)
                    && cohesionRatio < 0.35;
        }

        boolean disconnectedClusters() {
            return behavioralMethodCount >= GRAPH_MIN_METHODS
                    && fieldCount >= GRAPH_MIN_FIELDS
                    && extractionClusters.size() >= 2;
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "behavioral_method_count", behavioralMethodCount,
                    "field_count", fieldCount,
                    "field_touching_method_count", fieldTouchingMethodCount,
                    "method_field_cohesion_ratio", cohesionRatio,
                    "extraction_cluster_count", extractionClusters.size(),
                    "extraction_clusters", extractionClusters.stream()
                            .limit(5)
                            .map(ExtractionCluster::toJson)
                            .toList()
            );
        }

        private static Set<String> referencedKnownFields(JavaMethodInfo method, Set<String> knownFields) {
            Set<String> references = new LinkedHashSet<>();
            Stream.concat(method.ownFieldReads().stream(), method.ownFieldWrites().stream())
                    .filter(knownFields::contains)
                    .forEach(references::add);
            return references;
        }

        private static double cohesionRatio(Map<String, Set<String>> fieldsByMethod) {
            List<Map.Entry<String, Set<String>>> fieldTouchingMethods = fieldsByMethod.entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .toList();
            int pairs = 0;
            int connectedPairs = 0;
            for (int left = 0; left < fieldTouchingMethods.size(); left++) {
                for (int right = left + 1; right < fieldTouchingMethods.size(); right++) {
                    pairs++;
                    if (sharesField(fieldTouchingMethods.get(left).getValue(), fieldTouchingMethods.get(right).getValue())) {
                        connectedPairs++;
                    }
                }
            }
            return pairs == 0 ? 1.0 : (double) connectedPairs / pairs;
        }

        private static boolean sharesField(Set<String> left, Set<String> right) {
            return left.stream().anyMatch(right::contains);
        }

        private static List<ExtractionCluster> connectedComponents(Map<String, Set<String>> fieldsByMethod) {
            Map<String, Set<String>> methodToFields = fieldsByMethod.entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> new LinkedHashSet<>(entry.getValue()),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            Set<String> remainingMethods = new LinkedHashSet<>(methodToFields.keySet());
            List<ExtractionCluster> clusters = new ArrayList<>();
            while (!remainingMethods.isEmpty()) {
                String seed = remainingMethods.iterator().next();
                LinkedHashSet<String> methods = new LinkedHashSet<>();
                LinkedHashSet<String> fields = new LinkedHashSet<>();
                ArrayList<String> queue = new ArrayList<>();
                queue.add(seed);
                remainingMethods.remove(seed);
                for (int index = 0; index < queue.size(); index++) {
                    String method = queue.get(index);
                    methods.add(method);
                    Set<String> methodFields = methodToFields.getOrDefault(method, Set.of());
                    fields.addAll(methodFields);
                    List<String> connectedMethods = remainingMethods.stream()
                            .filter(candidate -> sharesField(methodFields, methodToFields.getOrDefault(candidate, Set.of())))
                            .toList();
                    connectedMethods.forEach(candidate -> {
                        remainingMethods.remove(candidate);
                        queue.add(candidate);
                    });
                }
                clusters.add(new ExtractionCluster(methods, fields));
            }
            return clusters;
        }
    }

    private record ExtractionCluster(Set<String> methods, Set<String> fields) {
        boolean substantial() {
            return fields.size() >= 2 && methods.size() >= 2;
        }

        int score() {
            return methods.size() + fields.size();
        }

        String label() {
            return !fields.isEmpty() ? fields.iterator().next() : methods.iterator().next();
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "methods", List.copyOf(methods),
                    "fields", List.copyOf(fields),
                    "method_count", methods.size(),
                    "field_count", fields.size()
            );
        }
    }
}
