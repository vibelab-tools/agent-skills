package com.codex.refactor.smell;

import java.util.List;

public abstract class BookBadSmellDetector implements BadSmellDetector {
    private final BadSmell smell;

    protected BookBadSmellDetector(BadSmell smell) {
        this.smell = smell;
    }

    @Override
    public final BadSmell smell() {
        return smell;
    }

    @Override
    public boolean isImplemented() {
        return false;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        return List.of();
    }
}
