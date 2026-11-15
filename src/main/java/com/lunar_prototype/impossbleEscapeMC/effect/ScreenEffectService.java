package com.lunar_prototype.impossbleEscapeMC.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.item.factory.HelmetVisors;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectPlayback.Step;

/**
 * 26.3のポストエフェクト ({@code player.postEffects()}) で画面効果をかける。
 *
 * エフェクト本体 (assets/iem/post_effect/*.json と shaders/post/*.fsh) はサーバーのリソースパックで配る。
 * クライアントは送られた順にポストエフェクトを重ねて描くため、次の層を下から順に重ねる。
 * <ol>
 *   <li>確認用: iem:control_debug ({@link #toggleControlDebug}、/shaderdebug)。空のシェーダーが画面の左下の端に書いた数値
 *       (カメラの向き・太陽の向きなど。リソースパックの shaders/include/iem_control.glsl) を読んで重ねて描く</li>
 *   <li>空: レイド終盤の起爆前に空だけを赤く染める iem:emp_precursor_* ({@link RaidEndSequence} が段階を決める)</li>
 *   <li>照明弾: レイド終盤の照明弾が見えている間 iem:flare_light_&lt;段階&gt; (照明弾の位置から周りを照らす光。段階は強さで、
 *       見えなくなった時にゆっくり消す。RaidEndSequence の照明弾が決める)</li>
 *   <li>フラッシュライト: 点けたフラッシュライトを手に持っている間 iem:flashlight (周りを照らす光なので色味より下)</li>
 *   <li>マズルフラッシュ: 発射信号を画作りへ渡す (追加パスなし) (周りを照らす光なので色味より下)</li>
 *   <li>画作り: ロビーでもレイド中でも常に iem:grade (太陽の影・接地の影・光の筋・レンズフレア・色味)。太陽のフレアの位置は数値から求める
 *       (ここより下の層は数値の画素をそのまま通す)。バイザーを付けたヘルメットをかぶっている間は、バイザー付きの画作り
 *       (iem:grade_&lt;バイザーの効果&gt;。最初の段でバイザーをかける。目の手前のガラスなのでレンズフレアより下)。
 *       雨に濡れると水滴付きの物 (iem:grade_visor_rain_&lt;段階&gt;) に替える。効果は IEM_shader/tools/post_effects.py が作る</li>
 *   <li>数値の画素: 画作りの無い時だけ iem:control。数値の画素を隣の行の色で隠す (画作りがある時は画作りが隠している)</li>
 *   <li>まぶしさ: ほかの人のフラッシュライトを向けられている間 iem:dazzle_&lt;段階&gt; ({@link #setDazzle}。段階は FlashlightDazzle が決める)</li>
 *   <li>負傷: 出血中は iem:bleeding、出血中で体力が少ない (瀕死) 時は iem:bleeding_critical</li>
 *   <li>演出: アドレナリン放出・死亡・レイド開始・EMP の閃光・ロアのムービー ({@link #playLoreIntro})。パラメーターを送れないため、強さ違いのエフェクトを時間割 (Step) で切り替える</li>
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
    private static final Key BLEEDING = Key.key(NAMESPACE, "bleeding");
    private static final Key BLEEDING_CRITICAL = Key.key(NAMESPACE, "bleeding_critical");
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
    /** 心拍の周期。adrenaline.fsh の BEAT_TICKS と同じ値にして、心音と画面の脈動を揃える */
    private static final int HEARTBEAT_TICKS = 10;
    /** 瀕死の心拍の周期。bleeding_critical.json の BeatTicks と同じ値にして、心音と画面の脈動を揃える */
    private static final int CRITICAL_HEARTBEAT_TICKS = 24;
    /** 出血中にこの割合を下回る体力を瀕死とみなす */
    private static final double CRITICAL_HEALTH_RATIO = 0.3;
    /** 画作り・負傷の層を見直す間隔 (tick) */
    private static final int STATE_UPDATE_INTERVAL_TICKS = 5;
    /** バイザーの水滴: 水滴の付くバイザー (画作り iem:grade_visor_rain_<段階> がある物。New_IEM_Resorcepack の tools/post_effects.py) と段階の数 */
    private static final String RAIN_VISOR = "iem:visor";
    private static final int RAIN_LEVELS = 6;
    /** 雨の中で 1 段濡れるまで (tick)。雨の当たらない所では RAIN_DRY_RATE 倍の速さで乾く */
    private static final int RAIN_TICKS_PER_LEVEL = 60;
    private static final int RAIN_DRY_RATE = 2;
    /**
     * mod (IEM_Compatibility) へ濡れ具合 (0〜1、float 1 つ) を送るチャンネル。受け取れるクライアントには段階ごとのエフェクトを付け替えず、
     * 画作り iem:grade_visor_rain 1 つで済ませる (水滴は mod が渡す値でなめらかに変わる。初めての段階に入るたびの読み込みも無い)
     */
    private static final String WET_CHANNEL = "iem:visor_wet";
    /** クライアントの読み込み完了の通知が来ない場合に待つのをやめるまで */
    private static final int LOAD_WAIT_TIMEOUT_TICKS = 400;
    /** 読み込み完了から演出を始めるまでの間。読み込み画面が閉じた直後は周りの描画が追いついていない */
    private static final int START_DELAY_AFTER_LOAD_TICKS = 10;

    private static ScreenEffectService instance;

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, ScreenEffectState> states = new HashMap<>();
    private final PostEffectTransport transport = new PostEffectTransport();
    private final MuzzleFlashSignal muzzleSignal;
    private final BukkitTask tickTask;
    private final PlayerLoadedListener playerLoadedListener;

    private ScreenEffectState state(Player player) { return state(player.getUniqueId()); }
    private ScreenEffectState state(UUID id) { return states.computeIfAbsent(id, ignored -> new ScreenEffectState()); }

    private ScreenEffectService(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.muzzleSignal = new MuzzleFlashSignal(plugin);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, WET_CHANNEL);
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
                if (player != null && instance == ScreenEffectService.this) onWorldLoaded(player);
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
        ScreenEffectService service = instance;
        service.tickTask.cancel();
        service.muzzleSignal.close();
        PacketEvents.getAPI().getEventManager().unregisterListener(service.playerLoadedListener);
        org.bukkit.event.HandlerList.unregisterAll(service);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(service.plugin, WET_CHANNEL);
        service.states.values().forEach(ScreenEffectState::discard);
        service.states.clear();
        Bukkit.getOnlinePlayers().forEach(service.transport::clear);
        instance = null;
    }

    /** 数値の画素の確認用エフェクトを付け外しする。付けたら true */
    public static boolean toggleControlDebug(Player player) {
        if (instance == null) return false;
        ScreenEffectState state = instance.state(player);
        state.controlDebug = !state.controlDebug;
        instance.refresh(player);
        return state.controlDebug;
    }

    /**
     * 診断用: iem のエフェクトを 1 つ、一番上の層に重ねる (null で外す)。ふだんの層はそのまま。メインスレッドから呼ぶ。
     * 初めてそのエフェクトを重ねた時のクライアントの停止時間を測る {@link ShaderWarmDiagnostic} が使う
     */
    public static void setProbe(Player player, Key effect) {
        if (instance == null) return;
        instance.state(player).probe = effect;
        instance.refresh(player);
    }

    /** 画面を真っ黒にする層 (iem:intro_0) を付け外しする。{@link ShaderWarmup} が、準備の間の停止を隠すために使う。メインスレッドから呼ぶ */
    static void setCover(Player player, boolean on) {
        if (instance == null) return;
        ScreenEffectState state = instance.state(player);
        if (state.cover == on) return;
        state.cover = on;
        instance.refresh(player);
    }

    /** メニューの後ろをぼかさない (隠れ家の画面でカメラが映す設備を見せる) を付け外しする */
    public static void setMenuClear(Player player, boolean clear) {
        if (instance == null) return;
        ScreenEffectState state = instance.state(player);
        if (state.menuClear == clear) return;
        state.menuClear = clear;
        instance.refresh(player);
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

    // --- ロアのムービー (iem:lore_intro。絵コンテはリソースパックの tools/lore_movie.json) ---
    private static final Key LORE_INTRO = Key.key(NAMESPACE, "lore_intro");
    /** ムービーの長さ。絵コンテ (lore_movie.json) の duration と同じにする */
    private static final int LORE_INTRO_MOVIE_TICKS = 1930;   // 96.5 秒
    /**
     * ムービーの後にかけておく余り。ムービーの時計はクライアントがエフェクトを組み立てて最初に描いた時から進むので、
     * シェーダーの組み立てに時間がかかっても最後が欠けないようにする (その間は最後のロゴがそのまま出ている)
     */
    private static final int LORE_INTRO_SLACK_TICKS = 6 * 20;
    /** ムービーをかけている長さ (ロゴの後、レイド開始の演出の段 intro_1〜6 で画面に戻す) */
    public static final int LORE_INTRO_TICKS = LORE_INTRO_MOVIE_TICKS + LORE_INTRO_SLACK_TICKS;

    /** ムービーの後、画面に戻す段 (intro_1〜6) の長さ */
    public static final int LORE_INTRO_RETURN_TICKS = (INTRO_STAGES - 1) * INTRO_STAGE_TICKS;

    /**
     * ロアのムービーを流す (画面全体を描きかえるエフェクトを長さの分だけかける。動きはシェーダーが、かかった最初のフレームからの経過で決める)。
     * 終わったら、暗い画面からレイド開始の演出と同じ段 (intro_1〜6) で画面に戻す。読み込み待ちの間は始めない (始まりを見逃さないように)。
     * onEnd はムービーの画面を外した時 (画面に戻し始める時) に呼ぶ (無ければ null)
     */
    public static void playLoreIntro(Player player, Runnable onEnd) {
        if (instance == null) return;
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(LORE_INTRO, LORE_INTRO_TICKS, false));
        for (int i = 1; i < INTRO_STAGES; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "intro_" + i), INTRO_STAGE_TICKS, false, i == 1 ? onEnd : null));
        }
        instance.start(player, steps);
    }

    // --- 出撃時の PDA (iem:pda_*。IEM_shader/tools/raid_pda.py) ---
    /** 電源が入るまでの暗転 (クライアントの読み込み待ちの間も表示する) */
    private static final int PDA_BLACK_TICKS = 16;
    /** 電源を入れる手元のスイッチの音を、液晶が開く何 tick 前に鳴らすか */
    private static final int PDA_SWITCH_LEAD_TICKS = 4;
    private static final int PDA_POWER_STAGES = 4;
    private static final int PDA_OFF_STAGES = 4;
    private static final int PDA_STAGE_TICKS = 2;
    /** 起動ログ・戦術マップを出している長さ */
    private static final int PDA_BOOT_TICKS = 22;
    private static final int PDA_TACMAP_TICKS = 64;
    /**
     * 見終わった後、PDA を見下ろした向きから顔を上げる間の長さ。その間はレイド開始の演出の弱い段 (intro_4〜6) を
     * 順にかけ、ぼかし・白黒・上下の黒帯が抜けていく
     */
    public static final int PDA_RISE_TICKS = 24;
    private static final int[] PDA_RISE_STAGES = {4, 5, 6};
    private static final int[] PDA_RISE_STAGE_TICKS = {7, 8, 9};
    public static final String PDA_INTRO = "pda_intro";

    /**
     * 出撃時の PDA の演出: 暗転 → (スイッチ) → 電源 (液晶が開く) → 起動ログ → 戦術マップ → 閉じる (CRT が消える) → 顔を上げる。
     * 画面の中身 (文字・絵)・効果音・顔を上げる向きは呼ぶ側が受け持つので、段に入った時に知らせる。
     * 演出が別の演出に置き換わった時は最後の知らせが来ないので、呼ぶ側は {@link #isPlaying} で確かめる
     */
    public static void playPdaIntro(Player player, Runnable onSwitch, Runnable onPower, Runnable onBoot, Runnable onTacmap,
                                    Runnable onClosing, Runnable onRise, Runnable onFinished) {
        if (instance == null) {
            onFinished.run();
            return;
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Key.key(NAMESPACE, "pda_black"), PDA_BLACK_TICKS - PDA_SWITCH_LEAD_TICKS, false));
        steps.add(new Step(Key.key(NAMESPACE, "pda_black"), PDA_SWITCH_LEAD_TICKS, false, onSwitch));
        for (int i = 1; i <= PDA_POWER_STAGES; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "pda_power_" + i), PDA_STAGE_TICKS, false, i == 1 ? onPower : null));
        }
        steps.add(new Step(Key.key(NAMESPACE, "pda_screen"), PDA_BOOT_TICKS, false, onBoot));
        steps.add(new Step(Key.key(NAMESPACE, "pda_screen"), PDA_TACMAP_TICKS, false, onTacmap));
        for (int i = 1; i <= PDA_OFF_STAGES; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "pda_off_" + i), PDA_STAGE_TICKS, false, i == 1 ? onClosing : null));
        }
        for (int i = 0; i < PDA_RISE_STAGES.length; i++) {
            steps.add(new Step(Key.key(NAMESPACE, "intro_" + PDA_RISE_STAGES[i]), PDA_RISE_STAGE_TICKS[i], false, i == 0 ? onRise : null));
        }
        steps.add(new Step(null, 1, false, onFinished));
        instance.start(player, steps, true, PDA_INTRO);
    }

    /** その名前の時間割がまだ続いているか */
    /** ほかの人のフラッシュライトを向けられた時のまぶしさの段 (1〜3、0 で外す)。画作りをかけている間 (生きている間) だけかける */
    public static void setDazzle(Player player, int level) {
        if (instance == null) return;
        ScreenEffectState state = instance.state(player);
        Integer next = level > 0 ? level : null;
        if (java.util.Objects.equals(state.dazzle, next)) return;
        state.dazzle = next;
        instance.refresh(player);
    }

    public static boolean isPlaying(Player player, String name) {
        if (instance == null) return false;
        ScreenEffectState state = instance.states.get(player.getUniqueId());
        return state != null && state.playback != null && name.equals(state.playback.name());
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

    private void start(Player player, List<Step> steps) { start(player, steps, false); }

    private void start(Player player, List<Step> steps, boolean showWhileWaiting) {
        start(player, steps, showWhileWaiting, null);
    }

    /** 同じ層の演出は新しい要求で置換する。中断した段の残りや完了処理は再生しない。 */
    private void start(Player player, List<Step> steps, boolean showWhileWaiting, String name) {
        ScreenEffectState state = state(player);
        ScreenEffectPlayback playback = new ScreenEffectPlayback(steps, showWhileWaiting, name);
        state.replacePlayback(playback);
        if (canAdvance(player.getUniqueId())) {
            Step entered = playback.begin();
            refresh(player);
            onStepStart(player, entered);
        } else refresh(player);
    }

    private boolean canAdvance(UUID playerId) {
        return state(playerId).canAdvance(Bukkit.getCurrentTick(), ShaderWarmup.isActive(playerId));
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        states.values().forEach(state -> state.expireLoadWaits(now, LOAD_WAIT_TIMEOUT_TICKS));
        if (now % STATE_UPDATE_INTERVAL_TICKS == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) updateGradeAndWound(player);
        }
        tickHits(now);
        tickMuzzleFlashes(now);
        tickHeartbeatRecovery();
        tickCriticalHeartbeat();
        for (var entry : new ArrayList<>(states.entrySet())) {
            ScreenEffectState state = entry.getValue();
            ScreenEffectPlayback playback = state.playback;
            if (playback == null) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                playback.cancel();
                state.playback = null;
                continue;
            }
            advancePlayback(player, state, playback, now);
        }
    }

    private void advancePlayback(Player player, ScreenEffectState state, ScreenEffectPlayback playback, int now) {
        UUID playerId = player.getUniqueId();
        // コールバックから他プレイヤーの状態が破棄/置換された場合、古い時計を進めない。
        if (states.get(playerId) != state || state.playback != playback) return;
        ScreenEffectPlayback.Advance advance = playback.advance(now, player.getWorld().getGameTime(), canAdvance(playerId), HEARTBEAT_TICKS);
        if (advance.heartbeat()) player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        switch (advance.change()) {
            case NONE -> { }
            case STARTED -> {
                // 最初の表示だけは、表示してから段の開始処理を呼ぶ（既存のタイミング）。
                refresh(player);
                onStepStart(player, advance.entered());
            }
            case STEP -> {
                onStepStart(player, advance.entered());
                // コールバックが退出/置換を起こしても、古い再生を復活させない。
                if (states.get(playerId) == state) refresh(player);
            }
            case FINISHED -> {
                state.playback = null;
                if (ADRENALINE_FADE.equals(playback.current().effect())) {
                    state.recovery = new AdrenalineHeartbeatRecovery(playback.recoveryDelay(now, HEARTBEAT_TICKS));
                }
                refresh(player);
            }
        }
    }

    private void onStepStart(Player player, Step step) {
        if (step.onStart() != null) {
            try {
                step.onStart().run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "画面効果の段の処理に失敗しました", e);
            }
        }
        if (DEATH_IMPACT.equals(step.effect())) {
            // 不意を突く低い衝撃音と耳鳴り
            player.playSound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.6f);
            player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
    }

    // --- 空・画作り・負傷・被弾の層 ---

    /** 生きている間は画作り (レイド終盤は空も、バイザー付きのヘルメットならバイザーも)、出血中なら負傷の層をかける */
    private void updateGradeAndWound(Player player) {
        UUID playerId = player.getUniqueId();
        ScreenEffectState state = state(player);
        PostEffectStack.Layers before = state.layers(Bukkit.getCurrentTick());
        boolean alive = !player.isDead() && !state.awaitingRespawn
                && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE;
        RaidInstance raid = alive && plugin.getRaidModule() != null ? plugin.getRaidModule().getRaidOf(player) : null;
        state.sky = raid != null ? raid.getEndSequence().skyEffect() : null;
        state.flareLight = raid != null ? raid.getEndSequence().flareLightEffect(player) : null;
        state.wound = alive ? woundEffectOf(player) : null;
        state.visor = withRain(player, alive ? HelmetVisors.postEffectOf(player.getInventory().getHelmet()) : null);
        state.graded = alive;
        state.flashlit = alive && com.lunar_prototype.impossbleEscapeMC.item.Flashlight.isLit(player);
        if (!before.equals(state.layers(Bukkit.getCurrentTick()))) refresh(player);
        SunShadowService.setInRaid(player, raid != null);
    }

    /**
     * バイザーが雨に濡れていれば、水滴の付いたバイザー (iem:visor_rain_<段階>。画作りは iem:grade_visor_rain_<段階>) にする。濡れ具合はここで進める
     * (雨の中で RAIN_TICKS_PER_LEVEL ごとに 1 段、雨の当たらない所では RAIN_DRY_RATE 倍の速さで乾く)。
     * mod のあるクライアントには濡れ具合を {@link #WET_CHANNEL} で送り、段階の無い iem:visor_rain にする
     */
    private Key withRain(Player player, Key visorEffect) {
        ScreenEffectState state = state(player);
        if (visorEffect == null || !RAIN_VISOR.equals(visorEffect.asString())) {
            state.visorWet = 0;
            state.sentWet = null;
            return visorEffect;
        }
        int wet = state.visorWet;
        wet = inRain(player)
                ? Math.min(RAIN_LEVELS * RAIN_TICKS_PER_LEVEL, wet + STATE_UPDATE_INTERVAL_TICKS)
                : Math.max(0, wet - STATE_UPDATE_INTERVAL_TICKS * RAIN_DRY_RATE);
        boolean channel = player.getListeningPluginChannels().contains(WET_CHANNEL);
        if (channel) sendWet(player, wet / (float) (RAIN_LEVELS * RAIN_TICKS_PER_LEVEL));
        if (wet == 0) {
            state.visorWet = 0;
            return visorEffect;
        }
        state.visorWet = wet;
        if (channel) return Key.key(visorEffect.namespace(), visorEffect.value() + "_rain");
        int level = (wet + RAIN_TICKS_PER_LEVEL - 1) / RAIN_TICKS_PER_LEVEL;
        return Key.key(visorEffect.namespace(), visorEffect.value() + "_rain_" + level);
    }

    /** 濡れ具合を mod へ送る (変わった時だけ) */
    private void sendWet(Player player, float wetness) {
        ScreenEffectState state = state(player);
        Float sent = state.sentWet;
        state.sentWet = wetness;
        if (sent != null && sent == wetness) return;
        player.sendPluginMessage(plugin, WET_CHANNEL, java.nio.ByteBuffer.allocate(4).putFloat(wetness).array());
    }

    /** 雨に打たれているか: 雨が降っていて、頭の上に雨を遮るブロックが無く、雪や乾いた所 (砂漠など) でない */
    private static boolean inRain(Player player) {
        org.bukkit.World world = player.getWorld();
        if (!world.hasStorm()) return false;
        org.bukkit.Location eye = player.getEyeLocation();
        if (world.getHighestBlockYAt(eye.getBlockX(), eye.getBlockZ(), org.bukkit.HeightMap.MOTION_BLOCKING) > eye.getBlockY()) return false;
        org.bukkit.block.Block block = eye.getBlock();
        return block.getTemperature() >= 0.15 && block.getHumidity() > 0.0;
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
        for (var entry : new ArrayList<>(states.entrySet())) {
            ScreenEffectState state = entry.getValue();
            if (state.hitTick == null) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            int elapsed = now - state.hitTick;
            if (player == null || elapsed >= ScreenEffectState.HIT_TICKS + ScreenEffectState.HIT_FADE_TICKS) state.hitTick = null;
            if (player != null && (elapsed == ScreenEffectState.HIT_TICKS || elapsed >= ScreenEffectState.HIT_TICKS + ScreenEffectState.HIT_FADE_TICKS)) refresh(player);
        }
    }

    /**
     * 撃った本人の画面に、その tick だけマズルフラッシュをかける。
     * 銃口の画面上の位置は送れないため、腰だめ・ADS で別のエフェクトにしてある (位置はパック側の従来値を使う)
     */
    public static void playMuzzleFlash(Player player, boolean aiming, boolean suppressed) {
        if (instance == null) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        instance.muzzleSignal.flash(player, suppressed ? 3 : aiming ? 2 : 1);
    }

    /** 信号だけを解除する。ポストエフェクトの構成は変更しない。 */
    private void tickMuzzleFlashes(int now) {
        muzzleSignal.tick(now);
    }

    /** 終了音は心拍の間隔と音量で表現する。瀕死・死亡・別演出に重ねない。 */
    private void tickHeartbeatRecovery() {
        for (var entry : new ArrayList<>(states.entrySet())) {
            ScreenEffectState state = entry.getValue();
            if (state.recovery == null) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                    || player.getGameMode() == GameMode.CREATIVE || !canAdvance(entry.getKey())
                    || BLEEDING_CRITICAL.equals(state.wound) || state.playback != null) {
                state.recovery = null;
                continue;
            }
            AdrenalineHeartbeatRecovery.Beat beat = state.recovery.tick();
            if (beat != null) player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, beat.volume(), beat.pitch());
            if (state.recovery.finished()) state.recovery = null;
        }
    }

    /** 瀕死の間は、画面の脈動に合わせて遅い心音を鳴らす。 */
    private void tickCriticalHeartbeat() {
        for (var entry : states.entrySet()) {
            ScreenEffectState state = entry.getValue();
            if (!BLEEDING_CRITICAL.equals(state.wound)) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.getWorld().getGameTime() % CRITICAL_HEARTBEAT_TICKS != 0) continue;
            if (state.playback != null && state.playback.heartbeatActive()) continue;
            player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBulletHit(BulletHitEvent event) {
        if (!(event.getVictim() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        state(player).hitTick = Bukkit.getCurrentTick();
        refresh(player);
    }

    private void refresh(Player player) {
        transport.apply(player, state(player).layers(Bukkit.getCurrentTick()));
    }

    private void clearIem(Player player) { transport.clear(player); }

    private void forget(UUID playerId) {
        ScreenEffectState state = states.remove(playerId);
        if (state != null) state.discard();
        muzzleSignal.clear(playerId);
    }

    // --- 死亡・リスポーン・ワールド移動 ---

    /** 即時リスポーンの環境では死亡画面が出ないため、死亡演出はリスポーン先の読み込み完了後に流す */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        state(player).death(new ScreenEffectPlayback(List.of(
                new Step(DEATH_IMPACT, DEATH_IMPACT_TICKS, false),
                new Step(DEATH_BLACKOUT, DEATH_BLACKOUT_TICKS, false),
                new Step(DEATH_FADE, DEATH_FADE_TICKS, false),
                new Step(DEATH_RECOVER, DEATH_RECOVER_TICKS, false)), false, null));
        muzzleSignal.clear(player.getUniqueId());
        refresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ScreenEffectState state = state(playerId);
        state.awaitingRespawn = false;
        state.awaitingLoadSince = Bukkit.getCurrentTick();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        ScreenEffectState state = state(event.getPlayer());
        state.recovery = null;
        state.awaitingLoadSince = Bukkit.getCurrentTick();
    }

    @EventHandler
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        // タイムアウトは読み込み完了ではない (重いワールドではまだ読み込み画面のまま)。PLAYER_LOADED パケットを待つ
        if (event.isTimeout()) return;
        onWorldLoaded(event.getPlayer());
    }

    private void onWorldLoaded(Player player) {
        // 読み込み完了の通知はイベントとパケットの両方から届くため、1回だけ扱う
        ScreenEffectState state = states.get(player.getUniqueId());
        if (state == null || !state.worldLoaded(Bukkit.getCurrentTick(), START_DELAY_AFTER_LOAD_TICKS)) return;
        // ワールドの切り替えでクライアントの表示がリセットされていても、今の層を確実に送り直す
        transport.resend(player, state.layers(Bukkit.getCurrentTick()));
        ShaderWarmup.onWorldLoaded(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 前回の演出の途中で抜けていた場合など、保存されて残ったエフェクトを外す
        forget(event.getPlayer().getUniqueId());
        clearIem(event.getPlayer());
        state(event.getPlayer()).awaitingLoadSince = Bukkit.getCurrentTick();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
        SunShadowService.forget(event.getPlayer().getUniqueId());
        // 保存されて次回の参加時に残らないよう、保存前に外しておく
        clearIem(event.getPlayer());
    }
}
