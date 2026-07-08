package com.codex.refactor.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JavaClassInfo {
    private final String name;
    private final String extendsName;
    private final boolean interfaceType;
    private final boolean abstractType;
    private final int startLine;
    private final int endLine;
    private final List<JavaFieldInfo> fields = new ArrayList<>();
    private final List<JavaMethodInfo> methods = new ArrayList<>();
    private final Set<String> implementsNames = new HashSet<>();

    public JavaClassInfo(
            String name,
            String extendsName,
            boolean interfaceType,
            boolean abstractType,
            int startLine,
            int endLine
    ) {
        this.name = name;
        this.extendsName = extendsName;
        this.interfaceType = interfaceType;
        this.abstractType = abstractType;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String name() {
        return name;
    }

    public String extendsName() {
        return extendsName;
    }

    public boolean interfaceType() {
        return interfaceType;
    }

    public boolean abstractType() {
        return abstractType;
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

    public List<JavaFieldInfo> fields() {
        return fields;
    }

    public List<JavaMethodInfo> methods() {
        return methods;
    }

    public Set<String> implementsNames() {
        return implementsNames;
    }

    public void addField(JavaFieldInfo field) {
        fields.add(field);
    }

    public void addMethod(JavaMethodInfo method) {
        methods.add(method);
    }

    public void addImplement(String implementName) {
        implementsNames.add(implementName);
    }

    public long behavioralMethodCount() {
        return methods.stream()
                .filter(method -> !method.accessorMethod())
                .filter(method -> !method.constructor())
                .count();
    }
}
