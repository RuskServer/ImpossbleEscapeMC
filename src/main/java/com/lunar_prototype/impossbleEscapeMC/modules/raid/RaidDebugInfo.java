package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レイド中のプレイヤーだけ、デバッグ画面 (F3) の情報を減らす (座標・向き・チャンク・見ているブロックなど。ゲームルールの reducedDebugInfo と同じ)。
 *
 * クライアントは自分のエンティティ宛ての「エンティティの状態」パケットの値 22 (減らす) / 23 (戻す) で切り替える。
 * 毎秒レイド中かを見て、変わったら本人にだけ送る。サーバーがリスポーンやワールド移動で自分から送る値は、レイド中なら 22 に書き換える
 * (ワールドのゲームルールには触れない)。
 * クライアントの MOD (ミニマップ・座標表示など) が自分で出す座標は消せない
 */
public final class RaidDebugInfo extends PacketListenerAbstract {

    private static final byte REDUCED = 22;
    private static final byte FULL = 23;
    private static final int CHECK_INTERVAL_TICKS = 20;

    private static RaidDebugInfo instance;

    private final ImpossbleEscapeMC plugin;
    /** 減らしているプレイヤー (パケットのスレッドからも読む) */
    private final Set<UUID> reduced = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    private RaidDebugInfo(ImpossbleEscapeMC plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new RaidDebugInfo(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, instance::check, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    /** 書き換えを止め、減らしていたプレイヤーには戻す値を送る */
    public static void shutdown() {
        if (instance == null) return;
        RaidDebugInfo stopping = instance;
        instance = null;
        stopping.task.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(stopping);
        for (UUID id : stopping.reduced) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) send(player, FULL);
        }
        stopping.reduced.clear();
    }

    /** レイド中かが変わった人に、減らす・戻すを送る */
    private void check() {
        RaidModule raids = plugin.getRaidModule();
        reduced.removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean inRaid = raids != null && raids.isInRaid(player);
            if (inRaid ? reduced.add(player.getUniqueId()) : reduced.remove(player.getUniqueId())) {
                send(player, inRaid ? REDUCED : FULL);
            }
        }
    }

    private static void send(Player player, byte status) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityStatus(player.getEntityId(), status));
    }

    /** サーバーが本人に送る「戻す」を、レイド中なら「減らす」にする */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) return;
        UUID playerId = event.getUser().getUUID();
        if (playerId == null || !reduced.contains(playerId)) return;
        WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
        if (packet.getStatus() != FULL || packet.getEntityId() != event.getUser().getEntityId()) return;
        packet.setStatus(REDUCED);
        event.markForReEncode(true);
    }
}
