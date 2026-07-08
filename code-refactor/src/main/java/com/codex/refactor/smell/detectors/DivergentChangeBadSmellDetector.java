package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class DivergentChangeBadSmellDetector extends BookBadSmellDetector {
    private static final int MIN_CONCERNS = 3;
    private static final int MIN_DISTINCT_METHODS = 3;
    private static final List<ConcernSpec> CONCERNS = List.of(
            new ConcernSpec("persistence", Set.of(
                    "save", "load", "persist", "repository", "repo", "dao", "db", "database",
                    "sql", "query", "insert", "delete", "select", "store", "fetch"
            )),
            new ConcernSpec("presentation", Set.of(
                    "render", "view", "html", "ui", "screen", "template", "page", "component",
                    "dom", "css", "style", "display"
            )),
            new ConcernSpec("validation", Set.of(
                    "validate", "validator", "check", "verify", "ensure", "guard", "rule", "valid"
            )),
            new ConcernSpec("calculation", Set.of(
                    "calculate", "calculator", "compute", "price", "total", "score", "tax",
                    "discount", "sum", "amount", "metric"
            )),
            new ConcernSpec("integration", Set.of(
                    "send", "http", "request", "email", "notify", "notification", "publish",
                    "client", "api", "remote", "webhook", "queue", "message"
            )),
            new ConcernSpec("serialization", Set.of(
                    "parse", "format", "serialize", "deserialize", "json", "xml", "csv",
                    "encode", "decode", "mapper"
            ))
    );

    public DivergentChangeBadSmellDetector() {
        super(BadSmell.DIVERGENT_CHANGE);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        ownerGroups(context).values().forEach(group -> analyzeOwner(group, context).ifPresent(findings::add));
        if (!findings.isEmpty()) {
            return List.copyOf(findings);
        }
        if (!context.analysis().methods().isEmpty() && !allowsSourceLineFallback(context.analysis().language())) {
            return List.of();
        }
        return sourceLineFallback(context);
    }

    private java.util.Optional<SmellFinding> analyzeOwner(OwnerGroup group, SmellAnalysisContext context) {
        List<JavaMethodInfo> behavioralMethods = group.methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .toList();
        if (behavioralMethods.size() < MIN_DISTINCT_METHODS) {
            return java.util.Optional.empty();
        }

        Map<String, ConcernEvidence> evidenceByConcern = emptyConcernEvidence();
        Map<String, CollaboratorEvidence> collaborators = new LinkedHashMap<>();
        for (JavaMethodInfo method : behavioralMethods) {
            List<SourceProjectIndex.CallEdge> callEdges = context.projectIndex().callEdgesFrom(method);
            classify(method, callEdges).forEach((concern, signals) ->
                    evidenceByConcern.get(concern).add(method.name(), signals));
            recordCollaborators(method, callEdges, collaborators);
        }

        List<ConcernEvidence> qualified = evidenceByConcern.values().stream()
                .filter(ConcernEvidence::qualified)
                .toList();
        Set<String> distinctMethods = new LinkedHashSet<>();
        qualified.forEach(evidence -> distinctMethods.addAll(evidence.methods()));

        if (qualified.size() < MIN_CONCERNS || distinctMethods.size() < MIN_DISTINCT_METHODS) {
            return java.util.Optional.empty();
        }

        String confidence = qualified.stream().filter(ConcernEvidence::hasStructuralSignal).count() >= MIN_CONCERNS
                ? "high"
                : "medium";
        String severity = qualified.size() >= 4 ? "high" : "medium";
        return java.util.Optional.of(DetectorSupport.finding(
                smell(),
                severity,
                confidence,
                group.name(),
                group.startLine(),
                group.endLine(),
                DetectorSupport.evidence(
                        "concern_count", qualified.size(),
                        "behavioral_method_count", behavioralMethods.size(),
                        "concern_methods", concernMethods(qualified),
                        "concern_signals", concernSignals(qualified),
                        "collaborator_clusters", collaboratorClusters(collaborators)
                ),
                "Class or module has methods grouped around several independent change concerns.",
                "Split the owner around cohesive reasons to change, for example persistence, presentation, validation, calculation, integration, or serialization."
        ));
    }

    private static Map<String, OwnerGroup> ownerGroups(SmellAnalysisContext context) {
        Map<String, OwnerGroup> groups = new LinkedHashMap<>();
        Set<String> classNames = new LinkedHashSet<>();
        for (JavaClassInfo classInfo : context.analysis().classes()) {
            classNames.add(classInfo.name());
            groups.put(classInfo.name(), new OwnerGroup(
                    classInfo.name(),
                    classInfo.startLine(),
                    classInfo.endLine(),
                    classInfo.methods()
            ));
        }
        for (JavaMethodInfo method : context.analysis().methods()) {
            if (classNames.contains(method.ownerClass())) {
                continue;
            }
            OwnerGroup group = groups.computeIfAbsent(method.ownerClass(), owner -> new OwnerGroup(
                    "<module>".equals(owner) ? context.analysis().path().getFileName().toString() : owner,
                    1,
                    Math.max(1, context.analysis().physicalLines()),
                    new ArrayList<>()
            ));
            group.methods().add(method);
        }
        return groups;
    }

    private static Map<String, Set<String>> classify(JavaMethodInfo method, List<SourceProjectIndex.CallEdge> callEdges) {
        Map<String, Set<String>> signalsByConcern = emptySignalMap();
        addValueSignals(signalsByConcern, "method_name", method.name());
        method.methodCallCounts().keySet().forEach(call -> addValueSignals(signalsByConcern, "call", call));
        method.ownFieldReads().forEach(field -> addValueSignals(signalsByConcern, "field_read", field));
        method.ownFieldWrites().forEach(field -> addValueSignals(signalsByConcern, "field_write", field));
        method.foreignMemberAccessCounts().keySet()
                .forEach(root -> addValueSignals(signalsByConcern, "foreign_root", root));
        callEdges.forEach(edge -> addCallGraphSignals(signalsByConcern, edge));
        addBodySignals(signalsByConcern, method);
        signalsByConcern.values().removeIf(Set::isEmpty);
        return signalsByConcern;
    }

    private static void addCallGraphSignals(
            Map<String, Set<String>> signalsByConcern,
            SourceProjectIndex.CallEdge edge
    ) {
        addValueSignals(signalsByConcern, "receiver_root", edge.call().receiverRoot());
        addValueSignals(signalsByConcern, "receiver_type", edge.call().receiverType());
        addValueSignals(signalsByConcern, "target_owner", edge.targetOwner());
        addValueSignals(signalsByConcern, "target_method", edge.targetMethod());
        addValueSignals(signalsByConcern, "call_graph", edge.call().methodName());
    }

    private static void recordCollaborators(
            JavaMethodInfo method,
            List<SourceProjectIndex.CallEdge> callEdges,
            Map<String, CollaboratorEvidence> collaborators
    ) {
        for (SourceProjectIndex.CallEdge edge : callEdges) {
            String key = collaboratorKey(edge);
            if (key.isBlank() || key.equals(method.ownerClass())) {
                continue;
            }
            collaborators.computeIfAbsent(key, CollaboratorEvidence::new)
                    .add(method.name(), edge.call().receiverRoot(), edge.call().methodName(), edge.resolved());
        }
    }

    private static String collaboratorKey(SourceProjectIndex.CallEdge edge) {
        if (!edge.targetOwner().isBlank()) {
            return edge.targetOwner();
        }
        if (!edge.call().receiverType().isBlank()) {
            return SourceProjectIndex.simpleTypeName(edge.call().receiverType());
        }
        if (!edge.call().receiverRoot().isBlank()
                && !"this".equals(edge.call().receiverRoot())
                && !"super".equals(edge.call().receiverRoot())) {
            return edge.call().receiverRoot();
        }
        return "";
    }

    private static void addValueSignals(Map<String, Set<String>> signalsByConcern, String source, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Set<String> tokens = tokens(value);
        for (ConcernSpec spec : CONCERNS) {
            for (String keyword : spec.keywords()) {
                if (tokens.contains(keyword)) {
                    signalsByConcern.get(spec.name()).add(source + ":" + value);
                    break;
                }
            }
        }
    }

    private static void addBodySignals(Map<String, Set<String>> signalsByConcern, JavaMethodInfo method) {
        String body = method.normalizedBody();
        if (body == null || body.isBlank()) {
            return;
        }
        body = body.replace(method.name(), " ");
        Set<String> bodyTokens = tokens(body);
        for (ConcernSpec spec : CONCERNS) {
            for (String keyword : spec.keywords()) {
                if (bodyTokens.contains(keyword)) {
                    signalsByConcern.get(spec.name()).add("body_token:" + keyword);
                    break;
                }
            }
        }
    }

    private static List<SmellFinding> sourceLineFallback(SmellAnalysisContext context) {
        Map<String, Set<Integer>> linesByConcern = new LinkedHashMap<>();
        CONCERNS.forEach(spec -> linesByConcern.put(spec.name(), new LinkedHashSet<>()));
        String[] lines = context.analysis().source().split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank() || commentLine(line)) {
                continue;
            }
            Set<String> tokens = tokens(line);
            for (ConcernSpec spec : CONCERNS) {
                if (spec.keywords().stream().anyMatch(tokens::contains)) {
                    linesByConcern.get(spec.name()).add(index + 1);
                }
            }
        }

        Map<String, List<Integer>> qualified = new LinkedHashMap<>();
        Set<Integer> distinctLines = new LinkedHashSet<>();
        linesByConcern.forEach((concern, lineNumbers) -> {
            if (!lineNumbers.isEmpty()) {
                qualified.put(concern, List.copyOf(lineNumbers));
                distinctLines.addAll(lineNumbers);
            }
        });
        if (qualified.size() < MIN_CONCERNS || distinctLines.size() < MIN_DISTINCT_METHODS) {
            return List.of();
        }
        return List.of(DetectorSupport.finding(
                BadSmell.DIVERGENT_CHANGE,
                "low",
                "low",
                context.analysis().path().getFileName().toString(),
                1,
                Math.max(1, context.analysis().physicalLines()),
                DetectorSupport.evidence(
                        "concern_count", qualified.size(),
                        "concern_lines", qualified
                ),
                "Source contains separate non-comment lines for several independent change concerns.",
                "Review this low-confidence signal before splitting the file; richer method or class evidence is preferred."
        ));
    }

    private static boolean commentLine(String line) {
        return line.startsWith("//")
                || line.startsWith("#")
                || line.startsWith("--")
                || line.startsWith("/*")
                || line.startsWith("*")
                || line.startsWith("<!--");
    }

    private static boolean allowsSourceLineFallback(String language) {
        return "html".equals(language)
                || "css".equals(language)
                || "vue".equals(language)
                || "sql".equals(language)
                || (language != null && language.startsWith("sql:"));
    }

    private static Map<String, ConcernEvidence> emptyConcernEvidence() {
        Map<String, ConcernEvidence> evidence = new LinkedHashMap<>();
        CONCERNS.forEach(spec -> evidence.put(spec.name(), new ConcernEvidence(spec.name())));
        return evidence;
    }

    private static Map<String, Set<String>> emptySignalMap() {
        Map<String, Set<String>> signals = new LinkedHashMap<>();
        CONCERNS.forEach(spec -> signals.put(spec.name(), new LinkedHashSet<>()));
        return signals;
    }

    private static Map<String, List<String>> concernMethods(List<ConcernEvidence> concerns) {
        Map<String, List<String>> methods = new LinkedHashMap<>();
        concerns.forEach(concern -> methods.put(concern.name(), List.copyOf(concern.methods())));
        return methods;
    }

    private static Map<String, List<String>> concernSignals(List<ConcernEvidence> concerns) {
        Map<String, List<String>> signals = new LinkedHashMap<>();
        concerns.forEach(concern -> signals.put(concern.name(), List.copyOf(concern.signals())));
        return signals;
    }

    private static Map<String, Map<String, Object>> collaboratorClusters(Map<String, CollaboratorEvidence> collaborators) {
        Map<String, Map<String, Object>> clusters = new LinkedHashMap<>();
        collaborators.values().stream()
                .filter(CollaboratorEvidence::qualified)
                .forEach(collaborator -> clusters.put(collaborator.name(), DetectorSupport.evidence(
                        "methods", List.copyOf(collaborator.methods()),
                        "receiver_roots", List.copyOf(collaborator.receiverRoots()),
                        "calls", List.copyOf(collaborator.calls()),
                        "resolved_call_count", collaborator.resolvedCallCount()
                )));
        return clusters;
    }

    private static Set<String> tokens(String value) {
        String camelSpaced = Pattern.compile("([a-z0-9])([A-Z])").matcher(value).replaceAll("$1 $2");
        String normalized = camelSpaced.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.trim().split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private record ConcernSpec(String name, Set<String> keywords) {
    }

    private record OwnerGroup(String name, int startLine, int endLine, List<JavaMethodInfo> methods) {
    }

    private static final class ConcernEvidence {
        private final String name;
        private final Set<String> methods = new LinkedHashSet<>();
        private final Set<String> signals = new LinkedHashSet<>();

        private ConcernEvidence(String name) {
            this.name = name;
        }

        private void add(String methodName, Set<String> methodSignals) {
            if (methodSignals.isEmpty()) {
                return;
            }
            methods.add(methodName);
            signals.addAll(methodSignals);
        }

        private String name() {
            return name;
        }

        private Set<String> methods() {
            return methods;
        }

        private Set<String> signals() {
            return signals;
        }

        private boolean qualified() {
            return !methods.isEmpty() && signals.size() >= 2;
        }

        private boolean hasStructuralSignal() {
            return signals.stream().anyMatch(signal -> signal.startsWith("call:")
                    || signal.startsWith("field_read:")
                    || signal.startsWith("field_write:")
                    || signal.startsWith("foreign_root:")
                    || signal.startsWith("receiver_root:")
                    || signal.startsWith("receiver_type:")
                    || signal.startsWith("target_owner:")
                    || signal.startsWith("target_method:")
                    || signal.startsWith("call_graph:"));
        }
    }

    private static final class CollaboratorEvidence {
        private final String name;
        private final Set<String> methods = new LinkedHashSet<>();
        private final Set<String> receiverRoots = new LinkedHashSet<>();
        private final Set<String> calls = new LinkedHashSet<>();
        private int resolvedCallCount;

        private CollaboratorEvidence(String name) {
            this.name = name;
        }

        private void add(String method, String receiverRoot, String call, boolean resolved) {
            methods.add(method);
            if (receiverRoot != null && !receiverRoot.isBlank()) {
                receiverRoots.add(receiverRoot);
            }
            if (call != null && !call.isBlank()) {
                calls.add(call);
            }
            if (resolved) {
                resolvedCallCount++;
            }
        }

        private String name() {
            return name;
        }

        private Set<String> methods() {
            return methods;
        }

        private Set<String> receiverRoots() {
            return receiverRoots;
        }

        private Set<String> calls() {
            return calls;
        }

        private int resolvedCallCount() {
            return resolvedCallCount;
        }

        private boolean qualified() {
            return !methods.isEmpty() && (!calls.isEmpty() || resolvedCallCount > 0);
        }
    }
}
