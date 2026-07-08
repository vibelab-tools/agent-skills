package com.codex.refactor.smell;

import com.codex.refactor.analysis.JavaSourceAnalyzer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BadSmellDetectorTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {
            "mysterious-name",
            "duplicated-code",
            "long-function",
            "long-parameter-list",
            "global-data",
            "mutable-data",
            "divergent-change",
            "shotgun-surgery",
            "feature-envy",
            "data-clumps",
            "primitive-obsession",
            "repeated-switches",
            "loops",
            "lazy-element",
            "speculative-generality",
            "temporary-field",
            "message-chains",
            "middle-man",
            "insider-trading",
            "large-class",
            "alternative-classes-with-different-interfaces",
            "data-class",
            "refused-bequest",
            "comments"
    })
    void standardDispatcherDetectsChapterThreeSmell(String smellId) throws Exception {
        Path fixture = tempDir.resolve("AllSmells.java");
        Files.writeString(fixture, allSmellsSource());

        Set<String> smellIds = BadSmellDetectionDispatcher.standard()
                .detect(new SmellAnalysisContext(new JavaSourceAnalyzer().analyze(fixture)))
                .stream()
                .map(finding -> finding.smell().id())
                .collect(Collectors.toSet());

        assertTrue(smellIds.contains(smellId), "Expected smell id: " + smellId + ", got: " + smellIds);
    }

    private static String allSmellsSource() {
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
}
