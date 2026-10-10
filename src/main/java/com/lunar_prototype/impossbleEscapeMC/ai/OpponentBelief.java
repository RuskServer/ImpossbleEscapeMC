package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * 見失った相手がどこにいそうかを、候補点 (パーティクル) の集まりで持つ (ベイズ推定)。
 *
 * 見失った位置と見えていた時の動きから候補点をばらまき、毎ステップ、プレイヤーの歩き・走りの速さで
 * 通れるブロックだけを動かす (予測)。そのうえで、このSCAVが自分で知覚したことだけで重みを更新する (観測)。
 * <ul>
 *   <li>見ている方向で射線の通る候補点に相手がいないなら、そこにはいない (負の証拠)</li>
 *   <li>足音・銃声・撃たれた方向がしたなら、その近くにいる</li>
 * </ul>
 * 味方の視界や相手の本当の位置は使わない (壁越しに居場所が分かってしまうため)。
 * <p>
 * 見える場所へ出たために消えた候補点の位置も覚えておく。重みが流れ込んで消える所 = 相手が出てきそうな所
 * (遮蔽の端) なので、照準を置いておく場所・制圧射撃で撃ち込む場所に使う。
 */
public final class OpponentBelief {

    /** 候補点の数 */
    private static final int PARTICLES = 64;
    /** 1回の観測で射線を調べる候補点の数 (負荷を抑えるため順番に一部だけ) */
    private static final int VISIBILITY_CHECKS = 16;
    /** 見えるはずの候補点に相手がいなかった時、それでも見落としていた確率 */
    private static final double MISS_PROBABILITY = 0.1;
    /** これより遠い候補点は見えても見落としやすい (ブロック) */
    private static final double CLEAR_VIEW_DISTANCE = 40.0;
    private static final double FAR_MISS_PROBABILITY = 0.5;
    /** 移動の速さ (ブロック/tick): 止まる・しゃがみ・歩き・走り */
    private static final double[] SPEEDS = {0.0, 0.065, 0.216, 0.28};
    private static final double[] SPEED_WEIGHTS = {0.3, 0.1, 0.35, 0.25};
    /** 1ステップごとに向き・速さを変える確率 */
    private static final double TURN_PROBABILITY = 0.12;
    /** 重みの偏りがこれを下回ったら選び直す (有効な候補点の割合) */
    private static final double RESAMPLE_THRESHOLD = 0.5;
    /** 選び直す時のずらし幅 (ブロック) */
    private static final double RESAMPLE_JITTER = 0.4;
    /** 音などの手がかりが分布から外れていた時、手がかりの近くへ置き直す候補点の割合 */
    private static final double REINJECT_FRACTION = 0.25;
    private static final double CUE_OUTSIDE_LIKELIHOOD = 0.05;
    private static final double EYE_HEIGHT = 1.6;
    /** 集まりの大きさ (この距離以内の重みをまとめる、ブロック) */
    private static final double CLUSTER_RADIUS = 2.0;
    /** 出てきそうな所の記録 (消えた候補点) の数と、重みが半分になるまでの時間・捨てるまでの時間 (tick) */
    private static final int FRONTIER_SIZE = 48;
    private static final double FRONTIER_HALF_LIFE_TICKS = 40.0;
    private static final int FRONTIER_MAX_AGE_TICKS = 100;
    /** 出てきそうな所として使うのに要る、最近消えた重みの合計と、その所に集まっている割合 */
    private static final double FRONTIER_MIN_TOTAL = 0.05;
    private static final double FRONTIER_MIN_SHARE = 0.3;

    private final double[] x = new double[PARTICLES];
    private final double[] y = new double[PARTICLES];
    private final double[] z = new double[PARTICLES];
    private final double[] vx = new double[PARTICLES];
    private final double[] vz = new double[PARTICLES];
    private final double[] weight = new double[PARTICLES];
    private final Random random = new Random();
    private final double[] fx = new double[FRONTIER_SIZE];
    private final double[] fy = new double[FRONTIER_SIZE];
    private final double[] fz = new double[FRONTIER_SIZE];
    private final double[] fw = new double[FRONTIER_SIZE];
    private final int[] ft = new int[FRONTIER_SIZE];
    private int frontierNext;
    private int frontierCount;
    private World world;
    private boolean active;
    private int nextVisibilityIndex;

    /** 推定の要約: 一番ありそうな場所と、その周りに集まっている確率 */
    public record Estimate(Location location, double mass, double spread) {
    }

    public boolean isActive() {
        return active && world != null;
    }

    public void clear() {
        active = false;
        world = null;
    }

    /**
     * 見失った時に、最後に見えた位置と動きから候補点をばらまく
     *
     * @param velocity 見えていた時の水平の移動 (ブロック/tick)
     */
    public void reset(Location lastSeen, Vector velocity) {
        world = lastSeen.getWorld();
        active = world != null;
        frontierCount = 0;
        double moving = velocity != null ? Math.hypot(velocity.getX(), velocity.getZ()) : 0.0;
        for (int i = 0; i < PARTICLES; i++) {
            x[i] = lastSeen.getX() + gaussian(0.3);
            y[i] = lastSeen.getY();
            z[i] = lastSeen.getZ() + gaussian(0.3);
            // 半分は見えていた時の動きを続け、残りはどの向きにも動きうる
            if (moving > 0.03 && i < PARTICLES / 2) {
                double scale = 0.6 + random.nextDouble() * 0.8;
                vx[i] = velocity.getX() * scale;
                vz[i] = velocity.getZ() * scale;
            } else {
                randomVelocity(i);
            }
            weight[i] = 1.0 / PARTICLES;
        }
    }

    /** 相手が見えた: その位置に集める */
    public void observeSeen(Location location, Vector velocity) {
        reset(location, velocity);
    }

    /** ticks 分だけ候補点を動かす。通れないブロックには入らず、向きを変える */
    public void predict(int ticks) {
        if (!isActive()) return;
        for (int i = 0; i < PARTICLES; i++) {
            if (random.nextDouble() < TURN_PROBABILITY) randomVelocity(i);
            double nx = x[i] + vx[i] * ticks;
            double nz = z[i] + vz[i] * ticks;
            double ny = walkableY(nx, y[i], nz);
            if (Double.isNaN(ny)) {
                // 壁: その場に留まり、次は別の向きへ
                randomVelocity(i);
            } else {
                x[i] = nx;
                y[i] = ny;
                z[i] = nz;
            }
        }
    }

    /**
     * 自分の視界の負の証拠: 見ている方向で射線の通る候補点に、相手は見えていない
     *
     * @param eye          SCAVの目の位置
     * @param look         見ている向き
     * @param fovDegrees   視野角 (全幅)
     */
    public void observeNotVisible(Location eye, Vector look, double fovDegrees, double maxDistance) {
        if (!isActive() || eye.getWorld() != world) return;
        Vector lookDir = look.clone().normalize();
        double cosHalf = Math.cos(Math.toRadians(fovDegrees / 2.0));
        int checked = 0;
        for (int n = 0; n < PARTICLES && checked < VISIBILITY_CHECKS; n++) {
            int i = (nextVisibilityIndex + n) % PARTICLES;
            Vector to = new Vector(x[i] - eye.getX(), y[i] + EYE_HEIGHT - eye.getY(), z[i] - eye.getZ());
            double distance = to.length();
            if (distance < 1.0E-3 || distance > maxDistance) continue;
            if (to.clone().multiply(1.0 / distance).dot(lookDir) < cosHalf) continue;
            checked++;
            RayTraceResult hit = world.rayTraceBlocks(eye, to.multiply(1.0 / distance), distance, FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitBlock() == null) {
                double miss = distance <= CLEAR_VIEW_DISTANCE ? MISS_PROBABILITY : FAR_MISS_PROBABILITY;
                recordFrontier(i, weight[i] * (1.0 - miss));
                weight[i] *= miss;
            }
        }
        nextVisibilityIndex = (nextVisibilityIndex + VISIBILITY_CHECKS) % PARTICLES;
        normalizeAndResample(null, 0.0);
    }

    /**
     * 音・撃たれた方向などの手がかり: 聞こえた位置 (誤差込み) の近くにいる
     *
     * @param sigma 位置の誤差 (ブロック)
     */
    public void observeNear(Location heard, double sigma) {
        if (heard.getWorld() == null) return;
        if (!isActive() || heard.getWorld() != world) {
            reset(heard, null);
            return;
        }
        double s2 = 2.0 * Math.max(0.5, sigma) * Math.max(0.5, sigma);
        for (int i = 0; i < PARTICLES; i++) {
            double dx = x[i] - heard.getX();
            double dz = z[i] - heard.getZ();
            // 手がかりから遠い候補点もわずかに残す (音の位置の誤差が想定より大きい時のため)
            weight[i] *= Math.exp(-(dx * dx + dz * dz) / s2) + 1.0E-3;
        }
        normalizeAndResample(heard, sigma);
    }

    /** 一番ありそうな場所 (重みが最も集まっている所の重心) と、そこへの集まり具合 */
    public Estimate estimate() {
        if (!isActive()) return null;
        int best = -1;
        double bestMass = -1.0;
        double r2 = CLUSTER_RADIUS * CLUSTER_RADIUS;
        for (int i = 0; i < PARTICLES; i++) {
            double mass = 0.0;
            for (int j = 0; j < PARTICLES; j++) {
                double dx = x[j] - x[i];
                double dz = z[j] - z[i];
                if (dx * dx + dz * dz <= r2) mass += weight[j];
            }
            if (mass > bestMass) {
                bestMass = mass;
                best = i;
            }
        }
        double sx = 0, sy = 0, sz = 0, sw = 0;
        for (int j = 0; j < PARTICLES; j++) {
            double dx = x[j] - x[best];
            double dz = z[j] - z[best];
            if (dx * dx + dz * dz <= r2) {
                sx += x[j] * weight[j];
                sy += y[j] * weight[j];
                sz += z[j] * weight[j];
                sw += weight[j];
            }
        }
        Location center = new Location(world, sx / sw, sy / sw, sz / sw);
        double spread = 0.0;
        for (int j = 0; j < PARTICLES; j++) {
            spread += weight[j] * Math.hypot(x[j] - center.getX(), z[j] - center.getZ());
        }
        return new Estimate(center, bestMass, spread);
    }

    /**
     * 相手が出てきそうな所: 見える場所へ出て消えた候補点のうち、最近の重みが一番集まっている所。
     * 記録が少ない・ばらけている時はnull
     *
     * @param now 今のtick
     */
    public Estimate emergence(int now) {
        if (!isActive() || frontierCount == 0) return null;
        double[] w = new double[frontierCount];
        double total = 0.0;
        for (int i = 0; i < frontierCount; i++) {
            int age = now - ft[i];
            w[i] = age > FRONTIER_MAX_AGE_TICKS ? 0.0 : fw[i] * Math.pow(0.5, age / FRONTIER_HALF_LIFE_TICKS);
            total += w[i];
        }
        if (total < FRONTIER_MIN_TOTAL) return null;
        double r2 = CLUSTER_RADIUS * CLUSTER_RADIUS;
        int best = -1;
        double bestMass = 0.0;
        for (int i = 0; i < frontierCount; i++) {
            if (w[i] <= 0.0) continue;
            double mass = 0.0;
            for (int j = 0; j < frontierCount; j++) {
                double dx = fx[j] - fx[i], dz = fz[j] - fz[i];
                if (dx * dx + dz * dz <= r2) mass += w[j];
            }
            if (mass > bestMass) {
                bestMass = mass;
                best = i;
            }
        }
        if (best < 0 || bestMass / total < FRONTIER_MIN_SHARE) return null;
        double sx = 0, sy = 0, sz = 0, sw = 0;
        for (int j = 0; j < frontierCount; j++) {
            double dx = fx[j] - fx[best], dz = fz[j] - fz[best];
            if (dx * dx + dz * dz <= r2) {
                sx += fx[j] * w[j];
                sy += fy[j] * w[j];
                sz += fz[j] * w[j];
                sw += w[j];
            }
        }
        return new Estimate(new Location(world, sx / sw, sy / sw, sz / sw), bestMass / total, 0.0);
    }

    private void recordFrontier(int i, double mass) {
        int slot = frontierNext;
        fx[slot] = x[i];
        fy[slot] = y[i];
        fz[slot] = z[i];
        fw[slot] = mass;
        ft[slot] = org.bukkit.Bukkit.getCurrentTick();
        frontierNext = (frontierNext + 1) % FRONTIER_SIZE;
        frontierCount = Math.min(FRONTIER_SIZE, frontierCount + 1);
    }

    private void normalizeAndResample(Location cue, double cueSigma) {
        double total = 0.0;
        for (double w : weight) total += w;
        if (total <= 1.0E-12) {
            // どの候補点も否定された: 手がかりがあればそこから、なければ今の点を均等に戻して探し直す
            if (cue != null) {
                reset(cue, null);
            } else {
                for (int i = 0; i < PARTICLES; i++) weight[i] = 1.0 / PARTICLES;
            }
            return;
        }
        double squares = 0.0;
        for (int i = 0; i < PARTICLES; i++) {
            weight[i] /= total;
            squares += weight[i] * weight[i];
        }
        // 手がかりの尤度の平均が小さい = 手がかりが今の分布の外
        boolean cueOutside = cue != null && total < CUE_OUTSIDE_LIKELIHOOD;
        if (1.0 / squares >= PARTICLES * RESAMPLE_THRESHOLD && !cueOutside) return;

        // 系統的リサンプリング: 重みに比例して候補点を選び直し、少しずらす
        double[] nx = new double[PARTICLES], ny = new double[PARTICLES], nz = new double[PARTICLES];
        double[] nvx = new double[PARTICLES], nvz = new double[PARTICLES];
        double step = 1.0 / PARTICLES;
        double u = random.nextDouble() * step;
        double cumulative = weight[0];
        int source = 0;
        for (int i = 0; i < PARTICLES; i++) {
            double target = u + i * step;
            while (target > cumulative && source < PARTICLES - 1) cumulative += weight[++source];
            nx[i] = x[source] + gaussian(RESAMPLE_JITTER);
            ny[i] = y[source];
            nz[i] = z[source] + gaussian(RESAMPLE_JITTER);
            nvx[i] = vx[source];
            nvz[i] = vz[source];
        }
        // 手がかりが今の分布の外 (全体の重みが小さかった) なら、一部を手がかりの近くへ置き直す
        int reinject = cueOutside ? (int) (PARTICLES * REINJECT_FRACTION) : 0;
        for (int i = 0; i < PARTICLES; i++) {
            if (i < reinject) {
                x[i] = cue.getX() + gaussian(cueSigma);
                y[i] = cue.getY();
                z[i] = cue.getZ() + gaussian(cueSigma);
                randomVelocity(i);
            } else {
                x[i] = nx[i];
                y[i] = ny[i];
                z[i] = nz[i];
                vx[i] = nvx[i];
                vz[i] = nvz[i];
            }
            weight[i] = 1.0 / PARTICLES;
        }
    }

    private void randomVelocity(int i) {
        double r = random.nextDouble();
        double speed = SPEEDS[SPEEDS.length - 1];
        for (int k = 0; k < SPEEDS.length; k++) {
            r -= SPEED_WEIGHTS[k];
            if (r <= 0) {
                speed = SPEEDS[k];
                break;
            }
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        vx[i] = Math.cos(angle) * speed;
        vz[i] = Math.sin(angle) * speed;
    }

    /** 立てる高さ (同じ高さ・1段上・1段下)。立てなければ NaN */
    private double walkableY(double px, double py, double pz) {
        int bx = (int) Math.floor(px), by = (int) Math.floor(py + 1.0E-3), bz = (int) Math.floor(pz);
        for (int dy : new int[]{0, 1, -1}) {
            Block feet = world.getBlockAt(bx, by + dy, bz);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                return by + dy;
            }
        }
        return Double.NaN;
    }

    private double gaussian(double sigma) {
        return random.nextGaussian() * sigma;
    }
}
