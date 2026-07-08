package com.codex.refactor.refactoring;

import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.SmellFinding;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefactoringCatalogTest {
    private final RefactoringCatalog catalog = RefactoringCatalog.standard();

    @Test
    void catalogContainsChapterSixThroughTwelveRefactorings() {
        assertEquals(61, catalog.allRefactorings().size());
        assertEquals(61, catalog.allPlaybooks().size());
        assertTrue(catalog.refactoringByName("Extract Function").isPresent());
        assertTrue(catalog.refactoringByName("Replace Superclass with Delegate").isPresent());
        assertTrue(catalog.refactoringByName("Change Function Declaration").isPresent());
        assertTrue(catalog.refactoringByName("Rename Field").isPresent());
        assertFalse(catalog.refactoringByName("Rename Function").isPresent());
    }

    @Test
    void everyBadSmellHasOnlyCatalogBackedRecommendations() {
        Set<String> catalogNames = catalog.allRefactorings()
                .stream()
                .map(RefactoringCatalog.RefactoringEntry::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> mappedSmellIds = catalog.allSmellMappings()
                .stream()
                .map(RefactoringCatalog.SmellRefactoringMapping::smellId)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(BadSmell.values().length, catalog.allSmellMappings().size());
        for (BadSmell smell : BadSmell.values()) {
            List<String> recommendations = catalog.recommendedRefactoringNames(smell);
            assertTrue(mappedSmellIds.contains(smell.id()), "Missing mapping for " + smell.id());
            assertFalse(recommendations.isEmpty(), "Missing recommendations for " + smell.id());
            assertTrue(catalogNames.containsAll(recommendations), "Unknown recommendation in " + smell.id());
            assertTrue(catalog.mappingFor(smell).orElseThrow().typicalEvidence().length() > 10);
        }
    }

    @Test
    void everyCatalogRefactoringHasAPlaybook() {
        Set<String> playbookNames = catalog.allPlaybooks()
                .stream()
                .map(RefactoringCatalog.RefactoringPlaybook::name)
                .collect(Collectors.toUnmodifiableSet());

        for (RefactoringCatalog.RefactoringEntry entry : catalog.allRefactorings()) {
            RefactoringCatalog.RefactoringPlaybook playbook = catalog.playbookByName(entry.name()).orElseThrow();
            assertTrue(playbookNames.contains(entry.name()));
            assertFalse(playbook.appliesWhen().isBlank(), entry.name());
            assertFalse(playbook.preconditions().isEmpty(), entry.name());
            assertFalse(playbook.steps().isEmpty(), entry.name());
            assertFalse(playbook.testFocus().isEmpty(), entry.name());
            assertFalse(playbook.risks().isEmpty(), entry.name());
        }
    }

    @Test
    void mappingsUseStrictBookNamesForRenamedFunctionAndInheritanceCases() {
        assertIterableEquals(
                List.of("Change Function Declaration", "Rename Variable", "Rename Field"),
                catalog.recommendedRefactoringNames(BadSmell.MYSTERIOUS_NAME)
        );
        assertIterableEquals(
                List.of("Push Down Method", "Push Down Field", "Replace Subclass with Delegate", "Replace Superclass with Delegate"),
                catalog.recommendedRefactoringNames(BadSmell.REFUSED_BEQUEST)
        );
        assertIterableEquals(
                List.of("Change Function Declaration", "Move Function", "Extract Superclass"),
                catalog.recommendedRefactoringNames(BadSmell.ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES)
        );
        assertIterableEquals(
                List.of(
                        "Extract Function",
                        "Replace Temp with Query",
                        "Introduce Parameter Object",
                        "Preserve Whole Object",
                        "Replace Function with Command",
                        "Decompose Conditional",
                        "Replace Conditional with Polymorphism",
                        "Split Loop"
                ),
                catalog.recommendedRefactoringNames(BadSmell.LONG_FUNCTION)
        );
    }

    @Test
    void recommendationsAreOrderedByFindingEvidenceWhenAvailable() {
        assertIterableEquals(
                List.of(
                        "Extract Function",
                        "Split Loop",
                        "Decompose Conditional",
                        "Replace Conditional with Polymorphism",
                        "Replace Function with Command",
                        "Replace Temp with Query",
                        "Introduce Parameter Object",
                        "Preserve Whole Object"
                ),
                catalog.recommendedRefactoringNames(
                        BadSmell.LONG_FUNCTION,
                        Map.of(
                                "signals", List.of("too_many_physical_lines", "high_cyclomatic_complexity"),
                                "physical_lines", 90,
                                "loop_count", 2,
                                "branch_dispatch_count", 1,
                                "local_variable_count", 7,
                                "parameter_count", 5
                        )
                )
        );
        assertIterableEquals(
                List.of("Introduce Parameter Object", "Preserve Whole Object", "Extract Class"),
                catalog.recommendedRefactoringNames(
                        BadSmell.DATA_CLUMPS,
                        Map.of("signal", "repeated_parameter_group")
                )
        );
        assertIterableEquals(
                List.of("Extract Class", "Introduce Parameter Object", "Preserve Whole Object"),
                catalog.recommendedRefactoringNames(
                        BadSmell.DATA_CLUMPS,
                        Map.of("signal", "repeated_field_group")
                )
        );
    }

    @Test
    void evidenceAwareOrderingCoversEveryBadSmell() {
        for (BadSmell smell : BadSmell.values()) {
            List<String> recommendations = catalog.recommendedRefactoringNames(smell, representativeEvidence(smell));
            List<Map<String, Object>> rationales = catalog.recommendedRefactoringRationale(smell, representativeEvidence(smell));

            assertEquals(expectedFirstRecommendation(smell), recommendations.getFirst(), "Unexpected first recommendation for " + smell.id());
            assertEquals(recommendations.size(), rationales.size(), "Rationale size mismatch for " + smell.id());
            assertEquals(recommendations.getFirst(), rationales.getFirst().get("name"));
            assertTrue(rationales.getFirst().get("reason").toString().contains(recommendations.getFirst()));
            assertTrue(rationales.getFirst().get("preconditions") instanceof List<?>);
            assertFalse(rationales.getFirst().get("first_safe_step").toString().isBlank());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void smellFindingJsonUsesCatalogRecommendationsAndDetails() {
        SmellFinding finding = new SmellFinding(
                BadSmell.COMMENTS,
                "low",
                "medium",
                Map.of("symbol", "explain"),
                Map.of("signal", "commented_out_code"),
                "Comment smell.",
                "Refactor the code so the comment is no longer needed."
        );

        List<String> recommendations = (List<String>) finding.toJson().get("recommended_refactorings");
        List<Map<String, Object>> details = (List<Map<String, Object>>) finding.toJson()
                .get("recommended_refactoring_details");
        List<Map<String, Object>> rationales = (List<Map<String, Object>>) finding.toJson()
                .get("recommended_refactoring_rationale");

        assertIterableEquals(
                List.of("Extract Function", "Change Function Declaration", "Introduce Assertion"),
                recommendations
        );
        assertEquals("Extract Function", details.getFirst().get("name"));
        assertEquals("6.1", details.getFirst().get("chapter"));
        assertEquals(Set.of("name", "chapter"), details.getFirst().keySet());
        assertEquals("Extract Function", rationales.getFirst().get("name"));
        assertTrue(rationales.getFirst().get("preconditions") instanceof List<?>);
        assertTrue(rationales.getFirst().get("steps") instanceof List<?>);
        assertTrue(rationales.getFirst().get("test_focus") instanceof List<?>);
        assertTrue(rationales.getFirst().get("risks") instanceof List<?>);
    }

    @Test
    void skillReferenceUsesOnlyCatalogBackedRefactoringNames() throws Exception {
        String reference = Files.readString(Path.of("skill/code-refactor/references/smell-to-refactoring.md"));
        Map<String, List<String>> referenceBySmell = new LinkedHashMap<>();

        for (String line : reference.split("\\R")) {
            if (!line.startsWith("| ") || line.contains("---") || line.contains("Candidate Refactorings")) {
                continue;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 4) {
                continue;
            }
            List<String> refactorings = List.of(columns[3].split(","))
                    .stream()
                    .map(String::trim)
                    .map(RefactoringCatalogTest::markdownLinkText)
                    .filter(name -> !name.isEmpty())
                    .toList();
            referenceBySmell.put(markdownLinkText(columns[1].trim()), refactorings);
        }

        for (BadSmell smell : BadSmell.values()) {
            List<String> refactorings = referenceBySmell.get(smell.englishName());
            assertIterableEquals(
                    catalog.recommendedRefactoringNames(smell),
                    refactorings,
                    "Skill reference mapping drifted for " + smell.id()
            );
        }
    }

    private static String markdownLinkText(String value) {
        int open = value.indexOf('[');
        int close = value.indexOf(']');
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close);
        }
        return value;
    }

    private static Map<String, Object> representativeEvidence(BadSmell smell) {
        return switch (smell) {
            case MYSTERIOUS_NAME -> Map.of("kind", "method");
            case DUPLICATED_CODE -> Map.of("signal", "normalized_statement_shape", "duplicate_count", 2);
            case LONG_FUNCTION -> Map.of(
                    "signals", List.of("too_many_physical_lines", "high_cyclomatic_complexity"),
                    "physical_lines", 90,
                    "loop_count", 2,
                    "branch_dispatch_count", 1,
                    "local_variable_count", 7,
                    "parameter_count", 5
            );
            case LONG_PARAMETER_LIST -> Map.of(
                    "signals", List.of("boolean_flag_cluster", "primitive_heavy_parameter_list"),
                    "boolean_flag_count", 2,
                    "primitive_parameter_count", 5,
                    "parameter_count", 6
            );
            case GLOBAL_DATA -> Map.of("signal", "public_static_mutable_data");
            case MUTABLE_DATA -> Map.of("signals", List.of("multiple_writers", "public_mutable_field"));
            case DIVERGENT_CHANGE -> Map.of(
                    "concern_count", 3,
                    "collaborator_clusters", List.of(Map.of("name", "Repository"))
            );
            case SHOTGUN_SURGERY -> Map.of("owner_count", 3, "file_count", 3, "change_key", "update_price_rules/0");
            case FEATURE_ENVY -> Map.of(
                    "foreign_access_ratio", 0.8,
                    "foreign_accesses", 6,
                    "own_data_accesses", 1,
                    "resolved_foreign_type", "Customer"
            );
            case DATA_CLUMPS -> Map.of("signal", "repeated_parameter_group");
            case PRIMITIVE_OBSESSION -> Map.of("signal", "coded_primitive_branching");
            case REPEATED_SWITCHES -> Map.of("signal", "repeated_switch_selector");
            case LOOPS -> Map.of("signal", "collection_transformation_loop");
            case LAZY_ELEMENT -> Map.of("signals", List.of("empty_class"));
            case SPECULATIVE_GENERALITY -> Map.of(
                    "signals", List.of("unused_abstraction"),
                    "abstract", true,
                    "known_subtypes_or_implementers", 0,
                    "type_reference_count", 0
            );
            case TEMPORARY_FIELD -> Map.of("signals", List.of("temporary_name", "single_calculation_phase"));
            case MESSAGE_CHAINS -> Map.of("signal", "long_object_navigation_chain", "max_chain_depth", 5);
            case MIDDLE_MAN -> Map.of(
                    "signal", "mostly_forwarding_class",
                    "delegation_methods", 5,
                    "delegation_ratio", 0.85,
                    "value_added_method_count", 0
            );
            case INSIDER_TRADING -> Map.of(
                    "signal", "deep_collaborator_structure_access",
                    "max_message_chain_depth", 5,
                    "collaborator_count", 2
            );
            case LARGE_CLASS -> Map.of(
                    "signals", List.of("too_many_fields", "multiple_responsibility_clusters"),
                    "field_count", 20,
                    "method_field_graph", Map.of("extraction_cluster_count", 2)
            );
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> Map.of(
                    "signal", "similar_role_different_interface",
                    "exact_method_name_overlap", 0.2,
                    "method_role_similarity", 0.8,
                    "shared_caller_owners", List.of("Client")
            );
            case DATA_CLASS -> Map.of(
                    "signals", List.of("public_fields", "externally_mutable_data"),
                    "setter_method_count", 2,
                    "behavioral_method_count", 0
            );
            case REFUSED_BEQUEST -> Map.of(
                    "signal", "rejected_inherited_contract",
                    "strong_rejection_count", 1,
                    "rejected_ratio", 0.5
            );
            case COMMENTS -> Map.of("signal", "structure_explaining_comments");
        };
    }

    private static String expectedFirstRecommendation(BadSmell smell) {
        return switch (smell) {
            case MYSTERIOUS_NAME -> "Change Function Declaration";
            case DUPLICATED_CODE -> "Slide Statements";
            case LONG_FUNCTION -> "Extract Function";
            case LONG_PARAMETER_LIST -> "Remove Flag Argument";
            case GLOBAL_DATA -> "Encapsulate Variable";
            case MUTABLE_DATA -> "Encapsulate Variable";
            case DIVERGENT_CHANGE -> "Extract Class";
            case SHOTGUN_SURGERY -> "Move Function";
            case FEATURE_ENVY -> "Move Function";
            case DATA_CLUMPS -> "Introduce Parameter Object";
            case PRIMITIVE_OBSESSION -> "Replace Type Code with Subclasses";
            case REPEATED_SWITCHES -> "Replace Conditional with Polymorphism";
            case LOOPS -> "Replace Loop with Pipeline";
            case LAZY_ELEMENT -> "Inline Class";
            case SPECULATIVE_GENERALITY -> "Collapse Hierarchy";
            case TEMPORARY_FIELD -> "Extract Class";
            case MESSAGE_CHAINS -> "Hide Delegate";
            case MIDDLE_MAN -> "Remove Middle Man";
            case INSIDER_TRADING -> "Hide Delegate";
            case LARGE_CLASS -> "Extract Class";
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> "Change Function Declaration";
            case DATA_CLASS -> "Encapsulate Record";
            case REFUSED_BEQUEST -> "Replace Subclass with Delegate";
            case COMMENTS -> "Extract Function";
        };
    }
}
