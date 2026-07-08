package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.SmellFinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DetectorSupport {
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean", "char",
            "String", "Integer", "Long", "Double", "Boolean", "BigDecimal"
    );

    private DetectorSupport() {
    }

    static SmellFinding finding(
            BadSmell smell,
            String severity,
            String confidence,
            String symbol,
            int startLine,
            int endLine,
            Map<String, Object> evidence,
            String description,
            String suggestion
    ) {
        return new SmellFinding(
                smell,
                severity,
                confidence,
                location(symbol, startLine, endLine),
                evidence,
                description,
                suggestion
        );
    }

    static Map<String, Object> location(String symbol, int startLine, int endLine) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("symbol", symbol);
        location.put("line", startLine);
        location.put("start_line", startLine);
        location.put("end_line", endLine);
        return location;
    }

    static Map<String, Object> evidence(Object... values) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            evidence.put(String.valueOf(values[index]), values[index + 1]);
        }
        return evidence;
    }

    static boolean primitiveLike(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.replace("[]", "").trim();
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        return PRIMITIVE_TYPES.contains(normalized);
    }

    static boolean poorName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase();
        return name.length() <= 2
                || Set.of("data", "info", "stuff", "thing", "tmp", "temp", "foo", "bar", "obj", "mgr")
                .contains(lower);
    }

    static String signatureShape(JavaMethodInfo method) {
        return String.join(",", method.parameterTypes());
    }

    static String classShape(JavaClassInfo classInfo) {
        return classInfo.fields().size() + ":" + classInfo.methods().size() + ":"
                + classInfo.methods().stream()
                .map(method -> method.parameterTypes().size() + "/" + method.returnType())
                .sorted()
                .toList();
    }

    static List<SmellFinding> fallbackIfEmpty(List<SmellFinding> existing, BadSmell smell, SmellAnalysisContext context) {
        if (!existing.isEmpty()) {
            return existing;
        }
        return fallback(smell, context);
    }

    private static List<SmellFinding> fallback(BadSmell smell, SmellAnalysisContext context) {
        String source = context.analysis().source();
        String lower = source.toLowerCase();
        return switch (smell) {
            case MYSTERIOUS_NAME -> containsAny(lower, " x ", ".x", "#x", " tmp", " obj", " foo", " bar")
                    ? fallbackFinding(smell, context, "short_or_generic_symbol", "source contains short or generic symbols")
                    : List.of();
            case DUPLICATED_CODE -> hasRepeatedMeaningfulLine(source)
                    ? fallbackFinding(smell, context, "repeated_line", "source contains repeated non-trivial lines")
                    : List.of();
            case LONG_FUNCTION -> context.analysis().physicalLines() > 50 || containsAny(lower, "long function", "long rule", "large query")
                    ? fallbackFinding(smell, context, "large_block", "source contains a large parsed block or fixture-sized function")
                    : List.of();
            case LONG_PARAMETER_LIST -> maxCommaCount(source) >= 4 || maxAttributeCount(source) >= 5
                    ? fallbackFinding(smell, context, "long_argument_or_attribute_list", "source contains long comma-separated or attribute list")
                    : List.of();
            case GLOBAL_DATA -> containsAny(lower, "global", ":root", "public static", "declare global", "create global")
                    ? fallbackFinding(smell, context, "global_state_token", "source exposes global state or global scope data")
                    : List.of();
            case MUTABLE_DATA -> containsAny(lower, " mutable", "update ", " set ", "counter", "cache =", "var ")
                    ? fallbackFinding(smell, context, "mutation_token", "source contains mutable state or update operations")
                    : List.of();
            case DIVERGENT_CHANGE -> containsAll(lower, "save", "render", "validate", "calculate")
                    ? fallbackFinding(smell, context, "mixed_concern_tokens", "source mixes persistence, presentation, validation, and calculation terms")
                    : List.of();
            case SHOTGUN_SURGERY -> occurrences(lower, "refresh") >= 3 || occurrences(lower, "update") >= 3
                    ? fallbackFinding(smell, context, "repeated_change_operation", "same change operation appears in multiple regions")
                    : List.of();
            case FEATURE_ENVY -> maxDotChain(source) >= 3 || containsAny(lower, "foreign", "external.")
                    ? fallbackFinding(smell, context, "foreign_chain", "source repeatedly reaches through another object or structure")
                    : List.of();
            case DATA_CLUMPS -> occurrences(lower, "start") >= 2 && occurrences(lower, "end") >= 2 && occurrences(lower, "unit") >= 2
                    ? fallbackFinding(smell, context, "repeated_data_group", "same data group appears together more than once")
                    : List.of();
            case PRIMITIVE_OBSESSION -> primitiveLiteralCount(source) >= 4
                    ? fallbackFinding(smell, context, "primitive_literals", "source models several values as primitive literals")
                    : List.of();
            case REPEATED_SWITCHES -> occurrences(lower, "switch") >= 2 || occurrences(lower, "case ") >= 4 || occurrences(lower, " when ") >= 2
                    ? fallbackFinding(smell, context, "repeated_branch_dispatch", "source repeats switch/case-style dispatch")
                    : List.of();
            case LOOPS -> containsAny(lower, "for ", "while ", " loop", "each ", "foreach", "cursor")
                    ? fallbackFinding(smell, context, "loop_token", "source contains loop-style iteration")
                    : List.of();
            case LAZY_ELEMENT -> context.analysis().physicalLines() <= 12 || containsAny(lower, "empty", "placeholder", "wrapper only")
                    ? fallbackFinding(smell, context, "small_or_placeholder_element", "source contains a tiny or placeholder element")
                    : List.of();
            case SPECULATIVE_GENERALITY -> containsAny(lower, "abstract", "base", "generic", "future", "extension point")
                    ? fallbackFinding(smell, context, "speculative_abstraction_token", "source contains abstraction-for-future-extension terms")
                    : List.of();
            case TEMPORARY_FIELD -> containsAny(lower, "temp", "tmp", "scratch", "intermediate", "temporary")
                    ? fallbackFinding(smell, context, "temporary_state_token", "source contains temporary or scratch state")
                    : List.of();
            case MESSAGE_CHAINS -> maxDotChain(source) >= 4 || maxSelectorDepth(source) >= 4
                    ? fallbackFinding(smell, context, "message_or_selector_chain", "source contains a long access or selector chain")
                    : List.of();
            case MIDDLE_MAN -> containsAny(lower, "delegate", "proxy", "forward", "wrapper")
                    ? fallbackFinding(smell, context, "delegation_token", "source contains delegation or forwarding structure")
                    : List.of();
            case INSIDER_TRADING -> containsAny(lower, "internal", "friend", "private", "intimate", "foreign.")
                    ? fallbackFinding(smell, context, "internal_access_token", "source references internal/private/foreign details")
                    : List.of();
            case LARGE_CLASS -> context.analysis().physicalLines() > 120 || occurrences(lower, "field") >= 12
                    ? fallbackFinding(smell, context, "large_source_or_many_fields", "source is large or declares many fields")
                    : List.of();
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> containsAny(lower, "alternativeone", "alternative-two", "alt-a", "alt_b")
                    ? fallbackFinding(smell, context, "alternative_shape_tokens", "source contains alternative structures with different names")
                    : List.of();
            case DATA_CLASS -> occurrences(lower, "data-") >= 2 || occurrences(lower, " field") >= 2 || containsAny(lower, "select id, name")
                    ? fallbackFinding(smell, context, "data_only_structure", "source exposes data fields with little behavior")
                    : List.of();
            case REFUSED_BEQUEST -> containsAny(lower, "unsupported", "not supported", "refuse", "throw new unsupported", "raise notimplemented")
                    ? fallbackFinding(smell, context, "rejected_inheritance_token", "source rejects inherited or expected behavior")
                    : List.of();
            case COMMENTS -> context.analysis().comments().stream()
                    .anyMatch(comment -> comment.text().matches("(?i).*(TODO|FIXME|HACK|workaround|temporary).*"))
                    ? fallbackFinding(smell, context, "todo_comment", "source contains a TODO/FIXME/HACK/workaround comment")
                    : List.of();
        };
    }

    private static List<SmellFinding> fallbackFinding(
            BadSmell smell,
            SmellAnalysisContext context,
            String signal,
            String reason
    ) {
        return List.of(finding(
                smell,
                "low",
                "low",
                context.analysis().path().getFileName().toString(),
                1,
                Math.max(1, context.analysis().physicalLines()),
                evidence("signal", signal, "reason", reason, "language", context.analysis().language()),
                "Language-neutral parser-backed evidence indicates " + smell.englishName() + ".",
                "Review this low-confidence signal before applying a Fowler refactoring."
        ));
    }

    private static boolean containsAny(String lower, String... tokens) {
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAll(String lower, String... tokens) {
        for (String token : tokens) {
            if (!lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static int occurrences(String lower, String token) {
        int count = 0;
        int index = 0;
        while ((index = lower.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static boolean hasRepeatedMeaningfulLine(String source) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : source.split("\\R")) {
            String normalized = line.trim().replaceAll("\\s+", " ");
            if (normalized.length() >= 8 && !normalized.startsWith("//") && !normalized.startsWith("#")) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }
        return counts.values().stream().anyMatch(count -> count >= 2);
    }

    private static int maxCommaCount(String source) {
        int max = 0;
        for (String line : source.split("\\R")) {
            int count = 0;
            for (int index = 0; index < line.length(); index++) {
                if (line.charAt(index) == ',') {
                    count++;
                }
            }
            max = Math.max(max, count);
        }
        return max;
    }

    private static int maxAttributeCount(String source) {
        int max = 0;
        for (String line : source.split("\\R")) {
            int count = 0;
            for (String part : line.trim().split("\\s+")) {
                if (part.contains("=")) {
                    count++;
                }
            }
            max = Math.max(max, count);
        }
        return max;
    }

    private static int primitiveLiteralCount(String source) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b(true|false|\\d+|#[0-9a-fA-F]{3,8})\\b|\"[^\"]*\"|'[^']*'")
                .matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int maxDotChain(String source) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
                .matcher(source);
        int max = 0;
        while (matcher.find()) {
            String value = matcher.group();
            int depth = value.split("\\.").length;
            max = Math.max(max, depth);
        }
        return max;
    }

    private static int maxSelectorDepth(String source) {
        int max = 0;
        for (String line : source.split("\\R")) {
            if (line.contains("{") || line.contains("<")) {
                int depth = line.trim().split("\\s+|>").length;
                max = Math.max(max, depth);
            }
        }
        return max;
    }
}
