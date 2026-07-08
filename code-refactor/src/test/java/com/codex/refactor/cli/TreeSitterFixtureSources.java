package com.codex.refactor.cli;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public final class TreeSitterFixtureSources {
    private TreeSitterFixtureSources() {
    }

    public static Stream<Arguments> languageFixtures() {
        return Stream.of(
                Arguments.of("bash", "sh", """
                        # TODO: simplify script flow
                        greet() {
                          if [ -n "$1" ]; then
                            echo "$1"
                          fi
                        }
                        greet "world"
                        """),
                Arguments.of("c", "c", """
                        // TODO: simplify branch
                        int max(int left, int right) {
                          if (left > right) {
                            return left;
                          }
                          return right;
                        }
                        """),
                Arguments.of("cpp", "cpp", """
                        // TODO: simplify wrapper
                        class Box {
                        public:
                          int value;
                          int get() { return value; }
                        };
                        """),
                Arguments.of("csharp", "cs", """
                        // TODO: simplify wrapper
                        class Box {
                          int value;
                          int Get() {
                            return value;
                          }
                        }
                        """),
                Arguments.of("go", "go", """
                        package sample
                        // TODO: simplify branch
                        func max(left int, right int) int {
                          if left > right {
                            return left
                          }
                          return right
                        }
                        """),
                Arguments.of("python", "py", """
                        # TODO: simplify branch
                        def max_value(left, right):
                            if left > right:
                                return left
                            return right
                        """),
                Arguments.of("rust", "rs", """
                        // TODO: simplify branch
                        fn max(left: i32, right: i32) -> i32 {
                          if left > right {
                            left
                          } else {
                            right
                          }
                        }
                        """),
                Arguments.of("html", "html", """
                        <!-- TODO: simplify markup -->
                        <main>
                          <section>
                            <h1>Title</h1>
                          </section>
                        </main>
                        """),
                Arguments.of("css", "css", """
                        /* TODO: simplify selector */
                        .panel {
                          color: red;
                          display: block;
                        }
                        """),
                Arguments.of("javascript", "js", """
                        // TODO: simplify branch
                        function max(left, right) {
                          if (left > right) return left;
                          return right;
                        }
                        """),
                Arguments.of("typescript", "ts", """
                        // TODO: simplify branch
                        function max(left: number, right: number): number {
                          if (left > right) return left;
                          return right;
                        }
                        """),
                Arguments.of("tsx", "tsx", """
                        // TODO: simplify component branch
                        type Props = { title: string }
                        export function Panel(props: Props) {
                          if (props.title.length > 0) return <section>{props.title}</section>
                          return <section>empty</section>
                        }
                        """),
                Arguments.of("vue", "vue", """
                        <!-- TODO: simplify component -->
                        <template><div>{{ title }}</div></template>
                        <script setup lang="ts">
                        const title: string = 'hello'
                        </script>
                        """),
                Arguments.of("ruby", "rb", """
                        # TODO: simplify branch
                        def max(left, right)
                          if left > right
                            left
                          else
                            right
                          end
                        end
                        """),
                Arguments.of("sql", "sql", """
                        -- TODO: simplify query
                        SELECT id, name
                        FROM users
                        WHERE active = true;
                        """)
        );
    }
}
