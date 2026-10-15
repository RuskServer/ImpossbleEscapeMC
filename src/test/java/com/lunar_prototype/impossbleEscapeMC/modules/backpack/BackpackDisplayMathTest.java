package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.github.retrooper.packetevents.util.Quaternion4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 背中のアイテムディスプレイが、前のアーマースタンドの頭にかぶせた時と同じ所・同じ大きさ・同じ向きに描かれる
 * (26.3 のクライアントの描き方をそのまま行列にして比べる。どちらもこの後にアイテムの head の表示設定が続く)
 */
public final class BackpackDisplayMathTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** アーマースタンド: LivingEntityRenderer (向き・裏返し・-1.501) → 頭の軸 (1px) → CustomHeadLayer.translateToHead */
    private static Matrix4f armorStand(float bodyYaw) {
        return new Matrix4f()
                .rotateY((float) Math.toRadians(180.0 - bodyYaw))
                .scale(-1f, -1f, 1f)
                .translate(0f, -1.501f, 0f)
                .translate(0f, 1f / 16f, 0f)
                .translate(0f, -0.25f, 0f)
                .rotateY((float) Math.PI)
                .scale(0.625f, -0.625f, -0.625f);
    }

    /** アイテムディスプレイ: エンティティの向き 0 → 変形 (移動・left_rotation・大きさ) → ItemDisplayRenderer の 180 度 */
    private static Matrix4f display(float bodyYaw) {
        Quaternion4f q = BackpackDisplayManager.bodyRotation(bodyYaw);
        return new Matrix4f()
                .rotateYXZ(0f, 0f, 0f)
                .translate(0f, BackpackDisplayManager.HEAD_TRANSLATION_Y, 0f)
                .rotate(new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW()))
                .scale(BackpackDisplayManager.HEAD_SCALE)
                .rotateY((float) Math.PI);
    }

    public static void main(String[] args) {
        for (float yaw : new float[]{0f, 37.5f, 90f, -135f, 179.9f, 270f}) {
            check(armorStand(yaw).equals(display(yaw), 1e-5f), "yaw " + yaw + "\n" + armorStand(yaw) + "\n" + display(yaw));
        }
        System.out.println("BackpackDisplayMathTest OK");
    }
}
