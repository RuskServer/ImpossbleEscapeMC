package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.util.CombatEvaluator;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import java.util.Random;

public class ScavBrain {
    public enum BrainLevel {
        LOW,
        MID,
        HIGH
    }

    private final Mob entity;
    private final Random random;
    private final BrainLevel brainLevel;

    // Internal States for AI simulation (0.0 to 1.0)
    private float aggression;
    private float fear;
    private float tactical;

    // --- Action Types ---
    // 0: Approach, 1: Maintain, 2: Retreat, 3: Strafe L, 4: Strafe R, 5: Jump, 6: Peek, 7: Jump Peek, 8: HOLD
    private int[] currentActions; // [Movement, Shooting]
    private int decisionTimer = 0;

    // --- Utility AI: Tactical Modes ---
    public enum TacticalMode {
        WITHDRAW, // 離脱
        PUSH,     // 詰める
        PEEK,     // ピーク
        FLANK,    // 裏取り/横移動
        HOLD      // 立ち止まる
    }
    private TacticalMode currentMode = TacticalMode.HOLD;
    private int modeInertia = 0; // モード維持タイマー

    // --- Action History (Circular Buffer & Bitfield) ---
    private static final int HISTORY_SIZE = 16;
    private final int[] moveHistory = new int[HISTORY_SIZE];
    private int historyIdx = 0;
    private long moveBitfield = 0;

    // --- Situational Awareness ---
    private int presenceTicks = 0; // ターゲットを見失ってからの時間
    private int sightDebounceTicks = 0; // 視認変化のデバウンス用カウンター
    private static final int DEBOUNCE_THRESHOLD_SEE = 2;  // 発見判定までの猶予 (0.1s)
    private static final int DEBOUNCE_THRESHOLD_LOST = 5; // 見失い判定までの猶予 (0.25s)
    private boolean debouncedCanSee = false; // デバウンス済みの視認状態

    // --- State Tracking for Interrupts ---
    private float lastSuppression = 0.0f;
    private double lastHealthPercent = 1.0;
    private boolean lastCanSee = false;

    public ScavBrain(Mob entity) {
        this(entity, BrainLevel.MID);
    }

    public ScavBrain(Mob entity, BrainLevel brainLevel) {
        this.entity = entity;
        this.random = new Random();
        this.brainLevel = brainLevel;
        this.currentActions = new int[] { 8, 1 }; 

        java.util.Arrays.fill(moveHistory, 8);

        this.aggression = 0.2f + random.nextFloat() * 0.3f;
        this.fear = 0.1f;
        this.tactical = 0.3f + random.nextFloat() * 0.2f;

        if (brainLevel == BrainLevel.LOW) {
            this.aggression = 0.5f + random.nextFloat() * 0.3f;
            this.tactical = 0.15f + random.nextFloat() * 0.15f;
        } else if (brainLevel == BrainLevel.HIGH) {
            this.aggression = 0.2f + random.nextFloat() * 0.2f;
            this.tactical = 0.5f + random.nextFloat() * 0.25f;
        }
    }

    public int[] decide(LivingEntity target, Location lastKnownLocation, GunStats stats, float suppression, float tacticalAdvice, boolean isSprinting, float alertness) {
        boolean canSeeNow = target != null;

        // --- 視認状態のデバウンス処理 ---
        if (canSeeNow != debouncedCanSee) {
            sightDebounceTicks++;
            int threshold = canSeeNow ? DEBOUNCE_THRESHOLD_SEE : DEBOUNCE_THRESHOLD_LOST;
            if (sightDebounceTicks >= threshold) {
                debouncedCanSee = canSeeNow;
                sightDebounceTicks = 0;
            }
        } else {
            sightDebounceTicks = 0;
        }

        boolean canSee = debouncedCanSee;
        double healthPercent = entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        
        // --- 距離の計算 (予測モデル) ---
        double dist;
        if (canSee && target != null) {
            dist = entity.getLocation().distance(target.getLocation());
        } else if (lastKnownLocation != null) {
            // 見失っていても、最後にいた場所への距離を使用する
            dist = entity.getLocation().distance(lastKnownLocation);
        } else {
            dist = 100.0; // 完全に情報がない場合のみフォールバック
        }
        
        // ターゲット存在確信度の更新
        if (canSee) presenceTicks = 0;
        else presenceTicks++;

        // --- 1. Update Internal States ---
        updateInternalStates(dist, stats, suppression, tacticalAdvice, canSee, healthPercent);

        // --- 2. Check for Interrupts ---
        String interruptReason = getInterruptReason(canSee, suppression, healthPercent, stats);
        boolean interrupted = interruptReason != null;

        // --- 3. Utility-Based Decision Logic ---
        if (decisionTimer <= 0 || interrupted || modeInertia <= 0) {
            String trigger = interrupted ? interruptReason : (decisionTimer <= 0 ? "TIMER" : "INERTIA");
            
            // A. 戦況の評価
            float pressure = (suppression * 0.4f) + (float)((1.0 - healthPercent) * 0.6f);
            
            ItemStack gun = entity.getEquipment().getItemInMainHand();
            float ammo = 1.0f;
            if (gun != null && gun.hasItemMeta() && stats != null) {
                int currentAmmo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
                ammo = (float) currentAmmo / stats.magSize;
            }

            float targetHealth = (target != null) ? (float)(target.getHealth() / target.getAttribute(Attribute.MAX_HEALTH).getValue()) : 1.0f;
            float advantage = (1.0f - targetHealth) * 0.4f + (ammo * 0.6f);
            float presence = Math.max(0, 1.0f - (presenceTicks / 600.0f));

            // B. 各戦術モードの Utility スコア算出
            float withdrawScore = (pressure * 0.8f) + ((1.0f - advantage) * 0.4f) + (fear * 0.6f);
            
            // PUSHスコアの強化: 10m以内かつ視認中(遮蔽が少ない)なら強烈なボーナス
            float pushScore = (advantage * 0.6f) + (aggression * 0.5f) + (float)(Math.max(0, 1.0 - dist/40.0) * 0.3);
            if (dist < 10.0 && canSee) {
                pushScore += 0.5f; // 強制的な突撃衝動
                if (healthPercent > 0.4) aggression += 0.1f; // 余裕があればさらに強気に
            }
            
            float peekScore = (presence * 0.6f) + (tactical * 0.4f) + (suppression < 0.2f ? 0.3f : 0.0f);
            float flankScore = (tactical * 0.7f) + ((1.0f - pressure) * 0.4f) + (canSee ? 0.2f : 0.0f);
            float holdScore = (tactical * 0.5f) + ((1.0f - aggression) * 0.3f) + (presence > 0.8f ? 0.4f : 0.0f);

            // 警戒度が低い時は押し込みを抑え、様子見に寄せる
            pushScore *= (0.7f + (0.6f * alertness));
            peekScore *= (0.8f + (0.4f * alertness));
            holdScore += (1.0f - alertness) * 0.25f;

            if (brainLevel == BrainLevel.LOW) {
                pushScore += 0.35f;
                holdScore -= 0.25f;
                flankScore -= 0.15f;
                peekScore -= 0.1f;
            } else if (brainLevel == BrainLevel.HIGH) {
                holdScore += 0.15f;
                flankScore += 0.12f;
                pushScore -= 0.15f;
            }

            // C. 最高スコアのモードを選択
            TacticalMode bestMode = TacticalMode.HOLD;
            float maxScore = holdScore;
            
            if (withdrawScore > maxScore) { maxScore = withdrawScore; bestMode = TacticalMode.WITHDRAW; }
            if (pushScore > maxScore) { maxScore = pushScore; bestMode = TacticalMode.PUSH; }
            if (peekScore > maxScore) { maxScore = peekScore; bestMode = TacticalMode.PEEK; }
            if (flankScore > maxScore) { maxScore = flankScore; bestMode = TacticalMode.FLANK; }

            // 戦術的慣性: 以前のモードと大きな差がなければ維持
            // ただし、緊急の割り込み(interrupted)がある場合は、慣性を無視して即座に遷移させる
            if (!interrupted && currentMode != bestMode && modeInertia > 0) {
                // 維持
            } else {
                currentMode = bestMode;
                modeInertia = 10 + random.nextInt(20); // 0.5s - 1.5s 維持
            }

            // D. モードを具体的なアクションに変換
            int moveAction = determineMoveActionByMode(currentMode, suppression, canSee, tacticalAdvice, dist);
            int shootAction = determineShootAction(canSee, suppression, isSprinting);
            
            // --- AI Analysis Logging ---
            // Format: [AI_LOG] UUID | Trigger | Mode | States(A,F,T) | Situational(Pr,Ad,Pres,Dist,SelfHP,TargetHP,Ammo) | Action(M,S)
            org.bukkit.Bukkit.getLogger().info(String.format(
                "[AI_LOG] %s | %s | %s | [%.2f,%.2f,%.2f] | [%.2f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f] | [%d,%d]",
                entity.getUniqueId().toString().substring(0, 8),
                trigger,
                currentMode.name(),
                aggression, fear, tactical,
                pressure, advantage, presence, dist, healthPercent, targetHealth, ammo,
                moveAction, shootAction
            ));
            
            this.currentActions = new int[] { moveAction, shootAction };
            recordHistory(moveAction);
            this.decisionTimer = 4 + random.nextInt(4);
        } else {
            decisionTimer--;
            modeInertia--;
        }

        this.lastCanSee = canSee;
        this.lastSuppression = suppression;
        this.lastHealthPercent = healthPercent;

        return currentActions;
    }

    private int determineMoveActionByMode(TacticalMode mode, float suppression, boolean canSee, float tacticalAdvice, double dist) {
        float r = random.nextFloat();
        
        switch (mode) {
            case WITHDRAW:
                // 強い制圧下では、背中を見せて逃げるだけでなく、蛇行(Strafe)して弾を避ける
                if (suppression > 0.5f && r < 0.6f) return random.nextBoolean() ? 3 : 4;
                return 2; // Retreat

            case PUSH:
                if (canSee) {
                    // 10m以内の至近距離では、迷わず最短距離で突っ込む (混乱を誘う)
                    if (dist < 10.0) {
                        // 至近距離突撃中は、たまにジャンプを混ぜてヘッドショットを避ける
                        if (r < 0.3f) return 5; // Jump
                        return 0; // Approach
                    }
                    // 少し距離がある場合はレレレを混ぜながら
                    if (dist < 18.0 && r < 0.6f) return random.nextBoolean() ? 3 : 4;
                    return 0; // Approach
                }
                return 0; // Approach

            case PEEK:
                // プリエイム/ピーク機動。ターゲットが見えていない時が本番
                if (!canSee && (tacticalAdvice > 0.5f || lastCanSee)) {
                    return (aggression > 0.4f && random.nextBoolean()) ? 7 : 6;
                }
                // 位置調整のための微細な横移動
                return random.nextBoolean() ? 3 : 4;

            case FLANK:
                // サークルストレイフのロジック: 横移動を基本に、距離の誤差を修正
                if (dist > 25.0) return 0; // 遠すぎるなら近づきながら
                if (dist < 12.0) return 2; // 近すぎるなら離れながら
                return random.nextBoolean() ? 3 : 4; // 適正距離なら純粋に横移動

            case HOLD:
            default:
                if (canSee) return 8; // 視認中は HOLD でエイムに集中
                // 見失った直後の 2秒間(40ticks) は角待ちを維持
                if (lastCanSee && presenceTicks < 40) return 8; 
                return 1; // それ以外は Maintain
        }
    }

    private void recordHistory(int moveAction) {
        moveHistory[historyIdx] = moveAction;
        historyIdx = (historyIdx + 1) % HISTORY_SIZE;
        // ビットフィールドの更新 (簡易的な発生履歴)
        moveBitfield |= (1L << moveAction);
        
        // 履歴が一周するごとにビットフィールドをリセット（または減衰）して「最近の傾向」を維持
        if (historyIdx == 0) {
            moveBitfield = 0;
            for (int act : moveHistory) moveBitfield |= (1L << act);
        }
    }

    private boolean isRepeatingAction(int action, int count) {
        int found = 0;
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (moveHistory[i] == action) {
                found++;
                if (found >= count) return true;
            }
        }
        return false;
    }

    private int getActionCount(int action) {
        int count = 0;
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (moveHistory[i] == action) count++;
        }
        return count;
    }

    private String getInterruptReason(boolean canSee, float suppression, double healthPercent, GunStats stats) {
        // A. Sudden sight change (Debounced state changed)
        if (canSee != lastCanSee) return "SIGHT_CHANGE";

        // B. Heavy damage or sudden suppression spike
        if (healthPercent < lastHealthPercent - 0.1) return "DAMAGE"; 
        if (suppression > lastSuppression + 0.3f) return "SUPPRESSION_SPIKE";

        // C. Panic state (Fear spikes)
        if (fear > 0.8f && lastSuppression < 0.5f) return "PANIC";

        // D. Out of ammo while trying to shoot
        if (currentActions[1] == 0) { 
            ItemStack gun = entity.getEquipment().getItemInMainHand();
            if (gun != null && gun.hasItemMeta()) {
                int ammo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
                if (ammo <= 0) return "OUT_OF_AMMO";
            }
        }

        return null;
    }

    private boolean shouldInterrupt(boolean canSee, float suppression, double healthPercent, GunStats stats) {
        return getInterruptReason(canSee, suppression, healthPercent, stats) != null;
    }

    private void updateInternalStates(double distance, GunStats stats, float suppression, float tacticalAdvice,
            boolean canSee, double healthPercent) {
        
        ItemStack gun = entity.getEquipment().getItemInMainHand();
        double ammoPercent = 1.0;
        if (gun != null && gun.hasItemMeta() && stats != null) {
            int currentAmmo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            ammoPercent = (double) currentAmmo / stats.magSize;
        }

        // Update Aggression/Fear using Evaluator
        float spikeAgg = CombatEvaluator.calculateAggressionSpike(distance, suppression, healthPercent, canSee);
        float spikeFear = CombatEvaluator.calculateFearSpike(suppression, healthPercent, ammoPercent);
        
        aggression += spikeAgg;
        fear += spikeFear;

        // --- 感情の相互作用 (Inter-emotional Bleed) ---
        // 1. 恐怖は攻撃性を抑制する (Aggression influenced by Fear)
        aggression *= (1.0f - (fear * 0.4f)); 

        // 2. 攻撃性が高いと恐怖を一時的に上書きする (Aggression masks Fear / Adrenaline)
        fear *= (1.0f - (aggression * 0.2f));

        // 3. 恐怖は戦術的本能を刺激する (Fear increases Tactical focus for survival)
        if (fear > 0.5f) tactical += fear * 0.05f;

        // 4. 戦術的状況把握は恐怖を抑える (Tactical composure reduces Fear)
        // 上限を設け、冷静であっても一度の恐怖を削れる量を制限する
        float tacticalSuppression = Math.min(tactical * 0.15f, 0.08f);
        fear *= (1.0f - tacticalSuppression);

        if (tacticalAdvice > 0.5f) {
            tactical += 0.1f;
        }
        // 冷静な時 (fear < 0.3) のみ、視認ロスト時に戦術的分析が進む
        if (!canSee && fear < 0.3f) {
            tactical += 0.02f;
        }

        // Decay
        aggression = clamp(aggression * 0.96f - 0.005f);
        fear = clamp(fear * 0.94f - 0.01f);
        tactical = clamp(tactical * 0.95f - 0.01f);
    }

    private int determineShootAction(boolean canSee, float suppression, boolean isSprinting) {
        if (isSprinting) return 1; // スプリント中は射撃不可

        if (canSee) {
            // Try to shoot if seeing target
            if (fear > 0.8f && suppression > 0.8f) {
                // Too suppressed/scared to shoot back efficiently, might panic fire
                return random.nextFloat() < 0.3f ? 0 : 1;
            }
            return 0; // Standard fire
        }
        // Jump shot logic is handled partially in controller. Brain decides 'yes'
        // occasionally if tactical is high
        if (tactical > 0.6f && random.nextFloat() < 0.2f) {
            return 0; // Attempt predictive/jump fire
        }

        return 1; // Don't shoot
    }

    private float clamp(float val) {
        return Math.max(0.0f, Math.min(1.0f, val));
    }

    // Compatibility methods so Controller doesn't break entirely yet, though some
    // need adjustment
    public void updateConditions(boolean lowHealth, boolean lowAmmo, boolean suppressed, boolean tacticalFlag) {
        // Conditions are already handled in updateInternalStates based on parameters,
        // but can add immediate spikes here if needed.
        if (lowHealth)
            fear = clamp(fear + 0.2f);
        if (lowAmmo)
            fear = clamp(fear + 0.1f);
        if (suppressed)
            fear = clamp(fear + 0.1f);
        if (tacticalFlag)
            tactical = clamp(tactical + 0.2f);
    }

    public float[] getNeuronStates() {
        return new float[] { aggression, fear, tactical, 0.0f };
    }

    public float getAdrenaline() {
        return aggression;
    }

    public float getFrustration() {
        return fear;
    }

    public int[] getLastActions() {
        return currentActions != null ? currentActions : new int[] { 0, 1 };
    }

    public String getCurrentModeName() {
        return currentMode != null ? currentMode.name() : TacticalMode.HOLD.name();
    }

    public BrainLevel getBrainLevel() {
        return brainLevel;
    }

    // Removed ML feedback loops
    public void reward(float amount) {
    }

    public void onDeath() {
    }

    public void terminate() {
    }
}
