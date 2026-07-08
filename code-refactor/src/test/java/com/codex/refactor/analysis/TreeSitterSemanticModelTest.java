package com.codex.refactor.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterSemanticModelTest {
    @TempDir
    Path tempDir;

    @Test
    void csharpModelExtractsInheritanceModifiersTypesFieldAccessAndDelegation() throws Exception {
        Path source = tempDir.resolve("ServiceProxy.cs");
        Files.writeString(source, """
                class ServiceProxy : BaseService, IWorker {
                  public static int Counter;
                  private readonly int stable;
                  Delegate delegateField;
                  public override void Flush() { throw new NotImplementedException(); }
                  public int First(int id) { return delegateField.First(id); }
                  public void SetCounter(int value) { Counter = value; }
                  public int GetStable() { return stable; }
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "csharp");

        JavaClassInfo serviceProxy = analysis.classes().stream()
                .filter(classInfo -> classInfo.name().equals("ServiceProxy"))
                .findFirst()
                .orElseThrow();
        assertEquals("BaseService", serviceProxy.extendsName());
        assertTrue(serviceProxy.implementsNames().contains("IWorker"));

        JavaFieldInfo counter = field(serviceProxy, "Counter");
        assertTrue(counter.publicField());
        assertTrue(counter.staticField());
        assertFalse(counter.finalField());

        JavaFieldInfo stable = field(serviceProxy, "stable");
        assertTrue(stable.finalField());

        JavaMethodInfo flush = method(serviceProxy, "Flush");
        assertTrue(flush.overrideAnnotation());
        assertTrue(flush.throwsUnsupportedOperation());
        assertTrue(flush.thrownTypes().stream().anyMatch(type -> type.contains("NotImplementedException")));

        JavaMethodInfo first = method(serviceProxy, "First");
        assertEquals("int", first.returnType());
        assertEquals("id", first.parameterNames().getFirst());
        assertEquals("int", first.parameterTypes().getFirst());
        assertTrue(first.simpleDelegation());
        assertEquals("delegateField", first.delegations().getFirst().delegateRoot());
        assertTrue(first.delegations().getFirst().fieldDelegate());
        assertEquals("delegateField", first.methodCalls().getFirst().receiverRoot());
        assertEquals("field", first.methodCalls().getFirst().receiverKind());
        assertEquals("Delegate", first.methodCalls().getFirst().receiverType());
        assertEquals("First", first.methodCalls().getFirst().methodName());
        assertEquals(1, first.methodCalls().getFirst().argumentCount());

        JavaMethodInfo setCounter = method(serviceProxy, "SetCounter");
        assertTrue(setCounter.ownFieldWrites().contains("Counter"));
        assertTrue(counter.assignedByMethods().contains("SetCounter"));

        JavaMethodInfo getStable = method(serviceProxy, "GetStable");
        assertTrue(getStable.accessorMethod());
        assertTrue(getStable.ownFieldReads().contains("stable"));
        assertTrue(stable.readByMethods().contains("GetStable"));
    }

    @Test
    void javascriptModelExtractsModuleFieldsLocalsStatementsAndGlobalWrites() throws Exception {
        Path source = tempDir.resolve("state.js");
        Files.writeString(source, """
                let cache = 0;
                const stable = 1;
                function touch(value) {
                  let localTotal = value + stable;
                  cache = cache + localTotal;
                  return cache;
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "javascript");

        JavaFieldInfo cache = analysis.fields().stream()
                .filter(field -> field.ownerClass().equals("<module>"))
                .filter(field -> field.name().equals("cache"))
                .findFirst()
                .orElseThrow();
        assertTrue(cache.publicField());
        assertTrue(cache.staticField());
        assertFalse(cache.finalField());
        assertTrue(cache.assignedByMethods().contains("touch"));

        JavaFieldInfo stable = analysis.fields().stream()
                .filter(field -> field.ownerClass().equals("<module>"))
                .filter(field -> field.name().equals("stable"))
                .findFirst()
                .orElseThrow();
        assertTrue(stable.finalField());
        assertTrue(stable.readByMethods().contains("touch"));

        JavaMethodInfo touch = analysis.methods().stream()
                .filter(method -> method.name().equals("touch"))
                .findFirst()
                .orElseThrow();
        assertTrue(touch.localVariables().contains("localTotal"));
        assertTrue(touch.ownFieldReads().contains("cache"));
        assertTrue(touch.ownFieldWrites().contains("cache"));
        assertFalse(touch.statementShapes().isEmpty());
    }

    @Test
    void javascriptControlFlowDoesNotDoubleVisitNestedCalls() throws Exception {
        Path source = tempDir.resolve("flow.js");
        Files.writeString(source, """
                function sync(order) {
                  if (order.ready()) {
                    order.save();
                  }
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "javascript");

        JavaMethodInfo sync = analysis.methods().stream()
                .filter(method -> method.name().equals("sync"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, sync.methodCallCounts().get("ready"));
        assertEquals(1, sync.methodCallCounts().get("save"));
    }

    @Test
    void javascriptModelInfersClassFieldsFromConstructorAssignments() throws Exception {
        Path source = tempDir.resolve("DataOnly.js");
        Files.writeString(source, """
                class DataOnly {
                  constructor() {
                    this.amount = 0;
                    this.code = '';
                  }
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "javascript");

        JavaClassInfo dataOnly = analysis.classes().stream()
                .filter(classInfo -> classInfo.name().equals("DataOnly"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, dataOnly.fields().size());
        assertTrue(dataOnly.fields().stream().anyMatch(field -> field.name().equals("amount")));
        assertTrue(dataOnly.fields().stream().anyMatch(field -> field.name().equals("code")));
        assertTrue(method(dataOnly, "constructor").constructor());
    }

    @Test
    void typescriptModelExtractsClassFieldDeclarations() throws Exception {
        Path source = tempDir.resolve("DataOnly.ts");
        Files.writeString(source, """
                class DataOnly {
                  public amount: number;
                  public code: string;
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "typescript");

        JavaClassInfo dataOnly = analysis.classes().stream()
                .filter(classInfo -> classInfo.name().equals("DataOnly"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, dataOnly.fields().size());
        assertEquals("number", field(dataOnly, "amount").type());
        assertEquals("string", field(dataOnly, "code").type());
    }

    @Test
    void rubyModelExtractsAttrAccessorFields() throws Exception {
        Path source = tempDir.resolve("data_only.rb");
        Files.writeString(source, """
                class DataOnly
                  attr_accessor :amount, :code
                end
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "ruby");

        JavaClassInfo dataOnly = analysis.classes().stream()
                .filter(classInfo -> classInfo.name().equals("DataOnly"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, dataOnly.fields().size());
        assertTrue(field(dataOnly, "amount").publicField());
        assertTrue(field(dataOnly, "code").publicField());
    }

    @Test
    void pythonModelExtractsIfElifDispatchSelectorAndLabels() throws Exception {
        Path source = tempDir.resolve("dispatch.py");
        Files.writeString(source, """
                def rank(status):
                    if status == 1:
                        return 'one'
                    elif status == 2:
                        return 'two'
                    return 'other'
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "python");

        JavaMethodInfo rank = analysis.methods().stream()
                .filter(method -> method.name().equals("rank"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, rank.branchDispatches().size());
        assertEquals("if_else_dispatch", rank.branchDispatches().getFirst().kind());
        assertEquals("status", rank.branchDispatches().getFirst().selector());
        assertEquals(2, rank.branchDispatches().getFirst().labels().size());
    }

    @Test
    void bashModelInfersPositionalParametersFromFunctionBody() throws Exception {
        Path source = tempDir.resolve("configure.sh");
        Files.writeString(source, """
                configure() {
                  echo "$1 $2 $3 $4 $5"
                }
                """);

        SourceFileAnalysis analysis = new TreeSitterSourceAnalyzer().analyze(source, "bash");

        JavaMethodInfo configure = analysis.methods().stream()
                .filter(method -> method.name().equals("configure"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("arg1", "arg2", "arg3", "arg4", "arg5"), configure.parameterNames());
    }

    private static JavaFieldInfo field(JavaClassInfo classInfo, String name) {
        return classInfo.fields().stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static JavaMethodInfo method(JavaClassInfo classInfo, String name) {
        return classInfo.methods().stream()
                .filter(method -> method.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
