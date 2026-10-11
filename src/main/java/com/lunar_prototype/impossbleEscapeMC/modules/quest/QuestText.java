package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import java.util.ArrayList;
import java.util.List;

/** クエストの説明・目標を折り返し、定義内の改行と空行も保持する。 */
final class QuestText {
    private QuestText() {}

    static List<String> wrap(String text, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("折り返し幅は正の値が必要です");
        if (text == null || text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int length = Math.min(limit, paragraph.codePointCount(start, paragraph.length()));
                int end = paragraph.offsetByCodePoints(start, length);
                lines.add(paragraph.substring(start, end));
                start = end;
            }
        }
        return lines;
    }
}
