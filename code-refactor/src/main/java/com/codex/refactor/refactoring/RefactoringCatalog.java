package com.codex.refactor.refactoring;

import com.codex.refactor.smell.BadSmell;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class RefactoringCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CATALOG_RESOURCE =
            "/com/codex/refactor/refactoring/refactorings-catalog.json";
    private static final String SMELL_MAP_RESOURCE =
            "/com/codex/refactor/refactoring/smell-refactoring-map.json";
    private static final String PLAYBOOK_RESOURCE =
            "/com/codex/refactor/refactoring/refactoring-playbooks.json";
    private static final RefactoringCatalog STANDARD = loadStandard();

    private final List<RefactoringEntry> refactorings;
    private final Map<String, RefactoringEntry> refactoringsByName;
    private final List<SmellRefactoringMapping> smellMappings;
    private final Map<String, SmellRefactoringMapping> smellMappingsById;
    private final List<RefactoringPlaybook> playbooks;
    private final Map<String, RefactoringPlaybook> playbooksByName;

    private RefactoringCatalog(
            List<RefactoringEntry> refactorings,
            Map<String, RefactoringEntry> refactoringsByName,
            List<SmellRefactoringMapping> smellMappings,
            Map<String, SmellRefactoringMapping> smellMappingsById,
            List<RefactoringPlaybook> playbooks,
            Map<String, RefactoringPlaybook> playbooksByName
    ) {
        this.refactorings = List.copyOf(refactorings);
        this.refactoringsByName = Collections.unmodifiableMap(new LinkedHashMap<>(refactoringsByName));
        this.smellMappings = List.copyOf(smellMappings);
        this.smellMappingsById = Collections.unmodifiableMap(new LinkedHashMap<>(smellMappingsById));
        this.playbooks = List.copyOf(playbooks);
        this.playbooksByName = Collections.unmodifiableMap(new LinkedHashMap<>(playbooksByName));
    }

    public static RefactoringCatalog standard() {
        return STANDARD;
    }

    public List<RefactoringEntry> allRefactorings() {
        return refactorings;
    }

    public List<SmellRefactoringMapping> allSmellMappings() {
        return smellMappings;
    }

    public Optional<RefactoringEntry> refactoringByName(String name) {
        return Optional.ofNullable(refactoringsByName.get(name));
    }

    public Optional<SmellRefactoringMapping> mappingFor(BadSmell smell) {
        return Optional.ofNullable(smellMappingsById.get(smell.id()));
    }

    public List<RefactoringPlaybook> allPlaybooks() {
        return playbooks;
    }

    public Optional<RefactoringPlaybook> playbookByName(String name) {
        return Optional.ofNullable(playbooksByName.get(name));
    }

    public List<String> recommendedRefactoringNames(BadSmell smell) {
        return mappingFor(smell)
                .map(SmellRefactoringMapping::refactorings)
                .orElse(List.of());
    }

    public List<String> recommendedRefactoringNames(BadSmell smell, Map<String, Object> evidence) {
        return orderedRecommendations(smell, evidence).stream()
                .map(RefactoringEntry::name)
                .toList();
    }

    public List<Map<String, Object>> recommendedRefactoringDetails(BadSmell smell, Map<String, Object> evidence) {
        return orderedRecommendations(smell, evidence).stream()
                .map(entry -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("name", entry.name());
                    detail.put("chapter", entry.chapter());
                    return detail;
                })
                .toList();
    }

    public List<Map<String, Object>> recommendedRefactoringRationale(BadSmell smell, Map<String, Object> evidence) {
        return orderedRecommendations(smell, evidence).stream()
                .map(entry -> {
                    RefactoringPlaybook playbook = playbooksByName.get(entry.name());
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("name", entry.name());
                    json.put("reason", reasonFor(smell, entry.name(), evidence));
                    json.put("applies_when", playbook.appliesWhen());
                    json.put("preconditions", playbook.preconditions());
                    json.put("first_safe_step", playbook.firstSafeStep());
                    json.put("steps", playbook.steps());
                    json.put("test_focus", playbook.testFocus());
                    json.put("risks", playbook.risks());
                    return json;
                })
                .toList();
    }

    private List<RefactoringEntry> orderedRecommendations(BadSmell smell, Map<String, Object> evidence) {
        List<String> names = recommendedRefactoringNames(smell);
        Map<String, Integer> originalOrder = new LinkedHashMap<>();
        for (int index = 0; index < names.size(); index++) {
            originalOrder.put(names.get(index), index);
        }
        return names.stream()
                .sorted(Comparator
                        .comparingInt((String name) -> -evidenceScore(smell, name, evidence))
                        .thenComparingInt(originalOrder::get))
                .map(refactoringsByName::get)
                .toList();
    }

    private static int evidenceScore(BadSmell smell, String refactoring, Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return 0;
        }
        List<String> signals = stringList(evidence.get("signals"));
        String signal = stringValue(evidence.get("signal"));
        return switch (smell) {
            case MYSTERIOUS_NAME -> mysteriousNameScore(refactoring, evidence);
            case DUPLICATED_CODE -> duplicatedCodeScore(refactoring, signal, evidence);
            case LONG_FUNCTION -> longFunctionScore(refactoring, evidence, signals);
            case LONG_PARAMETER_LIST -> longParameterListScore(refactoring, signals, evidence);
            case GLOBAL_DATA -> globalDataScore(refactoring, signal);
            case MUTABLE_DATA -> mutableDataScore(refactoring, signals);
            case DIVERGENT_CHANGE -> divergentChangeScore(refactoring, evidence);
            case SHOTGUN_SURGERY -> shotgunSurgeryScore(refactoring, evidence);
            case FEATURE_ENVY -> featureEnvyScore(refactoring, evidence);
            case DATA_CLUMPS -> dataClumpsScore(refactoring, signal);
            case PRIMITIVE_OBSESSION -> primitiveObsessionScore(refactoring, signal);
            case REPEATED_SWITCHES -> "Replace Conditional with Polymorphism".equals(refactoring) ? 100 : 0;
            case LOOPS -> "Replace Loop with Pipeline".equals(refactoring) ? 100 : 0;
            case LAZY_ELEMENT -> lazyElementScore(refactoring, signals);
            case SPECULATIVE_GENERALITY -> speculativeGeneralityScore(refactoring, signals, evidence);
            case TEMPORARY_FIELD -> temporaryFieldScore(refactoring, signals, evidence);
            case MESSAGE_CHAINS -> messageChainsScore(refactoring, signal, evidence);
            case MIDDLE_MAN -> middleManScore(refactoring, evidence);
            case INSIDER_TRADING -> insiderTradingScore(refactoring, signal, evidence);
            case LARGE_CLASS -> largeClassScore(refactoring, signals, evidence);
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES ->
                    alternativeClassesScore(refactoring, evidence);
            case DATA_CLASS -> dataClassScore(refactoring, signals, evidence);
            case REFUSED_BEQUEST -> refusedBequestScore(refactoring, signal, evidence);
            case COMMENTS -> commentsScore(refactoring, signal);
        };
    }

    private static int mysteriousNameScore(String refactoring, Map<String, Object> evidence) {
        String kind = stringValue(evidence.get("kind"));
        if ((kind.contains("method") || kind.contains("function")) && "Change Function Declaration".equals(refactoring)) {
            return 100;
        }
        if (kind.contains("field") && "Rename Field".equals(refactoring)) {
            return 100;
        }
        if ((kind.contains("variable") || kind.contains("parameter")) && "Rename Variable".equals(refactoring)) {
            return 100;
        }
        return 0;
    }

    private static int duplicatedCodeScore(String refactoring, String signal, Map<String, Object> evidence) {
        int duplicateCount = intValue(evidence.get("duplicate_count"));
        return switch (refactoring) {
            case "Extract Function" -> "exact_method_body".equals(signal) ? 100 : 85;
            case "Slide Statements" -> "normalized_statement_shape".equals(signal) ? 100 : 35;
            case "Pull Up Method" -> duplicateCount >= 3 ? 70 : 25;
            default -> 0;
        };
    }

    private static int longFunctionScore(String refactoring, Map<String, Object> evidence, List<String> signals) {
        int loopCount = intValue(evidence.get("loop_count"));
        int branchCount = intValue(evidence.get("branch_dispatch_count")) + intValue(evidence.get("switch_selector_count"));
        int localVariableCount = intValue(evidence.get("local_variable_count"));
        int parameterCount = intValue(evidence.get("parameter_count"));
        int physicalLines = intValue(evidence.get("physical_lines"));
        boolean complexControlFlow = signals.contains("high_cyclomatic_complexity")
                || signals.contains("deep_nesting")
                || branchCount > 0;
        boolean extractionBlocked = localVariableCount >= 6 || parameterCount >= 5;

        return switch (refactoring) {
            case "Extract Function" -> 100;
            case "Split Loop" -> loopCount >= 2 ? 90 : loopCount == 1 ? 45 : 0;
            case "Decompose Conditional" -> complexControlFlow ? 80 : 0;
            case "Replace Conditional with Polymorphism" -> branchCount > 0 ? 75 : 0;
            case "Replace Function with Command" -> extractionBlocked && physicalLines >= 50 ? 70 : extractionBlocked ? 55 : 0;
            case "Replace Temp with Query" -> localVariableCount >= 4 ? 60 : 0;
            case "Introduce Parameter Object" -> parameterCount >= 4 ? 50 : 0;
            case "Preserve Whole Object" -> parameterCount >= 4 ? 40 : 0;
            default -> 0;
        };
    }

    private static int longParameterListScore(String refactoring, List<String> signals, Map<String, Object> evidence) {
        int booleanFlagCount = intValue(evidence.get("boolean_flag_count"));
        int primitiveParameterCount = intValue(evidence.get("primitive_parameter_count"));
        int parameterCount = intValue(evidence.get("parameter_count"));
        return switch (refactoring) {
            case "Remove Flag Argument" -> booleanFlagCount >= 2 || signals.contains("boolean_flag_cluster") ? 100 : 0;
            case "Introduce Parameter Object" -> signals.contains("named_data_group")
                    || signals.contains("primitive_heavy_parameter_list") ? 90 : 20;
            case "Preserve Whole Object" -> signals.contains("named_data_group") ? 80 : 10;
            case "Combine Functions into Class" -> parameterCount >= 6 || primitiveParameterCount >= 5 ? 60 : 0;
            case "Replace Parameter with Query" -> 5;
            default -> 0;
        };
    }

    private static int globalDataScore(String refactoring, String signal) {
        return switch (refactoring) {
            case "Encapsulate Variable" -> 100;
            case "Move Function" -> "module_level_mutable_data".equals(signal) ? 70 : 45;
            default -> 0;
        };
    }

    private static int mutableDataScore(String refactoring, List<String> signals) {
        return switch (refactoring) {
            case "Encapsulate Variable" -> signalsContain(signals,
                    "public_mutable_field", "module_level_mutable_state", "final_reference_to_mutable_container") ? 100 : 70;
            case "Split Variable" -> signalsContain(signals, "multiple_writers") ? 80 : 35;
            case "Separate Query from Modifier" -> signalsContain(signals, "multiple_writers") ? 75 : 25;
            case "Remove Setting Method" -> signalsContain(signals, "public_mutable_field") ? 70 : 30;
            case "Replace Derived Variable with Query" -> signalsContain(signals, "multiple_writers") ? 55 : 20;
            case "Combine Functions into Class", "Combine Functions into Transform" ->
                    signalsContain(signals, "multiple_writers") ? 45 : 15;
            case "Change Reference to Value" -> signalsContain(signals, "final_reference_to_mutable_container") ? 90 : 25;
            case "Slide Statements", "Extract Function" -> 20;
            default -> 0;
        };
    }

    private static int divergentChangeScore(String refactoring, Map<String, Object> evidence) {
        int concernCount = intValue(evidence.get("concern_count"));
        boolean hasCollaborators = nonEmpty(evidence.get("collaborator_clusters"));
        return switch (refactoring) {
            case "Split Phase" -> concernCount == 2 ? 100 : 70;
            case "Extract Class" -> concernCount >= 3 ? 95 : 55;
            case "Move Function" -> hasCollaborators ? 90 : 60;
            case "Extract Function" -> concernCount >= 2 ? 75 : 45;
            default -> 0;
        };
    }

    private static int shotgunSurgeryScore(String refactoring, Map<String, Object> evidence) {
        int ownerCount = intValue(evidence.get("owner_count"));
        int fileCount = intValue(evidence.get("file_count"));
        int coChangeCommits = intValue(evidence.get("co_change_commits"));
        String changeKey = stringValue(evidence.get("change_key"));
        return switch (refactoring) {
            case "Move Function" -> 100;
            case "Move Field" -> changeKey.contains("field") || changeKey.contains("data") ? 90 : 45;
            case "Combine Functions into Class" -> ownerCount >= 3 ? 85 : 55;
            case "Combine Functions into Transform" -> changeKey.contains("build")
                    || changeKey.contains("transform")
                    || changeKey.contains("calculate") ? 80 : 45;
            case "Split Phase" -> fileCount >= 3 || coChangeCommits >= 3 ? 70 : 35;
            case "Inline Function", "Inline Class" -> fileCount >= 3 ? 60 : 30;
            default -> 0;
        };
    }

    private static int featureEnvyScore(String refactoring, Map<String, Object> evidence) {
        double foreignRatio = doubleValue(evidence.get("foreign_access_ratio"));
        int ownAccesses = intValue(evidence.get("own_data_accesses"));
        int foreignAccesses = intValue(evidence.get("foreign_accesses"));
        boolean resolvedTarget = !stringValue(evidence.get("resolved_foreign_type")).isBlank();
        return switch (refactoring) {
            case "Move Function" -> foreignRatio >= 0.65 && foreignAccesses >= ownAccesses + 2 ? 100 : 75;
            case "Extract Function" -> ownAccesses > 0 || !resolvedTarget ? 90 : 55;
            default -> 0;
        };
    }

    private static int dataClumpsScore(String refactoring, String signal) {
        return switch (signal) {
            case "repeated_field_group" -> switch (refactoring) {
                case "Extract Class" -> 100;
                case "Introduce Parameter Object" -> 40;
                case "Preserve Whole Object" -> 30;
                default -> 0;
            };
            case "repeated_parameter_group", "repeated_argument_group" -> switch (refactoring) {
                case "Introduce Parameter Object" -> 100;
                case "Preserve Whole Object" -> 70;
                case "Extract Class" -> 60;
                default -> 0;
            };
            case "mixed_data_group" -> switch (refactoring) {
                case "Extract Class" -> 100;
                case "Introduce Parameter Object" -> 90;
                case "Preserve Whole Object" -> 70;
                default -> 0;
            };
            default -> 0;
        };
    }

    private static int primitiveObsessionScore(String refactoring, String signal) {
        return switch (signal) {
            case "coded_primitive_branching" -> switch (refactoring) {
                case "Replace Type Code with Subclasses" -> 100;
                case "Replace Conditional with Polymorphism" -> 90;
                case "Replace Primitive with Object" -> 60;
                default -> 0;
            };
            case "boolean_flag_parameters", "domain_named_primitive_parameters" -> switch (refactoring) {
                case "Introduce Parameter Object" -> 100;
                case "Replace Primitive with Object" -> 80;
                case "Extract Class" -> 60;
                default -> 0;
            };
            case "domain_named_primitive_cluster" -> switch (refactoring) {
                case "Replace Primitive with Object" -> 100;
                case "Extract Class" -> 80;
                case "Introduce Parameter Object" -> 60;
                default -> 0;
            };
            default -> 0;
        };
    }

    private static int lazyElementScore(String refactoring, List<String> signals) {
        return switch (refactoring) {
            case "Inline Function" -> signalsContain(signals,
                    "empty_or_noop_method", "thin_forwarding_method", "simple_delegation") ? 100 : 45;
            case "Inline Class" -> signalsContain(signals, "empty_class", "thin_wrapper", "single_method_class") ? 100 : 45;
            case "Collapse Hierarchy" -> signalsContain(signals, "placeholder_named_type") ? 80 : 30;
            default -> 0;
        };
    }

    private static int speculativeGeneralityScore(String refactoring, List<String> signals, Map<String, Object> evidence) {
        boolean abstraction = boolValue(evidence.get("abstract")) || boolValue(evidence.get("interface"));
        int subtypeCount = intValue(evidence.get("known_subtypes_or_implementers"));
        int typeReferenceCount = intValue(evidence.get("type_reference_count"));
        return switch (refactoring) {
            case "Collapse Hierarchy" -> abstraction && subtypeCount <= 1 ? 100 : 50;
            case "Inline Function", "Inline Class" -> signalsContain(signals, "unused_abstraction") ? 90 : 55;
            case "Change Function Declaration" -> signalsContain(signals, "future_parameter", "unused_parameter") ? 100 : 40;
            case "Remove Dead Code" -> typeReferenceCount == 0 || signalsContain(signals, "unused_abstraction") ? 95 : 45;
            default -> 0;
        };
    }

    private static int temporaryFieldScore(String refactoring, List<String> signals, Map<String, Object> evidence) {
        int readCount = intValue(evidence.get("read_method_count"));
        int writeCount = intValue(evidence.get("write_method_count"));
        return switch (refactoring) {
            case "Extract Class" -> signalsContain(signals, "temporary_name", "sparse_temporary_state") ? 100 : 70;
            case "Move Function" -> readCount + writeCount >= 2 ? 80 : 45;
            case "Introduce Special Case" -> signalsContain(signals, "temporary_name") ? 65 : 35;
            default -> 0;
        };
    }

    private static int messageChainsScore(String refactoring, String signal, Map<String, Object> evidence) {
        int maxDepth = intValue(evidence.get("max_chain_depth"));
        int repeatedPrefixOccurrences = intValue(evidence.get("repeated_prefix_occurrences"));
        return switch (refactoring) {
            case "Hide Delegate" -> maxDepth >= 4 ? 100 : 70;
            case "Extract Function" -> "repeated_chain_prefix".equals(signal) || repeatedPrefixOccurrences >= 2 ? 95 : 55;
            case "Move Function" -> maxDepth >= 5 ? 80 : 40;
            default -> 0;
        };
    }

    private static int middleManScore(String refactoring, Map<String, Object> evidence) {
        int delegationMethods = intValue(evidence.get("delegation_methods"));
        int valueAdded = intValue(evidence.get("value_added_method_count"));
        double ratio = doubleValue(evidence.get("delegation_ratio"));
        return switch (refactoring) {
            case "Remove Middle Man" -> ratio >= 0.5 ? 100 : 70;
            case "Inline Function" -> delegationMethods <= 3 ? 95 : 60;
            case "Replace Superclass with Delegate", "Replace Subclass with Delegate" -> valueAdded > 0 ? 85 : 35;
            default -> 0;
        };
    }

    private static int insiderTradingScore(String refactoring, String signal, Map<String, Object> evidence) {
        int maxDepth = intValue(evidence.get("max_message_chain_depth"));
        int reciprocal = intValue(evidence.get("reciprocal_access_count"));
        int internalSelectors = intValue(evidence.get("internal_selector_count"));
        int collaboratorCount = intValue(evidence.get("collaborator_count"));
        return switch (refactoring) {
            case "Hide Delegate" -> maxDepth >= 4 || internalSelectors > 0 ? 100 : 65;
            case "Move Function" -> collaboratorCount >= 1 ? 90 : 55;
            case "Move Field" -> internalSelectors > 0 || reciprocal > 0 ? 85 : 45;
            case "Replace Subclass with Delegate", "Replace Superclass with Delegate" ->
                    signal.contains("inherit") || reciprocal > 0 ? 75 : 25;
            default -> 0;
        };
    }

    private static int largeClassScore(String refactoring, List<String> signals, Map<String, Object> evidence) {
        Map<?, ?> graph = mapValue(evidence.get("method_field_graph"));
        int extractionClusters = intValue(graph.get("extraction_cluster_count"));
        int fieldCount = intValue(evidence.get("field_count"));
        return switch (refactoring) {
            case "Extract Class" -> extractionClusters >= 2
                    || signalsContain(signals, "multiple_responsibility_clusters", "too_many_fields") ? 100 : 80;
            case "Extract Superclass" -> signalsContain(signals, "multiple_responsibility_clusters") ? 85 : 55;
            case "Replace Type Code with Subclasses" -> fieldCount >= 8 ? 65 : 35;
            default -> 0;
        };
    }

    private static int alternativeClassesScore(String refactoring, Map<String, Object> evidence) {
        double exactOverlap = doubleValue(evidence.get("exact_method_name_overlap"));
        double roleSimilarity = doubleValue(evidence.get("method_role_similarity"));
        boolean sharedCallers = nonEmpty(evidence.get("shared_caller_owners"));
        return switch (refactoring) {
            case "Change Function Declaration" -> exactOverlap < 0.5 ? 100 : 60;
            case "Move Function" -> roleSimilarity >= 0.70 || sharedCallers ? 90 : 55;
            case "Extract Superclass" -> roleSimilarity >= 0.70 && sharedCallers ? 85 : 50;
            default -> 0;
        };
    }

    private static int dataClassScore(String refactoring, List<String> signals, Map<String, Object> evidence) {
        int setters = intValue(evidence.get("setter_method_count"));
        int behavioralMethods = intValue(evidence.get("behavioral_method_count"));
        return switch (refactoring) {
            case "Encapsulate Record" -> signalsContain(signals, "public_fields", "externally_mutable_data") ? 100 : 75;
            case "Remove Setting Method" -> setters > 0 || signalsContain(signals, "externally_mutable_data") ? 95 : 45;
            case "Move Function" -> behavioralMethods == 0 ? 90 : 60;
            case "Extract Function" -> behavioralMethods == 0 ? 75 : 50;
            default -> 0;
        };
    }

    private static int refusedBequestScore(String refactoring, String signal, Map<String, Object> evidence) {
        int strongRejections = intValue(evidence.get("strong_rejection_count"));
        double rejectedRatio = doubleValue(evidence.get("rejected_ratio"));
        boolean verified = "rejected_inherited_contract".equals(signal);
        return switch (refactoring) {
            case "Replace Subclass with Delegate", "Replace Superclass with Delegate" ->
                    verified && (strongRejections >= 1 || rejectedRatio >= 0.4) ? 100 : 70;
            case "Push Down Method" -> rejectedRatio < 0.5 ? 85 : 55;
            case "Push Down Field" -> rejectedRatio < 0.5 ? 70 : 40;
            default -> 0;
        };
    }

    private static int commentsScore(String refactoring, String signal) {
        return switch (signal) {
            case "structure_explaining_comments", "comment_dense_complex_method", "long_method_with_internal_comments" ->
                    "Extract Function".equals(refactoring) ? 100 : 0;
            case "commented_out_code" -> "Extract Function".equals(refactoring) ? 80 : 0;
            case "debt_marker_comment" -> "Introduce Assertion".equals(refactoring) ? 60 : 0;
            default -> 0;
        };
    }

    private static String reasonFor(BadSmell smell, String refactoring, Map<String, Object> evidence) {
        String signal = displaySignal(evidence);
        String reason = switch (smell) {
            case MYSTERIOUS_NAME -> "The finding identifies an unclear " + readableKind(evidence) + "; this refactoring improves the program element name.";
            case DUPLICATED_CODE -> "The finding groups duplicated code (" + signal + "); this refactoring localizes the duplicated behavior.";
            case LONG_FUNCTION -> "The method evidence shows size or control-flow pressure; this refactoring attacks the strongest contributor before broad rewrites.";
            case LONG_PARAMETER_LIST -> "The parameter evidence indicates call-site complexity; this refactoring reduces the argument burden.";
            case GLOBAL_DATA -> "The evidence shows data reachable beyond a narrow owner; this refactoring narrows access before changing behavior.";
            case MUTABLE_DATA -> "The evidence shows risky mutation paths; this refactoring reduces or controls writes.";
            case DIVERGENT_CHANGE -> "The evidence shows multiple reasons to change in one owner; this refactoring separates those change axes.";
            case SHOTGUN_SURGERY -> "The evidence shows one change scattered across owners or files; this refactoring localizes that change.";
            case FEATURE_ENVY -> "The method uses foreign data more than local data; this refactoring moves or isolates the envied behavior.";
            case DATA_CLUMPS -> "The same data group appears together; this refactoring gives the group an explicit home.";
            case PRIMITIVE_OBSESSION -> "The evidence shows domain concepts carried as primitives; this refactoring introduces a domain shape.";
            case REPEATED_SWITCHES -> "The evidence shows repeated branch dispatch; this refactoring replaces repeated conditionals with type-specific behavior.";
            case LOOPS -> "The evidence shows explicit iteration; this refactoring makes the iteration intent explicit.";
            case LAZY_ELEMENT -> "The evidence shows a thin or empty element; this refactoring removes needless indirection.";
            case SPECULATIVE_GENERALITY -> "The evidence shows unused or weakly used generality; this refactoring removes future-facing structure.";
            case TEMPORARY_FIELD -> "The field appears valid only for a narrow calculation phase; this refactoring moves that state to a better owner.";
            case MESSAGE_CHAINS -> "The evidence shows deep object navigation; this refactoring hides or names the navigation path.";
            case MIDDLE_MAN -> "The evidence shows methods mostly forwarding to a delegate; this refactoring removes unnecessary delegation.";
            case INSIDER_TRADING -> "The evidence shows intimate access between collaborators; this refactoring restores an explicit boundary.";
            case LARGE_CLASS -> "The evidence shows too much size or low cohesion; this refactoring extracts a cohesive responsibility.";
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> "The classes play similar roles through different interfaces; this refactoring aligns their protocols.";
            case DATA_CLASS -> "The class mostly carries data with little behavior; this refactoring moves behavior or protects the data boundary.";
            case REFUSED_BEQUEST -> "The subclass rejects inherited behavior; this refactoring repairs or exits the inheritance relationship.";
            case COMMENTS -> "The comment evidence points to code that should explain itself; this refactoring moves explanation into structure.";
        };
        return reason + " Candidate: " + refactoring + ".";
    }

    private static boolean signalsContain(List<String> signals, String... candidates) {
        return Arrays.stream(candidates).anyMatch(signals::contains);
    }

    private static boolean nonEmpty(Object value) {
        if (value instanceof Iterable<?> values) {
            return values.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> values) {
            return !values.isEmpty();
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return value != null;
    }

    private static Map<?, ?> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static String displaySignal(Map<String, Object> evidence) {
        String signal = stringValue(evidence == null ? null : evidence.get("signal"));
        if (!signal.isBlank()) {
            return signal.replace('_', ' ');
        }
        List<String> signals = stringList(evidence == null ? null : evidence.get("signals"));
        if (!signals.isEmpty()) {
            return String.join(", ", signals).replace('_', ' ');
        }
        return "structured evidence";
    }

    private static String readableKind(Map<String, Object> evidence) {
        String kind = stringValue(evidence == null ? null : evidence.get("kind"));
        return kind.isBlank() ? "program element" : kind.replace('_', ' ');
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Iterable<?> values) {
            List<String> result = new java.util.ArrayList<>();
            for (Object nested : values) {
                String text = stringValue(nested);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
            return List.copyOf(new LinkedHashSet<>(result));
        }
        return List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private record RefactoringAdvice(
            String reason,
            List<String> preconditions,
            String firstSafeStep
    ) {
    }

    private static RefactoringCatalog loadStandard() {
        RefactoringCatalogDocument catalog = readResource(CATALOG_RESOURCE, RefactoringCatalogDocument.class);
        SmellRefactoringMapDocument smellMap = readResource(SMELL_MAP_RESOURCE, SmellRefactoringMapDocument.class);
        RefactoringPlaybookDocument playbookDocument = readResource(PLAYBOOK_RESOURCE, RefactoringPlaybookDocument.class);

        Map<String, RefactoringEntry> refactoringsByName = new LinkedHashMap<>();
        for (RefactoringEntry entry : catalog.refactorings()) {
            RefactoringEntry previous = refactoringsByName.put(entry.name(), entry);
            if (previous != null) {
                throw new IllegalStateException("Duplicate refactoring catalog entry: " + entry.name());
            }
        }

        Map<String, SmellRefactoringMapping> mappingsById = new LinkedHashMap<>();
        Set<String> knownSmellIds = Arrays.stream(BadSmell.values())
                .map(BadSmell::id)
                .collect(Collectors.toUnmodifiableSet());
        for (SmellRefactoringMapping mapping : smellMap.mappings()) {
            if (!knownSmellIds.contains(mapping.smellId())) {
                throw new IllegalStateException("Unknown smell id in refactoring mapping: " + mapping.smellId());
            }
            SmellRefactoringMapping previous = mappingsById.put(mapping.smellId(), mapping);
            if (previous != null) {
                throw new IllegalStateException("Duplicate smell refactoring mapping: " + mapping.smellId());
            }
            for (String refactoring : mapping.refactorings()) {
                if (!refactoringsByName.containsKey(refactoring)) {
                    throw new IllegalStateException(
                            "Smell " + mapping.smellId() + " references unknown refactoring: " + refactoring
                    );
                }
            }
        }
        for (BadSmell smell : BadSmell.values()) {
            if (!mappingsById.containsKey(smell.id())) {
                throw new IllegalStateException("Missing refactoring mapping for smell: " + smell.id());
            }
        }

        Map<String, RefactoringPlaybook> playbooksByName = new LinkedHashMap<>();
        for (RefactoringPlaybook playbook : playbookDocument.playbooks()) {
            if (!refactoringsByName.containsKey(playbook.name())) {
                throw new IllegalStateException("Unknown refactoring playbook: " + playbook.name());
            }
            RefactoringPlaybook previous = playbooksByName.put(playbook.name(), playbook);
            if (previous != null) {
                throw new IllegalStateException("Duplicate refactoring playbook: " + playbook.name());
            }
            if (playbook.steps().isEmpty()) {
                throw new IllegalStateException("Refactoring playbook has no steps: " + playbook.name());
            }
        }
        for (RefactoringEntry entry : catalog.refactorings()) {
            if (!playbooksByName.containsKey(entry.name())) {
                throw new IllegalStateException("Missing refactoring playbook: " + entry.name());
            }
        }

        return new RefactoringCatalog(
                catalog.refactorings(),
                refactoringsByName,
                smellMap.mappings(),
                mappingsById,
                playbookDocument.playbooks(),
                playbooksByName
        );
    }

    private static <T> T readResource(String resource, Class<T> type) {
        try (InputStream input = RefactoringCatalog.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing refactoring resource: " + resource);
            }
            return JSON.readValue(input, type);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read refactoring resource: " + resource, error);
        }
    }

    public record RefactoringEntry(
            String chapter,
            String name
    ) {
    }

    public record SmellRefactoringMapping(
            @JsonProperty("smell_id") String smellId,
            String smell,
            @JsonProperty("typical_evidence") String typicalEvidence,
            List<String> refactorings
    ) {
        public SmellRefactoringMapping {
            refactorings = List.copyOf(refactorings);
        }
    }

    public record RefactoringPlaybook(
            String name,
            @JsonProperty("applies_when") String appliesWhen,
            List<String> preconditions,
            List<String> steps,
            @JsonProperty("test_focus") List<String> testFocus,
            List<String> risks
    ) {
        public RefactoringPlaybook {
            preconditions = List.copyOf(preconditions);
            steps = List.copyOf(steps);
            testFocus = List.copyOf(testFocus);
            risks = List.copyOf(risks);
        }

        public String firstSafeStep() {
            return steps.isEmpty() ? "" : steps.getFirst();
        }
    }

    private record RefactoringCatalogDocument(
            String source,
            List<RefactoringEntry> refactorings
    ) {
    }

    private record SmellRefactoringMapDocument(
            String source,
            List<SmellRefactoringMapping> mappings
    ) {
    }

    private record RefactoringPlaybookDocument(
            String source,
            List<RefactoringPlaybook> playbooks
    ) {
    }
}
