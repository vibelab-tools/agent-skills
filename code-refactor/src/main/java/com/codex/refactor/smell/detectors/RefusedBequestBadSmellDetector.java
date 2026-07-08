package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class RefusedBequestBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> HOOK_METHOD_NAME_PARTS = Set.of(
            "after", "before", "hook", "callback", "visit", "listener"
    );

    public RefusedBequestBadSmellDetector() {
        super(BadSmell.REFUSED_BEQUEST);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        List<JavaClassInfo> indexedClasses = context.projectIndex().allClasses().isEmpty()
                ? context.analysis().classes()
                : context.projectIndex().allClasses();
        Map<String, JavaClassInfo> classesByName = classesByName(indexedClasses);
        context.analysis().classes().stream()
                .filter(classInfo -> !classInfo.interfaceType())
                .filter(RefusedBequestBadSmellDetector::hasInheritance)
                .map(classInfo -> candidate(classInfo, classesByName))
                .flatMap(Optional::stream)
                .map(this::finding)
                .forEach(findings::add);

        if (!findings.isEmpty()) {
            return findings;
        }
        if ("java".equals(context.analysis().language()) && !context.analysis().classes().isEmpty()) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(RefusedBequestCandidate candidate) {
        JavaClassInfo classInfo = candidate.classInfo();
        return DetectorSupport.finding(
                smell(),
                candidate.severity(),
                candidate.confidence(),
                classInfo.name(),
                classInfo.startLine(),
                classInfo.endLine(),
                candidate.evidence(),
                "Subclass rejects inherited behavior through unsupported, empty, or default-value overrides.",
                "Replace inheritance with delegation or move the unwanted behavior out of the superclass/interface."
        );
    }

    private static Optional<RefusedBequestCandidate> candidate(
            JavaClassInfo classInfo,
            Map<String, JavaClassInfo> classesByName
    ) {
        List<InheritedContract> inheritedContracts = inheritedContracts(classInfo, classesByName);
        ContractIndex contractIndex = ContractIndex.from(inheritedContracts);
        List<JavaMethodInfo> behavioralMethods = classInfo.methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .toList();

        List<RejectedOverride> rejectedOverrides = new ArrayList<>();
        for (JavaMethodInfo method : behavioralMethods) {
            Optional<InheritedContract> inherited = contractIndex.match(method);
            Optional<RejectionSignal> rejection = inherited
                    .flatMap(contract -> rejectionSignal(method, contract.method()))
                    .or(() -> externalOverrideRejection(method, inheritedContracts.isEmpty()));
            if (rejection.isEmpty()) {
                continue;
            }
            if (inherited.isPresent()) {
                rejectedOverrides.add(new RejectedOverride(method, inherited.get(), rejection.get(), true));
            } else if (method.overrideAnnotation()) {
                rejectedOverrides.add(new RejectedOverride(method, null, rejection.get(), false));
            }
        }

        int inheritedContractCount = inheritedContracts.isEmpty() ? behavioralMethods.size() : inheritedContracts.size();
        double rejectedRatio = (double) rejectedOverrides.size() / Math.max(1, inheritedContractCount);
        long strongRejections = rejectedOverrides.stream()
                .filter(rejection -> "strong".equals(rejection.signal().strength()))
                .count();
        if (strongRejections == 0 && !(rejectedOverrides.size() >= 2 && rejectedRatio >= 0.5)) {
            return Optional.empty();
        }
        if (rejectedOverrides.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RefusedBequestCandidate(
                classInfo,
                rejectedOverrides.stream()
                        .sorted(Comparator.comparing(rejection -> rejection.method().name()))
                        .toList(),
                inheritedContracts.size(),
                behavioralMethods.size(),
                rejectedRatio,
                strongRejections
        ));
    }

    private static Optional<RejectionSignal> externalOverrideRejection(
            JavaMethodInfo method,
            boolean noResolvedContracts
    ) {
        if (!noResolvedContracts || !method.overrideAnnotation()) {
            return Optional.empty();
        }
        Optional<RejectionSignal> signal = rejectionSignal(method, null);
        return signal.filter(rejection -> "strong".equals(rejection.strength()));
    }

    private static Optional<RejectionSignal> rejectionSignal(JavaMethodInfo method, JavaMethodInfo inheritedMethod) {
        String body = method.normalizedBody();
        String lowerBody = body.toLowerCase(Locale.ROOT);
        String thrownTypes = method.thrownTypes().stream()
                .map(type -> type.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        if (method.throwsUnsupportedOperation()
                || thrownTypes.contains("unsupportedoperationexception")
                || thrownTypes.contains("notimplementedexception")
                || lowerBody.contains("not supported")
                || lowerBody.contains("not implemented")
                || lowerBody.contains("unsupported")) {
            return Optional.of(new RejectionSignal("unsupported_or_not_implemented_throw", "strong"));
        }
        if (emptyBody(body) && inheritedMethod != null && !emptyBody(inheritedMethod.normalizedBody())
                && !hookLike(method.name())) {
            return Optional.of(new RejectionSignal("empty_override", "medium"));
        }
        if (defaultValueReturn(body) && inheritedMethod != null && !sameNormalizedBody(method, inheritedMethod)) {
            return Optional.of(new RejectionSignal("default_value_return", "medium"));
        }
        if (emptyCollectionReturn(lowerBody) && inheritedMethod != null && !sameNormalizedBody(method, inheritedMethod)) {
            return Optional.of(new RejectionSignal("empty_collection_return", "medium"));
        }
        return Optional.empty();
    }

    private static List<InheritedContract> inheritedContracts(
            JavaClassInfo classInfo,
            Map<String, JavaClassInfo> classesByName
    ) {
        List<InheritedContract> contracts = new ArrayList<>();
        LinkedHashSet<String> directParents = new LinkedHashSet<>();
        if (classInfo.extendsName() != null) {
            directParents.add(classInfo.extendsName());
        }
        directParents.addAll(classInfo.implementsNames());
        for (String parentName : directParents) {
            collectContracts(parentName, classesByName, new HashSet<>(), contracts);
        }
        return contracts;
    }

    private static void collectContracts(
            String parentName,
            Map<String, JavaClassInfo> classesByName,
            Set<String> visited,
            List<InheritedContract> contracts
    ) {
        String simpleName = simpleTypeName(parentName);
        if (!visited.add(simpleName)) {
            return;
        }
        JavaClassInfo parent = classesByName.get(simpleName);
        if (parent == null) {
            return;
        }
        parent.methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .map(method -> new InheritedContract(parent.name(), method))
                .forEach(contracts::add);
        if (parent.extendsName() != null) {
            collectContracts(parent.extendsName(), classesByName, visited, contracts);
        }
        parent.implementsNames().forEach(interfaceName ->
                collectContracts(interfaceName, classesByName, visited, contracts));
    }

    private static Map<String, JavaClassInfo> classesByName(List<JavaClassInfo> classes) {
        Map<String, JavaClassInfo> classesByName = new LinkedHashMap<>();
        for (JavaClassInfo classInfo : classes) {
            classesByName.putIfAbsent(simpleTypeName(classInfo.name()), classInfo);
        }
        return classesByName;
    }

    private static boolean hasInheritance(JavaClassInfo classInfo) {
        return classInfo.extendsName() != null || !classInfo.implementsNames().isEmpty();
    }

    private static boolean emptyBody(String body) {
        return body.matches("\\{\\s*\\}");
    }

    private static boolean defaultValueReturn(String body) {
        return body.matches("\\{\\s*return\\s+(null|false|0);?\\s*\\}");
    }

    private static boolean emptyCollectionReturn(String lowerBody) {
        return lowerBody.matches("\\{\\s*return\\s+.*(emptylist|emptyset|emptymap|list\\.of\\(\\)|set\\.of\\(\\)|map\\.of\\(\\)).*;?\\s*\\}");
    }

    private static boolean sameNormalizedBody(JavaMethodInfo left, JavaMethodInfo right) {
        return left.normalizedBody().equals(right.normalizedBody());
    }

    private static boolean hookLike(String methodName) {
        List<String> parts = splitWords(methodName);
        return parts.stream().anyMatch(HOOK_METHOD_NAME_PARTS::contains);
    }

    private static String signature(JavaMethodInfo method) {
        return method.name() + "(" + method.parameterTypes().stream()
                .map(RefusedBequestBadSmellDetector::normalizedType)
                .collect(Collectors.joining(",")) + ")";
    }

    private static String looseSignature(JavaMethodInfo method) {
        return method.name() + "/" + method.parameterTypes().size();
    }

    private static String normalizedType(String type) {
        String simpleName = simpleTypeName(type).replace("...", "[]");
        return simpleName.replaceAll("\\[\\]", "[]").toLowerCase(Locale.ROOT);
    }

    private static String simpleTypeName(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String normalized = type.replace("java.lang.", "").replaceAll("\\s+", "");
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized;
    }

    private static List<String> splitWords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
        return spaced.isBlank() ? List.of() : List.of(spaced.split("\\s+"));
    }

    private record InheritedContract(String owner, JavaMethodInfo method) {
    }

    private record RejectionSignal(String kind, String strength) {
    }

    private record RejectedOverride(
            JavaMethodInfo method,
            InheritedContract inheritedContract,
            RejectionSignal signal,
            boolean verified
    ) {
        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "method", method.name(),
                    "signature", signature(method),
                    "inherited_from", inheritedContract == null ? null : inheritedContract.owner(),
                    "verified_override", verified,
                    "rejection_kind", signal.kind(),
                    "strength", signal.strength(),
                    "line", method.startLine()
            );
        }
    }

    private record ContractIndex(
            Map<String, InheritedContract> exactContracts,
            Map<String, List<InheritedContract>> looseContracts
    ) {
        static ContractIndex from(List<InheritedContract> contracts) {
            Map<String, InheritedContract> exactContracts = new LinkedHashMap<>();
            Map<String, List<InheritedContract>> looseContracts = new LinkedHashMap<>();
            for (InheritedContract contract : contracts) {
                exactContracts.putIfAbsent(signature(contract.method()), contract);
                looseContracts.computeIfAbsent(looseSignature(contract.method()), ignored -> new ArrayList<>())
                        .add(contract);
            }
            return new ContractIndex(exactContracts, looseContracts);
        }

        Optional<InheritedContract> match(JavaMethodInfo method) {
            InheritedContract exact = exactContracts.get(signature(method));
            if (exact != null) {
                return Optional.of(exact);
            }
            List<InheritedContract> loose = looseContracts.getOrDefault(looseSignature(method), List.of());
            return loose.size() == 1 ? Optional.of(loose.getFirst()) : Optional.empty();
        }
    }

    private record RefusedBequestCandidate(
            JavaClassInfo classInfo,
            List<RejectedOverride> rejectedOverrides,
            int inheritedContractCount,
            int behavioralMethodCount,
            double rejectedRatio,
            long strongRejections
    ) {
        String severity() {
            return strongRejections >= 2 || rejectedRatio >= 0.5 ? "high" : "medium";
        }

        String confidence() {
            long verifiedCount = rejectedOverrides.stream().filter(RejectedOverride::verified).count();
            return verifiedCount == rejectedOverrides.size()
                    && (rejectedOverrides.size() >= 2 || rejectedRatio >= 0.4)
                    ? "high"
                    : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", rejectedOverrides.stream().allMatch(RejectedOverride::verified)
                            ? "rejected_inherited_contract"
                            : "explicit_override_rejects_unresolved_contract",
                    "extends", classInfo.extendsName(),
                    "implements", List.copyOf(classInfo.implementsNames()),
                    "rejected_methods", rejectedOverrides.stream().map(rejection -> rejection.method().name()).toList(),
                    "rejected_method_count", rejectedOverrides.size(),
                    "behavioral_method_count", behavioralMethodCount,
                    "inherited_contract_count", inheritedContractCount,
                    "verified_override_count", rejectedOverrides.stream().filter(RejectedOverride::verified).count(),
                    "unresolved_override_count", rejectedOverrides.stream().filter(rejection -> !rejection.verified()).count(),
                    "strong_rejection_count", strongRejections,
                    "rejected_ratio", rejectedRatio,
                    "rejected_overrides", rejectedOverrides.stream().map(RejectedOverride::toJson).toList()
            );
        }
    }
}
