package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import java.util.ArrayList;
import java.util.List;

/**
 * ブロックの「状態 + NBT」の文字列 (FAWE の BaseBlock#toString) から、置いた場所で変わる座標を除く。
 * 差分を取る時に、同じ中身のチェスト・看板などを「変わった」と数えないため
 */
final class SnbtPosition {

    private SnbtPosition() {
    }

    /** SNBT のいちばん外側の x: y: z: の項目を除く (文字列の中は見ない) */
    static String strip(String text) {
        int open = text.indexOf('{');
        if (open < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, open + 1));
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        StringBuilder entry = new StringBuilder();
        List<String> entries = new ArrayList<>();
        int i = open + 1;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                entry.append(c);
                if (c == '\\' && i + 1 < text.length()) entry.append(text.charAt(++i));
                else if (c == quote) quoted = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quote = c;
                entry.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                entry.append(c);
            } else if ((c == '}' || c == ']') && depth > 0) {
                depth--;
                entry.append(c);
            } else if (c == '}' && depth == 0) {
                break;
            } else if (c == ',' && depth == 0) {
                entries.add(entry.toString().trim());
                entry.setLength(0);
            } else {
                entry.append(c);
            }
        }
        if (!entry.isEmpty()) entries.add(entry.toString().trim());
        boolean first = true;
        for (String e : entries) {
            if (e.startsWith("x:") || e.startsWith("y:") || e.startsWith("z:")) continue;
            if (!first) out.append(',');
            out.append(e);
            first = false;
        }
        out.append(text.substring(Math.min(i, text.length())));
        return out.toString();
    }

}
