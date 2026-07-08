package com.codex.refactor.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class SourceProjectIndex {
    private static final SourceProjectIndex EMPTY = new SourceProjectIndex(List.of());

    private final List<SourceFileAnalysis> analyses;
    private final Map<String, List<JavaClassInfo>> classesByName = new LinkedHashMap<>();
    private final Map<String, List<JavaClassInfo>> subtypesByName = new LinkedHashMap<>();
    private final Map<String, Integer> typeReferenceCounts = new LinkedHashMap<>();
    private final Map<String, Path> classPaths = new LinkedHashMap<>();
    private final List<MethodEntry> methods = new ArrayList<>();
    private final Map<String, List<MethodEntry>> methodsByName = new LinkedHashMap<>();
    private final Map<String, List<MethodEntry>> methodsBySignature = new LinkedHashMap<>();
    private final Map<String, List<MethodEntry>> callersByMethodName = new LinkedHashMap<>();
    private final List<CallEdge> callEdges = new ArrayList<>();
    private final Map<JavaMethodInfo, List<CallEdge>> callEdgesByCaller = new LinkedHashMap<>();

    private SourceProjectIndex(List<SourceFileAnalysis> analyses) {
        this.analyses = List.copyOf(analyses);
        build();
    }

    public static SourceProjectIndex empty() {
        return EMPTY;
    }

    public static SourceProjectIndex from(List<SourceFileAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return empty();
        }
        return new SourceProjectIndex(analyses);
    }

    public List<SourceFileAnalysis> analyses() {
        return analyses;
    }

    public List<JavaClassInfo> allClasses() {
        return classesByName.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public List<MethodEntry> allMethods() {
        return List.copyOf(methods);
    }

    public Optional<JavaClassInfo> classByName(String name) {
        List<JavaClassInfo> matches = classesByName.getOrDefault(simpleTypeName(name), List.of());
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    public List<JavaClassInfo> classesByName(String name) {
        return List.copyOf(classesByName.getOrDefault(simpleTypeName(name), List.of()));
    }

    public List<JavaClassInfo> subtypesOf(String name) {
        return List.copyOf(subtypesByName.getOrDefault(simpleTypeName(name), List.of()));
    }

    public int subtypeCount(String name) {
        return subtypesByName.getOrDefault(simpleTypeName(name), List.of()).size();
    }

    public int typeReferenceCount(String name) {
        return typeReferenceCounts.getOrDefault(simpleTypeName(name), 0);
    }

    public Set<String> knownClassNames() {
        return Set.copyOf(classesByName.keySet());
    }

    public Optional<Path> pathForClass(String name) {
        return Optional.ofNullable(classPaths.get(simpleTypeName(name)));
    }

    public boolean containsClass(String name) {
        return classesByName.containsKey(simpleTypeName(name));
    }

    public List<MethodEntry> methodsByName(String name) {
        return List.copyOf(methodsByName.getOrDefault(simpleMethodName(name), List.of()));
    }

    public List<MethodEntry> methodsBySignature(String name, int parameterCount) {
        return List.copyOf(methodsBySignature.getOrDefault(methodSignatureKey(name, parameterCount), List.of()));
    }

    public List<MethodEntry> callersOf(String methodName) {
        return List.copyOf(callersByMethodName.getOrDefault(simpleMethodName(methodName), List.of()));
    }

    public List<CallEdge> allCallEdges() {
        return List.copyOf(callEdges);
    }

    public List<CallEdge> callEdgesFrom(JavaMethodInfo method) {
        return List.copyOf(callEdgesByCaller.getOrDefault(method, List.of()));
    }

    private void build() {
        for (SourceFileAnalysis analysis : analyses) {
            for (JavaClassInfo classInfo : analysis.classes()) {
                String className = simpleTypeName(classInfo.name());
                classesByName.computeIfAbsent(className, ignored -> new ArrayList<>()).add(classInfo);
                classPaths.putIfAbsent(className, analysis.path());
            }
            for (JavaMethodInfo method : analysis.methods()) {
                MethodEntry entry = new MethodEntry(analysis.path(), analysis.language(), method);
                methods.add(entry);
                methodsByName.computeIfAbsent(entry.simpleName(), ignored -> new ArrayList<>()).add(entry);
                methodsBySignature.computeIfAbsent(entry.signatureKey(), ignored -> new ArrayList<>()).add(entry);
                method.methodCallCounts().keySet().stream()
                        .map(SourceProjectIndex::simpleMethodName)
                        .filter(name -> !name.isBlank())
                        .forEach(name -> callersByMethodName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(entry));
            }
        }
        for (MethodEntry caller : methods) {
            for (MethodCallInfo call : caller.method().methodCalls()) {
                CallEdge edge = new CallEdge(caller, call, resolveTarget(caller, call));
                callEdges.add(edge);
                callEdgesByCaller.computeIfAbsent(caller.method(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        for (SourceFileAnalysis analysis : analyses) {
            for (JavaClassInfo classInfo : analysis.classes()) {
                parentTypes(classInfo)
                        .filter(type -> !type.isBlank())
                        .forEach(type -> {
                            subtypesByName.computeIfAbsent(type, ignored -> new ArrayList<>()).add(classInfo);
                            typeReferenceCounts.merge(type, 1, Integer::sum);
                        });
                classInfo.fields().stream()
                        .map(field -> simpleTypeName(field.type()))
                        .filter(type -> !type.isBlank())
                        .forEach(type -> typeReferenceCounts.merge(type, 1, Integer::sum));
                classInfo.methods().forEach(method -> {
                    Stream.concat(method.parameterTypes().stream(), Stream.of(method.returnType()))
                            .map(SourceProjectIndex::simpleTypeName)
                            .filter(type -> !type.isBlank())
                            .forEach(type -> typeReferenceCounts.merge(type, 1, Integer::sum));
                    method.variableTypes().values().stream()
                            .map(SourceProjectIndex::simpleTypeName)
                            .filter(type -> !type.isBlank())
                            .forEach(type -> typeReferenceCounts.merge(type, 1, Integer::sum));
                });
            }
        }
    }

    private Optional<MethodEntry> resolveTarget(MethodEntry caller, MethodCallInfo call) {
        String receiverType = resolvedReceiverType(caller, call);
        List<MethodEntry> candidates = methodsBySignature(call.methodName(), call.argumentCount());
        if (!receiverType.isBlank()) {
            String simpleReceiverType = simpleTypeName(receiverType);
            Optional<MethodEntry> typedTarget = candidates.stream()
                    .filter(candidate -> simpleTypeName(candidate.method().ownerClass()).equals(simpleReceiverType))
                    .findFirst();
            if (typedTarget.isPresent()) {
                return typedTarget;
            }
        }
        if ("self".equals(call.receiverKind())) {
            String owner = simpleTypeName(caller.method().ownerClass());
            return candidates.stream()
                    .filter(candidate -> simpleTypeName(candidate.method().ownerClass()).equals(owner))
                    .findFirst();
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private String resolvedReceiverType(MethodEntry caller, MethodCallInfo call) {
        if (call.receiverType() != null && !call.receiverType().isBlank()) {
            return call.receiverType();
        }
        if ("self".equals(call.receiverKind())) {
            return caller.method().ownerClass();
        }
        return "";
    }

    private static Stream<String> parentTypes(JavaClassInfo classInfo) {
        Stream<String> extended = classInfo.extendsName() == null
                ? Stream.empty()
                : Stream.of(simpleTypeName(classInfo.extendsName()));
        return Stream.concat(extended, classInfo.implementsNames().stream().map(SourceProjectIndex::simpleTypeName));
    }

    public static String simpleTypeName(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String normalized = type.replace("java.lang.", "")
                .replaceAll("\\s+", "")
                .replace("...", "[]");
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        normalized = normalized.replace("[]", "");
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        int scope = normalized.lastIndexOf("::");
        if (scope >= 0 && scope + 2 < normalized.length()) {
            normalized = normalized.substring(scope + 2);
        }
        return normalized.replaceAll("[^A-Za-z0-9_$]", "");
    }

    public static String simpleMethodName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim();
        int dot = normalized.lastIndexOf('.');
        int scope = normalized.lastIndexOf("::");
        int delimiter = Math.max(dot, scope);
        if (delimiter >= 0 && delimiter + 1 < normalized.length()) {
            normalized = normalized.substring(delimiter + 1);
        }
        return normalized.replaceAll("[^A-Za-z0-9_$]", "");
    }

    public static String methodSignatureKey(String name, int parameterCount) {
        return simpleMethodName(name) + "/" + Math.max(0, parameterCount);
    }

    public record MethodEntry(Path path, String language, JavaMethodInfo method) {
        public String simpleName() {
            return simpleMethodName(method.name());
        }

        public int parameterCount() {
            return method.parameterTypes().size();
        }

        public String signatureKey() {
            return methodSignatureKey(method.name(), parameterCount());
        }
    }

    public record CallEdge(MethodEntry caller, MethodCallInfo call, Optional<MethodEntry> target) {
        public boolean resolved() {
            return target.isPresent();
        }

        public String targetOwner() {
            return target.map(entry -> entry.method().ownerClass()).orElse("");
        }

        public String targetMethod() {
            return target.map(entry -> entry.method().name()).orElse("");
        }
    }
}
