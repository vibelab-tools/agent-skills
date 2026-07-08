package com.codex.refactor.analysis;

import java.util.List;

public record BranchDispatchInfo(
        String kind,
        String selector,
        List<String> labels,
        boolean hasDefault,
        int startLine,
        int endLine
) {
    public BranchDispatchInfo {
        labels = List.copyOf(labels);
    }
}
