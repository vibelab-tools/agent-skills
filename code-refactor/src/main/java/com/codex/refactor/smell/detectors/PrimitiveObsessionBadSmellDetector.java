package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PrimitiveObsessionBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> DOMAIN_TOKENS = Set.of(
            "id", "code", "status", "state", "type", "kind", "category", "mode",
            "role", "permission", "currency", "amount", "price", "rate", "tax",
            "phone", "email", "zip", "postal", "country", "region", "locale",
            "language", "tenant", "account", "customer", "user", "order", "sku",
            "unit", "level", "rank", "tier", "plan", "channel", "zone", "area",
            "city", "street", "lat", "latitude", "lng", "lon", "longitude"
    );
    private static final Set<String> CODED_BRANCH_TOKENS = Set.of(
            "code", "status", "state", "type", "kind", "category", "mode",
            "role", "permission", "level", "rank", "tier", "plan"
    );
    private static final Set<String> BOOLEAN_FLAG_TOKENS = Set.of(
            "flag", "enabled", "disabled", "active", "inactive", "valid", "invalid",
            "visible", "hidden", "secure", "force", "forced", "dry", "run",
            "notify", "include", "exclude", "skip", "ignore", "allow", "deny",
            "validate", "strict", "async", "sync", "recursive", "overwrite",
            "verbose", "debug", "silent", "express", "urgent", "required",
            "optional", "admin", "primary", "default"
    );
    private static final Set<String> NOISY_PRIMITIVE_CONCEPTS = Set.of(
            "name", "description", "message", "text", "value", "values", "data",
            "info", "count", "index", "size", "length", "left", "right", "total",
            "sum", "min", "max", "tmp", "temp", "result", "input", "output"
    );

    public PrimitiveObsessionBadSmellDetector() {
        super(BadSmell.PRIMITIVE_OBSESSION);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        Map<String, List<PrimitiveSlot>> fieldsByOwner = primitiveFieldsByOwner(context);

        context.analysis().classes().forEach(classInfo ->
                classFieldCluster(classInfo, fieldsByOwner.getOrDefault(classInfo.name(), List.of()))
                        .ifPresent(findings::add));

        context.analysis().methods().forEach(method -> {
            List<PrimitiveSlot> parameters = primitiveParameters(method);
            List<PrimitiveSlot> locals = primitiveLocals(method);
            List<PrimitiveSlot> visibleSlots = new ArrayList<>(parameters);
            visibleSlots.addAll(fieldsByOwner.getOrDefault(method.ownerClass(), List.of()));
            visibleSlots.addAll(locals);

            codedPrimitiveBranching(method, visibleSlots).ifPresent(findings::add);
            booleanFlagParameters(method, parameters).ifPresent(findings::add);
            methodParameterCluster(method, parameters).ifPresent(findings::add);
        });

        findings.addAll(repeatedDomainPrimitives(context, fieldsByOwner));
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private Optional<SmellFinding> classFieldCluster(JavaClassInfo classInfo, List<PrimitiveSlot> primitiveFields) {
        List<PrimitiveSlot> domainFields = primitiveFields.stream()
                .filter(PrimitiveSlot::domainConcept)
                .toList();
        if (domainFields.size() < 3) {
            return Optional.empty();
        }
        return Optional.of(DetectorSupport.finding(
                smell(),
                domainFields.size() >= 5 ? "high" : "medium",
                domainFields.size() >= 4 ? "high" : "medium",
                classInfo.name(),
                classInfo.startLine(),
                classInfo.endLine(),
                DetectorSupport.evidence(
                        "signal", "domain_named_primitive_cluster",
                        "scope", "fields",
                        "primitive_field_count", primitiveFields.size(),
                        "domain_primitive_count", domainFields.size(),
                        "primitive_names", names(domainFields),
                        "concepts", concepts(domainFields),
                        "items", items(domainFields)
                ),
                "Class models several domain concepts as loose primitive fields.",
                "Replace related primitives with value objects, enums, or a small extracted class that owns validation and behavior."
        ));
    }

    private Optional<SmellFinding> methodParameterCluster(JavaMethodInfo method, List<PrimitiveSlot> parameters) {
        List<PrimitiveSlot> domainParameters = parameters.stream()
                .filter(PrimitiveSlot::domainConcept)
                .toList();
        if (domainParameters.size() < 3) {
            return Optional.empty();
        }
        return Optional.of(DetectorSupport.finding(
                smell(),
                domainParameters.size() >= 5 ? "high" : "medium",
                domainParameters.size() >= 4 ? "high" : "medium",
                method.name(),
                method.startLine(),
                method.endLine(),
                DetectorSupport.evidence(
                        "signal", "domain_named_primitive_parameters",
                        "scope", "parameters",
                        "primitive_parameter_count", parameters.size(),
                        "domain_primitive_count", domainParameters.size(),
                        "primitive_names", names(domainParameters),
                        "concepts", concepts(domainParameters),
                        "items", items(domainParameters)
                ),
                "Method passes several domain concepts as loose primitive parameters.",
                "Introduce Parameter Object, Preserve Whole Object, or Replace Primitive with Object for the repeated domain values."
        ));
    }

    private Optional<SmellFinding> booleanFlagParameters(JavaMethodInfo method, List<PrimitiveSlot> parameters) {
        List<PrimitiveSlot> flags = parameters.stream()
                .filter(PrimitiveSlot::booleanType)
                .filter(PrimitiveSlot::booleanFlagName)
                .toList();
        if (flags.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(DetectorSupport.finding(
                smell(),
                flags.size() >= 3 ? "high" : "medium",
                "high",
                method.name(),
                method.startLine(),
                method.endLine(),
                DetectorSupport.evidence(
                        "signal", "boolean_flag_parameters",
                        "primitive_parameter_count", parameters.size(),
                        "boolean_flag_count", flags.size(),
                        "primitive_names", names(flags),
                        "items", items(flags)
                ),
                "Method behavior is controlled by multiple boolean primitive flags.",
                "Replace flag arguments with explicit methods, an options object, or domain-specific value objects."
        ));
    }

    private Optional<SmellFinding> codedPrimitiveBranching(JavaMethodInfo method, List<PrimitiveSlot> slots) {
        List<PrimitiveBranchEvidence> evidence = slots.stream()
                .filter(PrimitiveSlot::codedBranchConcept)
                .map(slot -> PrimitiveBranchEvidence.from(method, slot))
                .flatMap(Optional::stream)
                .toList();
        if (evidence.isEmpty()) {
            return Optional.empty();
        }
        boolean hasSwitch = evidence.stream().anyMatch(PrimitiveBranchEvidence::usedInSwitch);
        int literalComparisons = evidence.stream().mapToInt(PrimitiveBranchEvidence::literalComparisonCount).sum();
        return Optional.of(DetectorSupport.finding(
                smell(),
                hasSwitch || literalComparisons >= 2 ? "high" : "medium",
                "high",
                method.name(),
                method.startLine(),
                method.endLine(),
                DetectorSupport.evidence(
                        "signal", "coded_primitive_branching",
                        "primitive_names", evidence.stream().map(PrimitiveBranchEvidence::name).distinct().toList(),
                        "used_in_switch", hasSwitch,
                        "literal_comparison_count", literalComparisons,
                        "branch_evidence", evidence.stream().map(PrimitiveBranchEvidence::toJson).toList()
                ),
                "Primitive domain codes are used to drive branching behavior.",
                "Replace Primitive with Object, Replace Type Code with Subclasses, or use an enum/value object that owns the branching behavior."
        ));
    }

    private List<SmellFinding> repeatedDomainPrimitives(
            SmellAnalysisContext context,
            Map<String, List<PrimitiveSlot>> fieldsByOwner
    ) {
        Map<String, ConceptAccumulator> concepts = new LinkedHashMap<>();
        fieldsByOwner.values().stream()
                .flatMap(List::stream)
                .filter(PrimitiveSlot::domainConcept)
                .forEach(slot -> concepts.computeIfAbsent(slot.conceptKey(), ConceptAccumulator::new).add(slot));
        context.analysis().methods().stream()
                .flatMap(method -> primitiveParameters(method).stream())
                .filter(PrimitiveSlot::domainConcept)
                .forEach(slot -> concepts.computeIfAbsent(slot.conceptKey(), ConceptAccumulator::new).add(slot));

        return concepts.values().stream()
                .filter(ConceptAccumulator::reportable)
                .sorted(Comparator.comparing(ConceptAccumulator::conceptKey))
                .map(accumulator -> DetectorSupport.finding(
                        smell(),
                        "medium",
                        accumulator.owners().size() >= 3 ? "high" : "medium",
                        accumulator.firstSlot().owner(),
                        accumulator.firstSlot().startLine(),
                        accumulator.firstSlot().endLine(),
                        accumulator.evidence(),
                        "The same domain primitive concept appears repeatedly across the source model.",
                        "Introduce a named value object or enum for this concept so validation and behavior are not scattered."
                ))
                .toList();
    }

    private static Map<String, List<PrimitiveSlot>> primitiveFieldsByOwner(SmellAnalysisContext context) {
        Map<String, List<PrimitiveSlot>> fieldsByOwner = new LinkedHashMap<>();
        context.analysis().fields().stream()
                .filter(field -> DetectorSupport.primitiveLike(field.type()))
                .filter(field -> !(field.staticField() && field.finalField()))
                .map(PrimitiveSlot::fromField)
                .forEach(slot -> fieldsByOwner.computeIfAbsent(slot.owner(), ignored -> new ArrayList<>()).add(slot));
        return fieldsByOwner;
    }

    private static List<PrimitiveSlot> primitiveParameters(JavaMethodInfo method) {
        List<PrimitiveSlot> parameters = new ArrayList<>();
        for (int index = 0; index < method.parameterNames().size(); index++) {
            String type = method.parameterTypes().get(index);
            if (DetectorSupport.primitiveLike(type)) {
                parameters.add(PrimitiveSlot.fromParameter(method, index));
            }
        }
        return parameters;
    }

    private static List<PrimitiveSlot> primitiveLocals(JavaMethodInfo method) {
        return method.localVariables().stream()
                .map(name -> PrimitiveSlot.fromLocal(method, name, method.variableTypes().getOrDefault(name, "unknown")))
                .filter(slot -> DetectorSupport.primitiveLike(slot.type()))
                .toList();
    }

    private static List<String> names(List<PrimitiveSlot> slots) {
        return slots.stream().map(PrimitiveSlot::name).distinct().toList();
    }

    private static List<String> concepts(List<PrimitiveSlot> slots) {
        return slots.stream()
                .map(PrimitiveSlot::conceptKey)
                .filter(concept -> !concept.isBlank())
                .distinct()
                .toList();
    }

    private static List<Map<String, Object>> items(List<PrimitiveSlot> slots) {
        return slots.stream().map(PrimitiveSlot::toJson).toList();
    }

    private record PrimitiveSlot(
            String kind,
            String owner,
            String name,
            String type,
            int startLine,
            int endLine,
            List<String> tokens,
            String conceptKey
    ) {
        static PrimitiveSlot fromField(JavaFieldInfo field) {
            List<String> tokens = splitWords(field.name());
            return new PrimitiveSlot(
                    "field",
                    field.ownerClass(),
                    field.name(),
                    normalizeType(field.type()),
                    field.startLine(),
                    field.endLine(),
                    tokens,
                    PrimitiveObsessionBadSmellDetector.conceptKey(tokens)
            );
        }

        static PrimitiveSlot fromParameter(JavaMethodInfo method, int parameterIndex) {
            String name = method.parameterNames().get(parameterIndex);
            List<String> tokens = splitWords(name);
            return new PrimitiveSlot(
                    "parameter",
                    method.ownerClass() + "." + method.name(),
                    name,
                    normalizeType(method.parameterTypes().get(parameterIndex)),
                    method.startLine(),
                    method.startLine(),
                    tokens,
                    PrimitiveObsessionBadSmellDetector.conceptKey(tokens)
            );
        }

        static PrimitiveSlot fromLocal(JavaMethodInfo method, String name, String type) {
            List<String> tokens = splitWords(name);
            return new PrimitiveSlot(
                    "local_variable",
                    method.ownerClass() + "." + method.name(),
                    name,
                    normalizeType(type),
                    method.startLine(),
                    method.endLine(),
                    tokens,
                    PrimitiveObsessionBadSmellDetector.conceptKey(tokens)
            );
        }

        boolean domainConcept() {
            return !conceptKey.isBlank();
        }

        boolean codedBranchConcept() {
            return tokens.stream().anyMatch(CODED_BRANCH_TOKENS::contains);
        }

        boolean booleanType() {
            return "boolean".equals(type) || "Boolean".equals(type);
        }

        boolean booleanFlagName() {
            return booleanType() && tokens.stream().anyMatch(token ->
                    BOOLEAN_FLAG_TOKENS.contains(token) || token.startsWith("is") || token.startsWith("has"));
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "kind", kind,
                    "owner", owner,
                    "name", name,
                    "type", type,
                    "concept", conceptKey
            );
        }
    }

    private record PrimitiveBranchEvidence(
            PrimitiveSlot slot,
            boolean usedInSwitch,
            int literalComparisonCount
    ) {
        static Optional<PrimitiveBranchEvidence> from(JavaMethodInfo method, PrimitiveSlot slot) {
            boolean usedInSwitch = method.switchSelectors().stream()
                    .anyMatch(selector -> referencesName(selector, slot.name()));
            int literalComparisonCount = PrimitiveObsessionBadSmellDetector.literalComparisonCount(
                    method.normalizedBody(),
                    slot.name()
            );
            if (!usedInSwitch && literalComparisonCount == 0) {
                return Optional.empty();
            }
            return Optional.of(new PrimitiveBranchEvidence(slot, usedInSwitch, literalComparisonCount));
        }

        String name() {
            return slot.name();
        }

        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "name", slot.name(),
                    "kind", slot.kind(),
                    "type", slot.type(),
                    "concept", slot.conceptKey(),
                    "used_in_switch", usedInSwitch,
                    "literal_comparison_count", literalComparisonCount
            );
        }
    }

    private static final class ConceptAccumulator {
        private final String conceptKey;
        private final List<PrimitiveSlot> slots = new ArrayList<>();
        private final Set<String> owners = new LinkedHashSet<>();

        private ConceptAccumulator(String conceptKey) {
            this.conceptKey = conceptKey;
        }

        private String conceptKey() {
            return conceptKey;
        }

        private void add(PrimitiveSlot slot) {
            slots.add(slot);
            owners.add(slot.owner());
        }

        private boolean reportable() {
            return slots.size() >= 3
                    && owners.size() >= 2
                    && !NOISY_PRIMITIVE_CONCEPTS.contains(conceptKey);
        }

        private PrimitiveSlot firstSlot() {
            return slots.getFirst();
        }

        private Set<String> owners() {
            return owners;
        }

        private Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", "repeated_domain_primitive",
                    "concept", conceptKey,
                    "occurrences", slots.size(),
                    "owners", List.copyOf(owners),
                    "primitive_names", names(slots),
                    "items", items(slots)
            );
        }
    }

    private static int literalComparisonCount(String body, String name) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        String reference = "(?:this\\.)?" + Pattern.quote(name);
        String literal = "(?:\"[^\"]*\"|'[^']*'|\\d+(?:\\.\\d+)?[dDfFlL]?|true|false)";
        List<Pattern> patterns = List.of(
                Pattern.compile("(?<![A-Za-z0-9_$])" + reference + "\\s*(?:==|!=|<=|>=|<|>)\\s*" + literal),
                Pattern.compile(literal + "\\s*(?:==|!=|<=|>=|<|>)\\s*" + reference + "(?![A-Za-z0-9_$])"),
                Pattern.compile("(?<![A-Za-z0-9_$])" + reference + "\\.equals\\s*\\(\\s*" + literal + "\\s*\\)"),
                Pattern.compile(literal + "\\.equals\\s*\\(\\s*" + reference + "\\s*\\)"),
                Pattern.compile("Objects\\.equals\\s*\\(\\s*" + reference + "\\s*,\\s*" + literal + "\\s*\\)"),
                Pattern.compile("Objects\\.equals\\s*\\(\\s*" + literal + "\\s*,\\s*" + reference + "\\s*\\)")
        );
        return patterns.stream().mapToInt(pattern -> {
            int count = 0;
            java.util.regex.Matcher matcher = pattern.matcher(body);
            while (matcher.find()) {
                count++;
            }
            return count;
        }).sum();
    }

    private static boolean referencesName(String expression, String name) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String normalized = expression.replaceAll("\\s+", "");
        return normalized.equals(name)
                || normalized.equals("this." + name)
                || normalized.endsWith("." + name);
    }

    private static String conceptKey(List<String> tokens) {
        List<String> meaningful = tokens.stream()
                .filter(token -> !token.isBlank())
                .filter(token -> !Set.of("is", "has", "can", "should", "use", "with").contains(token))
                .map(PrimitiveObsessionBadSmellDetector::normalizeToken)
                .toList();
        if (meaningful.isEmpty() || meaningful.stream().noneMatch(DOMAIN_TOKENS::contains)) {
            return "";
        }
        String key = meaningful.stream()
                .filter(token -> !NOISY_PRIMITIVE_CONCEPTS.contains(token))
                .collect(Collectors.joining("_"));
        return key.isBlank() || NOISY_PRIMITIVE_CONCEPTS.contains(key) ? "" : key;
    }

    private static String normalizeToken(String token) {
        return switch (token) {
            case "postcode", "zipcode", "postalcode" -> "zip";
            case "lng", "lon" -> "longitude";
            case "lat" -> "latitude";
            case "userid" -> "user_id";
            case "tenantid" -> "tenant_id";
            default -> token;
        };
    }

    private static List<String> splitWords(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(String::toLowerCase)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "unknown";
        }
        String normalized = type.replace("java.lang.", "").trim();
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        return normalized.replace("[]", "");
    }
}
