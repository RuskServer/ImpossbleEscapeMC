package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackScavWeapon;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.PluginScavWeapon;
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
    private ScavWeapon weapon;
    private String weaponKey;

    private Chunk currentChunk = null;
    private Location lastKnownLocation = null;
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
    /** 視認中に味方へ位置を伝える間隔 (tick) */
    private static final int SHARE_INTERVAL_TICKS = 10;
    private int lastShareTick = Integer.MIN_VALUE / 2;
    private static final double LOW_EFFECTIVE_DAMAGE_THRESHOLD = 4.0;
    /** 壁越しの足音が聞こえる距離 */
    private static final double FOOTSTEP_MUFFLED_RANGE = 12.0;
    /** 足音から推定する位置の誤差の上限 (ブロック) */
    private static final double FOOTSTEP_MAX_LOCATION_ERROR = 6.0;
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
    private UUID intelOriginScavId;
    private int intelRelayDepth = 0;
    private boolean intelFromShared = false;

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
        this.currentAimVector = scav.getEyeLocation().getDirection();
        this.homeLocation = scav.getLocation().clone();
        this.alertness = (brainLevel == ScavBrain.BrainLevel.LOW) ? 0.15f : 0.25f;
        this.intelOriginScavId = scav.getUniqueId();
        updateChunkTicket();
    }

    public Mob getScav() { return scav; }
    public ScavSquad getSquad() { return squad; }
    public ScavBrain getBrain() { return brain; }
    public ScavBrain.BrainLevel getBrainLevel() { return brainLevel; }
    public void setLastKnownLocation(Location loc) { this.lastKnownLocation = loc; }
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
                // 味方から聞いた情報で動いていても、自分の目で見つけたら自分が発信元になる
                markDirectIntelSource();
                playScavVoice("minecraft:scav1", 1.0f, 1.0f);
                squad.shareTargetWithAllies(target.getLocation());
            }
        }

        if (target != null) {
            canSeeTarget = vision.checkTrackingVision(target);
            int nowTick = Bukkit.getCurrentTick();
            boolean freshSighting = !target.getUniqueId().equals(lastVisibleTargetId)
                    || nowTick - lastTargetVisibleTick > REACQUIRE_TICKS;
            if (canSeeTarget && !sawTargetLastStep && freshSighting) {
                // 見つけた直後は反応時間をおいてから撃ち始める。
                // 撃ち合い中に遮蔽物の端で見え隠れしただけの時は、反応し直さない
                fireHoldUntil = Math.max(fireHoldUntil, System.currentTimeMillis() + rollReactionDelayMs());
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

                if (Bukkit.getCurrentTick() - lastShareTick >= SHARE_INTERVAL_TICKS) {
                    lastShareTick = Bukkit.getCurrentTick();
                    if (Math.random() < 0.2) playScavVoice("minecraft:scav2", 1.0f, 1.0f);
                    squad.shareTargetWithAllies(lastKnownLocation);
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
                        updatePreAim(lastKnownLocation);
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

        updateBehaviorState();
        if (target == null && lastKnownLocation == null) {
            handleIdleOrReturnHome(raidSessionId);
        }

        logTargetTransitionIfNeeded(raidSessionId, target);

        // 装備チェック
        ScavWeapon weapon = resolveWeapon();
        if (weapon == null) {
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, 0.0f, new int[] {8, 1});
            return;
        }

        if (target != null || lastKnownLocation != null) weapon.prepare();

        boolean needsReload = weapon.needsReload();
        double healthPercent = scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        // 3. スイッチング
        if (squad.getMyRole() == ScavSquad.SquadRole.POINTMAN && (suppression > 0.8f || needsReload || healthPercent < 0.4)) {
            // 前衛を味方に任せたら、すぐに物陰へ下がる
            if (squad.requestRoleSwitch()) tactics.setCoverSearchCooldown(0);
        }

        // 4. カバー検索
        if (target != null && tactics.getCoverSearchCooldown() <= 0) {
            // 撃ってくる相手が見えている時は、制圧されていても物陰へ走らずに撃ち返す (移動中は撃たないため)
            if ((suppression > 0.6f && !canSeeTarget) || healthPercent < 0.4 || needsReload) {
                Location cover = tactics.findCover(target);
                tactics.setTacticalCoverLoc(cover);
                tactics.setCoverSearchCooldown(60);
                if (cover != null) tactics.setCoverStayTicks(100);
                coverBestDistance = Double.MAX_VALUE;
                coverStuckSteps = 0;
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
                    weapon, suppression, isSprinting, lastMobShotTime, t -> lastMobShotTime = t);
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

        if (moveAction == 8) {
            isHoldingAngle = true;
            if (lastKnownLocation != null) updatePreAim(lastKnownLocation);
            scav.getPathfinder().stopPathfinding();
        } else if (tactics.getTacticalCoverLoc() != null && tactics.getCoverStayTicks() > 0) {
            isHoldingAngle = false;
            double dist = scav.getLocation().distance(tactics.getTacticalCoverLoc());
            if (dist > 1.0) scav.getPathfinder().moveTo(tactics.getTacticalCoverLoc(), isSprinting ? 1.5 : 1.0);
            else if (!needsReload && canSeeTarget) {
                applyAimToEntity();
            } else if (!needsReload && lastKnownLocation != null && (moveAction == 6 || moveAction == 7)) {
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
            boolean peek = moveAction == 6 || moveAction == 7;
            if (!peek || !tactics.startPeek(lastKnownLocation, isSprinting, moveAction == 7)) handleSearching();
        }

        checkAndInteractWithDoors();

        // 射撃
        if (actions[1] == 0) {
            long now = System.currentTimeMillis();
            long interval = (long) (60000.0 / weapon.rpm());
            if (canSeeTarget) {
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
            } else if (isPreAiming && Math.random() < 0.05) {
                applyAimToEntity();
                if (now >= fireHoldUntil && now - lastMobShotTime >= interval) {
                    if (weapon.fire(0.3, false)) {
                        lastMobShotTime = now;
                        afterTriggerPull(false, now);
                    }
                }
            } else if (target != null) {
                tactics.handleJumpShot(target);
            }
        }

        logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, actions);
    }

    /** ランクごとの基本の弾のばらつき (BulletTaskの拡散量と同じ尺度)。高ランクほど正確 */
    private double baseSpread() {
        return switch (brainLevel) {
            case LOW -> 0.10;
            case MID -> 0.07;
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
        pursuitSteps = PURSUIT_STEPS;
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
                updatePreAim(lastKnownLocation);
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
        tactics.handleSearching(lastKnownLocation, searchTicks, isSprinting, this::updatePreAim);
        double dist = scav.getLocation().distance(lastKnownLocation);
        if (dist <= 2.5) {
            cornerCheckTicks++;
            if (cornerCheckTicks > 60) {
                lastKnownLocation = null;
                tactics.resetSlicing();
                cornerCheckTicks = 0;
                isAlerted = false;
                clearSharedIntel();
            }
        }
        searchTicks++;
        if (searchTicks > 600) {
            lastKnownLocation = null;
            isAlerted = false;
            clearSharedIntel();
        }
    }

    private void updateHumanAim(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        if (currentAimVector == null) currentAimVector = idealDir.clone();
        double lerp = 0.7 - (suppression * 0.2);
        currentAimVector = currentAimVector.clone().add(idealDir.clone().subtract(currentAimVector).multiply(lerp)).normalize();
        aimErrorYaw = (aimErrorYaw + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
        aimErrorPitch = (aimErrorPitch + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
    }

    private void updatePreAim(Location loc) {
        Vector ideal = loc.clone().add(0, 1.5, 0).toVector().subtract(scav.getEyeLocation().toVector()).normalize();
        if (currentAimVector == null) currentAimVector = ideal.clone();
        currentAimVector = currentAimVector.clone().add(ideal.clone().subtract(currentAimVector).multiply(0.3)).normalize();
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
        if (scav.getTarget() != null && scav.hasLineOfSight(scav.getTarget()) && sound.kind != SoundContact.Kind.GUNSHOT) {
            return;
        }

        Location source = sound.sourceLocation.clone();
        if (source.getWorld() != scav.getWorld()) return;
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        double dist = scav.getLocation().distance(source);

        // 足音は壁越しだとこもって遠くまで届かない。聞こえても、位置はおおまかにしか分からない
        if (sound.kind == SoundContact.Kind.FOOTSTEP) {
            boolean muffled = !hasClearSoundPath(source);
            if (muffled && dist > FOOTSTEP_MUFFLED_RANGE) return;
            double error = Math.min(FOOTSTEP_MAX_LOCATION_ERROR, dist * (muffled ? 0.2 : 0.08));
            source.add((Math.random() - 0.5) * 2.0 * error, 0, (Math.random() - 0.5) * 2.0 * error);
        }

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
            lastKnownLocation = inferred;
        }

        Vector toSource = source.toVector().subtract(scav.getEyeLocation().toVector());
        if (toSource.lengthSquared() > 0) {
            scav.setRotation(scav.getLocation().setDirection(toSource.normalize()).getYaw(), 0);
        }
        playScavVoice("minecraft:scav1", 1.0f, 1.0f); // 索敵ボイスに変更

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

    public void playScavVoice(String sound, float volume, float pitch) {
        int now = Bukkit.getCurrentTick();
        if (now < voiceAvailableTick) return;

        // 周囲の味方が最近喋ったかチェック
        for (ScavController ally : squad.getNearbyAllies()) {
            if (now - ally.lastVoiceTick < ALLY_VOICE_GAP_TICKS) return; // 誰かが2秒以内に喋り出していたらキャンセル
        }

        scav.getWorld().playSound(scav.getLocation(), sound, volume, pitch);
        lastVoiceTick = now;
        voiceAvailableTick = now + VOICE_LINE_COOLDOWN_TICKS + (int)(Math.random() * 40); // 5〜7秒のクールダウン
    }

    public void onKill(LivingEntity victim) {
        playScavVoice("minecraft:scav4", 1.0f, 1.0f);
    }

    public void onDamage(Entity attacker) {
        attacker = DatapackGunnerManager.resolveShooter(attacker);
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        addAlertness(0.35f, "TOOK_DAMAGE", raidSessionId);
        suppression = Math.min(1.0f, suppression + 0.3f);
        lastDamagedTick = Bukkit.getCurrentTick();
        CombatHeatmapManager.record(scav.getLocation(), CombatHeatmapManager.TraceType.DANGER, 1.0f);
        if (scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() < 0.5) {
            playScavVoice("minecraft:scav3", 1.0f, 1.0f);
        }
        if (attacker instanceof LivingEntity living) {
            scav.teleport(scav.getLocation().setDirection(living.getLocation().toVector().subtract(scav.getLocation().toVector()).normalize()));
            if (scav.getTarget() == null) {
                scav.setTarget(living);
                lastKnownLocation = living.getLocation();
                markDirectIntelSource();
                squad.shareTargetWithAllies(lastKnownLocation);
            }
        }
    }

    public UUID getIntelOriginScavId() {
        return intelOriginScavId != null ? intelOriginScavId : scav.getUniqueId();
    }

    public int getIntelRelayDepth() {
        return intelRelayDepth;
    }

    public void receiveSharedTarget(Location loc, UUID originScavId, int relayDepth) {
        if (loc == null) return;

        // 同じ発信元の情報でも、今持っているものより遠回りに伝わってきたもの (中継が多い) は使わない
        if (intelFromShared && Objects.equals(this.intelOriginScavId, originScavId) && relayDepth > this.intelRelayDepth) {
            if (lastKnownLocation == null) {
                lastKnownLocation = loc.clone();
            }
            isAlerted = true;
            return;
        }

        lastKnownLocation = loc.clone();
        isAlerted = true;
        intelFromShared = true;
        intelOriginScavId = (originScavId != null) ? originScavId : scav.getUniqueId();
        intelRelayDepth = Math.max(0, relayDepth);
        // 聞いた位置をさらに近くの味方へ伝える (中継数の上限は ScavSquad が見る)
        squad.shareTargetWithAllies(lastKnownLocation);
    }

    private void markDirectIntelSource() {
        intelOriginScavId = scav.getUniqueId();
        intelRelayDepth = 0;
        intelFromShared = false;
    }

    private void clearSharedIntel() {
        intelOriginScavId = scav.getUniqueId();
        intelRelayDepth = 0;
        intelFromShared = false;
    }

    public void addSuppression(float amount) { this.suppression = Math.min(1.0f, this.suppression + amount); }
    public void onDeath() { brain.onDeath(); releaseChunkTicket(); }
    public void terminate() { brain.terminate(); releaseChunkTicket(); releaseWeapon(); }

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
