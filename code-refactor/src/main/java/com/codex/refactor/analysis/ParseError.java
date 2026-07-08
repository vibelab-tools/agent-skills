package com.codex.refactor.analysis;

public record ParseError(
        String message,
        long startLine,
        long startColumn,
        long endLine,
        long endColumn,
        String severity
) {
}
