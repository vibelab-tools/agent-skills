package com.codex.refactor.history;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.JavaSourceAnalyzer;
import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.TreeSitterLanguageRegistry;
import com.codex.refactor.analysis.TreeSitterSourceAnalyzer;
import com.codex.refactor.language.LanguageDetector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GitHistoryAnalyzer {
    private static final Pattern HUNK_HEADER = Pattern.compile("@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@.*");
    private static final Set<String> CHANGE_VERBS = Set.of(
            "refresh", "update", "sync", "invalidate", "migrate", "configure", "recalculate",
            "rebuild", "reload", "reset", "register", "apply", "save", "persist"
    );
    private static final Map<String, String> VERB_FAMILIES = Map.ofEntries(
            Map.entry("refresh", "refresh"),
            Map.entry("reload", "refresh"),
            Map.entry("invalidate", "refresh"),
            Map.entry("update", "update"),
            Map.entry("sync", "update"),
            Map.entry("apply", "update"),
            Map.entry("save", "persist"),
            Map.entry("persist", "persist"),
            Map.entry("recalculate", "calculate"),
            Map.entry("rebuild", "calculate"),
            Map.entry("migrate", "migrate"),
            Map.entry("configure", "configure"),
            Map.entry("reset", "reset"),
            Map.entry("register", "register")
    );
    private static final int MAX_CHANGED_FILES_PER_COMMIT = 80;

    private final GitCommandRunner git;
    private final JavaSourceAnalyzer javaAnalyzer;
    private final TreeSitterSourceAnalyzer treeSitterAnalyzer;

    public GitHistoryAnalyzer() {
        this(new ProcessGitCommandRunner(), new JavaSourceAnalyzer(), new TreeSitterSourceAnalyzer());
    }

    GitHistoryAnalyzer(
            GitCommandRunner git,
            JavaSourceAnalyzer javaAnalyzer,
            TreeSitterSourceAnalyzer treeSitterAnalyzer
    ) {
        this.git = git;
        this.javaAnalyzer = javaAnalyzer;
        this.treeSitterAnalyzer = treeSitterAnalyzer;
    }

    public HistoryAnalysis analyze(List<Path> inputPaths, HistoryOptions options) {
        if (!options.enabled()) {
            return HistoryAnalysis.off();
        }
        if (inputPaths.isEmpty()) {
            return HistoryAnalysis.skipped("No input paths are available for Git history analysis.");
        }

        Optional<Path> repositoryRoot = repositoryRoot(inputPaths.getFirst());
        if (repositoryRoot.isEmpty()) {
            return HistoryAnalysis.skipped("Input path is not inside a Git repository or Git is unavailable.");
        }

        Path root = repositoryRoot.get();
        List<String> warnings = new ArrayList<>();
        List<String> commits = recentCommits(root, options.commitWindow(), warnings);
        Map<String, ClusterAccumulator> accumulators = new LinkedHashMap<>();
        int scanned = 0;
        for (String commit : commits) {
            CommitDiff diff = commitDiff(root, commit, warnings);
            if (diff.hunksByPath().isEmpty()) {
                continue;
            }
            if (diff.hunksByPath().size() > MAX_CHANGED_FILES_PER_COMMIT) {
                warnings.add("Skipped large commit " + shortCommit(commit) + " with "
                        + diff.hunksByPath().size() + " changed files.");
                continue;
            }
            List<ChangedSymbol> changedSymbols = changedSymbols(root, commit, diff, warnings);
            recordCoChanges(accumulators, commit, changedSymbols);
            scanned++;
        }

        List<ShotgunSurgeryHistoryEvidence> clusters = accumulators.values().stream()
                .map(accumulator -> accumulator.toEvidence(options.commitWindow()))
                .filter(evidence -> evidence.coChangeCommits() >= options.minCoChanges())
                .filter(evidence -> evidence.ownerCount() >= options.minOwners())
                .sorted(Comparator.comparing(ShotgunSurgeryHistoryEvidence::changeKey))
                .toList();
        Map<String, List<ShotgunSurgeryHistoryEvidence>> byPath = indexByPath(clusters);
        return new HistoryAnalysis(
                true,
                "ok",
                root,
                scanned,
                clusters.size(),
                byPath,
                warnings
        );
    }

    private Optional<Path> repositoryRoot(Path inputPath) {
        Path probe = Files.isRegularFile(inputPath) ? inputPath.getParent() : inputPath;
        if (probe == null) {
            probe = Path.of(".");
        }
        CommandResult result;
        try {
            result = git.run(probe, List.of("rev-parse", "--show-toplevel"));
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(result.stdout().trim()).toAbsolutePath().normalize());
    }

    private List<String> recentCommits(Path root, int commitWindow, List<String> warnings) {
        try {
            CommandResult result = git.run(root, List.of(
                    "log",
                    "--no-merges",
                    "--format=%H",
                    "-n",
                    String.valueOf(commitWindow)
            ));
            if (result.exitCode() != 0) {
                warnings.add("Could not read Git log: " + result.stderr());
                return List.of();
            }
            return result.stdout().lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException exception) {
            warnings.add("Could not read Git log: " + exception.getMessage());
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("Could not read Git log: " + exception.getMessage());
            return List.of();
        }
    }

    private CommitDiff commitDiff(Path root, String commit, List<String> warnings) {
        try {
            CommandResult result = git.run(root, List.of(
                    "show",
                    "--format=",
                    "--unified=0",
                    "--find-renames",
                    commit
            ));
            if (result.exitCode() != 0) {
                warnings.add("Could not read diff for commit " + shortCommit(commit) + ": " + result.stderr());
                return new CommitDiff(Map.of());
            }
            return CommitDiff.parse(result.stdout());
        } catch (IOException exception) {
            warnings.add("Could not read diff for commit " + shortCommit(commit) + ": " + exception.getMessage());
            return new CommitDiff(Map.of());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("Could not read diff for commit " + shortCommit(commit) + ": " + exception.getMessage());
            return new CommitDiff(Map.of());
        }
    }

    private List<ChangedSymbol> changedSymbols(
            Path root,
            String commit,
            CommitDiff diff,
            List<String> warnings
    ) {
        List<ChangedSymbol> symbols = new ArrayList<>();
        for (Map.Entry<String, List<LineRange>> entry : diff.hunksByPath().entrySet()) {
            String relativePath = entry.getKey();
            String language = LanguageDetector.detect(Path.of(relativePath), "auto");
            if (!"java".equals(language) && TreeSitterLanguageRegistry.languageFor(language).isEmpty()) {
                continue;
            }
            Optional<String> source = fileAtCommit(root, commit, relativePath, warnings);
            if (source.isEmpty()) {
                continue;
            }
            try {
                SourceFileAnalysis analysis = "java".equals(language)
                        ? javaAnalyzer.analyze(root.resolve(relativePath), source.get())
                        : treeSitterAnalyzer.analyze(root.resolve(relativePath), language, source.get());
                if (!analysis.parseErrors().isEmpty()) {
                    continue;
                }
                symbols.addAll(mapChangedRanges(relativePath, entry.getValue(), analysis));
            } catch (IOException exception) {
                warnings.add("Could not parse " + relativePath + " at " + shortCommit(commit)
                        + ": " + exception.getMessage());
            }
        }
        return symbols.stream()
                .collect(Collectors.toMap(ChangedSymbol::symbolKey, symbol -> symbol, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private Optional<String> fileAtCommit(Path root, String commit, String relativePath, List<String> warnings) {
        try {
            CommandResult result = git.run(root, List.of("show", commit + ":" + relativePath));
            if (result.exitCode() != 0) {
                return Optional.empty();
            }
            return Optional.of(result.stdout());
        } catch (IOException exception) {
            warnings.add("Could not read " + relativePath + " at " + shortCommit(commit) + ": " + exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("Could not read " + relativePath + " at " + shortCommit(commit) + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private List<ChangedSymbol> mapChangedRanges(
            String relativePath,
            List<LineRange> ranges,
            SourceFileAnalysis analysis
    ) {
        List<ChangedSymbol> symbols = new ArrayList<>();
        for (JavaMethodInfo method : analysis.methods()) {
            if (!intersects(ranges, method.startLine(), method.endLine())) {
                continue;
            }
            String changeKey = changeKey(method.name(), method.parameterTypes().size());
            if (changeKey == null) {
                continue;
            }
            symbols.add(new ChangedSymbol(
                    HistoryAnalysis.normalizePath(relativePath),
                    "method",
                    method.ownerClass(),
                    method.name(),
                    method.parameterTypes().size(),
                    method.startLine(),
                    method.endLine(),
                    changeKey
            ));
        }

        if (!symbols.isEmpty()) {
            return symbols;
        }

        for (JavaClassInfo classInfo : analysis.classes()) {
            if (intersects(ranges, classInfo.startLine(), classInfo.endLine())) {
                symbols.add(new ChangedSymbol(
                        HistoryAnalysis.normalizePath(relativePath),
                        "class",
                        classInfo.name(),
                        classInfo.name(),
                        0,
                        classInfo.startLine(),
                        classInfo.endLine(),
                        null
                ));
            }
        }
        return symbols;
    }

    private void recordCoChanges(
            Map<String, ClusterAccumulator> accumulators,
            String commit,
            List<ChangedSymbol> changedSymbols
    ) {
        changedSymbols.stream()
                .filter(symbol -> symbol.changeKey() != null)
                .collect(Collectors.groupingBy(ChangedSymbol::changeKey, LinkedHashMap::new, Collectors.toList()))
                .forEach((changeKey, symbols) -> {
                    long ownerCount = symbols.stream().map(ChangedSymbol::owner).distinct().count();
                    if (ownerCount < 2) {
                        return;
                    }
                    accumulators.computeIfAbsent(changeKey, ClusterAccumulator::new)
                            .record(commit, symbols);
                });
    }

    private static boolean intersects(List<LineRange> ranges, int startLine, int endLine) {
        return ranges.stream().anyMatch(range -> range.startLine() <= endLine && range.endLine() >= startLine);
    }

    private static String changeKey(String methodName, int parameterCount) {
        List<String> tokens = splitTokens(methodName);
        Optional<String> verb = tokens.stream().filter(CHANGE_VERBS::contains).findFirst();
        if (verb.isEmpty()) {
            return null;
        }
        String family = VERB_FAMILIES.getOrDefault(verb.get(), verb.get());
        List<String> objectTokens = tokens.stream()
                .filter(token -> !CHANGE_VERBS.contains(token))
                .toList();
        String object = objectTokens.isEmpty() ? "operation" : String.join("_", objectTokens);
        return family + "_" + object + "/" + parameterCount;
    }

    private static List<String> splitTokens(String methodName) {
        String normalized = methodName.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static Map<String, List<ShotgunSurgeryHistoryEvidence>> indexByPath(
            List<ShotgunSurgeryHistoryEvidence> clusters
    ) {
        Map<String, List<ShotgunSurgeryHistoryEvidence>> byPath = new LinkedHashMap<>();
        for (ShotgunSurgeryHistoryEvidence cluster : clusters) {
            cluster.symbols().values().stream()
                    .map(ChangedSymbol::path)
                    .distinct()
                    .forEach(path -> byPath.computeIfAbsent(path, ignored -> new ArrayList<>()).add(cluster));
        }
        return byPath.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private static String shortCommit(String commit) {
        return commit.length() <= 12 ? commit : commit.substring(0, 12);
    }

    interface GitCommandRunner {
        CommandResult run(Path workingDirectory, List<String> arguments) throws IOException, InterruptedException;
    }

    record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private record LineRange(int startLine, int endLine) {
    }

    private record CommitDiff(Map<String, List<LineRange>> hunksByPath) {
        private static CommitDiff parse(String diff) {
            Map<String, List<LineRange>> hunksByPath = new LinkedHashMap<>();
            String currentPath = null;
            for (String line : diff.split("\\R")) {
                if (line.startsWith("+++ ")) {
                    currentPath = parseNewPath(line);
                    if (currentPath != null) {
                        hunksByPath.computeIfAbsent(currentPath, ignored -> new ArrayList<>());
                    }
                    continue;
                }
                if (currentPath == null) {
                    continue;
                }
                Matcher matcher = HUNK_HEADER.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                int start = Integer.parseInt(matcher.group(1));
                int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                if (count <= 0) {
                    continue;
                }
                hunksByPath.get(currentPath).add(new LineRange(start, start + count - 1));
            }
            hunksByPath.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            return new CommitDiff(Map.copyOf(hunksByPath));
        }

        private static String parseNewPath(String line) {
            String value = line.substring(4).trim();
            if ("/dev/null".equals(value)) {
                return null;
            }
            if (value.startsWith("b/")) {
                value = value.substring(2);
            }
            return HistoryAnalysis.normalizePath(value);
        }
    }

    private static final class ClusterAccumulator {
        private final String changeKey;
        private final List<String> commits = new ArrayList<>();
        private final Set<String> owners = new LinkedHashSet<>();
        private final Map<String, ChangedSymbol> symbols = new LinkedHashMap<>();
        private final Map<String, Integer> symbolChangeCounts = new LinkedHashMap<>();

        private ClusterAccumulator(String changeKey) {
            this.changeKey = changeKey;
        }

        private void record(String commit, List<ChangedSymbol> changedSymbols) {
            if (!commits.contains(commit)) {
                commits.add(commit);
            }
            for (ChangedSymbol symbol : changedSymbols) {
                owners.add(symbol.owner());
                symbols.putIfAbsent(symbol.symbolKey(), symbol);
                symbolChangeCounts.merge(symbol.symbolKey(), 1, Integer::sum);
            }
        }

        private ShotgunSurgeryHistoryEvidence toEvidence(int commitWindow) {
            return new ShotgunSurgeryHistoryEvidence(
                    changeKey,
                    commitWindow,
                    List.copyOf(commits),
                    List.copyOf(owners),
                    Map.copyOf(symbols),
                    Map.copyOf(symbolChangeCounts)
            );
        }
    }

    private static final class ProcessGitCommandRunner implements GitCommandRunner {
        private static final Duration TIMEOUT = Duration.ofSeconds(20);

        @Override
        public CommandResult run(Path workingDirectory, List<String> arguments) throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(arguments);
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(false)
                    .start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readUtf8(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readUtf8(process.getErrorStream()));
            boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(124, "", "git command timed out");
            }
            return new CommandResult(process.exitValue(), get(stdout), get(stderr).trim());
        }

        private static String readUtf8(java.io.InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "";
            }
        }

        private static String get(CompletableFuture<String> output) throws InterruptedException {
            try {
                return output.get();
            } catch (ExecutionException exception) {
                return "";
            }
        }
    }
}
