package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 交戦中の立ち位置を選ぶ。
 *
 * 周囲の立てる場所を、相手 (見えなければ最後に分かった位置) に対して
 * 「撃てる位置」「遮蔽+顔出し位置」「隠れる位置」に分け、戦術モードごとの配点・交戦距離・移動距離・
 * 被弾の多かった場所・味方との重なり・回り込む角度で採点して、経路でたどり着ける最善の場所を選ぶ。
 * 選んだ場所はしばらく維持する (今いる場所には維持ボーナスを付け、選び直しは一定間隔か状況が変わった時だけ)。
 */
public final class TacticalPositioning {

    public enum SpotType {
        /** そこから相手が見える */
        FIRE,
        /** そこからは見えないが、横の顔出し位置に出れば撃てる */
        COVER_PEEK,
        /** 見えず、顔を出せる場所もない */
        HIDE
    }

    /**
     * @param stand  立つ場所 (ブロックの中心、足の高さ)
     * @param peek   遮蔽+顔出し位置の場合の顔出し位置 (それ以外はnull)
     * @param threat 選んだ時の相手の目の位置
     */
    public record Spot(Location stand, Location peek, SpotType type, Location threat) {
    }

    private record Candidate(Location stand, Location peek, SpotType type, double score) {
    }

    /** 遮蔽から撃ち合いへ出直す時の配点モード (ScavBrain のモードとは別に ScavController が使う) */
    public static final String MODE_ENGAGE = "ENGAGE";

    /** 候補を探す範囲 (ブロック) と間隔 */
    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_STEP = 2;
    /** 顔出し位置を探す横方向のずれ (ブロック) */
    private static final double[] PEEK_OFFSETS = {1.5, -1.5, 2.5, -2.5};
    /** 相手にこれより近い場所は選ばない */
    private static final double MIN_RANGE = 3.0;
    /** 待つ・顔出しの時、これより近ければ距離を取ろうとする (ブロック) */
    private static final double CLOSE_RANGE = 5.0;
    /** 選び直す間隔 (tick) */
    private static final int REEVALUATE_TICKS = 30;
    /** 相手がこれだけ動いたら選び直す (ブロック) */
    private static final double THREAT_MOVED = 4.0;
    /** 経路を確かめる候補の数 (点数の高い順) */
    private static final int PATH_CHECKS = 4;
    private static final double EYE_HEIGHT = 1.6;
    /** 撃たれた場所を覚えておく時間 (tick) と、その周り (ブロック) の減点。2〜3発で今の場所を離れる */
    private static final int HIT_MEMORY_TICKS = 100;
    private static final double HIT_RADIUS = 2.0;
    private static final double HIT_PENALTY = 0.7;
    /** 影響マップ: 味方が撃たれた所・相手の射線の減点 */
    private static final double DANGER_WEIGHT = 0.4;
    private static final double LANE_WEIGHT = 0.6;
    private static final double LANE_PENALTY_MAX = 2.0;

    private record Hit(Location location, int tick) {
    }

    private final Mob scav;
    private Spot current;
    private int nextEvaluationTick;
    private final List<Hit> recentHits = new ArrayList<>();

    public TacticalPositioning(Mob scav) {
        this.scav = scav;
    }

    public Spot current() {
        return current;
    }

    /** 次の update で選び直させる */
    public void invalidate() {
        nextEvaluationTick = 0;
    }

    /** 撃たれた。しばらくその周りを避け、すぐに選び直す */
    public void recordHit(Location location) {
        int now = Bukkit.getCurrentTick();
        recentHits.removeIf(hit -> now - hit.tick() > HIT_MEMORY_TICKS);
        recentHits.add(new Hit(location.clone(), now));
        nextEvaluationTick = 0;
    }

    public void clear() {
        current = null;
        nextEvaluationTick = 0;
    }

    /**
     * 必要なら立ち位置を選び直して返す
     *
     * @param threat     相手の目の位置 (見えなければ最後に分かった位置の目の高さ)
     * @param mode       ScavBrain の戦術モード名 (配点に使う)
     * @param range      保とうとする交戦距離
     * @param allies     近くの味方 (重ならないように・十字砲火になるように)
     */
    public Spot update(Location threat, String mode, double range, List<ScavController> allies) {
        int now = Bukkit.getCurrentTick();
        boolean threatMoved = current != null && (current.threat().getWorld() != threat.getWorld()
                || current.threat().distanceSquared(threat) > THREAT_MOVED * THREAT_MOVED);
        if (current == null || threatMoved || now >= nextEvaluationTick) {
            Spot chosen = choose(threat, mode, range, allies);
            if (chosen != null) current = chosen;
            nextEvaluationTick = now + REEVALUATE_TICKS;
        }
        return current;
    }

    /**
     * 相手から見えない立ち位置のうち、いちばん近くてたどり着ける所 (無ければnull)。
     * 弾が尽きた時の逃げ場を探すのに使う (今の立ち位置は変えない)
     *
     * @param threat 相手の目の位置
     */
    public Location nearestHidden(Location threat) {
        World world = scav.getWorld();
        Location base = scav.getLocation();
        List<Location> hidden = new ArrayList<>();
        int minX = Math.floorDiv(base.getBlockX() - SEARCH_RADIUS, SEARCH_STEP) * SEARCH_STEP;
        int minZ = Math.floorDiv(base.getBlockZ() - SEARCH_RADIUS, SEARCH_STEP) * SEARCH_STEP;
        for (int x = minX; x <= base.getBlockX() + SEARCH_RADIUS; x += SEARCH_STEP) {
            for (int z = minZ; z <= base.getBlockZ() + SEARCH_RADIUS; z += SEARCH_STEP) {
                Location stand = standable(world, x, base.getBlockY(), z);
                if (stand != null && horizontal(stand, threat) >= MIN_RANGE
                        && !clear(world, stand.clone().add(0, EYE_HEIGHT, 0), threat)) {
                    hidden.add(stand);
                }
            }
        }
        hidden.sort(Comparator.comparingDouble(stand -> horizontal(stand, base)));
        for (int i = 0; i < Math.min(PATH_CHECKS, hidden.size()); i++) {
            Location stand = hidden.get(i);
            if (horizontal(stand, base) <= 1.5) return stand;
            var path = scav.getPathfinder().findPath(stand);
            if (path != null && path.canReachFinalPoint()) return stand;
        }
        return null;
    }

    private Spot choose(Location threat, String mode, double range, List<ScavController> allies) {
        World world = scav.getWorld();
        Location base = scav.getLocation();
        List<Candidate> candidates = new ArrayList<>();
        // 格子はワールド座標に固定する (SCAVの位置基準だと、動くたびに格子がずれて隣の候補と行き来する)
        int minX = Math.floorDiv(base.getBlockX() - SEARCH_RADIUS, SEARCH_STEP) * SEARCH_STEP;
        int minZ = Math.floorDiv(base.getBlockZ() - SEARCH_RADIUS, SEARCH_STEP) * SEARCH_STEP;
        for (int x = minX; x <= base.getBlockX() + SEARCH_RADIUS; x += SEARCH_STEP) {
            for (int z = minZ; z <= base.getBlockZ() + SEARCH_RADIUS; z += SEARCH_STEP) {
                Location stand = standable(world, x, base.getBlockY(), z);
                if (stand != null) addCandidate(candidates, world, stand, threat, mode, range, allies);
            }
        }
        // 今選んでいる場所も必ず候補に入れる (格子から外れていても維持できるように)
        if (current != null && current.stand().getWorld() == world) {
            addCandidate(candidates, world, current.stand(), threat, mode, range, allies);
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());

        // たどり着けない場所を選ぶと、そこを目指し続けて動かなくなるため経路を確かめる
        for (int i = 0; i < Math.min(PATH_CHECKS, candidates.size()); i++) {
            Candidate candidate = candidates.get(i);
            if (horizontal(candidate.stand(), base) > 1.5) {
                var path = scav.getPathfinder().findPath(candidate.stand());
                if (path == null || !path.canReachFinalPoint()) continue;
            }
            return new Spot(candidate.stand(), candidate.peek(), candidate.type(), threat.clone());
        }
        return null;
    }

    private void addCandidate(List<Candidate> candidates, World world, Location stand, Location threat,
                              String mode, double range, List<ScavController> allies) {
        double threatDistance = horizontal(stand, threat);
        if (threatDistance < MIN_RANGE) return;
        SpotType type;
        Location peek = null;
        if (clear(world, stand.clone().add(0, EYE_HEIGHT, 0), threat)) {
            type = SpotType.FIRE;
        } else {
            peek = findPeek(world, stand, threat);
            type = peek != null ? SpotType.COVER_PEEK : SpotType.HIDE;
        }
        candidates.add(new Candidate(stand, peek, type, score(stand, peek, type, threat, threatDistance, mode, range, allies)));
    }

    /** 立ち位置の点数 (高いほど良い) */
    private double score(Location stand, Location peek, SpotType type, Location threat, double threatDistance,
                         String mode, double range, List<ScavController> allies) {
        double score = switch (mode) {
            // 詰める: 撃てる位置も嫌がらず、近めの距離を取る
            case "PUSH" -> type == SpotType.FIRE ? 2.0 : type == SpotType.COVER_PEEK ? 2.4 : 0.0;
            // 回り込む: 遮蔽を使いつつ、相手から見て今と違う角度へ
            case "FLANK" -> type == SpotType.FIRE ? 1.6 : type == SpotType.COVER_PEEK ? 2.6 : 0.2;
            // 出直す: 撃てる場所へ出る (遮蔽に留まる加点を上回るように)
            case MODE_ENGAGE -> type == SpotType.FIRE ? 3.4 : type == SpotType.COVER_PEEK ? 1.0 : 0.0;
            // 下がる: 隠れられる場所を優先し、遠めを取る
            case "WITHDRAW" -> type == SpotType.FIRE ? 0.0 : type == SpotType.COVER_PEEK ? 1.6 : 2.6;
            // 待つ・顔出し: 遮蔽から顔を出して撃てる場所
            default -> type == SpotType.FIRE ? 1.0 : type == SpotType.COVER_PEEK ? 3.0 : 0.6;
        };
        // 距離はモードの向きにだけ寄せる (詰めるのに下がる・下がるのに詰める、を起こさない)。
        // 待つ・顔出しでは、遠すぎるか近すぎる時だけ距離を動く理由にする (近めの撃ち合いから下がると当たらなくなる)
        Location here = scav.getLocation();
        double currentDistance = horizontal(here, threat);
        double desired = switch (mode) {
            case "PUSH" -> Math.min(currentDistance, range * 0.5);
            // 下がるのは隠れるため。撃てる (=相手からも見える) 場所へ遠ざかっても当たりにくくなるだけ
            case "WITHDRAW" -> type == SpotType.FIRE ? currentDistance : Math.max(currentDistance, range * 1.4);
            default -> Math.max(Math.min(CLOSE_RANGE, range), Math.min(range * 1.2, currentDistance));
        };
        score -= 0.15 * Math.abs(threatDistance - desired);

        // 味方が相手を制圧している間は、動くのも射線に出るのも安全になる (援護を受けて動く)
        boolean covered = false;
        for (ScavController ally : allies) {
            if (ally.isSuppressing()) {
                covered = true;
                break;
            }
        }
        score -= (covered ? 0.07 : 0.12) * horizontal(stand, here);
        // 今いる場所・今選んでいる場所に留まりやすくする (選び直しのたびに動き回らないように)
        if (horizontal(stand, here) < 1.5) score += 0.6;
        if (current != null && current.stand().getWorld() == stand.getWorld() && horizontal(current.stand(), stand) < 1.5) score += 0.6;

        // 影響マップ: 長期 (戦闘が起きやすい所) は少しだけ、最近味方が撃たれた所と、相手が撃ち込んでいる射線は強く避ける。
        // 射線は、撃てる位置ならその場所、遮蔽+顔出しなら顔を出す所で読む (隠れている場所に弾が来ても遮蔽が止める)
        score -= 0.1 * Math.max(0.0, CombatHeatmapManager.getScore(stand));
        score -= DANGER_WEIGHT * CombatHeatmapManager.danger(stand);
        Location exposedAt = type == SpotType.FIRE ? stand : type == SpotType.COVER_PEEK ? peek : null;
        if (exposedAt != null) {
            double lane = CombatHeatmapManager.fireLane(exposedAt.clone().add(0, EYE_HEIGHT, 0));
            score -= Math.min(LANE_PENALTY_MAX, (covered ? 0.5 : 1.0) * LANE_WEIGHT * lane);
        }
        // 撃たれたばかりの場所は避ける (撃たれても同じ場所に立ち続けないように)
        int now = Bukkit.getCurrentTick();
        for (Hit hit : recentHits) {
            int age = now - hit.tick();
            if (age > HIT_MEMORY_TICKS || hit.location().getWorld() != stand.getWorld()) continue;
            if (horizontal(hit.location(), stand) < HIT_RADIUS) score -= HIT_PENALTY * (1.0 - (double) age / HIT_MEMORY_TICKS);
        }
        if (stand.getY() > threat.getY() - EYE_HEIGHT + 1.0) score += 0.3; // 高所

        Vector fromThreat = stand.toVector().subtract(threat.toVector()).setY(0);
        if ("FLANK".equals(mode)) {
            Vector currentFromThreat = here.toVector().subtract(threat.toVector()).setY(0);
            score += 1.2 * Math.min(90.0, angle(fromThreat, currentFromThreat)) / 90.0;
        }
        for (ScavController ally : allies) {
            Location allyLocation = ally.getScav().getLocation();
            if (allyLocation.getWorld() != stand.getWorld()) continue;
            if (horizontal(allyLocation, stand) < 2.5) score -= 1.5; // 味方と重ならない
            Spot allySpot = ally.getPositioning().current();
            if (allySpot != null && allySpot.stand().getWorld() == stand.getWorld() && horizontal(allySpot.stand(), stand) < 2.5) score -= 1.5;
            // 味方と違う角度から撃つ (十字砲火)
            double crossAngle = angle(fromThreat, allyLocation.toVector().subtract(threat.toVector()).setY(0));
            if (crossAngle >= 30 && crossAngle <= 120) score += 0.5;
        }
        return score;
    }

    /** 遮蔽の横で、相手が見える顔出し位置 */
    private static Location findPeek(World world, Location stand, Location threat) {
        Vector toThreat = threat.toVector().subtract(stand.toVector()).setY(0);
        if (toThreat.lengthSquared() < 1.0E-6) return null;
        toThreat.normalize();
        Vector side = new Vector(-toThreat.getZ(), 0, toThreat.getX());
        for (double offset : PEEK_OFFSETS) {
            Location candidate = stand.clone().add(side.clone().multiply(offset));
            Location peek = standable(world, candidate.getBlockX(), stand.getBlockY(), candidate.getBlockZ());
            if (peek != null && Math.abs(peek.getY() - stand.getY()) < 0.5
                    && clear(world, peek.clone().add(0, EYE_HEIGHT, 0), threat)) {
                return peek;
            }
        }
        return null;
    }

    /**
     * 経路探索を使わず、まっすぐ歩いて行けるか (足元と頭の高さに遮るものがなく、途中に穴や段差がない)
     */
    public static boolean walkableStraight(Location from, Location to) {
        World world = from.getWorld();
        if (world != to.getWorld() || Math.abs(from.getY() - to.getY()) > 0.5) return false;
        Location start = from.clone();
        start.setY(to.getY());
        if (!clear(world, start.clone().add(0, 0.3, 0), to.clone().add(0, 0.3, 0))
                || !clear(world, start.clone().add(0, 1.5, 0), to.clone().add(0, 1.5, 0))) {
            return false;
        }
        double length = horizontal(start, to);
        int samples = (int) Math.ceil(length);
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Location point = start.clone().add(to.clone().subtract(start).multiply(t));
            if (!point.getBlock().getRelative(0, -1, 0).getType().isSolid()) return false;
        }
        return true;
    }

    /** 足と頭の高さが通れて足元に床がある場所 (同じ高さ・1段上・1段下の順に探す) */
    private static Location standable(World world, int x, int y, int z) {
        for (int dy : new int[]{0, 1, -1}) {
            Block feet = world.getBlockAt(x, y + dy, z);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                return new Location(world, x + 0.5, y + dy, z + 0.5);
            }
        }
        return null;
    }

    private static boolean clear(World world, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 1.0E-3) return true;
        RayTraceResult hit = world.rayTraceBlocks(from, direction.multiply(1.0 / length), length, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    private static double horizontal(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static double angle(Vector a, Vector b) {
        if (a.lengthSquared() < 1.0E-6 || b.lengthSquared() < 1.0E-6) return 0.0;
        return Math.toDegrees(a.angle(b));
    }
}
