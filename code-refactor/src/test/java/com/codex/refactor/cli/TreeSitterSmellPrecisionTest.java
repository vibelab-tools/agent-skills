package com.codex.refactor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TreeSitterSmellPrecisionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void cleanPythonFunctionCanProduceNoSmellFindings() throws Exception {
        Path source = tempDir.resolve("normalize.py");
        Files.writeString(source, """
                def normalize(value):
                    cleaned = value.strip()
                    return cleaned
                """);

        CliRun run = run("detect-smells", "--json", source.toString());

        assertEquals(0, run.exitCode(), run.stderr());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("ok", report.path("status").asText());
        assertEquals(0, report.path("summary").path("total_smells").asInt());
    }

    @Test
    void rustQuoteComparisonsDoNotCrashRepeatedSwitchDetection() throws Exception {
        Path source = tempDir.resolve("selector.rs");
        Files.writeString(source, """
                fn contains_quote(selector: &str) -> bool {
                    for ch in selector.chars() {
                        if ch == '"' || ch == '\\'' {
                            return true;
                        }
                    }
                    false
                }
                """);

        CliRun run = run("detect-smells", "--json", source.toString());

        assertEquals(0, run.exitCode(), run.stderr());
        assertEquals("ok", JSON.readTree(run.stdout()).path("status").asText());
    }

    @Test
    void pythonDataFieldsAreNotMutableOrTemporarySmellsByDefault() throws Exception {
        Path source = tempDir.resolve("settings.py");
        Files.writeString(source, """
                from dataclasses import dataclass

                @dataclass
                class Settings:
                    name: str
                    retries: int
                """);

        CliRun run = run("detect-smells", "--json", "--min-confidence", "high", source.toString());

        assertEquals(0, run.exitCode(), run.stderr());
        JsonNode smells = JSON.readTree(run.stdout()).path("files").get(0).path("smells");
        for (JsonNode smell : smells) {
            assertFalse(smell.path("id").asText().equals("mutable-data"));
            assertFalse(smell.path("id").asText().equals("temporary-field"));
        }
    }

    @Test
    void rustIteratorPipelineIsNotReportedAsMessageChain() throws Exception {
        Path source = tempDir.resolve("path.rs");
        Files.writeString(source, """
                fn extension(path: &Path) -> Option<String> {
                    path.extension()
                        .and_then(|value| value.to_str())
                        .map(str::to_ascii_lowercase)
                }
                """);

        CliRun run = run("detect-smells", "--json", source.toString());

        assertEquals(0, run.exitCode(), run.stderr());
        JsonNode smells = JSON.readTree(run.stdout()).path("files").get(0).path("smells");
        for (JsonNode smell : smells) {
            assertFalse(smell.path("id").asText().equals("message-chains"));
        }
    }

    private static CliRun run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = new Cli(
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        ).run(args);
        return new CliRun(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }
}
