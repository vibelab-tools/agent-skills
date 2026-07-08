# Language Support Plan

## Support Levels

- `planned`: target language, no implementation yet.
- `candidate`: likely parser path identified, not validated.
- `experimental`: parser adapter exists but limitations are significant.
- `baseline-covered`: parser adapter exists, JSON is stable on fixtures, and the
  requested-language x 24-smell matrix passes; some smell signals may still be
  low confidence for language paradigms where Fowler's OO smells only partially
  apply.
- `strong-generic`: parser adapter contributes reusable structural evidence
  such as functions, fields, module declarations, locals, branch dispatches,
  message chains, and comments to multiple detectors.
- `supported`: fixture-backed parser adapter and JSON metrics are stable.
- `fallback-needed`: ANTLR path is unclear or insufficient.

## Matrix

| Language | Target ID | Initial Status | Parser Direction | Notes |
| --- | --- | --- | --- | --- |
| Java | `java` | supported | JDK compiler AST API first, ANTLR remains a future backend option | Parser-backed and fixture-tested for metrics and all 24 smell signals. |
| Python | `python` | strong-generic | Tree-sitter Python grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; generic AST model supplies functions, locals, branches, comments, and chain evidence where grammar nodes expose them. |
| JavaScript | `javascript` | strong-generic | Tree-sitter JavaScript grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; module globals, locals, if/else dispatches, message chains, and statement shapes are covered. |
| TypeScript | `typescript` | strong-generic | Tree-sitter TypeScript grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; module globals, type hints, locals, if/else dispatches, and statement shapes are covered. |
| TSX | `tsx` | strong-generic | Tree-sitter TSX grammar | Fixture-tested parser-backed CLI path and 24-smell matrix with JSX syntax; React-specific hook semantics remain future refinement. |
| Vue | `vue` | baseline-covered | Tree-sitter Vue grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; generic container evidence exists, but deeper SFC script/template delegation remains future work. |
| C | `c` | strong-generic | Tree-sitter C grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; preprocessor semantics remain limited. |
| C++ | `cpp` | strong-generic | Tree-sitter C++ grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; modern template semantics remain limited. |
| C# | `csharp` | strong-generic | Tree-sitter C# grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; inheritance, modifiers, field access, delegation, override, and unsupported-operation evidence have focused tests. |
| Go | `go` | strong-generic | Tree-sitter Go grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; package-level and function-level evidence is covered where grammar nodes expose it. |
| Rust | `rust` | strong-generic | Tree-sitter Rust grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; macro expansion is not semantic analysis. |
| SQL | `sql` | baseline-covered | Tree-sitter SQL grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; dialect-specific validation and non-OO smell semantics remain limited. |
| HTML | `html` | baseline-covered | Tree-sitter HTML grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; Fowler OO smells only partially apply. |
| CSS | `css` | baseline-covered | Tree-sitter CSS grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; stylesheet-specific rules need refinement. |
| Bash | `bash` | strong-generic | Tree-sitter Bash grammar | Fixture-tested parser-backed CLI path and 24-smell matrix; shell-specific runtime expansion remains outside static parsing. |
| Ruby | `ruby` | strong-generic | Tree-sitter Ruby grammar | Fixture-tested parser-backed CLI path and 24-smell matrix. |

## SQL Dialect Policy

Use dialect IDs instead of claiming universal SQL:

- `sql:postgresql`
- `sql:mysql`
- `sql:sqlite`
- `sql:tsql`
- `sql:plsql`

Start with one dialect that has representative local fixtures.

## Vue Policy

Vue support should be implemented as a container adapter:

1. Parse the SFC structure.
2. Extract `<script>` and `<script setup>` blocks.
3. Detect JavaScript or TypeScript from the `lang` attribute.
4. Delegate script analysis to JS/TS/TSX adapter.
5. Optionally add template-specific metrics later.

Do not count template analysis as complete Vue support unless template rules are
implemented and tested.

## TSX Policy

TSX support should continue expanding fixtures across:

- JSX elements,
- nested expressions,
- hooks or function components,
- TypeScript type annotations,
- generic components or ambiguous `<T>` syntax.

Current baseline uses the dedicated `tree-sitter-tsx` grammar and includes JSX
plus TypeScript annotation fixtures. Add focused hook and generic-component
fixtures before claiming deeper React-specific semantics.

## Python, Bash, And Ruby Policy

Python, Bash, and Ruby have baseline Tree-sitter coverage. Further work should
focus on language-specific extraction precision rather than parser feasibility.

Acceptable outcomes:

- Keep the current Tree-sitter adapter if it remains reliable.
- Add an ANTLR or semantic adapter only when it materially improves findings.
- Keep any deeper support hidden behind the same neutral source model.
