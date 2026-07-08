package com.codex.refactor.smell.detectors;

import com.codex.refactor.analysis.DelegationInfo;
import com.codex.refactor.analysis.JavaClassInfo;
import com.codex.refactor.analysis.JavaFieldInfo;
import com.codex.refactor.analysis.JavaMethodInfo;
import com.codex.refactor.analysis.SourceProjectIndex;
import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MiddleManBadSmellDetector extends BookBadSmellDetector {
    public MiddleManBadSmellDetector() {
        super(BadSmell.MIDDLE_MAN);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        context.analysis().classes().forEach(classInfo ->
                candidate(classInfo, context).ifPresent(candidate -> findings.add(finding(candidate))));
        if (!findings.isEmpty()) {
            return findings;
        }
        if ("java".equals(context.analysis().language()) && !context.analysis().classes().isEmpty()) {
            return findings;
        }
        return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
    }

    private SmellFinding finding(MiddleManCandidate candidate) {
        return DetectorSupport.finding(
                smell(),
                candidate.severity(),
                candidate.confidence(),
                candidate.classInfo().name(),
                candidate.classInfo().startLine(),
                candidate.classInfo().endLine(),
                candidate.evidence(),
                "Most behavioral methods simply forward work to another object.",
                "Remove Middle Man by letting callers use the delegate directly where appropriate, or give this facade real coordination behavior."
        );
    }

    private static Optional<MiddleManCandidate> candidate(JavaClassInfo classInfo, SmellAnalysisContext context) {
        List<JavaMethodInfo> behavioralMethods = classInfo.methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> !method.accessorMethod())
                .toList();
        if (behavioralMethods.size() < 3) {
            return Optional.empty();
        }

        List<ForwardingMethod> forwardingMethods = behavioralMethods.stream()
                .flatMap(method -> fieldDelegations(method).stream())
                .toList();
        if (forwardingMethods.size() < 3) {
            return Optional.empty();
        }

        double delegationRatio = (double) forwardingMethods.size() / behavioralMethods.size();
        Map<String, Long> byDelegate = forwardingMethods.stream()
                .collect(Collectors.groupingBy(
                        forwarding -> forwarding.delegation().delegateRoot(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map.Entry<String, Long> dominant = byDelegate.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .orElseThrow();
        double dominantDelegateRatio = (double) dominant.getValue() / forwardingMethods.size();
        double passThroughRatio = forwardingMethods.stream()
                .mapToDouble(forwarding -> forwarding.delegation().passThroughRatio())
                .average()
                .orElse(0.0);
        long sameNameForwarders = forwardingMethods.stream()
                .filter(forwarding -> forwarding.delegation().sameName())
                .count();
        int valueAddedMethods = behavioralMethods.size() - forwardingMethods.size();
        int score = score(
                behavioralMethods.size(),
                forwardingMethods.size(),
                delegationRatio,
                dominantDelegateRatio,
                passThroughRatio,
                sameNameForwarders,
                valueAddedMethods
        );
        if (delegationRatio < 0.67 || dominantDelegateRatio < 0.67 || score < 4) {
            return Optional.empty();
        }
        DelegateResolution delegateResolution = DelegateResolution.from(
                classInfo,
                dominant.getKey(),
                forwardingMethods,
                context.projectIndex()
        );
        return Optional.of(new MiddleManCandidate(
                classInfo,
                behavioralMethods,
                forwardingMethods,
                dominant.getKey(),
                delegationRatio,
                dominantDelegateRatio,
                passThroughRatio,
                sameNameForwarders,
                valueAddedMethods,
                score,
                delegateResolution
        ));
    }

    private static List<ForwardingMethod> fieldDelegations(JavaMethodInfo method) {
        return method.delegations().stream()
                .filter(DelegationInfo::fieldDelegate)
                .filter(delegation -> method.cyclomaticComplexity() <= 1)
                .filter(delegation -> method.ownFieldWrites().isEmpty())
                .map(delegation -> new ForwardingMethod(method, delegation))
                .toList();
    }

    private static int score(
            int totalMethods,
            int forwardingMethods,
            double delegationRatio,
            double dominantDelegateRatio,
            double passThroughRatio,
            long sameNameForwarders,
            int valueAddedMethods
    ) {
        int score = 0;
        score += delegationRatio >= 0.85 ? 3 : delegationRatio >= 0.67 ? 2 : 0;
        score += dominantDelegateRatio >= 0.85 ? 2 : dominantDelegateRatio >= 0.67 ? 1 : 0;
        score += passThroughRatio >= 0.80 ? 1 : 0;
        score += sameNameForwarders >= Math.max(2, forwardingMethods / 2) ? 1 : 0;
        score += totalMethods >= 5 ? 1 : 0;
        score -= valueAddedMethods >= 2 ? 2 : 0;
        return score;
    }

    private record ForwardingMethod(JavaMethodInfo method, DelegationInfo delegation) {
        Map<String, Object> toJson() {
            return DetectorSupport.evidence(
                    "method", method.ownerClass() + "." + method.name(),
                    "delegate", delegation.delegateRoot(),
                    "target_method", delegation.targetMethod(),
                    "returns_delegate_result", delegation.returnsDelegateResult(),
                    "same_name", delegation.sameName(),
                    "arguments", delegation.arguments(),
                    "pass_through_argument_count", delegation.passThroughArgumentCount(),
                    "parameter_count", delegation.parameterCount(),
                    "pass_through_ratio", delegation.passThroughRatio(),
                    "start_line", delegation.startLine(),
                    "end_line", delegation.endLine()
            );
        }
    }

    private record MiddleManCandidate(
            JavaClassInfo classInfo,
            List<JavaMethodInfo> behavioralMethods,
            List<ForwardingMethod> forwardingMethods,
            String dominantDelegate,
            double delegationRatio,
            double dominantDelegateRatio,
            double passThroughRatio,
            long sameNameForwarders,
            int valueAddedMethods,
            int score,
            DelegateResolution delegateResolution
    ) {
        String severity() {
            return delegationRatio >= 0.85 && forwardingMethods.size() >= 4 ? "high" : "medium";
        }

        String confidence() {
            return (dominantDelegateRatio >= 0.80 && forwardingMethods.size() >= 3)
                    || delegateResolution.resolvedForwardingCallCount() >= 3 ? "high" : "medium";
        }

        Map<String, Object> evidence() {
            return DetectorSupport.evidence(
                    "signal", "mostly_forwarding_class",
                    "delegation_methods", forwardingMethods.size(),
                    "total_behavioral_methods", behavioralMethods.size(),
                    "delegation_ratio", delegationRatio,
                    "dominant_delegate", dominantDelegate,
                    "dominant_delegate_ratio", dominantDelegateRatio,
                    "pass_through_ratio", passThroughRatio,
                    "same_name_forwarding_methods", sameNameForwarders,
                    "value_added_method_count", valueAddedMethods,
                    "middle_man_score", score,
                    "resolved_delegate_type", delegateResolution.delegateType(),
                    "resolved_delegate_type_path", delegateResolution.delegatePath().map(Path::toString).orElse(""),
                    "resolved_forwarding_call_count", delegateResolution.resolvedForwardingCallCount(),
                    "resolved_forwarding_targets", delegateResolution.resolvedTargets(),
                    "forwarding_methods", forwardingMethods.stream().map(ForwardingMethod::toJson).toList()
            );
        }
    }

    private record DelegateResolution(
            String delegateType,
            Optional<Path> delegatePath,
            long resolvedForwardingCallCount,
            List<String> resolvedTargets
    ) {
        static DelegateResolution from(
                JavaClassInfo classInfo,
                String dominantDelegate,
                List<ForwardingMethod> forwardingMethods,
                SourceProjectIndex projectIndex
        ) {
            String delegateType = classInfo.fields().stream()
                    .filter(field -> field.name().equals(dominantDelegate))
                    .map(JavaFieldInfo::type)
                    .findFirst()
                    .map(SourceProjectIndex::simpleTypeName)
                    .orElse("");
            Optional<Path> delegatePath = delegateType.isBlank()
                    ? Optional.empty()
                    : projectIndex.pathForClass(delegateType);
            List<String> resolvedTargets = forwardingMethods.stream()
                    .flatMap(forwarding -> projectIndex.callEdgesFrom(forwarding.method()).stream()
                            .filter(edge -> forwarding.delegation().delegateRoot().equals(edge.call().receiverRoot()))
                            .filter(edge -> forwarding.delegation().targetMethod().equals(edge.call().methodName()))
                            .filter(SourceProjectIndex.CallEdge::resolved)
                            .filter(edge -> delegateType.isBlank()
                                    || SourceProjectIndex.simpleTypeName(edge.targetOwner()).equals(delegateType))
                            .map(edge -> edge.targetOwner() + "." + edge.targetMethod()))
                    .distinct()
                    .toList();
            return new DelegateResolution(
                    delegateType,
                    delegatePath,
                    resolvedTargets.size(),
                    resolvedTargets
            );
        }
    }
}
