package com.codex.refactor.planning;

import com.codex.refactor.cli.Cli.Options;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RefactoringPlanBuilder {
    private static final String SCHEMA_VERSION = "1.0";

    private RefactoringPlanBuilder() {
    }

    public static Map<String, Object> fromDetectSmellsReport(
            JsonNode report,
            Options options,
            String inputLabel
    ) {
        List<String> validationErrors = validateDetectSmellsReport(report);
        if (!validationErrors.isEmpty()) {
            return errorReport(options, inputLabel, validationErrors);
        }

        List<PlanCandidate> candidates = collectCandidates(report);
        List<PlanCandidate> filteredCandidates = candidates.stream()
                .filter(candidate -> confidenceRank(candidate.confidence()) >= confidenceRank(options.minConfidence()))
                .sorted(PlanCandidate.ORDERING)
                .toList();
        List<PlanCandidate> filtered = selectPlanCandidates(filteredCandidates, options);

        Map<String, Object> plan = baseReport(options, inputLabel);
        plan.put("status", "ok");
        plan.put("source_report", sourceReport(report));
        plan.put("summary", summary(candidates, filtered));
        plan.put("plan", numberedPlan(filtered));
        plan.put("warnings", warnings(report, candidates, filtered));
        plan.put("errors", List.of());
        return plan;
    }

    public static Map<String, Object> errorReport(Options options, String inputLabel, List<String> errors) {
        Map<String, Object> report = baseReport(options, inputLabel);
        report.put("status", "error");
        report.put("source_report", Map.of());
        report.put("summary", Map.of(
                "candidate_findings", 0,
                "planned_findings", 0
        ));
        report.put("plan", List.of());
        report.put("warnings", List.of());
        report.put("errors", errors.stream().map(RefactoringPlanBuilder::error).toList());
        return report;
    }

    public static String toMarkdown(Map<String, Object> report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Refactoring Plan\n\n");
        if ("error".equals(report.get("status"))) {
            markdown.append("Status: error\n\n");
            for (Map<String, Object> error : listOfMaps(report.get("errors"))) {
                markdown.append("- ").append(value(error.get("message"))).append('\n');
            }
            return markdown.toString();
        }

        Map<String, Object> summary = mapOf(report.get("summary"));
        markdown.append("Planned findings: ")
                .append(value(summary.get("planned_findings")))
                .append(" / candidates: ")
                .append(value(summary.get("candidate_findings")))
                .append("\n\n");

        List<Map<String, Object>> steps = listOfMaps(report.get("plan"));
        if (steps.isEmpty()) {
            markdown.append("No refactoring steps selected.\n");
            return markdown.toString();
        }

        for (Map<String, Object> step : steps) {
            Map<String, Object> refactoring = mapOf(step.get("primary_refactoring"));
            Map<String, Object> location = mapOf(step.get("location"));
            markdown.append("## ")
                    .append(value(step.get("rank")))
                    .append(". ")
                    .append(value(step.get("smell_type")))
                    .append(" -> ")
                    .append(value(refactoring.get("name")))
                    .append("\n\n");
            markdown.append("- File: `").append(value(step.get("file_path"))).append("`\n");
            if (!value(location.get("symbol")).isBlank()) {
                markdown.append("- Symbol: `").append(value(location.get("symbol"))).append("`\n");
            }
            if (!value(location.get("start_line")).isBlank()) {
                markdown.append("- Location: line ").append(value(location.get("start_line")));
                if (!value(location.get("end_line")).isBlank()
                        && !value(location.get("end_line")).equals(value(location.get("start_line")))) {
                    markdown.append("-").append(value(location.get("end_line")));
                }
                markdown.append('\n');
            }
            markdown.append("- Severity/confidence: ")
                    .append(value(step.get("severity")))
                    .append(" / ")
                    .append(value(step.get("confidence")))
                    .append('\n');
            markdown.append("- First safe step: ")
                    .append(value(refactoring.get("first_safe_step")))
                    .append('\n');
            List<String> testFocus = stringList(refactoring.get("test_focus"));
            if (!testFocus.isEmpty()) {
                markdown.append("- Test focus: ").append(String.join("; ", testFocus)).append('\n');
            }
            markdown.append("- Rerun: `").append(value(step.get("rerun_command"))).append("`\n\n");
        }
        return markdown.toString();
    }

    private static Map<String, Object> baseReport(Options options, String inputLabel) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema_version", SCHEMA_VERSION);
        report.put("tool", "plan-refactor");
        report.put("status", "ok");
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("input", inputLabel);
        invocation.put("format", options.format().name().toLowerCase(Locale.ROOT));
        invocation.put("max_findings", options.maxFindings());
        invocation.put("min_confidence", options.minConfidence());
        invocation.put("group_by", options.planGroupBy());
        invocation.put("max_findings_per_file", options.maxFindingsPerFile());
        report.put("invocation", invocation);
        return report;
    }

    private static List<String> validateDetectSmellsReport(JsonNode report) {
        List<String> errors = new ArrayList<>();
        if (report == null || !report.isObject()) {
            errors.add("Input is not a JSON object.");
            return errors;
        }
        if (!"detect-smells".equals(report.path("tool").asText())) {
            errors.add("Input report must have tool=detect-smells.");
        }
        if (!report.path("files").isArray()) {
            errors.add("Input report must contain files[].");
        }
        return errors;
    }

    private static List<PlanCandidate> collectCandidates(JsonNode report) {
        List<PlanCandidate> candidates = new ArrayList<>();
        for (JsonNode file : report.path("files")) {
            for (JsonNode smell : file.path("smells")) {
                candidates.add(new PlanCandidate(file, smell));
            }
        }
        return candidates;
    }

    private static Map<String, Object> sourceReport(JsonNode report) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("schema_version", text(report.path("schema_version")));
        source.put("tool", text(report.path("tool")));
        source.put("status", text(report.path("status")));
        source.put("summary", jsonNodeToValue(report.path("summary")));
        if (report.has("analysis_scope")) {
            source.put("analysis_scope", jsonNodeToValue(report.path("analysis_scope")));
        }
        if (report.has("history_analysis")) {
            source.put("history_analysis", jsonNodeToValue(report.path("history_analysis")));
        }
        return source;
    }

    private static Map<String, Object> summary(List<PlanCandidate> candidates, List<PlanCandidate> filtered) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candidate_findings", candidates.size());
        summary.put("planned_findings", filtered.size());
        summary.put("planned_files", filtered.stream().map(PlanCandidate::filePath).distinct().count());
        summary.put("critical", severityCount(filtered, "critical"));
        summary.put("high", severityCount(filtered, "high"));
        summary.put("medium", severityCount(filtered, "medium"));
        summary.put("low", severityCount(filtered, "low"));
        summary.put("high_confidence", confidenceCount(filtered, "high"));
        summary.put("medium_confidence", confidenceCount(filtered, "medium"));
        summary.put("low_confidence", confidenceCount(filtered, "low"));
        return summary;
    }

    private static List<PlanCandidate> selectPlanCandidates(List<PlanCandidate> candidates, Options options) {
        if ("finding".equals(options.planGroupBy())) {
            return candidates.stream()
                    .limit(options.maxFindings())
                    .toList();
        }
        return selectByFile(candidates, options.maxFindings(), options.maxFindingsPerFile());
    }

    private static List<PlanCandidate> selectByFile(
            List<PlanCandidate> candidates,
            int maxFindings,
            int maxFindingsPerFile
    ) {
        Map<String, List<PlanCandidate>> byFile = new LinkedHashMap<>();
        for (PlanCandidate candidate : candidates) {
            byFile.computeIfAbsent(candidate.filePath(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<FilePlanGroup> groups = byFile.entrySet().stream()
                .map(entry -> new FilePlanGroup(entry.getKey(), entry.getValue().stream()
                        .sorted(PlanCandidate.ORDERING)
                        .toList()))
                .sorted(FilePlanGroup.ORDERING)
                .toList();

        List<PlanCandidate> selected = new ArrayList<>();
        for (int round = 0; selected.size() < maxFindings && round < maxFindingsPerFile; round++) {
            boolean addedInRound = false;
            for (FilePlanGroup group : groups) {
                if (selected.size() >= maxFindings) {
                    break;
                }
                if (group.candidates().size() > round) {
                    selected.add(group.candidates().get(round));
                    addedInRound = true;
                }
            }
            if (!addedInRound) {
                break;
            }
        }
        return selected;
    }

    private static int severityCount(List<PlanCandidate> candidates, String severity) {
        return (int) candidates.stream()
                .filter(candidate -> severity.equalsIgnoreCase(candidate.severity()))
                .count();
    }

    private static int confidenceCount(List<PlanCandidate> candidates, String confidence) {
        return (int) candidates.stream()
                .filter(candidate -> confidence.equalsIgnoreCase(candidate.confidence()))
                .count();
    }

    private static List<Map<String, Object>> numberedPlan(List<PlanCandidate> candidates) {
        List<Map<String, Object>> plan = new ArrayList<>();
        int rank = 1;
        for (PlanCandidate candidate : candidates) {
            plan.add(candidate.toPlanStep(rank++));
        }
        return plan;
    }

    private static List<String> warnings(JsonNode report, List<PlanCandidate> candidates, List<PlanCandidate> filtered) {
        List<String> warnings = new ArrayList<>();
        if (!"ok".equals(report.path("status").asText())) {
            warnings.add("Source smell report status is " + report.path("status").asText() + ".");
        }
        if (!candidates.isEmpty() && filtered.isEmpty()) {
            warnings.add("No findings met the requested minimum confidence.");
        }
        return warnings;
    }

    private static Map<String, Object> error(String message) {
        return Map.of("message", message);
    }

    private static int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private static int confidenceRank(String confidence) {
        if (confidence == null) {
            return 0;
        }
        return switch (confidence.toLowerCase(Locale.ROOT)) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private static Object jsonNodeToValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                return node.asLong();
            }
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(child -> values.add(jsonNodeToValue(child)));
            return values;
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), jsonNodeToValue(entry.getValue())));
            return values;
        }
        return node.asText();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                maps.add(stringKeyMap(map));
            }
        }
        return maps;
    }

    private static Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringKeyMap(map);
        }
        return Map.of();
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (Object item : iterable) {
            String text = value(item);
            if (!text.isBlank()) {
                strings.add(text);
            }
        }
        return strings;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record PlanCandidate(JsonNode file, JsonNode smell) {
        private static final Comparator<PlanCandidate> ORDERING = Comparator
                .comparingInt((PlanCandidate candidate) -> severityRank(candidate.severity())).reversed()
                .thenComparing(Comparator.comparingInt((PlanCandidate candidate) ->
                        confidenceRank(candidate.confidence())).reversed())
                .thenComparing(Comparator.comparingInt(PlanCandidate::recommendationStrength).reversed())
                .thenComparing(PlanCandidate::filePath)
                .thenComparing(PlanCandidate::startLine)
                .thenComparing(PlanCandidate::smellId);

        private Map<String, Object> toPlanStep(int rank) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("rank", rank);
            step.put("smell_id", smellId());
            step.put("smell_type", text(smell.path("type")));
            step.put("book_chapter", text(smell.path("book_chapter")));
            step.put("file_path", filePath());
            step.put("language", text(file.path("language")));
            step.put("severity", severity());
            step.put("confidence", confidence());
            step.put("location", jsonNodeToValue(smell.path("location")));
            step.put("description", text(smell.path("description")));
            step.put("suggestion", text(smell.path("suggestion")));
            step.put("evidence", jsonNodeToValue(smell.path("evidence")));
            step.put("related_symbols", jsonNodeToValue(smell.path("related_symbols")));
            step.put("primary_refactoring", primaryRefactoring());
            step.put("supporting_refactorings", supportingRefactorings());
            step.put("rerun_command", rerunCommand());
            return step;
        }

        private Map<String, Object> primaryRefactoring() {
            JsonNode detail = first(smell.path("recommended_refactoring_details"));
            JsonNode rationale = first(smell.path("recommended_refactoring_rationale"));
            String fallbackName = text(first(smell.path("recommended_refactorings")));
            String name = firstNonBlank(text(path(detail, "name")), text(path(rationale, "name")), fallbackName);

            Map<String, Object> refactoring = new LinkedHashMap<>();
            refactoring.put("name", name);
            refactoring.put("chapter", text(path(detail, "chapter")));
            refactoring.put("reason", text(path(rationale, "reason")));
            refactoring.put("applies_when", text(path(rationale, "applies_when")));
            refactoring.put("preconditions", jsonNodeToValue(path(rationale, "preconditions")));
            refactoring.put("first_safe_step", firstNonBlank(
                    text(path(rationale, "first_safe_step")),
                    text(first(path(rationale, "steps"))),
                    "Make the smallest behavior-preserving change that addresses this finding."
            ));
            refactoring.put("steps", jsonNodeToValue(path(rationale, "steps")));
            refactoring.put("test_focus", jsonNodeToValue(path(rationale, "test_focus")));
            refactoring.put("risks", jsonNodeToValue(path(rationale, "risks")));
            return refactoring;
        }

        private List<Map<String, Object>> supportingRefactorings() {
            List<Map<String, Object>> supporting = new ArrayList<>();
            JsonNode details = smell.path("recommended_refactoring_details");
            JsonNode rationales = smell.path("recommended_refactoring_rationale");
            for (int index = 1; index < details.size(); index++) {
                JsonNode detail = details.get(index);
                JsonNode rationale = rationales.size() > index ? rationales.get(index) : null;
                Map<String, Object> refactoring = new LinkedHashMap<>();
                refactoring.put("name", text(detail.path("name")));
                refactoring.put("chapter", text(detail.path("chapter")));
                refactoring.put("reason", text(rationale == null ? null : rationale.path("reason")));
                refactoring.put("first_safe_step", text(rationale == null ? null : rationale.path("first_safe_step")));
                supporting.add(refactoring);
            }
            return supporting;
        }

        private int recommendationStrength() {
            return smell.path("recommended_refactoring_rationale").isArray()
                    && !smell.path("recommended_refactoring_rationale").isEmpty()
                    ? 1
                    : 0;
        }

        private String rerunCommand() {
            return "scripts/detect-smells --json --min-confidence "
                    + confidence()
                    + " "
                    + shellQuote(filePath());
        }

        private static String shellQuote(String value) {
            if (value.matches("[A-Za-z0-9_./:@%+=,-]+")) {
                return value;
            }
            return "'" + value.replace("'", "'\"'\"'") + "'";
        }

        private String severity() {
            return text(smell.path("severity"));
        }

        private String confidence() {
            return text(smell.path("confidence"));
        }

        private String filePath() {
            return text(file.path("path"));
        }

        private int startLine() {
            JsonNode location = smell.path("location");
            if (location.path("start_line").canConvertToInt()) {
                return location.path("start_line").asInt();
            }
            if (location.path("line").canConvertToInt()) {
                return location.path("line").asInt();
            }
            return Integer.MAX_VALUE;
        }

        private String smellId() {
            return text(smell.path("id"));
        }

        private static JsonNode first(JsonNode node) {
            return node != null && node.isArray() && !node.isEmpty() ? node.get(0) : null;
        }

        private static JsonNode path(JsonNode node, String field) {
            return node == null ? null : node.path(field);
        }

        private static String firstNonBlank(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return "";
        }
    }

    private record FilePlanGroup(String filePath, List<PlanCandidate> candidates) {
        private static final Comparator<FilePlanGroup> ORDERING = Comparator
                .comparingInt(FilePlanGroup::topSeverityRank).reversed()
                .thenComparing(Comparator.comparingInt(FilePlanGroup::topConfidenceRank).reversed())
                .thenComparing(Comparator.comparingInt(FilePlanGroup::highHighCount).reversed())
                .thenComparing(Comparator.comparingInt(FilePlanGroup::highSeverityCount).reversed())
                .thenComparing(Comparator.comparingInt(FilePlanGroup::totalCount).reversed())
                .thenComparing(FilePlanGroup::filePath);

        private int topSeverityRank() {
            return candidates.stream()
                    .mapToInt(candidate -> severityRank(candidate.severity()))
                    .max()
                    .orElse(0);
        }

        private int topConfidenceRank() {
            return candidates.stream()
                    .mapToInt(candidate -> confidenceRank(candidate.confidence()))
                    .max()
                    .orElse(0);
        }

        private int highHighCount() {
            return (int) candidates.stream()
                    .filter(candidate -> "high".equalsIgnoreCase(candidate.severity()))
                    .filter(candidate -> "high".equalsIgnoreCase(candidate.confidence()))
                    .count();
        }

        private int highSeverityCount() {
            return (int) candidates.stream()
                    .filter(candidate -> "high".equalsIgnoreCase(candidate.severity()))
                    .count();
        }

        private int totalCount() {
            return candidates.size();
        }
    }
}
