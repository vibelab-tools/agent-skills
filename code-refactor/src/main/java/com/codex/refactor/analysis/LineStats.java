package com.codex.refactor.analysis;

import java.util.ArrayList;
import java.util.List;

public record LineStats(int physicalLines, int blankLines, int commentLines, List<CommentInfo> comments) {
    public static LineStats fromSource(String source) {
        String[] lines = source.split("\\R", -1);
        int physical = lines.length;
        int blank = 0;
        int comment = 0;
        boolean inBlock = false;
        ArrayList<CommentInfo> comments = new ArrayList<>();

        for (int index = 0; index < lines.length; index++) {
            String trimmed = lines[index].trim();
            if (trimmed.isEmpty()) {
                blank++;
                continue;
            }

            if (isCommentLine(trimmed, inBlock)) {
                comment++;
                comments.add(new CommentInfo(index + 1, trimmed));
            }
            inBlock = updateBlockCommentState(trimmed, inBlock);
        }
        return new LineStats(physical, blank, comment, comments);
    }

    private static boolean isCommentLine(String trimmed, boolean inBlock) {
        return inBlock
                || trimmed.startsWith("//")
                || trimmed.startsWith("#")
                || trimmed.startsWith("--")
                || trimmed.startsWith("<!--")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*");
    }

    private static boolean updateBlockCommentState(String trimmed, boolean inBlock) {
        if (!inBlock && trimmed.contains("/*") && !trimmed.contains("*/")) {
            return true;
        }
        if (inBlock && trimmed.contains("*/")) {
            return false;
        }
        return inBlock;
    }
}
