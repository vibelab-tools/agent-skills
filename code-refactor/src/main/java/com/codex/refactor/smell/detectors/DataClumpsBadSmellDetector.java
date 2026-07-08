package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
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

public final class DataClumpsBadSmellDetector extends BookBadSmellDetector {
    private static final Map<String, String> NAME_SYNONYMS = Map.ofEntries(
            Map.entry("begin", "start"),
            Map.entry("from", "start"),
            Map.entry("first", "start"),
            Map.entry("finish", "end"),
            Map.entry("to", "end"),
            Map.entry("last", "end"),
            Map.entry("stop", "end"),
            Map.entry("zipcode", "zip"),
            Map.entry("postal", "zip"),
            Map.entry("postalcode", "zip"),
            Map.entry("postcode", "zip"),
            Map.entry("lng", "longitude"),
            Map.entry("lon", "longitude"),
            Map.entry("lat", "latitude"),
            Map.entry("amount", "value"),
            Map.entry("qty", "quantity")
    );
    private static final Set<String> NOISY_NAMES = Set.of(
            "id", "type", "name", "value", "data", "info", "flag", "status", "count", "index",
            "message", "level", "action", "actor", "ip", "trace", "traceid", "logger",
            "in", "out", "err"
    );
    private static final Set<String> INFRASTRUCTURE_NAMES = Set.of(
            "logger", "log", "repository", "repo", "dao", "mapper", "factory", "client",
            "service", "gateway", "adapter", "config", "configuration", "settings",
            "request", "response", "context", "ctx", "input", "output", "stream",
            "reader", "writer", "clock", "metrics", "meter", "tracer", "registry",
            "cache", "executor", "scheduler", "dispatcher"
    );
    private static final Set<String> INFRASTRUCTURE_TYPES = Set.of(
            "inputstream", "outputstream", "printstream", "printwriter", "reader", "writer",
            "logger", "log", "repository", "dao", "mapper", "factory", "client",
            "service", "gateway", "adapter", "config", "configuration", "settings",
            "request", "response", "context", "clock", "meterregistry", "tracer",
            "cache", "executor", "executorservice", "scheduler", "dispatcher"
    );
    private static final List<Theme> THEMES = List.of(
            new Theme("range", Set.of("start", "end", "unit")),
            new Theme("address", Set.of("street", "city", "zip", "state", "country")),
            new Theme("period", Set.of("start", "end", "date", "time", "timezone")),
            new Theme("money", Set.of("value", "currency", "amount", "tax", "price")),
            new Theme("coordinate", Set.of("latitude", "longitude", "x", "y", "z")),
            new Theme("contact", Set.of("email", "phone", "mobile", "name"))
    );

    public DataClumpsBadSmellDetector() {
        super(BadSmell.DATA_CLUMPS);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        Map<String, DataGroupAccumulator> groups = new LinkedHashMap<>();
        context.analysis().methods().stream()
                .filter(method -> method.parameterNames().size() >= 3)
                .forEach(method -> candidateGroups(method).forEach(group ->
                        groups.computeIfAbsent(group.groupKey(), DataGroupAccumulator::new)
                                .add("parameter_group", method.ownerClass() + "." + method.name(), group)));
        context.analysis().methods().stream()
                .forEach(method -> candidateArgumentGroups(method).forEach(group ->
                        groups.computeIfAbsent(group.groupKey(), DataGroupAccumulator::new)
                                .add("argument_group", method.ownerClass() + "." + method.name(), group)));

        context.analysis().classes().stream()
                .filter(classInfo -> classInfo.fields().size() >= 3)
                .forEach(classInfo -> candidateGroups(classInfo).forEach(group ->
                        groups.computeIfAbsent(group.groupKey(), DataGroupAccumulator::new)
                                .add("field_group", classInfo.name(), group)));

        List<SmellFinding> findings = groups.values().stream()
                .filter(DataGroupAccumulator::reportable)
                .sorted(Comparator.comparing(DataGroupAccumulator::groupKey))
                .map(accumulator -> finding(accumulator))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(DataGroupAccumulator accumulator) {
        String severity = accumulator.hasBothKinds() || accumulator.occurrences() >= 3 ? "high" : "medium";
        String confidence = accumulator.hasStrongTheme() || accumulator.hasBothKinds() ? "high" : "medium";
        return DetectorSupport.finding(
                smell(),
                severity,
                confidence,
                accumulator.firstLocation(),
                1,
                1,
                accumulator.evidence(),
                "The same group of data items travels together through multiple methods or classes.",
                "Introduce Parameter Object or Extract Class to represent this data group explicitly."
        );
    }

    private static List<DataGroup> candidateGroups(JavaMethodInfo method) {
        List<DataItem> items = new ArrayList<>();
        for (int index = 0; index < method.parameterNames().size(); index++) {
            items.add(DataItem.from(method.parameterTypes().get(index), method.parameterNames().get(index)));
        }
        return groupsFrom(items);
    }

    private static List<DataGroup> candidateArgumentGroups(JavaMethodInfo method) {
        String body = method.normalizedBody();
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<DataGroup> groups = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b[A-Za-z_$][A-Za-z0-9_$.]*\\s*\\(([^()]*)\\)")
                .matcher(body);
        while (matcher.find()) {
            List<String> arguments = splitArguments(matcher.group(1));
            if (arguments.size() < 3) {
                continue;
            }
            List<DataItem> items = arguments.stream()
                    .filter(argument -> argument.matches(".*[A-Za-z_$].*"))
                    .map(argument -> DataItem.from("unknown", lastSelector(argument)))
                    .toList();
            groups.addAll(groupsFrom(items));
        }
        return groups;
    }

    private static List<DataGroup> candidateGroups(JavaClassInfo classInfo) {
        List<DataItem> items = classInfo.fields().stream()
                .filter(field -> !field.staticField())
                .map(field -> DataItem.from(field.type(), field.name()))
                .toList();
        return groupsFrom(items);
    }

    private static List<DataGroup> groupsFrom(List<DataItem> items) {
        List<DataItem> meaningful = items.stream()
                .filter(item -> !NOISY_NAMES.contains(item.normalizedName()))
                .toList();
        if (meaningful.size() < 3) {
            return List.of();
        }

        Optional<DataGroup> themed = themedGroup(meaningful);
        if (themed.isPresent()) {
            return List.of(themed.get());
        }

        if (infrastructureDependencyGroup(meaningful)) {
            return List.of();
        }

        if (semanticDensity(meaningful) < 0.67) {
            return List.of();
        }
        return List.of(new DataGroup("generic", meaningful));
    }

    private static Optional<DataGroup> themedGroup(List<DataItem> items) {
        Set<String> names = items.stream().map(DataItem::normalizedName).collect(Collectors.toSet());
        return THEMES.stream()
                .map(theme -> {
                    List<DataItem> matched = items.stream()
                            .filter(item -> theme.names().contains(item.normalizedName()))
                            .toList();
                    return new ThemedMatch(theme.name(), matched);
                })
                .filter(match -> match.items().size() >= 3)
                .max(Comparator.comparingInt(match -> match.items().size()))
                .map(match -> new DataGroup(match.themeName(), match.items()));
    }

    private static double semanticDensity(List<DataItem> items) {
        long meaningful = items.stream().filter(item -> !item.normalizedName().isBlank()).count();
        return (double) meaningful / Math.max(1, items.size());
    }

    private static boolean infrastructureDependencyGroup(List<DataItem> items) {
        long infrastructureItems = items.stream()
                .filter(DataItem::infrastructureDependency)
                .count();
        return infrastructureItems >= 3 && infrastructureItems == items.size();
    }

    private record DataItem(String type, String originalName, String normalizedName) {
        static DataItem from(String type, String name) {
            return new DataItem(normalizeType(type), name, normalizeName(name));
        }

        boolean infrastructureDependency() {
            String lowerType = simpleType(type).toLowerCase();
            String lowerOriginalName = originalName == null ? "" : originalName.toLowerCase();
            return INFRASTRUCTURE_NAMES.contains(normalizedName)
                    || INFRASTRUCTURE_TYPES.contains(lowerType)
                    || INFRASTRUCTURE_NAMES.stream().anyMatch(lowerOriginalName::contains)
                    || INFRASTRUCTURE_TYPES.stream().anyMatch(lowerType::contains);
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "name", originalName,
                    "normalized_name", normalizedName,
                    "type", type
            );
        }
    }

    private record DataGroup(String theme, List<DataItem> items) {
        String groupKey() {
            return theme + ":" + items.stream()
                    .map(item -> item.normalizedName() + "/" + item.type())
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        String suggestedObjectName() {
            if (!"generic".equals(theme)) {
                return Character.toUpperCase(theme.charAt(0)) + theme.substring(1);
            }
            return "ParameterObject";
        }

        List<Map<String, Object>> itemsJson() {
            return items.stream().map(DataItem::toJson).toList();
        }
    }

    private static final class DataGroupAccumulator {
        private final String groupKey;
        private final Set<String> methods = new LinkedHashSet<>();
        private final Set<String> argumentMethods = new LinkedHashSet<>();
        private final Set<String> classes = new LinkedHashSet<>();
        private final List<DataGroup> groups = new ArrayList<>();

        private DataGroupAccumulator(String groupKey) {
            this.groupKey = groupKey;
        }

        private String groupKey() {
            return groupKey;
        }

        private void add(String kind, String location, DataGroup group) {
            groups.add(group);
            if ("parameter_group".equals(kind)) {
                methods.add(location);
            } else if ("argument_group".equals(kind)) {
                argumentMethods.add(location);
            } else {
                classes.add(location);
            }
        }

        private boolean reportable() {
            return groups.getFirst().items().size() >= 3
                    && (occurrences() >= 2 || hasBothKinds())
                    && (hasStrongTheme() || hasBothKinds());
        }

        private int occurrences() {
            return groups.size();
        }

        private boolean hasBothKinds() {
            int kinds = 0;
            if (!methods.isEmpty()) {
                kinds++;
            }
            if (!argumentMethods.isEmpty()) {
                kinds++;
            }
            if (!classes.isEmpty()) {
                kinds++;
            }
            return kinds >= 2;
        }

        private boolean hasStrongTheme() {
            return !"generic".equals(groups.getFirst().theme());
        }

        private String firstLocation() {
            if (!methods.isEmpty()) {
                return methods.iterator().next();
            }
            if (!argumentMethods.isEmpty()) {
                return argumentMethods.iterator().next();
            }
            return classes.iterator().next();
        }

        private Map<String, Object> evidence() {
            DataGroup group = groups.getFirst();
            String signal;
            if (hasBothKinds()) {
                signal = "mixed_data_group";
            } else if (!argumentMethods.isEmpty()) {
                signal = "repeated_argument_group";
            } else if (methods.isEmpty()) {
                signal = "repeated_field_group";
            } else {
                signal = "repeated_parameter_group";
            }
            return DetectorSupport.evidence(
                    "signal", signal,
                    "group_key", groupKey,
                    "theme", group.theme(),
                    "group_size", group.items().size(),
                    "occurrences", occurrences(),
                    "items", group.itemsJson(),
                    "methods", List.copyOf(methods),
                    "argument_methods", List.copyOf(argumentMethods),
                    "classes", List.copyOf(classes),
                    "suggested_object_name", group.suggestedObjectName()
            );
        }
    }

    private record Theme(String name, Set<String> names) {
    }

    private record ThemedMatch(String themeName, List<DataItem> items) {
    }

    private static String normalizeName(String name) {
        List<String> tokens = splitWords(name);
        String joined = String.join("", tokens);
        if (NAME_SYNONYMS.containsKey(joined)) {
            return NAME_SYNONYMS.get(joined);
        }
        String normalized = tokens.stream()
                .filter(token -> !token.isBlank())
                .reduce((first, second) -> second)
                .orElse("")
                .toLowerCase();
        return NAME_SYNONYMS.getOrDefault(normalized, normalized);
    }

    private static List<String> splitWords(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(String::toLowerCase)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "unknown";
        }
        return type.replace("java.lang.", "")
                .replaceAll("\\s+", "")
                .replaceAll("<.*>", "<>")
                .replace("[]", "Array");
    }

    private static String simpleType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String normalized = type.replace("[]", "");
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized;
    }

    private static List<String> splitArguments(String argumentText) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < argumentText.length(); index++) {
            char character = argumentText.charAt(index);
            if (character == '(' || character == '[' || character == '{') {
                depth++;
            } else if ((character == ')' || character == ']' || character == '}') && depth > 0) {
                depth--;
            }
            if (character == ',' && depth == 0) {
                addArgument(arguments, current);
            } else {
                current.append(character);
            }
        }
        addArgument(arguments, current);
        return arguments;
    }

    private static void addArgument(List<String> arguments, StringBuilder current) {
        String argument = current.toString().trim();
        if (!argument.isBlank()) {
            arguments.add(argument);
        }
        current.setLength(0);
    }

    private static String lastSelector(String value) {
        String normalized = value.replaceAll("\\s+", "")
                .replaceFirst("^this\\.", "")
                .replaceFirst("^self\\.", "");
        int dot = Math.max(normalized.lastIndexOf('.'), normalized.lastIndexOf("->"));
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.replaceAll("[^A-Za-z0-9_$]", "");
    }
}
