package com.lunar_prototype.impossbleEscapeMC.util;

import java.util.ArrayList;
import java.util.List;

/**
 * アイテムの説明文 (フレーバーテキストなど) を、説明欄の幅に収まるよう折り返す。幅はクライアントのフォントの送り ({@link FontWidths}) で測る。
 * <ul>
 *   <li>文の中の改行はそのまま改行にし、空行も残す</li>
 *   <li>英数字の続き (Spectre・SP-E・UNRG-7 など) とカタカナの続き (アーマーリグ・バックパックなど) は途中で切らない
 *       (1 つで幅を超える時だけ字の途中で切る)</li>
 *   <li>行の頭に来てはいけない字 (。、」）ー・小さい仮名など) は前の字に、行の終わりに来てはいけない字 (「（など) は次の字に付ける</li>
 *   <li>切り方は、行の数を一番少なくした上で、行の長さが揃い、切れ目が自然 (句読点の後・ひらがなから漢字の前などが良く、
 *       漢字の続きの中・送り仮名や助詞の前は悪い) になるものを選ぶ ({@link #fill})</li>
 *   <li>行の頭と終わりの空白は落とす</li>
 *   <li>色・書式のコード (§ と次の 1 字) は幅に数えず、次の字に付ける</li>
 * </ul>
 */
public final class LoreWrap {

    /** 説明欄の 1 行の幅の既定 (全角でおよそ 20 字) */
    public static final int DEFAULT_WIDTH = 180;

    private static final String NO_LINE_START = "、。，．,.!?！？)）]］}｝」』】〕〉》〙〗・:：;；ー～〜…‥々ゝゞヽヾぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ%％";
    private static final String NO_LINE_END = "(（[［{｛「『【〔〈《〘〖";

    private LoreWrap() {
    }

    /** text を幅 maxWidth で折り返した行 (text が null・空なら無し) */
    public static List<String> wrap(String text, int maxWidth) {
        if (maxWidth <= 0) throw new IllegalArgumentException("折り返しの幅は正の値が必要です");
        if (text == null || text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            fill(units(paragraph), maxWidth, lines);
        }
        return lines;
    }

    /** 説明欄の既定の幅で折り返す */
    public static List<String> wrap(String text) {
        return wrap(text, DEFAULT_WIDTH);
    }

    /** 文字列の幅 (書式のコードは数えない) */
    static float width(String text) {
        float total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == '§' && i + 1 < text.length()) {
                i += 1 + Character.charCount(text.codePointAt(i + 1));
                continue;
            }
            total += FontWidths.advance(cp, false);
            i += Character.charCount(cp);
        }
        return total;
    }

    /** 切ってよい所で分けた塊 (英数字の続き・空白・それ以外の 1 字。禁則の字は前後の塊に付ける) */
    private static List<String> units(String paragraph) {
        List<String> raw = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        String pendingCode = "";
        for (int i = 0; i < paragraph.length(); ) {
            int cp = paragraph.codePointAt(i);
            int n = Character.charCount(cp);
            if (cp == '§' && i + n < paragraph.length()) {
                int code = paragraph.codePointAt(i + n);
                String c = paragraph.substring(i, i + n + Character.charCount(code));
                if (word.length() > 0) word.append(c);
                else pendingCode += c;
                i += c.length();
                continue;
            }
            String ch = pendingCode + new String(Character.toChars(cp));
            pendingCode = "";
            if (isWordChar(cp)) {
                // 英数字とカタカナの境目では分ける (Spectre製・SP-E を、など)
                if (word.length() > 0 && isKatakana(lastVisible(word.toString())) != isKatakana(cp)) {
                    raw.add(word.toString());
                    word.setLength(0);
                }
                word.append(ch);
            } else {
                if (word.length() > 0) {
                    raw.add(word.toString());
                    word.setLength(0);
                }
                raw.add(ch);
            }
            i += n;
        }
        if (word.length() > 0) raw.add(word.toString());
        if (!pendingCode.isEmpty()) raw.add(pendingCode);

        List<String> units = new ArrayList<>();
        String carry = "";
        for (String u : raw) {
            u = carry + u;
            carry = "";
            int first = firstVisible(u);
            if (!units.isEmpty() && first >= 0 && NO_LINE_START.indexOf(first) >= 0) {
                units.set(units.size() - 1, units.getLast() + u);
            } else {
                units.add(u);
            }
            int last = lastVisible(units.getLast());
            if (last >= 0 && NO_LINE_END.indexOf(last) >= 0) carry = units.removeLast();
        }
        if (!carry.isEmpty()) units.add(carry);
        return units;
    }

    /** 1 行の重み (行の数を減らすのを、ほかの何よりも優先する) */
    private static final double LINE_COST = 10_000;
    /** 行の余り (幅に対する割合) の 2 乗に掛ける数 (行の長さを揃える) */
    private static final double SLACK_COST = 100;

    /**
     * 塊を幅に詰めて行にする。切り方は全部比べて (塊の数は多くないので、行の終わりごとに一番良い切り方を覚えていく)、
     * 行の数 x {@link #LINE_COST} + 行の余りの 2 乗 x {@link #SLACK_COST} + 切れ目の悪さ ({@link #breakCost}) が一番小さいものにする
     */
    private static void fill(List<String> source, int maxWidth, List<String> lines) {
        // 1 つで幅を超える塊 (長い英数字の続き) は字に分け、その間はどこで切ってもよいことにする
        List<String> units = new ArrayList<>();
        List<Boolean> split = new ArrayList<>();   // その塊の前が、分けた塊の途中か
        for (String unit : source) {
            if (width(unit) <= maxWidth) {
                units.add(unit);
                split.add(false);
                continue;
            }
            boolean first = true;
            for (int i = 0; i < unit.length(); ) {
                int cp = unit.codePointAt(i);
                int n = Character.charCount(cp);
                if (cp == '§' && i + n < unit.length()) n += Character.charCount(unit.codePointAt(i + n));
                String ch = unit.substring(i, i + n);
                if (cp == '§' || first) {
                    units.add(ch);
                    split.add(!first);
                } else {
                    units.set(units.size() - 1, units.getLast() + ch);   // 書式のコードの後の字は、コードと同じ塊に
                    split.set(split.size() - 1, split.getLast());
                }
                if (cp != '§') {
                    first = false;
                    if (i + n < unit.length()) {
                        units.add("");
                        split.add(true);
                    }
                }
                i += n;
            }
            if (units.getLast().isEmpty()) {
                units.removeLast();
                split.removeLast();
            }
        }
        int m = units.size();
        if (m == 0) return;
        float[] widths = new float[m];
        for (int i = 0; i < m; i++) widths[i] = width(units.get(i));

        double[] best = new double[m + 1];
        int[] from = new int[m + 1];
        java.util.Arrays.fill(best, Double.POSITIVE_INFINITY);
        best[0] = 0;
        for (int j = 1; j <= m; j++) {
            // 行は塊 i..j-1。行の終わりの空白は数えない
            int end = j;
            while (end > 0 && units.get(end - 1).isBlank()) end--;
            float w = 0;
            for (int i = end - 1; i >= 0; i--) {
                w += widths[i];
                if (w > maxWidth) break;
                if (units.get(i).isBlank() || Double.isInfinite(best[i])) continue;   // 行の頭の空白は前の行の終わりにする
                double slack = (maxWidth - w) / maxWidth;
                double cost = best[i] + LINE_COST + SLACK_COST * slack * slack + (i > 0 ? breakCost(units, i, split.get(i)) : 0);
                if (cost < best[j]) {
                    best[j] = cost;
                    from[j] = i;
                }
            }
            // 空白だけの終わり (行の頭の空白を飛ばした後の続き) は、空白の前の終わりと同じ
            if (Double.isInfinite(best[j]) && end < j && !Double.isInfinite(best[end])) {
                best[j] = best[end];
                from[j] = -end - 1;
            }
        }
        List<String> out = new ArrayList<>();
        for (int j = m; j > 0; ) {
            int i = from[j];
            if (i < 0) {
                j = -i - 1;
                continue;
            }
            out.add(stripTrailingSpaces(String.join("", units.subList(i, j))));
            j = i;
        }
        java.util.Collections.reverse(out);
        lines.addAll(out);
    }

    /** 文字の種類 (切れ目の良し悪しを決める) */
    private enum Kind { KANJI, HIRAGANA, KATAKANA, LATIN, CLOSE, OTHER }

    private static Kind kindOf(int cp) {
        if (cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0x3400 && cp <= 0x4DBF || cp == '々') return Kind.KANJI;
        if (cp >= 0x3041 && cp <= 0x309F) return Kind.HIRAGANA;
        if (isKatakana(cp)) return Kind.KATAKANA;
        if (cp < 0x80 && Character.isLetterOrDigit(cp)) return Kind.LATIN;
        if (NO_LINE_START.indexOf(cp) >= 0) return Kind.CLOSE;
        return Kind.OTHER;
    }

    /** 塊 i の前で切る悪さ (小さいほど自然な切れ目)。間の空白は飛ばして両側の字で決める */
    private static double breakCost(List<String> units, int i, boolean insideWord) {
        if (insideWord) return 40;
        int k = i - 1;
        while (k >= 0 && units.get(k).isBlank()) k--;
        if (k < 0) return 0;
        Kind a = kindOf(lastVisible(units.get(k))), b = kindOf(firstVisible(units.get(i)));
        if (k < i - 1) return b == Kind.HIRAGANA ? 30 : 0;                    // 空白の所 (英単語の後の助詞の前は悪い)
        if (a == Kind.CLOSE) return -5;                                       // 句読点・閉じ括弧の後
        if (a == Kind.HIRAGANA) return b == Kind.HIRAGANA ? 15 : 0;           // ひらがなの後 (助詞の後) は良い。ひらがなの続きの中はやや悪い
        if (b == Kind.HIRAGANA) return 30;                                    // 送り仮名・助詞の前
        if (a == Kind.KANJI && b == Kind.KANJI) return 30;                    // 漢字の続きの中
        if (a == Kind.LATIN && b == Kind.KANJI) return 15;                    // Spectre製 など
        return 5;                                                             // 漢字とカタカナ・英数字の境目
    }

    /** 途中で切らない続きの字 (英数字か、カタカナ) */
    private static boolean isWordChar(int cp) {
        return cp < 0x80 && (Character.isLetterOrDigit(cp) || "-_'./#+&".indexOf(cp) >= 0) || isKatakana(cp);
    }

    /** カタカナ (長音・半角を含む。中黒は語の区切りなので除く) */
    private static boolean isKatakana(int cp) {
        return cp >= 0x30A1 && cp <= 0x30FA || cp == 0x30FC || cp >= 0xFF66 && cp <= 0xFF9F;
    }

    /** 書式のコードを除いた最初・最後の字 (無ければ -1) */
    private static int firstVisible(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == '§' && i + 1 < s.length()) {
                i += 1 + Character.charCount(s.codePointAt(i + 1));
                continue;
            }
            return cp;
        }
        return -1;
    }

    private static int lastVisible(String s) {
        int last = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == '§' && i + 1 < s.length()) {
                i += 1 + Character.charCount(s.codePointAt(i + 1));
                continue;
            }
            last = cp;
            i += Character.charCount(cp);
        }
        return last;
    }

    private static String stripTrailingSpaces(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '　')) end--;
        return s.substring(0, end);
    }
}
