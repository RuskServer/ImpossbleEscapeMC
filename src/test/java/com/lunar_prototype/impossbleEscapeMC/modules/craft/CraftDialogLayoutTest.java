package com.lunar_prototype.impossbleEscapeMC.modules.craft;

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
 * クラフトの画面: 上の端末・レシピの行・キューの行が、行の幅を超えず、ちょうど行の幅で終わる (クライアントが途中で改行しない・中央揃えでずれない)。
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面 (ワークベンチの製作・弾薬製造の製作・キュー) を JSON で書き出す
 * (IEM_shader/tools/dialog_render.py で絵にする)
 */
public final class CraftDialogLayoutTest {
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

    private static String screen(String title, List<String> footer, List<String> elements) {
        return "{\"title\":\"" + title + "\",\"footer\":[" + String.join(",", footer.stream().map(f -> "\"" + f + "\"").toList())
                + "],\"elements\":[" + String.join(",", elements) + "]}";
    }

    public static void main(String[] args) throws Exception {
        List<CraftDialog.Tab> craftTabs = List.of(new CraftDialog.Tab("製作", true, "screen/craft"), new CraftDialog.Tab("キュー 2/3", false, "screen/queue"));
        List<CraftDialog.Tab> queueTabs = List.of(new CraftDialog.Tab("製作", false, "screen/craft"), new CraftDialog.Tab("キュー 2/3", true, "screen/queue"));
        CraftDialog.HeaderView workbench = new CraftDialog.HeaderView("ワークベンチ", 2, 2, 3, 1, craftTabs, List.of(
                new CraftDialog.Tab("すべて", true, "cat/all"), new CraftDialog.Tab("アタッチメント", false, "cat/attachment"),
                new CraftDialog.Tab("部品", false, "cat/parts"), new CraftDialog.Tab("修理キット", false, "cat/repair")));
        CraftDialog.HeaderView ammo = new CraftDialog.HeaderView("弾薬製造", 1, 3, 3, 0, List.of(
                new CraftDialog.Tab("製作", true, "screen/craft"), new CraftDialog.Tab("キュー 3/3", false, "screen/queue")), List.of());
        CraftDialog.HeaderView queue = new CraftDialog.HeaderView("ワークベンチ", 2, 2, 3, 1, queueTabs, List.of());
        CraftDialog.HeaderView longName = new CraftDialog.HeaderView("とても長い設備の名前がここに入る場合の確認用の文字列", 4, 12, 12, 9,
                craftTabs, List.of(new CraftDialog.Tab("とても長い種類の名前その一", true, "a"), new CraftDialog.Tab("とても長い種類の名前その二", false, "b"),
                new CraftDialog.Tab("とても長い種類の名前その三", false, "c"), new CraftDialog.Tab("とても長い種類の名前その四", false, "d")));

        CraftDialog.RecipeView bolts = new CraftDialog.RecipeView("強化ボルト", 4, "1:30",
                List.of(new CraftDialog.Need("ボルト", 6, 4), new CraftDialog.Need("スクラップ", 2, 2)), CraftDialog.RecipeState.READY, null, "make/0");
        CraftDialog.RecipeView toolbox = new CraftDialog.RecipeView("工具箱", 1, "5:00",
                List.of(new CraftDialog.Need("レンチ", 1, 2), new CraftDialog.Need("ドライバー", 2, 2), new CraftDialog.Need("ボルト", 6, 4)),
                CraftDialog.RecipeState.SHORT, null, "make/1");
        CraftDialog.RecipeView suppressor = new CraftDialog.RecipeView("サプレッサー (9mm)", 1, "12:00",
                List.of(new CraftDialog.Need("グラフィックボード", 0, 1), new CraftDialog.Need("工具箱", 1, 1)), CraftDialog.RecipeState.LOCKED,
                "Lv.3 で解放", "make/2");
        CraftDialog.RecipeView m855 = new CraftDialog.RecipeView("5.56x45mm M855", 60, "4:00",
                List.of(new CraftDialog.Need("火薬", 3, 2), new CraftDialog.Need("真鍮の薬莢", 80, 60)), CraftDialog.RecipeState.QUEUE_FULL, null, "make/0");
        CraftDialog.RecipeView m193 = new CraftDialog.RecipeView("5.56x45mm M193", 60, "3:00",
                List.of(new CraftDialog.Need("火薬", 3, 2), new CraftDialog.Need("真鍮の薬莢", 80, 60)), CraftDialog.RecipeState.QUEUE_FULL, null, "make/1");
        CraftDialog.RecipeView longRecipe = new CraftDialog.RecipeView("とても長いレシピの名前がここに入る場合の確認用の文字列です", 999, "99:59",
                List.of(new CraftDialog.Need("とても長い素材の名前その一", 999, 999), new CraftDialog.Need("とても長い素材の名前その二", 0, 999)),
                CraftDialog.RecipeState.READY, null, "make/99");

        CraftDialog.QueueView doneRow = new CraftDialog.QueueView("5.56x45mm M855", 60, CraftDialog.QueueState.DONE, 1.0, "0:00", 0, "take/0");
        CraftDialog.QueueView crafting = new CraftDialog.QueueView("強化ボルト", 4, CraftDialog.QueueState.CRAFTING, 0.45, "0:49", 0, "cancel/1");
        CraftDialog.QueueView waiting = new CraftDialog.QueueView("工具箱", 1, CraftDialog.QueueState.WAITING, 0.0, "5:00", 1, "cancel/2");

        // ---- 幅 ----
        for (CraftDialog.HeaderView h : List.of(workbench, ammo, queue, longName)) {
            List<DialogLine> lines = CraftDialog.headerLines(h);
            check(lines.size() == CraftDialog.HEADER_LINES, "header lines " + lines.size());
            checkWidths(lines, CraftDialog.HEADER_W, "header " + h.facility());
        }
        for (CraftDialog.RecipeView r : List.of(bolts, toolbox, suppressor, m855, m193, longRecipe)) {
            checkWidths(CraftDialog.recipeLines(r), CraftDialog.ROW_LINE_W, "recipe " + r.name());
        }
        for (CraftDialog.QueueView q : List.of(doneRow, crafting, waiting)) {
            checkWidths(CraftDialog.queueLines(q), CraftDialog.ROW_LINE_W, "queue " + q.name());
        }

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            List<String> screens = new ArrayList<>();
            screens.add(screen("ワークベンチ ― 製作", List.of("閉じる"), List.of(
                    element("header", CraftDialog.HEADER_BODY_W, null, CraftDialog.headerLines(workbench)),
                    element("row", CraftDialog.ROW_BODY_W, "mc:iron_nugget", CraftDialog.recipeLines(bolts)),
                    element("row", CraftDialog.ROW_BODY_W, "mc:shears", CraftDialog.recipeLines(toolbox)),
                    element("row", CraftDialog.ROW_BODY_W, "mc:iron_ingot", CraftDialog.recipeLines(suppressor)))));
            screens.add(screen("弾薬製造 ― 製作", List.of("閉じる"), List.of(
                    element("header", CraftDialog.HEADER_BODY_W, null, CraftDialog.headerLines(ammo)),
                    element("row", CraftDialog.ROW_BODY_W, "ammo/556x45_m855", CraftDialog.recipeLines(m855)),
                    element("row", CraftDialog.ROW_BODY_W, "ammo/556x45_m193", CraftDialog.recipeLines(m193)))));
            screens.add(screen("ワークベンチ ― キュー", List.of("閉じる"), List.of(
                    element("header", CraftDialog.HEADER_BODY_W, null, CraftDialog.headerLines(queue)),
                    element("row", CraftDialog.ROW_BODY_W, "ammo/556x45_m855", CraftDialog.queueLines(doneRow)),
                    element("row", CraftDialog.ROW_BODY_W, "mc:iron_nugget", CraftDialog.queueLines(crafting)),
                    element("row", CraftDialog.ROW_BODY_W, "mc:shears", CraftDialog.queueLines(waiting)))));
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("CraftDialogLayoutTest OK");
    }
}
