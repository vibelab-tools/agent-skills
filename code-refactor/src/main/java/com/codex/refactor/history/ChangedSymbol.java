package com.codex.refactor.history;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChangedSymbol(
        String path,
        String kind,
        String owner,
        String name,
        int parameterCount,
        int startLine,
        int endLine,
        String changeKey
) {
    public String symbolKey() {
        return path + "#" + owner + "." + name + "/" + parameterCount;
    }

    public Map<String, Object> toJson(int changeCount) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("path", path);
        json.put("kind", kind);
        json.put("owner", owner);
        json.put("name", name);
        json.put("parameter_count", parameterCount);
        json.put("start_line", startLine);
        json.put("end_line", endLine);
        json.put("change_count", changeCount);
        return json;
    }
}
