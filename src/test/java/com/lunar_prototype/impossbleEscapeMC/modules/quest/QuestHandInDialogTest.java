package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 納品の画面:
 * <ul>
 *   <li>目標の行と使える物の行が、行の幅を超えず、ちょうど行の幅で終わる (クライアントが途中で改行しない・中央揃えでずれない)</li>
 *   <li>割り当て: 選んだ行だけを、目標の順・行の順に、必要な数まで使う (ほかの目標に使った分は減る)</li>
 *   <li>開いた時の選び方: 行の順 (価値の低い順) に、必要な数に届くまで選ぶ</li>
 * </ul>
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す (IEM_shader/tools/dialog_render.py で絵にする)
 */
public final class QuestHandInDialogTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkWidths(List<DialogLine> lines, int width, String label) {
        for (int i = 0; i < lines.size(); i++) {
            DialogLine l = lines.get(i);
            check(l.max <= width && l.x == width, label + " line " + i + ": max " + l.max + " end " + l.x);
        }
    }

    private static String element(String kind, int width, String icon, List<DialogLine> lines) {
        Component body = Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
        return "{\"kind\":\"" + kind + "\",\"width\":" + width + (icon != null ? ",\"icon\":\"" + icon + "\"" : "")
                + ",\"component\":" + GsonComponentSerializer.gson().serialize(body) + "}";
    }

    public static void main(String[] args) throws Exception {
        // ---- 幅 ----
        checkWidths(QuestHandInDialog.objectiveLines(new QuestHandInDialog.ObjectiveView(null, "M855A1 弾を 60 発納品する (FIR)", 12, 60, 60)),
                QuestHandInDialog.LINE_W, "objective");
        checkWidths(QuestHandInDialog.objectiveLines(new QuestHandInDialog.ObjectiveView("とても長いクエストの名前がここに入る場合の確認用の文字列",
                "とても長い目標の説明がここに入る場合の確認用の文字列です。ここも長い", 0, 0, 1)), QuestHandInDialog.LINE_W, "objective long");
        checkWidths(QuestHandInDialog.groupLines(new QuestHandInDialog.GroupView("5.56x45mm M855A1", true, "スタッシュ 3 の Tactical Sling Bag",
                120, true, 48, "toggle/0/3")), TerminalFont.ROW_LINE_W, "group selected");
        checkWidths(QuestHandInDialog.groupLines(new QuestHandInDialog.GroupView("とても長いアイテムの名前がここに入る場合の確認用の文字列です", false,
                "持ち物の とても長いバックパックの名前がここに入る場合の確認用", 9999, false, 0, "toggle/12/34")), TerminalFont.ROW_LINE_W, "group long");
        checkWidths(QuestHandInDialog.groupLines(new QuestHandInDialog.GroupView("工具箱", true, "持ち物", 1, true, 0, "toggle/1/0")),
                TerminalFont.ROW_LINE_W, "group spare");

        // ---- 割り当て ----
        // 行 0: 30 個、行 1: 50 個、行 2: 10 個。目標 0 は 60 個 (行 0・1)、目標 1 は 40 個 (行 1・2)
        int[] amounts = {30, 50, 10};
        int[] needed = {60, 40};
        List<List<Integer>> groups = List.of(List.of(0, 1), List.of(1, 2));
        boolean[][] all = {{true, true, false}, {false, true, true}};
        int[][] use = QuestHandInDialog.allocate(needed, groups, amounts, all);
        check(use[0][0] == 30 && use[0][1] == 30, "objective 0 takes 30 + 30");
        check(use[1][1] == 20 && use[1][2] == 10, "objective 1 gets what is left of row 1, then row 2");

        // 選んでいない行は使わない
        boolean[][] some = {{false, true, false}, {false, false, true}};
        use = QuestHandInDialog.allocate(needed, groups, amounts, some);
        check(use[0][0] == 0 && use[0][1] == 50, "only the selected row");
        check(use[1][1] == 0 && use[1][2] == 10, "objective 1 only row 2");

        // 開いた時の選び方: 行の順に必要な数まで (目標 0 は行 0 と 1、目標 1 は行 1 の残り 20 と行 2)
        boolean[][] chosen = QuestHandInDialog.defaultSelection(needed, groups, amounts);
        check(chosen[0][0] && chosen[0][1] && !chosen[0][2], "default for objective 0");
        check(chosen[1][1] && chosen[1][2], "default for objective 1");
        // 1 行で足りれば、2 行目は選ばない
        chosen = QuestHandInDialog.defaultSelection(new int[]{5}, List.of(List.of(0, 1)), new int[]{30, 50});
        check(chosen[0][0] && !chosen[0][1], "stop once enough");

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            List<String> elements = new ArrayList<>();
            elements.add(element("text", 340, null, QuestHandInDialog.objectiveLines(
                    new QuestHandInDialog.ObjectiveView(null, "ボルトを 4 個納品する (FIR)", 1, 4, 4))));
            elements.add(element("row", 308, "bolts", QuestHandInDialog.groupLines(
                    new QuestHandInDialog.GroupView("ボルト", true, "持ち物", 2, true, 2, "toggle/0/0"))));
            elements.add(element("row", 308, "bolts", QuestHandInDialog.groupLines(
                    new QuestHandInDialog.GroupView("ボルト", true, "スタッシュ 2 の Tactical Sling Bag", 5, true, 1, "toggle/0/1"))));
            elements.add(element("row", 308, "bolts", QuestHandInDialog.groupLines(
                    new QuestHandInDialog.GroupView("ボルト", true, "スタッシュ 1", 8, false, 0, "toggle/0/2"))));
            elements.add(element("text", 340, null, QuestHandInDialog.objectiveLines(
                    new QuestHandInDialog.ObjectiveView(null, "小型バッテリーを 3 個納品する", 0, 0, 3))));
            elements.add(element("row", 308, "battery", QuestHandInDialog.groupLines(
                    new QuestHandInDialog.GroupView("小型バッテリー", false, "持ち物の Tactical Sling Bag", 1, false, 0, "toggle/1/3"))));
            Files.writeString(Path.of(dump), "{\"screens\":[{\"title\":\"納品 ― 部品の調達\",\"footer\":[\"納品する (3 個)\",\"自動で選び直す\",\"戻る\"],"
                    + "\"elements\":[" + String.join(",", elements) + "]}]}", StandardCharsets.UTF_8);
        }
        System.out.println("QuestHandInDialogTest OK");
    }
}
