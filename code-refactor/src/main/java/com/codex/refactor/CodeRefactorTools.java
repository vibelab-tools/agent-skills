package com.codex.refactor;

import com.codex.refactor.cli.Cli;

public final class CodeRefactorTools {
    private CodeRefactorTools() {
    }

    public static void main(String[] args) {
        int exitCode = new Cli(System.out, System.err).run(args);
        System.exit(exitCode);
    }
}
