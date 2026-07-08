package com.codex.refactor.smell;

import java.util.List;

public interface BadSmellDetector {
    BadSmell smell();

    boolean isImplemented();

    List<SmellFinding> detect(SmellAnalysisContext context);
}
