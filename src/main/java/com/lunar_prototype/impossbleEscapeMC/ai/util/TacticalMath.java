package com.lunar_prototype.impossbleEscapeMC.ai.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * タクティカルな移動ベクトルの計算を補助するユーティリティ
 */
public class TacticalMath {

    /**
     * ターゲットを中心とした「遠心力（円運動）」ベクトルを計算する
     * 距離が近いほど横への回避を強める
     */
    public static Vector calculateCentrifugalForce(Location current, Location target, int strafeDir, double distance) {
        Vector toTarget = target.toVector().subtract(current.toVector()).normalize();
        Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        
        // 距離が近いほど(12ブロック以下)、横への重みを大きくする
        double flankWeight = 1.2 - (distance / 20.0);
        flankWeight = Math.max(0.4, flankWeight);

        return tangent.multiply(strafeDir * flankWeight);
    }

    /**
     * 射線（Hazard）からの「斥力」ベクトルを計算する
     * ターゲットが自分を見ている直線上を避けようとする力
     */
    public static Vector calculateRepulsion(Location current, Location hazardLoc, Vector hazardDir) {
        Vector toSelf = current.toVector().subtract(hazardLoc.toVector());
        double dot = toSelf.normalize().dot(hazardDir.normalize());

        // 射線の中心に近い（ドット積が1に近い）ほど、強く弾かれるベクトルを生成
        if (dot > 0.9) { // 角度約25度以内
            Vector cross = hazardDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            // 左右どちらか、自分に近い方へ弾く
            if (toSelf.dot(cross) < 0) cross.multiply(-1);
            return cross.multiply(1.5 * dot);
        }
        return new Vector(0, 0, 0);
    }

    /**
     * 進行方向を「壁」や「崖」に沿うように補正する
     */
    public static Vector slideAlongWall(Vector move, Location current) {
        // 将来的にレイトレースを用いて壁に沿うロジックを追加可能
        return move;
    }
}
