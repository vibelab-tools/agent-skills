package com.codex.refactor.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineStatsTest {
    @Test
    void fromSourceCountsBlankLinesAndCommentStyles() {
        LineStats stats = LineStats.fromSource("""
                int value = 1;

                // java comment
                # shell comment
                -- sql comment
                /* block start
                 * block middle
                 */
                int done = value;
                """);

        assertEquals(10, stats.physicalLines());
        assertEquals(2, stats.blankLines());
        assertEquals(6, stats.commentLines());
        assertEquals(3, stats.comments().getFirst().line());
        assertEquals("// java comment", stats.comments().getFirst().text());
        assertEquals(8, stats.comments().getLast().line());
        assertEquals("*/", stats.comments().getLast().text());
    }
}
