package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Stoppable;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NG;

/** タスクの納品・完了報告の共通制限。実行時の参加状態で判定し、通常の目標進行には使わない。 */
@Service
public final class QuestDeliveryAccess implements Startable, Stoppable {
    private static QuestDeliveryAccess instance;
    private final RaidModule raids;
    @Inject public QuestDeliveryAccess(RaidModule raids) { this.raids = raids; }
    @Override public void start() { instance = this; }
    @Override public void stop() { if (instance == this) instance = null; }

    public static boolean blocked(Player player) {
        if (player == null) return false; // プレイヤー操作を伴わない目標計算・検証は対象外。
        return instance != null && instance.raids.isInRaid(player);
    }

    public static boolean requireOutsideRaid(Player player) {
        if (!blocked(player)) return true;
        Messages.chat(player, "iem.quest.questdeliveryaccess.requireoutsideraid.notice_001", MessageText.of("iem.quest.questdeliveryaccess.requireoutsideraid.notice_001", MessageText.payload(MessageText.of("iem.quest.questdeliveryaccess.requireoutsideraid.notice_001.text_01").color(NG))));
        return false;
    }
}
