package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * レイド結果の画面 (ダイアログ) の文字の組み立てを確かめる。サーバーは要らない。
 * 上の札・レベルの板・経験値の内訳・クエスト納品の行・キルログの行のどの行も、途中で行の幅を超えず、ちょうど行の幅で終わる。
 * 評価ランクの決まりも確かめる。環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す
 * (IEM_shader/tools/dialog_render.py が絵にする)
 */
public final class RaidResultLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void lines(List<DialogLine> lines, int width, String what) {
        for (DialogLine l : lines) check(l.max <= width && l.x == width, what + " " + l.max + " / " + l.x + " (幅 " + width + ")");
    }

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    public static void main(String[] args) throws Exception {
        TextColor ok = TextColor.color(0x78CD60), amber = TextColor.color(0xDEAC4E);
        RaidResultDialog.HeaderView header = new RaidResultDialog.HeaderView("脱出成功", true, "北道路脱出 · ノヴォザリエ薬品工場 · 23:41", null, null);
        RaidResultDialog.HeaderView header4 = new RaidResultDialog.HeaderView("死亡", false, "ノヴォザリエ薬品工場 · 11:38", "戻る: クエスト納品", "page/handin");
        lines(RaidResultDialog.headerLines(header), RaidResultDialog.LINE_W, "header");
        lines(RaidResultDialog.headerLines(header4), RaidResultDialog.LINE_W, "header4");

        RaidResultDialog.LevelView before = new RaidResultDialog.LevelView(7, false, 300, 3400, 7000, 3100, true);
        RaidResultDialog.LevelView after = new RaidResultDialog.LevelView(8, true, 1450, 550, 8000, 0, true);
        lines(RaidResultDialog.levelLines(before), RaidResultDialog.LINE_W, "level before");
        lines(RaidResultDialog.levelLines(after), RaidResultDialog.LINE_W, "level after");

        List<RaidResultDialog.ExpRow> rows = List.of(
                new RaidResultDialog.ExpRow(UiIcon.EXTRACT, "脱出成功", "北道路脱出", 1750),
                new RaidResultDialog.ExpRow(UiIcon.KILL, "SCAV を排除", "×5", 400),
                new RaidResultDialog.ExpRow(UiIcon.KILL, "BOSS を排除", "×1", 400),
                new RaidResultDialog.ExpRow(UiIcon.KILL, "PMC を排除", "×1", 160));
        lines(RaidResultDialog.expRowLines(rows, 2710), RaidResultDialog.LINE_W, "exp rows");

        List<RaidResultDialog.HandInView> handins = List.of(
                new RaidResultDialog.HandInView("補給路の確認", "Kovacs", "ボルト", 0, 3, 3, true, "handin/supply_route/1"),
                new RaidResultDialog.HandInView("実戦配備", "Kovacs", "小型バッテリー", 0, 1, 1, false, "handin/field_deployment/1"),
                new RaidResultDialog.HandInView("衛生環境の維持と、とても長いクエストの名前", "Pharmakon", "浄水フィルター", 1, 2, 2, true, "handin/x/0"));
        for (RaidResultDialog.HandInView h : handins) lines(RaidResultDialog.handInLines(h), RaidResultDialog.ROW_W, "handin " + h.quest());

        List<RaidReport.Kill> kills = List.of(
                new RaidReport.Kill(252_000, "SCAV", RaidReport.Kind.SCAV, null, "AK-74M", "5.45x39mm PS", 38, "胴"),
                new RaidReport.Kill(255_000, "SCAV", RaidReport.Kind.SCAV, null, "AK-74M", "5.45x39mm PS", 41, "頭"),
                new RaidReport.Kill(807_000, "Mirai_77", RaidReport.Kind.PMC, null, "AK-74M", "5.45x39mm BS", 64, "頭"),
                new RaidReport.Kill(1_023_000, "Tagilla", RaidReport.Kind.BOSS, null, "SVD", "7.62x54R LPS", 112, "頭"),
                new RaidReport.Kill(1_100_000, "SCAV", RaidReport.Kind.SCAV, null, null, null, -1, null));
        RaidReport.Kill death = new RaidReport.Kill(698_000, "Mirai_77", RaidReport.Kind.PMC, null, "M4A1", "5.56x45mm M855A1", 85, "胴");
        for (RaidReport.Kill k : kills) lines(RaidResultDialog.killLines(k, false), RaidResultDialog.ROW_W, "kill " + k.name());
        lines(RaidResultDialog.killLines(death, true), RaidResultDialog.ROW_W, "death");

        // 評価ランク: 死亡は D、脱出は 利益 (千₽ ごとに 1) + SCAV 5・PMC 20・BOSS 30
        RaidReport survived = new RaidReport("工場", true, "脱出成功", "北道路", 0, List.of(), 1, 0, false,
                List.of(new RaidReport.LootStack(null, 151_000, false)), 0, kills, null);
        check(survived.score() == 151 + 5 * 3 + 20 + 30 && survived.rank().equals("A"), "rank A " + survived.score());
        check(survived.pointsToNextRank() == 250 - 216, "to S " + survived.pointsToNextRank());
        RaidReport dead = new RaidReport("工場", false, "死亡", null, 0, List.of(), 1, 0, false, List.of(), 42_000, kills, death);
        check(dead.rank().equals("D"), "rank D");
        check(RaidReport.rankOf(119).equals("B") && RaidReport.rankOf(120).equals("A") && RaidReport.rankOf(49).equals("C"), "rank steps");

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            var gson = GsonComponentSerializer.gson();
            List<String> screens = new ArrayList<>();
            screens.add(screen("ノヴォザリエ薬品工場 ― レイド結果", "次へ ▶  戦利品", "閉じる", List.of(
                    element("header", 340, null, gson.serialize(join(RaidResultDialog.headerLines(header)))),
                    element("header", 340, null, gson.serialize(join(RaidResultDialog.levelLines(after)))),
                    element("header", 340, null, gson.serialize(join(RaidResultDialog.expRowLines(rows, 2710)))))));
            screens.add(screen("ノヴォザリエ薬品工場 ― レイド結果", "まとめて納品  (3件)", "次へ ▶  キルログ", List.of(
                    element("header", 340, null, gson.serialize(join(RaidResultDialog.headerLines(new RaidResultDialog.HeaderView("脱出成功", true, "北道路脱出 · ノヴォザリエ薬品工場 · 23:41", "戻る: 戦利品", "loot"))))),
                    element("row", 308, "bolts", gson.serialize(join(RaidResultDialog.handInLines(handins.get(0))))),
                    element("row", 308, "battery", gson.serialize(join(RaidResultDialog.handInLines(handins.get(1))))),
                    element("row", 308, "filter", gson.serialize(join(RaidResultDialog.handInLines(handins.get(2))))))));
            List<String> killElements = new ArrayList<>();
            killElements.add(element("header", 340, null, gson.serialize(join(RaidResultDialog.headerLines(header4)))));
            for (RaidReport.Kill k : kills.subList(0, 4)) killElements.add(element("row", 308, "ak", gson.serialize(join(RaidResultDialog.killLines(k, false)))));
            killElements.add(element("row", 308, "m4", gson.serialize(join(RaidResultDialog.killLines(death, true)))));
            screens.add(screen("ノヴォザリエ薬品工場 ― レイド結果", "◀ クエスト納品", "閉じる", killElements));
            // 戦利品のページ (チェストの画面のタイトル)
            List<RaidReport.LootStack> loot = new ArrayList<>();
            long[] values = {48_000, 42_000, 31_000, 21_600, 15_000, 13_000, 6_600, 5_400, 3_000, 1_200, 540};
            for (int i = 0; i < values.length; i++) loot.add(new RaidReport.LootStack(null, values[i], i == 1 || i == 5 || i == 7));
            RaidReport lootReport = new RaidReport("工場", true, "脱出成功", "北道路", 0, List.of(), 1, 0, false, loot, 42_000, kills, null);
            screens.add("{\"title\":\"\",\"footer\":[],\"elements\":[{\"kind\":\"chest\",\"width\":176,\"items\":" + loot.size()
                    + ",\"component\":" + gson.serialize(RaidResultLootGUI.title(lootReport, loot)) + "}]}");
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("RaidResultLayoutTest: header, level, exp rows, hand-in rows, kill rows fit their widths; ranks ok");
    }

    private static String screen(String title, String b1, String b2, List<String> elements) {
        return "{\"title\":\"" + title + "\",\"footer\":[\"" + b1 + "\",\"" + b2 + "\"],\"elements\":[" + String.join(",", elements) + "]}";
    }

    private static String element(String kind, int width, String icon, String component) {
        return "{\"kind\":\"" + kind + "\",\"width\":" + width + (icon != null ? ",\"icon\":\"" + icon + "\"" : "") + ",\"component\":" + component + "}";
    }
}
