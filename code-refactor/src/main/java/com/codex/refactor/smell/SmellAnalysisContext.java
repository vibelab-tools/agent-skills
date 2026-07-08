package com.codex.refactor.smell;

import com.codex.refactor.analysis.SourceFileAnalysis;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.history.HistoryAnalysis;

public record SmellAnalysisContext(
        SourceFileAnalysis analysis,
        HistoryAnalysis historyAnalysis,
        SourceProjectIndex projectIndex
) {
    public SmellAnalysisContext(SourceFileAnalysis analysis) {
        this(analysis, HistoryAnalysis.off(), SourceProjectIndex.empty());
    }

    public SmellAnalysisContext(SourceFileAnalysis analysis, HistoryAnalysis historyAnalysis) {
        this(analysis, historyAnalysis, SourceProjectIndex.empty());
    }

    public SmellAnalysisContext {
        if (projectIndex == null) {
            projectIndex = SourceProjectIndex.empty();
        }
    }
}
