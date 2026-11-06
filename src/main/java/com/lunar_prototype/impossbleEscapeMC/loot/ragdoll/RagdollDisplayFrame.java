package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.bukkit.Location;

/**
 * 見た目の ItemDisplay を置く位置。
 * <p>
 * Display の変換 (translation・回転) は、エンティティの向き (yaw・pitch) で回った座標系で適用される
 * (描画は「エンティティの向きの回転 → 変換」の順。billboard が FIXED なら向きが 0 の時だけ恒等)。
 * 死んだ SCAV の位置をそのまま使うと向きが付き、見た目だけが原点まわりに回って、ワールド座標に置いた操作箱とずれる。
 * 変換は物理のワールド座標 (ポーズの中心 - 原点) で組んでいるので、向きは必ず 0 にする。
 */
final class RagdollDisplayFrame {
    private RagdollDisplayFrame() {
    }

    /** origin と同じ座標で、向き (yaw・pitch) だけ 0 にした位置。origin は変えない */
    static Location at(Location origin) {
        Location frame = origin.clone();
        frame.setYaw(0f);
        frame.setPitch(0f);
        return frame;
    }
}
