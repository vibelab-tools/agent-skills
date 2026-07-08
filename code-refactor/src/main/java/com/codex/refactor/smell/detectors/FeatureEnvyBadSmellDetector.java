package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.MessageChainInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FeatureEnvyBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> COLLABORATION_ROLE_SUFFIXES = Set.of(
            "Formatter", "Mapper", "Converter", "Translator", "Serializer", "Deserializer",
            "Renderer", "Presenter", "View", "Assembler", "Adapter"
    );
    private static final Set<String> COLLABORATION_METHOD_PREFIXES = Set.of(
            "format", "map", "convert", "translate", "serialize", "deserialize", "render", "present", "adapt"
    );

    public FeatureEnvyBadSmellDetector() {
        super(BadSmell.FEATURE_ENVY);
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
                .map(method -> candidate(method, context))
                .flatMap(Optional::stream)
                .filter(FeatureEnvyCandidate::reportable)
                .forEach(candidate -> findings.add(DetectorSupport.finding(
                        smell(),
                        candidate.severity(),
                        candidate.confidence(),
                        candidate.method().name(),
                        candidate.method().startLine(),
                        candidate.method().endLine(),
                        candidate.evidence(),
                        "Method is dominated by access to one external object and uses little data from its own owner.",
                        "Consider Move Function toward the envied data owner, or extract a collaboration that exposes behavior instead of data."
                )));
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static Optional<FeatureEnvyCandidate> candidate(JavaMethodInfo method, SmellAnalysisContext context) {
        Map<String, Integer> foreignAccesses = filteredForeignAccesses(method);
        if (foreignAccesses.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<String, Integer> dominant = foreignAccesses.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .orElseThrow();
        int foreignTotal = foreignAccesses.values().stream().mapToInt(Integer::intValue).sum();
        int ownReadAccesses = method.ownFieldReadCounts().values().stream().mapToInt(Integer::intValue).sum();
        int ownWriteAccesses = method.ownFieldWriteCounts().values().stream().mapToInt(Integer::intValue).sum();
        int ownDataAccesses = ownReadAccesses + ownWriteAccesses;
        double dominantRatio = (double) dominant.getValue() / Math.max(1, foreignTotal);
        List<Integer> accessLines = method.foreignMemberAccessLines()
                .getOrDefault(dominant.getKey(), Set.of())
                .stream()
                .sorted()
                .toList();
        ForeignTypeEvidence foreignTypeEvidence = ForeignTypeEvidence.from(method, dominant.getKey(), context.projectIndex());
        boolean collaborationRole = collaborationRole(method);
        int score = envyScore(
                dominant.getValue(),
                dominantRatio,
                ownDataAccesses,
                accessLines.size(),
                method.maxMessageChainDepth(),
                collaborationRole,
                method.simpleDelegation(),
                foreignTypeEvidence
        );
        return Optional.of(new FeatureEnvyCandidate(
                method,
                dominant.getKey(),
                dominant.getValue(),
                foreignAccesses,
                foreignTotal,
                dominantRatio,
                accessLines,
                ownReadAccesses,
                ownWriteAccesses,
                score,
                collaborationRole,
                foreignTypeEvidence
        ));
    }

    private static Map<String, Integer> filteredForeignAccesses(JavaMethodInfo method) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        method.foreignMemberAccessCounts().forEach((root, count) -> {
            if (foreignInstanceRoot(method, root)) {
                filtered.put(root, count);
            }
        });
        return filtered;
    }

    private static boolean foreignInstanceRoot(JavaMethodInfo method, String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        if ("this".equals(root) || "super".equals(root) || root.equals(method.ownerClass())) {
            return false;
        }
        boolean knownVariable = method.variableTypes().containsKey(root)
                || method.localVariables().contains(root)
                || method.parameterNames().contains(root);
        return knownVariable || !Character.isUpperCase(root.charAt(0));
    }

    private static int envyScore(
            int dominantAccesses,
            double dominantRatio,
            int ownDataAccesses,
            int accessLineCount,
            int maxMessageChainDepth,
            boolean collaborationRole,
            boolean simpleDelegation,
            ForeignTypeEvidence foreignTypeEvidence
    ) {
        int score = 0;
        score += dominantAccesses >= 6 ? 2 : dominantAccesses >= 4 ? 1 : 0;
        score += ownDataAccesses == 0 ? 2 : dominantAccesses >= ownDataAccesses + 4 ? 1 : 0;
        score += dominantRatio >= 0.70 ? 1 : 0;
        score += maxMessageChainDepth >= 3 ? 1 : 0;
        score += accessLineCount >= 3 ? 1 : 0;
        score += foreignTypeEvidence.strongMatch() ? 1 : 0;
        score -= foreignTypeEvidence.resolved() && foreignTypeEvidence.matchCount() == 0 ? 2 : 0;
        score -= collaborationRole ? 2 : 0;
        score -= simpleDelegation ? 2 : 0;
        return score;
    }

    private static boolean collaborationRole(JavaMethodInfo method) {
        boolean roleOwner = COLLABORATION_ROLE_SUFFIXES.stream().anyMatch(method.ownerClass()::endsWith);
        boolean roleMethod = COLLABORATION_METHOD_PREFIXES.stream().anyMatch(prefix -> method.name().startsWith(prefix));
        return roleOwner && roleMethod;
    }

    private record FeatureEnvyCandidate(
            JavaMethodInfo method,
            String foreignRoot,
            int foreignAccesses,
            Map<String, Integer> allForeignAccesses,
            int foreignTotal,
            double foreignAccessRatio,
            List<Integer> foreignAccessLines,
            int ownFieldReadAccesses,
            int ownFieldWriteAccesses,
            int envyScore,
            boolean collaborationRole,
            ForeignTypeEvidence foreignTypeEvidence
    ) {
        boolean reportable() {
            int ownDataAccesses = ownFieldReadAccesses + ownFieldWriteAccesses;
            boolean dominantForeignData = foreignAccesses >= 4
                    && foreignAccessRatio >= 0.55
                    && foreignAccesses >= ownDataAccesses + 3
                    && (foreignAccessLines.size() >= 2 || method.maxMessageChainDepth() >= 4)
                    && envyScore >= 4;
            boolean resolvedCompactEnvy = foreignTypeEvidence.strongMatch()
                    && foreignAccesses >= 3
                    && foreignAccessRatio >= 0.55
                    && foreignAccesses >= ownDataAccesses + 2
                    && (foreignAccessLines.size() >= 2 || method.maxMessageChainDepth() >= 3)
                    && envyScore >= 4;
            return (dominantForeignData || resolvedCompactEnvy)
                    && foreignAccessRatio >= 0.55
                    && !method.simpleDelegation();
        }

        String severity() {
            return envyScore >= 6 && ownFieldReadAccesses + ownFieldWriteAccesses == 0 ? "high" : "medium";
        }

        String confidence() {
            return envyScore >= 5 || foreignTypeEvidence.strongMatch() ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "foreign_root", foreignRoot,
                    "foreign_root_type", method.variableTypes().getOrDefault(foreignRoot, "unknown"),
                    "foreign_accesses", foreignAccesses,
                    "foreign_total_accesses", foreignTotal,
                    "foreign_access_ratio", foreignAccessRatio,
                    "foreign_access_lines", foreignAccessLines,
                    "all_foreign_accesses", allForeignAccesses,
                    "own_field_reads", method.ownFieldReads().size(),
                    "own_field_writes", method.ownFieldWrites().size(),
                    "own_field_read_accesses", ownFieldReadAccesses,
                    "own_field_write_accesses", ownFieldWriteAccesses,
                    "own_data_accesses", ownFieldReadAccesses + ownFieldWriteAccesses,
                    "max_message_chain_depth", method.maxMessageChainDepth(),
                    "collaboration_role_adjustment", collaborationRole,
                    "resolved_foreign_type", foreignTypeEvidence.resolvedType(),
                    "resolved_foreign_type_path", foreignTypeEvidence.path().map(Path::toString).orElse(""),
                    "foreign_root_members_accessed", foreignTypeEvidence.accessedRootMembers(),
                    "matching_foreign_members", foreignTypeEvidence.matchingMembers(),
                    "matching_foreign_member_count", foreignTypeEvidence.matchCount(),
                    "known_foreign_member_count", foreignTypeEvidence.knownMemberCount(),
                    "foreign_member_match_ratio", foreignTypeEvidence.matchRatio(),
                    "envy_score", envyScore
            );
        }
    }

    private record ForeignTypeEvidence(
            String resolvedType,
            Optional<Path> path,
            Set<String> accessedRootMembers,
            Set<String> matchingMembers,
            int knownMemberCount
    ) {
        static ForeignTypeEvidence from(JavaMethodInfo method, String foreignRoot, SourceProjectIndex projectIndex) {
            String typeName = SourceProjectIndex.simpleTypeName(method.variableTypes().get(foreignRoot));
            if (typeName.isBlank()) {
                return unresolved(rootMembers(method, foreignRoot));
            }
            Optional<JavaClassInfo> classInfo = projectIndex.classByName(typeName);
            if (classInfo.isEmpty()) {
                return unresolved(rootMembers(method, foreignRoot));
            }
            Set<String> accessedMembers = rootMembers(method, foreignRoot);
            Set<String> knownMembers = knownMembers(classInfo.get());
            Set<String> matchingMembers = new LinkedHashSet<>(accessedMembers);
            matchingMembers.retainAll(knownMembers);
            return new ForeignTypeEvidence(
                    classInfo.get().name(),
                    projectIndex.pathForClass(typeName),
                    accessedMembers,
                    matchingMembers,
                    knownMembers.size()
            );
        }

        private static ForeignTypeEvidence unresolved(Set<String> accessedMembers) {
            return new ForeignTypeEvidence("", Optional.empty(), accessedMembers, Set.of(), 0);
        }

        boolean resolved() {
            return !resolvedType.isBlank();
        }

        int matchCount() {
            return matchingMembers.size();
        }

        double matchRatio() {
            return accessedRootMembers.isEmpty() ? 0.0 : (double) matchCount() / accessedRootMembers.size();
        }

        boolean strongMatch() {
            return resolved() && matchCount() >= 3 && matchRatio() >= 0.60;
        }

        private static Set<String> rootMembers(JavaMethodInfo method, String foreignRoot) {
            Set<String> members = new LinkedHashSet<>();
            method.messageChains().stream()
                    .filter(MessageChainInfo::objectNavigation)
                    .filter(chain -> foreignRoot.equals(chain.root()))
                    .map(MessageChainInfo::selectors)
                    .filter(selectors -> !selectors.isEmpty())
                    .map(List::getFirst)
                    .forEach(members::add);
            return members;
        }

        private static Set<String> knownMembers(JavaClassInfo classInfo) {
            Set<String> members = new LinkedHashSet<>();
            classInfo.fields().stream()
                    .map(field -> SourceProjectIndex.simpleMethodName(field.name()))
                    .filter(name -> !name.isBlank())
                    .forEach(members::add);
            classInfo.methods().stream()
                    .filter(method -> !method.constructor())
                    .map(method -> SourceProjectIndex.simpleMethodName(method.name()))
                    .filter(name -> !name.isBlank())
                    .forEach(members::add);
            return members;
        }
    }
}
