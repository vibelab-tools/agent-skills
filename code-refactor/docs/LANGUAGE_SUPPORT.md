# Language Support

## Support Levels

- `experimental`: parser adapter exists but limitations are significant.
- `baseline-covered`: parser adapter and stable JSON are covered by positive
  and negative fixtures; only smells supported by structured evidence are
  reported.
- `strong-generic`: the parser supplies reusable function, field, local,
  branch, chain, and comment evidence to multiple detectors.
- `supported`: stable parser/metric behavior has focused fixtures.

Support means the parser and documented evidence work. It does not mean every
Fowler smell applies to every language. A valid analysis may return zero smells.

## Matrix

| Language | ID | Status | Parser | Important limitations |
| --- | --- | --- | --- | --- |
| Java | `java` | supported | JDK compiler AST | Primary semantic path; cross-project type resolution remains bounded. |
| Python | `python` | strong-generic | Tree-sitter Python | Dynamic dispatch and metaprogramming are not resolved. |
| JavaScript | `javascript` | strong-generic | Tree-sitter JavaScript | Runtime object shape is not inferred. |
| TypeScript | `typescript` | strong-generic | Tree-sitter TypeScript | Generic model uses syntax/type hints, not a full type checker. |
| TSX | `tsx` | strong-generic | Tree-sitter TSX | React hook/component semantics remain conservative. |
| Vue | `vue` | baseline-covered | Tree-sitter Vue | Deeper SFC script/template delegation remains future work. |
| C | `c` | strong-generic | Tree-sitter C | Preprocessor semantics remain limited. |
| C++ | `cpp` | strong-generic | Tree-sitter C++ | Template semantics remain limited. |
| C# | `csharp` | strong-generic | Tree-sitter C# | Structured inheritance/field/delegation evidence has focused tests. |
| Go | `go` | strong-generic | Tree-sitter Go | Interface satisfaction is not fully resolved. |
| Rust | `rust` | strong-generic | Tree-sitter Rust | Macro expansion and trait resolution are not semantic analysis. |
| SQL | `sql` | baseline-covered | Tree-sitter SQL | Dialect-specific and OO smells apply only where evidence fits. |
| HTML | `html` | baseline-covered | Tree-sitter HTML | Most Fowler OO smells do not apply. |
| CSS | `css` | baseline-covered | Tree-sitter CSS | Stylesheet-specific rules need refinement. |
| Bash | `bash` | strong-generic | Tree-sitter Bash | Runtime expansion and sourced code are outside static parsing. |
| Ruby | `ruby` | strong-generic | Tree-sitter Ruby | Metaprogramming semantics remain limited. |

## SQL Dialect Policy

Use a named dialect ID where dialect behavior matters:

```text
sql:postgresql
sql:mysql
sql:sqlite
sql:tsql
sql:plsql
```

Do not infer a dialect-specific finding from generic SQL syntax alone.

## Container And Dynamic Language Policy

- Vue should extract SFC blocks and delegate script analysis before making
  deeper JavaScript or TypeScript claims.
- TSX needs focused fixtures for hooks, generic components, and JSX edge cases
  before React-specific findings are promoted.
- Python, Bash, and Ruby improvements should target demonstrated false
  positives or missing structural evidence, not parser coverage counts.

Add a language-specific adapter or deeper semantic backend only when it
materially improves real-repository precision behind the same neutral model.
