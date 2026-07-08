package com.codex.refactor.language;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class LanguageDetector {
    private static final Set<String> SUPPORTED_LANGUAGE_IDS = Set.of(
            "bash",
            "c",
            "cpp",
            "csharp",
            "go",
            "python",
            "rust",
            "html",
            "css",
            "javascript",
            "typescript",
            "tsx",
            "vue",
            "ruby",
            "sql",
            "sql:postgresql",
            "sql:mysql",
            "sql:sqlite",
            "sql:tsql",
            "sql:plsql",
            "java"
    );

    private LanguageDetector() {
    }

    public static String detect(Path path, String forcedLanguage) {
        if (forcedLanguage != null && !"auto".equals(forcedLanguage)) {
            return forcedLanguage;
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".c")) {
            return "c";
        }
        if (fileName.endsWith(".cc") || fileName.endsWith(".cpp") || fileName.endsWith(".cxx")
                || fileName.endsWith(".hpp") || fileName.endsWith(".hh") || fileName.endsWith(".hxx")) {
            return "cpp";
        }
        if (fileName.endsWith(".cs")) {
            return "csharp";
        }
        if (fileName.endsWith(".go")) {
            return "go";
        }
        if (fileName.endsWith(".rs")) {
            return "rust";
        }
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "html";
        }
        if (fileName.endsWith(".css")) {
            return "css";
        }
        if (fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs")) {
            return "javascript";
        }
        if (fileName.endsWith(".ts")) {
            return "typescript";
        }
        if (fileName.endsWith(".tsx")) {
            return "tsx";
        }
        if (fileName.endsWith(".vue")) {
            return "vue";
        }
        if (fileName.endsWith(".rb")) {
            return "ruby";
        }
        if (fileName.endsWith(".sql")) {
            return "sql";
        }
        if (fileName.endsWith(".java")) {
            return "java";
        }
        if (fileName.endsWith(".py")) {
            return "python";
        }
        if (fileName.endsWith(".sh") || fileName.endsWith(".bash")) {
            return "bash";
        }
        return "unknown";
    }

    public static boolean isSupported(String languageId) {
        return languageId != null && SUPPORTED_LANGUAGE_IDS.contains(languageId);
    }
}
