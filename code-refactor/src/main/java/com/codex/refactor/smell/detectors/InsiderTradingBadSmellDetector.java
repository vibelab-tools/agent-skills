package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.MessageChainInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class InsiderTradingBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> INTERNAL_DETAIL_TOKENS = Set.of(
            "internal", "private", "secret", "token", "credential", "credentials",
            "password", "passwd", "raw", "impl", "implementation", "delegate",
            "flags", "bitmask", "state", "cache", "backing", "hidden"
    );
    private static final Set<String> COLLABORATION_ROLE_SUFFIXES = Set.of(
            "Mapper", "Assembler", "Serializer", "Deserializer", "Formatter", "Translator",
            "Converter", "Adapter", "Presenter", "Renderer", "Projector", "View"
    );
    private static final Set<String> COLLABORATION_METHOD_PREFIXES = Set.of(
            "map", "assemble", "serialize", "deserialize", "format", "translate",
            "convert", "adapt", "present", "render", "project", "toDto", "toDomain"
    );

    public InsiderTradingBadSmellDetector() {
        super(BadSmell.INSIDER_TRADING);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        context.analysis().methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .map(method -> candidate(method, context.projectIndex()))
                .flatMap(Optional::stream)
                .forEach(candidate -> findings.add(finding(candidate)));
        if (!findings.isEmpty()) {
            return findings;
        }
        if ("java".equals(context.analysis().language()) && !context.analysis().methods().isEmpty()) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(InsiderTradingCandidate candidate) {
        return DetectorSupport.finding(
                smell(),
                candidate.severity(),
                candidate.confidence(),
                candidate.method().name(),
                candidate.method().startLine(),
                candidate.method().endLine(),
                candidate.evidence(),
                "Method appears to know too much about collaborators' internal object structure.",
                "Move behavior toward the collaborating owner, hide the data traversal behind intention-revealing methods, or introduce a narrower facade."
        );
    }

    private static Optional<InsiderTradingCandidate> candidate(JavaMethodInfo method, SourceProjectIndex projectIndex) {
        List<MessageChainInfo> chains = relevantChains(method);
        if (chains.isEmpty()) {
            return Optional.empty();
        }

        Map<String, CollaboratorAccess> collaborators = new LinkedHashMap<>();
        for (MessageChainInfo chain : chains) {
            collaborators.computeIfAbsent(chain.root(), root ->
                    new CollaboratorAccess(root, method.variableTypes().getOrDefault(root, "unknown")))
                    .add(chain);
        }
        collaborators.values().forEach(collaborator ->
                enrichCollaborator(method, collaborator, projectIndex));

        int accessCount = chains.size();
        int collaboratorCount = collaborators.size();
        int maxDepth = chains.stream().mapToInt(MessageChainInfo::depth).max().orElse(0);
        int internalSelectorCount = chains.stream()
                .mapToInt(InsiderTradingBadSmellDetector::internalSelectorCount)
                .sum();
        int knownTypeCount = (int) collaborators.values().stream()
                .filter(CollaboratorAccess::knownProjectType)
                .count();
        int reciprocalAccessCount = collaborators.values().stream()
                .mapToInt(CollaboratorAccess::reciprocalAccessCount)
                .sum();
        int resolvedCallCount = collaborators.values().stream()
                .mapToInt(CollaboratorAccess::resolvedCallCount)
                .sum();
        boolean roleAdjusted = collaborationRole(method);
        int score = score(
                accessCount,
                collaboratorCount,
                maxDepth,
                internalSelectorCount,
                collaborators.values().stream().mapToInt(CollaboratorAccess::accessCount).max().orElse(0),
                knownTypeCount,
                reciprocalAccessCount,
                resolvedCallCount,
                roleAdjusted,
                method.simpleDelegation()
        );

        String signal = signal(accessCount, collaboratorCount, maxDepth, internalSelectorCount, reciprocalAccessCount, knownTypeCount);
        if (signal.isBlank() || score < 4) {
            return Optional.empty();
        }
        return Optional.of(new InsiderTradingCandidate(
                method,
                signal,
                chains,
                List.copyOf(collaborators.values()),
                accessCount,
                collaboratorCount,
                maxDepth,
                internalSelectorCount,
                knownTypeCount,
                reciprocalAccessCount,
                resolvedCallCount,
                roleAdjusted,
                score
        ));
    }

    private static List<MessageChainInfo> relevantChains(JavaMethodInfo method) {
        Map<String, MessageChainInfo> unique = new LinkedHashMap<>();
        method.messageChains().stream()
                .filter(MessageChainInfo::objectNavigation)
                .filter(chain -> chain.depth() >= 3)
                .filter(chain -> method.parameterNames().contains(chain.root())
                        || method.localVariables().contains(chain.root())
                        || method.variableTypes().containsKey(chain.root()))
                .forEach(chain -> unique.merge(
                        chain.chainText(),
                        chain,
                        (existing, replacement) -> existing.depth() >= replacement.depth() ? existing : replacement
                ));
        return unique.values().stream()
                .sorted(Comparator.comparingInt(MessageChainInfo::line)
                        .thenComparing(MessageChainInfo::chainText))
                .toList();
    }

    private static int score(
            int accessCount,
            int collaboratorCount,
            int maxDepth,
            int internalSelectorCount,
            int dominantCollaboratorAccesses,
            int knownTypeCount,
            int reciprocalAccessCount,
            int resolvedCallCount,
            boolean roleAdjusted,
            boolean simpleDelegation
    ) {
        int score = 0;
        score += collaboratorCount >= 2 && accessCount >= 4 ? 2 : 0;
        score += maxDepth >= 5 ? 2 : maxDepth >= 4 ? 1 : 0;
        score += internalSelectorCount > 0 ? 3 : 0;
        score += dominantCollaboratorAccesses >= 3 ? 1 : 0;
        score += knownTypeCount > 0 && accessCount >= 2 ? 1 : 0;
        score += reciprocalAccessCount > 0 ? 2 : 0;
        score += resolvedCallCount >= 2 && reciprocalAccessCount > 0 ? 1 : 0;
        score -= roleAdjusted && internalSelectorCount == 0 ? 3 : 0;
        score -= simpleDelegation ? 2 : 0;
        return score;
    }

    private static String signal(
            int accessCount,
            int collaboratorCount,
            int maxDepth,
            int internalSelectorCount,
            int reciprocalAccessCount,
            int knownTypeCount
    ) {
        if (internalSelectorCount > 0 && maxDepth >= 3) {
            return "internal_named_collaborator_access";
        }
        if (reciprocalAccessCount > 0 && maxDepth >= 3) {
            return "bidirectional_intimate_collaborator_access";
        }
        if (collaboratorCount >= 2 && accessCount >= 4 && maxDepth >= 3) {
            return "multi_collaborator_intimate_access";
        }
        if (knownTypeCount > 0 && accessCount >= 3 && maxDepth >= 4) {
            return "typed_collaborator_structure_access";
        }
        if (maxDepth >= 4 && accessCount >= 3) {
            return "deep_collaborator_structure_access";
        }
        return "";
    }

    private static void enrichCollaborator(
            JavaMethodInfo method,
            CollaboratorAccess collaborator,
            SourceProjectIndex projectIndex
    ) {
        String collaboratorType = SourceProjectIndex.simpleTypeName(collaborator.type);
        if (collaboratorType.isBlank()) {
            return;
        }
        collaborator.knownProjectType = projectIndex.containsClass(collaboratorType);
        collaborator.resolvedCallCount = (int) projectIndex.callEdgesFrom(method).stream()
                .filter(edge -> collaboratorType.equals(SourceProjectIndex.simpleTypeName(edge.targetOwner()))
                        || collaboratorType.equals(SourceProjectIndex.simpleTypeName(edge.call().receiverType())))
                .count();

        String ownerType = SourceProjectIndex.simpleTypeName(method.ownerClass());
        projectIndex.allMethods().stream()
                .map(SourceProjectIndex.MethodEntry::method)
                .filter(other -> collaboratorType.equals(SourceProjectIndex.simpleTypeName(other.ownerClass())))
                .filter(other -> !other.name().equals(method.name()) || !other.ownerClass().equals(method.ownerClass()))
                .forEach(other -> {
                    int reverseCount = (int) relevantChains(other).stream()
                            .filter(chain -> ownerType.equals(SourceProjectIndex.simpleTypeName(other.variableTypes().get(chain.root()))))
                            .count();
                    if (reverseCount > 0) {
                        collaborator.reciprocalAccessCount += reverseCount;
                        collaborator.reciprocalMethods.add(other.name());
                    }
                });
    }

    private static boolean collaborationRole(JavaMethodInfo method) {
        boolean roleOwner = COLLABORATION_ROLE_SUFFIXES.stream().anyMatch(method.ownerClass()::endsWith);
        boolean roleMethod = COLLABORATION_METHOD_PREFIXES.stream().anyMatch(method.name()::startsWith);
        return roleOwner || roleMethod;
    }

    private static int internalSelectorCount(MessageChainInfo chain) {
        return (int) chain.selectors().stream()
                .flatMap(selector -> splitWords(selector).stream())
                .filter(INTERNAL_DETAIL_TOKENS::contains)
                .count();
    }

    private static List<String> splitWords(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(String::toLowerCase)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static List<Map<String, Object>> representativeChains(List<MessageChainInfo> chains) {
        return chains.stream()
                .sorted(Comparator.comparingInt(MessageChainInfo::depth).reversed()
                        .thenComparing(MessageChainInfo::line)
                        .thenComparing(MessageChainInfo::chainText))
                .limit(5)
                .map(MessageChainInfo::toJson)
                .toList();
    }

    private static final class CollaboratorAccess {
        private final String root;
        private final String type;
        private final List<MessageChainInfo> chains = new ArrayList<>();
        private boolean knownProjectType;
        private int resolvedCallCount;
        private int reciprocalAccessCount;
        private final Set<String> reciprocalMethods = new LinkedHashSet<>();

        private CollaboratorAccess(String root, String type) {
            this.root = root;
            this.type = type;
        }

        private void add(MessageChainInfo chain) {
            chains.add(chain);
        }

        private int accessCount() {
            return chains.size();
        }

        private int maxDepth() {
            return chains.stream().mapToInt(MessageChainInfo::depth).max().orElse(0);
        }

        private int internalSelectorCount() {
            return chains.stream().mapToInt(InsiderTradingBadSmellDetector::internalSelectorCount).sum();
        }

        private boolean knownProjectType() {
            return knownProjectType;
        }

        private int resolvedCallCount() {
            return resolvedCallCount;
        }

        private int reciprocalAccessCount() {
            return reciprocalAccessCount;
        }

        private Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "root", root,
                    "type", type,
                    "known_project_type", knownProjectType,
                    "access_count", accessCount(),
                    "max_depth", maxDepth(),
                    "internal_selector_count", internalSelectorCount(),
                    "resolved_call_count", resolvedCallCount,
                    "reciprocal_access_count", reciprocalAccessCount,
                    "reciprocal_methods", List.copyOf(reciprocalMethods),
                    "lines", chains.stream().map(MessageChainInfo::line).collect(Collectors.toCollection(LinkedHashSet::new))
            );
        }
    }

    private record InsiderTradingCandidate(
            JavaMethodInfo method,
            String signal,
            List<MessageChainInfo> chains,
            List<CollaboratorAccess> collaborators,
            int accessCount,
            int collaboratorCount,
            int maxDepth,
            int internalSelectorCount,
            int knownTypeCount,
            int reciprocalAccessCount,
            int resolvedCallCount,
            boolean roleAdjusted,
            int score
    ) {
        String severity() {
            return score >= 6 || internalSelectorCount > 0 ? "high" : "medium";
        }

        String confidence() {
            return score >= 6 || internalSelectorCount > 0 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", signal,
                    "foreign_member_accesses", accessCount,
                    "foreign_roots", collaborators.stream().map(collaborator -> collaborator.root).toList(),
                    "collaborator_count", collaboratorCount,
                    "max_message_chain_depth", maxDepth,
                    "internal_selector_count", internalSelectorCount,
                    "known_project_type_count", knownTypeCount,
                    "reciprocal_access_count", reciprocalAccessCount,
                    "resolved_call_count", resolvedCallCount,
                    "collaboration_role_adjustment", roleAdjusted,
                    "insider_trading_score", score,
                    "collaborators", collaborators.stream().map(CollaboratorAccess::toJson).toList(),
                    "chains", representativeChains(chains),
                    "chain_lines", chains.stream().map(MessageChainInfo::line).distinct().toList()
            );
        }
    }
}
