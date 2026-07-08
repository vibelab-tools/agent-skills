package com.codex.refactor.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaMethodInfo {
    private final String name;
    private final String ownerClass;
    private final String returnType;
    private final boolean constructor;
    private final int startLine;
    private final int endLine;
    private final List<String> parameterNames = new ArrayList<>();
    private final List<String> parameterTypes = new ArrayList<>();
    private final Set<String> localVariables = new HashSet<>();
    private final Set<String> ownFieldReads = new HashSet<>();
    private final Set<String> ownFieldWrites = new HashSet<>();
    private final Map<String, String> variableTypes = new LinkedHashMap<>();
    private final Map<String, Integer> ownFieldReadCounts = new LinkedHashMap<>();
    private final Map<String, Integer> ownFieldWriteCounts = new LinkedHashMap<>();
    private final Map<String, Integer> foreignMemberAccessCounts = new LinkedHashMap<>();
    private final Map<String, Set<Integer>> foreignMemberAccessLines = new LinkedHashMap<>();
    private final Map<String, Integer> methodCallCounts = new LinkedHashMap<>();
    private final List<MethodCallInfo> methodCalls = new ArrayList<>();
    private final List<String> statementShapes = new ArrayList<>();
    private final List<String> switchSelectors = new ArrayList<>();
    private final List<BranchDispatchInfo> branchDispatches = new ArrayList<>();
    private final List<Integer> loopLines = new ArrayList<>();
    private final List<Integer> chainLines = new ArrayList<>();
    private final List<MessageChainInfo> messageChains = new ArrayList<>();
    private final List<DelegationInfo> delegations = new ArrayList<>();
    private final List<String> thrownTypes = new ArrayList<>();
    private String normalizedBody = "";
    private int cyclomaticComplexity = 1;
    private int cognitiveComplexity;
    private int maxNestingDepth;
    private int maxMessageChainDepth;
    private boolean accessorMethod;
    private boolean simpleDelegation;
    private boolean throwsUnsupportedOperation;
    private boolean overrideAnnotation;

    public JavaMethodInfo(
            String name,
            String ownerClass,
            String returnType,
            boolean constructor,
            int startLine,
            int endLine
    ) {
        this.name = name;
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.constructor = constructor;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String name() {
        return name;
    }

    public String ownerClass() {
        return ownerClass;
    }

    public String returnType() {
        return returnType;
    }

    public boolean constructor() {
        return constructor;
    }

    public int startLine() {
        return startLine;
    }

    public int endLine() {
        return endLine;
    }

    public int physicalLines() {
        return Math.max(1, endLine - startLine + 1);
    }

    public List<String> parameterNames() {
        return parameterNames;
    }

    public List<String> parameterTypes() {
        return parameterTypes;
    }

    public Set<String> localVariables() {
        return localVariables;
    }

    public Map<String, String> variableTypes() {
        return variableTypes;
    }

    public Set<String> ownFieldReads() {
        return ownFieldReads;
    }

    public Set<String> ownFieldWrites() {
        return ownFieldWrites;
    }

    public Map<String, Integer> ownFieldReadCounts() {
        return ownFieldReadCounts;
    }

    public Map<String, Integer> ownFieldWriteCounts() {
        return ownFieldWriteCounts;
    }

    public Map<String, Integer> foreignMemberAccessCounts() {
        return foreignMemberAccessCounts;
    }

    public Map<String, Set<Integer>> foreignMemberAccessLines() {
        return foreignMemberAccessLines;
    }

    public Map<String, Integer> methodCallCounts() {
        return methodCallCounts;
    }

    public List<MethodCallInfo> methodCalls() {
        return methodCalls;
    }

    public List<String> statementShapes() {
        return statementShapes;
    }

    public List<String> switchSelectors() {
        return switchSelectors;
    }

    public List<BranchDispatchInfo> branchDispatches() {
        return branchDispatches;
    }

    public void recordBranchDispatch(BranchDispatchInfo dispatch) {
        branchDispatches.add(dispatch);
    }

    public List<Integer> loopLines() {
        return loopLines;
    }

    public List<Integer> chainLines() {
        return chainLines;
    }

    public List<MessageChainInfo> messageChains() {
        return messageChains;
    }

    public List<DelegationInfo> delegations() {
        return delegations;
    }

    public void recordDelegation(DelegationInfo delegation) {
        delegations.add(delegation);
    }

    public List<String> thrownTypes() {
        return thrownTypes;
    }

    public String normalizedBody() {
        return normalizedBody;
    }

    public void setNormalizedBody(String normalizedBody) {
        this.normalizedBody = normalizedBody;
    }

    public int cyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void incrementCyclomaticComplexity() {
        cyclomaticComplexity++;
    }

    public int cognitiveComplexity() {
        return cognitiveComplexity;
    }

    public void addCognitiveComplexity(int amount) {
        cognitiveComplexity += amount;
    }

    public int maxNestingDepth() {
        return maxNestingDepth;
    }

    public void recordNestingDepth(int depth) {
        maxNestingDepth = Math.max(maxNestingDepth, depth);
    }

    public int maxMessageChainDepth() {
        return maxMessageChainDepth;
    }

    public void recordMessageChainDepth(int depth, int line) {
        maxMessageChainDepth = Math.max(maxMessageChainDepth, depth);
        if (depth >= 4) {
            chainLines.add(line);
        }
    }

    public void recordMessageChain(MessageChainInfo chain) {
        messageChains.add(chain);
        if (chain.objectNavigation()) {
            recordMessageChainDepth(chain.depth(), chain.line());
        }
    }

    public boolean accessorMethod() {
        return accessorMethod;
    }

    public void setAccessorMethod(boolean accessorMethod) {
        this.accessorMethod = accessorMethod;
    }

    public boolean simpleDelegation() {
        return simpleDelegation;
    }

    public void setSimpleDelegation(boolean simpleDelegation) {
        this.simpleDelegation = simpleDelegation;
    }

    public boolean throwsUnsupportedOperation() {
        return throwsUnsupportedOperation;
    }

    public void setThrowsUnsupportedOperation(boolean throwsUnsupportedOperation) {
        this.throwsUnsupportedOperation = throwsUnsupportedOperation;
    }

    public boolean overrideAnnotation() {
        return overrideAnnotation;
    }

    public void setOverrideAnnotation(boolean overrideAnnotation) {
        this.overrideAnnotation = overrideAnnotation;
    }

    public void addParameter(String type, String name) {
        parameterTypes.add(type);
        parameterNames.add(name);
        variableTypes.put(name, type);
    }

    public void addLocalVariable(String type, String name) {
        localVariables.add(name);
        variableTypes.put(name, type);
    }

    public void recordOwnFieldRead(String fieldName) {
        ownFieldReads.add(fieldName);
        ownFieldReadCounts.merge(fieldName, 1, Integer::sum);
    }

    public void recordOwnFieldWrite(String fieldName) {
        ownFieldWrites.add(fieldName);
        ownFieldWriteCounts.merge(fieldName, 1, Integer::sum);
    }

    public void recordForeignMemberAccess(String root, int line) {
        foreignMemberAccessCounts.merge(root, 1, Integer::sum);
        foreignMemberAccessLines.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(line);
    }

    public void recordMethodCall(MethodCallInfo call) {
        methodCallCounts.merge(call.methodName(), 1, Integer::sum);
        methodCalls.add(call);
    }
}
