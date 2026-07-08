package com.codex.refactor.smell.detectors;

import com.codex.refactor.smell.BadSmell;
import com.codex.refactor.smell.BookBadSmellDetector;
import com.codex.refactor.smell.SmellAnalysisContext;
import com.codex.refactor.smell.SmellFinding;
import com.codex.refactor.analysis.MessageChainInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MessageChainsBadSmellDetector extends BookBadSmellDetector {
    public MessageChainsBadSmellDetector() {
        super(BadSmell.MESSAGE_CHAINS);
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = context.analysis().methods().stream()
                .map(this::findingFor)
                .flatMap(Optional::stream)
                .toList();
        if (findings.isEmpty() && context.analysis().methods().stream().allMatch(method -> method.messageChains().isEmpty())) {
            return DetectorSupport.fallbackIfEmpty(findings, smell(), context);
        }
        return findings;
    }

    private Optional<SmellFinding> findingFor(com.codex.refactor.analysis.JavaMethodInfo method) {
        List<MessageChainInfo> chains = relevantChains(method);
        if (chains.isEmpty()) {
            return Optional.empty();
        }

        MessageChainInfo longest = chains.stream()
                .max(Comparator.comparingInt(MessageChainInfo::depth))
                .orElseThrow();
        RepeatedPrefix repeatedPrefix = repeatedPrefix(chains).orElse(null);
        boolean longChain = longest.depth() >= 4;
        boolean repeatedMediumChain = repeatedPrefix != null && repeatedPrefix.depth() >= 3;
        if (!longChain && !repeatedMediumChain) {
            return Optional.empty();
        }

        String signal = longChain ? "long_object_navigation_chain" : "repeated_chain_prefix";
        int maxDepth = longest.depth();
        List<Integer> lines = chains.stream()
                .filter(chain -> longChain ? chain.depth() >= 4 : chain.prefixKey(repeatedPrefix.depth()).equals(repeatedPrefix.prefix()))
                .map(MessageChainInfo::line)
                .distinct()
                .sorted()
                .toList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("signal", signal);
        evidence.put("max_chain_depth", maxDepth);
        evidence.put("chain_lines", lines);
        evidence.put("chains", representativeChains(chains).stream().map(MessageChainInfo::toJson).toList());
        evidence.put("ignored_chain_kinds", ignoredKinds(method));
        if (repeatedPrefix != null) {
            evidence.put("repeated_prefix", repeatedPrefix.prefix());
            evidence.put("repeated_prefix_depth", repeatedPrefix.depth());
            evidence.put("repeated_prefix_occurrences", repeatedPrefix.occurrences());
            evidence.put("repeated_prefix_line_count", repeatedPrefix.lineCount());
        }

        return Optional.of(DetectorSupport.finding(
                smell(),
                maxDepth >= 5 || (repeatedPrefix != null && repeatedPrefix.occurrences() >= 3) ? "high" : "medium",
                "high",
                method.name(),
                method.startLine(),
                method.endLine(),
                evidence,
                "Method reaches through object structure instead of asking a nearer collaborator for the needed result.",
                "Hide Delegate or introduce an intention-revealing query on the nearer collaborator."
        ));
    }

    private static List<MessageChainInfo> relevantChains(com.codex.refactor.analysis.JavaMethodInfo method) {
        return method.messageChains().stream()
                .filter(MessageChainInfo::objectNavigation)
                .toList();
    }

    private static List<MessageChainInfo> representativeChains(List<MessageChainInfo> chains) {
        return chains.stream()
                .collect(Collectors.toMap(
                        chain -> chain.line() + ":" + chain.chainText(),
                        chain -> chain,
                        (left, right) -> left.depth() >= right.depth() ? left : right,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingInt(MessageChainInfo::depth).reversed()
                        .thenComparing(MessageChainInfo::line)
                        .thenComparing(MessageChainInfo::chainText))
                .limit(5)
                .toList();
    }

    private static Optional<RepeatedPrefix> repeatedPrefix(List<MessageChainInfo> chains) {
        Map<String, List<MessageChainInfo>> byPrefix = new LinkedHashMap<>();
        for (MessageChainInfo chain : chains) {
            if (chain.depth() < 3) {
                continue;
            }
            String prefix = chain.prefixKey(3);
            byPrefix.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(chain);
        }
        return byPrefix.entrySet().stream()
                .map(entry -> new RepeatedPrefix(
                        entry.getKey(),
                        3,
                        entry.getValue().size(),
                        (int) entry.getValue().stream().map(MessageChainInfo::line).distinct().count()
                ))
                .filter(prefix -> prefix.occurrences() >= 3 || (prefix.occurrences() >= 2 && prefix.lineCount() >= 2))
                .max(Comparator.comparingInt(RepeatedPrefix::occurrences));
    }

    private static Map<String, Long> ignoredKinds(com.codex.refactor.analysis.JavaMethodInfo method) {
        return method.messageChains().stream()
                .filter(chain -> !chain.objectNavigation())
                .collect(Collectors.groupingBy(MessageChainInfo::kind, LinkedHashMap::new, Collectors.counting()));
    }

    private record RepeatedPrefix(String prefix, int depth, int occurrences, int lineCount) {
    }
}
