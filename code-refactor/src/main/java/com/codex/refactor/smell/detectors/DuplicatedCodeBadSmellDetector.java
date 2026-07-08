package com.codex.refactor.smell.detectors;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DuplicatedCodeBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "default", "return", "throw",
            "try", "catch", "finally", "break", "continue", "new", "class", "struct", "interface",
            "public", "private", "protected", "internal", "static", "final", "readonly", "const",
            "var", "let", "def", "fn", "func", "function", "true", "false", "null", "nil", "none"
    );
    private static final Set<String> TYPE_KEYWORDS = Set.of(
            "byte", "short", "int", "integer", "long", "float", "double", "decimal", "boolean",
            "bool", "char", "string", "str", "void", "object", "number", "any", "unknown"
    );

    public DuplicatedCodeBadSmellDetector() {
        super(BadSmell.DUPLICATED_CODE);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        Map<String, List<DuplicateCandidate>> candidatesByFingerprint = new LinkedHashMap<>();
        context.analysis().methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .map(DuplicatedCodeBadSmellDetector::candidate)
                .flatMap(java.util.Optional::stream)
                .forEach(candidate -> candidatesByFingerprint
                        .computeIfAbsent(candidate.fingerprint(), ignored -> new ArrayList<>())
                        .add(candidate));

        List<SmellFinding> findings = candidatesByFingerprint.values().stream()
                .filter(candidates -> candidates.size() >= 2)
                .map(this::finding)
                .sorted(Comparator.comparing(finding -> (String) finding.location().get("symbol")))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(List<DuplicateCandidate> candidates) {
        DuplicateCandidate first = candidates.getFirst();
        List<String> owners = candidates.stream().map(DuplicateCandidate::methodSymbol).toList();
        int startLine = candidates.stream().mapToInt(candidate -> candidate.method().startLine()).min().orElse(1);
        int endLine = candidates.stream().mapToInt(candidate -> candidate.method().endLine()).max().orElse(1);
        int statementCount = candidates.stream().mapToInt(DuplicateCandidate::statementCount).max().orElse(0);
        boolean exactBody = candidates.stream()
                .map(candidate -> candidate.method().normalizedBody())
                .distinct()
                .count() == 1;
        return DetectorSupport.finding(
                smell(),
                statementCount >= 5 || candidates.size() >= 3 ? "high" : "medium",
                exactBody ? "high" : "medium",
                first.methodSymbol(),
                startLine,
                endLine,
                DetectorSupport.evidence(
                        "signal", exactBody ? "exact_method_body" : "normalized_statement_shape",
                        "duplicates", owners,
                        "duplicate_count", candidates.size(),
                        "statement_count", statementCount,
                        "fingerprint", first.fingerprint(),
                        "normalized_body_length", first.fingerprint().length()
                ),
                exactBody
                        ? "Multiple methods have the same normalized body."
                        : "Multiple methods have the same normalized statement shape after local names and literals are abstracted.",
                "Extract the duplicated behavior behind one shared function, class, or template method."
        );
    }

    private static java.util.Optional<DuplicateCandidate> candidate(JavaMethodInfo method) {
        List<String> statements = statements(method);
        if (statements.size() < 3) {
            return java.util.Optional.empty();
        }
        String fingerprint = canonicalFingerprint(statements);
        if (fingerprint.length() < 24) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new DuplicateCandidate(method, fingerprint, statements.size()));
    }

    private static List<String> statements(JavaMethodInfo method) {
        if (!method.statementShapes().isEmpty()) {
            return method.statementShapes().stream()
                    .map(shape -> {
                        int delimiter = shape.indexOf(':');
                        return delimiter >= 0 ? shape.substring(delimiter + 1) : shape;
                    })
                    .map(String::trim)
                    .filter(statement -> !statement.isBlank())
                    .toList();
        }
        String body = methodBodyText(method.normalizedBody());
        List<String> statements = new ArrayList<>();
        for (String part : body.split("[;{}\\n]")) {
            String statement = part.trim();
            if (!statement.isBlank()
                    && !statement.matches("(?i)^(public|private|protected|internal)?\\s*(static\\s+)?[A-Za-z_$][A-Za-z0-9_$<>\\[\\]?]*\\s+" + Pattern.quote(method.name()) + "\\s*\\(.*")) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private static String canonicalFingerprint(List<String> statements) {
        Map<String, String> localNames = new LinkedHashMap<>();
        List<String> canonical = statements.stream()
                .map(statement -> canonicalize(statement, localNames))
                .filter(statement -> !statement.isBlank())
                .toList();
        return String.join("|", canonical);
    }

    private static String canonicalize(String statement, Map<String, String> localNames) {
        String normalized = statement
                .replaceAll("\"(?:\\\\.|[^\"])*\"", "__STR__")
                .replaceAll("'(?:\\\\.|[^'])*'", "__STR__")
                .replaceAll("\\b\\d+(?:\\.\\d+)?[dDfFlL]?\\b", "__NUM__");
        Matcher matcher = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(normalized);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(normalized, last, matcher.start());
            String token = matcher.group();
            String lower = token.toLowerCase(Locale.ROOT);
            if (KEYWORDS.contains(lower)) {
                result.append(lower);
            } else if (TYPE_KEYWORDS.contains(lower)) {
                result.append("$TYPE");
            } else if (followedByCall(normalized, matcher.end())) {
                result.append("CALL_").append(token);
            } else {
                result.append(localNames.computeIfAbsent(token, ignored -> "$V" + (localNames.size() + 1)));
            }
            last = matcher.end();
        }
        result.append(normalized.substring(last));
        return result.toString()
                .replaceAll("\\s+", "")
                .replace("__STR__", "__LIT__")
                .replace("__NUM__", "__LIT__");
    }

    private static boolean followedByCall(String value, int offset) {
        int index = offset;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index < value.length() && value.charAt(index) == '(';
    }

    private static String methodBodyText(String methodText) {
        String normalized = methodText == null ? "" : methodText.replaceAll("\\s+", " ").trim();
        int openBrace = normalized.indexOf('{');
        int closeBrace = normalized.lastIndexOf('}');
        if (openBrace >= 0 && closeBrace > openBrace) {
            return normalized.substring(openBrace + 1, closeBrace).trim();
        }
        return normalized;
    }

    private record DuplicateCandidate(JavaMethodInfo method, String fingerprint, int statementCount) {
        String methodSymbol() {
            return method.ownerClass() + "." + method.name();
        }
    }
}
