package com.codex.refactor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiLanguageCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageFixtures")
    void detectSmellsUsesTreeSitterForRequestedLanguage(String language, String extension, String source) throws Exception {
        Path fixture = tempDir.resolve("sample." + extension);
        Files.writeString(fixture, source);

        CliRun run = run("detect-smells", "--json", "--language", language, fixture.toString());

        assertEquals(0, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        JsonNode file = report.path("files").get(0);
        assertEquals("ok", report.path("status").asText());
        assertEquals("ok", file.path("status").asText());
        assertEquals(language, file.path("language").asText());
        assertEquals("tree-sitter", file.path("parser").asText());
        assertTrue(report.path("summary").path("total_smells").asInt() > 0);
        assertTrue(containsSmell(file, "comments"));
    }

    static Stream<Arguments> languageFixtures() {
        return TreeSitterFixtureSources.languageFixtures();
    }

    private static CliRun run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Cli cli = new Cli(
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );
        int exitCode = cli.run(args);
        return new CliRun(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }

    private static boolean containsSmell(JsonNode file, String smellId) {
        for (JsonNode smell : file.path("smells")) {
            if (smellId.equals(smell.path("id").asText())) {
                return true;
            }
        }
        return false;
    }
}
