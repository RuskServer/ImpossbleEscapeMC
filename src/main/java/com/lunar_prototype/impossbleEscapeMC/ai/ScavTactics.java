package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.util.TacticalMath;
import com.lunar_prototype.impossbleEscapeMC.ai.util.TacticalVision;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavWeapon;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

public class ScavTactics {
    private final Mob scav;
    private final GunListener gunListener;
    private final ScavBrain brain;

    // Tactical Movement States
    private int strafeDir = 1; 
    private int strafeTicks = 0;
    private int jumpCooldown = 0;

    // Cover & Peek States
    private Location tacticalCoverLoc = null;
    private int coverStayTicks = 0;
    private int coverSearchCooldown = 0;
    private int peekPhase = 0; // 0: None, 1: Out, 2: Back
    private Location coverLocation = null;
    private Location peekLocation = null;
    private int peekTicks = 0;
    /** 前回顔を出した側 (1 / -1、未実施は0) */
    private int lastPeekSide = 0;
    private int peekShotsRemaining = 0;
    private int peekOutLimit = 5;
    /** 顔出しから戻った後、次に顔を出せるまでの待ち */
    private int peekRestTicks = 0;

    // 横移動の緩急
    /** 横移動の区間ごとの回る勢い (回る半径) の倍率 */
    private double orbitScale = 1.0;
    private int strafePauseTicks = 0;

    // Search/Slicing
    private Location slicingPoint = null;

    public ScavTactics(Mob scav, GunListener gunListener, ScavBrain brain) {
        this.scav = scav;
        this.gunListener = gunListener;
        this.brain = brain;
    }

    public Location getTacticalCoverLoc() { return tacticalCoverLoc; }
    public void setTacticalCoverLoc(Location loc) { this.tacticalCoverLoc = loc; }
    public int getCoverStayTicks() { return coverStayTicks; }
    public void setCoverStayTicks(int ticks) { this.coverStayTicks = ticks; }
    public int getCoverSearchCooldown() { return coverSearchCooldown; }
    public void setCoverSearchCooldown(int ticks) { this.coverSearchCooldown = ticks; }
    public int getPeekPhase() { return peekPhase; }
    
    public void updateTimers() {
        if (jumpCooldown > 0) jumpCooldown--;
        if (strafeTicks > 0) strafeTicks--;
        if (coverStayTicks > 0) {
            coverStayTicks--;
            if (coverStayTicks == 0) {
                tacticalCoverLoc = null;
            }
        }
        if (coverSearchCooldown > 0) coverSearchCooldown--;
        if (peekRestTicks > 0) peekRestTicks--;
    }

    public void handleCombatMovement(int action, LivingEntity target, boolean isAuto, float aggression, float suppression, boolean isSprinting, Iterable<ScavController> nearbyAllies, double preferredRange) {
        Location sLoc = scav.getLocation();
        Location tLoc = target.getLocation();
        double dist = sLoc.distance(tLoc);
        
        boolean isCQC = dist < 10.0;
        int minTicks = (isCQC && isAuto && aggression > 0.5f) ? 5 : 20;
        int varTicks = (isCQC && isAuto && aggression > 0.5f) ? 10 : 30;

        if (strafeTicks <= 0 || (isCQC && isAuto && strafeTicks > 15)) {
            strafeDir = (Math.random() > 0.5) ? 1 : -1;
            strafeTicks = minTicks + (int) (Math.random() * varTicks);
            orbitScale = 0.6 + Math.random() * 0.8; // 区間ごとに回る半径・勢いを変える
        } else if (Math.random() < 0.04) {
            strafeDir = -strafeDir; // 区間の途中での不意の切り返し
        }

        // 横移動・距離維持中は、時々立ち止まって狙いを安定させる (止まる長さもばらつかせる)
        boolean lateral = action == 1 || action == 3 || action == 4;
        if (strafePauseTicks > 0) {
            strafePauseTicks--;
            if (lateral) {
                scav.getPathfinder().stopPathfinding();
                return;
            }
        } else if (lateral && Math.random() < 0.05) {
            strafePauseTicks = 2 + (int) (Math.random() * 5);
            scav.getPathfinder().stopPathfinding();
            return;
        }

        Vector toTarget = tLoc.toVector().subtract(sLoc.toVector()).normalize();
        Vector moveVec = new Vector(0, 0, 0);

        switch (action) {
            case 0: moveVec.add(toTarget.clone().multiply(1.0)); break;
            case 1: moveVec.add(toTarget.clone().multiply((dist - preferredRange) * 0.2)); break;
            case 2: moveVec.add(toTarget.clone().multiply(-1.2)); break;
            case 5:
                if (scav.isOnGround() && jumpCooldown <= 0) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
                    jumpCooldown = 60;
                }
                break;
        }

        double centrifugalWeight = ((action == 3 || action == 4) ? 1.5 : 0.8) * orbitScale;
        if (isCQC && isAuto) centrifugalWeight *= 1.8; 
        moveVec.add(TacticalMath.calculateCentrifugalForce(sLoc, tLoc, strafeDir, dist).multiply(centrifugalWeight));
        moveVec.add(TacticalMath.calculateRepulsion(sLoc, tLoc, target.getEyeLocation().getDirection()));

        for (ScavController ally : nearbyAllies) {
            double allyDist = sLoc.distance(ally.getScav().getLocation());
            if (allyDist < 4.0) {
                moveVec.add(sLoc.toVector().subtract(ally.getScav().getLocation().toVector()).normalize().multiply(0.5));
            }
        }

        if (moveVec.lengthSquared() > 0) {
            Vector finalMove = moveVec.normalize();
            Location dest = sLoc.clone().add(finalMove.multiply(2.0));
            scav.getPathfinder().moveTo(dest, isSprinting ? 1.5 : 1.0);
        }
    }

    /** 物陰の候補のうち、経路を確認する数 (近い順・安全な順) */
    private static final int COVER_PATH_CHECKS = 4;

    public Location findCover(LivingEntity target) {
        if (target == null) return null;
        Location sLoc = scav.getLocation();
        Location tLoc = target.getEyeLocation();
        World world = scav.getWorld();

        java.util.List<Location> candidates = new java.util.ArrayList<>();
        java.util.Map<Location, Float> scores = new java.util.HashMap<>();

        for (int x = -8; x <= 8; x += 2) {
            for (int z = -8; z <= 8; z += 2) {
                for (int y = -1; y <= 2; y++) {
                    Location checkLoc = sLoc.clone().add(x, y, z);
                    // 足元・頭の高さが通れて、下に立てるブロックがある場所だけ (天井が低い所は目がブロックの中に入る)
                    if (!checkLoc.getBlock().isPassable()) continue;
                    if (!checkLoc.clone().add(0, 1, 0).getBlock().isPassable()) continue;
                    if (!checkLoc.clone().add(0, -1, 0).getBlock().getType().isSolid()) continue;

                    Location eyeAtCheck = checkLoc.clone().add(0, 1.6, 0);
                    Vector toTarget = tLoc.toVector().subtract(eyeAtCheck.toVector());
                    var result = world.rayTraceBlocks(eyeAtCheck, toTarget.normalize(), toTarget.length(),
                        org.bukkit.FluidCollisionMode.NEVER, true);

                    if (result != null && result.getHitBlock() != null) {
                        float dangerScore = CombatHeatmapManager.getScore(checkLoc);
                        double dist = checkLoc.distance(sLoc);
                        candidates.add(checkLoc);
                        scores.put(checkLoc, dangerScore + (float)(dist * 0.2));
                    }
                }
            }
        }

        // たどり着けない物陰を選ぶと、撃たずにそこを目指し続けてしまうため経路を確かめる
        candidates.sort(java.util.Comparator.comparingDouble(scores::get));
        for (int i = 0; i < Math.min(COVER_PATH_CHECKS, candidates.size()); i++) {
            Location candidate = candidates.get(i);
            var path = scav.getPathfinder().findPath(candidate);
            if (path != null && path.canReachFinalPoint()) return candidate;
        }
        return null;
    }

    /**
     * @param targetVisible 相手が見えているか (SCAVの視覚判定の結果)
     * @param fireAllowed   撃ってよいか (発見直後の反応時間などで撃てない時はfalse)
     * @param aim           撃つ直前に照準を相手へ合わせる処理。移動中は頭が進行方向を向くため、撃つ前に必ず合わせる
     */
    public void handlePeekManeuver(boolean targetVisible, boolean fireAllowed, Runnable aim, ScavWeapon weapon, float suppression, boolean isSprinting, long lastShotTime, java.util.function.Consumer<Long> shotTimeSetter) {
        peekTicks++;
        if (peekPhase == 1) { // Moving out
            scav.getPathfinder().moveTo(peekLocation, isSprinting ? 1.5 : 1.0);
            if (targetVisible) {
                long now = System.currentTimeMillis();
                long interval = (long) (60000.0 / weapon.rpm());
                if (fireAllowed && now - lastShotTime >= interval) {
                    aim.run();
                    if (weapon.fire(0.1 + (suppression * 0.1))) {
                        shotTimeSetter.accept(now);
                        peekShotsRemaining--;
                    }
                    if (peekShotsRemaining <= 0) {
                        peekPhase = 2;
                        peekTicks = 0;
                    }
                }
            } else if (peekTicks >= peekOutLimit) {
                // 見えないまま時間切れなら撃たずに引っ込む (誰もいない方向への空撃ちを防ぐ)
                peekPhase = 2;
                peekTicks = 0;
            }
            // 連射の遅い銃でも出っぱなしにならないようにする
            if (peekPhase == 1 && peekTicks > peekOutLimit + 8) {
                peekPhase = 2;
                peekTicks = 0;
            }
        } else if (peekPhase == 2) { // Moving back
            scav.getPathfinder().moveTo(coverLocation, isSprinting ? 1.5 : 1.0);
            if (scav.getLocation().distance(coverLocation) < 1.0 || peekTicks >= 5) {
                peekPhase = 0;
                peekRestTicks = 4 + (int) (Math.random() * 20); // 次に顔を出すまでの間をばらつかせる
            }
        }
    }

    /**
     * 物陰から顔を出して撃つ動きを始める。出る側・距離・撃つ回数・出ている時間を毎回ばらつかせる
     *
     * @param jump 顔を出す時に跳ぶ (ジャンプピーク)
     * @return 始めた場合true。前回の顔出しから間が空いていなければfalse
     */
    public boolean startPeek(Location lastKnownLocation, boolean isSprinting, boolean jump) {
        if (peekRestTicks > 0) return false;
        coverLocation = scav.getLocation().clone();
        Vector toTarget = lastKnownLocation.toVector().subtract(coverLocation.toVector()).normalize();
        Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
        // 前回と逆側から出ることが多いが、同じ側から出直すこともある
        int side;
        if (lastPeekSide == 0) side = Math.random() < 0.5 ? 1 : -1;
        else side = Math.random() < 0.65 ? -lastPeekSide : lastPeekSide;
        lastPeekSide = side;
        double offset = 1.0 + Math.random() * 2.0;
        peekLocation = coverLocation.clone().add(tangent.multiply(side * offset));
        peekShotsRemaining = 1 + (int) (Math.random() * 3);
        peekOutLimit = 3 + (int) (Math.random() * 6);
        peekPhase = 1;
        peekTicks = 0;
        scav.getPathfinder().moveTo(peekLocation, isSprinting ? 1.5 : 1.0);
        if (jump && scav.isOnGround() && jumpCooldown <= 0) {
            scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
            jumpCooldown = 20;
        }
        return true;
    }

    public void handleSearching(Location lastKnownLocation, int searchTicks, boolean isSprinting, java.util.function.Consumer<Location> preAimer) {
        if (slicingPoint == null || searchTicks % 40 == 0) {
            slicingPoint = TacticalVision.findSlicingPoint(scav.getLocation(), lastKnownLocation);
        }

        double distToLast = scav.getLocation().distance(lastKnownLocation);
        double speed = isSprinting ? 1.4 : 1.0;

        if (slicingPoint != null && scav.getLocation().distance(slicingPoint) > 1.5) {
            scav.getPathfinder().moveTo(slicingPoint, speed);
            preAimer.accept(lastKnownLocation);
        } else if (distToLast > 2.5) {
            Vector toTarget = lastKnownLocation.toVector().subtract(scav.getLocation().toVector()).normalize();
            Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
            Vector moveVec = tangent.multiply(strafeDir * 0.5).add(toTarget.multiply(0.3));
            scav.getPathfinder().moveTo(scav.getLocation().add(moveVec), 0.8);
            preAimer.accept(lastKnownLocation);

            if (searchTicks > 200) {
                scav.getPathfinder().moveTo(lastKnownLocation, speed);
            }
        }
    }

    public void resetSlicing() {
        slicingPoint = null;
    }

    public void handleJumpShot(LivingEntity target) {
        if (scav.isOnGround() && jumpCooldown <= 0 && checkJumpShotVision(target)) {
            if (Math.random() < 0.4) {
                scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
                jumpCooldown = 40;
            }
        }
    }

    private boolean checkJumpShotVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location jumpEye = eye.clone().add(0, 1.2, 0);
        Vector direction = target.getEyeLocation().toVector().subtract(jumpEye.toVector());
        var result = jumpEye.getWorld().rayTraceBlocks(jumpEye, direction.normalize(), direction.length(),
                org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null;
    }
}
