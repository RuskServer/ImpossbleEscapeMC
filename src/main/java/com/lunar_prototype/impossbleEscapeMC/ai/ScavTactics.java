package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.util.TacticalMath;
import com.lunar_prototype.impossbleEscapeMC.ai.util.TacticalVision;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
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
        if (coverStayTicks > 0) coverStayTicks--;
        if (coverSearchCooldown > 0) coverSearchCooldown--;
    }

    public void handleCombatMovement(int action, LivingEntity target, boolean isAuto, float aggression, float suppression, boolean isSprinting, Iterable<ScavController> nearbyAllies) {
        Location sLoc = scav.getLocation();
        Location tLoc = target.getLocation();
        double dist = sLoc.distance(tLoc);
        
        boolean isCQC = dist < 10.0;
        int minTicks = (isCQC && isAuto && aggression > 0.5f) ? 5 : 20;
        int varTicks = (isCQC && isAuto && aggression > 0.5f) ? 10 : 30;

        if (strafeTicks <= 0 || (isCQC && isAuto && strafeTicks > 15)) {
            strafeDir = (Math.random() > 0.5) ? 1 : -1;
            strafeTicks = minTicks + (int) (Math.random() * varTicks);
        }

        Vector toTarget = tLoc.toVector().subtract(sLoc.toVector()).normalize();
        Vector moveVec = new Vector(0, 0, 0);

        switch (action) {
            case 0: moveVec.add(toTarget.clone().multiply(1.0)); break;
            case 1: moveVec.add(toTarget.clone().multiply((dist - 12.0) * 0.2)); break;
            case 2: moveVec.add(toTarget.clone().multiply(-1.2)); break;
            case 5:
                if (scav.isOnGround() && jumpCooldown <= 0) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
                    jumpCooldown = 60;
                }
                break;
        }

        double centrifugalWeight = (action == 3 || action == 4) ? 1.5 : 0.8;
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

    public Location findCover(LivingEntity target) {
        if (target == null) return null;
        Location sLoc = scav.getLocation();
        Location tLoc = target.getEyeLocation();
        World world = scav.getWorld();

        Location bestCover = null;
        float bestScore = Float.MAX_VALUE;

        for (int x = -8; x <= 8; x += 2) {
            for (int z = -8; z <= 8; z += 2) {
                for (int y = -1; y <= 2; y++) {
                    Location checkLoc = sLoc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType().isSolid()) continue;
                    if (!checkLoc.clone().add(0, -1, 0).getBlock().getType().isSolid()) continue;

                    Location eyeAtCheck = checkLoc.clone().add(0, 1.6, 0);
                    Vector toTarget = tLoc.toVector().subtract(eyeAtCheck.toVector());
                    var result = world.rayTraceBlocks(eyeAtCheck, toTarget.normalize(), toTarget.length(), 
                        org.bukkit.FluidCollisionMode.NEVER, true);
                    
                    if (result != null && result.getHitBlock() != null) {
                        float dangerScore = CombatHeatmapManager.getScore(checkLoc);
                        double dist = checkLoc.distance(sLoc);
                        float finalScore = dangerScore + (float)(dist * 0.2); 

                        if (finalScore < bestScore) {
                            bestScore = finalScore;
                            bestCover = checkLoc;
                        }
                    }
                }
            }
        }
        return bestCover;
    }

    public void handlePeekManeuver(LivingEntity target, GunStats stats, float suppression, boolean isSprinting, long lastShotTime, java.util.function.Consumer<Long> shotTimeSetter) {
        peekTicks++;
        if (peekPhase == 1) { // Moving out
            scav.getPathfinder().moveTo(peekLocation, isSprinting ? 1.5 : 1.0);
            boolean currentLos = target != null && scav.hasLineOfSight(target);
            if (currentLos || peekTicks >= 5) {
                long now = System.currentTimeMillis();
                long interval = (long) (60000.0 / stats.rpm);
                if (now - lastShotTime >= interval) {
                    gunListener.executeMobShoot(scav, stats, 1, 0.1 + (suppression * 0.1));
                    shotTimeSetter.accept(now);
                    peekPhase = 2;
                    peekTicks = 0;
                }
            }
        } else if (peekPhase == 2) { // Moving back
            scav.getPathfinder().moveTo(coverLocation, isSprinting ? 1.5 : 1.0);
            if (scav.getLocation().distance(coverLocation) < 1.0 || peekTicks >= 5) {
                peekPhase = 0;
            }
        }
    }

    public void startPeek(Location lastKnownLocation, boolean isSprinting) {
        coverLocation = scav.getLocation().clone();
        Vector toTarget = lastKnownLocation.toVector().subtract(coverLocation.toVector()).normalize();
        Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
        if (Math.random() > 0.5) tangent.multiply(-1);
        peekLocation = coverLocation.clone().add(tangent.multiply(2.0));
        peekPhase = 1;
        peekTicks = 0;
        scav.getPathfinder().moveTo(peekLocation, isSprinting ? 1.5 : 1.0);
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
