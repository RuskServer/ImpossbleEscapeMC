package com.lunar_prototype.impossbleEscapeMC.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 26.3のポストエフェクト ({@code player.postEffects()}) で、アドレナリン放出時・死亡時の画面演出を流す。
 *
 * エフェクト本体 (assets/iem/post_effect/*.json と shaders/post/*.fsh) はサーバーのリソースパックで配る。
 * ポストエフェクトはパラメーターを送れないため、強さ違いのエフェクトを時間割 (Step) で切り替えて演出する。
 * 付け外しするのは iem 名前空間のエフェクトだけで、他のポストエフェクトには触れない。
 *
 * ポストエフェクトはプレイヤーデータに保存されリスポーン後も引き継がれるため、参加・退出・停止時に掃除する。
 * また、リスポーンやワールド移動の読み込み中に演出が終わってしまわないよう、
 * クライアントがワールドを読み込み終わるまで時間割を進めない。
 * 読み込み完了はクライアントの PLAYER_LOADED パケットで判断する。Paperの PlayerClientLoadedWorldEvent は
 * 60tickで打ち切られ (タイムアウト)、その後に読み込みが終わっても再び呼ばれないため、重いワールドでは早すぎる。
 */
public final class ScreenEffectService implements Listener {

    private static final String NAMESPACE = "iem";
    private static final Key ADRENALINE_ONSET = Key.key(NAMESPACE, "adrenaline_onset");
    private static final Key ADRENALINE = Key.key(NAMESPACE, "adrenaline");
    private static final Key ADRENALINE_FADE = Key.key(NAMESPACE, "adrenaline_fade");
    private static final Key DEATH_IMPACT = Key.key(NAMESPACE, "death_impact");
    private static final Key DEATH_BLACKOUT = Key.key(NAMESPACE, "death_blackout");
    private static final Key DEATH_FADE = Key.key(NAMESPACE, "death_fade");
    private static final Key DEATH_RECOVER = Key.key(NAMESPACE, "death_recover");

    private static final int ADRENALINE_ONSET_TICKS = 4;
    private static final int ADRENALINE_FADE_TICKS = 12;
    // 死亡演出は合計4秒: 衝撃 → 暗転 → 薄れる → 回復。
    // 即時リスポーンで死んだ瞬間にロビーへ戻るため、ロビーに着いてから死んだことを受け止める間を取る
    private static final int DEATH_IMPACT_TICKS = 6;
    private static final int DEATH_BLACKOUT_TICKS = 28;
    private static final int DEATH_FADE_TICKS = 26;
    private static final int DEATH_RECOVER_TICKS = 20;
    /** 心拍の周期。adrenaline.fsh の BEAT_TICKS と同じ値にして、心音と画面の脈動を揃える */
    private static final int HEARTBEAT_TICKS = 10;
    /** クライアントの読み込み完了の通知が来ない場合に待つのをやめるまで */
    private static final int LOAD_WAIT_TIMEOUT_TICKS = 400;
    /** 読み込み完了から演出を始めるまでの間。読み込み画面が閉じた直後は周りの描画が追いついていない */
    private static final int START_DELAY_AFTER_LOAD_TICKS = 10;

    /** 時間割の1段。effect が null の段はエフェクト無し */
    private record Step(Key effect, int ticks, boolean heartbeat) {
    }

    private static final class Playback {
        private final List<Step> steps;
        private int index;
        private int remaining;
        private boolean started;

        private Playback(List<Step> steps) {
            this.steps = steps;
            this.remaining = steps.getFirst().ticks();
        }

        private Step current() {
            return steps.get(index);
        }
    }

    private static ScreenEffectService instance;

    private final Map<UUID, Playback> playing = new HashMap<>();
    /** ワールドの読み込み待ちのプレイヤーと、待ち始めたtick */
    private final Map<UUID, Integer> awaitingLoad = new HashMap<>();
    /** 読み込みが終わったプレイヤーと、演出を進めてよくなるtick */
    private final Map<UUID, Integer> settlingUntil = new HashMap<>();
    /** 死亡してまだリスポーンしていないプレイヤー */
    private final Set<UUID> awaitingRespawn = new HashSet<>();
    private final BukkitTask tickTask;
    private final PlayerLoadedListener playerLoadedListener;

    private ScreenEffectService(Plugin plugin) {
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        this.playerLoadedListener = new PlayerLoadedListener(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(playerLoadedListener);
    }

    /** クライアントがワールドを読み込み終えた時に送るパケット (参加・リスポーン・ワールド移動のたび) を見る */
    private final class PlayerLoadedListener extends PacketListenerAbstract {
        private final Plugin plugin;

        private PlayerLoadedListener(Plugin plugin) {
            super(PacketListenerPriority.MONITOR);
            this.plugin = plugin;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() != PacketType.Play.Client.PLAYER_LOADED) return;
            UUID playerId = event.getUser().getUUID();
            if (playerId == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) onWorldLoaded(player);
            });
        }
    }

    public static void init(Plugin plugin) {
        if (instance != null) return;
        instance = new ScreenEffectService(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** 演出を止め、オンラインの全員から iem のエフェクトを外す */
    public static void shutdown() {
        if (instance == null) return;
        instance.tickTask.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance.playerLoadedListener);
        Bukkit.getOnlinePlayers().forEach(player -> apply(player, null));
        instance = null;
    }

    /** アドレナリン放出の演出 (強い一撃 → 心拍で脈打つ → 弱めて終わる) を、効果時間に合わせて流す */
    public static void playAdrenaline(Player player, long durationMillis) {
        if (instance == null) return;
        int totalTicks = (int) (durationMillis / 50);
        int sustainTicks = Math.max(1, totalTicks - ADRENALINE_ONSET_TICKS - ADRENALINE_FADE_TICKS);
        instance.start(player, List.of(
                new Step(ADRENALINE_ONSET, ADRENALINE_ONSET_TICKS, true),
                new Step(ADRENALINE, sustainTicks, true),
                new Step(ADRENALINE_FADE, ADRENALINE_FADE_TICKS, true)));
    }

    private void start(Player player, List<Step> steps) {
        Playback playback = new Playback(steps);
        playing.put(player.getUniqueId(), playback);
        if (canAdvance(player.getUniqueId())) {
            begin(player, playback);
        }
    }

    private void begin(Player player, Playback playback) {
        playback.started = true;
        apply(player, playback.current().effect());
        onStepStart(player, playback.current());
    }

    private boolean canAdvance(UUID playerId) {
        return !awaitingRespawn.contains(playerId) && !awaitingLoad.containsKey(playerId)
                && Bukkit.getCurrentTick() >= settlingUntil.getOrDefault(playerId, 0);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        awaitingLoad.values().removeIf(since -> now - since >= LOAD_WAIT_TIMEOUT_TICKS);
        settlingUntil.values().removeIf(until -> now >= until);

        for (Map.Entry<UUID, Playback> entry : new ArrayList<>(playing.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Playback playback = entry.getValue();
            if (player == null) {
                playing.remove(entry.getKey());
                continue;
            }
            if (!canAdvance(entry.getKey())) continue;
            if (!playback.started) {
                begin(player, playback);
                continue;
            }

            if (playback.current().heartbeat() && player.getWorld().getGameTime() % HEARTBEAT_TICKS == 0) {
                player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            if (--playback.remaining > 0) continue;

            playback.index++;
            if (playback.index >= playback.steps.size()) {
                playing.remove(entry.getKey());
                apply(player, null);
            } else {
                playback.remaining = playback.current().ticks();
                apply(player, playback.current().effect());
                onStepStart(player, playback.current());
            }
        }
    }

    private static void onStepStart(Player player, Step step) {
        if (DEATH_IMPACT.equals(step.effect())) {
            // 不意を突く低い衝撃音と耳鳴り
            player.playSound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.6f);
            player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
    }

    /** iem 以外のポストエフェクトは残したまま、iem のエフェクトを effect だけにする (null なら外す) */
    private static void apply(Player player, Key effect) {
        List<Key> effects = new ArrayList<>();
        for (Key existing : player.postEffects().values()) {
            if (!NAMESPACE.equals(existing.namespace())) effects.add(existing);
        }
        if (effect != null) effects.add(effect);
        player.postEffects().set(effects);
    }

    // --- 死亡・リスポーン・ワールド移動 ---

    /** 即時リスポーンの環境では死亡画面が出ないため、死亡演出はリスポーン先の読み込み完了後に流す */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        awaitingRespawn.add(player.getUniqueId());
        apply(player, null);
        playing.put(player.getUniqueId(), new Playback(List.of(
                new Step(DEATH_IMPACT, DEATH_IMPACT_TICKS, false),
                new Step(DEATH_BLACKOUT, DEATH_BLACKOUT_TICKS, false),
                new Step(DEATH_FADE, DEATH_FADE_TICKS, false),
                new Step(DEATH_RECOVER, DEATH_RECOVER_TICKS, false))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        awaitingRespawn.remove(playerId);
        awaitingLoad.put(playerId, Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        awaitingLoad.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        // タイムアウトは読み込み完了ではない (重いワールドではまだ読み込み画面のまま)。PLAYER_LOADED パケットを待つ
        if (event.isTimeout()) return;
        onWorldLoaded(event.getPlayer());
    }

    private void onWorldLoaded(Player player) {
        // 読み込み完了の通知はイベントとパケットの両方から届くため、1回だけ扱う
        if (awaitingLoad.remove(player.getUniqueId()) == null) return;
        settlingUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + START_DELAY_AFTER_LOAD_TICKS);
        // ワールドの切り替えでクライアントの表示がリセットされていても、今の段のエフェクトを確実に送り直す
        Playback playback = playing.get(player.getUniqueId());
        if (playback != null && playback.started) {
            apply(player, playback.current().effect());
            player.postEffects().update();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 前回の演出の途中で抜けていた場合など、保存されて残ったエフェクトを外す
        apply(event.getPlayer(), null);
        awaitingLoad.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playing.remove(playerId);
        awaitingLoad.remove(playerId);
        settlingUntil.remove(playerId);
        awaitingRespawn.remove(playerId);
        // 保存されて次回の参加時に残らないよう、保存前に外しておく
        apply(event.getPlayer(), null);
    }
}
