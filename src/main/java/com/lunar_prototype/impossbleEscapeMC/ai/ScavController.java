package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackScavWeapon;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.PluginScavWeapon;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavWeapon;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class ScavController {
    public static class SoundContact {
        public enum Kind {
            FOOTSTEP,
            GUNSHOT,
            UNKNOWN
        }

        public final Kind kind;
        public final Location sourceLocation;
        public final Vector movementDirection;
        public final double movementSpeed;
        public final boolean sprinting;
        public final boolean sneaking;
        public final int continuousNoiseTicks;
        public final double walkedDistance;

        public SoundContact(Kind kind, Location sourceLocation, Vector movementDirection, double movementSpeed,
                            boolean sprinting, boolean sneaking, int continuousNoiseTicks, double walkedDistance) {
            this.kind = kind != null ? kind : Kind.UNKNOWN;
            this.sourceLocation = sourceLocation != null ? sourceLocation.clone() : null;
            this.movementDirection = movementDirection != null ? movementDirection.clone() : null;
            this.movementSpeed = movementSpeed;
            this.sprinting = sprinting;
            this.sneaking = sneaking;
            this.continuousNoiseTicks = continuousNoiseTicks;
            this.walkedDistance = walkedDistance;
        }

        public static SoundContact footstep(Location sourceLocation, Vector movementDirection, double movementSpeed,
                                            boolean sprinting, boolean sneaking, int continuousNoiseTicks, double walkedDistance) {
            return new SoundContact(Kind.FOOTSTEP, sourceLocation, movementDirection, movementSpeed, sprinting, sneaking, continuousNoiseTicks, walkedDistance);
        }

        public static SoundContact gunshot(Location sourceLocation) {
            return new SoundContact(Kind.GUNSHOT, sourceLocation, null, 0.0, false, false, 0, 0.0);
        }
    }

    private enum BehaviorState {
        RELAXED,
        SUSPICIOUS,
        COMBAT_READY
    }

    private final ImpossbleEscapeMC plugin;
    private final Mob scav;
    private final ScavBrain brain;
    private final GunListener gunListener;
    private final ScavBrain.BrainLevel brainLevel;

    // Components
    private final ScavVision vision;
    private final ScavSquad squad;
    private final ScavTactics tactics;
    private final TacticalPositioning positioning;
    /** 撃てる位置に着いたのに相手が見えないステップ数 (続いたら立ち位置を選び直す) */
    private int positionNoSightSteps = 0;
    private static final int POSITION_NO_SIGHT_STEPS = 7;
    /** 負傷の時 (とリロードの上限) に、立ち位置を「下がる」で選ぶ時間 (tick) */
    private static final int WITHDRAW_TICKS = 100;
    /** 制圧された時の「下がる」時間 (tick)。制圧はすぐ引くため短くし、隠れたまま撃ち返さなくなるのを防ぐ */
    private static final int SUPPRESSED_WITHDRAW_TICKS = 40;
    /** リロードで下がる時、撃てるようになってから顔を出し直すまでの余裕 (tick) */
    private static final int RELOAD_WITHDRAW_MARGIN_TICKS = 10;
    private int withdrawUntilTick = 0;
    /** 立ち位置に着いたか。着いたのは1ブロック以内、離れたのは2ブロックより外 (境目で行き来しないように) */
    private static final double SPOT_ARRIVE = 1.0;
    private static final double SPOT_LEAVE = 2.0;
    private TacticalPositioning.Spot arrivedSpot;
    /** 相手が見えている時、相手を向いたまま横歩きで向かう立ち位置の距離の上限 (ブロック) */
    private static final double DIRECT_MOVE_MAX = 10.0;
    /**
     * 見えている相手がこれより近ければ、まっすぐ行けない立ち位置へ経路探索で回り込まず、その場で撃ち合う (ブロック)。
     * 経路探索中は進む方向を向いて撃たないため、近くの相手に背中を見せたまま歩き続けてしまう
     */
    private static final double CLOSE_FIGHT_DISTANCE = 10.0;
    /** 相手から見えていない時に、経路探索で立ち位置を移る速さ */
    private static final double RELOCATE_SPEED = 1.8;
    /** 立ち位置へ経路探索で歩いている (照準で向きを上書きしない) */
    private boolean followingPath = false;
    /** 見失って・撃たれてからこのtickの間は、撃ち合いの最中とみなす (立ち位置を選んで戦う) */
    private static final int ENGAGED_MEMORY_TICKS = 200;
    /** 同じあたりからの音がこの間隔 (tick) 以内に続いた時は、居場所の推定だけ更新する (連射の1発ごとに反応し直さない) */
    private static final int SOUND_REPEAT_TICKS = 20;
    private static final double SOUND_REPEAT_DISTANCE = 6.0;
    private int lastSoundReactionTick = Integer.MIN_VALUE / 2;
    private ScavWeapon weapon;
    private String weaponKey;
    /** 銃があと何tickで撃てるか (この判断ステップの時点)。撃てない間は顔を出さない */
    private int weaponReadyInTicks = 0;
    /** これ以下の待ちなら顔を出す (出るまでの間に撃てるようになる) */
    static final int PEEK_READY_TICKS = 4;
    /** これ以上撃てない (リロード・持ち替えなど) なら、弾切れと同じく隠れられる場所へ下がる */
    private static final int LONG_UNREADY_TICKS = 20;

    // --- 遮蔽からの撃ち合いへの復帰 ---
    // 遮蔽に着くと顔出し (単発を数発撃って戻り、数秒休む) を繰り返すだけになり、撃てる立ち位置へ出直さなかった。
    // 隠れてしばらく経ち、撃てて・傷が浅く・撃たれていなければ、撃てる立ち位置へ出て撃ち合い、時間が来たら遮蔽へ戻る
    /** 隠れてから出直すまで (tick)。強さごとの最短と、それに足すばらつき */
    private static final int REENGAGE_WAIT_LOW = 60;
    private static final int REENGAGE_WAIT_MID = 80;
    private static final int REENGAGE_WAIT_HIGH = 100;
    private static final int REENGAGE_WAIT_JITTER = 60;
    /** 出て撃ち合う長さ (tick)。撃たれたら遮蔽へ戻る。撃てなくなると従来どおり下がる */
    private static final int REENGAGE_MIN_TICKS = 60;
    private static final int REENGAGE_JITTER_TICKS = 60;
    /** 出直す条件: マガジンの残り・体力の割合・制圧・最後に撃たれてからの時間 (tick) */
    private static final double REENGAGE_MIN_AMMO_RATIO = 0.5;
    private static final double REENGAGE_MIN_HEALTH = 0.5;
    private static final float REENGAGE_MAX_SUPPRESSION = 0.3f;
    private static final int REENGAGE_NO_DAMAGE_TICKS = 60;
    /** 遮蔽 (撃てない立ち位置) に着いた tick。撃てる立ち位置にいる間は -1 */
    private int inCoverSinceTick = -1;
    private int reengageAfterTick;
    private int reengageUntilTick;
    private double lastAmmoRatio = 1.0;

    // --- 弾切れ (予備も尽きた時) ---
    /** 弾を分けてもらいに行く味方の範囲と、受け取れる距離 */
    private static final double AMMO_SUPPLIER_RANGE = 40.0;
    private static final double AMMO_HANDOVER_DISTANCE = 2.5;
    /** 弾を分けてくれる味方・隠れる所を探し直す間隔 (tick) */
    private static final int UNARMED_RECHECK_TICKS = 20;
    /** 隠れる所が無い時に、相手から離れる距離 (ブロック) */
    private static final double UNARMED_FLEE_DISTANCE = 14.0;
    /** 弾を分けてもらう相手 (向かっている間だけ) */
    private UUID ammoSupplierId;
    /** 弾が尽き、分けてくれる味方もいない (撃ち合いを避けて隠れる) */
    private boolean unarmed;
    private int unarmedRecheckTick;
    /** 弾が尽きた時に向かう隠れ場所 (無ければ相手から離れる) */
    private Location unarmedRefuge;
    /** 相手から隠れている時、弾がこの割合を切っていればリロードしておく (相手が分からない時は減っていれば) */
    private static final double TACTICAL_RELOAD_RATIO = 0.5;

    private Chunk currentChunk = null;
    private Location lastKnownLocation = null;
    /** 見失った相手の居場所の推定。lastKnownLocation は見えていない間、この推定の一番ありそうな場所になる */
    private final OpponentBelief belief = new OpponentBelief();

    // --- 制圧射撃 (見えない相手が出てきそうな所へ単発を撃ち込む) ---
    /** 最後に見えてからこのtick以内の相手だけ制圧する (古い推定へ撃ち続けない) */
    private static final int SUPPRESS_MEMORY_TICKS = 100;
    /** 出てきそうな所への集まりがこれ以上の時だけ撃つ */
    private static final double SUPPRESS_MIN_SHARE = 0.4;
    /** 弾がこの割合を切ったら撃たない (顔を出された時のために残す) */
    private static final double SUPPRESS_MIN_AMMO_RATIO = 0.4;
    /** 交戦距離の何倍までの所を撃つか */
    private static final double SUPPRESS_RANGE_FACTOR = 1.5;
    private static final double SUPPRESS_AIM_TOLERANCE_DEGREES = 4.0;
    /** 単発の間隔 (ms)。毎秒2〜3発 */
    private static final long SUPPRESS_INTERVAL_MS = 330;
    private static final long SUPPRESS_INTERVAL_JITTER_MS = 170;
    /** 人を狙っていないぶんの追加のばらつき */
    private static final double SUPPRESS_EXTRA_SPREAD = 0.04;
    /** 何発撃ったら間を置くか、置く時間 (tick) */
    private static final int SUPPRESS_BURST_MIN = 3;
    private static final int SUPPRESS_BURST_MAX = 5;
    private static final int SUPPRESS_PAUSE_TICKS = 30;
    private static final int SUPPRESS_PAUSE_JITTER_TICKS = 40;
    private int suppressShots = 0;
    /** 最後に制圧射撃をしたtick (味方はこれを見て、援護を受けて動く) */
    private int lastSuppressTick = Integer.MIN_VALUE / 2;
    /** 制圧射撃をしたとみなす時間 (tick) */
    private static final int SUPPRESSING_TICKS = 30;
    /** 味方が動いている時は、集まりが薄くても制圧して援護する */
    private static final double SUPPRESS_COVER_MIN_SHARE = 0.25;
    private int suppressBurst = SUPPRESS_BURST_MIN;
    private int suppressPauseUntilTick = 0;
    private int searchTicks = 0;
    private float suppression = 0.0f;
    private boolean isAlerted = false;
    private int cornerCheckTicks = 0;
    private boolean isHoldingAngle = false;
    private int lastSquadUpdate = 0;
    private int lostTargetSteps = 0;

    // --- 追跡 (AIは3tickごとに1ステップ) ---
    /** 見失ってからターゲットを手放すまでのステップ数 (約3秒) */
    private static final int TARGET_MEMORY_STEPS = 20;
    /** 見失った時、観測した移動速度から何tick先の位置を予測するか */
    private static final int LOSS_PREDICTION_TICKS = 20;
    private static final double LOSS_PREDICTION_MAX_DISTANCE = 6.0;
    /** 見失った直後に予測地点へ走って追うステップ数 (約6秒)。到着したら慎重な捜索に切り替える */
    private static final int PURSUIT_STEPS = 40;
    /** 撃たれた方向から推す、撃った相手の位置の誤差 (ブロック) */
    private static final double ATTACKER_LOCATION_SIGMA = 1.5;
    private static final double PURSUIT_SPEED = 1.5;
    private UUID lastSeenTargetId = null;
    private Location lastSeenLocation = null;
    private int lastSeenTick = 0;
    private Vector observedVelocity = new Vector();
    private int pursuitSteps = 0;
    private int lastSearchTick = -1;

    // --- 撃ち方のばらつき ---
    /** この時刻 (ms) までは撃たない (発見直後の反応時間・指切りの間・ためらい) */
    private long fireHoldUntil = 0;
    /** フルオートで今のバーストに残っている引き金の回数 */
    private int burstPullsRemaining = 0;
    /** 通常のSCAVがフルオートに切り替える距離 (ブロック)。これより遠くでは単発で撃つ */
    private static final double CLOSE_RANGE_FULL_AUTO = 10.0;

    // --- 命中精度 ---
    /** 制圧値1.0あたりのばらつきの増加。撃ち返されるほど狙いが乱れる */
    private static final double SUPPRESSION_SPREAD = 0.2;
    /** 連射中、引き金を引くたびに増えるばらつき (反動で照準が上ずる) と、その上限 */
    private static final double RECOIL_SPREAD_PER_PULL = 0.03;
    /** 単発で撃つ時の1発あたりの反動。単発の間隔は反動が収まる時間より短いため、小さくしないと単発でも散り続ける */
    private static final double RECOIL_SPREAD_PER_SINGLE_SHOT = 0.01;
    private static final double RECOIL_SPREAD_MAX = 0.24;
    /** 前に引き金を引いてからこれだけ空いたら、反動は収まったとみなす (ms) */
    private static final long RECOIL_RESET_MS = 350;
    /** 反動が収まってから続けて引き金を引いた回数 */
    private int consecutivePulls = 0;
    private boolean sawTargetLastStep = false;
    /** 最後にターゲットが見えていたtickと、その相手 */
    private int lastTargetVisibleTick = Integer.MIN_VALUE / 2;
    private UUID lastVisibleTargetId = null;
    /** これより長く見えていなかった相手が見えた時だけ、反応時間をおく (tick) */
    private static final int REACQUIRE_TICKS = 30;

    // --- ヒートマップの「安全」記録 ---
    /** 記録間隔 (約3秒)。毎ステップ記録すると「危険」(被弾1回 +2) をすぐ打ち消してしまう */
    private static final int SAFE_RECORD_INTERVAL_STEPS = 20;
    /** 直近この時間 (tick) 以内に撃たれていたら安全とはみなさない */
    private static final int SAFE_RECORD_NO_DAMAGE_TICKS = 60;
    private int safeRecordCooldownSteps = 0;

    // --- 物陰への移動 ---
    /** この距離以上近づいたら「進んでいる」とみなす */
    private static final double COVER_PROGRESS_MIN = 0.3;
    /** 物陰へ近づけないまま、このステップ数 (約1.5秒) が過ぎたら諦める */
    private static final int COVER_STUCK_STEPS = 10;
    private double coverBestDistance = Double.MAX_VALUE;
    private int coverStuckSteps = 0;
    private int lastRetargetTick = Integer.MIN_VALUE / 2;
    private int lastDamagedTick = Integer.MIN_VALUE / 2;

    public boolean isSprinting() {
        return isSprinting;
    }

    private boolean isSprinting = false;
    private boolean isPreAiming = false;

    private Vector currentAimVector = null;
    /** 照準を向けておく場所と、その期限 (毎ステップ更新されなくなったら向けるのをやめる) */
    private Location preAimPoint = null;
    private int preAimUntilTick = 0;
    private static final int PRE_AIM_HOLD_TICKS = ScavController.STEP_TICKS * 2;
    private double aimErrorYaw = 0;
    private double aimErrorPitch = 0;
    private long lastMobShotTime = 0;

    /** AIの1ステップのtick数 (AIは3tickごとに1回動く) */
    public static final int STEP_TICKS = 3;

    /** 次に喋れるtick */
    private int voiceAvailableTick = 0;
    /** 最後に喋り出したtick */
    private int lastVoiceTick = Integer.MIN_VALUE / 2;
    private static final int VOICE_LINE_COOLDOWN_TICKS = 100;
    /** 周りの味方がこの時間 (tick) 以内に喋り出していたら喋らない */
    private static final int ALLY_VOICE_GAP_TICKS = 40;
    /** 撃たれ続けて制圧がこの値を超えた時に叫ぶ */
    private static final float UNDER_FIRE_VOICE_SUPPRESSION = 0.5f;
    /** 警戒していない時の独り言の頻度 (平均1分に1回) と、独り言を言う時に近くにいるべきプレイヤーの距離 */
    private static final double IDLE_VOICE_CHANCE_PER_STEP = STEP_TICKS / (20.0 * 60.0);
    private static final double IDLE_VOICE_PLAYER_RANGE = 24.0;
    /** 前のステップでリロード中だったか (弾切れでリロードを始めた瞬間を知るため) */
    private boolean wasReloading;
    /** 視認中に味方へ位置を伝える間隔 (tick) */
    private static final int SHARE_INTERVAL_TICKS = 10;
    private int lastShareTick = Integer.MIN_VALUE / 2;
    private static final double LOW_EFFECTIVE_DAMAGE_THRESHOLD = 4.0;
    /** 壁越しの足音が聞こえる距離 */
    private static final double FOOTSTEP_MUFFLED_RANGE = 12.0;
    /** 足音から推定する位置の誤差の上限 (ブロック) */
    private static final double FOOTSTEP_MAX_LOCATION_ERROR = 6.0;
    /** 銃声から推定する位置の誤差の上限 (ブロック) */
    private static final double GUNSHOT_MAX_LOCATION_ERROR = 8.0;
    /** 平常時でも、これだけ (移動イベント数) 音を出し続けている相手は位置を追う */
    private static final int FOOTSTEP_TRACKING_NOISE_TICKS = 120;
    /** 防具で元のダメージの6割未満まで減らされたら「効きにくい」 */
    private static final double LOW_EFFECTIVE_DAMAGE_RATIO = 0.6;
    private static final int TARGET_MEMORY_EXPIRE_TICKS = 20 * 60;
    private static final float ALERTNESS_RELAXED_THRESHOLD = 0.30f;
    private static final float ALERTNESS_COMBAT_THRESHOLD = 0.65f;
    private final Map<UUID, TargetCombatMemory> targetMemories = new HashMap<>();

    private static class TargetCombatMemory {
        int hits;
        int lowEffectiveHits;
        long lastUpdateTick;
        int recognizedHeadArmorClass = -1;
        int recognizedChestArmorClass = -1;
    }
    private UUID lastLoggedTargetId = null;
    private boolean initializedTargetState = false;
    private final Location homeLocation;
    private Location lastHeardSoundLocation = null;
    private int investigateTicks = 0;
    private BehaviorState behaviorState = BehaviorState.RELAXED;
    private float alertness;
    private boolean returningHome = false;

    // --- 味方の救援 ---
    // 味方には過去の視認位置・時刻・観測速度を伝える。実際の接敵・射撃は自分の視認で判断する。
    /** 敵を見ている・撃たれてから、この間 (tick) は交戦中とみなす */
    private static final int COMBAT_MEMORY_TICKS = ScavHelpEncounter.QUIET_TICKS;
    /** 救援に向かい続ける時間 (tick)。呼ばれ直すと延びる */
    private static final int ASSIST_DURATION_TICKS = 600;
    /** 味方のどれだけ後ろ・横に着くか (ブロック) */
    private static final double ASSIST_BEHIND = 2.5;
    private static final double ASSIST_SIDE = 1.5;
    /** この距離まで来たら着いたとみなす */
    private static final double ASSIST_ARRIVE_DISTANCE = 1.5;
    private static final double ASSIST_SPEED = 1.3;
    /** 着いた後、味方が向いている方向のどこを見張るか (ブロック先) */
    private static final double ASSIST_WATCH_DISTANCE = 12.0;
    private int lastCombatTick = Integer.MIN_VALUE / 2;
    /** 救援に向かっている味方 */
    private UUID assistCallerId;
    private int assistUntilTick;
    private int assistStartedTick;
    private ScavContactReport sharedContact;
    private Location assignedSearchPoint;
    private int assignedSearchStartedTick;
    private int nextSharedSearchTick;
    private int sharedSearchUntilTick;
    /** 味方の左右どちらに着くか (1 / -1) */
    private int assistSide = 1;

    public ScavController(ImpossbleEscapeMC plugin, Mob scav, GunListener listener) {
        this(plugin, scav, listener, ScavBrain.BrainLevel.MID);
    }

    public ScavController(ImpossbleEscapeMC plugin, Mob scav, GunListener listener, ScavBrain.BrainLevel brainLevel) {
        this.plugin = plugin;
        this.scav = scav;
        this.brainLevel = brainLevel;
        this.brain = new ScavBrain(scav, brainLevel);
        this.gunListener = listener;
        this.vision = new ScavVision(scav);
        this.squad = new ScavSquad(this);
        this.tactics = new ScavTactics(scav, listener, brain);
        this.positioning = new TacticalPositioning(scav);
        this.currentAimVector = scav.getEyeLocation().getDirection();
        this.homeLocation = scav.getLocation().clone();
        this.alertness = (brainLevel == ScavBrain.BrainLevel.LOW) ? 0.15f : 0.25f;
        updateChunkTicket();
    }

    public Mob getScav() { return scav; }
    public ScavSquad getSquad() { return squad; }
    public TacticalPositioning getPositioning() { return positioning; }
    public ScavBrain getBrain() { return brain; }
    public ScavBrain.BrainLevel getBrainLevel() { return brainLevel; }
    public void setLastKnownLocation(Location loc) {
        this.lastKnownLocation = loc;
        if (loc != null) belief.reset(loc, null); else belief.clear();
    }
    public void setAlerted(boolean alerted) { this.isAlerted = alerted; }
    public int getSearchTicks() { return searchTicks; }
    public void setSearchTicks(int ticks) { this.searchTicks = ticks; }

    public void onTick() {
        updateChunkTicket();
        cleanupTargetMemories();
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        decayAlertness();
        vision.setAlertness(alertness);

        // ヒートマップ記録: 敵を認識している最中に、撃たれずに物陰で耐えられた場所を「安全」として記録する
        if (safeRecordCooldownSteps > 0) safeRecordCooldownSteps--;
        Location heldCover = tactics.getTacticalCoverLoc();
        boolean engaged = target != null || lastKnownLocation != null;
        if (engaged && heldCover != null && safeRecordCooldownSteps == 0 && suppression < 0.2f
                && Bukkit.getCurrentTick() - lastDamagedTick > SAFE_RECORD_NO_DAMAGE_TICKS
                && heldCover.getWorld() == scav.getWorld() && scav.getLocation().distance(heldCover) < 1.5) {
            CombatHeatmapManager.record(scav.getLocation(), CombatHeatmapManager.TraceType.SAFE, 0.5f);
            safeRecordCooldownSteps = SAFE_RECORD_INTERVAL_STEPS;
        }

        // タイマー更新
        if (suppression > 0) suppression = Math.max(0, suppression - 0.02f);
        tactics.updateTimers();

        // 1. 分隊更新
        lastSquadUpdate++;
        if (lastSquadUpdate >= 10) {
            squad.updateNearbyAllies();
            squad.handleSquadRoles();
            lastSquadUpdate = 0;
        }
        brain.setSquadRole(squad.getMyRole());

        // 2. 索敵 & 情報共有
        if (target == null) {
            target = vision.scanForTargets();
            if (target != null) {
                scav.setTarget(target);
                playScavVoice(ScavVoice.SPOTTED);
            }
        } else if (!sawTargetLastStep) {
            // 見失った相手を追っている間も、見えている別の相手がいればそちらに切り替える
            // (追跡中の相手の予測ばかり見て、目の前に来た別のプレイヤーに反応しないのを防ぐ)
            LivingEntity other = vision.scanForTargets();
            if (other != null && !other.getUniqueId().equals(target.getUniqueId())) {
                target = other;
                scav.setTarget(target);
                lostTargetSteps = 0;
                pursuitSteps = 0;
                belief.clear();
                playScavVoice(ScavVoice.SPOTTED);
            }
        }

        if (target != null) {
            canSeeTarget = vision.checkTrackingVision(target);
            int nowTick = Bukkit.getCurrentTick();
            boolean freshSighting = !target.getUniqueId().equals(lastVisibleTargetId)
                    || nowTick - lastTargetVisibleTick > REACQUIRE_TICKS;
            if (canSeeTarget && !sawTargetLastStep && freshSighting) {
                // 見つけた直後は反応時間をおいてから撃ち始める。
                // 撃ち合い中に遮蔽物の端で見え隠れしただけの時は、反応し直さない。
                // 顔出しや、いそうな方向へ照準を向けていた時は、構えているぶん反応が速い
                long reaction = rollReactionDelayMs();
                if (tactics.getPeekPhase() > 0 || nowTick <= preAimUntilTick) reaction /= 3;
                fireHoldUntil = Math.max(fireHoldUntil, System.currentTimeMillis() + reaction);
            }
            if (canSeeTarget) {
                lastTargetVisibleTick = nowTick;
                lastVisibleTargetId = target.getUniqueId();
            }
            sawTargetLastStep = canSeeTarget;
            if (canSeeTarget) {
                recordSighting(target);
                lostTargetSteps = 0;
                pursuitSteps = 0;
                addAlertness(0.08f, "VISUAL_CONTACT", raidSessionId);
                lastKnownLocation = target.getLocation();
                searchTicks = 0;
                isAlerted = true;
                isPreAiming = false;
                updateHumanAim(target);

                enterCombat();
                if (Bukkit.getCurrentTick() - lastShareTick >= SHARE_INTERVAL_TICKS) {
                    lastShareTick = Bukkit.getCurrentTick();
                    if (Math.random() < 0.2) playScavVoice(ScavVoice.TAUNT);
                }
            } else {
                lostTargetSteps++;
                if (lostTargetSteps == 1) {
                    beginPursuit();
                }
                if (lostTargetSteps >= TARGET_MEMORY_STEPS && lastKnownLocation != null) {
                    scav.setTarget(null);
                    target = null;
                    canSeeTarget = false;
                    lostTargetSteps = 0;
                    isPreAiming = false;
                    handleSearching();
                } else {
                    handleSearching();
                    if (lastKnownLocation != null && !isSprinting) {
                        isPreAiming = true;
                        preAimAtThreat();
                    } else {
                        isPreAiming = false;
                    }
                }
            }
        } else {
            lostTargetSteps = 0;
            sawTargetLastStep = false;
            isPreAiming = false;
            handleSearching();
        }

        updateBelief(target, canSeeTarget);
        updateBehaviorState();
        // 自分で敵を捉えていない時は、呼ばれていれば味方の救援に向かう
        boolean assisting = target == null && lastKnownLocation == null && handleAssist();
        if (target == null && lastKnownLocation == null && !assisting) {
            handleIdleOrReturnHome(raidSessionId);
        }

        logTargetTransitionIfNeeded(raidSessionId, target);

        // 装備チェック
        ScavWeapon weapon = resolveWeapon();
        if (weapon == null) {
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, 0.0f, new int[] {8, 1});
            return;
        }

        if (target != null || lastKnownLocation != null || assisting) weapon.prepare();

        // 撃てない時間を自分で作るなら、相手から見られていない時に。隠れている間に弾を補充しておく
        boolean knowsEnemy = target != null || lastKnownLocation != null;
        boolean exposedToTarget = target != null && scav.hasLineOfSight(target);
        if (!exposedToTarget && tactics.getPeekPhase() == 0
                && weapon.ammoRatio() < (knowsEnemy ? TACTICAL_RELOAD_RATIO : 1.0)) {
            if (weapon.startReload() && knowsEnemy) playScavVoice(ScavVoice.RELOAD);
        }
        // 撃ち尽くしてリロードを始めた時も、戦っているなら味方に知らせる
        // (リロード中には構え直しなどの準備中も含まれるので、弾が無いことで見分ける)
        boolean reloading = weapon.isReloading();
        if (reloading && !wasReloading && weapon.ammo() <= 0 && knowsEnemy) playScavVoice(ScavVoice.RELOAD);
        wasReloading = reloading;
        weaponReadyInTicks = weapon.ticksUntilReady();
        boolean needsReload = weapon.needsReload() || weaponReadyInTicks >= LONG_UNREADY_TICKS;
        double healthPercent = scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        updateAmmoState(weapon);
        lastAmmoRatio = weapon.ammoRatio();
        // 撃てない間は下がる (弾を分けてもらいに行く間・弾が尽きて隠れる間は、それぞれの動きに任せる)
        boolean coverForReload = needsReload && ammoSupplierId == null && !unarmed;

        // 3. スイッチング
        if (squad.getMyRole() == ScavSquad.SquadRole.POINTMAN && (suppression > 0.8f || needsReload || healthPercent < 0.4)) {
            // 前衛を味方に任せたら、すぐに物陰へ下がる
            if (squad.requestRoleSwitch()) tactics.setCoverSearchCooldown(0);
        }

        // 4. カバー検索
        if (target != null && tactics.getCoverSearchCooldown() <= 0) {
            // 撃ってくる相手が見えている時は、制圧されていても物陰へ走らずに撃ち返す (移動中は撃たないため)
            if ((suppression > 0.6f && !canSeeTarget) || healthPercent < 0.4 || coverForReload) {
              if (!assisting && pursuitSteps == 0 && isEngaged(canSeeTarget) && (canSeeTarget || lastKnownLocation != null)) {
                // 相手の位置が分かっている間は、立ち位置の仕組みで隠れられる場所へ下がる
                // 負傷なら長く、リロードなら撃てるようになるまで、制圧だけなら短く下がる
                int withdrawTicks = SUPPRESSED_WITHDRAW_TICKS;
                if (coverForReload) withdrawTicks = Math.max(withdrawTicks, Math.min(WITHDRAW_TICKS, weaponReadyInTicks + RELOAD_WITHDRAW_MARGIN_TICKS));
                if (healthPercent < 0.4) withdrawTicks = WITHDRAW_TICKS;
                withdrawUntilTick = Bukkit.getCurrentTick() + withdrawTicks;
                positioning.invalidate();
                tactics.setCoverSearchCooldown(60);
              } else {
                Location cover = tactics.findCover(target);
                tactics.setTacticalCoverLoc(cover);
                tactics.setCoverSearchCooldown(60);
                if (cover != null) tactics.setCoverStayTicks(100);
                coverBestDistance = Double.MAX_VALUE;
                coverStuckSteps = 0;
              }
            }
        }

        Location tacticalCover = tactics.getTacticalCoverLoc();
        boolean movingToCover = tacticalCover != null
                && tactics.getCoverStayTicks() > 0
                && scav.getLocation().distanceSquared(tacticalCover) > 1.0;
        // 物陰へ近づけなくなったら諦める (移動中は撃たないため、引っかかったまま撃たなくなるのを防ぐ)
        if (movingToCover) {
            double coverDistance = scav.getLocation().distance(tacticalCover);
            if (coverDistance < coverBestDistance - COVER_PROGRESS_MIN) {
                coverBestDistance = coverDistance;
                coverStuckSteps = 0;
            } else if (++coverStuckSteps >= COVER_STUCK_STEPS) {
                tactics.setTacticalCoverLoc(null);
                tactics.setCoverStayTicks(0);
                movingToCover = false;
            }
        }
        // Aggression/fear are decision inputs, not physical sprint state. Treat only
        // active tactical relocation as sprinting so stationary SCAVs can fire.
        this.isSprinting = tactics.getPeekPhase() > 0 || movingToCover;

        // 5. タクティカルアドバイス
        float tacticalAdvice = 0.0f;
        boolean hasLos = target != null && scav.hasLineOfSight(target);
        if (!hasLos && lastKnownLocation != null) tacticalAdvice = 0.8f;
        else if (suppression > 0.5f) tacticalAdvice = 0.5f;

        if (target != null) {
            float lowEffectRatio = getLowEffectiveRatio(target.getUniqueId());
            if (brainLevel == ScavBrain.BrainLevel.MID && lowEffectRatio >= 0.55f) {
                tacticalAdvice = Math.max(tacticalAdvice, 0.85f);
                // 通りにくい相手を見続けないよう、MIDだけ周期的にターゲット再評価 (約1秒ごと)
                if (Bukkit.getCurrentTick() - lastRetargetTick >= 20) {
                    lastRetargetTick = Bukkit.getCurrentTick();
                    LivingEntity alt = vision.scanForTargets();
                    if (alt != null && !alt.getUniqueId().equals(target.getUniqueId())) {
                        scav.setTarget(alt);
                        target = alt;
                        canSeeTarget = vision.checkVision(target);
                        lastKnownLocation = target.getLocation();
                    }
                }
            }
        }

        // 6. Peek Maneuver
        if (tactics.getPeekPhase() > 0) {
            tactics.handlePeekManeuver(canSeeTarget, System.currentTimeMillis() >= fireHoldUntil, this::applyAimToEntity,
                    weapon, baseSpread() + suppression * SUPPRESSION_SPREAD, isSprinting, lastMobShotTime, t -> lastMobShotTime = t);
            checkAndInteractWithDoors();
            int[] peekActions = brain.decide(canSeeTarget ? target : null, lastKnownLocation, weapon, suppression, tacticalAdvice, isSprinting, alertness);
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, peekActions);
            return;
        }

        // 7. AI思考 & 移動
        brain.updateConditions(healthPercent < 0.3, needsReload, suppression > 0.5f, tacticalAdvice > 0.5f);
        int[] actions = brain.decide(canSeeTarget ? target : null, lastKnownLocation, weapon, suppression, tacticalAdvice, isSprinting, alertness);
        if (actions.length < 2) {
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, actions);
            return;
        }

        int moveAction = actions[0];
        // 特殊移動判定
        float[] neurons = brain.getNeuronStates();
        // 行動番号は ScavBrain の定義 (2: 後退, 3/4: 横移動, 6: ピーク, 7: ジャンプピーク) に合わせる
        if (neurons[1] > 0.7f) {
            // 怖い時は下がる。強い制圧下では横に動いて弾を避ける
            moveAction = (suppression > 0.5f && Math.random() < 0.6) ? ((Math.random() > 0.5) ? 3 : 4) : 2;
        }
        if (neurons[2] > 0.7f && !hasLos && lastKnownLocation != null) {
            if (neurons[0] > 0.6f && Math.random() > 0.6) moveAction = 6; // Peek
            else if (Math.random() > 0.8) moveAction = 1; // 見えていない時のピーク以外の動きは捜索になる
        }

        // 相手の位置が分かっている間は、立ち位置を選んでそこで戦う (使えない時は以下の従来の動き)。
        // 見失った直後の追跡・顔出し・弾切れや負傷で物陰へ下がっている間は、そちらを優先する
        boolean retreatingToCover = tactics.getTacticalCoverLoc() != null && tactics.getCoverStayTicks() > 0;
        boolean fetchingAmmo = fetchAmmoFromAlly(weapon) || handleUnarmed(target);
        boolean positioned = !fetchingAmmo && !assisting && pursuitSteps == 0 && !retreatingToCover && isEngaged(canSeeTarget)
                && handlePositioning(target, canSeeTarget);
        if (!positioned && !fetchingAmmo) followingPath = false;
        if (fetchingAmmo) {
            isHoldingAngle = false;
        } else if (positioned) {
            isHoldingAngle = false;
        } else if (moveAction == 8 && !assisting) {
            isHoldingAngle = true;
            preAimAtThreat();
            tactics.stopMoving();
        } else if (tactics.getTacticalCoverLoc() != null && tactics.getCoverStayTicks() > 0) {
            isHoldingAngle = false;
            double dist = scav.getLocation().distance(tactics.getTacticalCoverLoc());
            if (dist > 1.0) scav.getPathfinder().moveTo(tactics.getTacticalCoverLoc(), isSprinting ? 1.5 : 1.0);
            else if (!needsReload && canSeeTarget) {
                applyAimToEntity();
            } else if (weaponReadyInTicks <= PEEK_READY_TICKS && lastKnownLocation != null && (moveAction == 6 || moveAction == 7)) {
                // 物陰に着いたら、隠れたままにならないよう顔を出して撃ち返す
                tactics.startPeek(lastKnownLocation, isSprinting, moveAction == 7);
            }
        } else if (canSeeTarget) {
            isHoldingAngle = false;
            boolean isAuto = weapon.isAutomatic();
            tactics.handleCombatMovement(moveAction, target, isAuto, neurons[0], suppression, isSprinting, squad.getNearbyAllies(), brain.getEngagementRange());
        } else if (lastKnownLocation != null) {
            isHoldingAngle = false;
            // 顔出しは前回から間が空いていなければ行わず、捜索を続ける
            boolean peek = (moveAction == 6 || moveAction == 7) && weaponReadyInTicks <= PEEK_READY_TICKS;
            if (!peek || !tactics.startPeek(lastKnownLocation, isSprinting, moveAction == 7)) handleSearching();
        }

        checkAndInteractWithDoors();

        // 射撃 (経路探索で回り込んでいる間は進む方向を向いているため撃たない)
        if (!followingPath) {
            long now = System.currentTimeMillis();
            long interval = (long) (60000.0 / weapon.rpm());
            if (canSeeTarget && actions[1] == 0) {
                applyAimToEntity();
                if (now >= fireHoldUntil && now - lastMobShotTime >= interval) {
                    boolean fullAuto = useFullAuto(weapon, target);
                    // 単発で撃つ時やポンプアクションの場合は人間らしい「タップ遅延」や「次弾装填待ち」を追加
                    if (!fullAuto || weapon.isManualAction()) {
                        long extraDelay = 50 + (long)(Math.random() * 150);
                        if (weapon.isManualAction()) {
                            extraDelay += 300 + (long)(Math.random() * 400); // ポンプアクションはコッキング時間を考慮して大幅に遅延
                        }
                        if (now - lastMobShotTime < interval + extraDelay) return;
                    }

                    // 間が空いたら反動は収まっている
                    if (now - lastMobShotTime > RECOIL_RESET_MS) consecutivePulls = 0;
                    double inacc = baseSpread()
                            + (suppression * SUPPRESSION_SPREAD)
                            + (scav.getVelocity().length() > 0.1 ? 0.04 : 0)
                            + Math.min(RECOIL_SPREAD_MAX, consecutivePulls * (fullAuto ? RECOIL_SPREAD_PER_PULL : RECOIL_SPREAD_PER_SINGLE_SHOT));
                    if (weapon.isManualAction()) {
                        inacc += 0.08; // ポンプアクションは反動が大きく、次弾の精密射撃が難しいことを表現
                    }

                    // 構え直し中などで引き金を引けなかった時は、連射数・射撃間隔に数えない
                    if (weapon.fire(inacc, fullAuto)) {
                        consecutivePulls++;
                        lastMobShotTime = now;
                        afterTriggerPull(fullAuto, now);
                    }
                }
            } else if (!canSeeTarget && suppressiveFire(weapon, now)) {
                // 見えない相手が出てきそうな所へ撃ち込んだ
            } else if (actions[1] == 0 && target != null && !unarmed) {
                tactics.handleJumpShot(target);
            }
        }

        logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, actions);
    }

    /**
     * 予備の弾が尽きて撃ち切った時: 同じ口径の弾を持つ味方がいれば分けてもらいに行き、いなければ撃ち合いを避けて隠れる
     * ({@link #handleUnarmed})。弾がある間は何もしない。味方・隠れる所は1秒ごとに探し直す
     */
    private void updateAmmoState(ScavWeapon weapon) {
        ScavAmmoSupply supply = ScavAmmoSupply.of(scav.getUniqueId());
        boolean outOfAmmo = supply != null && supply.spare() <= 0 && weapon.ammo() <= 0 && !weapon.isReloading();
        if (!outOfAmmo) {
            ammoSupplierId = null;
            unarmed = false;
            unarmedRefuge = null;
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (now < unarmedRecheckTick) return;
        unarmedRecheckTick = now + UNARMED_RECHECK_TICKS;
        ScavController supplier = findAmmoSupplier(supply);
        UUID previous = ammoSupplierId;
        ammoSupplierId = supplier != null ? supplier.getScav().getUniqueId() : null;
        boolean wasUnarmed = unarmed;
        unarmed = supplier == null;
        if (unarmed) unarmedRefuge = findRefuge();
        if (ammoSupplierId != null && !ammoSupplierId.equals(previous)) {
            Bukkit.getLogger().info("[AI_AMMO] " + scav.getUniqueId().toString().substring(0, 8)
                    + " | out of ammo, fetching from " + ammoSupplierId.toString().substring(0, 8));
        } else if (unarmed && !wasUnarmed) {
            Bukkit.getLogger().info("[AI_AMMO] " + scav.getUniqueId().toString().substring(0, 8)
                    + " | out of ammo, no supplier, refuge=" + (unarmedRefuge == null ? "none" : unarmedRefuge.toVector()));
        }
    }

    /** 弾が尽きた時の逃げ場: 相手から見えない近くの場所。無ければ相手と反対の方へ離れた、たどり着ける場所 */
    private Location findRefuge() {
        Location threat = threatEye();
        if (threat == null) return null;
        Location hidden = positioning.nearestHidden(threat);
        if (hidden != null) return hidden;
        Vector away = scav.getLocation().toVector().subtract(threat.toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-6) away = new Vector(1, 0, 0);
        away.normalize();
        for (double angle : new double[]{0, 45, -45, 90, -90}) {
            Vector direction = away.clone().rotateAroundY(Math.toRadians(angle));
            Location candidate = scav.getLocation().add(direction.multiply(UNARMED_FLEE_DISTANCE));
            var path = scav.getPathfinder().findPath(candidate);
            if (path != null && path.canReachFinalPoint()) return candidate;
        }
        return null;
    }

    /** 相手の目の位置 (見えていれば今の位置、見失っていれば最後に分かった位置) */
    private Location threatEye() {
        LivingEntity target = scav.getTarget();
        if (target != null && target.getWorld() == scav.getWorld()) return target.getEyeLocation();
        return lastKnownLocation != null && lastKnownLocation.getWorld() == scav.getWorld()
                ? lastKnownLocation.clone().add(0, 1.6, 0) : null;
    }

    /**
     * 弾が尽き、分けてくれる味方もいない時: 詰めたり撃ち合いの立ち位置に出たりせず、逃げ場へ下がってそこで待つ
     * (相手を見張り、味方の救援を待つ)。敵を知らない時は何もしない
     *
     * @return 逃げ場へ向かっている・待っている (他の移動をしない) 場合true
     */
    private boolean handleUnarmed(LivingEntity target) {
        if (!unarmed || (target == null && lastKnownLocation == null)) return false;
        if (unarmedRefuge == null || unarmedRefuge.getWorld() != scav.getWorld()) {
            // 逃げ場が無い: その場で止まって見張る (詰めはしない)
            tactics.stopMoving();
            scav.getPathfinder().stopPathfinding();
            preAimAtThreat();
            return true;
        }
        if (scav.getLocation().distanceSquared(unarmedRefuge) > 1.0) {
            scav.getPathfinder().moveTo(unarmedRefuge, RELOCATE_SPEED);
            followingPath = true;
        } else {
            tactics.stopMoving();
            preAimAtThreat();
        }
        return true;
    }

    /** 同じ口径の弾を、自分の1マガジン分より多く持っている近くの味方 (いちばん近い者) */
    private ScavController findAmmoSupplier(ScavAmmoSupply mine) {
        String caliber = mine == null ? null : mine.caliber();
        if (caliber == null) return null;
        ScavController best = null;
        double bestDistance = AMMO_SUPPLIER_RANGE * AMMO_SUPPLIER_RANGE;
        for (ScavController ally : squad.getNearbyAllies()) {
            if (ally == this || !ally.getScav().isValid() || ally.getScav().getWorld() != scav.getWorld()) continue;
            if (spareToShare(ally, caliber) <= 0) continue;
            double distance = ally.getScav().getLocation().distanceSquared(scav.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ally;
            }
        }
        return best;
    }

    /** 味方が分けられる弾の数 (同じ口径で、自分の銃の1マガジン分は手元に残す) */
    private static int spareToShare(ScavController ally, String caliber) {
        ScavAmmoSupply supply = ScavAmmoSupply.of(ally.getScav().getUniqueId());
        if (supply == null || !caliber.equalsIgnoreCase(supply.caliber())) return 0;
        DatapackGunProfile profile = DatapackGunCatalog.get(
                DatapackGunCatalog.gunIdOf(ally.getScav().getEquipment().getItemInMainHand()));
        int keep = profile != null ? profile.magazineSize() : 30;
        return supply.spare() - keep;
    }

    /**
     * 弾を分けてくれる味方が決まっていれば、その味方の所へ行き、着いたら弾を受け取る (自分の2マガジン分まで)。
     * 受け取ると予備ができるので、撃ち切った銃はそのまま再装填が始まる
     *
     * @return 味方の所へ向かっている (他の移動をしない) 場合true
     */
    private boolean fetchAmmoFromAlly(ScavWeapon weapon) {
        if (ammoSupplierId == null) return false;
        ScavController ally = ScavSpawner.getController(ammoSupplierId);
        ScavAmmoSupply mine = ScavAmmoSupply.of(scav.getUniqueId());
        if (ally == null || mine == null || !ally.getScav().isValid() || ally.getScav().getWorld() != scav.getWorld()) {
            ammoSupplierId = null;
            unarmedRecheckTick = 0;
            return false;
        }
        Location allyAt = ally.getScav().getLocation();
        if (scav.getLocation().distanceSquared(allyAt) > AMMO_HANDOVER_DISTANCE * AMMO_HANDOVER_DISTANCE) {
            scav.getPathfinder().moveTo(allyAt, RELOCATE_SPEED);
            followingPath = true;
            return true;
        }
        int share = Math.min(spareToShare(ally, mine.caliber()), weapon.magazineSize() * 2);
        ScavAmmoSupply allySupply = ScavAmmoSupply.of(ammoSupplierId);
        int taken = share > 0 && allySupply != null ? allySupply.take(share) : 0;
        mine.add(taken);
        Bukkit.getLogger().info(String.format("[AI_AMMO] %s | handover from %s rounds=%d",
                scav.getUniqueId().toString().substring(0, 8), ammoSupplierId.toString().substring(0, 8), taken));
        ammoSupplierId = null;
        unarmedRecheckTick = 0;
        return false;
    }

    /**
     * 制圧射撃: 見失って間もない相手が出てきそうな所 (遮蔽の端) へ、単発を間隔を空けて撃ち込み、顔を出させない。
     * 撃ち込む所は自分の推定 (見た・聞いた・撃たれたことだけから作る) で決め、射線の通る所にだけ撃つ。
     * 数発撃ったら間を置き、顔を出された時のために弾を残す
     *
     * @return 撃った場合true
     */
    private boolean suppressiveFire(ScavWeapon weapon, long now) {
        int tick = Bukkit.getCurrentTick();
        if (tick - lastTargetVisibleTick > SUPPRESS_MEMORY_TICKS) return false;
        if (weaponReadyInTicks > 0 || weapon.ammoRatio() < SUPPRESS_MIN_AMMO_RATIO || tactics.getPeekPhase() > 0) return false;
        // 味方が立ち位置を移っている間は、その援護として撃つ (間を置かず、出てきそうな所が多少ばらけていても撃つ)
        boolean coveringAlly = false;
        for (ScavController ally : squad.getNearbyAllies()) {
            if (ally.isRelocating()) {
                coveringAlly = true;
                break;
            }
        }
        if (tick < suppressPauseUntilTick && !coveringAlly) return false;
        OpponentBelief.Estimate emergence = belief.emergence(tick);
        if (emergence == null || emergence.mass() < (coveringAlly ? SUPPRESS_COVER_MIN_SHARE : SUPPRESS_MIN_SHARE)) return false;
        Location point = emergence.location().clone().add(0, 1.5, 0);
        Location eye = scav.getEyeLocation();
        if (point.getWorld() != eye.getWorld() || eye.distance(point) > brain.getEngagementRange() * SUPPRESS_RANGE_FACTOR) return false;
        Vector toPoint = point.toVector().subtract(eye.toVector());
        double distance = toPoint.length();
        if (distance < 1.0E-3) return false;
        toPoint.multiply(1.0 / distance);
        org.bukkit.util.RayTraceResult blocked = eye.getWorld().rayTraceBlocks(eye, toPoint, distance, org.bukkit.FluidCollisionMode.NEVER, true);
        if (blocked != null && blocked.getHitBlock() != null) return false; // 壁に撃ち込まない
        updatePreAim(emergence.location());
        // 照準がその所へ向いてから撃つ
        if (currentAimVector == null || Math.toDegrees(currentAimVector.angle(toPoint)) > SUPPRESS_AIM_TOLERANCE_DEGREES) return false;
        long gap = SUPPRESS_INTERVAL_MS + (long) (Math.random() * SUPPRESS_INTERVAL_JITTER_MS);
        if (now < fireHoldUntil || now - lastMobShotTime < gap) return false;
        applyAimToEntity();
        if (!weapon.fire(baseSpread() + suppression * SUPPRESSION_SPREAD + SUPPRESS_EXTRA_SPREAD, false)) return false;
        lastMobShotTime = now;
        lastSuppressTick = tick;
        if (++suppressShots >= suppressBurst) {
            suppressShots = 0;
            suppressBurst = SUPPRESS_BURST_MIN + (int) (Math.random() * (SUPPRESS_BURST_MAX - SUPPRESS_BURST_MIN + 1));
            suppressPauseUntilTick = tick + SUPPRESS_PAUSE_TICKS + (int) (Math.random() * SUPPRESS_PAUSE_JITTER_TICKS);
        }
        return true;
    }

    /** 最近、見えない相手へ制圧射撃をしたか */
    public boolean isSuppressing() {
        return Bukkit.getCurrentTick() - lastSuppressTick <= SUPPRESSING_TICKS;
    }

    /** 選んだ立ち位置へ移っている途中か (味方はこれを見て制圧射撃で援護する) */
    public boolean isRelocating() {
        return positioning.current() != null && arrivedSpot == null;
    }

    /** ランクごとの基本の弾のばらつき (BulletTaskの拡散量と同じ尺度)。高ランクほど正確 */
    private double baseSpread() {
        return switch (brainLevel) {
            case LOW -> 0.13;
            case MID -> 0.09;
            case HIGH -> 0.04;
        };
    }

    /** 発見から撃ち始めるまでの反応時間 (ms)。ランクが高いほど速いが、毎回ばらつく */
    private long rollReactionDelayMs() {
        return switch (brainLevel) {
            case LOW -> 250 + (long) (Math.random() * 300);
            case MID -> 170 + (long) (Math.random() * 250);
            case HIGH -> 120 + (long) (Math.random() * 200);
        };
    }

    /**
     * 引き金を引いた後の間を決める。フルオートは数回で指を離し (指切り)、単発の銃は時々ためらう。
     * データパック銃は引き金を引いてから数tick撃ち続けるため、間はそれより長めに取る
     */
    /**
     * フルオートで撃つか。通常のSCAV (LOW/MID) は単発で撃ち、近くまで詰められた時だけ短く連射する。HIGHは常にフルオート
     */
    private boolean useFullAuto(ScavWeapon weapon, LivingEntity target) {
        if (!weapon.isAutomatic()) return false;
        if (brainLevel == ScavBrain.BrainLevel.HIGH) return true;
        return target != null && target.getWorld() == scav.getWorld()
                && scav.getLocation().distanceSquared(target.getLocation()) <= CLOSE_RANGE_FULL_AUTO * CLOSE_RANGE_FULL_AUTO;
    }

    private void afterTriggerPull(boolean fullAuto, long now) {
        if (fullAuto) {
            if (burstPullsRemaining <= 0) {
                // 引き金1回でデータパック銃は約5tick撃ち続けるため、回数は少なめ。
                // 通常のSCAVの近距離での連射は1〜2回、HIGHは1〜3回
                burstPullsRemaining = switch (brainLevel) {
                    case LOW, MID -> 1 + (int) (Math.random() * 2);
                    case HIGH -> 1 + (int) (Math.random() * 3);
                };
            }
            if (--burstPullsRemaining <= 0) {
                fireHoldUntil = now + 300 + (long) (Math.random() * 500);
            }
        } else if (Math.random() < 0.12) {
            fireHoldUntil = now + 250 + (long) (Math.random() * 450);
        }
    }

    /** 見えている間、ターゲットの水平移動速度 (ブロック/tick) を観測しておく */
    private void recordSighting(LivingEntity target) {
        Location now = target.getLocation();
        int tick = Bukkit.getCurrentTick();
        if (!target.getUniqueId().equals(lastSeenTargetId)) {
            // 別の相手に切り替わったら、前の相手の移動速度は使わない
            lastSeenTargetId = target.getUniqueId();
            lastSeenLocation = null;
            observedVelocity = new Vector();
        }
        if (lastSeenLocation != null && lastSeenLocation.getWorld() == now.getWorld()) {
            int elapsed = tick - lastSeenTick;
            if (elapsed > 0 && elapsed <= 20) {
                Vector velocity = now.toVector().subtract(lastSeenLocation.toVector()).multiply(1.0 / elapsed).setY(0);
                observedVelocity = observedVelocity.multiply(0.5).add(velocity.multiply(0.5));
            } else {
                observedVelocity = new Vector();
            }
        }
        squad.releaseSearch();
        assignedSearchPoint = null;
        lastSeenLocation = now.clone();
        lastSeenTick = tick;
    }

    /**
     * 見失った瞬間に、観測した移動方向へ先読みした地点 (壁があれば手前) を最後の位置とし、そこへ走って追う
     */
    private void beginPursuit() {
        if (lastSeenLocation == null || lastSeenLocation.getWorld() != scav.getWorld()) return;
        Vector lead = observedVelocity.clone().multiply(LOSS_PREDICTION_TICKS);
        if (lead.length() > LOSS_PREDICTION_MAX_DISTANCE) {
            lead.normalize().multiply(LOSS_PREDICTION_MAX_DISTANCE);
        }
        Location predicted = lastSeenLocation.clone();
        if (lead.lengthSquared() > 0.01) {
            Location from = lastSeenLocation.clone().add(0, 1.0, 0);
            Vector dir = lead.clone().normalize();
            org.bukkit.util.RayTraceResult hit = from.getWorld().rayTraceBlocks(from, dir, lead.length(), org.bukkit.FluidCollisionMode.NEVER, true);
            double reach = (hit != null && hit.getHitPosition() != null)
                    ? Math.max(0.0, hit.getHitPosition().distance(from.toVector()) - 0.5)
                    : lead.length();
            predicted.add(dir.multiply(reach));
        }
        lastKnownLocation = predicted;
        belief.reset(lastSeenLocation, observedVelocity);
        // 立ち位置を選んで戦っている間は、見失った (多くは自分から遮蔽に引っ込んだ) 位置へ走って追わない。
        // 最後に分かった位置は立ち位置の選び直しに使う
        pursuitSteps = positioning.current() != null ? 0 : PURSUIT_STEPS;
        searchTicks = 0;
        cornerCheckTicks = 0;
        tactics.resetSlicing();
    }

    private void handleSearching() {
        // 見失い処理と移動処理の両方から呼ばれるため、1tickに1回だけ進める
        int tick = Bukkit.getCurrentTick();
        if (tick == lastSearchTick) return;
        lastSearchTick = tick;

        if (lastKnownLocation != null && pursuitSteps > 0) {
            pursuitSteps--;
            if (lastKnownLocation.getWorld() == scav.getWorld() && scav.getLocation().distance(lastKnownLocation) > 2.5) {
                scav.getPathfinder().moveTo(lastKnownLocation, PURSUIT_SPEED);
                preAimAtThreat();
                return;
            }
            pursuitSteps = 0; // 予測地点に着いた: ここからは角を確認しながら捜索する
        }

        if (lastKnownLocation == null) {
            if (isAlerted && Math.random() < 0.05) {
                scav.setRotation(scav.getLocation().getYaw() + (float)(Math.random()-0.5)*90f, (float)(Math.random()-0.5)*40f);
            }
            return;
        }
        // 捜索中も、照準は相手が出てきそうな所 (分からなければ一番ありそうな場所) へ向けておく
        ScavContactReport report = visualReport();
        Location searchGoal = report != null ? assignedSearchGoal(report) : null;
        tactics.handleSearching(searchGoal != null ? searchGoal : lastKnownLocation, searchTicks, isSprinting, loc -> preAimAtThreat());
        double dist = scav.getLocation().distance(searchGoal != null ? searchGoal : lastKnownLocation);
        if (dist <= 2.5) {
            cornerCheckTicks++;
            if (cornerCheckTicks > 60) {
                lastKnownLocation = null;
                positioning.clear();
                tactics.resetSlicing();
                cornerCheckTicks = 0;
                isAlerted = false;
            }
        }
        searchTicks++;
        if (searchTicks > 600) {
            lastKnownLocation = null;
            positioning.clear();
            isAlerted = false;
        }
    }

    /** 照準のブレを更新する。照準そのものは tickAim が毎tick相手へ寄せる */
    private void updateHumanAim(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        if (currentAimVector == null) currentAimVector = idealDir.clone();
        aimErrorYaw = (aimErrorYaw + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
        aimErrorPitch = (aimErrorPitch + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
    }

    /** 見えていない相手へ照準を向けておく。出てきそうな所 (遮蔽の端) が分かればそこへ、なければ一番ありそうな場所へ */
    private void preAimAtThreat() {
        OpponentBelief.Estimate emergence = belief.emergence(Bukkit.getCurrentTick());
        if (emergence != null) updatePreAim(emergence.location());
        else if (lastKnownLocation != null) updatePreAim(lastKnownLocation);
    }

    /** 見えていない相手がいそうな場所へ照準を向けておく。照準は tickAim が毎tick寄せる */
    private void updatePreAim(Location loc) {
        preAimPoint = loc.clone().add(0, 1.5, 0);
        preAimUntilTick = Bukkit.getCurrentTick() + PRE_AIM_HOLD_TICKS;
    }

    /**
     * 毎tick呼ぶ。見えている相手 (なければ照準を向けておく場所) へ照準を滑らかに寄せ、SCAVの向きに反映する。
     * 判断 (onTick) は3tickごとだが、向きをその間隔で変えると、間に移動の向きへ引き戻されて首が暴れる
     */
    public void tickAim() {
        if (!scav.isValid()) return;
        updateAim();
        // 直接移動は向きを基準に前後左右を決めるため、照準を合わせた後に指示する
        tactics.tickDirectMove();
    }

    private void updateAim() {
        // 経路探索で立ち位置へ歩いている間は、進む方向を向かせる (照準で向きを上書きすると、
        // 移動の制御が1tickに90度までしか回れず、相手と反対側の場所へ進めなくなる)
        if (followingPath) return;
        LivingEntity target = scav.getTarget();
        Vector ideal = null;
        double stepLerp;
        if (sawTargetLastStep && target != null && target.isValid() && target.getWorld() == scav.getWorld()) {
            ideal = target.getEyeLocation().toVector().subtract(scav.getEyeLocation().toVector());
            stepLerp = 0.9 - (suppression * 0.2);
        } else if (preAimPoint != null && Bukkit.getCurrentTick() <= preAimUntilTick && preAimPoint.getWorld() == scav.getWorld()) {
            ideal = preAimPoint.toVector().subtract(scav.getEyeLocation().toVector());
            stepLerp = 0.3;
        } else {
            return;
        }
        if (ideal.lengthSquared() < 1.0E-6) return;
        ideal.normalize();
        if (currentAimVector == null) currentAimVector = ideal.clone();
        // 1ステップ (STEP_TICKS tick) で stepLerp だけ寄るよう、1tickあたりの割合に直す
        double tickLerp = 1.0 - Math.pow(1.0 - stepLerp, 1.0 / STEP_TICKS);
        currentAimVector = currentAimVector.clone().add(ideal.subtract(currentAimVector).multiply(tickLerp)).normalize();
        applyAimToEntity();
    }

    private void applyAimToEntity() {
        if (currentAimVector == null) return;
        Location l = scav.getLocation();
        scav.setRotation(l.setDirection(currentAimVector).getYaw() + (float)aimErrorYaw, l.setDirection(currentAimVector).getPitch() + (float)aimErrorPitch);
    }

    public void onSoundHeard(Location source) {
        onSoundHeard(SoundContact.gunshot(source));
    }

    public void onSoundHeard(SoundContact sound) {
        if (sound == null || sound.sourceLocation == null) return;
        // 相手が見えている間は、音で分かることはない (撃ってくる相手の銃声のたびに振り向かない)
        if (sawTargetLastStep && scav.getTarget() != null) return;
        if (scav.getTarget() != null && scav.hasLineOfSight(scav.getTarget()) && sound.kind != SoundContact.Kind.GUNSHOT) {
            return;
        }

        Location source = sound.sourceLocation.clone();
        if (source.getWorld() != scav.getWorld()) return;
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        double dist = scav.getLocation().distance(source);

        // 音から分かる位置はおおまか (遠いほど・壁越しほどずれる)。足音は壁越しだとこもって遠くまで届かない
        boolean muffled = !hasClearSoundPath(source);
        if (sound.kind == SoundContact.Kind.FOOTSTEP && muffled && dist > FOOTSTEP_MUFFLED_RANGE) return;
        double error = sound.kind == SoundContact.Kind.GUNSHOT
                ? Math.min(GUNSHOT_MAX_LOCATION_ERROR, dist * (muffled ? 0.15 : 0.08))
                : Math.min(FOOTSTEP_MAX_LOCATION_ERROR, dist * (muffled ? 0.2 : 0.08));
        source.add((Math.random() - 0.5) * 2.0 * error, 0, (Math.random() - 0.5) * 2.0 * error);

        // 連射など、同じあたりから音が続いている: 居場所の推定だけ更新し、反応 (振り向き・声・調べ始め) はし直さない
        int nowTick = Bukkit.getCurrentTick();
        if (nowTick - lastSoundReactionTick < SOUND_REPEAT_TICKS && lastHeardSoundLocation != null
                && lastHeardSoundLocation.getWorld() == source.getWorld() && lastHeardSoundLocation.distance(source) < SOUND_REPEAT_DISTANCE) {
            if (lastKnownLocation != null) {
                belief.observeNear(source, Math.max(1.0, error * 0.6));
                OpponentBelief.Estimate estimate = belief.estimate();
                if (estimate != null) lastKnownLocation = estimate.location();
            }
            return;
        }
        lastSoundReactionTick = nowTick;

        float hearingBoost = (float) Math.max(0.08, 0.28 - (dist / 180.0));
        double movementSpeed = Math.max(0.0, sound.movementSpeed);
        if (sound.kind == SoundContact.Kind.GUNSHOT) {
            hearingBoost = (float) Math.max(0.20, 0.45 - (dist / 140.0));
        } else {
            if (sound.sprinting) hearingBoost += 0.08f;
            if (sound.continuousNoiseTicks >= 40) hearingBoost += 0.08f;
            if (sound.continuousNoiseTicks >= 80) hearingBoost += 0.05f;
            if (!sound.sneaking && movementSpeed > 0.12) hearingBoost += 0.03f;
            if (sound.walkedDistance > 1.5) hearingBoost += 0.03f;
        }
        addAlertness(hearingBoost, "SOUND_HEARD", raidSessionId);

        isAlerted = true;
        lastHeardSoundLocation = source.clone();

        SoundArc arc = classifySoundArc(source);
        int baseInvestigate = (behaviorState == BehaviorState.RELAXED) ? 40 : 100;
        if (sound.kind == SoundContact.Kind.GUNSHOT) {
            baseInvestigate += 20;
        } else {
            if (sound.sprinting) baseInvestigate += 20;
            if (sound.continuousNoiseTicks >= 40) baseInvestigate += 20;
            if (arc != SoundArc.FRONT) baseInvestigate += 20;
            if (arc == SoundArc.BACK) baseInvestigate += 20;
        }
        investigateTicks = baseInvestigate;

        // 足音の通知は2秒以上歩き続けてから来るため、「長く音を出し続けている」はそれより長い時間で判断する
        if (sound.kind == SoundContact.Kind.GUNSHOT
                || arc != SoundArc.FRONT
                || sound.sprinting
                || sound.continuousNoiseTicks >= FOOTSTEP_TRACKING_NOISE_TICKS
                || behaviorState != BehaviorState.RELAXED) {
            Location inferred = source.clone();
            if (sound.kind == SoundContact.Kind.FOOTSTEP && sound.movementDirection != null && sound.movementSpeed > 0.05) {
                double lead = Math.min(5.0, 1.0 + (sound.movementSpeed * 0.35) + (arc == SoundArc.BACK ? 1.25 : 0.0));
                inferred.add(sound.movementDirection.clone().normalize().multiply(lead));
            }
            // 音の位置は誤差込みの手がかりとして推定に入れる (前の推定と合わせて、一番ありそうな場所を使う)
            belief.observeNear(inferred, Math.max(1.0, error * 0.6));
            OpponentBelief.Estimate estimate = belief.estimate();
            lastKnownLocation = estimate != null ? estimate.location() : inferred;
        }

        // 音の方へ振り向く (一瞬で向きを変えず、照準と同じく滑らかに、音のした所の目の高さへ)
        updatePreAim(source);
        playScavVoice(ScavVoice.HEARD);

        if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("x", source.getX());
            payload.put("y", source.getY());
            payload.put("z", source.getZ());
            payload.put("state", behaviorState.name());
            payload.put("kind", sound.kind.name());
            payload.put("arc", arc.name());
            payload.put("speed", movementSpeed);
            payload.put("continuousTicks", sound.continuousNoiseTicks);
            payload.put("walkedDistance", sound.walkedDistance);
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "SOUND_INVESTIGATE_START", payload);
        }
    }

    /** 音源との間に壁が無いか (足音が直接届くか) */
    private boolean hasClearSoundPath(Location source) {
        Location eye = scav.getEyeLocation();
        Vector toSource = source.clone().add(0, 1.0, 0).toVector().subtract(eye.toVector());
        double length = toSource.length();
        if (length < 0.01) return true;
        var hit = scav.getWorld().rayTraceBlocks(eye, toSource.multiply(1.0 / length), length, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    private enum SoundArc {
        FRONT,
        SIDE,
        BACK
    }

    private SoundArc classifySoundArc(Location source) {
        Vector toSource = source.toVector().subtract(scav.getLocation().toVector());
        if (toSource.lengthSquared() == 0) return SoundArc.FRONT;

        Vector forward = scav.getLocation().getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() == 0) return SoundArc.FRONT;
        forward.normalize();

        Vector flatToSource = toSource.clone();
        flatToSource.setY(0);
        if (flatToSource.lengthSquared() == 0) return SoundArc.FRONT;
        flatToSource.normalize();

        Vector lateral = new Vector(-forward.getZ(), 0, forward.getX());
        double frontDot = flatToSource.dot(forward);
        double sideDot = Math.abs(flatToSource.dot(lateral.normalize()));

        if (frontDot < -0.35) return SoundArc.BACK;
        if (sideDot > 0.65) return SoundArc.SIDE;
        return SoundArc.FRONT;
    }

    public void playScavVoice(ScavVoice voice) {
        int now = Bukkit.getCurrentTick();
        if (now < voiceAvailableTick) return;

        // 周囲の味方が最近喋ったかチェック
        for (ScavController ally : squad.getNearbyAllies()) {
            if (now - ally.lastVoiceTick < ALLY_VOICE_GAP_TICKS) return; // 誰かが2秒以内に喋り出していたらキャンセル
        }

        scav.getWorld().playSound(scav.getLocation(), voice.sound(), 1.0f, 1.0f);
        lastVoiceTick = now;
        voiceAvailableTick = now + VOICE_LINE_COOLDOWN_TICKS + (int)(Math.random() * 40); // 5〜7秒のクールダウン
    }

    public void onKill(LivingEntity victim) {
        playScavVoice(ScavVoice.KILL);
    }

    public void onDamage(Entity attacker) {
        attacker = DatapackGunnerManager.resolveShooter(attacker);
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        addAlertness(0.35f, "TOOK_DAMAGE", raidSessionId);
        suppression = Math.min(1.0f, suppression + 0.3f);
        lastDamagedTick = Bukkit.getCurrentTick();
        // 出直して撃ち合っている時に撃たれたら、遮蔽へ戻る (撃ち合いを続けて撃たれ続けない)
        reengageUntilTick = 0;
        brain.requestDecision("DAMAGE_EVENT");
        positioning.recordHit(scav.getLocation());
        CombatHeatmapManager.record(scav.getLocation(), CombatHeatmapManager.TraceType.DANGER, 1.0f);
        playScavVoice(ScavVoice.HIT);
        if (attacker instanceof LivingEntity living && living.getWorld() == scav.getWorld()) {
            Vector toAttacker = living.getLocation().toVector().subtract(scav.getLocation().toVector());
            // 同じ位置からの被弾 (向きが決まらない) では振り向かない
            if (toAttacker.lengthSquared() > 1.0E-6) scav.teleport(scav.getLocation().setDirection(toAttacker.normalize()));
            // 撃たれた方向から、撃った相手のおおよその位置が分かる
            belief.observeNear(living.getLocation(), ATTACKER_LOCATION_SIGMA);
            if (scav.getTarget() == null) {
                scav.setTarget(living);
                lastKnownLocation = living.getLocation();
            }
        }
        enterCombat();
    }

    /**
     * 撃ち合いの最中か (相手が見えている・見失って間もない・撃たれて間もない)。
     * この間だけ立ち位置を選んで戦う。音だけで知った遠くの相手には、立ち位置で待たずに捜索で近づく
     */
    private boolean isEngaged(boolean canSeeTarget) {
        int now = Bukkit.getCurrentTick();
        return canSeeTarget || now - lastTargetVisibleTick <= ENGAGED_MEMORY_TICKS || now - lastDamagedTick <= ENGAGED_MEMORY_TICKS;
    }

    /**
     * 選んだ立ち位置へ向かい、着いたらその場所の種類に応じて戦う。相手の位置が分からなければfalse
     */
    private boolean handlePositioning(LivingEntity target, boolean canSeeTarget) {
        Location threat = canSeeTarget && target != null ? target.getEyeLocation()
                : lastKnownLocation != null ? lastKnownLocation.clone().add(0, 1.6, 0) : null;
        if (threat == null || threat.getWorld() != scav.getWorld()) return false;
        followingPath = false;
        int now = Bukkit.getCurrentTick();
        String mode = now < withdrawUntilTick ? "WITHDRAW"
                : now < reengageUntilTick ? TacticalPositioning.MODE_ENGAGE : brain.getCurrentModeName();
        TacticalPositioning.Spot spot = positioning.update(threat, mode, brain.getEngagementRange(), squad.getNearbyAllies());
        if (spot == null) return false;

        Location here = scav.getLocation();
        double distance = Math.hypot(here.getX() - spot.stand().getX(), here.getZ() - spot.stand().getZ());
        boolean sameSpot = arrivedSpot != null && arrivedSpot.stand().equals(spot.stand());
        boolean arrived = sameSpot ? distance <= SPOT_LEAVE : distance <= SPOT_ARRIVE;
        arrivedSpot = arrived ? spot : null;
        if (!arrived) {
            // 相手が見えていてまっすぐ行けるなら相手を向いたまま横歩きで、それ以外は経路探索で回り込む
            // (経路探索は進む方向を向くため、撃ち合いの最中に背中を向けてしまう)
            // 撃てない間 (リロード・弾切れ) は撃ち合えないので、近くても立ち位置へ下がる
            boolean closeFight = canSeeTarget && target != null && weaponReadyInTicks <= PEEK_READY_TICKS
                    && here.distanceSquared(target.getLocation()) <= CLOSE_FIGHT_DISTANCE * CLOSE_FIGHT_DISTANCE;
            if (canSeeTarget && distance <= DIRECT_MOVE_MAX && TacticalPositioning.walkableStraight(here, spot.stand())) {
                tactics.moveDirectTo(spot.stand(), 1.2);
            } else if (closeFight) {
                // 近くで見えている相手に背中を向けて回り込むと撃ち返せないので、立ち位置へ向かうのをやめてその場で撃ち合う
                scav.getPathfinder().stopPathfinding();
                tactics.stopMoving();
            } else {
                // 見えていない間の移動は小走りにする (撃たれない場所を移るのに時間をかけると、待っているだけに見える)
                scav.getPathfinder().moveTo(spot.stand(), canSeeTarget ? 1.3 : RELOCATE_SPEED);
                followingPath = true;
            }
            positionNoSightSteps = 0;
            return true;
        }

        updateReengage(spot, now);
        switch (spot.type()) {
            case FIRE -> {
                if (canSeeTarget) {
                    positionNoSightSteps = 0;
                    // 止まって撃つ (小刻みに横へずれると、揺れて見える上に当たらなくなる)。
                    // 撃たれて位置を変える時は、選び直しで別の場所へ移る
                    tactics.stopMoving();
                } else {
                    // 撃てるはずの位置から見えない (相手が動いた): しばらく待って選び直す
                    tactics.stopMoving();
                    preAimAtThreat();
                    if (++positionNoSightSteps >= POSITION_NO_SIGHT_STEPS) {
                        positionNoSightSteps = 0;
                        positioning.invalidate();
                    }
                }
            }
            case COVER_PEEK -> {
                tactics.stopMoving();
                if (!canSeeTarget) {
                    preAimAtThreat();
                    // 撃てない間 (リロード・ボルト操作など) は顔を出さずに待つ
                    if (weaponReadyInTicks <= PEEK_READY_TICKS) tactics.startPeekTo(spot.peek());
                }
            }
            case HIDE -> {
                tactics.stopMoving();
                preAimAtThreat();
            }
        }
        return true;
    }

    /**
     * 遮蔽に着いてから出直すまでを数え、条件がそろったら撃てる立ち位置へ出させる ({@link TacticalPositioning#MODE_ENGAGE})。
     * 撃てる立ち位置にいる間は数え直す
     */
    private void updateReengage(TacticalPositioning.Spot spot, int now) {
        if (spot.type() == TacticalPositioning.SpotType.FIRE) {
            inCoverSinceTick = -1;
            return;
        }
        if (inCoverSinceTick < 0) {
            inCoverSinceTick = now;
            int wait = switch (brainLevel) {
                case LOW -> REENGAGE_WAIT_LOW;
                case MID -> REENGAGE_WAIT_MID;
                case HIGH -> REENGAGE_WAIT_HIGH;
            };
            reengageAfterTick = now + wait + java.util.concurrent.ThreadLocalRandom.current().nextInt(REENGAGE_WAIT_JITTER);
        }
        if (now < reengageAfterTick || now < reengageUntilTick || now < withdrawUntilTick) return;
        double health = scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (weaponReadyInTicks > 0 || lastAmmoRatio < REENGAGE_MIN_AMMO_RATIO || health < REENGAGE_MIN_HEALTH
                || suppression > REENGAGE_MAX_SUPPRESSION || now - lastDamagedTick < REENGAGE_NO_DAMAGE_TICKS) return;
        reengageUntilTick = now + REENGAGE_MIN_TICKS + java.util.concurrent.ThreadLocalRandom.current().nextInt(REENGAGE_JITTER_TICKS);
        inCoverSinceTick = -1;
        positioning.invalidate();
    }

    /**
     * 見えていない相手の居場所の推定を1ステップ進め、一番ありそうな場所を lastKnownLocation にする。
     * 自分の視界で「見ているのに見えない」場所を消していく (追跡中は視野角なし、手放した後は視野角の内側だけ)
     */
    private void updateBelief(LivingEntity target, boolean canSeeTarget) {
        if (lastKnownLocation == null) {
            belief.clear();
            return;
        }
        if (canSeeTarget || lastKnownLocation.getWorld() != scav.getWorld()) return;
        if (!belief.isActive()) belief.reset(lastKnownLocation, null);
        belief.predict(STEP_TICKS);
        Location eye = scav.getEyeLocation();
        belief.observeNotVisible(eye, eye.getDirection(), target != null ? 360.0 : vision.fovDegrees(), vision.maxVisionDistance());
        OpponentBelief.Estimate estimate = belief.estimate();
        if (estimate != null) lastKnownLocation = estimate.location();
    }

    /** 交戦を始めた・続けている時に呼ぶ。救援に向かうのをやめ、周りの味方を呼ぶ */
    private void enterCombat() {
        lastCombatTick = Bukkit.getCurrentTick();
        LivingEntity target = scav.getTarget();
        squad.enterCombat(target != null ? target.getUniqueId() : null);
        assistCallerId = null;
        sharedContact = null;
        squad.callForHelp();
    }

    /** 敵を見ているか撃たれてから間もないか */
    public boolean isInCombat() {
        return Bukkit.getCurrentTick() - lastCombatTick < COMBAT_MEMORY_TICKS;
    }

    public UUID getAssistCallerId() {
        return assistCallerId;
    }

    /** 今向いている水平方向 (救援に来た味方は、この方向を見張る) */
    public Vector getFacing() {
        Vector facing = (currentAimVector != null ? currentAimVector : scav.getEyeLocation().getDirection()).clone().setY(0);
        return facing.lengthSquared() > 1.0E-6 ? facing.normalize() : new Vector(0, 0, 1);
    }

    /**
     * 味方から救援を頼まれた。交戦中や、別の味方の救援中なら断る
     *
     * @return 引き受けた場合true
     */
    public boolean receiveHelpCall(ScavController caller) {
        if (caller == this || isInCombat() || !caller.getScav().isValid()
                || caller.getScav().getWorld() != scav.getWorld() || !caller.isInCombat()) return false;
        UUID callerId = caller.getScav().getUniqueId();
        UUID previousCallerId = assistCallerId;
        int now = Bukkit.getCurrentTick();
        if (assistCallerId != null && !assistCallerId.equals(callerId)) {
            ScavController previous = ScavSpawner.getController(assistCallerId);
            boolean active = previous != null && previous.getScav().isValid() && previous.isInCombat()
                    && previous.getScav().getWorld() == scav.getWorld() && now <= assistUntilTick;
            if (!ScavHelpPolicy.shouldSwitch(active ? previous.helpPriority(scav.getLocation()) : 0,
                    caller.helpPriority(scav.getLocation()), now - assistStartedTick, active)) return false;
        }
        if (!squad.joinHelpEncounter(caller.getSquad())) return false;
        if (!callerId.equals(assistCallerId)) {
            squad.releaseSearch();
            assignedSearchPoint = null;
            assistStartedTick = now;
            sharedContact = null;
            assistSide = squad.supportSide();
            addAlertness(0.2f, "HELP_CALL", ScavSpawner.getRaidSessionId(scav.getUniqueId()));
        }
        ScavContactReport report = caller.visualReport();
        if (report != null && (sharedContact == null || report.observedTick() > sharedContact.observedTick()
                || !report.targetId().equals(sharedContact.targetId()))) {
            if (sharedContact == null || !report.targetId().equals(sharedContact.targetId())) {
                squad.releaseSearch();
                assignedSearchPoint = null;
                nextSharedSearchTick = 0;
            }
            sharedContact = report;
            sharedSearchUntilTick = report.observedTick() + ScavContactReport.MAX_AGE;
        }
        assistCallerId = callerId;
        assistUntilTick = Bukkit.getCurrentTick() + ASSIST_DURATION_TICKS;
        if (!callerId.equals(previousCallerId)) {
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("callerId", callerId.toString());
            details.put("previousCallerId", previousCallerId == null ? null : previousCallerId.toString());
            details.put("priority", caller.helpPriority(scav.getLocation()));
            details.put("observationTick", sharedContact == null ? null : sharedContact.observedTick());
            details.put("contactSource", sharedContact == null ? "NONE" : "VISUAL_REPORT");
            logCoordination("HELP_ASSIGNED", details);
        }
        isAlerted = true;
        return true;
    }

    /** 味方の後ろ・横へ向かい、着いたら味方が向いている方向を見張る。救援中でなければfalse */
    private boolean handleAssist() {
        int now = Bukkit.getCurrentTick();
        ScavController caller = assistCallerId == null ? null : ScavSpawner.getController(assistCallerId);
        if (caller == null || !caller.getScav().isValid() || caller.getScav().getWorld() != scav.getWorld()
                || now > assistUntilTick || !caller.isInCombat()) {
            assistCallerId = null;
            caller = null;
            // Finish a short search using the last actual report, rather than immediately abandoning it.
            if (sharedContact == null || !sharedContact.fresh(now) || now >= sharedSearchUntilTick
                    || sharedContact.position().getWorld() != scav.getWorld()) {
                sharedContact = null;
                assignedSearchPoint = null;
                squad.releaseSearch();
                return false;
            }
        }
        if (sharedContact != null && sharedContact.fresh(now) && sharedContact.position().getWorld() == scav.getWorld()
                && (caller == null || now - sharedContact.observedTick() >= 60)) {
            Location goal = assignedSearchGoal(sharedContact);
            updatePreAim(sharedContact.predicted(now));
            if (goal != null) {
                if (scav.getLocation().distanceSquared(goal) > 2.25) scav.getPathfinder().moveTo(goal, ASSIST_SPEED);
                else scav.getPathfinder().stopPathfinding();
                return true;
            }
            if (caller == null) return false;
        }
        if (caller == null) return false;

        Location callerLoc = caller.getScav().getLocation();
        Vector facing = caller.getFacing();
        if (sharedContact != null && sharedContact.fresh(now) && sharedContact.position().getWorld() == scav.getWorld()) {
            Vector towardObservation = sharedContact.position().toVector().subtract(callerLoc.toVector()).setY(0);
            if (towardObservation.lengthSquared() > 0.01) facing = towardObservation.normalize();
        }
        assistSide = squad.supportSide();
        Vector side = new Vector(-facing.getZ(), 0, facing.getX()).multiply(assistSide * ASSIST_SIDE);
        Location post = callerLoc.clone().subtract(facing.clone().multiply(ASSIST_BEHIND)).add(side);

        if (scav.getLocation().distanceSquared(post) > ASSIST_ARRIVE_DISTANCE * ASSIST_ARRIVE_DISTANCE) {
            // 着く場所が壁の中などで経路が無ければ、味方のところへ向かう
            if (!scav.getPathfinder().moveTo(post, ASSIST_SPEED)) {
                scav.getPathfinder().moveTo(callerLoc, ASSIST_SPEED);
            }
        } else {
            scav.getPathfinder().stopPathfinding();
            updatePreAim(sharedContact != null && sharedContact.fresh(now)
                    ? sharedContact.predicted(now) : callerLoc.clone().add(facing.clone().multiply(ASSIST_WATCH_DISTANCE)));
        }
        return true;
    }

    private ScavContactReport visualReport() {
        int now = Bukkit.getCurrentTick();
        if (lastSeenLocation == null || lastSeenTargetId == null || now - lastSeenTick >= ScavContactReport.MAX_AGE) return null;
        LivingEntity current = scav.getTarget();
        if (current != null && !current.getUniqueId().equals(lastSeenTargetId)) return null;
        return new ScavContactReport(lastSeenTargetId, scav.getUniqueId(), lastSeenLocation, observedVelocity, lastSeenTick);
    }

    private double helpPriority(Location responder) {
        int now = Bukkit.getCurrentTick();
        return ScavHelpPolicy.priority(scav.getHealth() / Math.max(1, scav.getMaxHealth()), suppression,
                now - lastDamagedTick < 40, now - lastTargetVisibleTick < 40, squad.responderCount(),
                responder.getWorld() == scav.getWorld() ? responder.distance(scav.getLocation()) : 1000);
    }

    private Location assignedSearchGoal(ScavContactReport report) {
        int now = Bukkit.getCurrentTick();
        if (assignedSearchPoint != null && now - assignedSearchStartedTick >= 20
                && scav.getLocation().distanceSquared(assignedSearchPoint) <= 6.25
                && canInspectSearchPoint(assignedSearchPoint)) {
            squad.inspected(report, assignedSearchPoint);
            logCoordination("SEARCH_SECTOR_INSPECTED", java.util.Map.of("targetId", report.targetId().toString(),
                    "x", assignedSearchPoint.getX(), "y", assignedSearchPoint.getY(), "z", assignedSearchPoint.getZ()));
            assignedSearchPoint = null;
            nextSharedSearchTick = now;
        }
        if (now < nextSharedSearchTick) return assignedSearchPoint;
        Location point = squad.searchPoint(report);
        nextSharedSearchTick = now + 20; // path probes at most once per second per searching SCAV
        if (point == null || assignedSearchPoint == null || point.distanceSquared(assignedSearchPoint) > 0.01) {
            assignedSearchPoint = point;
            assignedSearchStartedTick = now;
            if (point != null) logCoordination("SEARCH_SECTOR_ASSIGNED", java.util.Map.of(
                    "targetId", report.targetId().toString(), "observationTick", report.observedTick(),
                    "uncertainty", report.uncertainty(now), "x", point.getX(), "y", point.getY(), "z", point.getZ()));
        }
        return assignedSearchPoint;
    }

    private void logCoordination(String event, java.util.Map<String, Object> details) {
        String session = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        if (session != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled())
            plugin.getAiRaidLogger().logEvent(session, scav.getUniqueId(), event, details);
    }

    private boolean canInspectSearchPoint(Location point) {
        Location eye = scav.getEyeLocation();
        Vector direction = point.clone().add(0, 1, 0).toVector().subtract(eye.toVector());
        double distance = direction.length();
        if (distance < 0.1) return true;
        if (Math.toDegrees(eye.getDirection().angle(direction)) > vision.fovDegrees() / 2) return false;
        return eye.getWorld().rayTraceBlocks(eye, direction.normalize(), distance,
                org.bukkit.FluidCollisionMode.NEVER, true) == null;
    }

    /** 味方の銃声を聞いた。戦闘が近くで起きていることだけ分かる (撃っている味方の位置を敵の位置とは扱わない) */
    public void onAllyGunfire() {
        addAlertness(0.1f, "ALLY_GUNFIRE", ScavSpawner.getRaidSessionId(scav.getUniqueId()));
        isAlerted = true;
    }

    public void addSuppression(float amount) {
        float before = suppression;
        suppression = Math.min(1.0f, suppression + amount);
        // 至近弾が続いて頭を上げられなくなった瞬間に叫ぶ
        if (before < UNDER_FIRE_VOICE_SUPPRESSION && suppression >= UNDER_FIRE_VOICE_SUPPRESSION) playScavVoice(ScavVoice.UNDER_FIRE);
    }
    public void onDeath() { squad.releaseSearch(); brain.onDeath(); releaseChunkTicket(); }
    public void terminate() {
        brain.terminate();
        releaseChunkTicket();
        releaseWeapon();
        // 倒されずに消えた時 (レイド終了など) も、弾の割り当てを残さない
        com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply.remove(scav.getUniqueId());
    }

    /**
     * 手に持っている銃に対応する武器を返す。持ち替えたら作り直す。
     * データパック銃 (Toi's Armory) を優先し、そうでなければプラグインの銃定義を使う。
     */
    private ScavWeapon resolveWeapon() {
        ItemStack item = scav.getEquipment() != null ? scav.getEquipment().getItemInMainHand() : null;
        String datapackGunId = DatapackGunCatalog.gunIdOf(item);
        String pluginItemId = (datapackGunId == null && item != null && item.hasItemMeta())
                ? item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        String key = datapackGunId != null ? "datapack:" + datapackGunId : pluginItemId != null ? "plugin:" + pluginItemId : null;
        if (java.util.Objects.equals(key, weaponKey)) return weapon;

        releaseWeapon();
        weaponKey = key;
        if (datapackGunId != null) {
            DatapackGunProfile profile = DatapackGunCatalog.get(datapackGunId);
            if (profile != null) weapon = new DatapackScavWeapon(scav, profile);
        } else if (pluginItemId != null) {
            ItemDefinition def = ItemRegistry.get(pluginItemId);
            if (def != null && def.gunStats != null) weapon = new PluginScavWeapon(scav, def, gunListener);
        }
        return weapon;
    }

    private void releaseWeapon() {
        if (weapon != null) {
            weapon.release();
            weapon = null;
        }
        weaponKey = null;
    }

    private void updateChunkTicket() {
        Chunk newChunk = scav.getLocation().getChunk();
        if (currentChunk == null || !currentChunk.equals(newChunk)) {
            if (currentChunk != null) currentChunk.removePluginChunkTicket(plugin);
            currentChunk = newChunk;
            currentChunk.addPluginChunkTicket(plugin);
        }
    }

    private void releaseChunkTicket() {
        if (currentChunk != null) { currentChunk.removePluginChunkTicket(plugin); currentChunk = null; }
    }

    private void checkAndInteractWithDoors() {
        Location loc = scav.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        tryOpenDoor(loc.getBlock());
        tryOpenDoor(loc.clone().add(0, 1, 0).getBlock());
        for (double d : new double[]{1.0, 1.5}) {
            if (tryOpenDoor(loc.clone().add(dir.clone().multiply(d)).getBlock()) || 
                tryOpenDoor(loc.clone().add(0, 1, 0).add(dir.clone().multiply(d)).getBlock())) break;
        }
    }

    private boolean tryOpenDoor(Block block) {
        if (block.getType().toString().contains("TRAPDOOR")) return false;
        if (block.getBlockData() instanceof Openable openable && !openable.isOpen()) {
            openable.setOpen(true);
            block.setBlockData(openable);
            Sound s = block.getType().toString().contains("IRON") ? Sound.BLOCK_IRON_DOOR_OPEN : 
                     (block.getType().toString().contains("FENCE_GATE") ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_WOODEN_DOOR_OPEN);
            block.getWorld().playSound(block.getLocation(), s, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    /**
     * @param rawDamage 防具で軽減される前のダメージ。分かる場合 (データパック銃) は軽減の割合で効きにくさを判断する。
     *                  データパック銃は元のダメージが小さい銃が多く、絶対値で判断すると防具が無くても「効きにくい」になるため
     */
    public void onBulletHitDealt(LivingEntity victim, double finalDamage, double rawDamage, boolean penetrated, String hitLocation) {
        if (victim == null) return;
        TargetCombatMemory memory = targetMemories.computeIfAbsent(victim.getUniqueId(), id -> new TargetCombatMemory());
        memory.hits++;
        memory.lastUpdateTick = Bukkit.getCurrentTick();

        boolean lowEffective = !penetrated || (Double.isNaN(rawDamage) || rawDamage <= 0
                ? finalDamage < LOW_EFFECTIVE_DAMAGE_THRESHOLD
                : finalDamage < rawDamage * LOW_EFFECTIVE_DAMAGE_RATIO);
        if (lowEffective) {
            memory.lowEffectiveHits++;
        } else if (memory.lowEffectiveHits > 0 && Math.random() < 0.35) {
            // 有効打が続く時は過去の低有効打印象を少しずつ減衰
            memory.lowEffectiveHits--;
        }

        if (memory.hits > 16) {
            memory.hits = 8;
            memory.lowEffectiveHits = Math.max(0, memory.lowEffectiveHits / 2);
        }

        if (brainLevel == ScavBrain.BrainLevel.HIGH) {
            if ("head".equalsIgnoreCase(hitLocation)) {
                memory.recognizedHeadArmorClass = getArmorClassFromSlot(victim, EquipmentSlot.HEAD);
            } else {
                memory.recognizedChestArmorClass = getArmorClassFromSlot(victim, EquipmentSlot.CHEST);
            }
        }
    }

    private int getArmorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    private float getLowEffectiveRatio(UUID targetId) {
        TargetCombatMemory memory = targetMemories.get(targetId);
        if (memory == null || memory.hits <= 0) return 0.0f;
        return (float) memory.lowEffectiveHits / (float) memory.hits;
    }

    private void cleanupTargetMemories() {
        long now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, TargetCombatMemory>> it = targetMemories.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetCombatMemory> e = it.next();
            if (now - e.getValue().lastUpdateTick > TARGET_MEMORY_EXPIRE_TICKS) {
                it.remove();
            }
        }
    }

    private void logTargetTransitionIfNeeded(String raidSessionId, LivingEntity target) {
        if (raidSessionId == null || plugin.getAiRaidLogger() == null || !plugin.getAiRaidLogger().isEnabled()) return;
        UUID currentTargetId = target != null ? target.getUniqueId() : null;

        if (!initializedTargetState) {
            initializedTargetState = true;
            lastLoggedTargetId = currentTargetId;
            if (currentTargetId != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("targetId", currentTargetId.toString());
                payload.put("reason", "INITIAL");
                plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_ACQUIRED", payload);
            }
            return;
        }

        if (Objects.equals(lastLoggedTargetId, currentTargetId)) return;
        if (lastLoggedTargetId == null && currentTargetId != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetId", currentTargetId.toString());
            payload.put("reason", "ACQUIRE");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_ACQUIRED", payload);
        } else if (lastLoggedTargetId != null && currentTargetId == null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("previousTargetId", lastLoggedTargetId.toString());
            payload.put("reason", "LOST");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_LOST", payload);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("fromTargetId", lastLoggedTargetId.toString());
            payload.put("toTargetId", currentTargetId.toString());
            payload.put("reason", "SWITCH");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_SWITCH", payload);
        }
        lastLoggedTargetId = currentTargetId;
    }

    private void logSnapshotIfNeeded(String raidSessionId, LivingEntity target, boolean canSeeTarget, float tacticalAdvice, int[] actions) {
        if (raidSessionId == null || plugin.getAiRaidLogger() == null || !plugin.getAiRaidLogger().isEnabled()) return;
        int interval = plugin.getAiRaidLogger().getSampleIntervalTicks();
        if (interval <= 0 || Bukkit.getCurrentTick() % interval != 0) return;

        int move = actions != null && actions.length > 0 ? actions[0] : 8;
        int shoot = actions != null && actions.length > 1 ? actions[1] : 1;
        float[] neurons = brain.getNeuronStates();
        float aggression = neurons.length > 0 ? neurons[0] : 0.0f;
        float fear = neurons.length > 1 ? neurons[1] : 0.0f;
        float tactical = neurons.length > 2 ? neurons[2] : 0.0f;

        plugin.getAiRaidLogger().logSnapshot(
                raidSessionId,
                scav.getUniqueId(),
                brainLevel.name(),
                scav.getLocation(),
                target != null ? target.getUniqueId() : null,
                canSeeTarget,
                lastKnownLocation,
                suppression,
                searchTicks,
                isSprinting,
                isHoldingAngle,
                alertness,
                1.0f - alertness,
                behaviorState.name(),
                scav.getLocation().distance(homeLocation),
                brain.getCurrentModeName(),
                move,
                shoot,
                aggression,
                fear,
                tactical,
                tacticalAdvice,
                plugin.getAiRaidLogger().isCaptureRaycastEnabled() ? buildRaycastSurfaceSample(target) : null
        );
    }

    private void updateBehaviorState() {
        if (alertness >= ALERTNESS_COMBAT_THRESHOLD) {
            behaviorState = BehaviorState.COMBAT_READY;
        } else if (alertness >= ALERTNESS_RELAXED_THRESHOLD) {
            behaviorState = BehaviorState.SUSPICIOUS;
        } else {
            behaviorState = BehaviorState.RELAXED;
        }
    }

    private void handleIdleOrReturnHome(String raidSessionId) {
        boolean moved = false;

        if (investigateTicks > 0 && lastHeardSoundLocation != null) {
            investigateTicks--;
            double distToSound = scav.getLocation().distance(lastHeardSoundLocation);
            if (distToSound > 1.5) {
                scav.getPathfinder().moveTo(lastHeardSoundLocation, 0.9);
                moved = true;
            }
            if (distToSound < 2.0 || investigateTicks <= 0) {
                lastHeardSoundLocation = null;
                investigateTicks = 0;
            }
        }

        if (!moved && behaviorState == BehaviorState.RELAXED) {
            double homeDist = scav.getLocation().distance(homeLocation);
            if (homeDist > 3.0) {
                scav.getPathfinder().moveTo(homeLocation, 0.75);
                if (!returningHome && raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("homeDistance", homeDist);
                    plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "RETURN_HOME_START", payload);
                }
                returningHome = true;
            } else if (homeDist <= 1.5) {
                scav.getPathfinder().stopPathfinding();
                if (returningHome && raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("homeDistance", homeDist);
                    plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "RETURN_HOME_DONE", payload);
                }
                returningHome = false;
            }
        } else {
            returningHome = false;
        }

        // 警戒していない時は、たまに独り言を言う (近くのプレイヤーにだけ聞こえる距離なので、誰もいなければ言わない)
        if (behaviorState == BehaviorState.RELAXED && Math.random() < IDLE_VOICE_CHANCE_PER_STEP
                && !scav.getLocation().getNearbyPlayers(IDLE_VOICE_PLAYER_RANGE).isEmpty()) {
            playScavVoice(ScavVoice.IDLE);
        }
    }

    private void decayAlertness() {
        float decay;
        if (brainLevel == ScavBrain.BrainLevel.LOW) {
            decay = 0.0025f;
        } else {
            decay = 0.0015f;
        }
        if (lastKnownLocation != null) {
            decay *= 0.5f;
        }
        alertness = clamp01(alertness - decay);
    }

    private void addAlertness(float amount, String reason, String raidSessionId) {
        float before = alertness;
        alertness = clamp01(alertness + amount);
        if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
            if (Math.abs(alertness - before) > 0.0001f) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("reason", reason);
                payload.put("before", before);
                payload.put("after", alertness);
                payload.put("delta", alertness - before);
                plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "ALERTNESS_CHANGE", payload);
            }
        }
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private List<Map<String, Object>> buildRaycastSurfaceSample(LivingEntity target) {
        ScavVision.LosSnapshot los = vision.getLastLosSnapshot();
        if (los == null || los.rays == null || los.rays.isEmpty()) return null;

        List<Map<String, Object>> out = new ArrayList<>();

        Map<String, Object> primary = new HashMap<>();
        primary.put("kind", "los_primary");
        primary.put("targetId", los.targetId != null ? los.targetId.toString() : null);
        primary.put("visible", los.visible);
        if (los.primaryRay != null) {
            primary.put("band", los.primaryRay.band);
            primary.put("lateral", los.primaryRay.lateral);
            primary.put("distance", los.primaryRay.distance);
            primary.put("blockType", los.primaryRay.blockType);
            primary.put("face", los.primaryRay.face);
            primary.put("x", los.primaryRay.hitX);
            primary.put("y", los.primaryRay.hitY);
            primary.put("z", los.primaryRay.hitZ);
        }
        out.add(primary);

        // 面圧縮: バンド(upper/middle/lower)ごとに可視率/距離を集約
        Map<String, BandAgg> bands = new HashMap<>();
        Map<String, SurfaceAgg> surfaces = new HashMap<>();

        for (ScavVision.LosRay ray : los.rays) {
            String bandKey = ray.band != null ? ray.band : "unknown";
            BandAgg b = bands.computeIfAbsent(bandKey, k -> new BandAgg());
            b.count++;
            if (ray.clear) b.clearCount++;
            b.minDistance = Math.min(b.minDistance, ray.distance);
            b.distanceSum += ray.distance;

            if (!ray.clear && ray.blockType != null) {
                String face = ray.face != null ? ray.face : "UNKNOWN";
                String surfaceKey = bandKey + "|" + ray.blockType + "|" + face;
                SurfaceAgg s = surfaces.computeIfAbsent(surfaceKey, k -> new SurfaceAgg(bandKey, ray.blockType, face));
                s.count++;
                s.minDistance = Math.min(s.minDistance, ray.distance);
                s.distanceSum += ray.distance;
            }
        }

        List<Map<String, Object>> bandList = new ArrayList<>();
        for (Map.Entry<String, BandAgg> e : bands.entrySet()) {
            BandAgg b = e.getValue();
            Map<String, Object> rec = new HashMap<>();
            rec.put("band", e.getKey());
            rec.put("count", b.count);
            rec.put("clearCount", b.clearCount);
            rec.put("visibilityRatio", b.count > 0 ? (double) b.clearCount / (double) b.count : 0.0);
            rec.put("minDistance", b.minDistance == Double.POSITIVE_INFINITY ? null : b.minDistance);
            rec.put("avgDistance", b.count > 0 ? b.distanceSum / (double) b.count : null);
            bandList.add(rec);
        }

        List<Map<String, Object>> surfaceList = new ArrayList<>();
        for (SurfaceAgg s : surfaces.values()) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("band", s.band);
            rec.put("blockType", s.blockType);
            rec.put("face", s.face);
            rec.put("count", s.count);
            rec.put("minDistance", s.minDistance == Double.POSITIVE_INFINITY ? null : s.minDistance);
            rec.put("avgDistance", s.count > 0 ? s.distanceSum / (double) s.count : null);
            surfaceList.add(rec);
        }

        Map<String, Object> surfaceSummary = new HashMap<>();
        surfaceSummary.put("kind", "depth_bins");
        surfaceSummary.put("bands", bandList);
        surfaceSummary.put("surfaces", surfaceList);
        surfaceSummary.put("rayCount", los.rays.size());
        out.add(surfaceSummary);

        return out;
    }

    private static class BandAgg {
        int count = 0;
        int clearCount = 0;
        double minDistance = Double.POSITIVE_INFINITY;
        double distanceSum = 0.0;
    }

    private static class SurfaceAgg {
        final String band;
        final String blockType;
        final String face;
        int count = 0;
        double minDistance = Double.POSITIVE_INFINITY;
        double distanceSum = 0.0;

        SurfaceAgg(String band, String blockType, String face) {
            this.band = band;
            this.blockType = blockType;
            this.face = face;
        }
    }
}
