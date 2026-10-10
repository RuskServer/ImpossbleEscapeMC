package com.lunar_prototype.impossbleEscapeMC.modules.medical;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 状態異常を、バニラのステータス効果と同じ見え方 (画面右上のアイコン・インベントリの一覧) で出す。
 * 新しい効果はクライアントに表示できないため、レイドで効かないバニラの効果を付け、
 * リソースパック (IEM_shader/tools/status_icons.py) でアイコンと名前を差し替える。割り当てはそちらと同じにする:
 * <ul>
 *   <li>出血 → 不運 (UNLUCK)。強さが出血のレベル。運はバニラのルート表と釣りにしか効かない</li>
 *   <li>脚の骨折 → 試練の予感 (TRIAL_OMEN)。トライアルスポナーにしか効かない</li>
 *   <li>腕の骨折 → 採掘速度低下 (MINING_FATIGUE)。ブロックを壊す速さと素手の攻撃の間隔だけ</li>
 *   <li>鎮痛剤 → 幸運 (LUCK)。残り時間が出る</li>
 * </ul>
 * 不吉な予感 (BAD_OMEN) は、村の判定 (ベッドや作業台系のブロックで決まる) の中で別の効果に変わって消えるため使わない。
 * これらの効果は状態異常の表示専用で、状態異常が無ければ外す (他から付いた同じ効果も外れる)
 */
public final class StatusEffectIcons {

    static final PotionEffectType BLEEDING = PotionEffectType.UNLUCK;
    static final PotionEffectType LEG_FRACTURE = PotionEffectType.TRIAL_OMEN;
    static final PotionEffectType ARM_FRACTURE = PotionEffectType.MINING_FATIGUE;
    static final PotionEffectType PAINKILLER = PotionEffectType.LUCK;
    /** 鎮痛剤の残り時間が、付けてある効果とこれ以上ずれたら付け直す (tick) */
    private static final int PAINKILLER_RESYNC_TICKS = 40;

    private StatusEffectIcons() {
    }

    /** 状態異常に合わせて効果を付け外しする */
    public static void sync(Player player, PlayerData data) {
        set(player, BLEEDING, data.getBleedingLevel() > 0, PotionEffect.INFINITE_DURATION,
                Math.min(255, data.getBleedingLevel() - 1));
        set(player, LEG_FRACTURE, data.hasLegFracture(), PotionEffect.INFINITE_DURATION, 0);
        set(player, ARM_FRACTURE, data.hasArmFracture(), PotionEffect.INFINITE_DURATION, 0);
        long remainingMillis = data.getPainkillerUntil() - System.currentTimeMillis();
        set(player, PAINKILLER, data.isPainkillerActive() && remainingMillis > 0,
                (int) Math.max(1, remainingMillis / 50), 0);
    }

    /** 全部外す (クリエイティブ・観戦に切り替えた時など) */
    public static void clear(Player player) {
        player.removePotionEffect(BLEEDING);
        player.removePotionEffect(LEG_FRACTURE);
        player.removePotionEffect(ARM_FRACTURE);
        player.removePotionEffect(PAINKILLER);
    }

    private static void set(Player player, PotionEffectType type, boolean active, int duration, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (!active) {
            if (current != null) player.removePotionEffect(type);
            return;
        }
        boolean same = current != null && current.getAmplifier() == amplifier && !current.hasParticles()
                && (duration == PotionEffect.INFINITE_DURATION ? current.isInfinite()
                : !current.isInfinite() && Math.abs(current.getDuration() - duration) <= PAINKILLER_RESYNC_TICKS);
        if (same) return;
        // 弱い・短い効果は今の効果を上書きしないため、外してから付け直す。粒子は出さず、アイコンだけ出す
        if (current != null) player.removePotionEffect(type);
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, false, false, true));
    }
}
