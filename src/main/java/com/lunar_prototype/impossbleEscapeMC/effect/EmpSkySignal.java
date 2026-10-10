package com.lunar_prototype.impossbleEscapeMC.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レイド終盤の EMP (ミサイル・起爆・衝撃波・オーロラ) を、リソースパックの空と雲のシェーダーに伝える。
 *
 * シェーダーにはサーバーから値を渡す口がないため、時刻同期パケットの経過tick で伝える。
 * クライアントはこれを GameTime (経過tick を 24000 で割った余り) としてシェーダーに渡すので、余りの範囲を使い分ける。
 * <ul>
 *   <li>0〜17999: 通常。全員・全ワールドの時刻パケットを、余りが必ずこの範囲になるよう書き換える。
 *       クライアントは次の同期まで自分で時刻を進めるため、演出の範囲との間を空けておく</li>
 *   <li>20000〜23999: 演出中。20000 + 演出の開始からのtick を送り、シェーダーはそこから経過時間を読む</li>
 * </ul>
 * 書き換えるのはクライアントに見せる値だけで、サーバーの経過tick (予約されたブロック更新・データパックの schedule など) には触れない。
 * シェーダーは常に有効なので、演出をしないプレイヤーの時刻も書き換え続ける必要がある。
 */
public final class EmpSkySignal extends PacketListenerAbstract {

    private static final long DAY = 24000L;
    private static final long NORMAL_RANGE = 18000L;
    /** iem_emp_time.glsl の EMP_SIGNAL_START と同じ値 */
    private static final long SIGNAL_START = 20000L;
    private static final long SIGNAL_MAX_TICKS = DAY - SIGNAL_START - 1;

    /** 演出を始めたサーバーtick と、見せる経過tick の 24000 区切りの起点 */
    private record Signal(int startTick, long base) {
    }

    private static EmpSkySignal instance;

    private final Map<UUID, Signal> signals = new ConcurrentHashMap<>();

    private EmpSkySignal() {
        super(PacketListenerPriority.NORMAL);
    }

    public static void init() {
        if (instance != null) return;
        instance = new EmpSkySignal();
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    /** 書き換えを止め、演出中だったプレイヤーには本当の時刻を送り直す */
    public static void shutdown() {
        if (instance == null) return;
        EmpSkySignal stopping = instance;
        PacketEvents.getAPI().getEventManager().unregisterListener(stopping);
        instance = null;
        for (UUID id : stopping.signals.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) resend(player);
        }
        stopping.signals.clear();
    }

    /** このプレイヤーの空で演出を始める。シェーダーの時間 0 が1発目のミサイルの発射になる */
    public static void start(Player player) {
        if (instance == null) return;
        // 時刻が戻らないよう、今見せている経過tick より後の 24000 区切りを起点にする
        long base = (normal(player.getWorld().getGameTime()) / DAY + 1) * DAY;
        instance.signals.put(player.getUniqueId(), new Signal(Bukkit.getCurrentTick(), base));
        resend(player);
    }

    /** このプレイヤーの演出を終え、通常の時刻に戻す */
    public static void stop(UUID playerId) {
        if (instance == null || instance.signals.remove(playerId) == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) resend(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.TIME_UPDATE) return;
        UUID playerId = event.getUser().getUUID();
        Signal signal = playerId != null ? signals.get(playerId) : null;
        WrapperPlayServerTimeUpdate packet = new WrapperPlayServerTimeUpdate(event);

        long shown;
        if (signal != null) {
            long elapsed = Math.min(Math.max(Bukkit.getCurrentTick() - signal.startTick(), 0), SIGNAL_MAX_TICKS);
            shown = signal.base() + SIGNAL_START + elapsed;
        } else {
            shown = normal(packet.getWorldAge());
        }
        if (shown == packet.getWorldAge()) return;
        packet.setWorldAge(shown);
        event.markForReEncode(true);
    }

    /** 余りが 0〜17999 に収まるよう、18000 進むごとに次の 24000 区切りへ飛ばす (時刻は戻らない) */
    private static long normal(long gameTime) {
        return Math.floorDiv(gameTime, NORMAL_RANGE) * DAY + Math.floorMod(gameTime, NORMAL_RANGE);
    }

    /** 個人の時刻を同じ値で設定し直すと、サーバーが即座に時刻同期パケットを送り直す (それをここで書き換える) */
    private static void resend(Player player) {
        player.setPlayerTime(player.getPlayerTimeOffset(), player.isPlayerTimeRelative());
    }
}
