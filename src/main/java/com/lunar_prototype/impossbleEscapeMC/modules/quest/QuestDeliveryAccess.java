package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NG;

/** タスクの納品・完了報告の共通制限。実行時の参加状態で判定し、通常の目標進行には使わない。 */
public final class QuestDeliveryAccess {
    private QuestDeliveryAccess() { }

    public static boolean blocked(Player player) {
        if (player == null) return false; // プレイヤー操作を伴わない目標計算・検証は対象外。
        var plugin = ImpossbleEscapeMC.getInstance();
        if (plugin == null) return false;
        var services = plugin.getServiceContainer();
        RaidModule raids = services != null ? services.get(RaidModule.class) : null;
        if (raids == null) raids = plugin.getRaidModule();
        return raids != null && raids.isInRaid(player);
    }

    public static boolean requireOutsideRaid(Player player) {
        if (!blocked(player)) return true;
        Messages.chat(player, "iem.quest.questdeliveryaccess.requireoutsideraid.notice_001", MessageText.of("iem.quest.questdeliveryaccess.requireoutsideraid.notice_001", MessageText.payload(MessageText.of("iem.quest.questdeliveryaccess.requireoutsideraid.notice_001.text_01").color(NG))));
        return false;
    }
}
