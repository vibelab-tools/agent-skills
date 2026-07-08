package com.codex.refactor.analysis;

import java.util.List;

public record DelegationInfo(
        String delegateRoot,
        String delegateKind,
        String targetMethod,
        boolean returnsDelegateResult,
        boolean sameName,
        List<String> arguments,
        int passThroughArgumentCount,
        int parameterCount,
        int startLine,
        int endLine
) {
    public DelegationInfo {
        arguments = List.copyOf(arguments);
    }

    public boolean fieldDelegate() {
        return "field".equals(delegateKind);
    }

    public double passThroughRatio() {
        if (arguments.isEmpty()) {
            return 1.0;
        }
        return (double) passThroughArgumentCount / arguments.size();
    }
}
