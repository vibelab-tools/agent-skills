package com.codex.refactor.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

public record MethodCallInfo(
        String receiverExpression,
        String receiverRoot,
        String receiverKind,
        String receiverType,
        String methodName,
        int argumentCount,
        int line
) {
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("receiver_expression", receiverExpression);
        json.put("receiver_root", receiverRoot);
        json.put("receiver_kind", receiverKind);
        json.put("receiver_type", receiverType);
        json.put("method_name", methodName);
        json.put("argument_count", argumentCount);
        json.put("line", line);
        return json;
    }
}
