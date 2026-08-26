package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.SmellFinding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class DetectorSupport {
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean", "char",
            "String", "Integer", "Long", "Double", "Boolean", "BigDecimal"
    );

    private DetectorSupport() {
    }

    static SmellFinding finding(
            BadSmell smell,
            String severity,
            String confidence,
            String symbol,
            int startLine,
            int endLine,
            Map<String, Object> evidence,
            String description,
            String suggestion
    ) {
        return new SmellFinding(
                smell,
                severity,
                confidence,
                location(symbol, startLine, endLine),
                evidence,
                description,
                suggestion
        );
    }

    static Map<String, Object> location(String symbol, int startLine, int endLine) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("symbol", symbol);
        location.put("line", startLine);
        location.put("start_line", startLine);
        location.put("end_line", endLine);
        return location;
    }

    static Map<String, Object> evidence(Object... values) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            evidence.put(String.valueOf(values[index]), values[index + 1]);
        }
        return evidence;
    }

    static boolean primitiveLike(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.replace("[]", "").trim();
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        return PRIMITIVE_TYPES.contains(normalized);
    }

    static boolean poorName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase();
        return name.length() <= 2
                || Set.of("data", "info", "stuff", "thing", "tmp", "temp", "foo", "bar", "obj", "mgr")
                .contains(lower);
    }

    static String signatureShape(JavaMethodInfo method) {
        return String.join(",", method.parameterTypes());
    }

    static String classShape(JavaClassInfo classInfo) {
        return classInfo.fields().size() + ":" + classInfo.methods().size() + ":"
                + classInfo.methods().stream()
                .map(method -> method.parameterTypes().size() + "/" + method.returnType())
                .sorted()
                .toList();
    }

}
