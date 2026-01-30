package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SCAVの戦闘ロジック
 * 状況（HP、弾数、距離、遮蔽）を数値化し、Rust脳に入力して行動を決定する。
 */
public class ScavCombatLogic {

    // Rust側のQ-TableアクションIDとの対応 (LiquidCombatEngine参照)
    // 0:ATTACK, 1:EVADE, 2:BAIT, 3:COUNTER, 4:OBSERVE, 5:RETREAT, 6:BURST, 7:SLIDE
    private static final String[] ACTION_NAMES = {
            "ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"
    };

    /**
     * AIの思考メインプロセス
     *
     * @param self        自身のMobインスタンス
     * @param brain       Rustと通信する脳 (ScavBrain)
     * @param context     センサーがスキャンした環境情報 (ScavContext)
     * @param gunStats    持っている銃の性能
     * @param currentAmmo 現在の装弾数
     * @return 決定された行動計画 (ScavDecision)
     */
    public ScavDecision think(Mob self, ScavBrain brain, ScavContext context, GunStats gunStats, int currentAmmo) {
        ScavDecision decision = new ScavDecision();
        decision.decision = new ScavDecision.DecisionCore();
        decision.movement = new ScavDecision.MovementPlan();
        decision.communication = new ScavDecision.Communication();
        decision.engine_version = "SCAV-v1.0-RustHybrid";

        // 1. ターゲット選定 (最も脅威、または最も近い敵)
        ScavContext.EnemyInfo targetInfo = selectBestTarget(context, self);
        Player target = (targetInfo != null) ? targetInfo.playerInstance : null;

        // ターゲット不在なら待機
        if (target == null) {
            decision.decision.action_type = "OBSERVE";
            decision.movement.strategy = "WANDER";
            decision.reasoning = "NO_TARGET";
            return decision;
        }

        // 2. 戦況パラメータの計算
        double dist = targetInfo.dist;
        double hpPct = (double) context.entity.hp_pct / 100.0;
        double ammoPct = (double) currentAmmo / gunStats.magSize;

        // アドバンテージ算出 (HPと弾数が重要、距離は武器の適正距離かどうか)
        double advantage = calculateAdvantage(hpPct, ammoPct, dist, gunStats);

        // 3. 【重要】リロードの強制介入 (Logic Override)
        // 弾が切れている、または弾が少なくて敵が遠い場合は脳を無視してリロード
        if (currentAmmo <= 0 || (ammoPct < 0.3 && dist > 15.0)) {
            decision.decision.action_type = "RELOAD";
            decision.movement.strategy = "NEAREST_COVER"; // 隠れてリロード
            decision.reasoning = "EMPTY_MAG_OVERRIDE";
            decision.communication.voice_line = "Empty! Reloading!"; // 英語またはロシア語

            // ストレス蓄積 (Rust脳へのフィードバック用)
            brain.frustration += 0.1f;
            return decision;
        }

        // 4. Rust脳への入力作成 (5次元ベクトル)
        // [0]:優位性, [1]:距離, [2]:HP率, [3]:弾薬状況, [4]:敵数
        float[] inputs = new float[5];
        inputs[0] = (float) advantage;
        inputs[1] = (float) dist;
        inputs[2] = (float) hpPct;
        inputs[3] = (float) ammoPct;
        inputs[4] = context.environment.nearby_enemies.size();

        // 5. Rustで推論実行 (Action ID取得)
        int actionIdx = brain.think(inputs);
        String chosenAction = ACTION_NAMES[Math.min(actionIdx, ACTION_NAMES.length - 1)];

        // 6. 行動の具体化 (Decision Objectへの変換)
        resolveAction(decision, chosenAction, target, context, brain);

        // 7. ボイスラインと推論ログ
        assignVoiceLine(decision, brain, advantage);
        decision.reasoning = String.format("A:%.2f D:%.1f Am:%.2f | Act:%s (T:%.2f)",
                advantage, dist, ammoPct, chosenAction, brain.systemTemperature);

        return decision;
    }

    /**
     * ターゲット選定ロジック
     * 距離が近く、かつHPが減っている敵を優先する (SCAVらしいハイエナ戦法)
     */
    private ScavContext.EnemyInfo selectBestTarget(ScavContext context, Mob self) {
        List<ScavContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies == null || enemies.isEmpty()) return null;

        ScavContext.EnemyInfo best = null;
        double maxScore = -999.0;

        for (ScavContext.EnemyInfo enemy : enemies) {
            if (enemy.playerInstance == null) continue;

            // 基本スコア: 近いほど高い
            double score = 20.0 - enemy.dist;

            // 視界内ボーナス
            if (enemy.in_sight) score += 5.0;

            // 弱ってる敵ボーナス (Health: "low" -> 高スコア)
            if ("low".equalsIgnoreCase(enemy.health)) score += 10.0;

            if (score > maxScore) {
                maxScore = score;
                best = enemy;
            }
        }
        return best;
    }

    /**
     * 戦況優位性の計算
     */
    private double calculateAdvantage(double hpPct, double ammoPct, double dist, GunStats stats) {
        // 1. 生存状態 (HP)
        double score = hpPct * 0.5;

        // 2. 火力持続力 (Ammo)
        score += ammoPct * 0.3;

        // 3. 適正距離ボーナス
        // 例: SMGなら近距離、SRなら遠距離で優位
        // ここでは簡易的に 5m~20m を汎用的な適正距離とする
        if (dist >= 5.0 && dist <= 20.0) {
            score += 0.2;
        } else if (dist < 3.0) {
            // 近すぎると不利 (銃身が長いと取り回しが悪いイメージ)
            score -= 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 選択されたアクションIDを具体的な移動・行動計画に変換する
     */
    private void resolveAction(ScavDecision d, String actionType, Player target, ScavContext context, ScavBrain brain) {
        d.decision.action_type = actionType;

        switch (actionType) {
            case "ATTACK" -> {
                d.movement.destination = "ENEMY";
                // 興奮状態(Temp高)なら、少し左右にブレながら突っ込む
                if (brain.systemTemperature > 0.7) {
                    d.movement.strategy = "STRAFE_ATTACK";
                } else {
                    d.movement.strategy = "MAINTAIN_DISTANCE";
                }
            }
            case "EVADE" -> {
                d.movement.strategy = "SIDESTEP";
                // 遮蔽物が近くにあればそちらへ
                if (context.environment.nearest_cover != null && context.environment.nearest_cover.dist < 10) {
                    d.movement.destination = "NEAREST_COVER";
                }
            }
            case "RETREAT" -> {
                d.movement.destination = "NEAREST_COVER";
                d.movement.strategy = "SPRINT_AWAY";
            }
            case "BURST_DASH", "RUSH" -> { // Rust側で定義が異なる場合のフォールバック
                d.decision.action_type = "RUSH"; // SCAVControllerで認識できる名前に統一
                d.movement.destination = "ENEMY";
                d.movement.strategy = "RUSH";
            }
            case "OBSERVE", "BAITING" -> {
                d.movement.strategy = "HOLD_POSITION";
                // 挑発
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    d.communication.voice_line = "Come out!";
                }
            }
            case "ORBITAL_SLIDE" -> {
                // 相手を中心に回る動き
                d.movement.strategy = "ORBITAL";
            }
            default -> {
                d.decision.action_type = "OBSERVE";
                d.movement.strategy = "WANDER";
            }
        }
    }

    /**
     * 状況に応じたボイスライン（SCAV語録）の割り当て
     * ロシア語スラング風の文字列をセットする
     */
    private void assignVoiceLine(ScavDecision d, ScavBrain brain, double advantage) {
        // すでに設定されていればスキップ
        if (d.communication.voice_line != null) return;

        double roll = ThreadLocalRandom.current().nextDouble();

        // 喋る確率は興奮度(SystemTemperature)に依存
        if (roll > (brain.systemTemperature * 0.3)) return;

        if (d.decision.action_type.equals("ATTACK") || d.decision.action_type.equals("RUSH")) {
            // 攻撃的
            String[] aggressiveLines = {
                    "Wonon Suka!",       // There he is, bitch!
                    "Kepka!",            // Cap! (PMCs)
                    "Davay mochi ikh!",  // Come on, kill them!
                    "Opachki!"           // Woops!
            };
            d.communication.voice_line = aggressiveLines[ThreadLocalRandom.current().nextInt(aggressiveLines.length)];
        }
        else if (advantage < 0.3 || d.decision.action_type.equals("RETREAT")) {
            // 劣勢・逃走
            String[] fearLines = {
                    "Aaaah! Trup!",      // Corpse!
                    "Pomogite!",         // Help!
                    "Ne strelay!",       // Don't shoot!
                    "Suka blyat!"        // (Curse)
            };
            d.communication.voice_line = fearLines[ThreadLocalRandom.current().nextInt(fearLines.length)];
        }
    }
}