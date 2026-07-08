package com.codex.refactor.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ShotgunSurgeryHistoryEvidence(
        String changeKey,
        int commitWindow,
        List<String> commits,
        List<String> owners,
        Map<String, ChangedSymbol> symbols,
        Map<String, Integer> symbolChangeCounts
) {
    public int coChangeCommits() {
        return commits.size();
    }

    public int ownerCount() {
        return owners.size();
    }

    public boolean touchesPath(String relativePath) {
        return symbols.values().stream().anyMatch(symbol -> symbol.path().equals(relativePath));
    }

    public ChangedSymbol representativeFor(String relativePath) {
        return symbols.values().stream()
                .filter(symbol -> symbol.path().equals(relativePath))
                .min(Comparator.comparingInt(ChangedSymbol::startLine))
                .orElseGet(() -> symbols.values().stream()
                        .min(Comparator.comparing(ChangedSymbol::symbolKey))
                        .orElse(null));
    }

    public Map<String, Object> toJsonEvidence() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("signal", "history_confirmed");
        json.put("change_key", changeKey);
        json.put("co_change_commits", coChangeCommits());
        json.put("recent_commit_window", commitWindow);
        json.put("owners", owners);
        json.put("owner_count", ownerCount());
        json.put("sample_commits", commits.stream().limit(5).toList());
        json.put("symbols", symbols.values().stream()
                .sorted(Comparator.comparing(ChangedSymbol::symbolKey))
                .map(symbol -> symbol.toJson(symbolChangeCounts.getOrDefault(symbol.symbolKey(), 0)))
                .toList());
        return json;
    }

    public ShotgunSurgeryHistoryEvidence merge(ShotgunSurgeryHistoryEvidence other) {
        List<String> mergedCommits = new ArrayList<>(commits);
        other.commits.stream().filter(commit -> !mergedCommits.contains(commit)).forEach(mergedCommits::add);

        List<String> mergedOwners = new ArrayList<>(owners);
        other.owners.stream().filter(owner -> !mergedOwners.contains(owner)).forEach(mergedOwners::add);

        Map<String, ChangedSymbol> mergedSymbols = new LinkedHashMap<>(symbols);
        mergedSymbols.putAll(other.symbols);

        Map<String, Integer> mergedCounts = new LinkedHashMap<>(symbolChangeCounts);
        other.symbolChangeCounts.forEach((symbol, count) -> mergedCounts.merge(symbol, count, Integer::sum));

        return new ShotgunSurgeryHistoryEvidence(
                changeKey,
                Math.max(commitWindow, other.commitWindow),
                List.copyOf(mergedCommits),
                List.copyOf(mergedOwners),
                Map.copyOf(mergedSymbols),
                Map.copyOf(mergedCounts)
        );
    }
}
