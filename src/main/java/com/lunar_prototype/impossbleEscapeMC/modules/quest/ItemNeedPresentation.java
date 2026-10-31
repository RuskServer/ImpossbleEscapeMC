package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutMaterialNeeds;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

/** クエストと隠れ家の表示内容を決める。サーバー・パケット・アイテムの実体には触れない。 */
final class ItemNeedPresentation {
    private ItemNeedPresentation() {}
    /** 納品の目標 1 つ (クエストの名前、あと何個) */
    record Need(String questName, HandInObjective objective, int remaining) {}

    record Snapshot(List<Need> quests, Map<String,HideoutMaterialNeeds.Need> hideout) {
        Snapshot {
            // 受注データのMapの走査順で、説明の行が入れ替わらないようにする。
            quests=quests.stream().sorted(Comparator.comparing(Need::questName)).toList();
            hideout=Map.copyOf(hideout);
        }
        boolean empty() { return quests.isEmpty() && hideout.isEmpty(); }
    }
    record Decoration(List<Component> lines, boolean glow) {
        Decoration { lines=List.copyOf(lines); }
    }

    /** 適格性は別々に保ち、両用途を見出し・用途行・補足の共通書式で並べる。 */
    static Decoration describe(String itemId, String type, boolean fir, Snapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        List<Component> questLines = new ArrayList<>();
        boolean usable = false;
        if (type != null) for (Need need : snapshot.quests()) {
            if (need.objective().matches(itemId,type,fir)) {
                usable = true;
                questLines.add(row(need.questName(),"残り " + need.remaining() + "個",NamedTextColor.GOLD));
                if (need.objective().isRequireFIR()) questLines.add(note("FIR必須"));
            } else if (!fir && need.objective().isRequireFIR() && need.objective().matches(itemId,type,true)) {
                questLines.add(row(need.questName(),"残り " + need.remaining() + "個",NamedTextColor.DARK_GRAY));
                questLines.add(note("FIR必須・この品は対象外"));
            }
        }
        if (!questLines.isEmpty()) {
            lines.add(header("クエスト納品",usable ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
            lines.addAll(questLines);
        }
        HideoutMaterialNeeds.Need material = snapshot.hideout().get(itemId);
        if (material != null) {
            if (!lines.isEmpty()) lines.add(Component.empty());
            NamedTextColor color=material.missing() ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY;
            lines.add(header("隠れ家強化",color));
            for (var use : material.uses()) {
                lines.add(row(use.facility() + " Lv." + use.level(),"必要 " + use.amount() + "個",color));
                if (use.prerequisiteMissing()) lines.add(note("前提設備待ち"));
            }
            lines.add(row("確保 " + Math.min(material.have(),material.required()) + " / " + material.required() + "個",
                    material.missing() ? "不足 " + material.shortage() + "個" : "確保済み",color));
            usable |= material.missing();
        }
        return new Decoration(lines,usable);
    }

    private static Component header(String text,NamedTextColor color) {
        return Component.text(text,color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC,false);
    }
    private static Component row(String name,String detail,NamedTextColor color) {
        return Component.text("  " + name,NamedTextColor.GRAY)
                .append(Component.text("  " + detail,color)).decoration(TextDecoration.ITALIC,false);
    }
    private static Component note(String text) {
        return Component.text("    " + text,NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false);
    }
}
