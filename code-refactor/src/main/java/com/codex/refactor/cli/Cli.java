package com.codex.refactor.cli;

import com.codex.refactor.report.ReportBuilder;
import com.codex.refactor.planning.RefactoringPlanBuilder;
import com.codex.refactor.language.LanguageDetector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class Cli {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Set<String> DEFAULT_EXCLUDED_NAMES = Set.of(
            ".git",
            "node_modules",
            "vendor",
            "dist",
            "build",
            "target",
            ".next",
            ".nuxt",
            "coverage"
    );

    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;

    public Cli(PrintStream out, PrintStream err) {
        this(out, err, System.in);
    }

    public Cli(PrintStream out, PrintStream err, InputStream in) {
        this.out = out;
        this.err = err;
        this.in = in;
    }

    public int run(String[] args) {
        try {
            return runInternal(args);
        } catch (CliException exception) {
            err.println(exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            err.println("Internal error: " + exception.getMessage());
            return 2;
        }
    }

    private int runInternal(String[] args) {
        if (args.length == 0) {
            err.println("Missing command.");
            err.println(helpText());
            return 1;
        }

        if (isHelp(args[0])) {
            out.println(helpText());
            return 0;
        }

        ToolCommand command = ToolCommand.fromToken(args[0])
                .orElseThrow(() -> new CliException("Unknown command: " + args[0]));

        Options options = parseOptions(command, args);
        if (options.help()) {
            out.println(helpText());
            return 0;
        }
        if (options.paths().isEmpty()) {
            throw new CliException("Missing input path.");
        }

        if (command == ToolCommand.PLAN_REFACTOR) {
            return runPlanRefactor(options);
        }

        List<Path> inputPaths = new ArrayList<>();
        for (String rawPath : options.paths()) {
            Path path = Path.of(rawPath);
            if (!Files.exists(path)) {
                return writeInvocationError(command, options, "Input path does not exist: " + rawPath);
            }
            if (!Files.isReadable(path)) {
                return writeInvocationError(command, options, "Input path is not readable: " + rawPath);
            }
            inputPaths.add(path);
        }

        List<Path> resolvedPaths;
        try {
            resolvedPaths = expandInputs(inputPaths, options);
        } catch (IOException exception) {
            return writeInvocationError(command, options, exception.getMessage());
        }
        if (options.maxFiles() != null && resolvedPaths.size() > options.maxFiles()) {
            return writeInvocationError(command, options,
                    "Input expansion exceeded --max-files: " + resolvedPaths.size() + " > " + options.maxFiles());
        }

        Object report = ReportBuilder.analysisReport(command, options, resolvedPaths);
        writeReport(options, report);
        if (options.failOnParseError() && ReportBuilder.hasParseErrors(report)) {
            return 3;
        }
        return 0;
    }

    private int runPlanRefactor(Options options) {
        if (options.paths().size() != 1) {
            return writePlanInvocationError(options, "plan-refactor expects exactly one detect-smells JSON report path or '-'.");
        }

        String inputLabel = options.paths().getFirst();
        JsonNode inputReport;
        try {
            if ("-".equals(inputLabel)) {
                inputReport = JSON.readTree(in);
            } else {
                Path inputPath = Path.of(inputLabel);
                if (!Files.exists(inputPath)) {
                    return writePlanInvocationError(options, "Input path does not exist: " + inputLabel);
                }
                if (!Files.isReadable(inputPath)) {
                    return writePlanInvocationError(options, "Input path is not readable: " + inputLabel);
                }
                inputReport = JSON.readTree(inputPath.toFile());
            }
        } catch (IOException exception) {
            return writePlanInvocationError(options, "Could not read detect-smells JSON report: " + exception.getMessage());
        }

        Object report = RefactoringPlanBuilder.fromDetectSmellsReport(inputReport, options, inputLabel);
        writeReport(options, report);
        if (report instanceof java.util.Map<?, ?> map && "error".equals(map.get("status"))) {
            return 1;
        }
        return 0;
    }

    private int writeInvocationError(ToolCommand command, Options options, String message) {
        if (options.format() == OutputFormat.JSON) {
            Object report = ReportBuilder.invocationErrorReport(command, options, message);
            writeReport(options, report);
        } else {
            err.println(message);
        }
        return 1;
    }

    private int writePlanInvocationError(Options options, String message) {
        if (options.format() == OutputFormat.JSON) {
            Object report = RefactoringPlanBuilder.errorReport(options,
                    options.paths().isEmpty() ? "" : String.join(" ", options.paths()),
                    List.of(message));
            writeReport(options, report);
        } else {
            err.println(message);
        }
        return 1;
    }

    private void writeReport(Options options, Object report) {
        if (options.format() == OutputFormat.JSON) {
            try {
                out.println(JSON.writeValueAsString(report));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize report JSON", exception);
            }
            return;
        }

        if (report instanceof java.util.Map<?, ?> map) {
            if ("plan-refactor".equals(map.get("tool"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> plan = (Map<String, Object>) map;
                out.print(RefactoringPlanBuilder.toMarkdown(plan));
                return;
            }
            out.printf(Locale.ROOT, "%s: status=%s, files=%s%n",
                    map.get("tool"), map.get("status"), map.get("files"));
            return;
        }
        out.println(report);
    }

    private Options parseOptions(ToolCommand command, String[] args) {
        OutputFormat format = OutputFormat.TEXT;
        String language = "auto";
        boolean failOnParseError = false;
        boolean noDefaultExcludes = false;
        boolean help = false;
        Integer maxFiles = null;
        String config = null;
        String historyAnalysis = "off";
        int historyCommits = 200;
        int historyMinCoChanges = 3;
        int historyMinOwners = 3;
        String minConfidence = "low";
        int maxFindings = 5;
        int maxFindingsPerFile = 3;
        String planGroupBy = "file";
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> paths = new ArrayList<>();

        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            switch (token) {
                case "--help", "-h" -> help = true;
                case "--json" -> format = OutputFormat.JSON;
                case "--format" -> {
                    String value = requireValue(args, ++index, token);
                    format = OutputFormat.parse(value);
                }
                case "--language" -> language = requireValue(args, ++index, token);
                case "--include" -> includes.add(requireValue(args, ++index, token));
                case "--exclude" -> excludes.add(requireValue(args, ++index, token));
                case "--config" -> config = requireValue(args, ++index, token);
                case "--max-files" -> maxFiles = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--history-analysis" -> historyAnalysis = parseHistoryAnalysis(requireValue(args, ++index, token));
                case "--history-commits" -> historyCommits = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--history-min-cochanges" -> historyMinCoChanges = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--history-min-owners" -> historyMinOwners = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--min-confidence" -> minConfidence = parseConfidence(requireValue(args, ++index, token));
                case "--max-findings" -> maxFindings = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--max-findings-per-file" ->
                        maxFindingsPerFile = parsePositiveInt(requireValue(args, ++index, token), token);
                case "--group-by" -> planGroupBy = parsePlanGroupBy(requireValue(args, ++index, token));
                case "--fail-on-parse-error" -> failOnParseError = true;
                case "--no-default-excludes" -> noDefaultExcludes = true;
                default -> {
                    if (token.startsWith("-")) {
                        throw new CliException("Unknown option for " + command.primaryName() + ": " + token);
                    }
                    paths.add(token);
                }
            }
        }

        return new Options(
                command,
                format,
                language,
                includes,
                excludes,
                config,
                maxFiles,
                historyAnalysis,
                historyCommits,
                historyMinCoChanges,
                historyMinOwners,
                minConfidence,
                maxFindings,
                maxFindingsPerFile,
                planGroupBy,
                failOnParseError,
                noDefaultExcludes,
                help,
                paths
        );
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new CliException("Missing value for " + option + ".");
        }
        return args[index];
    }

    private static int parsePositiveInt(String value, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new CliException("Expected a positive integer for " + option + ".");
        }
    }

    private static String parseHistoryAnalysis(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "off", "git" -> normalized;
            default -> throw new CliException("Unsupported history analysis provider: " + value);
        };
    }

    private static String parseConfidence(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "low", "medium", "high" -> normalized;
            default -> throw new CliException("Unsupported confidence threshold: " + value);
        };
    }

    private static String parsePlanGroupBy(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "file", "finding" -> normalized;
            default -> throw new CliException("Unsupported plan grouping: " + value);
        };
    }

    private static boolean isHelp(String value) {
        return "--help".equals(value) || "-h".equals(value);
    }

    private static List<Path> expandInputs(List<Path> inputPaths, Options options) throws IOException {
        List<PathMatcher> includes = options.includes().stream().map(Cli::globMatcher).toList();
        List<PathMatcher> excludes = options.excludes().stream().map(Cli::globMatcher).toList();
        List<Path> files = new ArrayList<>();
        for (Path inputPath : inputPaths) {
            if (Files.isRegularFile(inputPath)) {
                if (included(inputPath, includes, excludes, options, true)) {
                    files.add(inputPath);
                }
                continue;
            }
            try (Stream<Path> stream = Files.walk(inputPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> included(path, includes, excludes, options, false))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean included(
            Path path,
            List<PathMatcher> includes,
            List<PathMatcher> excludes,
            Options options,
            boolean explicitFile
    ) {
        Path normalized = path.normalize();
        if (!options.noDefaultExcludes() && defaultExcluded(normalized)) {
            return false;
        }
        if (!includes.isEmpty() && includes.stream().noneMatch(matcher -> matcher.matches(normalized))) {
            return false;
        }
        if (excludes.stream().anyMatch(matcher -> matcher.matches(normalized))) {
            return false;
        }
        return explicitFile || supportedDirectoryInput(normalized, options);
    }

    private static boolean supportedDirectoryInput(Path path, Options options) {
        if (!"auto".equals(options.language())) {
            return true;
        }
        return LanguageDetector.isSupported(LanguageDetector.detect(path, options.language()));
    }

    private static boolean defaultExcluded(Path path) {
        for (Path part : path) {
            if (DEFAULT_EXCLUDED_NAMES.contains(part.toString())) {
                return true;
            }
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return fileName.endsWith(".min.js")
                || fileName.contains(".generated.")
                || fileName.contains(".pb.");
    }

    private static PathMatcher globMatcher(String pattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }

    public static String helpText() {
        return """
                Usage:
                  code-refactor-tools analyze-complexity [options] <path>...
                  code-refactor-tools detect-smells [options] <path>...
                  code-refactor-tools plan-refactor [options] <detect-smells-report.json|->

                Aliases:
                  complexity    Alias for analyze-complexity
                  smells        Alias for detect-smells
                  plan          Alias for plan-refactor

                Options:
                  --format json|text          Output format. Default: text.
                  --json                      Alias for --format json.
                  --language <id>             Force language for all file inputs.
                  --include <glob>            Include glob. Repeatable.
                  --exclude <glob>            Exclude glob. Repeatable.
                  --config <file>             Optional thresholds and language config.
                  --max-files <n>             Directory scan safety limit.
                  --history-analysis off|git  Optional Git history analysis. Default: off.
                  --history-commits <n>       Recent non-merge commits to inspect. Default: 200.
                  --history-min-cochanges <n> Minimum co-change commits. Default: 3.
                  --history-min-owners <n>    Minimum distinct owners in a cluster. Default: 3.
                  --min-confidence low|medium|high
                                               Filter smell findings below confidence. Default: low.
                  --max-findings <n>          Maximum planned findings for plan-refactor. Default: 5.
                  --group-by file|finding     Plan grouping for plan-refactor. Default: file.
                  --max-findings-per-file <n> Maximum planned findings per file when --group-by file. Default: 3.
                  --fail-on-parse-error       Return non-zero when parse errors are found.
                  --no-default-excludes       Include generated/vendor paths.
                  --help, -h                  Show this help.
                """;
    }

    public enum OutputFormat {
        JSON,
        TEXT;

        static OutputFormat parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "json" -> JSON;
                case "text" -> TEXT;
                default -> throw new CliException("Unsupported output format: " + value);
            };
        }
    }

    public enum ToolCommand {
        ANALYZE_COMPLEXITY("analyze-complexity", "complexity"),
        DETECT_SMELLS("detect-smells", "smells"),
        PLAN_REFACTOR("plan-refactor", "plan");

        private final String primaryName;
        private final String alias;

        ToolCommand(String primaryName, String alias) {
            this.primaryName = primaryName;
            this.alias = alias;
        }

        public String primaryName() {
            return primaryName;
        }

        public static Optional<ToolCommand> fromToken(String token) {
            for (ToolCommand command : values()) {
                if (command.primaryName.equals(token) || command.alias.equals(token)) {
                    return Optional.of(command);
                }
            }
            return Optional.empty();
        }
    }

    public record Options(
            ToolCommand command,
            OutputFormat format,
            String language,
            List<String> includes,
            List<String> excludes,
            String config,
            Integer maxFiles,
            String historyAnalysis,
            int historyCommits,
            int historyMinCoChanges,
            int historyMinOwners,
            String minConfidence,
            int maxFindings,
            int maxFindingsPerFile,
            String planGroupBy,
            boolean failOnParseError,
            boolean noDefaultExcludes,
            boolean help,
            List<String> paths
    ) {
    }

    private static final class CliException extends RuntimeException {
        private CliException(String message) {
            super(message);
        }
    }
}
