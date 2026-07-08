package com.codex.refactor.analysis;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageChainInfoTest {
    @Test
    void fromExpressionIgnoresDotsInsideArgumentsIndexesAndStrings() {
        MessageChainInfo chain = MessageChainInfo.fromExpression(
                        "order.customer(\"first.last\").addresses[0].street().trim()",
                        12,
                        Set.of("order"))
                .orElseThrow();

        assertEquals("order", chain.root());
        assertEquals(
                java.util.List.of("customer", "addresses0", "street", "trim"),
                chain.selectors());
        assertEquals(5, chain.depth());
        assertEquals(12, chain.line());
        assertEquals("object_navigation", chain.kind());
        assertTrue(chain.objectNavigation());
    }
}
