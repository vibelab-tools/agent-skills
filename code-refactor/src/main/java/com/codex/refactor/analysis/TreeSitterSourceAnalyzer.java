package com.codex.refactor.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TreeSitterSourceAnalyzer {
    private static final Set<String> FUNCTION_NODES = Set.of(
            "function_definition",
            "function_declaration",
            "method_declaration",
            "method_definition",
            "function_item",
            "function",
            "arrow_function",
            "lambda",
            "method",
            "constructor",
            "singleton_method",
            "shell_function_definition",
            "command",
            "create_function_statement",
            "rule_set",
            "script_element"
    );
    private static final Set<String> CLASS_NODES = Set.of(
            "class_declaration",
            "class_definition",
            "class",
            "class_specifier",
            "struct_specifier",
            "interface_declaration",
            "type_declaration",
            "impl_item",
            "module",
            "element",
            "stylesheet",
            "source_file",
            "program",
            "translation_unit"
    );
    private static final Set<String> FIELD_NODES = Set.of(
            "field_declaration",
            "field_definition",
            "public_field_definition",
            "property_declaration",
            "property_signature",
            "member_declaration",
            "variable_declaration",
            "lexical_declaration",
            "const_declaration",
            "variable_declarator",
            "init_declarator",
            "declaration",
            "assignment",
            "assignment_statement",
            "pair"
    );
    private static final Set<String> STATEMENT_NODES = Set.of(
            "expression_statement",
            "return_statement",
            "throw_statement",
            "raise_statement",
            "assignment_statement",
            "assignment_expression",
            "variable_declaration",
            "lexical_declaration",
            "declaration",
            "if_statement",
            "if_expression",
            "for_statement",
            "while_statement",
            "switch_statement",
            "match_expression"
    );
    private static final Set<String> DECISION_NODES = Set.of(
            "if_statement",
            "if_expression",
            "conditional_expression",
            "ternary_expression",
            "catch_clause",
            "rescue",
            "when",
            "case"
    );
    private static final Set<String> LOOP_NODES = Set.of(
            "for_statement",
            "enhanced_for_statement",
            "while_statement",
            "do_statement",
            "for_expression",
            "loop_expression",
            "until",
            "while",
            "repeat_statement"
    );
    private static final Set<String> SWITCH_NODES = Set.of(
            "switch_statement",
            "switch_expression",
            "match_expression",
            "case_statement"
    );

    public SourceFileAnalysis analyze(Path path, String languageId) throws IOException {
        String source = Files.readString(path);
        return analyze(path, languageId, source);
    }

    public SourceFileAnalysis analyze(Path path, String languageId, String source) throws IOException {
        LineStats lineStats = LineStats.fromSource(source);
        SourceFileAnalysis analysis = new SourceFileAnalysis(
                path,
                languageId,
                "tree-sitter",
                "tree-sitter-" + languageId,
                source,
                lineStats.physicalLines(),
                lineStats.blankLines(),
                lineStats.commentLines()
        );
        analysis.comments().addAll(lineStats.comments());

        Optional<TSLanguage> language = TreeSitterLanguageRegistry.languageFor(languageId);
        if (language.isEmpty()) {
            analysis.warnings().add("No Tree-sitter grammar is registered for " + languageId + ".");
            return analysis;
        }

        TSParser parser = new TSParser();
        parser.setLanguage(language.get());
        TSTree tree = parser.parseString(null, source);
        TSNode root = tree.getRootNode();
        collectParseErrors(analysis, root);
        GenericModelBuilder builder = new GenericModelBuilder(analysis, source.getBytes(StandardCharsets.UTF_8));
        builder.visit(root);
        builder.postProcess();
        return analysis;
    }

    private static void collectParseErrors(SourceFileAnalysis analysis, TSNode node) {
        if (node.isError()) {
            analysis.parseErrors().add(new ParseError(
                    "Tree-sitter parse error at " + node.getType(),
                    node.getStartPoint().getRow() + 1L,
                    node.getStartPoint().getColumn() + 1L,
                    node.getEndPoint().getRow() + 1L,
                    node.getEndPoint().getColumn() + 1L,
                    "error"
            ));
        }
        for (int index = 0; index < node.getNamedChildCount(); index++) {
            collectParseErrors(analysis, node.getNamedChild(index));
        }
    }

    private static final class GenericModelBuilder {
        private final SourceFileAnalysis analysis;
        private final byte[] sourceBytes;
        private final ArrayDeque<JavaClassInfo> classStack = new ArrayDeque<>();
        private JavaMethodInfo currentMethod;
        private int nestingDepth;

        private GenericModelBuilder(SourceFileAnalysis analysis, byte[] sourceBytes) {
            this.analysis = analysis;
            this.sourceBytes = sourceBytes;
        }

        private void visit(TSNode node) {
            String type = node.getType();
            if (isClassNode(type) && !isRootNode(type)) {
                visitClass(node);
                return;
            }
            if (isFunctionNode(type)) {
                visitFunction(node);
                return;
            }

            boolean childrenAlreadyVisited = recordCurrentMethodEvidence(node);
            if (currentMethod == null && FIELD_NODES.contains(type)) {
                if (classStack.isEmpty()) {
                    addModuleField(node);
                } else {
                    addGenericField(node);
                }
            } else if (currentMethod != null) {
                if (FIELD_NODES.contains(type)) {
                    addGenericLocalVariable(node);
                }
                if (STATEMENT_NODES.contains(type)) {
                    recordStatementShape(node);
                }
            }
            if (!childrenAlreadyVisited) {
                visitChildren(node);
            }
        }

        private void visitClass(TSNode node) {
            String nodeText = text(node);
            String className = nameFor(node, "class@" + startLine(node));
            JavaClassInfo classInfo = new JavaClassInfo(
                    className,
                    extendsName(nodeText),
                    interfaceType(node, nodeText),
                    abstractType(nodeText) || interfaceType(node, nodeText),
                    startLine(node),
                    endLine(node)
            );
            implementsNames(nodeText, classInfo.extendsName()).forEach(classInfo::addImplement);
            analysis.classes().add(classInfo);
            inferClassFields(classInfo, nodeText, startLine(node));
            classStack.push(classInfo);
            visitChildren(node);
            classStack.pop();
        }

        private void visitFunction(TSNode node) {
            String owner = classStack.isEmpty() ? "<module>" : classStack.peek().name();
            String nodeText = text(node);
            String name = nameFor(node, node.getType() + "@" + startLine(node));
            JavaMethodInfo method = new JavaMethodInfo(
                    name,
                    owner,
                    returnTypeFor(node, nodeText, name),
                    constructorLike(analysis.language(), owner, name),
                    startLine(node),
                    endLine(node)
            );
            addParameters(node, method, nodeText);
            if ("bash".equals(analysis.language()) && method.parameterNames().isEmpty()) {
                addShellPositionalParameters(method, nodeText);
            }
            method.setNormalizedBody(normalize(nodeText));
            method.setOverrideAnnotation(overrideAnnotation(nodeText));
            recordThrownEvidence(method, nodeText);
            JavaMethodInfo previous = currentMethod;
            currentMethod = method;
            visitChildren(node);
            currentMethod = previous;
            finalizeMethod(method);
            if (!classStack.isEmpty()) {
                classStack.peek().addMethod(method);
            }
            analysis.methods().add(method);
        }

        private void visitChildren(TSNode node) {
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                visit(node.getNamedChild(index));
            }
        }

        private boolean recordCurrentMethodEvidence(TSNode node) {
            if (currentMethod == null) {
                return false;
            }
            String type = node.getType();
            if (DECISION_NODES.contains(type)) {
                if (type.equals("if_statement") || type.equals("if_expression")) {
                    ifElseDispatch(node).ifPresent(currentMethod::recordBranchDispatch);
                }
                currentMethod.incrementCyclomaticComplexity();
                currentMethod.addCognitiveComplexity(1 + nestingDepth);
                currentMethod.recordNestingDepth(nestingDepth + 1);
                nestingDepth++;
                visitChildren(node);
                nestingDepth--;
                return true;
            } else if (LOOP_NODES.contains(type)) {
                currentMethod.loopLines().add(startLine(node));
                currentMethod.incrementCyclomaticComplexity();
                currentMethod.addCognitiveComplexity(1 + nestingDepth);
                currentMethod.recordNestingDepth(nestingDepth + 1);
                nestingDepth++;
                visitChildren(node);
                nestingDepth--;
                return true;
            } else if (SWITCH_NODES.contains(type)) {
                String selector = switchSelector(node);
                currentMethod.switchSelectors().add(selector);
                currentMethod.recordBranchDispatch(new BranchDispatchInfo(
                        "switch",
                        selector,
                        switchLabels(node),
                        switchHasDefault(node),
                        startLine(node),
                        endLine(node)
                ));
                currentMethod.incrementCyclomaticComplexity();
                currentMethod.addCognitiveComplexity(1 + nestingDepth);
            } else if (type.equals("member_expression")
                    || type.equals("field_expression")
                    || type.equals("scoped_identifier")
                    || type.equals("member_access_expression")
                    || type.equals("call_expression")
                    || type.equals("invocation_expression")) {
                if (type.equals("call_expression") || type.equals("invocation_expression")) {
                    methodCallInfo(text(node), startLine(node)).ifPresent(currentMethod::recordMethodCall);
                }
                MessageChainInfo.fromExpression(text(node), startLine(node), currentMethod.variableTypes().keySet())
                        .ifPresent(currentMethod::recordMessageChain);
                String root = rootName(text(node));
                if (root != null && !"this".equals(root) && !root.equals("self")) {
                    currentMethod.recordForeignMemberAccess(root, startLine(node));
                }
            } else if (type.equals("throw_statement")
                    || type.equals("throw_expression")
                    || type.equals("raise")
                    || type.equals("raise_statement")) {
                recordThrownEvidence(currentMethod, text(node));
            }
            return false;
        }

        private void addGenericField(TSNode node) {
            JavaClassInfo owner = classStack.peek();
            String nodeText = text(node);
            String name = fieldNameFor(node, nodeText, "field@" + startLine(node));
            if (name.isBlank()) {
                return;
            }
            if (fieldExists(owner.name(), name, startLine(node))) {
                return;
            }
            JavaFieldInfo field = new JavaFieldInfo(
                    name,
                    fieldTypeFor(node, nodeText, name),
                    owner.name(),
                    publicLike(nodeText),
                    staticLike(nodeText),
                    finalLike(nodeText),
                    startLine(node),
                    endLine(node)
            );
            owner.addField(field);
            analysis.fields().add(field);
        }

        private void addModuleField(TSNode node) {
            String nodeText = text(node);
            if (assignmentWithoutDeclaration(nodeText) || nodeText.contains("=>")) {
                return;
            }
            String name = fieldNameFor(node, nodeText, "field@" + startLine(node));
            if (name.isBlank() || fieldExists("<module>", name, startLine(node))) {
                return;
            }
            JavaFieldInfo field = new JavaFieldInfo(
                    name,
                    fieldTypeFor(node, nodeText, name),
                    "<module>",
                    true,
                    true,
                    finalLike(nodeText),
                    startLine(node),
                    endLine(node)
            );
            analysis.fields().add(field);
        }

        private void addGenericLocalVariable(TSNode node) {
            String nodeText = text(node);
            if (assignmentWithoutDeclaration(nodeText) || nodeText.contains("=>")) {
                return;
            }
            String name = fieldNameFor(node, nodeText, "");
            if (name.isBlank()
                    || currentMethod.parameterNames().contains(name)
                    || currentMethod.localVariables().contains(name)) {
                return;
            }
            currentMethod.addLocalVariable(fieldTypeFor(node, nodeText, name), name);
        }

        private void recordStatementShape(TSNode node) {
            String statement = normalize(text(node));
            if (!statement.isBlank()) {
                currentMethod.statementShapes().add(node.getType() + ":" + statement);
            }
        }

        private void addParameters(TSNode node, JavaMethodInfo method, String nodeText) {
            TSNode parameters = node.getChildByFieldName("parameters");
            if (parameters == null || parameters.isNull()) {
                parameters = firstDescendantOfType(node, "parameter_list", "formal_parameters", "parameters");
            }
            if (parameters == null || parameters.isNull()) {
                addParametersFromText(method, nodeText);
                return;
            }
            for (int index = 0; index < parameters.getNamedChildCount(); index++) {
                TSNode child = parameters.getNamedChild(index);
                String type = child.getType();
                if (type.contains("parameter") || type.equals("identifier")) {
                    String childText = text(child);
                    String name = parameterNameFor(child, childText, "param" + index);
                    if (!name.isBlank()) {
                        method.addParameter(parameterTypeFor(childText, name), name);
                    }
                }
            }
            if (method.parameterNames().isEmpty()) {
                addParametersFromText(method, nodeText);
            }
        }

        private void postProcess() {
            for (JavaClassInfo classInfo : analysis.classes()) {
                for (JavaMethodInfo method : classInfo.methods()) {
                    recordFieldAccesses(classInfo, method);
                    directDelegation(classInfo, method).ifPresent(delegation -> {
                        method.recordDelegation(delegation);
                        method.setSimpleDelegation(true);
                    });
                    method.setAccessorMethod(method.accessorMethod() || accessorLike(classInfo, method));
                }
            }
            List<JavaFieldInfo> moduleFields = analysis.fields().stream()
                    .filter(field -> "<module>".equals(field.ownerClass()))
                    .toList();
            if (!moduleFields.isEmpty()) {
                for (JavaMethodInfo method : analysis.methods()) {
                    recordModuleFieldAccesses(moduleFields, method);
                }
            }
        }

        private void finalizeMethod(JavaMethodInfo method) {
            method.setAccessorMethod(textAccessorLike(method));
        }

        private void recordFieldAccesses(JavaClassInfo classInfo, JavaMethodInfo method) {
            String body = method.normalizedBody();
            for (JavaFieldInfo field : classInfo.fields()) {
                if (referencesField(body, field.name())) {
                    method.recordOwnFieldRead(field.name());
                    field.markRead(method.name());
                }
                if (assignsField(body, field.name())) {
                    method.recordOwnFieldWrite(field.name());
                    field.markAssigned(method.name());
                }
            }
        }

        private void recordModuleFieldAccesses(List<JavaFieldInfo> moduleFields, JavaMethodInfo method) {
            String body = method.normalizedBody();
            for (JavaFieldInfo field : moduleFields) {
                if (method.parameterNames().contains(field.name()) || method.localVariables().contains(field.name())) {
                    continue;
                }
                if (referencesField(body, field.name())) {
                    method.recordOwnFieldRead(field.name());
                    field.markRead(method.name());
                }
                if (assignsField(body, field.name())) {
                    method.recordOwnFieldWrite(field.name());
                    field.markAssigned(method.name());
                }
            }
        }

        private boolean fieldExists(String ownerClass, String name, int startLine) {
            return fieldExists(ownerClass, name);
        }

        private boolean fieldExists(String ownerClass, String name) {
            return analysis.fields().stream()
                    .anyMatch(field -> field.ownerClass().equals(ownerClass)
                            && field.name().equals(name));
        }

        private JavaFieldInfo fieldInCurrentClass(String name) {
            if (classStack.isEmpty()) {
                return null;
            }
            for (JavaFieldInfo field : classStack.peek().fields()) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }

        private void inferClassFields(JavaClassInfo owner, String classText, int classStartLine) {
            inferRubyAccessorFields(owner, classText, classStartLine);
            inferDeclaredClassFields(owner, classText, classStartLine);
            inferAssignedInstanceFields(owner, classText, classStartLine);
        }

        private void inferRubyAccessorFields(JavaClassInfo owner, String classText, int classStartLine) {
            Matcher matcher = Pattern.compile("(?m)^\\s*attr_(?:accessor|reader|writer)\\s+([^\\n]+)")
                    .matcher(classText);
            while (matcher.find()) {
                String[] names = matcher.group(1)
                        .replaceAll("[\\[\\]%,]", " ")
                        .trim()
                        .split("\\s+");
                for (String name : names) {
                    String cleaned = cleanName(name.replaceFirst("^:", "").replaceAll("[\"']", ""), "");
                    addInferredField(owner, cleaned, "unknown", true, false, false,
                            lineForOffset(classText, matcher.start(1), classStartLine));
                }
            }
        }

        private void inferDeclaredClassFields(JavaClassInfo owner, String classText, int classStartLine) {
            Matcher typed = Pattern.compile(
                    "(?m)^\\s*((?:public|private|protected|internal|readonly|static|declare|override|abstract|accessor|export\\s+)*)"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                            + "([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\s*(?:[=;\\n])"
            ).matcher(classText);
            while (typed.find()) {
                String modifiers = typed.group(1);
                addInferredField(
                        owner,
                        cleanName(typed.group(2), ""),
                        cleanTypeName(typed.group(3)),
                        publicByDefault(modifiers),
                        staticLike(modifiers),
                        finalLike(modifiers),
                        lineForOffset(classText, typed.start(2), classStartLine)
                );
            }

            Matcher initialized = Pattern.compile(
                    "(?m)^\\s*((?:public|private|protected|internal|readonly|static|declare|override|abstract|accessor|export\\s+)*)"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?!>).*"
            ).matcher(classText);
            while (initialized.find()) {
                String modifiers = initialized.group(1);
                addInferredField(
                        owner,
                        cleanName(initialized.group(2), ""),
                        "unknown",
                        publicByDefault(modifiers),
                        staticLike(modifiers),
                        finalLike(modifiers),
                        lineForOffset(classText, initialized.start(2), classStartLine)
                );
            }
        }

        private void inferAssignedInstanceFields(JavaClassInfo owner, String classText, int classStartLine) {
            Matcher matcher = Pattern.compile("(?:this\\.|self\\.|@)([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=|\\+=|-=|\\*=|/=|%=|\\+\\+|--)").matcher(classText);
            while (matcher.find()) {
                addInferredField(owner, cleanName(matcher.group(1), ""), "unknown", true, false, false,
                        lineForOffset(classText, matcher.start(1), classStartLine));
            }
        }

        private void addInferredField(
                JavaClassInfo owner,
                String name,
                String type,
                boolean publicField,
                boolean staticField,
                boolean finalField,
                int line
        ) {
            if (name.isBlank() || fieldExists(owner.name(), name)) {
                return;
            }
            JavaFieldInfo field = new JavaFieldInfo(
                    name,
                    type == null || type.isBlank() ? "unknown" : type,
                    owner.name(),
                    publicField,
                    staticField,
                    finalField,
                    line,
                    line
            );
            owner.addField(field);
            analysis.fields().add(field);
        }

        private Optional<DelegationInfo> directDelegation(JavaClassInfo classInfo, JavaMethodInfo method) {
            if (method.cyclomaticComplexity() > 1 || method.methodCallCounts().values().stream().mapToInt(Integer::intValue).sum() > 1) {
                return Optional.empty();
            }
            String body = methodBodyText(method.normalizedBody());
            Matcher matcher = Pattern.compile(
                    "(?:^|[;{}\\n])\\s*(return\\s+)?(?:this\\.|self\\.|@)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:\\.|->|::)\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*;?\\s*(?:end|}|$)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(body);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String delegateRoot = matcher.group(2);
            String delegateKind;
            if (classInfo.fields().stream().anyMatch(field -> field.name().equals(delegateRoot))) {
                delegateKind = "field";
            } else if (method.parameterNames().contains(delegateRoot)) {
                delegateKind = "parameter";
            } else {
                return Optional.empty();
            }
            List<String> arguments = splitArguments(matcher.group(4));
            int passThroughArguments = (int) arguments.stream()
                    .filter(method.parameterNames()::contains)
                    .count();
            return Optional.of(new DelegationInfo(
                    delegateRoot,
                    delegateKind,
                    matcher.group(3),
                    matcher.group(1) != null,
                    method.name().equals(matcher.group(3)),
                    arguments,
                    passThroughArguments,
                    method.parameterNames().size(),
                    method.startLine(),
                    method.endLine()
            ));
        }

        private static boolean accessorLike(JavaClassInfo classInfo, JavaMethodInfo method) {
            if (!method.delegations().isEmpty() || method.simpleDelegation()) {
                return false;
            }
            if (textAccessorLike(method)) {
                return true;
            }
            String body = methodBodyText(method.normalizedBody());
            String propertyName = propertyNameFromAccessor(method.name());
            if (!propertyName.isBlank() && classInfo.fields().stream()
                    .anyMatch(field -> field.name().equalsIgnoreCase(propertyName) && referencesField(body, field.name()))) {
                return true;
            }
            boolean referencesOnlyOneField = classInfo.fields().stream()
                    .filter(field -> referencesField(body, field.name()))
                    .count() == 1;
            return referencesOnlyOneField
                    && method.cyclomaticComplexity() <= 1
                    && method.physicalLines() <= 4
                    && (body.matches("(?is).*\\breturn\\b.*")
                    || body.matches("(?is).*=.*"));
        }

        private static boolean textAccessorLike(JavaMethodInfo method) {
            String body = methodBodyText(method.normalizedBody());
            boolean getter = method.name().matches("(?i)^(get|is|has)[A-Z_].*")
                    && body.matches("(?is).*\\breturn\\b.*");
            boolean setter = method.name().matches("(?i)^set[A-Z_].*")
                    && method.parameterNames().size() == 1
                    && body.matches("(?is).*=.*");
            return getter || setter;
        }

        private static String propertyNameFromAccessor(String methodName) {
            Matcher matcher = Pattern.compile("(?i)^(get|set|is|has)([A-Z_].*)$").matcher(methodName);
            if (!matcher.find()) {
                return "";
            }
            String property = matcher.group(2);
            return property.isBlank()
                    ? ""
                    : Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }

        private static boolean referencesField(String body, String fieldName) {
            return Pattern.compile("(?<![A-Za-z0-9_$])(?:this\\.|self\\.|@|this->)?" + Pattern.quote(fieldName) + "(?![A-Za-z0-9_$])")
                    .matcher(body)
                    .find();
        }

        private static boolean assignsField(String body, String fieldName) {
            String reference = "(?<![A-Za-z0-9_$])(?:this\\.|self\\.|@|this->)?" + Pattern.quote(fieldName) + "(?![A-Za-z0-9_$])";
            return Pattern.compile(reference + "\\s*(?:=|\\+=|-=|\\*=|/=|%=|\\+\\+|--)").matcher(body).find()
                    || Pattern.compile("(?:\\+\\+|--)\\s*" + reference).matcher(body).find();
        }

        private static String methodBodyText(String methodText) {
            String normalized = normalize(methodText);
            int openBrace = normalized.indexOf('{');
            int closeBrace = normalized.lastIndexOf('}');
            if (openBrace >= 0 && closeBrace > openBrace) {
                return normalized.substring(openBrace + 1, closeBrace).trim();
            }
            int firstLine = normalized.indexOf('\n');
            return firstLine >= 0 ? normalized.substring(firstLine + 1).trim() : normalized;
        }

        private static List<String> splitArguments(String argumentText) {
            if (argumentText == null || argumentText.isBlank()) {
                return List.of();
            }
            List<String> arguments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int parenDepth = 0;
            for (int index = 0; index < argumentText.length(); index++) {
                char character = argumentText.charAt(index);
                if (character == '(') {
                    parenDepth++;
                } else if (character == ')' && parenDepth > 0) {
                    parenDepth--;
                }
                if (character == ',' && parenDepth == 0) {
                    addArgument(arguments, current);
                } else {
                    current.append(character);
                }
            }
            addArgument(arguments, current);
            return arguments;
        }

        private static void addArgument(List<String> arguments, StringBuilder current) {
            String argument = normalize(current.toString())
                    .replaceFirst("^this\\.", "")
                    .replaceFirst("^self\\.", "")
                    .replaceFirst("^@", "");
            if (!argument.isBlank()) {
                arguments.add(argument);
            }
            current.setLength(0);
        }

        private void addParametersFromText(JavaMethodInfo method, String nodeText) {
            String parameters = firstParenthesizedContent(nodeText).orElse("");
            if (parameters.isBlank()) {
                return;
            }
            for (String parameter : splitArguments(parameters)) {
                String name = parameterNameFor(null, parameter, "");
                if (!name.isBlank()) {
                    method.addParameter(parameterTypeFor(parameter, name), name);
                }
            }
        }

        private void addShellPositionalParameters(JavaMethodInfo method, String nodeText) {
            Matcher matcher = Pattern.compile("\\$\\{?(\\d+)\\}?").matcher(nodeText);
            int maxPosition = 0;
            while (matcher.find()) {
                int position = Integer.parseInt(matcher.group(1));
                if (position > 0) {
                    maxPosition = Math.max(maxPosition, position);
                }
            }
            for (int position = 1; position <= maxPosition; position++) {
                method.addParameter("unknown", "arg" + position);
            }
        }

        private static Optional<String> firstParenthesizedContent(String value) {
            int start = value.indexOf('(');
            if (start < 0) {
                return Optional.empty();
            }
            int depth = 0;
            for (int index = start; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    depth--;
                    if (depth == 0) {
                        return Optional.of(value.substring(start + 1, index));
                    }
                }
            }
            return Optional.empty();
        }

        private static void recordThrownEvidence(JavaMethodInfo method, String text) {
            String normalized = normalize(text);
            String lower = normalized.toLowerCase(Locale.ROOT);
            Matcher thrown = Pattern.compile("\\b(?:throw|raise)\\s+(?:new\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*)").matcher(normalized);
            while (thrown.find()) {
                method.thrownTypes().add(thrown.group(1));
            }
            if (lower.contains("unsupportedoperationexception")
                    || lower.contains("notimplementedexception")
                    || lower.contains("not implemented")
                    || lower.contains("not supported")
                    || lower.contains("unsupported")) {
                method.setThrowsUnsupportedOperation(true);
            }
        }

        private static boolean interfaceType(TSNode node, String nodeText) {
            String type = node.getType();
            String lower = nodeText.toLowerCase(Locale.ROOT);
            return type.contains("interface")
                    || lower.matches("(?s)^\\s*(export\\s+)?interface\\b.*")
                    || lower.matches("(?s)^\\s*(pub\\s+)?trait\\b.*");
        }

        private static boolean abstractType(String nodeText) {
            String lower = nodeText.toLowerCase(Locale.ROOT);
            return lower.contains("abstract class")
                    || lower.matches("(?s)^\\s*(export\\s+)?interface\\b.*")
                    || lower.matches("(?s)^\\s*(pub\\s+)?trait\\b.*");
        }

        private static String extendsName(String nodeText) {
            String normalized = normalize(nodeText);
            for (Pattern pattern : List.of(
                    Pattern.compile("\\bextends\\s+([A-Za-z_$][A-Za-z0-9_$.:<>]*)"),
                    Pattern.compile("\\bclass\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*<\\s*([A-Za-z_$][A-Za-z0-9_$.:]*)"),
                    Pattern.compile("\\bclass\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(\\s*([A-Za-z_$][A-Za-z0-9_$.:]*)"),
                    Pattern.compile("\\b(?:class|struct)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*:\\s*(?:public|private|protected|virtual|abstract|sealed|override\\s+)*([A-Za-z_$][A-Za-z0-9_$.:]*)")
            )) {
                Matcher matcher = pattern.matcher(normalized);
                if (matcher.find()) {
                    return cleanTypeName(matcher.group(1));
                }
            }
            return null;
        }

        private static List<String> implementsNames(String nodeText, String extendsName) {
            String normalized = normalize(nodeText);
            LinkedHashSet<String> names = new LinkedHashSet<>();
            Matcher implementsMatcher = Pattern.compile("\\bimplements\\s+([^\\{]+)").matcher(normalized);
            if (implementsMatcher.find()) {
                addTypeList(names, implementsMatcher.group(1));
            }
            Matcher csharpMatcher = Pattern.compile("\\b(?:class|struct)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*:\\s*([^\\{]+)").matcher(normalized);
            if (csharpMatcher.find()) {
                List<String> parents = typeList(csharpMatcher.group(1));
                parents.stream().skip(1).forEach(names::add);
            }
            Matcher pythonMatcher = Pattern.compile("\\bclass\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(([^)]*)\\)").matcher(normalized);
            if (pythonMatcher.find()) {
                List<String> parents = typeList(pythonMatcher.group(1));
                parents.stream().skip(1).forEach(names::add);
            }
            if (extendsName != null) {
                names.remove(extendsName);
            }
            return List.copyOf(names);
        }

        private static void addTypeList(Set<String> names, String value) {
            names.addAll(typeList(value));
        }

        private static List<String> typeList(String value) {
            return java.util.Arrays.stream(value.split(","))
                    .map(part -> part.replaceAll("\\b(public|private|protected|virtual|abstract|sealed|override|new)\\b", ""))
                    .map(GenericModelBuilder::cleanTypeName)
                    .filter(part -> !part.isBlank())
                    .toList();
        }

        private static boolean publicLike(String nodeText) {
            String lower = normalize(nodeText).toLowerCase(Locale.ROOT);
            return lower.matches("(?s).*\\b(public|export|pub)\\b.*");
        }

        private static boolean staticLike(String nodeText) {
            String lower = normalize(nodeText).toLowerCase(Locale.ROOT);
            return lower.matches("(?s).*\\b(static|companion|object)\\b.*");
        }

        private static boolean finalLike(String nodeText) {
            String lower = normalize(nodeText).toLowerCase(Locale.ROOT);
            return lower.matches("(?s).*\\b(final|readonly|const|val)\\b.*")
                    || lower.matches("(?s)^\\s*const\\b.*");
        }

        private static boolean publicByDefault(String modifiers) {
            String lower = normalize(modifiers).toLowerCase(Locale.ROOT);
            return !lower.matches("(?s).*\\b(private|protected)\\b.*");
        }

        private static boolean constructorLike(String language, String owner, String name) {
            if ("<module>".equals(owner) || name == null || name.isBlank()) {
                return false;
            }
            if (name.equals(owner)) {
                return true;
            }
            String normalizedLanguage = language == null ? "" : language.toLowerCase(Locale.ROOT);
            String normalizedName = name.toLowerCase(Locale.ROOT);
            return (Set.of("javascript", "typescript", "tsx").contains(normalizedLanguage)
                    && normalizedName.equals("constructor"))
                    || (normalizedLanguage.equals("ruby") && normalizedName.equals("initialize"));
        }

        private static int lineForOffset(String text, int offset, int baseLine) {
            int line = baseLine;
            int boundedOffset = Math.max(0, Math.min(offset, text.length()));
            for (int index = 0; index < boundedOffset; index++) {
                if (text.charAt(index) == '\n') {
                    line++;
                }
            }
            return line;
        }

        private static boolean assignmentWithoutDeclaration(String nodeText) {
            String normalized = normalize(nodeText);
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.matches("(?s)^\\s*(var|let|const|val|final|readonly|public|private|protected|internal|static|pub|export|declare)\\b.*")) {
                return false;
            }
            if (normalized.matches("(?s)^\\s*[A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*(?:[=;,{].*)?$")) {
                return false;
            }
            if (normalized.matches("(?s)^\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*:\\s*[A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*.*$")) {
                return false;
            }
            return normalized.matches("(?s).*\\b[A-Za-z_$][A-Za-z0-9_$]*\\s*(?:=|\\+=|-=|\\*=|/=|%=|:=).*");
        }

        private static boolean overrideAnnotation(String nodeText) {
            String lower = normalize(nodeText).toLowerCase(Locale.ROOT);
            return lower.contains("@override")
                    || lower.matches("(?s).*\\boverride\\b.*");
        }

        private String fieldNameFor(TSNode node, String nodeText, String fallback) {
            String normalized = normalize(nodeText);
            for (Pattern pattern : List.of(
                    Pattern.compile("\\b(?:public|private|protected|internal|static|readonly|const|final|volatile|mutable|export|pub\\s+)*([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*[^=;{]+"),
                    Pattern.compile("\\b(?:public|private|protected|internal|static|readonly|const|final|volatile|mutable|export|pub\\s+)*(?:[A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*\\s+)+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:[=;,{]|$)"),
                    Pattern.compile("\\b(?:var|let|const)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"),
                    Pattern.compile("@([A-Za-z_$][A-Za-z0-9_$]*)\\b")
            )) {
                Matcher matcher = pattern.matcher(normalized);
                if (matcher.find()) {
                    return cleanName(matcher.group(1), fallback);
                }
            }
            return nameFor(node, fallback);
        }

        private static String fieldTypeFor(TSNode node, String nodeText, String fieldName) {
            String normalized = normalize(nodeText);
            Matcher colon = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\s*:\\s*([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)").matcher(normalized);
            if (colon.find()) {
                return cleanTypeName(colon.group(1));
            }
            Matcher prefixType = Pattern.compile("\\b([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\s+" + Pattern.quote(fieldName) + "\\b").matcher(normalized);
            String candidate = "";
            while (prefixType.find()) {
                candidate = prefixType.group(1);
            }
            if (!candidate.isBlank() && !Set.of("var", "let", "const", "public", "private", "protected", "static", "readonly").contains(candidate)) {
                return cleanTypeName(candidate);
            }
            return "unknown";
        }

        private String parameterNameFor(TSNode node, String parameterText, String fallback) {
            String normalized = normalize(parameterText);
            Matcher colon = Pattern.compile("^\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*.+$").matcher(normalized);
            if (colon.find()) {
                return cleanName(colon.group(1), fallback);
            }
            String[] parts = normalized.replaceAll("[,)]", " ").trim().split("\\s+");
            if (parts.length >= 2) {
                String first = cleanTypeName(parts[0]);
                String second = cleanName(parts[1], "");
                if (typeKeyword(first) && !second.isBlank()) {
                    return second;
                }
                if (typeKeyword(parts[1]) && !first.isBlank()) {
                    return cleanName(first, fallback);
                }
                if (!second.isBlank()) {
                    return second;
                }
            }
            return node == null ? cleanName(normalized, fallback) : nameFor(node, fallback);
        }

        private static String parameterTypeFor(String parameterText, String parameterName) {
            String normalized = normalize(parameterText);
            Matcher colon = Pattern.compile("\\b" + Pattern.quote(parameterName) + "\\s*:\\s*([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)").matcher(normalized);
            if (colon.find()) {
                return cleanTypeName(colon.group(1));
            }
            Matcher prefixType = Pattern.compile("^\\s*([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\s+" + Pattern.quote(parameterName) + "\\b").matcher(normalized);
            if (prefixType.find()) {
                return cleanTypeName(prefixType.group(1));
            }
            Matcher suffixType = Pattern.compile("\\b" + Pattern.quote(parameterName) + "\\s+([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\b").matcher(normalized);
            if (suffixType.find() && typeKeyword(suffixType.group(1))) {
                return cleanTypeName(suffixType.group(1));
            }
            return "unknown";
        }

        private static String returnTypeFor(TSNode node, String nodeText, String methodName) {
            String normalized = normalize(nodeText);
            Matcher colonReturn = Pattern.compile("\\)\\s*:\\s*([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)").matcher(normalized);
            if (colonReturn.find()) {
                return cleanTypeName(colonReturn.group(1));
            }
            Matcher arrowReturn = Pattern.compile("\\)\\s*(?:->|=>)\\s*([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)").matcher(normalized);
            if (arrowReturn.find()) {
                return cleanTypeName(arrowReturn.group(1));
            }
            Matcher goReturn = Pattern.compile("\\)\\s+([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\s*\\{").matcher(normalized);
            if (goReturn.find() && typeKeyword(goReturn.group(1))) {
                return cleanTypeName(goReturn.group(1));
            }
            Matcher prefixReturn = Pattern.compile("\\b([A-Za-z_$][A-Za-z0-9_$.:<>\\[\\]?]*)\\s+" + Pattern.quote(methodName) + "\\s*\\(").matcher(normalized);
            String candidate = "";
            while (prefixReturn.find()) {
                candidate = prefixReturn.group(1);
            }
            if (!candidate.isBlank() && !Set.of("function", "def", "fn", "func", "public", "private", "protected", "static", "override").contains(candidate)) {
                return cleanTypeName(candidate);
            }
            return "unknown";
        }

        private static boolean typeKeyword(String value) {
            String lower = cleanTypeName(value).toLowerCase(Locale.ROOT);
            return Set.of(
                    "string", "str", "char", "boolean", "bool", "int", "integer", "long", "short",
                    "float", "double", "decimal", "number", "void", "object", "any", "unknown",
                    "i8", "i16", "i32", "i64", "u8", "u16", "u32", "u64", "usize", "isize",
                    "f32", "f64"
            ).contains(lower) || Character.isUpperCase(cleanTypeName(value).isBlank() ? 'x' : cleanTypeName(value).charAt(0));
        }

        private static String cleanTypeName(String value) {
            if (value == null) {
                return "";
            }
            String cleaned = value.trim()
                    .replaceAll("[;,{(<].*", "")
                    .replaceAll("\\[\\]$", "Array")
                    .replaceAll("[^A-Za-z0-9_$.:<>?\\[\\]]", "");
            int dot = cleaned.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < cleaned.length()) {
                cleaned = cleaned.substring(dot + 1);
            }
            int scope = cleaned.lastIndexOf("::");
            if (scope >= 0 && scope + 2 < cleaned.length()) {
                cleaned = cleaned.substring(scope + 2);
            }
            return cleaned;
        }

        private TSNode firstDescendantOfType(TSNode node, String... types) {
            List<String> wanted = List.of(types);
            if (wanted.contains(node.getType())) {
                return node;
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                TSNode found = firstDescendantOfType(node.getNamedChild(index), types);
                if (found != null && !found.isNull()) {
                    return found;
                }
            }
            return null;
        }

        private String switchSelector(TSNode node) {
            for (String fieldName : List.of("condition", "value", "argument", "scrutinee")) {
                TSNode child = node.getChildByFieldName(fieldName);
                if (child != null && !child.isNull()) {
                    return normalize(text(child));
                }
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                TSNode child = node.getNamedChild(index);
                String type = child.getType();
                if (!type.contains("case")
                        && !type.contains("arm")
                        && !type.contains("body")
                        && !type.contains("block")) {
                    return normalize(text(child));
                }
            }
            return normalize(text(node));
        }

        private List<String> switchLabels(TSNode node) {
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            collectSwitchLabels(node, labels);
            return List.copyOf(labels);
        }

        private void collectSwitchLabels(TSNode node, LinkedHashSet<String> labels) {
            if (switchLabelNode(node)) {
                String label = switchLabel(node);
                if (!label.isBlank() && !"default".equalsIgnoreCase(label)) {
                    labels.add(label);
                }
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                collectSwitchLabels(node.getNamedChild(index), labels);
            }
        }

        private boolean switchHasDefault(TSNode node) {
            String lower = normalize(text(node)).toLowerCase(Locale.ROOT);
            return lower.contains("default") || lower.contains("else");
        }

        private Optional<BranchDispatchInfo> ifElseDispatch(TSNode node) {
            String nodeText = normalize(text(node));
            List<String> conditions = new ArrayList<>();
            collectIfConditionTexts(node, conditions);
            String normalized = String.join(" ", conditions);
            LinkedHashSet<ConditionBranch> branches = new LinkedHashSet<>();
            collectEqualityBranches(normalized, branches);
            if (branches.size() < 2) {
                return Optional.empty();
            }
            Map<String, LinkedHashSet<String>> labelsBySelector = new java.util.LinkedHashMap<>();
            for (ConditionBranch branch : branches) {
                labelsBySelector.computeIfAbsent(branch.selector(), ignored -> new LinkedHashSet<>())
                        .add(branch.label());
            }
            return labelsBySelector.entrySet().stream()
                    .filter(entry -> entry.getValue().size() >= 2)
                    .max((left, right) -> Integer.compare(left.getValue().size(), right.getValue().size()))
                    .map(entry -> new BranchDispatchInfo(
                            "if_else_dispatch",
                            entry.getKey(),
                            List.copyOf(entry.getValue()),
                            nodeText.matches("(?is).*\\belse\\b(?!\\s+if).*"),
                            startLine(node),
                            endLine(node)
                    ));
        }

        private void collectIfConditionTexts(TSNode node, List<String> conditions) {
            if (node.getType().equals("if_statement")
                    || node.getType().equals("if_expression")
                    || node.getType().equals("elif_clause")) {
                TSNode condition = node.getChildByFieldName("condition");
                if (condition != null && !condition.isNull()) {
                    conditions.add(normalize(text(condition)));
                }
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                collectIfConditionTexts(node.getNamedChild(index), conditions);
            }
        }

        private static void collectEqualityBranches(String text, LinkedHashSet<ConditionBranch> branches) {
            Pattern direct = Pattern.compile(
                    "\\b([A-Za-z_$][A-Za-z0-9_$.]*)\\s*(?:===|==|!=|!==|=)\\s*(\"[^\"]+\"|'[^']+'|[A-Z_$][A-Z0-9_$]+|\\d+)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher directMatcher = direct.matcher(text);
            while (directMatcher.find()) {
                addConditionBranch(branches, directMatcher.group(1), directMatcher.group(2));
            }
            Pattern reversed = Pattern.compile(
                    "(\"[^\"]+\"|'[^']+'|[A-Z_$][A-Z0-9_$]+|\\d+)\\s*(?:===|==|!=|!==|=)\\s*\\b([A-Za-z_$][A-Za-z0-9_$.]*)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher reversedMatcher = reversed.matcher(text);
            while (reversedMatcher.find()) {
                addConditionBranch(branches, reversedMatcher.group(2), reversedMatcher.group(1));
            }
            Pattern equalsCall = Pattern.compile(
                    "\\b([A-Za-z_$][A-Za-z0-9_$.]*)\\s*\\.\\s*(?:equals|equalsIgnoreCase)\\s*\\(\\s*(\"[^\"]+\"|'[^']+'|[A-Z_$][A-Z0-9_$]+|\\d+)\\s*\\)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher equalsMatcher = equalsCall.matcher(text);
            while (equalsMatcher.find()) {
                addConditionBranch(branches, equalsMatcher.group(1), equalsMatcher.group(2));
            }
        }

        private static void addConditionBranch(LinkedHashSet<ConditionBranch> branches, String selector, String label) {
            String normalizedSelector = normalizeSelector(selector);
            String normalizedLabel = cleanSwitchLabel(label);
            if (!normalizedSelector.isBlank() && !normalizedLabel.isBlank() && !normalizedSelector.equals(normalizedLabel)) {
                branches.add(new ConditionBranch(normalizedSelector, normalizedLabel));
            }
        }

        private boolean switchLabelNode(TSNode node) {
            String type = node.getType();
            return type.equals("switch_case")
                    || type.equals("case_statement")
                    || type.equals("case")
                    || type.equals("when")
                    || type.equals("match_arm");
        }

        private String switchLabel(TSNode node) {
            for (String fieldName : List.of("value", "condition", "pattern")) {
                TSNode child = node.getChildByFieldName(fieldName);
                if (child != null && !child.isNull()) {
                    return cleanSwitchLabel(text(child));
                }
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                TSNode child = node.getNamedChild(index);
                String type = child.getType();
                if (!type.contains("body") && !type.contains("block") && !type.contains("statement")) {
                    return cleanSwitchLabel(text(child));
                }
            }
            return cleanSwitchLabel(text(node));
        }

        private static String normalizeSelector(String value) {
            String selector = normalize(value)
                    .replaceFirst("^this\\.", "")
                    .replaceFirst("^self\\.", "")
                    .replaceAll("\\s+", "");
            if (selector.startsWith("(") && selector.endsWith(")") && selector.length() > 2) {
                selector = selector.substring(1, selector.length() - 1);
            }
            return selector;
        }

        private static String cleanSwitchLabel(String value) {
            String cleaned = normalize(value)
                    .replaceFirst("^(case|when)\\s+", "")
                    .replaceFirst("\\s*(=>|:).*", "")
                    .trim();
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot >= 0 && lastDot + 1 < cleaned.length()) {
                cleaned = cleaned.substring(lastDot + 1);
            }
            if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                    || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            return cleaned;
        }

        private String nameFor(TSNode node, String fallback) {
            TSNode name = node.getChildByFieldName("name");
            if (name != null && !name.isNull()) {
                return cleanName(text(name), fallback);
            }
            for (int index = 0; index < node.getNamedChildCount(); index++) {
                TSNode child = node.getNamedChild(index);
                if (child.getType().equals("identifier")
                        || child.getType().equals("property_identifier")
                        || child.getType().equals("constant")
                        || child.getType().equals("type_identifier")) {
                    return cleanName(text(child), fallback);
                }
            }
            return fallback;
        }

        private String cleanName(String value, String fallback) {
            String cleaned = value.replaceAll("[^A-Za-z0-9_$#@-]", "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }

        private String text(TSNode node) {
            int start = Math.max(0, node.getStartByte());
            int end = Math.min(sourceBytes.length, node.getEndByte());
            if (end < start) {
                return "";
            }
            return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
        }

        private int startLine(TSNode node) {
            return node.getStartPoint().getRow() + 1;
        }

        private int endLine(TSNode node) {
            return node.getEndPoint().getRow() + 1;
        }

        private boolean isFunctionNode(String type) {
            return FUNCTION_NODES.contains(type)
                    || (type.contains("function") && !type.contains("call"))
                    || type.contains("method_declaration")
                    || type.contains("method_definition");
        }

        private boolean isClassNode(String type) {
            return CLASS_NODES.contains(type)
                    || type.contains("class_declaration")
                    || type.contains("struct")
                    || type.contains("interface_declaration");
        }

        private boolean isRootNode(String type) {
            return type.equals("source_file")
                    || type.equals("program")
                    || type.equals("translation_unit")
                    || type.equals("stylesheet");
        }

        private static String normalize(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").trim();
        }

        private static String rootName(String value) {
            String normalized = value.trim();
            int stop = normalized.length();
            for (String delimiter : List.of(".", "(", "[", "->", "::")) {
                int index = normalized.indexOf(delimiter);
                if (index >= 0) {
                    stop = Math.min(stop, index);
                }
            }
            if (stop <= 0) {
                return null;
            }
            String root = normalized.substring(0, stop).trim().toLowerCase(Locale.ROOT);
            return root.matches("[a-zA-Z_$][a-zA-Z0-9_$]*") ? root : null;
        }

        private static String callName(String value) {
            String normalized = value.trim();
            int argumentStart = normalized.indexOf('(');
            if (argumentStart >= 0) {
                normalized = normalized.substring(0, argumentStart);
            }
            normalized = normalized.trim();
            int dot = normalized.lastIndexOf('.');
            int scope = normalized.lastIndexOf("::");
            int delimiter = Math.max(dot, scope);
            if (delimiter >= 0 && delimiter + 1 < normalized.length()) {
                normalized = normalized.substring(delimiter + 1);
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("[A-Za-z_$][A-Za-z0-9_$]*$")
                    .matcher(normalized);
            return matcher.find() ? matcher.group() : null;
        }

        private Optional<MethodCallInfo> methodCallInfo(String value, int line) {
            String methodName = callName(value);
            if (methodName == null || methodName.isBlank()) {
                return Optional.empty();
            }
            String receiverExpression = receiverExpression(value);
            String receiverRoot = receiverRoot(receiverExpression);
            String receiverKind = receiverKind(receiverRoot, receiverExpression);
            return Optional.of(new MethodCallInfo(
                    receiverExpression,
                    receiverRoot,
                    receiverKind,
                    receiverType(receiverRoot, receiverKind),
                    methodName,
                    argumentCount(value),
                    line
            ));
        }

        private String receiverKind(String receiverRoot, String receiverExpression) {
            if (receiverExpression.isBlank()) {
                return "self";
            }
            if ("this".equals(receiverRoot) || "self".equals(receiverRoot) || "super".equals(receiverRoot)) {
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

        private String receiverType(String receiverRoot, String receiverKind) {
            if ("self".equals(receiverKind)) {
                return classStack.isEmpty() ? "" : classStack.peek().name();
            }
            if ("field".equals(receiverKind)) {
                JavaFieldInfo field = fieldInCurrentClass(receiverRoot);
                return field == null ? "" : field.type();
            }
            if (currentMethod != null && ("parameter".equals(receiverKind) || "local".equals(receiverKind))) {
                return currentMethod.variableTypes().getOrDefault(receiverRoot, "");
            }
            if ("static".equals(receiverKind)) {
                return receiverRoot;
            }
            return "";
        }

        private static String receiverExpression(String value) {
            String normalized = normalize(value);
            int argumentStart = normalized.indexOf('(');
            if (argumentStart >= 0) {
                normalized = normalized.substring(0, argumentStart).trim();
            }
            int dot = normalized.lastIndexOf('.');
            int scope = normalized.lastIndexOf("::");
            int delimiter = Math.max(dot, scope);
            if (delimiter <= 0) {
                return "";
            }
            return normalized.substring(0, delimiter).trim();
        }

        private static String receiverRoot(String receiverExpression) {
            String normalized = receiverExpression.trim();
            if (normalized.startsWith("this.") || normalized.startsWith("self.")) {
                normalized = normalized.substring(normalized.indexOf('.') + 1);
            }
            int dot = normalized.indexOf('.');
            int scope = normalized.indexOf("::");
            int delimiter = dot < 0 ? scope : scope < 0 ? dot : Math.min(dot, scope);
            if (delimiter >= 0) {
                normalized = normalized.substring(0, delimiter);
            }
            Matcher matcher = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*$").matcher(normalized);
            return matcher.find() ? matcher.group() : "";
        }

        private static int argumentCount(String value) {
            int start = value.indexOf('(');
            int end = value.lastIndexOf(')');
            if (start < 0 || end <= start) {
                return 0;
            }
            String arguments = value.substring(start + 1, end).trim();
            if (arguments.isBlank()) {
                return 0;
            }
            int count = 1;
            int parenDepth = 0;
            int bracketDepth = 0;
            boolean inString = false;
            char quote = 0;
            for (int index = 0; index < arguments.length(); index++) {
                char character = arguments.charAt(index);
                if (inString) {
                    if (character == quote && (index == 0 || arguments.charAt(index - 1) != '\\')) {
                        inString = false;
                    }
                    continue;
                }
                if (character == '"' || character == '\'') {
                    inString = true;
                    quote = character;
                } else if (character == '(') {
                    parenDepth++;
                } else if (character == ')' && parenDepth > 0) {
                    parenDepth--;
                } else if (character == '[' || character == '{') {
                    bracketDepth++;
                } else if ((character == ']' || character == '}') && bracketDepth > 0) {
                    bracketDepth--;
                } else if (character == ',' && parenDepth == 0 && bracketDepth == 0) {
                    count++;
                }
            }
            return count;
        }

        private record ConditionBranch(String selector, String label) {
        }
    }
}
