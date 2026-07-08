package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MysteriousNameBadSmellDetector extends BookBadSmellDetector {
    private static final Set<String> ACCEPTABLE_SHORT_PARAMETER_NAMES = Set.of(
            "id", "x", "y", "z", "dx", "dy", "ok", "io", "db", "ui"
    );
    private static final Set<String> ACCEPTABLE_SHORT_LOCAL_NAMES = Set.of(
            "i", "j", "k", "n", "x", "y", "z", "r", "ok"
    );
    private static final Set<String> GENERIC_NAMES = Set.of(
            "data", "info", "stuff", "thing", "tmp", "temp", "foo", "bar", "obj", "mgr", "misc", "helper"
    );

    public MysteriousNameBadSmellDetector() {
        super(BadSmell.MYSTERIOUS_NAME);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        context.analysis().classes().forEach(classInfo -> {
            if (DetectorSupport.poorName(classInfo.name())) {
                findings.add(DetectorSupport.finding(
                        smell(), "medium", "medium", classInfo.name(), classInfo.startLine(), classInfo.endLine(),
                        DetectorSupport.evidence("name", classInfo.name(), "kind", "class"),
                        "Class name is too short or generic to communicate intent.",
                        "Rename the class so it describes the responsibility it owns."
                ));
            }
        });
        context.analysis().methods().forEach(method -> {
            if (!method.constructor() && DetectorSupport.poorName(method.name())) {
                findings.add(DetectorSupport.finding(
                        smell(), "medium", "medium", method.name(), method.startLine(), method.endLine(),
                        DetectorSupport.evidence("name", method.name(), "kind", "method"),
                        "Method name is too short or generic to communicate intent.",
                        "Rename the method after the observable behavior it provides."
                ));
            }
            parameterFindings(method).forEach(findings::add);
            localVariableFindings(method).forEach(findings::add);
        });
        context.analysis().fields().forEach(field -> {
            if (DetectorSupport.poorName(field.name())) {
                findings.add(DetectorSupport.finding(
                        smell(), "low", "medium", field.name(), field.startLine(), field.endLine(),
                        DetectorSupport.evidence("name", field.name(), "kind", "field"),
                        "Field name is too short or generic to communicate its role.",
                        "Rename the field to describe the domain concept or stored value."
                ));
            }
        });
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private List<SmellFinding> parameterFindings(JavaMethodInfo method) {
        List<SmellFinding> findings = new ArrayList<>();
        for (String parameterName : method.parameterNames()) {
            if (poorParameterName(parameterName, method)) {
                findings.add(DetectorSupport.finding(
                        smell(), "low", "medium", parameterName, method.startLine(), method.startLine(),
                        DetectorSupport.evidence(
                                "name", parameterName,
                                "kind", "parameter",
                                "owner", method.ownerClass() + "." + method.name()
                        ),
                        "Parameter name is too short or generic to communicate its role.",
                        "Rename the parameter to describe the value expected by the method."
                ));
            }
        }
        return findings;
    }

    private List<SmellFinding> localVariableFindings(JavaMethodInfo method) {
        return method.localVariables().stream()
                .filter(name -> poorLocalName(name, method))
                .map(name -> DetectorSupport.finding(
                        smell(), "low", "medium", name, method.startLine(), method.endLine(),
                        DetectorSupport.evidence(
                                "name", name,
                                "kind", "local_variable",
                                "owner", method.ownerClass() + "." + method.name()
                        ),
                        "Local variable name is too short or generic to communicate its role in this method.",
                        "Rename the local variable to describe the intermediate value it holds."
                ))
                .toList();
    }

    private static boolean poorParameterName(String name, JavaMethodInfo method) {
        String lower = normalize(name);
        if (ACCEPTABLE_SHORT_PARAMETER_NAMES.contains(lower)) {
            return false;
        }
        return GENERIC_NAMES.contains(lower)
                || (name.length() <= 1 && (method.parameterNames().size() >= 2 || method.physicalLines() >= 4));
    }

    private static boolean poorLocalName(String name, JavaMethodInfo method) {
        String lower = normalize(name);
        if (ACCEPTABLE_SHORT_LOCAL_NAMES.contains(lower) && method.physicalLines() <= 8) {
            return false;
        }
        return GENERIC_NAMES.contains(lower)
                || (name.length() <= 1 && method.physicalLines() >= 6);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
