package com.lunar_prototype.impossbleEscapeMC.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidEndSequence;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 26.3のポストエフェクト ({@code player.postEffects()}) で画面効果をかける。
 *
 * エフェクト本体 (assets/iem/post_effect/*.json と shaders/post/*.fsh) はサーバーのリソースパックで配る。
 * クライアントは送られた順にポストエフェクトを重ねて描くため、次の層を下から順に重ねる。
 * <ol>
 *   <li>空: レイド終盤の起爆前に空だけを赤く染める iem:emp_precursor_* ({@link RaidEndSequence} が段階を決める)</li>
 *   <li>色味: レイド中は常に iem:raid_grade</li>
 *   <li>負傷: 出血中は iem:bleeding、出血中で体力が少ない (瀕死) 時は iem:bleeding_critical</li>
 *   <li>演出: アドレナリン放出・死亡・レイド開始・EMP の閃光。パラメーターを送れないため、強さ違いのエフェクトを時間割 (Step) で切り替える</li>
 *   <li>被弾: 撃たれた瞬間の iem:hit → iem:hit_fade</li>
 * </ol>
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
    private static final Key RAID_GRADE = Key.key(NAMESPACE, "raid_grade");
    private static final Key BLEEDING = Key.key(NAMESPACE, "bleeding");
    private static final Key BLEEDING_CRITICAL = Key.key(NAMESPACE, "bleeding_critical");
    private static final Key HIT = Key.key(NAMESPACE, "hit");
    private static final Key HIT_FADE = Key.key(NAMESPACE, "hit_fade");
    private static final Key EMP_FLASH = Key.key(NAMESPACE, "emp_flash");
    /** EMP の閃光は emp_flash (真っ白) から emp_flash_fade_1〜3 へ順に薄れる */
    private static final int EMP_FLASH_FADE_STAGES = 3;
    private static final int EMP_FLASH_TICKS = 3;
    private static final int[] EMP_FLASH_FADE_TICKS = {4, 6, 10};
    /** レイド開始の演出。intro_0 (真っ黒) から intro_6 (ほぼ素の画面) へ順に明るくする */
    private static final int INTRO_STAGES = 7;
    private static final int INTRO_BLACK_TICKS = 20;
    private static final int INTRO_STAGE_TICKS = 8;

    private static final int ADRENALINE_ONSET_TICKS = 4;
    private static final int ADRENALINE_FADE_TICKS = 12;
    // 死亡演出は合計4秒: 衝撃 → 暗転 → 薄れる → 回復。
    // 即時リスポーンで死んだ瞬間にロビーへ戻るため、ロビーに着いてから死んだことを受け止める間を取る
    private static final int DEATH_IMPACT_TICKS = 6;
    private static final int DEATH_BLACKOUT_TICKS = 28;
    private static final int DEATH_FADE_TICKS = 26;
    private static final int DEATH_RECOVER_TICKS = 20;
    private static final int HIT_TICKS = 2;
    private static final int HIT_FADE_TICKS = 3;
    /** 心拍の周期。adrenaline.fsh の BEAT_TICKS と同じ値にして、心音と画面の脈動を揃える */
    private static final int HEARTBEAT_TICKS = 10;
    /** 瀕死の心拍の周期。bleeding_critical.json の BeatTicks と同じ値にして、心音と画面の脈動を揃える */
    private static final int CRITICAL_HEARTBEAT_TICKS = 24;
    /** 出血中にこの割合を下回る体力を瀕死とみなす */
    private static final double CRITICAL_HEALTH_RATIO = 0.3;
    /** 色味・負傷の層を見直す間隔 (tick) */
    private static final int STATE_UPDATE_INTERVAL_TICKS = 5;
    /** クライアントの読み込み完了の通知が来ない場合に待つのをやめるまで */
    private static final int LOAD_WAIT_TIMEOUT_TICKS = 400;
    /** 読み込み完了から演出を始めるまでの間。読み込み画面が閉じた直後は周りの描画が追いついていない */
    private static final int START_DELAY_AFTER_LOAD_TICKS = 10;

    /** 時間割の1段。effect が null の段はエフェクト無し */
    private record Step(Key effect, int ticks, boolean heartbeat) {
    }

    private static final class Playback {
        private final List<Step> steps;
        /** 読み込み待ちの間も最初の段を表示しておくか (時間割は読み込みが終わってから進める) */
        private final boolean showWhileWaiting;
        private int index;
        private int remaining;
        private boolean started;

        private Playback(List<Step> steps) {
            this(steps, false);
        }

        private Playback(List<Step> steps, boolean showWhileWaiting) {
            this.steps = steps;
            this.showWhileWaiting = showWhileWaiting;
            this.remaining = steps.getFirst().ticks();
        }

        private Step current() {
            return steps.get(index);
        }
    }

    private static ScreenEffectService instance;

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Playback> playing = new HashMap<>();
    /** 空の層のエフェクト (レイド終盤) */
    private final Map<UUID, Key> sky = new HashMap<>();
    /** 色味の層をかけているプレイヤー (レイド中) */
    private final Set<UUID> graded = new HashSet<>();
    /** 負傷の層のエフェクト (出血中・瀕死) */
    private final Map<UUID, Key> wound = new HashMap<>();
    /** 被弾した tick */
    private final Map<UUID, Integer> hitTick = new HashMap<>();
    /** ワールドの読み込み待ちのプレイヤーと、待ち始めたtick */
    private final Map<UUID, Integer> awaitingLoad = new HashMap<>();
    /** 読み込みが終わったプレイヤーと、演出を進めてよくなるtick */
    private final Map<UUID, Integer> settlingUntil = new HashMap<>();
    /** 死亡してまだリスポーンしていないプレイヤー */
    private final Set<UUID> awaitingRespawn = new HashSet<>();
    private final BukkitTask tickTask;
    private final PlayerLoadedListener playerLoadedListener;

    private ScreenEffectService(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        this.playerLoadedListener = new PlayerLoadedListener();
        PacketEvents.getAPI().getEventManager().registerListener(playerLoadedListener);
    }

    /** クライアントがワールドを読み込み終えた時に送るパケット (参加・リスポーン・ワールド移動のたび) を見る */
    private final class PlayerLoadedListener extends PacketListenerAbstract {
        private PlayerLoadedListener() {
            super(PacketListenerPriority.MONITOR);
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

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new ScreenEffectService(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** 演出を止め、オンラインの全員から iem のエフェクトを外す */
    public static void shutdown() {
        if (instance == null) return;
        instance.tickTask.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance.playerLoadedListener);
        Bukkit.getOnlinePlayers().forEach(ScreenEffectService::clearIem);
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

    /**
     * レイド開始の演出 (暗転から、白黒・ぼかし・フィルムグレイン・上下の黒帯が薄れていく)。
     * レイドワールドへテレポートした後に呼ぶ。読み込み画面が閉じた瞬間に素の画面が見えないよう、読み込み中から真っ黒にしておく
     */
    public static void playRaidIntro(Player player) {
        if (instance == null) return;
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Key.key(NAMESPACE, "intro_0"), INTRO_BLACK_TICKS, false));
        for (int i = 1; i < INTRO_STAGES; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "intro_" + i), INTRO_STAGE_TICKS, false));
        }
        instance.start(player, steps, true);
    }

    /** 上空で EMP が起爆した瞬間の閃光 (真っ白に飛んで、揺れと色収差を残しながら戻る) */
    public static void playEmpFlash(Player player) {
        if (instance == null) return;
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(EMP_FLASH, EMP_FLASH_TICKS, false));
        for (int i = 1; i <= EMP_FLASH_FADE_STAGES; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "emp_flash_fade_" + i), EMP_FLASH_FADE_TICKS[i - 1], false));
        }
        instance.start(player, steps);
    }

    private void start(Player player, List<Step> steps) {
        start(player, steps, false);
    }

    private void start(Player player, List<Step> steps, boolean showWhileWaiting) {
        Playback playback = new Playback(steps, showWhileWaiting);
        playing.put(player.getUniqueId(), playback);
        if (canAdvance(player.getUniqueId())) {
            begin(player, playback);
        } else {
            refresh(player);
        }
    }

    private void begin(Player player, Playback playback) {
        playback.started = true;
        refresh(player);
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

        if (now % STATE_UPDATE_INTERVAL_TICKS == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateGradeAndWound(player);
            }
        }
        tickHits(now);
        tickCriticalHeartbeat();

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
            } else {
                playback.remaining = playback.current().ticks();
                onStepStart(player, playback.current());
            }
            refresh(player);
        }
    }

    private static void onStepStart(Player player, Step step) {
        if (DEATH_IMPACT.equals(step.effect())) {
            // 不意を突く低い衝撃音と耳鳴り
            player.playSound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.6f);
            player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
    }

    // --- 空・色味・負傷・被弾の層 ---

    /** レイド中なら色味 (終盤は空も)、出血中なら負傷の層をかける */
    private void updateGradeAndWound(Player player) {
        UUID playerId = player.getUniqueId();
        boolean alive = !player.isDead() && !awaitingRespawn.contains(playerId)
                && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE;

        RaidInstance raid = alive && plugin.getRaidModule() != null ? plugin.getRaidModule().getRaidOf(player) : null;
        Key skyEffect = raid != null ? raid.getEndSequence().skyEffect() : null;
        Key woundEffect = alive ? woundEffectOf(player) : null;

        boolean changed = raid != null ? graded.add(playerId) : graded.remove(playerId);
        Key previousSky = skyEffect != null ? sky.put(playerId, skyEffect) : sky.remove(playerId);
        changed |= previousSky != skyEffect;
        Key previous = woundEffect != null ? wound.put(playerId, woundEffect) : wound.remove(playerId);
        changed |= previous != woundEffect;
        if (changed) refresh(player);
    }

    private Key woundEffectOf(Player player) {
        PlayerDataModule dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        if (data == null || data.getBleedingLevel() <= 0) return null;
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double ratio = maxHealth != null ? player.getHealth() / maxHealth.getValue() : 1.0;
        return ratio < CRITICAL_HEALTH_RATIO ? BLEEDING_CRITICAL : BLEEDING;
    }

    /** 被弾の層を、撃たれてからの経過に合わせて hit → hit_fade → 無し と進める */
    private void tickHits(int now) {
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(hitTick.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            int elapsed = now - entry.getValue();
            if (player == null || elapsed >= HIT_TICKS + HIT_FADE_TICKS) hitTick.remove(entry.getKey());
            if (player != null && (elapsed == HIT_TICKS || elapsed >= HIT_TICKS + HIT_FADE_TICKS)) refresh(player);
        }
    }

    private Key hitEffectOf(UUID playerId) {
        Integer tick = hitTick.get(playerId);
        if (tick == null) return null;
        return Bukkit.getCurrentTick() - tick < HIT_TICKS ? HIT : HIT_FADE;
    }

    /** 瀕死の間は、画面の脈動に合わせて遅い心音を鳴らす (アドレナリンの心音が鳴っている間は鳴らさない) */
    private void tickCriticalHeartbeat() {
        for (Map.Entry<UUID, Key> entry : wound.entrySet()) {
            if (!BLEEDING_CRITICAL.equals(entry.getValue())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.getWorld().getGameTime() % CRITICAL_HEARTBEAT_TICKS != 0) continue;
            Playback playback = playing.get(entry.getKey());
            if (playback != null && playback.started && playback.current().heartbeat()) continue;
            player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBulletHit(BulletHitEvent event) {
        if (!(event.getVictim() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        hitTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
        refresh(player);
    }

    /** iem 以外のポストエフェクトは残したまま、iem のエフェクトを今の層 (空 → 色味 → 負傷 → 演出 → 被弾) にする */
    private void refresh(Player player) {
        UUID playerId = player.getUniqueId();
        List<Key> current = new ArrayList<>(player.postEffects().values());
        List<Key> effects = new ArrayList<>();
        for (Key existing : current) {
            if (!NAMESPACE.equals(existing.namespace())) effects.add(existing);
        }
        Key skyEffect = sky.get(playerId);
        if (skyEffect != null) effects.add(skyEffect);
        if (graded.contains(playerId)) effects.add(RAID_GRADE);
        Key woundEffect = wound.get(playerId);
        if (woundEffect != null) effects.add(woundEffect);
        Playback playback = playing.get(playerId);
        if (playback != null && (playback.started || playback.showWhileWaiting) && playback.current().effect() != null) {
            effects.add(playback.current().effect());
        }
        Key hitEffect = hitEffectOf(playerId);
        if (hitEffect != null) effects.add(hitEffect);

        if (!effects.equals(current)) player.postEffects().set(effects);
    }

    /** iem のエフェクトをすべて外す (他のポストエフェクトは残す) */
    private static void clearIem(Player player) {
        List<Key> effects = new ArrayList<>();
        for (Key existing : player.postEffects().values()) {
            if (!NAMESPACE.equals(existing.namespace())) effects.add(existing);
        }
        player.postEffects().set(effects);
    }

    private void forget(UUID playerId) {
        playing.remove(playerId);
        sky.remove(playerId);
        graded.remove(playerId);
        wound.remove(playerId);
        hitTick.remove(playerId);
        awaitingLoad.remove(playerId);
        settlingUntil.remove(playerId);
        awaitingRespawn.remove(playerId);
    }

    // --- 死亡・リスポーン・ワールド移動 ---

    /** 即時リスポーンの環境では死亡画面が出ないため、死亡演出はリスポーン先の読み込み完了後に流す */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        awaitingRespawn.add(playerId);
        sky.remove(playerId);
        graded.remove(playerId);
        wound.remove(playerId);
        hitTick.remove(playerId);
        playing.put(playerId, new Playback(List.of(
                new Step(DEATH_IMPACT, DEATH_IMPACT_TICKS, false),
                new Step(DEATH_BLACKOUT, DEATH_BLACKOUT_TICKS, false),
                new Step(DEATH_FADE, DEATH_FADE_TICKS, false),
                new Step(DEATH_RECOVER, DEATH_RECOVER_TICKS, false))));
        refresh(player);
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
        // ワールドの切り替えでクライアントの表示がリセットされていても、今の層を確実に送り直す
        refresh(player);
        player.postEffects().update();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 前回の演出の途中で抜けていた場合など、保存されて残ったエフェクトを外す
        forget(event.getPlayer().getUniqueId());
        clearIem(event.getPlayer());
        awaitingLoad.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
        // 保存されて次回の参加時に残らないよう、保存前に外しておく
        clearIem(event.getPlayer());
    }
}
