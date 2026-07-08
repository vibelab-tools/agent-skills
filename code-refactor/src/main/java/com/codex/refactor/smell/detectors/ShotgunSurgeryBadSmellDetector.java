package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;
import com.codex.refactor.history.ChangedSymbol;
import com.codex.refactor.history.ShotgunSurgeryHistoryEvidence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShotgunSurgeryBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> CHANGE_VERBS = Set.of(
            "refresh", "update", "sync", "invalidate", "migrate", "configure", "recalculate",
            "rebuild", "reload", "reset", "register", "apply", "save", "persist"
    );
    private static final Set<String> COMMON_METHODS = Set.of(
            "toString", "equals", "hashCode", "compareTo", "clone", "close", "run", "main"
    );

    public ShotgunSurgeryBadSmellDetector() {
        super(BadSmell.SHOTGUN_SURGERY);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SourceProjectIndex.MethodEntry> indexedMethods = context.projectIndex().allMethods().isEmpty()
                ? context.analysis().methods().stream()
                .map(method -> new SourceProjectIndex.MethodEntry(context.analysis().path(), context.analysis().language(), method))
                .toList()
                : context.projectIndex().allMethods();
        Map<String, List<SourceProjectIndex.MethodEntry>> methodsByChange = new LinkedHashMap<>();
        indexedMethods.stream()
                .filter(entry -> !entry.method().constructor())
                .filter(entry -> !entry.method().accessorMethod())
                .filter(entry -> !COMMON_METHODS.contains(entry.method().name()))
                .filter(entry -> changeLike(entry.method().name()))
                .forEach(entry -> methodsByChange
                        .computeIfAbsent(normalizedChangeKey(entry.method()), ignored -> new ArrayList<>())
                        .add(entry));

        List<SmellFinding> findings = new ArrayList<>();
        List<ShotgunSurgeryHistoryEvidence> historyEvidences = context.historyAnalysis()
                .shotgunSurgeryFor(context.analysis().path());
        Set<String> emittedHistoryKeys = new HashSet<>();
        methodsByChange.forEach((changeKey, entries) -> {
            List<SourceProjectIndex.MethodEntry> currentFileEntries = entries.stream()
                    .filter(entry -> entry.path().equals(context.analysis().path()))
                    .toList();
            if (currentFileEntries.isEmpty()) {
                return;
            }
            List<String> distinctOwners = entries.stream()
                    .map(entry -> entry.method().ownerClass())
                    .distinct()
                    .toList();
            List<String> distinctFiles = entries.stream()
                    .map(entry -> entry.path().toString())
                    .distinct()
                    .toList();
            if (distinctOwners.size() >= 3) {
                ShotgunSurgeryHistoryEvidence historyEvidence = historyEvidences.stream()
                        .filter(evidence -> evidence.changeKey().equals(historyChangeKey(currentFileEntries.getFirst().method())))
                        .findFirst()
                        .orElse(null);
                if (historyEvidence != null) {
                    emittedHistoryKeys.add(historyEvidence.changeKey());
                }
                String representative = currentFileEntries.getFirst().method().name();
                findings.add(DetectorSupport.finding(
                        smell(),
                        historyEvidence == null ? "medium" : "high",
                        historyEvidence == null ? "medium" : "high",
                        representative,
                        currentFileEntries.stream().mapToInt(entry -> entry.method().startLine()).min().orElse(1),
                        currentFileEntries.stream().mapToInt(entry -> entry.method().endLine()).max().orElse(1),
                        mergeEvidence(DetectorSupport.evidence(
                                "change_key", changeKey,
                                "method_names", entries.stream().map(entry -> entry.method().name()).distinct().toList(),
                                "current_file_methods", currentFileEntries.stream().map(entry -> entry.method().name()).distinct().toList(),
                                "owners", distinctOwners,
                                "owner_count", distinctOwners.size(),
                                "files", distinctFiles,
                                "file_count", distinctFiles.size()
                        ), historyEvidence),
                        historyEvidence == null
                                ? "Same change-like operation is scattered across several owners."
                                : "Same change-like operation is scattered across several owners and Git history shows these owners changing together.",
                        historyEvidence == null
                                ? "Look for a missing abstraction or Move Function target that localizes this recurring change."
                                : "Use the history-confirmed cluster to look for a missing abstraction or Move Function target that localizes this recurring change."
                ));
            }
        });
        historyEvidences.stream()
                .filter(evidence -> !emittedHistoryKeys.contains(evidence.changeKey()))
                .map(evidence -> historyFinding(context, evidence))
                .forEach(findings::add);
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static String normalizedChangeKey(JavaMethodInfo method) {
        return canonicalChangeKey(method.name(), method.parameterTypes().size());
    }

    private static boolean changeLike(String methodName) {
        String normalized = methodName.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .toLowerCase();
        for (String token : normalized.split("\\s+")) {
            if (CHANGE_VERBS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String historyChangeKey(JavaMethodInfo method) {
        return canonicalChangeKey(method.name(), method.parameterTypes().size());
    }

    private static String canonicalChangeKey(String methodName, int parameterCount) {
        String normalized = methodName.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .toLowerCase();
        List<String> tokens = java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
        String verb = tokens.stream().filter(CHANGE_VERBS::contains).findFirst().orElse(null);
        if (verb == null) {
            return methodName.replaceAll("\\d+$", "").toLowerCase() + "/" + parameterCount;
        }
        String family = switch (verb) {
            case "reload", "invalidate" -> "refresh";
            case "sync", "apply" -> "update";
            case "save" -> "persist";
            case "recalculate", "rebuild" -> "calculate";
            default -> verb;
        };
        List<String> objectTokens = tokens.stream()
                .filter(token -> !CHANGE_VERBS.contains(token))
                .toList();
        String object = objectTokens.isEmpty() ? "operation" : String.join("_", objectTokens);
        return family + "_" + object + "/" + parameterCount;
    }

    private static Map<String, Object> mergeEvidence(
            Map<String, Object> staticEvidence,
            ShotgunSurgeryHistoryEvidence historyEvidence
    ) {
        if (historyEvidence == null) {
            return staticEvidence;
        }
        Map<String, Object> merged = new LinkedHashMap<>(staticEvidence);
        merged.put("history", historyEvidence.toJsonEvidence());
        return merged;
    }

    private SmellFinding historyFinding(
            SmellAnalysisContext context,
            ShotgunSurgeryHistoryEvidence evidence
    ) {
        String relativePath = context.historyAnalysis().relativePath(context.analysis().path()).orElse("");
        ChangedSymbol representative = evidence.representativeFor(relativePath);
        if (representative == null) {
            representative = evidence.symbols().values().stream().findFirst().orElse(null);
        }
        String symbol = representative == null ? evidence.changeKey() : representative.name();
        int startLine = representative == null ? 1 : representative.startLine();
        int endLine = representative == null ? Math.max(1, context.analysis().physicalLines()) : representative.endLine();
        return DetectorSupport.finding(
                smell(),
                "high",
                "high",
                symbol,
                startLine,
                endLine,
                evidence.toJsonEvidence(),
                "Git history shows the same change-like operation repeatedly changing across several owners.",
                "Review this history-confirmed cluster for a missing abstraction or Move Function target."
        );
    }
}
