package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * クエストの画面 (ダイアログ) の文字の組み立てを確かめる。サーバーは要らない。
 * 一覧の上の端末・一覧の行・詳細の上の端末・見出し・目標の行・報酬の行のどの行も、途中で行の幅を超えず
 * (超えるとクライアントが改行する)、ちょうど行の幅で終わる。
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す
 * (IEM_shader/tools/dialog_render.py がクライアントと同じ規則で絵にする)
 */
public final class QuestDialogLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkLines(List<DialogLine> lines, int width, String what) {
        for (DialogLine l : lines) {
            check(l.max <= width && l.x == width, what + " " + l.max + " / " + l.x + " (幅 " + width + ")");
        }
    }

    private static final TextColor NOTE = TextColor.color(0xAAA48C);
    private static final TextColor OK = TextColor.color(0x78CD60);
    private static final TextColor NG = TextColor.color(0xD64A34);
    private static final TextColor AMBER = TextColor.color(0xDEAC4E);
    private static final TextColor DIM = TextColor.color(0x827E6E);

    public static void main(String[] args) throws Exception {
        List<QuestDialog.Tab> tabs = List.of(new QuestDialog.Tab("すべて 5", true, "tab/all"), new QuestDialog.Tab("進行中 2", false, "tab/active"),
                new QuestDialog.Tab("受領可 1", false, "tab/start"), new QuestDialog.Tab("完了 1", false, "tab/done"),
                new QuestDialog.Tab("ロック 1", false, "tab/locked"));
        QuestDialog.ListHeaderView traderHeader = new QuestDialog.ListHeaderView("KOVACS", 1, 1, 1,
                List.of(new QuestDialog.Chip("まとめて納品  1件", AMBER, "bulk", "")), tabs);
        QuestDialog.ListHeaderView pdaHeader = new QuestDialog.ListHeaderView("TASKS", 2, 3, 4,
                List.of(new QuestDialog.Chip("完了・ロック: 隠す", NOTE, "all", "")),
                List.of(new QuestDialog.Tab("すべて 9", true, "tab/all"), new QuestDialog.Tab("Kovacs 3", false, "tab/kovacs"),
                        new QuestDialog.Tab("Pharmakon 3", false, "tab/pharmakon"), new QuestDialog.Tab("Bastion 3", false, "tab/bastion")));
        checkLines(QuestDialog.listHeaderLines(traderHeader), QuestDialog.HEADER_W, "trader header");
        checkLines(QuestDialog.listHeaderLines(pdaHeader), QuestDialog.HEADER_W, "pda header");

        Component hover = Component.text("依頼文");
        List<QuestDialog.RowView> rows = List.of(
                new QuestDialog.RowView("補給路の確認", QuestDialog.Status.REPORT, null, "2/2", OK, 2, 2,
                        List.of(new QuestDialog.Part("✓ ノヴォザリエ薬品工場 から脱出", OK), new QuestDialog.Part("✓ ボルト 3/3 納品", OK)),
                        "detail/a", "report/a", hover),
                new QuestDialog.RowView("照準の微調整", QuestDialog.Status.ACTIVE, null, "1/3", AMBER, 3, 1,
                        List.of(new QuestDialog.Part("✓ SCAV 排除 5/5", OK), new QuestDialog.Part("EOTech XPS2 0/1 納品", NOTE),
                                new QuestDialog.Part("ノヴォザリエ薬品工場 から脱出", NOTE)), "detail/b", "detail/b", hover),
                new QuestDialog.RowView("至近距離の洗礼", QuestDialog.Status.START, null, "8,000₽", AMBER, 0, 0,
                        List.of(new QuestDialog.Part("SCAV 排除 ×5", NOTE)), "detail/c", "accept/c", hover),
                new QuestDialog.RowView("実戦配備", QuestDialog.Status.DONE, null, null, AMBER, 0, 0,
                        List.of(new QuestDialog.Part("完了済み", DIM)), "detail/d", "detail/d", hover),
                new QuestDialog.RowView("精密な中間点", QuestDialog.Status.LOCKED, null, "15,000₽", DIM, 0, 0,
                        List.of(new QuestDialog.Part("「至近距離の洗礼」完了後", NG), new QuestDialog.Part("SVD 販売解放", AMBER)),
                        "detail/e", "detail/e", hover),
                new QuestDialog.RowView("工場の遺産 - 調査と、とても長いクエストの名前", QuestDialog.Status.ACTIVE, "Pharmakon", "0/7", AMBER, 7, 3,
                        List.of(new QuestDialog.Part("浄水フィルター 0/2 納品", NOTE)), "detail/f", "detail/f", hover));
        for (QuestDialog.RowView row : rows) checkLines(QuestDialog.rowLines(row), TerminalFont.ROW_LINE_W, "row " + row.name());

        QuestDialog.DetailView detail = new QuestDialog.DetailView("照準の微調整", "Kovacs", QuestDialog.Status.ACTIVE, 1, 3);
        checkLines(QuestDialog.detailHeaderLines(detail), QuestDialog.HEADER_W, "detail header");
        for (QuestDialog.Status s : QuestDialog.Status.values()) {
            checkLines(QuestDialog.detailHeaderLines(new QuestDialog.DetailView("名前", "Kovacs", s, 0, 2)), QuestDialog.HEADER_W, "detail " + s);
        }
        for (String label : List.of("目標", "報酬", "受領の条件")) {
            checkLines(List.of(QuestDialog.sectionLine(UiIcon.QUEST, label)), QuestDialog.HEADER_W, "section " + label);
        }
        List<QuestDialog.ObjectiveView> objectives = List.of(
                new QuestDialog.ObjectiveView("ノヴォザリエ薬品工場で SCAV を 5 体排除する", "5/5", true, 1.0, null),
                new QuestDialog.ObjectiveView("EOTech XPS2 (FIR) を 1 個納品する", "0/1", false, 0.0, "handin/b"),
                new QuestDialog.ObjectiveView("ノヴォザリエ薬品工場 から脱出する (1回)", "0/1", false, 0.0, null),
                new QuestDialog.ObjectiveView("30m以上70m以下の距離からSCAVを7体排除する、とても長い目標の説明文", "3/7", false, 3 / 7.0, null));
        for (QuestDialog.ObjectiveView o : objectives) checkLines(QuestDialog.objectiveLines(o), TerminalFont.ROW_LINE_W, "objective " + o.text());
        List<QuestDialog.NoteView> notes = List.of(
                new QuestDialog.NoteView("22,000₽", AMBER, "所持金に加わる", NOTE),
                new QuestDialog.NoteView("EXP 1,200", OK, "経験値", NOTE),
                new QuestDialog.NoteView("SVD 販売解放", TextColor.color(0xE0D6AA), "Kovacs で買えるようになる", NOTE));
        for (QuestDialog.NoteView n : notes) checkLines(QuestDialog.noteLines(n), TerminalFont.ROW_LINE_W, "note " + n.text());

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            var gson = GsonComponentSerializer.gson();
            List<String> screens = new ArrayList<>();
            // 一覧 (トレーダー)
            StringBuilder list = new StringBuilder("{\"title\":\"Kovacs ― 依頼\",\"footer\":[\"ショップ\",\"閉じる\"],\"elements\":[");
            list.append(element("header", 340, null, gson.serialize(QuestDialog.listHeader(traderHeader))));
            String[] icons = {"report", "kill", "kill", "hand_in", "unlock"};
            for (int i = 0; i < 5; i++) list.append(',').append(element("row", 308, icons[i], gson.serialize(QuestDialog.row(rows.get(i)))));
            screens.add(list.append("]}").toString());
            // 詳細
            StringBuilder det = new StringBuilder("{\"title\":\"Kovacs ― 照準の微調整\",\"footer\":[\"納品する\",\"戻る\"],\"elements\":[");
            det.append(element("header", 340, null, gson.serialize(QuestDialog.detailHeader(detail))));
            det.append(',').append(element("text", 340, null, gson.serialize(Component.text(
                    "「いい働きだった。次はこいつを試してくれ。仕入れた光学機器の精度を実戦で確かめたい。ノヴォザリエ薬品工場へ向かい、正確な射撃でSCAVを排除、無事に持ち帰るんだ。」", NOTE))));
            det.append(',').append(element("header", 340, null, gson.serialize(QuestDialog.section(UiIcon.QUEST, "目標"))));
            String[] oi = {"kill", "hand_in", "extract"};
            for (int i = 0; i < 3; i++) det.append(',').append(element("row", 308, oi[i], gson.serialize(QuestDialog.row(objectives.get(i)))));
            det.append(',').append(element("header", 340, null, gson.serialize(QuestDialog.section(UiIcon.MONEY, "報酬"))));
            String[] ri = {"money", "exp", "trade"};
            for (int i = 0; i < 3; i++) det.append(',').append(element("row", 308, ri[i], gson.serialize(QuestDialog.row(notes.get(i)))));
            screens.add(det.append("]}").toString());
            // 一覧 (PDA)
            StringBuilder pda = new StringBuilder("{\"title\":\"PDA ― タスク\",\"footer\":[\"PDA に戻る\",\"閉じる\"],\"elements\":[");
            pda.append(element("header", 340, null, gson.serialize(QuestDialog.listHeader(pdaHeader))));
            pda.append(',').append(element("row", 308, "hand_in", gson.serialize(QuestDialog.row(rows.get(5)))));
            for (int i = 0; i < 3; i++) pda.append(',').append(element("row", 308, icons[i], gson.serialize(QuestDialog.row(rows.get(i)))));
            screens.add(pda.append("]}").toString());
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("QuestDialogLayoutTest: header, rows, detail, sections, objectives and rewards fit their widths");
    }

    private static String element(String kind, int width, String icon, String component) {
        return "{\"kind\":\"" + kind + "\",\"width\":" + width + (icon != null ? ",\"icon\":\"" + icon + "\"" : "")
                + ",\"component\":" + component + "}";
    }
}
