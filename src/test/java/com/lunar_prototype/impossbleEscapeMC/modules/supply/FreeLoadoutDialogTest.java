package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 武器切替後の古い受取・他画面の操作・二重受取と、装備一覧の幅を検証する。 */
public final class FreeLoadoutDialogTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var selection = new SupplyLoadoutGUI.Selection();
        String initial = selection.key("receive");
        check(selection.weapon() == SupplyLoadout.Weapon.SHOTGUN && selection.accepts(initial), "initial choice");
        check(!selection.accepts(new SupplyLoadoutGUI.Selection().key("receive")), "other dialog rejected");
        selection.select(SupplyLoadout.Weapon.SMG);
        check(!selection.accepts(initial), "receive from before weapon switch rejected");
        check(selection.weapon() == SupplyLoadout.Weapon.SMG, "selected weapon matches preview");
        String failed = selection.key("receive");
        selection.refresh();
        check(!selection.accepts(failed), "failed receive invalidates queued duplicates");
        String current = selection.key("receive");
        check(selection.accepts(current) && selection.finish(), "current choice consumed once");
        check(!selection.accepts(current) && !selection.finish(), "double receive and closed screen rejected");
        selection.select(SupplyLoadout.Weapon.PISTOL);
        check(selection.weapon() == SupplyLoadout.Weapon.SMG, "closed choice immutable");
        var plain = PlainTextComponentSerializer.plainText();
        for (String name : new String[]{"MP5", "中古クラス2アーマー", "非常に長い名前の無料装備のアイテムが追加された場合でも数量は見える"}) {
            var lines = SupplyLoadoutGUI.itemLines(name, 64);
            for (var line : lines) check(line.x == TerminalFont.ROW_LINE_W && line.max <= TerminalFont.ROW_LINE_W, "fixed row width: " + name);
            check(plain.serialize(lines.get(1).build()).contains("数量 64"), "visible amount");
        }
        System.out.println("FreeLoadoutDialogTest passed: stale choices, duplicate receive, closed sessions and item rows");
    }
}
