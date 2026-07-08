package com.codex.refactor.analysis;

import java.util.HashSet;
import java.util.Set;

public final class JavaFieldInfo {
    private final String name;
    private final String type;
    private final String ownerClass;
    private final boolean publicField;
    private final boolean staticField;
    private final boolean finalField;
    private final int startLine;
    private final int endLine;
    private final Set<String> readByMethods = new HashSet<>();
    private final Set<String> assignedByMethods = new HashSet<>();

    public JavaFieldInfo(
            String name,
            String type,
            String ownerClass,
            boolean publicField,
            boolean staticField,
            boolean finalField,
            int startLine,
            int endLine
    ) {
        this.name = name;
        this.type = type;
        this.ownerClass = ownerClass;
        this.publicField = publicField;
        this.staticField = staticField;
        this.finalField = finalField;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String ownerClass() {
        return ownerClass;
    }

    public boolean publicField() {
        return publicField;
    }

    public boolean staticField() {
        return staticField;
    }

    public boolean finalField() {
        return finalField;
    }

    public int startLine() {
        return startLine;
    }

    public int endLine() {
        return endLine;
    }

    public Set<String> readByMethods() {
        return readByMethods;
    }

    public Set<String> assignedByMethods() {
        return assignedByMethods;
    }

    public void markRead(String methodName) {
        if (methodName != null) {
            readByMethods.add(methodName);
        }
    }

    public void markAssigned(String methodName) {
        if (methodName != null) {
            assignedByMethods.add(methodName);
        }
    }
}
