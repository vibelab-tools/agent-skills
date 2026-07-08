package com.codex.refactor.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TreeSitterBash;
import org.treesitter.TreeSitterC;
import org.treesitter.TreeSitterCSharp;
import org.treesitter.TreeSitterCpp;
import org.treesitter.TreeSitterCss;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterHtml;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterRuby;
import org.treesitter.TreeSitterRust;
import org.treesitter.TreeSitterSql;
import org.treesitter.TreeSitterTypescript;
import org.treesitter.TreeSitterTsx;
import org.treesitter.TreeSitterVue;

import java.util.Optional;

public final class TreeSitterLanguageRegistry {
    private TreeSitterLanguageRegistry() {
    }

    public static Optional<TSLanguage> languageFor(String languageId) {
        return switch (languageId) {
            case "bash" -> Optional.of(new TreeSitterBash());
            case "c" -> Optional.of(new TreeSitterC());
            case "cpp" -> Optional.of(new TreeSitterCpp());
            case "csharp" -> Optional.of(new TreeSitterCSharp());
            case "go" -> Optional.of(new TreeSitterGo());
            case "python" -> Optional.of(new TreeSitterPython());
            case "rust" -> Optional.of(new TreeSitterRust());
            case "html" -> Optional.of(new TreeSitterHtml());
            case "css" -> Optional.of(new TreeSitterCss());
            case "javascript" -> Optional.of(new TreeSitterJavascript());
            case "typescript" -> Optional.of(new TreeSitterTypescript());
            case "tsx" -> Optional.of(new TreeSitterTsx());
            case "vue" -> Optional.of(new TreeSitterVue());
            case "ruby" -> Optional.of(new TreeSitterRuby());
            case "sql", "sql:postgresql", "sql:mysql", "sql:sqlite", "sql:tsql", "sql:plsql" ->
                    Optional.of(new TreeSitterSql());
            default -> Optional.empty();
        };
    }
}
