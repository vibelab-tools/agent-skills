package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AlternativeClassesWithDifferentInterfacesBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> CLASS_STOP_TOKENS = Set.of(
            "abstract", "base", "default", "impl", "implementation", "test", "tests",
            "mock", "stub", "fake", "sample", "example", "one", "two", "a", "b"
    );
    private static final Set<String> METHOD_STOP_TOKENS = Set.of(
            "by", "with", "from", "to", "for", "of", "and", "or", "the", "a", "an"
    );
    private static final Set<String> GENERIC_METHOD_ACTIONS = Set.of(
            "do", "run", "execute", "process", "handle", "apply", "perform", "work"
    );
    private static final Map<String, String> ACTION_SYNONYMS = Map.ofEntries(
            Map.entry("read", "read"),
            Map.entry("fetch", "read"),
            Map.entry("load", "read"),
            Map.entry("get", "read"),
            Map.entry("find", "read"),
            Map.entry("query", "read"),
            Map.entry("lookup", "read"),
            Map.entry("retrieve", "read"),
            Map.entry("select", "read"),
            Map.entry("write", "write"),
            Map.entry("save", "write"),
            Map.entry("store", "write"),
            Map.entry("put", "write"),
            Map.entry("persist", "write"),
            Map.entry("set", "write"),
            Map.entry("update", "update"),
            Map.entry("patch", "update"),
            Map.entry("replace", "update"),
            Map.entry("delete", "delete"),
            Map.entry("remove", "delete"),
            Map.entry("destroy", "delete"),
            Map.entry("drop", "delete"),
            Map.entry("create", "create"),
            Map.entry("new", "create"),
            Map.entry("build", "create"),
            Map.entry("make", "create"),
            Map.entry("open", "open"),
            Map.entry("connect", "open"),
            Map.entry("close", "close"),
            Map.entry("disconnect", "close"),
            Map.entry("total", "aggregate"),
            Map.entry("sum", "aggregate"),
            Map.entry("aggregate", "aggregate"),
            Map.entry("count", "count"),
            Map.entry("size", "count"),
            Map.entry("length", "count"),
            Map.entry("validate", "validate"),
            Map.entry("check", "validate"),
            Map.entry("verify", "validate"),
            Map.entry("format", "format"),
            Map.entry("render", "format"),
            Map.entry("present", "format"),
            Map.entry("map", "convert"),
            Map.entry("convert", "convert"),
            Map.entry("translate", "convert"),
            Map.entry("adapt", "convert")
    );
    private static final Map<String, String> ROLE_SYNONYMS = Map.ofEntries(
            Map.entry("source", "storage"),
            Map.entry("store", "storage"),
            Map.entry("storage", "storage"),
            Map.entry("repository", "storage"),
            Map.entry("repo", "storage"),
            Map.entry("cache", "storage"),
            Map.entry("dao", "storage"),
            Map.entry("gateway", "gateway"),
            Map.entry("client", "gateway"),
            Map.entry("provider", "gateway"),
            Map.entry("adapter", "adapter"),
            Map.entry("mapper", "adapter"),
            Map.entry("converter", "adapter"),
            Map.entry("formatter", "adapter"),
            Map.entry("reader", "storage"),
            Map.entry("writer", "storage")
    );

    public AlternativeClassesWithDifferentInterfacesBadSmellDetector() {
        super(BadSmell.ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        List<ClassProfile> profiles = profiles(context);
        Path analysisPath = context.analysis().path();
        for (int left = 0; left < profiles.size(); left++) {
            ClassProfile leftProfile = profiles.get(left);
            for (int right = left + 1; right < profiles.size(); right++) {
                ClassProfile rightProfile = profiles.get(right);
                if (!shouldCompareForContext(leftProfile, rightProfile, analysisPath)) {
                    continue;
                }
                candidate(leftProfile, rightProfile).ifPresent(candidate -> findings.add(finding(candidate)));
            }
        }
        if (!findings.isEmpty()) {
            return findings;
        }
        if ("java".equals(context.analysis().language()) && !context.analysis().classes().isEmpty()) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static List<ClassProfile> profiles(SmellAnalysisContext context) {
        List<JavaClassInfo> classes = context.projectIndex().allClasses().isEmpty()
                ? context.analysis().classes()
                : context.projectIndex().allClasses();
        Map<String, ProjectUsage> projectUsageByClass = projectUsageByClass(context.projectIndex());
        return classes.stream()
                .filter(classInfo -> !classInfo.interfaceType())
                .filter(classInfo -> !classInfo.abstractType())
                .map(classInfo -> ClassProfile.from(
                        classInfo,
                        context.projectIndex().pathForClass(classInfo.name()).orElse(context.analysis().path()),
                        projectUsageByClass.getOrDefault(
                                SourceProjectIndex.simpleTypeName(classInfo.name()),
                                new ProjectUsage(Set.of(), Set.of()))))
                .filter(ClassProfile::eligible)
                .toList();
    }

    private static boolean shouldCompareForContext(ClassProfile left, ClassProfile right, Path analysisPath) {
        Path leftPath = left.sourcePath();
        Path rightPath = right.sourcePath();
        if (leftPath == null || rightPath == null || leftPath.equals(rightPath)) {
            return leftPath == null || leftPath.equals(analysisPath);
        }
        Path canonical = leftPath.toString().compareTo(rightPath.toString()) <= 0 ? leftPath : rightPath;
        return canonical.equals(analysisPath);
    }

    private SmellFinding finding(AlternativeClassCandidate candidate) {
        JavaClassInfo left = candidate.left().classInfo();
        JavaClassInfo right = candidate.right().classInfo();
        return DetectorSupport.finding(
                smell(),
                candidate.severity(),
                candidate.confidence(),
                left.name(),
                left.startLine(),
                right.endLine(),
                candidate.evidence(),
                "Two classes have a similar structural role but expose different interfaces.",
                "Consider Change Function Declaration, Move Function, or Extract Superclass where the shared role is real."
        );
    }

    private static Optional<AlternativeClassCandidate> candidate(ClassProfile left, ClassProfile right) {
        InterfaceDifference difference = interfaceDifference(left, right);
        if (!difference.different()) {
            return Optional.empty();
        }

        List<MethodMatch> methodMatches = methodMatches(left, right);
        long strongMatches = methodMatches.stream().filter(match -> match.score() >= 0.60).count();
        double methodRoleSimilarity = methodRoleSimilarity(methodMatches, left.methods().size(), right.methods().size());
        double signatureSimilarity = jaccard(left.methodShapes(), right.methodShapes());
        double classRoleSimilarity = jaccard(left.roleTokens(), right.roleTokens());
        double fieldSimilarity = fieldSimilarity(left, right);
        double behaviorSimilarity = jaccard(left.behaviorTokens(), right.behaviorTokens());
        double baseSimilarity = 0.42 * methodRoleSimilarity
                + 0.22 * signatureSimilarity
                + 0.14 * classRoleSimilarity
                + 0.12 * fieldSimilarity
                + 0.10 * behaviorSimilarity;
        double projectUsageSimilarity = nonEmptyJaccard(left.inboundCallActions(), right.inboundCallActions());
        Set<String> sharedCallers = new LinkedHashSet<>(left.inboundCallerOwners());
        sharedCallers.retainAll(right.inboundCallerOwners());
        double projectUsageBonus = 0.08 * projectUsageSimilarity + Math.min(0.04, sharedCallers.size() * 0.02);
        double overallSimilarity = Math.min(1.0, baseSimilarity + projectUsageBonus);

        boolean similarRole = strongMatches >= Math.min(left.methods().size(), right.methods().size())
                || (strongMatches >= 2 && methodRoleSimilarity >= 0.55)
                || (strongMatches >= 1 && projectUsageSimilarity >= 0.75 && !sharedCallers.isEmpty());
        if (!similarRole || overallSimilarity < 0.58) {
            return Optional.empty();
        }
        return Optional.of(new AlternativeClassCandidate(
                left,
                right,
                methodMatches.stream().filter(match -> match.score() >= 0.50).toList(),
                difference,
                methodRoleSimilarity,
                signatureSimilarity,
                classRoleSimilarity,
                fieldSimilarity,
                behaviorSimilarity,
                projectUsageSimilarity,
                sharedCallers,
                overallSimilarity
        ));
    }

    private static InterfaceDifference interfaceDifference(ClassProfile left, ClassProfile right) {
        Set<String> leftNames = left.methods().stream().map(MethodRole::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> rightNames = right.methods().stream().map(MethodRole::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> sharedNames = new LinkedHashSet<>(leftNames);
        sharedNames.retainAll(rightNames);
        double exactNameOverlap = (double) sharedNames.size() / Math.max(1, Math.min(leftNames.size(), rightNames.size()));
        double rawNameTokenSimilarity = jaccard(left.rawMethodTokens(), right.rawMethodTokens());
        boolean different = exactNameOverlap < 0.50 && rawNameTokenSimilarity < 0.55;
        return new InterfaceDifference(different, exactNameOverlap, rawNameTokenSimilarity, sharedNames);
    }

    private static List<MethodMatch> methodMatches(ClassProfile left, ClassProfile right) {
        List<MethodMatch> matches = new ArrayList<>();
        Set<MethodRole> usedRight = new HashSet<>();
        left.methods().stream()
                .sorted(Comparator.comparing(MethodRole::name))
                .forEach(leftMethod -> {
                    Optional<MethodMatch> best = right.methods().stream()
                            .filter(rightMethod -> !usedRight.contains(rightMethod))
                            .map(rightMethod -> MethodMatch.between(leftMethod, rightMethod))
                            .max(Comparator.comparingDouble(MethodMatch::score));
                    best.filter(match -> match.score() >= 0.45).ifPresent(match -> {
                        usedRight.add(match.right());
                        matches.add(match);
                    });
                });
        return matches.stream()
                .sorted(Comparator.comparingDouble(MethodMatch::score).reversed())
                .toList();
    }

    private static double methodRoleSimilarity(List<MethodMatch> matches, int leftCount, int rightCount) {
        int denominator = Math.max(leftCount, rightCount);
        if (denominator == 0) {
            return 0.0;
        }
        double score = matches.stream().mapToDouble(MethodMatch::score).sum();
        return score / denominator;
    }

    private static double fieldSimilarity(ClassProfile left, ClassProfile right) {
        int countDelta = Math.abs(left.fieldCount() - right.fieldCount());
        double countSimilarity = 1.0 - ((double) countDelta / Math.max(1, Math.max(left.fieldCount(), right.fieldCount())));
        return 0.55 * countSimilarity + 0.45 * jaccard(left.fieldTypeCategories(), right.fieldTypeCategories());
    }

    private record ClassProfile(
            JavaClassInfo classInfo,
            List<MethodRole> methods,
            Set<String> roleTokens,
            Set<String> rawMethodTokens,
            Set<String> methodShapes,
            Set<String> behaviorTokens,
            Set<String> fieldTypeCategories,
            Set<String> inboundCallActions,
            Set<String> inboundCallerOwners,
            Path sourcePath,
            int fieldCount
    ) {
        static ClassProfile from(JavaClassInfo classInfo, Path sourcePath, ProjectUsage projectUsage) {
            List<MethodRole> methods = classInfo.methods().stream()
                    .filter(method -> !method.constructor())
                    .filter(method -> !method.accessorMethod())
                    .map(MethodRole::from)
                    .filter(MethodRole::meaningful)
                    .toList();
            Set<String> roleTokens = AlternativeClassesWithDifferentInterfacesBadSmellDetector.roleTokens(classInfo, methods);
            return new ClassProfile(
                    classInfo,
                    methods,
                    roleTokens,
                    methods.stream().flatMap(method -> method.rawTokens().stream()).collect(Collectors.toCollection(LinkedHashSet::new)),
                    methods.stream().map(MethodRole::signatureShape).collect(Collectors.toCollection(LinkedHashSet::new)),
                    methods.stream().flatMap(method -> method.behaviorTokens().stream()).collect(Collectors.toCollection(LinkedHashSet::new)),
                    classInfo.fields().stream()
                            .filter(field -> !field.staticField())
                            .map(JavaFieldInfo::type)
                            .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::typeCategory)
                            .collect(Collectors.toCollection(LinkedHashSet::new)),
                    projectUsage.callActions(),
                    projectUsage.callerOwners(),
                    sourcePath,
                    (int) classInfo.fields().stream().filter(field -> !field.staticField()).count()
            );
        }

        boolean eligible() {
            return methods.size() >= 2
                    && !classInfo.name().matches(".*(Test|Tests|Mock|Stub|Fake)$");
        }
    }

    private record MethodRole(
            JavaMethodInfo method,
            String name,
            Set<String> rawTokens,
            String action,
            Set<String> objectTokens,
            String returnCategory,
            List<String> parameterCategories,
            Set<String> behaviorTokens
    ) {
        static MethodRole from(JavaMethodInfo method) {
            List<String> tokens = splitWords(method.name());
            String action = tokens.isEmpty() ? "" : normalizeAction(tokens.getFirst());
            Set<String> objectTokens = tokens.stream()
                    .skip(1)
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeRoleToken)
                    .filter(token -> !METHOD_STOP_TOKENS.contains(token))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> behaviorTokens = new LinkedHashSet<>();
            method.methodCallCounts().keySet().stream()
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::splitWords)
                    .flatMap(List::stream)
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeAction)
                    .filter(actionToken -> !actionToken.isBlank())
                    .forEach(behaviorTokens::add);
            method.ownFieldReads().stream()
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::splitWords)
                    .flatMap(List::stream)
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeRoleToken)
                    .forEach(behaviorTokens::add);
            method.delegations().stream()
                    .map(delegation -> splitWords(delegation.targetMethod()))
                    .flatMap(List::stream)
                    .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeAction)
                    .filter(actionToken -> !actionToken.isBlank())
                    .forEach(behaviorTokens::add);
            return new MethodRole(
                    method,
                    method.name(),
                    new LinkedHashSet<>(tokens),
                    action,
                    objectTokens,
                    typeCategory(method.returnType()),
                    method.parameterTypes().stream()
                            .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::typeCategory)
                            .toList(),
                    behaviorTokens
            );
        }

        boolean meaningful() {
            return !name.isBlank() && !action.isBlank();
        }

        int parameterCount() {
            return parameterCategories.size();
        }

        String signatureShape() {
            return parameterCategories + "->" + returnCategory;
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "name", name,
                    "action", action,
                    "object_tokens", List.copyOf(objectTokens),
                    "return_category", returnCategory,
                    "parameter_categories", parameterCategories,
                    "behavior_tokens", List.copyOf(behaviorTokens)
            );
        }
    }

    private record MethodMatch(
            MethodRole left,
            MethodRole right,
            double actionSimilarity,
            double signatureSimilarity,
            double objectSimilarity,
            double behaviorSimilarity,
            double score
    ) {
        static MethodMatch between(MethodRole left, MethodRole right) {
            double actionSimilarity = left.action().isBlank() || right.action().isBlank()
                    ? 0.0
                    : left.action().equals(right.action()) ? 1.0 : 0.0;
            double signatureSimilarity = AlternativeClassesWithDifferentInterfacesBadSmellDetector.signatureSimilarity(left, right);
            double objectSimilarity = jaccard(left.objectTokens(), right.objectTokens());
            double behaviorSimilarity = jaccard(left.behaviorTokens(), right.behaviorTokens());
            double score = 0.48 * actionSimilarity
                    + 0.30 * signatureSimilarity
                    + 0.12 * objectSimilarity
                    + 0.10 * behaviorSimilarity;
            return new MethodMatch(
                    left,
                    right,
                    actionSimilarity,
                    signatureSimilarity,
                    objectSimilarity,
                    behaviorSimilarity,
                    score
            );
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "left", left.toJson(),
                    "right", right.toJson(),
                    "action_similarity", actionSimilarity,
                    "signature_similarity", signatureSimilarity,
                    "object_similarity", objectSimilarity,
                    "behavior_similarity", behaviorSimilarity,
                    "score", score
            );
        }
    }

    private record InterfaceDifference(
            boolean different,
            double exactNameOverlap,
            double rawNameTokenSimilarity,
            Set<String> sharedMethodNames
    ) {
    }

    private record AlternativeClassCandidate(
            ClassProfile left,
            ClassProfile right,
            List<MethodMatch> methodMatches,
            InterfaceDifference interfaceDifference,
            double methodRoleSimilarity,
            double signatureSimilarity,
            double classRoleSimilarity,
            double fieldSimilarity,
            double behaviorSimilarity,
            double projectUsageSimilarity,
            Set<String> sharedCallerOwners,
            double overallSimilarity
    ) {
        String severity() {
            return overallSimilarity >= 0.75 && (methodRoleSimilarity >= 0.70 || projectUsageSimilarity >= 0.75)
                    ? "high"
                    : "medium";
        }

        String confidence() {
            return overallSimilarity >= 0.70 && (methodMatches.size() >= 2 || !sharedCallerOwners.isEmpty())
                    ? "high"
                    : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", "similar_role_different_interface",
                    "classes", List.of(left.classInfo().name(), right.classInfo().name()),
                    "left_methods", left.methods().stream().map(MethodRole::name).toList(),
                    "right_methods", right.methods().stream().map(MethodRole::name).toList(),
                    "shared_method_names", List.copyOf(interfaceDifference.sharedMethodNames()),
                    "exact_method_name_overlap", interfaceDifference.exactNameOverlap(),
                    "raw_method_token_similarity", interfaceDifference.rawNameTokenSimilarity(),
                    "method_role_similarity", methodRoleSimilarity,
                    "signature_similarity", signatureSimilarity,
                    "class_role_similarity", classRoleSimilarity,
                    "field_similarity", fieldSimilarity,
                    "behavior_similarity", behaviorSimilarity,
                    "project_usage_similarity", projectUsageSimilarity,
                    "shared_caller_owners", List.copyOf(sharedCallerOwners),
                    "left_inbound_call_actions", List.copyOf(left.inboundCallActions()),
                    "right_inbound_call_actions", List.copyOf(right.inboundCallActions()),
                    "overall_similarity", overallSimilarity,
                    "left_class_role_tokens", List.copyOf(left.roleTokens()),
                    "right_class_role_tokens", List.copyOf(right.roleTokens()),
                    "method_matches", methodMatches.stream().map(MethodMatch::toJson).toList()
            );
        }
    }

    private record ProjectUsage(Set<String> callActions, Set<String> callerOwners) {
    }

    private static Map<String, ProjectUsage> projectUsageByClass(SourceProjectIndex projectIndex) {
        Map<String, MutableProjectUsage> usageByClass = new LinkedHashMap<>();
        projectIndex.allCallEdges().stream()
                .forEach(edge -> {
                    String className = SourceProjectIndex.simpleTypeName(edge.targetOwner());
                    if (className.isBlank()) {
                        className = SourceProjectIndex.simpleTypeName(edge.call().receiverType());
                    }
                    if (className.isBlank()) {
                        return;
                    }
                    MutableProjectUsage usage = usageByClass.computeIfAbsent(className, ignored -> new MutableProjectUsage());
                    String action = callAction(edge.call().methodName());
                    if (!action.isBlank()) {
                        usage.callActions().add(action);
                    }
                    String callerOwner = SourceProjectIndex.simpleTypeName(edge.caller().method().ownerClass());
                    if (!callerOwner.isBlank() && !callerOwner.equals(className)) {
                        usage.callerOwners().add(callerOwner);
                    }
                });
        return usageByClass.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ProjectUsage(
                                Set.copyOf(entry.getValue().callActions()),
                                Set.copyOf(entry.getValue().callerOwners())),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private record MutableProjectUsage(Set<String> callActions, Set<String> callerOwners) {
        private MutableProjectUsage() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private static String callAction(String methodName) {
        List<String> tokens = splitWords(methodName);
        if (tokens.isEmpty()) {
            return "";
        }
        return normalizeAction(tokens.getFirst());
    }

    private static Set<String> roleTokens(JavaClassInfo classInfo, List<MethodRole> methods) {
        Set<String> tokens = splitWords(classInfo.name()).stream()
                .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeRoleToken)
                .filter(token -> !CLASS_STOP_TOKENS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        classInfo.fields().stream()
                .filter(field -> !field.staticField())
                .map(JavaFieldInfo::type)
                .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::simpleTypeName)
                .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::splitWords)
                .flatMap(List::stream)
                .map(AlternativeClassesWithDifferentInterfacesBadSmellDetector::normalizeRoleToken)
                .filter(token -> !CLASS_STOP_TOKENS.contains(token))
                .forEach(tokens::add);
        methods.stream()
                .flatMap(method -> method.objectTokens().stream())
                .filter(token -> !CLASS_STOP_TOKENS.contains(token))
                .forEach(tokens::add);
        return tokens;
    }

    private static double signatureSimilarity(MethodRole left, MethodRole right) {
        double returnSimilarity = left.returnCategory().equals(right.returnCategory()) ? 1.0 : 0.0;
        double parameterCountSimilarity = left.parameterCount() == right.parameterCount()
                ? 1.0
                : 1.0 - ((double) Math.abs(left.parameterCount() - right.parameterCount())
                / Math.max(1, Math.max(left.parameterCount(), right.parameterCount())));
        double parameterCategorySimilarity = jaccard(
                new LinkedHashSet<>(left.parameterCategories()),
                new LinkedHashSet<>(right.parameterCategories())
        );
        return 0.45 * returnSimilarity + 0.35 * parameterCountSimilarity + 0.20 * parameterCategorySimilarity;
    }

    private static String normalizeAction(String token) {
        return ACTION_SYNONYMS.getOrDefault(token, GENERIC_METHOD_ACTIONS.contains(token) ? "" : token);
    }

    private static String normalizeRoleToken(String token) {
        return ROLE_SYNONYMS.getOrDefault(token, token);
    }

    private static String typeCategory(String type) {
        if (type == null || type.isBlank()) {
            return "unknown";
        }
        String normalized = simpleTypeName(type);
        if (Set.of("byte", "short", "int", "integer", "long", "float", "double", "bigdecimal").contains(normalized)) {
            return "number";
        }
        if (Set.of("boolean", "bool").contains(normalized)) {
            return "boolean";
        }
        if (Set.of("string", "char", "character").contains(normalized)) {
            return "text";
        }
        if (normalized.contains("list") || normalized.contains("set") || normalized.contains("collection") || normalized.endsWith("[]")) {
            return "collection";
        }
        if (normalized.contains("map") || normalized.contains("dict")) {
            return "map";
        }
        if (normalized.contains("optional")) {
            return "optional";
        }
        if ("void".equals(normalized)) {
            return "void";
        }
        return "object";
    }

    private static String simpleTypeName(String type) {
        String normalized = type.replace("java.lang.", "").replaceAll("\\s+", "");
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.toLowerCase();
    }

    private static List<String> splitWords(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(String::toLowerCase)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / Math.max(1, union.size());
    }

    private static double nonEmptyJaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        return jaccard(left, right);
    }
}
