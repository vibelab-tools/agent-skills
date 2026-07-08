package com.codex.refactor.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record MessageChainInfo(
        String root,
        List<String> selectors,
        int depth,
        int line,
        String chainText,
        String kind
) {
    private static final Set<String> COMMON_PACKAGE_ROOTS = Set.of(
            "java", "javax", "jakarta", "org", "com", "net", "io"
    );
    private static final Set<String> FLUENT_SELECTORS = Set.of(
            "stream", "parallelStream", "filter", "map", "flatMap", "collect", "toList", "toSet", "toMap",
            "sorted", "distinct", "limit", "skip", "peek", "forEach", "reduce", "findFirst", "findAny",
            "builder", "build", "with", "then", "thenApply", "thenCompose", "thenAccept", "exceptionally",
            "catch", "finally", "of", "ofNullable", "orElse", "orElseGet", "orElseThrow", "ifPresent"
    );

    public static Optional<MessageChainInfo> fromExpression(
            String expression,
            int line,
            Set<String> knownVariables
    ) {
        List<String> parts = splitTopLevelDots(expression);
        if (parts.size() < 2) {
            return Optional.empty();
        }
        String root = cleanPart(parts.getFirst());
        if (root.isBlank()) {
            return Optional.empty();
        }
        List<String> selectors = parts.stream()
                .skip(1)
                .map(MessageChainInfo::cleanPart)
                .filter(selector -> !selector.isBlank())
                .toList();
        if (selectors.isEmpty()) {
            return Optional.empty();
        }
        String kind = classify(root, selectors, knownVariables);
        return Optional.of(new MessageChainInfo(
                root,
                List.copyOf(selectors),
                1 + selectors.size(),
                line,
                compact(expression),
                kind
        ));
    }

    public boolean objectNavigation() {
        return "object_navigation".equals(kind);
    }

    public List<String> pathParts() {
        List<String> parts = new ArrayList<>();
        parts.add(root);
        parts.addAll(selectors);
        return parts;
    }

    public String prefixKey(int depth) {
        List<String> parts = pathParts();
        if (parts.size() < depth) {
            return String.join(".", parts);
        }
        return String.join(".", parts.subList(0, depth));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("root", root);
        json.put("selectors", selectors);
        json.put("depth", depth);
        json.put("line", line);
        json.put("chain_text", chainText);
        json.put("kind", kind);
        return json;
    }

    private static List<String> splitTopLevelDots(String expression) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        SplitState state = SplitState.outside();
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (state.inString()) {
                state = state.consumeStringCharacter(character, expression, index);
                current.append(character);
                continue;
            }
            if (SplitState.stringStart(character)) {
                state = state.startString(character);
                current.append(character);
                continue;
            }
            state = state.updateNesting(character);
            if (state.topLevelDot(character)) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private record SplitState(int parenDepth, int bracketDepth, boolean inString, char quote) {
        static SplitState outside() {
            return new SplitState(0, 0, false, '\0');
        }

        static boolean stringStart(char character) {
            return character == '"' || character == '\'';
        }

        SplitState consumeStringCharacter(char character, String expression, int index) {
            if (character == quote && (index == 0 || expression.charAt(index - 1) != '\\')) {
                return new SplitState(parenDepth, bracketDepth, false, quote);
            }
            return this;
        }

        SplitState startString(char character) {
            return new SplitState(parenDepth, bracketDepth, true, character);
        }

        SplitState updateNesting(char character) {
            if (character == '(') {
                return new SplitState(parenDepth + 1, bracketDepth, inString, quote);
            }
            if (character == ')' && parenDepth > 0) {
                return new SplitState(parenDepth - 1, bracketDepth, inString, quote);
            }
            if (character == '[') {
                return new SplitState(parenDepth, bracketDepth + 1, inString, quote);
            }
            if (character == ']' && bracketDepth > 0) {
                return new SplitState(parenDepth, bracketDepth - 1, inString, quote);
            }
            return this;
        }

        boolean topLevelDot(char character) {
            return character == '.' && parenDepth == 0 && bracketDepth == 0;
        }
    }

    private static String cleanPart(String part) {
        String cleaned = part.trim();
        int paren = cleaned.indexOf('(');
        if (paren >= 0) {
            cleaned = cleaned.substring(0, paren);
        }
        cleaned = cleaned.replaceAll("^new\\s+", "");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_$]", "");
        return cleaned;
    }

    private static String classify(String root, List<String> selectors, Set<String> knownVariables) {
        if ("this".equals(root) || "super".equals(root) || "self".equals(root)) {
            return "self_access";
        }
        if (COMMON_PACKAGE_ROOTS.contains(root) && selectors.stream().limit(2).anyMatch(MessageChainInfo::lowercaseStart)) {
            return "package_access";
        }
        if (!knownVariables.contains(root) && Character.isUpperCase(root.charAt(0))) {
            return "static_access";
        }
        long fluentCount = selectors.stream().filter(FLUENT_SELECTORS::contains).count();
        if (fluentCount >= 2 && fluentCount * 2 >= selectors.size()) {
            return "fluent_api";
        }
        return "object_navigation";
    }

    private static boolean lowercaseStart(String value) {
        return !value.isBlank() && Character.isLowerCase(value.charAt(0));
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
