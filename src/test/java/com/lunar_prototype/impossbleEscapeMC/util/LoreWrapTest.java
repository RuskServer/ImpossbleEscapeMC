package com.lunar_prototype.impossbleEscapeMC.util;

import java.util.List;

/**
 * 説明文の折り返し: 幅に収まる、文の中身が欠けない、英数字の続きを切らない、禁則の字が行の頭・終わりに来ない、改行と空行を残す
 */
public final class LoreWrapTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkFits(List<String> lines, int max) {
        for (String line : lines) check(LoreWrap.width(line) <= max, "幅に収まる: " + line + " (" + LoreWrap.width(line) + ")");
    }

    public static void main(String[] args) {
        check(LoreWrap.wrap(null).isEmpty() && LoreWrap.wrap("").isEmpty(), "空なら行なし");
        check(LoreWrap.wrap("短い説明。").equals(List.of("短い説明。")), "短ければそのまま");

        String text = "国連軍が Spectre の SP-E を写した UNRG-7 を、さらに誰かが写した物。説明書は無い。";
        List<String> lines = LoreWrap.wrap(text, 90);
        check(lines.size() > 1, "折り返す " + lines);
        checkFits(lines, 90);
        check(String.join("", lines).replace(" ", "").equals(text.replace(" ", "")), "中身が欠けない " + lines);
        for (String word : List.of("Spectre", "SP-E", "UNRG-7")) {
            check(lines.stream().anyMatch(l -> l.contains(word)), "英数字の続きを切らない: " + word + " " + lines);
        }
        for (String line : lines) {
            check(!line.startsWith("。") && !line.startsWith("、") && !line.startsWith(" "), "行の頭に句読点・空白が来ない: " + line);
            check(!line.endsWith(" "), "行の終わりに空白が残らない: " + line);
        }

        // 句読点がちょうど行の頭に来る長さでも、前の字に付く
        for (int width = 27; width <= 120; width++) {
            for (String line : LoreWrap.wrap("あいうえおかきくけこ。さしすせそ「たちつてと」なにぬねの、はひふへほ。", width)) {
                check(!line.startsWith("。") && !line.startsWith("、") && !line.startsWith("」"), "禁則 (行の頭) " + width + ": " + line);
                check(!line.endsWith("「"), "禁則 (行の終わり) " + width + ": " + line);
            }
        }

        // カタカナの続きは切らない (英数字との境目では切ってよい。「アーマーリグ。」1 つが入る幅から)
        for (int width = 70; width <= 200; width++) {
            List<String> rig = LoreWrap.wrap("Spectre製の実験的な次世代アーマーリグ。重厚な防護と引き換えに収納は少ない。", width);
            check(rig.stream().anyMatch(l -> l.contains("アーマーリグ")), "カタカナの続き " + width + ": " + rig);
        }

        // 行の数は詰めた時と同じまま、行の長さを揃える (最後の行が 1〜2 字だけにならない)
        List<String> bag = LoreWrap.wrap("観光客向けの特大バックパック。MBSS を上回る収納を誇るが、背負えば否応なく目立つ。");
        check(bag.size() == 3 && bag.getLast().codePointCount(0, bag.getLast().length()) > 4, "長さを揃える " + bag);
        // 送り仮名・助詞の前では切らない
        List<String> rig = LoreWrap.wrap("Spectre製の実験的な次世代アーマーリグ。重厚な防護と引き換えに収納は少ない。");
        check(rig.size() == 3 && rig.stream().anyMatch(l -> l.contains("アーマーリグ")), "自然な切れ目 " + rig);
        for (List<String> wrapped : List.of(rig, bag, LoreWrap.wrap(text))) {
            for (String line : wrapped.subList(1, wrapped.size())) {
                int first = line.codePointAt(0);
                check(!(first >= 0x3041 && first <= 0x309F), "行の頭がひらがな (送り仮名・助詞) にならない: " + wrapped);
            }
        }

        // 改行と空行は残す
        check(LoreWrap.wrap("一行目\n\n三行目").equals(List.of("一行目", "", "三行目")), "改行と空行");

        // 幅を超える英数字の続きは字の途中で切る
        List<String> longWord = LoreWrap.wrap("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", 60);
        check(longWord.size() > 1, "長い英単語は切る");
        checkFits(longWord, 60);
        check(String.join("", longWord).equals("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"), "長い英単語も欠けない");

        // 書式のコードは幅に数えない
        check(LoreWrap.width("§cあ") == LoreWrap.width("あ"), "書式のコードは幅 0");
        List<String> colored = LoreWrap.wrap("§cあいうえおかきくけこさしすせそ", 60);
        checkFits(colored, 60);
        check(colored.getFirst().startsWith("§cあ"), "書式のコードは次の字に付く " + colored);

        // 既定の幅 (全角でおよそ 20 字)
        checkFits(LoreWrap.wrap("観光客向けの特大バックパック。MBSS を上回る収納を誇るが、背負えば否応なく目立つ。"), LoreWrap.DEFAULT_WIDTH);
        System.out.println("LoreWrapTest ok");
    }
}
