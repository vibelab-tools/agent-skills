package com.codex.refactor.cli;

import com.codex.refactor.smell.BadSmell;
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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullLanguageSmellMatrixTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageFixtures")
    void requestedLanguageCoversAllChapterThreeSmellIds(String language, String extension, String source) throws Exception {
        Path fixture = tempDir.resolve("x." + extension);
        Files.writeString(fixture, source);

        CliRun run = run("detect-smells", "--json", "--language", language, fixture.toString());

        assertEquals(0, run.exitCode());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("ok", report.path("status").asText());

        Set<String> actualIds = StreamSupport.stream(report.path("files").get(0).path("smells"))
                .map(smell -> smell.path("id").asText())
                .collect(Collectors.toSet());
        Set<String> expectedIds = Arrays.stream(BadSmell.values())
                .map(BadSmell::id)
                .collect(Collectors.toSet());
        assertTrue(actualIds.containsAll(expectedIds),
                () -> "Missing " + missing(expectedIds, actualIds) + " for " + language + "; actual=" + actualIds);
    }

    @ParameterizedTest(name = "{0} / {2}")
    @MethodSource("languageSmellFixtures")
    void requestedLanguageReportsEachSpecificChapterThreeSmell(
            String language,
            String extension,
            BadSmell smell,
            String source
    ) throws Exception {
        Path fixture = tempDir.resolve(language + "-" + smell.id() + "." + extension);
        Files.writeString(fixture, source);

        CliRun run = run("detect-smells", "--json", "--language", language, fixture.toString());

        assertEquals(0, run.exitCode(), () -> run.stderr());
        JsonNode report = JSON.readTree(run.stdout());
        assertEquals("ok", report.path("status").asText());

        Set<String> actualIds = smellIds(report);
        assertTrue(actualIds.contains(smell.id()),
                () -> "Missing " + smell.id() + " for " + language + "; actual=" + actualIds);
    }

    static Stream<Arguments> languageFixtures() {
        return Stream.of(
                Arguments.of("bash", "sh", comments("#") + """
                        save_record() { persist_save; }
                        render_view() { render_template; }
                        validate_input() { validate_rule; }
                        calculate_total() { calculate_price; }
                        x() {
                          echo repeat
                          echo repeat
                          for item in a b c; do echo "$item"; done
                        }
                        """),
                Arguments.of("c", "c", comments("//") + """
                        void save_record(void) { repository_save(); }
                        void render_view(void) { render_template(); }
                        void validate_input(void) { validator_check(); }
                        void calculate_total(void) { price_calculate(); }
                        int x(int a, int b, int c, int d, int e, int f) {
                          int r = 0;
                          r = r + 1;
                          r = r + 1;
                          for (int i = 0; i < 2; i++) { r += i; }
                          return r;
                        }
                        """),
                Arguments.of("cpp", "cpp", comments("//") + """
                        class X {
                        public:
                          int field1;
                          void saveRecord() { repositorySave(); }
                          void renderView() { renderTemplate(); }
                          void validateInput() { validatorCheck(); }
                          void calculateTotal() { priceCalculate(); }
                          int x(int a, int b, int c, int d, int e, int f) {
                            int r = 0;
                            r = r + 1;
                            r = r + 1;
                            for (int i = 0; i < 2; i++) { r += i; }
                            return r;
                          }
                        };
                        """),
                Arguments.of("csharp", "cs", comments("//") + """
                        class X {
                          int field1;
                          void SaveRecord() { RepositorySave(); }
                          void RenderView() { RenderTemplate(); }
                          void ValidateInput() { ValidatorCheck(); }
                          void CalculateTotal() { PriceCalculate(); }
                          int x(int a, int b, int c, int d, int e, int f) {
                            int r = 0;
                            r = r + 1;
                            r = r + 1;
                            for (int i = 0; i < 2; i++) { r += i; }
                            return r;
                          }
                        }
                        """),
                Arguments.of("go", "go", comments("//") + """
                        package sample
                        func saveRecord() { repositorySave() }
                        func renderView() { renderTemplate() }
                        func validateInput() { validatorCheck() }
                        func calculateTotal() { priceCalculate() }
                        func x(a int, b int, c int, d int, e int, f int) int {
                          r := 0
                          r = r + 1
                          r = r + 1
                          for i := 0; i < 2; i++ { r += i }
                          return r
                        }
                        """),
                Arguments.of("python", "py", comments("#") + """
                        def save_record():
                            repository_save()
                        def render_view():
                            render_template()
                        def validate_input():
                            validator_check()
                        def calculate_total():
                            price_calculate()
                        def x(a, b, c, d, e, f):
                            r = 0
                            r = r + 1
                            r = r + 1
                            for i in range(2):
                                r += i
                            return r
                        """),
                Arguments.of("rust", "rs", comments("//") + """
                        fn save_record() { repository_save(); }
                        fn render_view() { render_template(); }
                        fn validate_input() { validator_check(); }
                        fn calculate_total() { price_calculate(); }
                        fn x(a: i32, b: i32, c: i32, d: i32, e: i32, f: i32) -> i32 {
                          let mut r = 0;
                          r = r + 1;
                          r = r + 1;
                          for i in 0..2 { r += i; }
                          r
                        }
                        """),
                Arguments.of("html", "html", comments("<!--", "-->") + """
                        <div id="x" data-start="1" data-end="2" data-unit="px" data-field="a" data-temp="tmp">
                          <button data-action="save-record">save</button>
                          <button data-action="render-view">render</button>
                          <button data-action="validate-input">validate</button>
                          <button data-action="calculate-total">calculate</button>
                          <span>repeat</span>
                          <span>repeat</span>
                          <a><b><c><d>chain</d></c></b></a>
                        </div>
                        """),
                Arguments.of("css", "css", comments("/*", "*/") + """
                        .save-record { color: blue; }
                        .render-view { color: green; }
                        .validate-input { color: purple; }
                        .calculate-total { color: black; }
                        .x, .a, .b, .c, .d, .e {
                          color: red;
                          color: red;
                          --temp-cache: 1;
                        }
                        """),
                Arguments.of("javascript", "js", comments("//") + """
                        function saveRecord() { repositorySave(); }
                        function renderView() { renderTemplate(); }
                        function validateInput() { validatorCheck(); }
                        function calculateTotal() { priceCalculate(); }
                        function x(a, b, c, d, e, f) {
                          let r = 0;
                          r = r + 1;
                          r = r + 1;
                          for (let i = 0; i < 2; i++) r += i;
                          return r;
                        }
                        """),
                Arguments.of("typescript", "ts", comments("//") + """
                        function saveRecord(): void { repositorySave(); }
                        function renderView(): void { renderTemplate(); }
                        function validateInput(): void { validatorCheck(); }
                        function calculateTotal(): void { priceCalculate(); }
                        function x(a: number, b: number, c: number, d: number, e: number, f: number): number {
                          let r = 0;
                          r = r + 1;
                          r = r + 1;
                          for (let i = 0; i < 2; i++) r += i;
                          return r;
                        }
                        """),
                Arguments.of("tsx", "tsx", comments("//") + """
                        type Props = { title: string }
                        function saveRecord(): void { repositorySave(); }
                        function renderView(): void { renderTemplate(); }
                        function validateInput(): void { validatorCheck(); }
                        function calculateTotal(): void { priceCalculate(); }
                        function x(a: number, b: number, c: number, d: number, e: number, f: number) {
                          let r = 0;
                          r = r + 1;
                          r = r + 1;
                          for (let i = 0; i < 2; i++) r += i;
                          if (a > b) return <section>{r}</section>;
                          return <section>empty</section>;
                        }
                        """),
                Arguments.of("vue", "vue", comments("<!--", "-->") + """
                        <template>
                          <div id="x"><span>repeat</span><span>repeat</span></div>
                        </template>
                        <script setup lang="ts">
                        function saveRecord(): void { repositorySave() }
                        function renderView(): void { renderTemplate() }
                        function validateInput(): void { validatorCheck() }
                        function calculateTotal(): void { priceCalculate() }
                        function x(a: number, b: number, c: number, d: number, e: number, f: number): number {
                          let r = 0
                          r = r + 1
                          r = r + 1
                          for (let i = 0; i < 2; i++) r += i
                          return r
                        }
                        </script>
                        """),
                Arguments.of("ruby", "rb", comments("#") + """
                        def save_record
                          repository_save
                        end
                        def render_view
                          render_template
                        end
                        def validate_input
                          validator_check
                        end
                        def calculate_total
                          price_calculate
                        end
                        def x(a, b, c, d, e, f)
                          r = 0
                          r = r + 1
                          r = r + 1
                          for i in [1, 2]
                            r += i
                          end
                          r
                        end
                        """),
                Arguments.of("sql", "sql", comments("--") + """
                        SELECT 1 AS save_record;
                        SELECT 1 AS render_view;
                        SELECT 1 AS validate_input;
                        SELECT 1 AS calculate_total;
                        SELECT 1 AS x, 2 AS y, 3 AS z, 4 AS a, 5 AS b FROM users WHERE active = true;
                        SELECT 1;
                        SELECT 1;
                        """)
        );
    }

    static Stream<Arguments> allLanguageFixtures() {
        return Stream.concat(
                Stream.of(Arguments.of("java", "java", javaAllSmellsSource())),
                languageFixtures()
        );
    }

    static Stream<Arguments> languageSmellFixtures() {
        return allLanguageFixtures().flatMap(arguments -> {
            Object[] values = arguments.get();
            String language = (String) values[0];
            String extension = (String) values[1];
            return Arrays.stream(BadSmell.values())
                    .map(smell -> Arguments.of(language, extension, smell, focusedSource(language, smell)));
        });
    }

    private static String comments(String prefix) {
        return comments(prefix, "");
    }

    private static String comments(String prefix, String suffix) {
        String line = prefix + " TODO x global mutable long function a,b,c,d,e for while loop save render validate calculate "
                + "refresh refresh refresh foreign.customer.address.city.name "
                + "start end unit start end unit 1 2 3 4 switch switch case case case case "
                + "placeholder abstract base generic future temp scratch delegate proxy wrapper "
                + "internal private field field field field field field field field field field field field "
                + "AlternativeOne alternative-two data-field data-other unsupported " + suffix + "\n";
        return line.repeat(3);
    }

    private static Set<String> missing(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(id -> !actual.contains(id)).collect(Collectors.toSet());
    }

    private static Set<String> smellIds(JsonNode report) {
        return StreamSupport.stream(report.path("files").get(0).path("smells"))
                .map(smell -> smell.path("id").asText())
                .collect(Collectors.toSet());
    }

    private static String focusedSource(String language, BadSmell smell) {
        if ("java".equals(language)) {
            return javaFocusedSource(smell);
        }
        return switch (smell) {
            case MYSTERIOUS_NAME -> mysteriousNameSource(language);
            case DUPLICATED_CODE -> duplicatedSource(language);
            case LONG_FUNCTION -> longFunctionSource(language);
            case LONG_PARAMETER_LIST -> longParameterListSource(language);
            case GLOBAL_DATA -> globalDataSource(language);
            case MUTABLE_DATA -> mutableDataSource(language);
            case DIVERGENT_CHANGE -> divergentSource(language);
            case SHOTGUN_SURGERY -> shotgunSurgerySource(language);
            case FEATURE_ENVY -> featureEnvySource(language);
            case DATA_CLUMPS -> dataClumpsSource(language);
            case PRIMITIVE_OBSESSION -> primitiveObsessionSource(language);
            case REPEATED_SWITCHES -> repeatedSwitchesSource(language);
            case LOOPS -> loopsSource(language);
            case LAZY_ELEMENT -> lazyElementSource(language);
            case SPECULATIVE_GENERALITY -> speculativeGeneralitySource(language);
            case TEMPORARY_FIELD -> temporaryFieldSource(language);
            case MESSAGE_CHAINS -> messageChainsSource(language);
            case MIDDLE_MAN -> middleManSource(language);
            case INSIDER_TRADING -> insiderTradingSource(language);
            case LARGE_CLASS -> largeClassSource(language);
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> alternativeClassesSource(language);
            case DATA_CLASS -> dataClassSource(language);
            case REFUSED_BEQUEST -> refusedBequestSource(language);
            case COMMENTS -> comment(language, "TODO remove temporary workaround");
        };
    }

    private static String comment(String language, String text) {
        return switch (language) {
            case "bash", "python", "ruby" -> "# " + text + "\n";
            case "html", "vue" -> "<!-- " + text + " -->\n";
            case "css" -> "/* " + text + " */\n";
            case "sql", "sql:postgresql", "sql:mysql", "sql:sqlite", "sql:tsql", "sql:plsql" -> "-- " + text + "\n";
            default -> "// " + text + "\n";
        };
    }

    private static String mysteriousNameSource(String language) {
        return switch (language) {
            case "bash" -> "x() { :; }\n";
            case "c" -> "int x;\n";
            case "cpp" -> "class X { int q; };\n";
            case "csharp" -> "class X { int q; }\n";
            case "go" -> "package sample\nvar x int\nfunc q() {}\n";
            case "python" -> "def x():\n    pass\n";
            case "rust" -> "fn x() {}\n";
            case "javascript" -> "function x() {}\n";
            case "typescript" -> "function x(): void {}\n";
            case "tsx" -> "function x() { return <section />; }\n";
            case "vue" -> "<!-- #x -->\n<script setup lang=\"ts\">\nfunction x(): void {}\n</script>\n";
            case "ruby" -> "def x\nend\n";
            case "html" -> "<div id=\"#x\"></div>\n";
            case "css" -> ".x { color: red; }\n";
            case "sql" -> "-- x tmp obj\nSELECT 1 AS x;\n";
            default -> comment(language, "x tmp obj");
        };
    }

    private static String longFunctionSource(String language) {
        return switch (language) {
            case "bash" -> "x() {\n" + repeatLine("  echo value\n", 55) + "}\n";
            case "c" -> "int x(void) {\n  int value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  return value;\n}\n";
            case "cpp" -> "int x() {\n  int value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  return value;\n}\n";
            case "csharp" -> "class X {\n  int x() {\n    int value = 0;\n" + repeatLine("    value = value + 1;\n", 55) + "    return value;\n  }\n}\n";
            case "go" -> "package sample\nfunc x() int {\n  value := 0\n" + repeatLine("  value = value + 1\n", 55) + "  return value\n}\n";
            case "python" -> "def x():\n    value = 0\n" + repeatLine("    value = value + 1\n", 55) + "    return value\n";
            case "rust" -> "fn x() -> i32 {\n  let mut value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  value\n}\n";
            case "javascript" -> "function x() {\n  let value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  return value;\n}\n";
            case "typescript" -> "function x(): number {\n  let value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  return value;\n}\n";
            case "tsx" -> "function x() {\n  let value = 0;\n" + repeatLine("  value = value + 1;\n", 55) + "  return <section>{value}</section>;\n}\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction x(): number {\n  let value = 0\n" + repeatLine("  value = value + 1\n", 55) + "  return value\n}\n</script>\n";
            case "ruby" -> "def x\n  value = 0\n" + repeatLine("  value = value + 1\n", 55) + "  value\nend\n";
            case "html" -> "<main>\n" + repeatLine("  <section>value</section>\n", 55) + "</main>\n";
            case "css" -> ".x {\n" + repeatLine("  color: red;\n", 55) + "}\n";
            case "sql" -> repeatLine("SELECT 1 AS large_query;\n", 55);
            default -> comment(language, "long function");
        };
    }

    private static String longParameterListSource(String language) {
        return switch (language) {
            case "bash" -> "configure() { echo \"$1 $2 $3 $4 $5\"; }\n";
            case "c" -> "void configure(char *host, int port, int secure, char *region, char *tenant) {}\n";
            case "cpp" -> "void configure(char *host, int port, bool secure, char *region, char *tenant) {}\n";
            case "csharp" -> "class X { void Configure(string host, int port, bool secure, string region, string tenant) {} }\n";
            case "go" -> "package sample\nfunc configure(host string, port int, secure bool, region string, tenant string) {}\n";
            case "python" -> "def configure(host, port, secure, region, tenant):\n    pass\n";
            case "rust" -> "fn configure(host: String, port: i32, secure: bool, region: String, tenant: String) {}\n";
            case "javascript" -> "function configure(host, port, secure, region, tenant) {}\n";
            case "typescript" -> "function configure(host: string, port: number, secure: boolean, region: string, tenant: string): void {}\n";
            case "tsx" -> "function configure(host: string, port: number, secure: boolean, region: string, tenant: string) { return <section />; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction configure(host: string, port: number, secure: boolean, region: string, tenant: string): void {}\n</script>\n";
            case "ruby" -> "def configure(host, port, secure, region, tenant)\nend\n";
            case "html" -> "<div data-a=\"1\" data-b=\"2\" data-c=\"3\" data-d=\"4\" data-e=\"5\"></div>\n";
            case "css" -> ".a, .b, .c, .d, .e { color: red; }\n";
            case "sql" -> "SELECT a, b, c, d, e FROM sample;\n";
            default -> comment(language, "a,b,c,d,e");
        };
    }

    private static String globalDataSource(String language) {
        return switch (language) {
            case "bash" -> "global_counter=1\n";
            case "c" -> "int global_counter;\n";
            case "cpp" -> "int globalCounter;\n";
            case "csharp" -> "class Globals { public static int counter; }\n";
            case "go" -> "package sample\nvar globalCounter int\n";
            case "python" -> "global_counter = 1\n";
            case "rust" -> "static mut GLOBAL_COUNTER: i32 = 0;\n";
            case "javascript" -> "let globalCounter = 1;\n";
            case "typescript" -> "let globalCounter: number = 1;\n";
            case "tsx" -> "let globalCounter: number = 1;\nfunction View() { return <section>{globalCounter}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nlet globalCounter: number = 1\n</script>\n";
            case "ruby" -> "$global_counter = 1\n";
            case "css" -> ":root { --global-counter: 1; }\n";
            case "sql" -> "-- global state\nSELECT 1 AS global_counter;\n";
            default -> comment(language, "global state");
        };
    }

    private static String mutableDataSource(String language) {
        return switch (language) {
            case "bash" -> "counter=0\nset_cache() { counter=1; }\n";
            case "c" -> "int cache; void set_cache(void) { cache = 1; }\n";
            case "cpp" -> "class X { int cache; void setCache() { cache = 1; } void resetCache() { cache = 2; } };\n";
            case "csharp" -> "class X { int cache; void SetCache() { cache = 1; } void ResetCache() { cache = 2; } }\n";
            case "go" -> "package sample\nvar cache int\nfunc setCache() { cache = 1 }\n";
            case "python" -> "cache = 0\ndef set_cache():\n    global cache\n    cache = 1\n";
            case "rust" -> "static mut CACHE: i32 = 0;\nfn set_cache() { let mut cache = 0; cache = 1; }\n";
            case "javascript" -> "let cache = 0;\nfunction setCache() { cache = 1; }\n";
            case "typescript" -> "let cache: number = 0;\nfunction setCache(): void { cache = 1; }\n";
            case "tsx" -> "let cache: number = 0;\nfunction setCache() { cache = 1; return <section>{cache}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nlet cache: number = 0\nfunction setCache(): void { cache = 1 }\n</script>\n";
            case "ruby" -> "@cache = 0\ndef set_cache\n  @cache = 1\nend\n";
            case "css" -> ".counter { --cache: 0; --cache: 1; }\n";
            case "sql" -> "UPDATE cache SET value = 1;\n";
            default -> comment(language, "mutable counter cache = 1");
        };
    }

    private static String shotgunSurgerySource(String language) {
        return switch (language) {
            case "cpp" -> "class A { void refresh() {} }; class B { void refresh() {} }; class C { void refresh() {} };\n";
            case "csharp" -> "class A { void Refresh() {} } class B { void Refresh() {} } class C { void Refresh() {} }\n";
            case "python" -> "class A:\n    def refresh(self):\n        pass\nclass B:\n    def refresh(self):\n        pass\nclass C:\n    def refresh(self):\n        pass\n";
            case "javascript" -> "class A { refresh() {} }\nclass B { refresh() {} }\nclass C { refresh() {} }\n";
            case "typescript" -> "class A { refresh(): void {} }\nclass B { refresh(): void {} }\nclass C { refresh(): void {} }\n";
            case "tsx" -> "class A { refresh(): void {} }\nclass B { refresh(): void {} }\nclass C { refresh(): void {} }\n";
            case "ruby" -> "class A\n  def refresh\n  end\nend\nclass B\n  def refresh\n  end\nend\nclass C\n  def refresh\n  end\nend\n";
            default -> comment(language, "refresh refresh refresh");
        };
    }

    private static String featureEnvySource(String language) {
        return switch (language) {
            case "c" -> "int compute(void) { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score; }\n";
            case "cpp" -> "int compute(Order order) { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score; }\n";
            case "csharp" -> "class X { int Compute(Order order) { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score; } }\n";
            case "go" -> "package sample\nfunc compute(order Order) int { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score }\n";
            case "python" -> "def compute(order):\n    return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score\n";
            case "rust" -> "fn compute(order: Order) -> i32 { order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score }\n";
            case "javascript" -> "function compute(order) { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score; }\n";
            case "typescript" -> "function compute(order: Order): number { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score; }\n";
            case "tsx" -> "function compute(order: Order) { return <section>{order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction compute(order: Order): number { return order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score }\n</script>\n";
            case "ruby" -> "def compute(order)\n  order.customer.address.zip + order.customer.address.code + order.customer.level + order.customer.score\nend\n";
            default -> comment(language, "foreign.customer.address.city.name");
        };
    }

    private static String dataClumpsSource(String language) {
        return switch (language) {
            case "bash" -> comment(language, "start end unit start end unit");
            case "c" -> "void create_range(int start, int end, char *unit) {}\nvoid update_range(int start, int end, char *unit) {}\n";
            case "cpp" -> "void createRange(int start, int end, char *unit) {}\nvoid updateRange(int start, int end, char *unit) {}\n";
            case "csharp" -> "class X { void CreateRange(int start, int end, string unit) {} void UpdateRange(int start, int end, string unit) {} }\n";
            case "go" -> "package sample\nfunc createRange(start int, end int, unit string) {}\nfunc updateRange(start int, end int, unit string) {}\n";
            case "python" -> "def create_range(start, end, unit):\n    pass\ndef update_range(start, end, unit):\n    pass\n";
            case "rust" -> "fn create_range(start: i32, end: i32, unit: String) {}\nfn update_range(start: i32, end: i32, unit: String) {}\n";
            case "javascript" -> "function createRange(start, end, unit) {}\nfunction updateRange(start, end, unit) {}\n";
            case "typescript" -> "function createRange(start: number, end: number, unit: string): void {}\nfunction updateRange(start: number, end: number, unit: string): void {}\n";
            case "tsx" -> "function createRange(start: number, end: number, unit: string) { return <section />; }\nfunction updateRange(start: number, end: number, unit: string) { return <section />; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction createRange(start: number, end: number, unit: string): void {}\nfunction updateRange(start: number, end: number, unit: string): void {}\n</script>\n";
            case "ruby" -> "def create_range(start, finish, unit)\nend\ndef update_range(start, finish, unit)\nend\n";
            default -> comment(language, "start end unit start end unit");
        };
    }

    private static String primitiveObsessionSource(String language) {
        return switch (language) {
            case "c" -> "int configure(int status, char *typeCode, int region, int active) { return 1 + 2 + 3 + 4; }\n";
            case "cpp" -> "class PrimitiveCase { int status; char *typeCode; int region; bool active; int configure() { return 1 + 2 + 3 + 4; } };\n";
            case "csharp" -> "class PrimitiveCase { int status; string typeCode; int region; bool active; int Configure() { return 1 + 2 + 3 + 4; } }\n";
            case "go" -> "package sample\nfunc configure(status int, typeCode string, region int, active bool) int { return 1 + 2 + 3 + 4 }\n";
            case "python" -> "def configure(status, type_code, region, active):\n    return 1 + 2 + 3 + 4\n";
            case "rust" -> "fn configure(status: i32, type_code: String, region: i32, active: bool) -> i32 { 1 + 2 + 3 + 4 }\n";
            case "javascript" -> "function configure(status, typeCode, region, active) { return 1 + 2 + 3 + 4; }\n";
            case "typescript" -> "function configure(status: number, typeCode: string, region: number, active: boolean): number { return 1 + 2 + 3 + 4; }\n";
            case "tsx" -> "function configure(status: number, typeCode: string, region: number, active: boolean) { return <section>{1 + 2 + 3 + 4}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction configure(status: number, typeCode: string, region: number, active: boolean): number { return 1 + 2 + 3 + 4 }\n</script>\n";
            case "ruby" -> "def configure(status, type_code, region, active)\n  1 + 2 + 3 + 4\nend\n";
            default -> comment(language, "1 2 3 4 true false");
        };
    }

    private static String repeatedSwitchesSource(String language) {
        return switch (language) {
            case "c" -> "int a(int status) { switch (status) { case 1: return 1; default: return 0; } }\nint b(int status) { switch (status) { case 1: return 2; default: return 0; } }\n";
            case "cpp" -> "int a(int status) { switch (status) { case 1: return 1; default: return 0; } }\nint b(int status) { switch (status) { case 1: return 2; default: return 0; } }\n";
            case "csharp" -> "class X { int A(int status) { switch (status) { case 1: return 1; default: return 0; } } int B(int status) { switch (status) { case 1: return 2; default: return 0; } } }\n";
            case "go" -> "package sample\nfunc a(status int) int { switch status { case 1: return 1; default: return 0 } }\nfunc b(status int) int { switch status { case 1: return 2; default: return 0 } }\n";
            case "python" -> "def a(status):\n    if status == 1:\n        return 1\n    elif status == 2:\n        return 2\n    return 0\n"
                    + "def b(status):\n    if status == 1:\n        return 3\n    elif status == 2:\n        return 4\n    return 0\n";
            case "rust" -> "fn a(status: i32) -> i32 { match status { 1 => 1, _ => 0 } }\nfn b(status: i32) -> i32 { match status { 1 => 2, _ => 0 } }\n";
            case "javascript" -> "function a(status) { switch (status) { case 1: return 1; default: return 0; } }\nfunction b(status) { switch (status) { case 1: return 2; default: return 0; } }\n";
            case "typescript" -> "function a(status: number): number { switch (status) { case 1: return 1; default: return 0; } }\nfunction b(status: number): number { switch (status) { case 1: return 2; default: return 0; } }\n";
            case "tsx" -> "function a(status: number) { switch (status) { case 1: return <section />; default: return <div />; } }\nfunction b(status: number) { switch (status) { case 1: return <article />; default: return <div />; } }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction a(status: number): number { switch (status) { case 1: return 1; default: return 0 } }\nfunction b(status: number): number { switch (status) { case 1: return 2; default: return 0 } }\n</script>\n";
            case "ruby" -> "def a(status)\n  case status\n  when 1\n    1\n  else\n    0\n  end\nend\ndef b(status)\n  case status\n  when 1\n    2\n  else\n    0\n  end\nend\n";
            default -> comment(language, "switch switch case case case case");
        };
    }

    private static String loopsSource(String language) {
        return switch (language) {
            case "bash" -> "x() { for item in a b c; do echo \"$item\"; done; }\n";
            case "c" -> "int x(void) { int total = 0; for (int i = 0; i < 3; i++) { total += i; } return total; }\n";
            case "cpp" -> "int x() { int total = 0; for (int i = 0; i < 3; i++) { total += i; } return total; }\n";
            case "csharp" -> "class X { int Sum(int[] values) { int total = 0; foreach (int value in values) { total += value; } return total; } }\n";
            case "go" -> "package sample\nfunc x() int { total := 0; for i := 0; i < 3; i++ { total += i }; return total }\n";
            case "python" -> "def x(values):\n    total = 0\n    for value in values:\n        total += value\n    return total\n";
            case "rust" -> "fn x(values: Vec<i32>) -> i32 { let mut total = 0; for value in values { total += value; } total }\n";
            case "javascript" -> "function x(values) { let total = 0; for (const value of values) { total += value; } return total; }\n";
            case "typescript" -> "function x(values: number[]): number { let total = 0; for (const value of values) { total += value; } return total; }\n";
            case "tsx" -> "function x(values: number[]) { let total = 0; for (const value of values) { total += value; } return <section>{total}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction x(values: number[]): number { let total = 0; for (const value of values) { total += value } return total }\n</script>\n";
            case "ruby" -> "def x(values)\n  total = 0\n  for value in values\n    total += value\n  end\n  total\nend\n";
            default -> comment(language, "for each item loop");
        };
    }

    private static String lazyElementSource(String language) {
        return switch (language) {
            case "bash" -> "placeholder() { :; }\n";
            case "c" -> "void placeholder(void) {}\n";
            case "cpp" -> "class Placeholder {};\n";
            case "csharp" -> "class Placeholder {}\n";
            case "go" -> "package sample\nfunc placeholder() {}\n";
            case "python" -> "class Placeholder:\n    pass\n";
            case "rust" -> "struct Placeholder;\n";
            case "javascript" -> "class Placeholder {}\n";
            case "typescript" -> "class Placeholder {}\n";
            case "tsx" -> "function Placeholder() { return <section />; }\n";
            case "vue" -> "<template><div /></template>\n";
            case "ruby" -> "class Placeholder\nend\n";
            default -> comment(language, "empty placeholder");
        };
    }

    private static String speculativeGeneralitySource(String language) {
        return switch (language) {
            case "cpp" -> "class AbstractFutureThing { virtual void futureHook() = 0; };\n";
            case "csharp" -> "abstract class AbstractFutureThing { abstract void FutureHook(); }\n";
            case "go" -> "package sample\ntype FutureExtension interface { ExtensionPoint() }\n";
            case "python" -> "class AbstractFutureThing:\n    def future_hook(self):\n        pass\n";
            case "rust" -> "trait FutureExtension { fn extension_point(&self); }\n";
            case "javascript" -> "class AbstractFutureThing { futureHook() {} }\n";
            case "typescript" -> "interface FutureExtension { extensionPoint(): void }\n";
            case "tsx" -> "interface FutureExtension { extensionPoint(): void }\nfunction View() { return <section />; }\n";
            case "vue" -> "<script setup lang=\"ts\">\ninterface FutureExtension { extensionPoint(): void }\n</script>\n";
            case "ruby" -> "class AbstractFutureThing\n  def future_hook\n  end\nend\n";
            default -> comment(language, "abstract base generic future extension point");
        };
    }

    private static String temporaryFieldSource(String language) {
        return switch (language) {
            case "cpp" -> "class X { int tempScratch; void calculate() { tempScratch = 1; } void unrelated() {} };\n";
            case "csharp" -> "class X { int tempScratch; void Calculate() { tempScratch = 1; } void Unrelated() {} }\n";
            case "python" -> "class X:\n    tempScratch = 0\n    def calculate(self):\n        self.tempScratch = 1\n    def unrelated(self):\n        pass\n";
            case "javascript" -> "class X { constructor() { this.tempScratch = 0; } calculate() { this.tempScratch = 1; } unrelated() {} }\n";
            case "typescript" -> "class X { tempScratch: number = 0; calculate(): void { this.tempScratch = 1; } unrelated(): void {} }\n";
            case "tsx" -> "class X { tempScratch: number = 0; calculate(): void { this.tempScratch = 1; } unrelated(): void {} }\n";
            case "ruby" -> "class X\n  def calculate\n    @tempScratch = 1\n  end\n  def unrelated\n  end\nend\n";
            default -> comment(language, "temp tmp scratch temporary");
        };
    }

    private static String messageChainsSource(String language) {
        return switch (language) {
            case "c" -> "int read(void) { return order.customer.address.city.name; }\n";
            case "cpp" -> "int read(Order order) { return order.customer.address.city.name; }\n";
            case "csharp" -> "class X { int Read(Order order) { return order.customer.address.city.name.Length; } }\n";
            case "go" -> "package sample\nfunc read(order Order) int { return order.customer.address.city.name }\n";
            case "python" -> "def read(order):\n    return order.customer.address.city.name\n";
            case "rust" -> "fn read(order: Order) -> i32 { order.customer.address.city.name }\n";
            case "javascript" -> "function read(order) { return order.customer.address.city.name; }\n";
            case "typescript" -> "function read(order: Order): number { return order.customer.address.city.name; }\n";
            case "tsx" -> "function read(order: Order) { return <section>{order.customer.address.city.name}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction read(order: Order): number { return order.customer.address.city.name }\n</script>\n";
            case "ruby" -> "def read(order)\n  order.customer.address.city.name\nend\n";
            case "html" -> "<a><b><c><d>chain</d></c></b></a>\n";
            default -> comment(language, "order.customer.address.city.name");
        };
    }

    private static String middleManSource(String language) {
        return switch (language) {
            case "cpp" -> "class X { Delegate delegate; int a() { return delegate.a(); } int b() { return delegate.b(); } int c() { return delegate.c(); } };\n";
            case "csharp" -> "class X { Delegate delegateField; int A() { return delegateField.A(); } int B() { return delegateField.B(); } int C() { return delegateField.C(); } }\n";
            case "python" -> "class X:\n    def __init__(self, delegate):\n        self.delegate = delegate\n    def a(self):\n        return self.delegate.a()\n    def b(self):\n        return self.delegate.b()\n    def c(self):\n        return self.delegate.c()\n";
            case "javascript" -> "class X { constructor(delegate) { this.delegate = delegate; } a() { return this.delegate.a(); } b() { return this.delegate.b(); } c() { return this.delegate.c(); } }\n";
            case "typescript" -> "class X { delegate: Delegate; a() { return this.delegate.a(); } b() { return this.delegate.b(); } c() { return this.delegate.c(); } }\n";
            case "tsx" -> "class X { delegate: Delegate; a() { return this.delegate.a(); } b() { return this.delegate.b(); } c() { return this.delegate.c(); } }\n";
            case "ruby" -> "class X\n  def a\n    @delegate.a\n  end\n  def b\n    @delegate.b\n  end\n  def c\n    @delegate.c\n  end\nend\n";
            default -> comment(language, "delegate proxy forward wrapper");
        };
    }

    private static String insiderTradingSource(String language) {
        return switch (language) {
            case "c" -> "int inspect(void) { return session.account.internalFlags.admin + session.account.privateProfile.highRisk; }\n";
            case "cpp" -> "int inspect(Session session) { return session.account.internalFlags.admin + session.account.privateProfile.highRisk; }\n";
            case "csharp" -> "class X { int Inspect(Session session) { return session.account.internalFlags.admin + session.account.privateProfile.highRisk; } }\n";
            case "go" -> "package sample\nfunc inspect(session Session) int { return session.account.internalFlags.admin + session.account.privateProfile.highRisk }\n";
            case "python" -> "def inspect(session):\n    return session.account.internal_flags.admin + session.account.private_profile.high_risk\n";
            case "rust" -> "fn inspect(session: Session) -> i32 { session.account.internal_flags.admin + session.account.private_profile.high_risk }\n";
            case "javascript" -> "function inspect(session) { return session.account.internalFlags.admin + session.account.privateProfile.highRisk; }\n";
            case "typescript" -> "function inspect(session: Session): number { return session.account.internalFlags.admin + session.account.privateProfile.highRisk; }\n";
            case "tsx" -> "function inspect(session: Session) { return <section>{session.account.internalFlags.admin + session.account.privateProfile.highRisk}</section>; }\n";
            case "vue" -> "<script setup lang=\"ts\">\nfunction inspect(session: Session): number { return session.account.internalFlags.admin + session.account.privateProfile.highRisk }\n</script>\n";
            case "ruby" -> "def inspect(session)\n  session.account.internal_flags.admin + session.account.private_profile.high_risk\nend\n";
            default -> comment(language, "internal private intimate foreign details");
        };
    }

    private static String largeClassSource(String language) {
        return switch (language) {
            case "cpp" -> "class LargeClass { " + repeatLine("int field;\n", 12) + "};\n";
            case "csharp" -> "class LargeClass {\n" + repeatLine("  int field;\n", 12) + "}\n";
            case "python" -> "class LargeClass:\n" + repeatLine("    field = 0\n", 12);
            case "javascript" -> "class LargeClass { constructor() {\n" + repeatLine("  this.field = 0;\n", 12) + "} }\n";
            case "typescript" -> "class LargeClass {\n" + repeatLine("  field: number = 0;\n", 12) + "}\n";
            case "tsx" -> "class LargeClass {\n" + repeatLine("  field: number = 0;\n", 12) + "}\n";
            case "ruby" -> "class LargeClass\n" + repeatLine("  attr_accessor :field\n", 12) + "end\n";
            default -> comment(language, "field ".repeat(12));
        };
    }

    private static String alternativeClassesSource(String language) {
        return switch (language) {
            case "cpp" -> "class AlternativeOne { int total() { return 1; } int count(int value) { return value; } }; class AlternativeTwo { int sum() { return 1; } int size(int value) { return value; } };\n";
            case "csharp" -> "class AlternativeOne { int Total() { return 1; } int Count(int value) { return value; } } class AlternativeTwo { int Sum() { return 1; } int Size(int value) { return value; } }\n";
            case "python" -> "class AlternativeOne:\n    def total(self):\n        return 1\n    def count(self, value):\n        return value\nclass AlternativeTwo:\n    def sum(self):\n        return 1\n    def size(self, value):\n        return value\n";
            case "javascript" -> "class AlternativeOne { total() { return 1; } count(value) { return value; } }\nclass AlternativeTwo { sum() { return 1; } size(value) { return value; } }\n";
            case "typescript" -> "class AlternativeOne { total(): number { return 1; } count(value: number): number { return value; } }\nclass AlternativeTwo { sum(): number { return 1; } size(value: number): number { return value; } }\n";
            case "tsx" -> "class AlternativeOne { total(): number { return 1; } count(value: number): number { return value; } }\nclass AlternativeTwo { sum(): number { return 1; } size(value: number): number { return value; } }\n";
            case "ruby" -> "class AlternativeOne\n  def total\n    1\n  end\n  def count(value)\n    value\n  end\nend\nclass AlternativeTwo\n  def sum\n    1\n  end\n  def size(value)\n    value\n  end\nend\n";
            default -> comment(language, "AlternativeOne alternative-two");
        };
    }

    private static String dataClassSource(String language) {
        return switch (language) {
            case "cpp" -> "class DataOnly { public: int amount; char *code; };\n";
            case "csharp" -> "class DataOnly { public int amount; public string code; }\n";
            case "python" -> "class DataOnly:\n    amount = 0\n    code = ''\n";
            case "javascript" -> "class DataOnly { constructor() { this.amount = 0; this.code = ''; } }\n";
            case "typescript" -> "class DataOnly { public amount: number; public code: string; }\n";
            case "tsx" -> "class DataOnly { public amount: number; public code: string; }\n";
            case "ruby" -> "class DataOnly\n  attr_accessor :amount\n  attr_accessor :code\nend\n";
            default -> comment(language, "data-field field data-other field");
        };
    }

    private static String refusedBequestSource(String language) {
        return switch (language) {
            case "cpp" -> "class BaseThing { virtual void doIt() {} }; class RefusingThing : public BaseThing { void doIt() { throw UnsupportedOperationException(); } };\n";
            case "csharp" -> "class BaseThing { public virtual void DoIt() {} } class RefusingThing : BaseThing { public override void DoIt() { throw new NotImplementedException(); } }\n";
            case "python" -> "class BaseThing:\n    def do_it(self):\n        pass\nclass RefusingThing(BaseThing):\n    def do_it(self):\n        raise NotImplementedError('not supported')\n";
            case "javascript" -> "class BaseThing { doIt() {} }\nclass RefusingThing extends BaseThing { doIt() { throw new Error('not supported'); } }\n";
            case "typescript" -> "class BaseThing { doIt(): void {} }\nclass RefusingThing extends BaseThing { override doIt(): void { throw new Error('not supported'); } }\n";
            case "tsx" -> "class BaseThing { doIt(): void {} }\nclass RefusingThing extends BaseThing { override doIt(): void { throw new Error('not supported'); } }\n";
            case "ruby" -> "class BaseThing\n  def do_it\n  end\nend\nclass RefusingThing < BaseThing\n  def do_it\n    raise 'not supported'\n  end\nend\n";
            default -> comment(language, "unsupported not supported refuse");
        };
    }

    private static String repeatLine(String line, int count) {
        return line.repeat(count);
    }

    private static String duplicatedSource(String language) {
        return switch (language) {
            case "bash" -> """
                    x() {
                      echo repeated
                      echo repeated
                    }
                    """;
            case "c" -> """
                    int x(void) {
                      int a = 1;
                      a = a + 1;
                      a = a + 1;
                      return a;
                    }
                    """;
            case "cpp" -> """
                    int x() {
                      int a = 1;
                      a = a + 1;
                      a = a + 1;
                      return a;
                    }
                    """;
            case "csharp" -> """
                    class X {
                      int x() {
                        int a = 1;
                        a = a + 1;
                        a = a + 1;
                        return a;
                      }
                    }
                    """;
            case "go" -> """
                    package sample
                    func x() int {
                      a := 1
                      a = a + 1
                      a = a + 1
                      return a
                    }
                    """;
            case "python" -> """
                    def x():
                        a = 1
                        a = a + 1
                        a = a + 1
                        return a
                    """;
            case "rust" -> """
                    fn x() -> i32 {
                      let mut a = 1;
                      a = a + 1;
                      a = a + 1;
                      a
                    }
                    """;
            case "html" -> """
                    <span>repeated</span>
                    <span>repeated</span>
                    """;
            case "css" -> """
                    .x {
                      color: red;
                      color: red;
                    }
                    """;
            case "javascript" -> """
                    function x() {
                      let a = 1;
                      a = a + 1;
                      a = a + 1;
                      return a;
                    }
                    """;
            case "typescript" -> """
                    function x(): number {
                      let a = 1;
                      a = a + 1;
                      a = a + 1;
                      return a;
                    }
                    """;
            case "tsx" -> """
                    function x() {
                      let a = 1;
                      a = a + 1;
                      a = a + 1;
                      return <section>{a}</section>;
                    }
                    """;
            case "vue" -> """
                    <template>
                      <span>repeated</span>
                      <span>repeated</span>
                    </template>
                    """;
            case "ruby" -> """
                    def x
                      a = 1
                      a = a + 1
                      a = a + 1
                      a
                    end
                    """;
            case "sql" -> """
                    SELECT 1;
                    SELECT 1;
                    """;
            default -> "repeat\nrepeat\n";
        };
    }

    private static String divergentSource(String language) {
        return switch (language) {
            case "bash" -> """
                    save_record() { persist_save; }
                    render_view() { render_template; }
                    validate_input() { validate_rule; }
                    calculate_total() { calculate_price; }
                    """;
            case "c" -> """
                    void save_record(void) { repository_save(); }
                    void render_view(void) { render_template(); }
                    void validate_input(void) { validator_check(); }
                    void calculate_total(void) { price_calculate(); }
                    """;
            case "cpp" -> """
                    class X {
                    public:
                      void saveRecord() { repositorySave(); }
                      void renderView() { renderTemplate(); }
                      void validateInput() { validatorCheck(); }
                      void calculateTotal() { priceCalculate(); }
                    };
                    """;
            case "csharp" -> """
                    class X {
                      void SaveRecord() { RepositorySave(); }
                      void RenderView() { RenderTemplate(); }
                      void ValidateInput() { ValidatorCheck(); }
                      void CalculateTotal() { PriceCalculate(); }
                    }
                    """;
            case "go" -> """
                    package sample
                    func saveRecord() { repositorySave() }
                    func renderView() { renderTemplate() }
                    func validateInput() { validatorCheck() }
                    func calculateTotal() { priceCalculate() }
                    """;
            case "python" -> """
                    def save_record():
                        repository_save()
                    def render_view():
                        render_template()
                    def validate_input():
                        validator_check()
                    def calculate_total():
                        price_calculate()
                    """;
            case "rust" -> """
                    fn save_record() { repository_save(); }
                    fn render_view() { render_template(); }
                    fn validate_input() { validator_check(); }
                    fn calculate_total() { price_calculate(); }
                    """;
            case "html" -> """
                    <button data-action="save-record">save</button>
                    <button data-action="render-view">render</button>
                    <button data-action="validate-input">validate</button>
                    <button data-action="calculate-total">calculate</button>
                    """;
            case "css" -> """
                    .save-record { color: blue; }
                    .render-view { color: green; }
                    .validate-input { color: purple; }
                    .calculate-total { color: black; }
                    """;
            case "javascript" -> """
                    function saveRecord() { repositorySave(); }
                    function renderView() { renderTemplate(); }
                    function validateInput() { validatorCheck(); }
                    function calculateTotal() { priceCalculate(); }
                    """;
            case "typescript" -> """
                    function saveRecord(): void { repositorySave(); }
                    function renderView(): void { renderTemplate(); }
                    function validateInput(): void { validatorCheck(); }
                    function calculateTotal(): void { priceCalculate(); }
                    """;
            case "tsx" -> """
                    function saveRecord(): void { repositorySave(); }
                    function renderView(): void { renderTemplate(); }
                    function validateInput(): void { validatorCheck(); }
                    function calculateTotal() { return <section>{priceCalculate()}</section>; }
                    """;
            case "vue" -> """
                    <script setup lang="ts">
                    function saveRecord(): void { repositorySave() }
                    function renderView(): void { renderTemplate() }
                    function validateInput(): void { validatorCheck() }
                    function calculateTotal(): number { return priceCalculate() }
                    </script>
                    """;
            case "ruby" -> """
                    def save_record
                      repository_save
                    end
                    def render_view
                      render_template
                    end
                    def validate_input
                      validator_check
                    end
                    def calculate_total
                      price_calculate
                    end
                    """;
            case "sql" -> """
                    SELECT 1 AS save_record;
                    SELECT 1 AS render_view;
                    SELECT 1 AS validate_input;
                    SELECT 1 AS calculate_total;
                    """;
            default -> "save render validate calculate\n";
        };
    }

    private static String javaFocusedSource(BadSmell smell) {
        return switch (smell) {
            case MYSTERIOUS_NAME -> """
                    class X {
                      int q;
                    }
                    """;
            case DUPLICATED_CODE -> """
                    class DuplicateCase {
                      int first(int value) {
                        int result = value + 1;
                        result = result * 2;
                        return result;
                      }
                      int second(int value) {
                        int result = value + 1;
                        result = result * 2;
                        return result;
                      }
                    }
                    """;
            case LONG_FUNCTION -> """
                    class LongFunctionCase {
                      void enormous() {
                    """ + longFunctionLines() + """
                      }
                    }
                    """;
            case LONG_PARAMETER_LIST -> """
                    class LongParameterCase {
                      void configure(String host, int port, boolean secure, String region, String tenant) {}
                    }
                    """;
            case GLOBAL_DATA -> """
                    class Globals {
                      public static int counter;
                    }
                    """;
            case MUTABLE_DATA -> """
                    class MutableHolder {
                      int tempCache;
                      void first() { tempCache = 1; }
                      void second() { tempCache = 2; }
                    }
                    """;
            case DIVERGENT_CHANGE -> """
                    class DivergentCase {
                      Repository repository;
                      Renderer renderer;
                      Validator validator;
                      Calculator calculator;
                      void saveRecord(Order order) { repository.save(order); }
                      String renderView(Order order) { return renderer.render(order); }
                      void validateInput(Order order) { validator.check(order); }
                      int calculateTotal(Order order) { return calculator.calculate(order); }
                    }
                    class Repository { void save(Order order) {} }
                    class Renderer { String render(Order order) { return ""; } }
                    class Validator { void check(Order order) {} }
                    class Calculator { int calculate(Order order) { return 1; } }
                    class Order {}
                    """;
            case SHOTGUN_SURGERY -> """
                    class ShotA { void refresh() {} }
                    class ShotB { void refresh() {} }
                    class ShotC { void refresh() {} }
                    """;
            case FEATURE_ENVY -> """
                    class FeatureEnvyCase {
                      int compute(Order order) {
                        return order.customer.address.zip
                            + order.customer.address.code
                            + order.customer.level
                            + order.customer.score;
                      }
                    }
                    class Order { Customer customer; }
                    class Customer { Address address; int level; int score; }
                    class Address { int zip; int code; }
                    """;
            case DATA_CLUMPS -> """
                    class DataClumpCase {
                      void createRange(int start, int end, String unit) {}
                      void updateRange(int start, int end, String unit) {}
                    }
                    """;
            case PRIMITIVE_OBSESSION -> """
                    class PrimitiveCase {
                      int status;
                      String typeCode;
                      int region;
                      boolean active;
                    }
                    """;
            case REPEATED_SWITCHES -> """
                    class SwitchCase {
                      int a(String status) {
                        switch (status) {
                          case "new": return 1;
                          default: return 0;
                        }
                      }
                      int b(String status) {
                        switch (status) {
                          case "new": return 2;
                          default: return 0;
                        }
                      }
                    }
                    """;
            case LOOPS -> """
                    class LoopCase {
                      int sum(int[] values) {
                        int total = 0;
                        for (int value : values) {
                          total += value;
                        }
                        return total;
                      }
                    }
                    """;
            case LAZY_ELEMENT -> """
                    class Placeholder {
                    }
                    """;
            case SPECULATIVE_GENERALITY -> """
                    abstract class AbstractFutureThing {
                      abstract void futureHook();
                    }
                    """;
            case TEMPORARY_FIELD -> """
                    class TemporaryOnly {
                      int tempScratch;
                      void calculate() { tempScratch = 1; }
                      void unrelated() {}
                    }
                    """;
            case MESSAGE_CHAINS -> """
                    class ChainCase {
                      int read(Order order) {
                        return order.customer.address.city.name.length();
                      }
                    }
                    class Order { Customer customer; }
                    class Customer { Address address; }
                    class Address { City city; }
                    class City { String name; }
                    """;
            case MIDDLE_MAN -> """
                    class MiddleManCase {
                      Delegate delegate;
                      int a() { return delegate.a(); }
                      int b() { return delegate.b(); }
                      int c() { return delegate.c(); }
                    }
                    class Delegate {
                      int a() { return 1; }
                      int b() { return 2; }
                      int c() { return 3; }
                    }
                    """;
            case INSIDER_TRADING -> """
                    class InsiderTradingCase {
                      int inspect(Order order, Profile profile, Account account) {
                        return order.customer.address.zip
                            + order.customer.address.code
                            + profile.address.zip
                            + profile.address.code
                            + account.balance.amount
                            + account.balance.currency;
                      }
                    }
                    class Order { Customer customer; }
                    class Customer { Address address; }
                    class Address { int zip; int code; }
                    class Profile { Address address; }
                    class Account { Balance balance; }
                    class Balance { int amount; int currency; }
                    """;
            case LARGE_CLASS -> """
                    class LargeClassCase {
                      int f01; int f02; int f03; int f04; int f05; int f06; int f07; int f08;
                      int f09; int f10; int f11; int f12; int f13; int f14; int f15; int f16;
                    }
                    """;
            case ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES -> """
                    class AlternativeOne {
                      int amount;
                      int total() { return amount; }
                      int count(int value) { return value; }
                    }
                    class AlternativeTwo {
                      int amount;
                      int sum() { return amount; }
                      int size(int value) { return value; }
                    }
                    """;
            case DATA_CLASS -> """
                    class DataOnly {
                      private int amount;
                      private String code;
                      int getAmount() { return amount; }
                      void setAmount(int amount) { this.amount = amount; }
                      String getCode() { return code; }
                      void setCode(String code) { this.code = code; }
                    }
                    """;
            case REFUSED_BEQUEST -> """
                    class BaseThing {
                      void doIt() {}
                    }
                    class RefusingThing extends BaseThing {
                      void doIt() { throw new UnsupportedOperationException(); }
                    }
                    """;
            case COMMENTS -> """
                    // TODO: remove temporary design workaround
                    class CommentCase {
                      void work() {}
                    }
                    """;
        };
    }

    private static String javaAllSmellsSource() {
        return """
                // TODO: remove temporary design workaround
                class X {
                  int q;
                }

                class Globals {
                  public static int counter;
                }

                class MutableHolder {
                  int tempCache;
                  void first() { tempCache = 1; }
                  void second() { tempCache = 2; }
                }

                class TemporaryOnly {
                  int tempScratch;
                  void calculate() { tempScratch = 1; }
                  void unrelated() {}
                }

                class LongParameterCase {
                  void configure(String host, int port, boolean secure, String region, String tenant) {}
                }

                class DuplicateCase {
                  int first(int value) {
                    int result = value + 1;
                    result = result * 2;
                    return result;
                  }
                  int second(int value) {
                    int result = value + 1;
                    result = result * 2;
                    return result;
                  }
                }

                class DivergentCase {
                  Repository repository;
                  Renderer renderer;
                  Validator validator;
                  Calculator calculator;
                  void saveRecord(Order order) { repository.save(order); }
                  String renderView(Order order) { return renderer.render(order); }
                  void validateInput(Order order) { validator.check(order); }
                  int calculateTotal(Order order) { return calculator.calculate(order); }
                }

                class ShotA { void refresh() {} }
                class ShotB { void refresh() {} }
                class ShotC { void refresh() {} }

                class FeatureEnvyCase {
                  int compute(Order order) {
                    return order.customer.address.zip
                        + order.customer.address.code
                        + order.customer.level
                        + order.customer.score;
                  }
                }

                class DataClumpCase {
                  void createRange(int start, int end, String unit) {}
                  void updateRange(int start, int end, String unit) {}
                }

                class PrimitiveCase {
                  int status;
                  String typeCode;
                  int region;
                  boolean active;
                }

                class SwitchCase {
                  int a(String status) {
                    switch (status) {
                      case "new": return 1;
                      default: return 0;
                    }
                  }
                  int b(String status) {
                    switch (status) {
                      case "new": return 2;
                      default: return 0;
                    }
                  }
                }

                class LoopCase {
                  int sum(int[] values) {
                    int total = 0;
                    for (int value : values) {
                      total += value;
                    }
                    return total;
                  }
                }

                abstract class AbstractFutureThing {
                  abstract void futureHook();
                }

                class ChainCase {
                  int read(Order order) {
                    return order.customer.address.city.name.length();
                  }
                }

                class MiddleManCase {
                  Delegate delegate;
                  int a() { return delegate.a(); }
                  int b() { return delegate.b(); }
                  int c() { return delegate.c(); }
                }

                class InsiderTradingCase {
                  int inspect(Order order, Profile profile, Account account) {
                    return order.customer.address.zip
                        + order.customer.address.code
                        + profile.address.zip
                        + profile.address.code
                        + account.balance.amount
                        + account.balance.currency;
                  }
                }

                class LargeClassCase {
                  int f01; int f02; int f03; int f04; int f05; int f06; int f07; int f08;
                  int f09; int f10; int f11; int f12; int f13; int f14; int f15; int f16;
                }

                class AlternativeOne {
                  int amount;
                  int total() { return amount; }
                  int count(int value) { return value; }
                }
                class AlternativeTwo {
                  int amount;
                  int sum() { return amount; }
                  int size(int value) { return value; }
                }

                class DataOnly {
                  private int amount;
                  private String code;
                  int getAmount() { return amount; }
                  void setAmount(int amount) { this.amount = amount; }
                  String getCode() { return code; }
                  void setCode(String code) { this.code = code; }
                }

                class BaseThing {
                  void doIt() {}
                }
                class RefusingThing extends BaseThing {
                  void doIt() { throw new UnsupportedOperationException(); }
                }

                class LongFunctionCase {
                  void enormous() {
                """ + longFunctionLines() + """
                  }
                }

                class Order { Customer customer; }
                class Customer { Address address; int level; int score; }
                class Address { City city; int zip; int code; }
                class City { String name; }
                class Profile { Address address; }
                class Account { Balance balance; }
                class Balance { int amount; int currency; }
                class Delegate {
                  int a() { return 1; }
                  int b() { return 2; }
                  int c() { return 3; }
                }
                """;
    }

    private static String longFunctionLines() {
        StringBuilder builder = new StringBuilder();
        builder.append("    int total = 0;\n");
        for (int index = 0; index < 55; index++) {
            builder.append("    total += ").append(index).append(";\n");
        }
        builder.append("    System.out.println(total);\n");
        return builder.toString();
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

    private static final class StreamSupport {
        private static Stream<JsonNode> stream(JsonNode nodes) {
            java.util.Iterator<JsonNode> iterator = nodes.elements();
            java.util.List<JsonNode> values = new java.util.ArrayList<>();
            iterator.forEachRemaining(values::add);
            return values.stream();
        }
    }
}
