package com.codex.refactor.smell;

import com.codex.refactor.refactoring.RefactoringCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SmellFinding(
        BadSmell smell,
        String severity,
        String confidence,
        Map<String, Object> location,
        Map<String, Object> evidence,
        String description,
        String suggestion
) {
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", smell.id());
        json.put("type", smell.englishName());
        json.put("book_chapter", smell.chapter());
        json.put("severity", severity);
        json.put("confidence", confidence);
        json.put("location", location);
        json.put("evidence", evidence);
        json.put("description", description);
        json.put("suggestion", suggestion);
        json.put("recommended_refactorings", recommendedRefactorings(smell, evidence));
        json.put("recommended_refactoring_details", recommendedRefactoringDetails(smell, evidence));
        json.put("recommended_refactoring_rationale", recommendedRefactoringRationale(smell, evidence));
        json.put("related_symbols", relatedSymbols());
        json.put("why_not_higher_confidence", whyNotHigherConfidence());
        return json;
    }

    private static List<String> recommendedRefactorings(BadSmell smell, Map<String, Object> evidence) {
        return RefactoringCatalog.standard().recommendedRefactoringNames(smell, evidence);
    }

    private static List<Map<String, Object>> recommendedRefactoringDetails(BadSmell smell, Map<String, Object> evidence) {
        return RefactoringCatalog.standard().recommendedRefactoringDetails(smell, evidence);
    }

    private static List<Map<String, Object>> recommendedRefactoringRationale(BadSmell smell, Map<String, Object> evidence) {
        return RefactoringCatalog.standard().recommendedRefactoringRationale(smell, evidence);
    }

    private List<String> relatedSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        Object symbol = location.get("symbol");
        if (symbol instanceof String text && !text.isBlank()) {
            symbols.add(text);
        }
        for (String key : List.of(
                "owners",
                "method_names",
                "current_file_methods",
                "dominant_delegate",
                "resolved_delegate_type",
                "resolved_forwarding_targets",
                "foreign_root",
                "resolved_foreign_type",
                "matching_foreign_members",
                "classes",
                "fields",
                "methods"
        )) {
            collectSymbol(evidence.get(key), symbols);
        }
        return new ArrayList<>(symbols);
    }

    private static void collectSymbol(Object value, Set<String> symbols) {
        if (value instanceof String text) {
            if (!text.isBlank()) {
                symbols.add(text);
            }
            return;
        }
        if (value instanceof Iterable<?> values) {
            for (Object nested : values) {
                collectSymbol(nested, symbols);
            }
        }
    }

    private String whyNotHigherConfidence() {
        return switch (confidence == null ? "" : confidence.toLowerCase(java.util.Locale.ROOT)) {
            case "high" -> "";
            case "medium" -> "Evidence is deterministic but still heuristic; confirm the design boundary before refactoring.";
            default -> "Low-confidence fallback or shallow syntactic evidence; review the code before refactoring.";
        };
    }
}
