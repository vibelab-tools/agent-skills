package com.codex.refactor.history;

public record HistoryOptions(
        String mode,
        int commitWindow,
        int minCoChanges,
        int minOwners
) {
    public static HistoryOptions off() {
        return new HistoryOptions("off", 0, 0, 0);
    }

    public boolean enabled() {
        return "git".equals(mode);
    }
}
