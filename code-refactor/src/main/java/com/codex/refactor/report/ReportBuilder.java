package com.codex.refactor.report;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.ParseError;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.analysis.TreeSitterLanguageRegistry;
import com.codex.refactor.analysis.TreeSitterSourceAnalyzer;
import com.codex.refactor.cli.Cli.Options;
import com.codex.refactor.cli.Cli.ToolCommand;
import com.codex.refactor.history.GitHistoryAnalyzer;
import com.codex.refactor.history.HistoryAnalysis;
import com.codex.refactor.history.HistoryOptions;
import com.codex.refactor.language.LanguageDetector;
import com.codex.refactor.smell.BadSmellDetectionDispatcher;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReportBuilder {
    private static final String SCHEMA_VERSION = "1.0";
    private static final int DEFAULT_PROJECT_INDEX_BATCH_SIZE = 160;
    private static final String PROJECT_INDEX_BATCH_SIZE_PROPERTY = "codeRefactor.smell.projectIndexBatchSize";
    private static final List<String> PROJECT_MARKERS = List.of(
            "pom.xml",
            "package.json",
            "go.mod",
            "Cargo.toml",
            "Gemfile",
            "pyproject.toml"
    );

    private ReportBuilder() {
    }

    public static Map<String, Object> analysisReport(
            ToolCommand command,
            Options options,
            List<Path> paths
    ) {
        List<Map<String, Object>> files = new ArrayList<>();
        JavaSourceAnalyzer javaAnalyzer = new JavaSourceAnalyzer();
        TreeSitterSourceAnalyzer treeSitterAnalyzer = new TreeSitterSourceAnalyzer();
        HistoryAnalysis historyAnalysis = command == ToolCommand.DETECT_SMELLS
                ? historyAnalysis(paths, options)
                : HistoryAnalysis.off();
        List<InputAnalysis> inputAnalyses = new ArrayList<>();
        for (Path path : paths) {
            String language = LanguageDetector.detect(path, options.language());
            if ("java".equals(language)) {
                inputAnalyses.add(inputAnalyzed(command, javaAnalyzer, path));
            } else if (TreeSitterLanguageRegistry.languageFor(language).isPresent()) {
                inputAnalyses.add(inputAnalyzed(command, treeSitterAnalyzer, path, language));
            } else {
                inputAnalyses.add(InputAnalysis.prebuilt(fileUnsupported(command, options, path)));
            }
        }
        ProjectIndexPlan projectIndexPlan = command == ToolCommand.DETECT_SMELLS
                ? ProjectIndexPlan.forSmellDetection(inputAnalyses)
                : ProjectIndexPlan.off();
        for (InputAnalysis input : inputAnalyses) {
            files.add(input.toFile(command, options, historyAnalysis, projectIndexPlan.indexFor(input)));
        }

        Map<String, Object> report = baseReport(command, options);
        report.put("status", topLevelStatus(files));
        report.put("summary", summary(command, files));
        if (command == ToolCommand.DETECT_SMELLS) {
            report.put("analysis_scope", projectIndexPlan.toJson());
        }
        if (command == ToolCommand.DETECT_SMELLS && historyAnalysis.enabled()) {
            report.put("history_analysis", historyAnalysis.toJson());
        }
        report.put("files", files);
        report.put("errors", List.of());
        return report;
    }

    public static Map<String, Object> invocationErrorReport(
            ToolCommand command,
            Options options,
            String message
    ) {
        Map<String, Object> report = baseReport(command, options);
        report.put("status", "error");
        report.put("summary", emptySummary(command, options.paths().size()));
        report.put("files", List.of());
        report.put("errors", List.of(error(message)));
        return report;
    }

    public static boolean hasParseErrors(Object report) {
        if (!(report instanceof Map<?, ?> reportMap)) {
            return false;
        }
        Object files = reportMap.get("files");
        if (!(files instanceof List<?> fileList)) {
            return false;
        }
        return fileList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(file -> "parse_error".equals(file.get("status")));
    }

    private static Map<String, Object> baseReport(ToolCommand command, Options options) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema_version", SCHEMA_VERSION);
        report.put("tool", command.primaryName());
        report.put("status", "ok");
        report.put("invocation", invocation(options));
        return report;
    }

    private static Map<String, Object> invocation(Options options) {
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("paths", options.paths());
        invocation.put("format", options.format().name().toLowerCase());
        invocation.put("language", options.language());
        invocation.put("include", options.includes());
        invocation.put("exclude", options.excludes());
        invocation.put("config", options.config());
        invocation.put("max_files", options.maxFiles());
        invocation.put("history_analysis", options.historyAnalysis());
        invocation.put("history_commits", options.historyCommits());
        invocation.put("history_min_cochanges", options.historyMinCoChanges());
        invocation.put("history_min_owners", options.historyMinOwners());
        invocation.put("min_confidence", options.minConfidence());
        invocation.put("fail_on_parse_error", options.failOnParseError());
        invocation.put("no_default_excludes", options.noDefaultExcludes());
        return invocation;
    }

    private static HistoryAnalysis historyAnalysis(List<Path> paths, Options options) {
        HistoryOptions historyOptions = new HistoryOptions(
                options.historyAnalysis(),
                options.historyCommits(),
                options.historyMinCoChanges(),
                options.historyMinOwners()
        );
        return new GitHistoryAnalyzer().analyze(paths, historyOptions);
    }

    private static Map<String, Object> summary(ToolCommand command, List<Map<String, Object>> files) {
        int filesTotal = files.size();
        int filesAnalyzed = (int) files.stream().filter(file -> "ok".equals(file.get("status"))).count();
        int filesSkipped = (int) files.stream().filter(file -> "unsupported_language".equals(file.get("status"))
                || "skipped".equals(file.get("status"))).count();
        int parseErrors = (int) files.stream().filter(file -> "parse_error".equals(file.get("status"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files_total", filesTotal);
        summary.put("files_analyzed", filesAnalyzed);

        if (command == ToolCommand.ANALYZE_COMPLEXITY) {
            summary.put("files_skipped", filesSkipped);
            summary.put("files_with_parse_errors", parseErrors);
            summary.put("functions_total", files.stream().mapToInt(file -> listOfMaps(file.get("functions")).size()).sum());
            summary.put("classes_total", files.stream().mapToInt(file -> listOfMaps(file.get("classes")).size()).sum());
            summary.put("max_cyclomatic_complexity", maxFunctionMetric(files, "cyclomatic_complexity"));
            summary.put("max_cognitive_complexity", maxFunctionMetric(files, "cognitive_complexity"));
            return summary;
        }

        List<Map<String, Object>> smells = files.stream()
                .flatMap(file -> listOfMaps(file.get("smells")).stream())
                .toList();
        summary.put("total_smells", smells.size());
        summary.put("critical", severityCount(smells, "critical"));
        summary.put("high", severityCount(smells, "high"));
        summary.put("medium", severityCount(smells, "medium"));
        summary.put("low", severityCount(smells, "low"));
        return summary;
    }

    private static Map<String, Object> emptySummary(ToolCommand command, int filesTotal) {
        return summary(command, List.of()).entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> {
                    if ("files_total".equals(entry.getKey())) {
                        map.put(entry.getKey(), filesTotal);
                    } else {
                        map.put(entry.getKey(), entry.getValue());
                    }
                }, LinkedHashMap::putAll);
    }

    private static InputAnalysis inputAnalyzed(
            ToolCommand command,
            JavaSourceAnalyzer javaAnalyzer,
            Path path
    ) {
        try {
            SourceFileAnalysis analysis = javaAnalyzer.analyze(path);
            return InputAnalysis.analyzed(analysis);
        } catch (IOException exception) {
            return InputAnalysis.prebuilt(readErrorFile(command, path, exception.getMessage()));
        }
    }

    private static InputAnalysis inputAnalyzed(
            ToolCommand command,
            TreeSitterSourceAnalyzer treeSitterAnalyzer,
            Path path,
            String language
    ) {
        try {
            SourceFileAnalysis analysis = treeSitterAnalyzer.analyze(path, language);
            return InputAnalysis.analyzed(analysis);
        } catch (IOException exception) {
            return InputAnalysis.prebuilt(readErrorFile(command, path, exception.getMessage()));
        }
    }

    private static Map<String, Object> complexityFile(SourceFileAnalysis analysis) {
        Map<String, Object> file = commonAnalyzedFile(analysis);
        file.put("metrics", metrics(analysis));
        file.put("functions", analysis.methods().stream().map(ReportBuilder::methodJson).toList());
        file.put("classes", analysis.classes().stream().map(ReportBuilder::classJson).toList());
        return file;
    }

    private static Map<String, Object> smellsFile(
            SourceFileAnalysis analysis,
            Options options,
            HistoryAnalysis historyAnalysis,
            SourceProjectIndex projectIndex
    ) {
        Map<String, Object> file = commonAnalyzedFile(analysis);
        if (!analysis.parseErrors().isEmpty()) {
            file.put("smells", List.of());
            return file;
        }
        List<SmellFinding> findings = BadSmellDetectionDispatcher.standard()
                .detect(new SmellAnalysisContext(analysis, historyAnalysis, projectIndex))
                .stream()
                .filter(finding -> confidenceRank(finding.confidence()) >= confidenceRank(options.minConfidence()))
                .toList();
        file.put("smells", findings.stream()
                .map(SmellFinding::toJson)
                .toList());
        return file;
    }

    private static int confidenceRank(String confidence) {
        if (confidence == null) {
            return 0;
        }
        return switch (confidence.toLowerCase(java.util.Locale.ROOT)) {
            case "high" -> 2;
            case "medium" -> 1;
            default -> 0;
        };
    }

    private static Map<String, Object> commonAnalyzedFile(SourceFileAnalysis analysis) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", analysis.path().toString());
        file.put("language", analysis.language());
        file.put("parser", analysis.parser());
        file.put("parser_id", analysis.parserId());
        file.put("status", analysis.status());
        file.put("parse_errors", analysis.parseErrors().stream().map(ReportBuilder::parseErrorJson).toList());
        file.put("warnings", analysis.warnings());
        return file;
    }

    private static Map<String, Object> metrics(SourceFileAnalysis analysis) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("physical_lines", analysis.physicalLines());
        metrics.put("logical_lines", Math.max(0, analysis.physicalLines() - analysis.blankLines() - analysis.commentLines()));
        metrics.put("blank_lines", analysis.blankLines());
        metrics.put("comment_lines", analysis.commentLines());
        metrics.put("function_count", analysis.methods().size());
        metrics.put("class_count", analysis.classes().size());
        metrics.put("max_nesting_depth", analysis.maxNestingDepth());
        metrics.put("cyclomatic_complexity", analysis.methods().stream()
                .mapToInt(JavaMethodInfo::cyclomaticComplexity).sum());
        metrics.put("cognitive_complexity", analysis.methods().stream()
                .mapToInt(JavaMethodInfo::cognitiveComplexity).sum());
        metrics.put("maintainability_index", null);
        return metrics;
    }

    private static Map<String, Object> methodJson(JavaMethodInfo method) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", method.name());
        json.put("kind", method.constructor() ? "constructor" : "method");
        json.put("location", location(method.name(), method.startLine(), method.endLine()));
        json.put("metrics", Map.of(
                "physical_lines", method.physicalLines(),
                "parameter_count", method.parameterNames().size(),
                "max_nesting_depth", method.maxNestingDepth(),
                "cyclomatic_complexity", method.cyclomaticComplexity(),
                "cognitive_complexity", method.cognitiveComplexity()
        ));
        json.put("thresholds", List.of());
        return json;
    }

    private static Map<String, Object> classJson(JavaClassInfo classInfo) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", classInfo.name());
        json.put("kind", classInfo.interfaceType() ? "interface" : "class");
        json.put("location", location(classInfo.name(), classInfo.startLine(), classInfo.endLine()));
        json.put("metrics", Map.of(
                "physical_lines", classInfo.physicalLines(),
                "method_count", classInfo.methods().size(),
                "field_count", classInfo.fields().size()
        ));
        json.put("thresholds", List.of());
        return json;
    }

    private static Map<String, Object> parseErrorJson(ParseError parseError) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("message", parseError.message());
        json.put("start_line", parseError.startLine());
        json.put("start_column", parseError.startColumn());
        json.put("end_line", parseError.endLine());
        json.put("end_column", parseError.endColumn());
        json.put("severity", parseError.severity());
        return json;
    }

    public static Map<String, Object> location(String symbol, int startLine, int endLine) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("symbol", symbol);
        location.put("line", startLine);
        location.put("start_line", startLine);
        location.put("end_line", endLine);
        return location;
    }

    private static Map<String, Object> fileUnsupported(ToolCommand command, Options options, Path path) {
        String language = LanguageDetector.detect(path, options.language());
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path.toString());
        file.put("language", language);

        if (command == ToolCommand.ANALYZE_COMPLEXITY) {
            file.put("parser", "none");
            file.put("parser_id", "none");
        }

        file.put("status", "unsupported_language");
        file.put("skip_reason", "no_parser_adapter");

        if (command == ToolCommand.ANALYZE_COMPLEXITY) {
            file.put("metrics", null);
            file.put("functions", List.of());
            file.put("classes", List.of());
        } else {
            file.put("smells", List.of());
        }

        file.put("parse_errors", List.of());
        file.put("warnings", List.of("No parser-backed adapter is implemented for " + language + " yet."));
        return file;
    }

    private static Map<String, Object> readErrorFile(ToolCommand command, Path path, String message) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", path.toString());
        file.put("language", "unknown");
        file.put("status", "read_error");
        if (command == ToolCommand.ANALYZE_COMPLEXITY) {
            file.put("parser", "none");
            file.put("parser_id", "none");
            file.put("metrics", null);
            file.put("functions", List.of());
            file.put("classes", List.of());
        } else {
            file.put("smells", List.of());
        }
        file.put("parse_errors", List.of());
        file.put("warnings", List.of(message));
        return file;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        return error;
    }

    private static String topLevelStatus(List<Map<String, Object>> files) {
        if (files.isEmpty()) {
            return "ok";
        }
        boolean allOk = files.stream().allMatch(file -> "ok".equals(file.get("status")));
        return allOk ? "ok" : "partial";
    }

    private static int maxFunctionMetric(List<Map<String, Object>> files, String key) {
        return files.stream()
                .flatMap(file -> listOfMaps(file.get("functions")).stream())
                .map(function -> function.get("metrics"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .mapToInt(metrics -> intValue(metrics.get(key)))
                .max()
                .orElse(0);
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int severityCount(List<Map<String, Object>> smells, String severity) {
        return (int) smells.stream().filter(smell -> severity.equals(smell.get("severity"))).count();
    }

    private static int projectIndexBatchSize() {
        String value = System.getProperty(PROJECT_INDEX_BATCH_SIZE_PROPERTY);
        if (value == null || value.isBlank()) {
            return DEFAULT_PROJECT_INDEX_BATCH_SIZE;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : DEFAULT_PROJECT_INDEX_BATCH_SIZE;
        } catch (NumberFormatException exception) {
            return DEFAULT_PROJECT_INDEX_BATCH_SIZE;
        }
    }

    private static List<List<SourceFileAnalysis>> projectIndexBatches(List<SourceFileAnalysis> analyses, int batchSize) {
        if (analyses.isEmpty()) {
            return List.of();
        }
        Map<Path, List<SourceFileAnalysis>> byProjectRoot = new LinkedHashMap<>();
        analyses.stream()
                .sorted(Comparator.comparing(analysis -> analysis.path().toString()))
                .forEach(analysis -> byProjectRoot
                        .computeIfAbsent(projectGroup(analysis.path()), ignored -> new ArrayList<>())
                        .add(analysis));

        List<List<SourceFileAnalysis>> batches = new ArrayList<>();
        for (List<SourceFileAnalysis> group : byProjectRoot.values()) {
            group.sort(Comparator.comparing(analysis -> analysis.path().toString()));
            for (int start = 0; start < group.size(); start += batchSize) {
                batches.add(List.copyOf(group.subList(start, Math.min(start + batchSize, group.size()))));
            }
        }
        return batches;
    }

    private static Path projectGroup(Path path) {
        Path current = path.toAbsolutePath().normalize().getParent();
        Path fallback = current == null ? path.toAbsolutePath().normalize() : current;
        while (current != null) {
            for (String marker : PROJECT_MARKERS) {
                if (Files.exists(current.resolve(marker))) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private record InputAnalysis(SourceFileAnalysis analysis, Map<String, Object> prebuiltFile) {
        static InputAnalysis analyzed(SourceFileAnalysis analysis) {
            return new InputAnalysis(analysis, null);
        }

        static InputAnalysis prebuilt(Map<String, Object> file) {
            return new InputAnalysis(null, file);
        }

        Optional<SourceFileAnalysis> optionalAnalysis() {
            return Optional.ofNullable(analysis);
        }

        Map<String, Object> toFile(
                ToolCommand command,
                Options options,
                HistoryAnalysis historyAnalysis,
                SourceProjectIndex projectIndex
        ) {
            if (prebuiltFile != null) {
                return prebuiltFile;
            }
            return command == ToolCommand.ANALYZE_COMPLEXITY
                    ? complexityFile(analysis)
                    : smellsFile(analysis, options, historyAnalysis, projectIndex);
        }
    }

    private record ProjectIndexPlan(
            String mode,
            int batchSize,
            int batchCount,
            Map<SourceFileAnalysis, SourceProjectIndex> indexes
    ) {
        static ProjectIndexPlan off() {
            return new ProjectIndexPlan("off", 0, 0, Map.of());
        }

        static ProjectIndexPlan forSmellDetection(List<InputAnalysis> inputs) {
            List<SourceFileAnalysis> analyses = inputs.stream()
                    .flatMap(input -> input.optionalAnalysis().stream())
                    .toList();
            if (analyses.isEmpty()) {
                return new ProjectIndexPlan("none", projectIndexBatchSize(), 0, Map.of());
            }

            int batchSize = projectIndexBatchSize();
            List<List<SourceFileAnalysis>> batches = projectIndexBatches(analyses, batchSize);
            Map<SourceFileAnalysis, SourceProjectIndex> indexes = new IdentityHashMap<>();
            for (List<SourceFileAnalysis> batch : batches) {
                SourceProjectIndex projectIndex = SourceProjectIndex.from(batch);
                batch.forEach(analysis -> indexes.put(analysis, projectIndex));
            }
            String mode = batches.size() <= 1 ? "full" : "partitioned";
            return new ProjectIndexPlan(mode, batchSize, batches.size(), indexes);
        }

        SourceProjectIndex indexFor(InputAnalysis input) {
            return input.optionalAnalysis()
                    .map(analysis -> indexes.getOrDefault(analysis, SourceProjectIndex.empty()))
                    .orElse(SourceProjectIndex.empty());
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("project_index_mode", mode);
            json.put("project_index_batch_size", batchSize);
            json.put("project_index_batch_count", batchCount);
            json.put("project_index_grouping", "nearest-project-marker-then-size");
            return json;
        }
    }
}
