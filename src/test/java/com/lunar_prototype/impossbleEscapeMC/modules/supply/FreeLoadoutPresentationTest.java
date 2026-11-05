package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static com.lunar_prototype.impossbleEscapeMC.modules.supply.FreeLoadoutPresentation.*;

/** 実際の受取制約の優先順と、ホバーなしでも分かる案内を検証する。 */
public final class FreeLoadoutPresentationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var ready = status(true, false, 0, 900000, null);
        check(ready.state() == State.READY, "ready");
        check(status(false, true, 1000, 900000, "raid").state() == State.IN_RAID, "raid first");
        check(status(false, false, 1000, 900000, "loading").state() == State.LOADING, "loading first");
        var waiting = status(true, false, 61001, 720000, "次に受け取れるまで 1:02");
        check(waiting.state() == State.WAITING && waiting.shortLabel().equals("1:02"), "ceil countdown");
        var inventory = status(true, false, 0, 900000, "持ち物を空にしてください");
        check(inventory.state() == State.INVENTORY && inventory.shortLabel().equals("要整理"), "inventory block");
        var plain = PlainTextComponentSerializer.plainText();
        var label = buttonLabel(ready);
        check(label.font() == null, "icon font must not become the label parent font");
        check(label.children().getFirst().font().equals(net.kyori.adventure.key.Key.key("iem", "icon")), "icon keeps its own font");
        for (var child : label.children().subList(1, label.children().size()))
            check(child.font() == null, "text uses the default font instead of inheriting icon glyphs");
        check(plain.serialize(buttonLabel(ready)).contains("無料装備 · 受取可"), "visible name and availability");
        check(summary(inventory).stream().map(plain::serialize).anyMatch(s -> s.contains(inventory.reason())), "reason in body");
        check(summary(waiting).stream().map(plain::serialize).anyMatch(s -> s.contains("12 分")), "skill-adjusted interval");
        check(summary(ready).stream().noneMatch(c -> c.hoverEvent() != null), "essential guidance needs no hover");
        System.out.println("FreeLoadoutPresentationTest passed");
    }
}
