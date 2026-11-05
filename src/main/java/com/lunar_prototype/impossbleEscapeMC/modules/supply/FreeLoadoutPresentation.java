package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.*;

/** 出撃画面とリザルトで共通の無料装備の案内。受取可否の本判定は SupplyLoadout に任せる。 */
public final class FreeLoadoutPresentation {
    public static final String TITLE = "無料装備";
    public enum State { READY, WAITING, INVENTORY, LOADING, IN_RAID }
    public record Status(State state, long remaining, long interval, String reason) {
        public String shortLabel() {
            return switch (state) {
                case READY -> "受取可";
                case WAITING -> SupplyLoadout.formatDuration(remaining);
                case INVENTORY -> "要整理";
                case LOADING -> "読込中";
                case IN_RAID -> "レイド中";
            };
        }
    }

    private FreeLoadoutPresentation() {}

    public static Status capture(Player player) {
        var services = ImpossbleEscapeMC.getInstance().getServiceContainer();
        PlayerDataModule module = services.get(PlayerDataModule.class);
        PlayerData data = module != null ? module.getPlayerData(player.getUniqueId()) : null;
        RaidModule raids = services.get(RaidModule.class);
        return status(data != null, raids != null && raids.isInRaid(player),
                SupplyLoadout.remainingCooldown(data), SupplyLoadout.cooldownMillis(data),
                SupplyLoadout.blockingReason(player, data));
    }

    static Status status(boolean loaded, boolean inRaid, long remaining, long interval, String reason) {
        State state = inRaid ? State.IN_RAID : !loaded ? State.LOADING : remaining > 0 ? State.WAITING
                : reason != null ? State.INVENTORY : State.READY;
        return new Status(state, remaining, interval, reason);
    }

    public static Component buttonLabel(Status status) {
        return Component.empty().append(UiIcon.TRADE.glyph()).append(Component.text(" " + TITLE, AMBER).decorate(TextDecoration.BOLD))
                .append(Component.text(" · " + status.shortLabel(), status.state() == State.READY ? OK : NOTE));
    }

    public static List<Component> summary(Status status) {
        return List.of(
                Component.text("武器・弾薬・最低限の装備を無料で受け取れます", NAME),
                Component.text("ショットガン / SMG / ピストルから選択", NOTE),
                Component.text("状態: " + (status.reason() == null ? "今すぐ受け取れます" : status.reason()),
                        status.state() == State.READY ? OK : AMBER),
                Component.text("受取前に持ち物と装備を空にしてください", NOTE),
                Component.text("受取後の待ち時間: " + status.interval() / 60000 + " 分", DIM),
                Component.text("支給品は売値 0₽・納品不可。中身の確認はいつでもできます", DIM));
    }

    public static Component tooltip(Status status) {
        return Component.join(JoinConfiguration.newlines(), summary(status));
    }
}
