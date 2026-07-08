package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LongParameterListBadSmellDetector extends BookBadSmellDetector {
    private static final int WARNING_PARAMETERS = 4;
    private static final Set<String> DOMAIN_GROUP_TOKENS = Set.of(
            "start", "end", "unit", "from", "to", "street", "city", "zip", "state", "country",
            "amount", "currency", "price", "tax", "email", "phone", "lat", "latitude", "lng", "longitude"
    );
    private static final Set<String> BOOLEAN_FLAG_TOKENS = Set.of(
            "enabled", "active", "force", "dry", "notify", "include", "skip", "validate",
            "strict", "recursive", "overwrite", "debug", "verbose", "admin"
    );

    public LongParameterListBadSmellDetector() {
        super(BadSmell.LONG_PARAMETER_LIST);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().methods().stream()
                .filter(method -> method.parameterNames().size() > WARNING_PARAMETERS)
                .map(method -> DetectorSupport.finding(
                        smell(),
                        severity(method),
                        confidence(method),
                        method.name(),
                        method.startLine(),
                        method.endLine(),
                        evidence(method),
                        "Method takes enough parameters that call sites become hard to read and change.",
                        suggestion(method)
                ))
                .toList();
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private static String severity(JavaMethodInfo method) {
        int count = method.parameterNames().size();
        return count >= 7 || signals(method).size() >= 3 ? "high" : "medium";
    }

    private static String confidence(JavaMethodInfo method) {
        return method.parameterTypes().stream().anyMatch(type -> !"unknown".equals(type))
                || signals(method).size() >= 2
                ? "high"
                : "medium";
    }

    private static Map<String, Object> evidence(JavaMethodInfo method) {
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        method.parameterTypes().forEach(type -> typeCounts.merge(normalizeType(type), 1L, Long::sum));
        return DetectorSupport.evidence(
                "signals", signals(method),
                "parameter_count", method.parameterNames().size(),
                "threshold", WARNING_PARAMETERS,
                "parameter_names", method.parameterNames(),
                "parameter_types", method.parameterTypes(),
                "parameter_type_counts", typeCounts,
                "primitive_parameter_count", primitiveParameterCount(method),
                "boolean_flag_count", booleanFlagCount(method),
                "domain_group_token_count", domainGroupTokenCount(method)
        );
    }

    private static String suggestion(JavaMethodInfo method) {
        if (domainGroupTokenCount(method) >= 3) {
            return "Consider Introduce Parameter Object or Preserve Whole Object for the named data group.";
        }
        if (booleanFlagCount(method) >= 2) {
            return "Replace flag arguments with explicit methods or an options object.";
        }
        if (primitiveParameterCount(method) >= method.parameterNames().size() - 1) {
            return "Group related primitive values into a value object or parameter object.";
        }
        return "Consider Introduce Parameter Object or Preserve Whole Object.";
    }

    private static List<String> signals(JavaMethodInfo method) {
        java.util.ArrayList<String> signals = new java.util.ArrayList<>();
        signals.add("too_many_parameters");
        if (primitiveParameterCount(method) >= Math.max(4, method.parameterNames().size() - 1)) {
            signals.add("primitive_heavy_parameter_list");
        }
        if (booleanFlagCount(method) >= 2) {
            signals.add("boolean_flag_cluster");
        }
        if (domainGroupTokenCount(method) >= 3) {
            signals.add("named_data_group");
        }
        if (sameTypeRun(method) >= 4) {
            signals.add("same_type_run");
        }
        return List.copyOf(signals);
    }

    private static long primitiveParameterCount(JavaMethodInfo method) {
        return method.parameterTypes().stream().filter(DetectorSupport::primitiveLike).count();
    }

    private static long booleanFlagCount(JavaMethodInfo method) {
        long count = 0;
        for (int index = 0; index < method.parameterNames().size(); index++) {
            String type = index < method.parameterTypes().size() ? method.parameterTypes().get(index) : "";
            String name = method.parameterNames().get(index).toLowerCase(Locale.ROOT);
            if (Set.of("boolean", "bool", "Boolean").contains(type)
                    || BOOLEAN_FLAG_TOKENS.stream().anyMatch(name::contains)) {
                count++;
            }
        }
        return count;
    }

    private static long domainGroupTokenCount(JavaMethodInfo method) {
        return method.parameterNames().stream()
                .flatMap(name -> splitWords(name).stream())
                .map(LongParameterListBadSmellDetector::normalizeNameToken)
                .filter(DOMAIN_GROUP_TOKENS::contains)
                .distinct()
                .count();
    }

    private static int sameTypeRun(JavaMethodInfo method) {
        int best = 0;
        int current = 0;
        String previous = "";
        for (String type : method.parameterTypes()) {
            String normalized = normalizeType(type);
            if (normalized.equals(previous)) {
                current++;
            } else {
                previous = normalized;
                current = 1;
            }
            best = Math.max(best, current);
        }
        return best;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "unknown";
        }
        String normalized = type.replace("java.lang.", "").replaceAll("\\s+", "");
        int generic = normalized.indexOf('<');
        if (generic >= 0) {
            normalized = normalized.substring(0, generic);
        }
        return normalized;
    }

    private static String normalizeNameToken(String token) {
        return switch (token) {
            case "begin", "from" -> "start";
            case "finish", "to", "stop" -> "end";
            case "zipcode", "postal", "postalcode", "postcode" -> "zip";
            case "lng", "lon" -> "longitude";
            case "lat" -> "latitude";
            default -> token;
        };
    }

    private static List<String> splitWords(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }
}
