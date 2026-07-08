package com.codex.refactor.analysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JavaSourceAnalyzer {
    public SourceFileAnalysis analyze(Path path) throws IOException {
        String source = Files.readString(path);
        return analyze(path, source);
    }

    public SourceFileAnalysis analyze(Path path, String source) throws IOException {
        LineStats lineStats = LineStats.fromSource(source);
        SourceFileAnalysis analysis = new SourceFileAnalysis(
                path,
                "java",
                "jdk-compiler",
                "javac-tree-api",
                source,
                lineStats.physicalLines(),
                lineStats.blankLines(),
                lineStats.commentLines()
        );
        analysis.comments().addAll(lineStats.comments());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            analysis.warnings().add("JDK compiler is unavailable; Java parser-backed analysis could not run.");
            return analysis;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject fileObject = new InMemoryJavaFileObject(path, source);
        JavacTask task = (JavacTask) compiler.getTask(
                null,
                null,
                diagnostics,
                List.of("-proc:none"),
                null,
                List.of(fileObject)
        );

        Iterable<? extends CompilationUnitTree> units = task.parse();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                long line = Math.max(1, diagnostic.getLineNumber());
                long column = Math.max(1, diagnostic.getColumnNumber());
                analysis.parseErrors().add(new ParseError(
                        diagnostic.getMessage(Locale.ROOT),
                        line,
                        column,
                        line,
                        column,
                        "error"
                ));
            }
        }

        SourcePositions positions = com.sun.source.util.Trees.instance(task).getSourcePositions();
        for (CompilationUnitTree unit : units) {
            new JavaModelScanner(analysis, unit, positions).scan(unit, null);
        }
        return analysis;
    }

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private InMemoryJavaFileObject(Path path, String source) {
            super(URI.create("string:///" + path.getFileName()), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class JavaModelScanner extends TreePathScanner<Void, Void> {
        private static final Set<Tree.Kind> BOOLEAN_COMPLEXITY_KINDS = Set.of(
                Tree.Kind.CONDITIONAL_AND,
                Tree.Kind.CONDITIONAL_OR
        );

        private final SourceFileAnalysis analysis;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final ArrayDeque<JavaClassInfo> classStack = new ArrayDeque<>();
        private final Set<IfTree> elseIfContinuations = Collections.newSetFromMap(new IdentityHashMap<>());
        private JavaMethodInfo currentMethod;
        private int nestingDepth;

        private JavaModelScanner(SourceFileAnalysis analysis, CompilationUnitTree unit, SourcePositions positions) {
            this.analysis = analysis;
            this.unit = unit;
            this.positions = positions;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            String className = tree.getSimpleName().toString();
            boolean interfaceType = tree.getKind() == Tree.Kind.INTERFACE || tree.getKind() == Tree.Kind.ANNOTATION_TYPE;
            boolean abstractType = hasModifier(tree.getModifiers(), Modifier.ABSTRACT) || interfaceType;
            JavaClassInfo classInfo = new JavaClassInfo(
                    className,
                    tree.getExtendsClause() == null ? null : tree.getExtendsClause().toString(),
                    interfaceType,
                    abstractType,
                    startLine(tree),
                    endLine(tree)
            );
            for (Tree implementsClause : tree.getImplementsClause()) {
                classInfo.addImplement(implementsClause.toString());
            }

            analysis.classes().add(classInfo);
            classStack.push(classInfo);
            super.visitClass(tree, unused);
            classStack.pop();
            return null;
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            JavaClassInfo owner = currentClass();
            if (owner == null) {
                return super.visitMethod(tree, unused);
            }

            boolean constructor = tree.getReturnType() == null;
            JavaMethodInfo method = new JavaMethodInfo(
                    tree.getName().toString(),
                    owner.name(),
                    constructor ? owner.name() : tree.getReturnType().toString(),
                    constructor,
                    startLine(tree),
                    endLine(tree)
            );
            for (VariableTree parameter : tree.getParameters()) {
                method.addParameter(parameter.getType().toString(), parameter.getName().toString());
            }
            method.setNormalizedBody(normalize(sourceFor(tree.getBody())));
            method.setOverrideAnnotation(hasAnnotation(tree.getModifiers(), "Override"));

            JavaMethodInfo previous = currentMethod;
            currentMethod = method;
            super.visitMethod(tree, unused);
            finalizeMethod(tree, method);
            currentMethod = previous;

            owner.addMethod(method);
            analysis.methods().add(method);
            return null;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            JavaClassInfo owner = currentClass();
            if (owner != null && currentMethod == null) {
                JavaFieldInfo field = new JavaFieldInfo(
                        tree.getName().toString(),
                        tree.getType() == null ? "var" : tree.getType().toString(),
                        owner.name(),
                        hasModifier(tree.getModifiers(), Modifier.PUBLIC),
                        hasModifier(tree.getModifiers(), Modifier.STATIC),
                        hasModifier(tree.getModifiers(), Modifier.FINAL),
                        startLine(tree),
                        endLine(tree)
                );
                owner.addField(field);
                analysis.fields().add(field);
            } else if (currentMethod != null && !currentMethod.parameterNames().contains(tree.getName().toString())) {
                currentMethod.addLocalVariable(
                        tree.getType() == null ? "var" : tree.getType().toString(),
                        tree.getName().toString()
                );
            }
            return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            if (currentMethod != null) {
                JavaFieldInfo field = fieldInCurrentClass(tree.getName().toString());
                if (field != null) {
                    currentMethod.recordOwnFieldRead(field.name());
                    field.markRead(currentMethod.name());
                }
            }
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void unused) {
            markAssignmentTarget(tree.getVariable());
            return super.visitAssignment(tree, unused);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
            markAssignmentTarget(tree.getVariable());
            return super.visitCompoundAssignment(tree, unused);
        }

        @Override
        public Void visitUnary(UnaryTree tree, Void unused) {
            if (tree.getKind() == Tree.Kind.POSTFIX_INCREMENT
                    || tree.getKind() == Tree.Kind.POSTFIX_DECREMENT
                    || tree.getKind() == Tree.Kind.PREFIX_INCREMENT
                    || tree.getKind() == Tree.Kind.PREFIX_DECREMENT) {
                markAssignmentTarget(tree.getExpression());
            }
            return super.visitUnary(tree, unused);
        }

        @Override
        public Void visitBlock(BlockTree tree, Void unused) {
            if (currentMethod != null) {
                for (Tree statement : tree.getStatements()) {
                    currentMethod.statementShapes().add(statement.getKind() + ":" + normalize(statement.toString()));
                }
            }
            return super.visitBlock(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            if (currentMethod != null) {
                String expression = tree.getExpression().toString();
                String root = rootName(expression);
                if (root != null && !"this".equals(root) && !root.equals(currentMethod.ownerClass())) {
                    currentMethod.recordForeignMemberAccess(root, startLine(tree));
                }
                MessageChainInfo.fromExpression(tree.toString(), startLine(tree), currentMethod.variableTypes().keySet())
                        .ifPresent(currentMethod::recordMessageChain);
            }
            return super.visitMemberSelect(tree, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            if (currentMethod != null) {
                currentMethod.recordMethodCall(methodCallInfo(tree));
            }
            return super.visitMethodInvocation(tree, unused);
        }

        @Override
        public Void visitIf(IfTree tree, Void unused) {
            if (currentMethod != null && !elseIfContinuations.remove(tree)) {
                ifElseDispatch(tree).ifPresent(currentMethod::recordBranchDispatch);
            }
            recordDecision();
            return scanNested(() -> super.visitIf(tree, unused));
        }

        @Override
        public Void visitForLoop(ForLoopTree tree, Void unused) {
            recordLoop(tree);
            return scanNested(() -> super.visitForLoop(tree, unused));
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
            recordLoop(tree);
            return scanNested(() -> super.visitEnhancedForLoop(tree, unused));
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
            recordLoop(tree);
            return scanNested(() -> super.visitWhileLoop(tree, unused));
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
            recordLoop(tree);
            return scanNested(() -> super.visitDoWhileLoop(tree, unused));
        }

        @Override
        public Void visitSwitch(SwitchTree tree, Void unused) {
            if (currentMethod != null) {
                recordSwitchDispatch("switch", tree.getExpression(), tree.getCases(), tree);
            }
            recordDecision();
            return scanNested(() -> super.visitSwitch(tree, unused));
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) {
            if (currentMethod != null) {
                recordSwitchDispatch("switch_expression", tree.getExpression(), tree.getCases(), tree);
            }
            recordDecision();
            return scanNested(() -> super.visitSwitchExpression(tree, unused));
        }

        @Override
        public Void visitCase(CaseTree tree, Void unused) {
            if (currentMethod != null && !tree.getLabels().isEmpty()) {
                currentMethod.incrementCyclomaticComplexity();
            }
            return super.visitCase(tree, unused);
        }

        @Override
        public Void visitCatch(CatchTree tree, Void unused) {
            recordDecision();
            return scanNested(() -> super.visitCatch(tree, unused));
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree tree, Void unused) {
            recordDecision();
            return super.visitConditionalExpression(tree, unused);
        }

        @Override
        public Void visitBinary(BinaryTree tree, Void unused) {
            if (currentMethod != null && BOOLEAN_COMPLEXITY_KINDS.contains(tree.getKind())) {
                currentMethod.incrementCyclomaticComplexity();
                currentMethod.addCognitiveComplexity(1);
            }
            return super.visitBinary(tree, unused);
        }

        @Override
        public Void visitThrow(ThrowTree tree, Void unused) {
            if (currentMethod != null) {
                String expression = tree.getExpression().toString();
                currentMethod.thrownTypes().add(expression);
                if (expression.contains("UnsupportedOperationException")) {
                    currentMethod.setThrowsUnsupportedOperation(true);
                }
            }
            return super.visitThrow(tree, unused);
        }

        private void recordSwitchDispatch(
                String kind,
                ExpressionTree expression,
                List<? extends CaseTree> cases,
                Tree tree
        ) {
            String selector = normalizeSelector(expression.toString());
            currentMethod.switchSelectors().add(selector);
            currentMethod.recordBranchDispatch(new BranchDispatchInfo(
                    kind,
                    selector,
                    caseLabels(cases),
                    cases.stream().anyMatch(this::defaultCase),
                    startLine(tree),
                    endLine(tree)
            ));
        }

        private Optional<BranchDispatchInfo> ifElseDispatch(IfTree tree) {
            Map<String, LinkedHashSet<String>> labelsBySelector = new LinkedHashMap<>();
            IfTree current = tree;
            boolean hasDefault = false;
            int endLine = endLine(tree);
            while (current != null) {
                conditionBranches(current.getCondition()).forEach(branch ->
                        labelsBySelector.computeIfAbsent(branch.selector(), ignored -> new LinkedHashSet<>())
                                .add(branch.label()));
                StatementTree elseStatement = current.getElseStatement();
                if (elseStatement instanceof IfTree next) {
                    elseIfContinuations.add(next);
                    current = next;
                    endLine = endLine(next);
                } else {
                    hasDefault = elseStatement != null;
                    if (elseStatement != null) {
                        endLine = endLine(elseStatement);
                    }
                    current = null;
                }
            }

            Optional<Map.Entry<String, LinkedHashSet<String>>> bestSelector = labelsBySelector.entrySet().stream()
                    .filter(entry -> entry.getValue().size() >= 2)
                    .max((left, right) -> Integer.compare(left.getValue().size(), right.getValue().size()));
            if (bestSelector.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new BranchDispatchInfo(
                    "if_else_dispatch",
                    bestSelector.get().getKey(),
                    List.copyOf(bestSelector.get().getValue()),
                    hasDefault,
                    startLine(tree),
                    endLine
            ));
        }

        private List<ConditionBranch> conditionBranches(Tree condition) {
            if (condition == null) {
                return List.of();
            }
            return switch (condition.getKind()) {
                case PARENTHESIZED -> conditionBranches(((ParenthesizedTree) condition).getExpression());
                case CONDITIONAL_AND, CONDITIONAL_OR -> {
                    BinaryTree binary = (BinaryTree) condition;
                    List<ConditionBranch> branches = new ArrayList<>(conditionBranches(binary.getLeftOperand()));
                    branches.addAll(conditionBranches(binary.getRightOperand()));
                    yield branches;
                }
                case EQUAL_TO -> {
                    BinaryTree binary = (BinaryTree) condition;
                    yield equalityBranch(binary.getLeftOperand(), binary.getRightOperand())
                            .map(List::of)
                            .orElseGet(List::of);
                }
                case METHOD_INVOCATION -> equalsInvocationBranch((MethodInvocationTree) condition)
                        .map(List::of)
                        .orElseGet(List::of);
                default -> List.of();
            };
        }

        private Optional<ConditionBranch> equalityBranch(Tree left, Tree right) {
            String leftText = normalizeOperand(left.toString());
            String rightText = normalizeOperand(right.toString());
            if (labelLike(leftText) && selectorLike(rightText)) {
                return Optional.of(new ConditionBranch(normalizeSelector(rightText), normalizeLabel(leftText)));
            }
            if (labelLike(rightText) && selectorLike(leftText)) {
                return Optional.of(new ConditionBranch(normalizeSelector(leftText), normalizeLabel(rightText)));
            }
            return Optional.empty();
        }

        private Optional<ConditionBranch> equalsInvocationBranch(MethodInvocationTree tree) {
            List<? extends ExpressionTree> arguments = tree.getArguments();
            if (arguments.size() != 1) {
                return Optional.empty();
            }
            String selected = normalizeOperand(tree.getMethodSelect().toString());
            String suffix = selected.endsWith(".equalsIgnoreCase") ? ".equalsIgnoreCase" : ".equals";
            if (!selected.endsWith(suffix)) {
                return Optional.empty();
            }
            String receiver = normalizeOperand(selected.substring(0, selected.length() - suffix.length()));
            String argument = normalizeOperand(arguments.getFirst().toString());
            if (labelLike(receiver) && selectorLike(argument)) {
                return Optional.of(new ConditionBranch(normalizeSelector(argument), normalizeLabel(receiver)));
            }
            if (selectorLike(receiver) && labelLike(argument)) {
                return Optional.of(new ConditionBranch(normalizeSelector(receiver), normalizeLabel(argument)));
            }
            return Optional.empty();
        }

        private void finalizeMethod(MethodTree tree, JavaMethodInfo method) {
            if (tree.getBody() == null) {
                return;
            }
            List<? extends Tree> statements = tree.getBody().getStatements();
            String body = normalize(sourceFor(tree.getBody()));
            boolean getter = method.name().matches("^(get|is)[A-Z].*")
                    && statements.size() == 1
                    && body.contains("return");
            boolean setter = method.name().matches("^set[A-Z].*")
                    && method.parameterNames().size() == 1
                    && statements.size() <= 2
                    && body.contains("=");
            method.setAccessorMethod(getter || setter);

            Optional<DelegationInfo> delegation = directDelegation(statements, method);
            delegation.ifPresent(method::recordDelegation);
            method.setSimpleDelegation(delegation.isPresent());
        }

        private Optional<DelegationInfo> directDelegation(
                List<? extends Tree> statements,
                JavaMethodInfo method
        ) {
            if (statements.size() != 1) {
                return Optional.empty();
            }

            Tree statement = statements.getFirst();
            boolean returnsDelegateResult;
            ExpressionTree expression;
            if (statement instanceof ReturnTree returnTree) {
                expression = returnTree.getExpression();
                returnsDelegateResult = true;
            } else if (statement instanceof ExpressionStatementTree expressionStatement) {
                expression = expressionStatement.getExpression();
                returnsDelegateResult = false;
            } else {
                return Optional.empty();
            }
            if (!(expression instanceof MethodInvocationTree invocation)) {
                return Optional.empty();
            }

            String selected = normalizeOperand(invocation.getMethodSelect().toString());
            int delimiter = selected.lastIndexOf('.');
            if (delimiter <= 0 || delimiter + 1 >= selected.length()) {
                return Optional.empty();
            }
            String receiver = selected.substring(0, delimiter);
            String delegateRoot = delegateRoot(receiver);
            if (delegateRoot == null
                    || "this".equals(delegateRoot)
                    || "super".equals(delegateRoot)
                    || delegateRoot.equals(method.ownerClass())) {
                return Optional.empty();
            }

            String delegateKind;
            if (fieldInCurrentClass(delegateRoot) != null) {
                delegateKind = "field";
            } else if (method.parameterNames().contains(delegateRoot)) {
                delegateKind = "parameter";
            } else {
                return Optional.empty();
            }

            List<String> arguments = invocation.getArguments().stream()
                    .map(Object::toString)
                    .map(JavaModelScanner::normalizeOperand)
                    .toList();
            int passThroughArguments = (int) arguments.stream()
                    .filter(method.parameterNames()::contains)
                    .count();
            String targetMethod = selected.substring(delimiter + 1);
            return Optional.of(new DelegationInfo(
                    delegateRoot,
                    delegateKind,
                    targetMethod,
                    returnsDelegateResult,
                    method.name().equals(targetMethod),
                    arguments,
                    passThroughArguments,
                    method.parameterNames().size(),
                    startLine(statement),
                    endLine(statement)
            ));
        }

        private MethodCallInfo methodCallInfo(MethodInvocationTree tree) {
            String selected = normalizeOperand(tree.getMethodSelect().toString());
            String methodName = selected.contains(".")
                    ? selected.substring(selected.lastIndexOf('.') + 1)
                    : selected;
            String receiverExpression = "";
            if (tree.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                receiverExpression = normalizeOperand(memberSelect.getExpression().toString());
                methodName = memberSelect.getIdentifier().toString();
            }
            String receiverRoot = receiverRoot(receiverExpression);
            String receiverKind = receiverKind(receiverRoot, receiverExpression);
            String receiverType = receiverType(receiverRoot, receiverExpression, receiverKind);
            return new MethodCallInfo(
                    receiverExpression,
                    receiverRoot,
                    receiverKind,
                    receiverType,
                    methodName,
                    tree.getArguments().size(),
                    startLine(tree)
            );
        }

        private String receiverKind(String receiverRoot, String receiverExpression) {
            if (receiverExpression == null || receiverExpression.isBlank()) {
                return "self";
            }
            if ("this".equals(receiverRoot) || "super".equals(receiverRoot)) {
                return "self";
            }
            if (fieldInCurrentClass(receiverRoot) != null) {
                return "field";
            }
            if (currentMethod != null && currentMethod.parameterNames().contains(receiverRoot)) {
                return "parameter";
            }
            if (currentMethod != null && currentMethod.localVariables().contains(receiverRoot)) {
                return "local";
            }
            if (!receiverRoot.isBlank() && Character.isUpperCase(receiverRoot.charAt(0))) {
                return "static";
            }
            return "unknown";
        }

        private String receiverType(String receiverRoot, String receiverExpression, String receiverKind) {
            if ("self".equals(receiverKind)) {
                return currentMethod == null ? "" : currentMethod.ownerClass();
            }
            if ("field".equals(receiverKind)) {
                JavaFieldInfo field = fieldInCurrentClass(receiverRoot);
                return field == null ? "" : field.type();
            }
            if (("parameter".equals(receiverKind) || "local".equals(receiverKind)) && currentMethod != null) {
                return currentMethod.variableTypes().getOrDefault(receiverRoot, "");
            }
            if ("static".equals(receiverKind)) {
                return receiverRoot;
            }
            String thisField = thisQualifiedFieldName(receiverExpression);
            if (thisField != null) {
                JavaFieldInfo field = fieldInCurrentClass(thisField);
                if (field != null) {
                    return field.type();
                }
            }
            return "";
        }

        private void recordDecision() {
            if (currentMethod == null) {
                return;
            }
            currentMethod.incrementCyclomaticComplexity();
            currentMethod.addCognitiveComplexity(1 + nestingDepth);
            currentMethod.recordNestingDepth(nestingDepth + 1);
        }

        private void recordLoop(Tree tree) {
            if (currentMethod != null) {
                currentMethod.loopLines().add(startLine(tree));
            }
            recordDecision();
        }

        private Void scanNested(ScannerCall call) {
            nestingDepth++;
            try {
                return call.scan();
            } finally {
                nestingDepth--;
            }
        }

        private void markAssignmentTarget(Tree target) {
            if (currentMethod == null) {
                return;
            }
            String fieldName = switch (target.getKind()) {
                case IDENTIFIER -> ((IdentifierTree) target).getName().toString();
                case MEMBER_SELECT -> {
                    MemberSelectTree memberSelect = (MemberSelectTree) target;
                    String expression = memberSelect.getExpression().toString();
                    if ("this".equals(expression)) {
                        yield memberSelect.getIdentifier().toString();
                    }
                    yield null;
                }
                default -> null;
            };
            if (fieldName == null) {
                return;
            }
            JavaFieldInfo field = fieldInCurrentClass(fieldName);
            if (field != null) {
                currentMethod.recordOwnFieldWrite(field.name());
                field.markAssigned(currentMethod.name());
            }
        }

        private JavaFieldInfo fieldInCurrentClass(String name) {
            JavaClassInfo currentClass = currentClass();
            if (currentClass == null) {
                return null;
            }
            for (JavaFieldInfo field : currentClass.fields()) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }

        private JavaClassInfo currentClass() {
            return classStack.peek();
        }

        private int startLine(Tree tree) {
            long position = positions.getStartPosition(unit, tree);
            if (position < 0) {
                return 1;
            }
            return (int) unit.getLineMap().getLineNumber(position);
        }

        private int endLine(Tree tree) {
            long position = positions.getEndPosition(unit, tree);
            if (position < 0) {
                return startLine(tree);
            }
            return (int) unit.getLineMap().getLineNumber(position);
        }

        private String sourceFor(Tree tree) {
            if (tree == null) {
                return "";
            }
            long start = positions.getStartPosition(unit, tree);
            long end = positions.getEndPosition(unit, tree);
            if (start < 0 || end < start || end > analysis.source().length()) {
                return tree.toString();
            }
            return analysis.source().substring((int) start, (int) end);
        }

        private boolean defaultCase(CaseTree caseTree) {
            return caseTree.getLabels().isEmpty()
                    || caseTree.getLabels().stream()
                    .map(Object::toString)
                    .map(JavaModelScanner::normalizeLabel)
                    .anyMatch("default"::equalsIgnoreCase);
        }

        private static List<String> caseLabels(List<? extends CaseTree> cases) {
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            for (CaseTree caseTree : cases) {
                for (Object label : caseTree.getLabels()) {
                    String normalized = normalizeLabel(label.toString());
                    if (!normalized.isBlank() && !"default".equalsIgnoreCase(normalized)) {
                        labels.add(normalized);
                    }
                }
            }
            return List.copyOf(labels);
        }

        private static boolean hasModifier(ModifiersTree modifiers, Modifier modifier) {
            return modifiers.getFlags().contains(modifier);
        }

        private static boolean hasAnnotation(ModifiersTree modifiers, String name) {
            return modifiers.getAnnotations().stream()
                    .map(annotation -> annotation.getAnnotationType().toString())
                    .map(JavaModelScanner::simpleAnnotationName)
                    .anyMatch(name::equals);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").trim();
        }

        private static String simpleAnnotationName(String value) {
            int dot = value.lastIndexOf('.');
            return dot >= 0 && dot + 1 < value.length() ? value.substring(dot + 1) : value;
        }

        private static String normalizeOperand(String value) {
            return stripOuterParentheses(normalize(value));
        }

        private static String normalizeSelector(String value) {
            String normalized = normalizeOperand(value).replaceAll("\\s+", "");
            while (normalized.startsWith("this.")) {
                normalized = normalized.substring("this.".length());
            }
            return normalized;
        }

        private static String normalizeLabel(String value) {
            String normalized = normalizeOperand(value);
            if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                    || (normalized.startsWith("'") && normalized.endsWith("'"))) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            int lastDot = normalized.lastIndexOf('.');
            if (lastDot >= 0 && lastDot + 1 < normalized.length()) {
                normalized = normalized.substring(lastDot + 1);
            }
            return normalized.trim();
        }

        private static boolean labelLike(String value) {
            return literalLike(value) || constantLike(value);
        }

        private static boolean selectorLike(String value) {
            String normalized = normalizeOperand(value);
            return !normalized.isBlank()
                    && normalized.matches(".*[A-Za-z_$].*")
                    && !literalLike(normalized)
                    && !constantLike(normalized);
        }

        private static boolean literalLike(String value) {
            String normalized = normalizeOperand(value);
            return normalized.matches("\"[^\"]*\"|'[^']*'|\\d+(?:\\.\\d+)?[dDfFlL]?|true|false");
        }

        private static boolean constantLike(String value) {
            String normalized = normalizeOperand(value);
            int lastDot = normalized.lastIndexOf('.');
            String tail = lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
            return tail.matches("[A-Z][A-Z0-9_]*");
        }

        private static String delegateRoot(String receiver) {
            String normalized = normalizeOperand(receiver).replaceAll("\\s+", "");
            if (normalized.startsWith("this.")) {
                normalized = normalized.substring("this.".length());
            }
            int delimiter = normalized.indexOf('.');
            if (delimiter >= 0) {
                normalized = normalized.substring(0, delimiter);
            }
            return normalized.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? normalized : null;
        }

        private static String receiverRoot(String receiverExpression) {
            String normalized = normalizeOperand(receiverExpression).replaceAll("\\s+", "");
            if (normalized.isBlank()) {
                return "this";
            }
            if (normalized.startsWith("this.")) {
                normalized = normalized.substring("this.".length());
            }
            int delimiter = normalized.indexOf('.');
            if (delimiter >= 0) {
                normalized = normalized.substring(0, delimiter);
            }
            return normalized.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? normalized : "";
        }

        private static String thisQualifiedFieldName(String receiverExpression) {
            String normalized = normalizeOperand(receiverExpression).replaceAll("\\s+", "");
            if (!normalized.startsWith("this.")) {
                return null;
            }
            String fieldName = normalized.substring("this.".length());
            int delimiter = fieldName.indexOf('.');
            if (delimiter >= 0) {
                fieldName = fieldName.substring(0, delimiter);
            }
            return fieldName.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? fieldName : null;
        }

        private static String stripOuterParentheses(String value) {
            String result = value == null ? "" : value.trim();
            while (result.startsWith("(") && result.endsWith(")") && balancedOuterParentheses(result)) {
                result = result.substring(1, result.length() - 1).trim();
            }
            return result;
        }

        private static boolean balancedOuterParentheses(String value) {
            int depth = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    depth--;
                    if (depth == 0 && index < value.length() - 1) {
                        return false;
                    }
                }
            }
            return depth == 0;
        }

        private static String rootName(String expression) {
            String normalized = expression.trim();
            if (normalized.isEmpty() || normalized.startsWith("\"")) {
                return null;
            }
            int dot = normalized.indexOf('.');
            int paren = normalized.indexOf('(');
            int stop = normalized.length();
            if (dot >= 0) {
                stop = Math.min(stop, dot);
            }
            if (paren >= 0) {
                stop = Math.min(stop, paren);
            }
            String root = normalized.substring(0, stop);
            return root.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? root : null;
        }

        private interface ScannerCall {
            Void scan();
        }

        private record ConditionBranch(String selector, String label) {
        }
    }
}
