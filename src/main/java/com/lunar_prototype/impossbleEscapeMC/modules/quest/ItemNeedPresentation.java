package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutMaterialNeeds;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** クエストと隠れ家の表示内容を決める。サーバー・パケット・アイテムの実体には触れない。 */
final class ItemNeedPresentation {
    private ItemNeedPresentation() {}
    /** 納品の目標 1 つ (クエストの名前、あと何個) */
    record Need(String questName, HandInObjective objective, int remaining) {
    }

    record Snapshot(List<Need> quests, Map<String,HideoutMaterialNeeds.Need> hideout) {
        Snapshot { quests=List.copyOf(quests);hideout=Map.copyOf(hideout); }
        boolean empty() { return quests.isEmpty() && hideout.isEmpty(); }
    }
    record Decoration(List<Component> lines, boolean glow) {
        Decoration { lines=List.copyOf(lines); }
    }

    /** 適格性はクエストと隠れ家で別に判定する。隠れ家の素材にFIR条件は足さない。 */
    static Decoration describe(String itemId, String type, boolean fir, Snapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        boolean usable = false;
        if (type != null) for (Need need : snapshot.quests()) {
            if (need.objective().matches(itemId,type,fir)) {
                usable = true;
                lines.add(line("★ クエスト「" + need.questName() + "」で納品 (あと " + need.remaining() + ")", NamedTextColor.GOLD));
            } else if (!fir && need.objective().isRequireFIR() && need.objective().matches(itemId,type,true)) {
                lines.add(line("クエスト「" + need.questName() + "」は FIR 品のみ (このアイテムは対象外)", NamedTextColor.DARK_GRAY));
            }
        }
        HideoutMaterialNeeds.Need material = snapshot.hideout().get(itemId);
        if (material != null) {
            if (!lines.isEmpty()) lines.add(Component.empty());
            lines.add(line("◆ 隠れ家の強化素材",NamedTextColor.AQUA));
            for (var use : material.uses()) lines.add(line("  " + use.facility() + " Lv." + use.level() + ": " + use.amount() + "個必要"
                    + (use.prerequisiteMissing() ? " (前提設備待ち)" : ""),NamedTextColor.GRAY));
            lines.add(line("  確保 " + Math.min(material.have(),material.required()) + " / 合計 " + material.required()
                    + (material.missing() ? " (不足 " + material.shortage() + ")" : " (確保済み)"),
                    material.missing() ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            usable |= material.missing();
        }
        return new Decoration(lines,usable);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
