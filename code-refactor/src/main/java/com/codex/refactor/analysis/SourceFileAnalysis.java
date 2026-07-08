package com.codex.refactor.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SourceFileAnalysis {
    private final Path path;
    private final String language;
    private final String parser;
    private final String parserId;
    private final String source;
    private final int physicalLines;
    private final int blankLines;
    private final int commentLines;
    private final List<JavaClassInfo> classes = new ArrayList<>();
    private final List<JavaMethodInfo> methods = new ArrayList<>();
    private final List<JavaFieldInfo> fields = new ArrayList<>();
    private final List<CommentInfo> comments = new ArrayList<>();
    private final List<ParseError> parseErrors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public SourceFileAnalysis(
            Path path,
            String language,
            String parser,
            String parserId,
            String source,
            int physicalLines,
            int blankLines,
            int commentLines
    ) {
        this.path = path;
        this.language = language;
        this.parser = parser;
        this.parserId = parserId;
        this.source = source;
        this.physicalLines = physicalLines;
        this.blankLines = blankLines;
        this.commentLines = commentLines;
    }

    public Path path() {
        return path;
    }

    public String language() {
        return language;
    }

    public String parser() {
        return parser;
    }

    public String parserId() {
        return parserId;
    }

    public String source() {
        return source;
    }

    public int physicalLines() {
        return physicalLines;
    }

    public int blankLines() {
        return blankLines;
    }

    public int commentLines() {
        return commentLines;
    }

    public List<JavaClassInfo> classes() {
        return classes;
    }

    public List<JavaMethodInfo> methods() {
        return methods;
    }

    public List<JavaFieldInfo> fields() {
        return fields;
    }

    public List<CommentInfo> comments() {
        return comments;
    }

    public List<ParseError> parseErrors() {
        return parseErrors;
    }

    public List<String> warnings() {
        return warnings;
    }

    public String status() {
        return parseErrors.isEmpty() ? "ok" : "parse_error";
    }

    public int maxCyclomaticComplexity() {
        return methods.stream().mapToInt(JavaMethodInfo::cyclomaticComplexity).max().orElse(0);
    }

    public int maxCognitiveComplexity() {
        return methods.stream().mapToInt(JavaMethodInfo::cognitiveComplexity).max().orElse(0);
    }

    public int maxNestingDepth() {
        return methods.stream().mapToInt(JavaMethodInfo::maxNestingDepth).max().orElse(0);
    }
}
