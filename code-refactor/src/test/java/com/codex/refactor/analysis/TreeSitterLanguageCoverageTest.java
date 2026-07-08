package com.codex.refactor.analysis;

import com.codex.refactor.smell.BadSmellDetectionDispatcher;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.cli.TreeSitterFixtureSources;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterLanguageCoverageTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageFixtures")
    void parsesRequestedLanguageAndRunsSmellDispatcher(String language, String extension, String source) throws Exception {
        Path fixture = tempDir.resolve("sample." + extension);
        Files.writeString(fixture, source);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(fixture, language);

        assertEquals("tree-sitter", analysis.parser());
        assertEquals(language, analysis.language());
        assertEquals("ok", analysis.status());
        assertTrue(analysis.physicalLines() > 0);

        BadSmellDetectionDispatcher.standard().detect(new SmellAnalysisContext(analysis));
    }

    static Stream<Arguments> languageFixtures() {
        return TreeSitterFixtureSources.languageFixtures();
    }
}
