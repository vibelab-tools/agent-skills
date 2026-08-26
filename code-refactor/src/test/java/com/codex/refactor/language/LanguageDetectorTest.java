package com.codex.refactor.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsSupportedExtensionlessScriptsFromShebang() throws Exception {
        Path python = tempDir.resolve("review-changes");
        Files.writeString(python, "#!/usr/bin/env python3\nprint('ok')\n");
        Path shell = tempDir.resolve("analyze");
        Files.writeString(shell, "#!/bin/sh\necho ok\n");

        assertEquals("python", LanguageDetector.detect(python, "auto"));
        assertEquals("bash", LanguageDetector.detect(shell, "auto"));
    }
}
