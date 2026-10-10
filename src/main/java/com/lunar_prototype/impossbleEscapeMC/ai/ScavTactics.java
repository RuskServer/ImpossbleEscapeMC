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
                stopMoving();
                return;
            }
        } else if (lateral && Math.random() < 0.05) {
            strafePauseTicks = 2 + (int) (Math.random() * 5);
            stopMoving();
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
            double speed = isSprinting ? 1.5 : 1.0;
            // 2ブロック先へ行けなければ1ブロック先、それも無理ならその場に留まる
            if (!moveDirect(sLoc.clone().add(finalMove.clone().multiply(2.0)), speed)
                    && !moveDirect(sLoc.clone().add(finalMove.clone().multiply(1.0)), speed)) {
                stopMoving();
            }
        }
    }

    // --- 直接移動 ---
    // 撃ち合い中の横移動や顔出しは短い距離を素早く切り返すため、毎回の経路探索 (止まっては動く) を通さず、
    // 移動の制御 (MoveControl) に直接指示する。まっすぐ行けない (途中に壁・穴がある) 時は、その動きをしない
    // (経路探索に任せると、回り込んで壁の前まで歩くなど、顔出しや横移動に見えない動きになる)。
    // 行き先へ向かう指示 (setWantedPosition) は体を移動方向へ回すため、照準を合わせた向きと毎tick取り合って体が揺れる。
    // そこでスケルトンが弓を構えたまま横移動するのと同じストレイフ (向いている方向を基準に前後左右へ動く) を使う

    /** 顔出しの出入りの速さ (移動速度に対する倍率) */
    private static final double PEEK_SPEED = 1.8;
    /** 顔出し位置にこの距離まで来たら止まったとみなして撃つ */
    private static final double PEEK_SETTLED_DISTANCE = 0.6;

    /** 直接移動の行き先。MoveControl は行き先を与えた次の1tickしか動かないため、毎tick与え直す (tickDirectMove) */
    private Location directTarget;
    private double directSpeed;
    /** 判断 (3tickごと) で更新されなくなったら直接移動をやめる tick */
    private int directUntilTick;
    private static final double DIRECT_ARRIVE_DISTANCE = 0.3;

    /**
     * 相手を向いたまま、近くの場所へ直接移動する (着くか、次の判断まで)
     *
     * @return 動き始めた場合true。まっすぐ行けない時はfalse (動かない)
     */
    public boolean moveDirectTo(Location dest, double speed) {
        return moveDirect(dest, speed);
    }

    private boolean moveDirect(Location dest, double speed) {
        if (!canStepStraight(dest)) {
            endStrafe();
            return false;
        }
        scav.getPathfinder().stopPathfinding();
        directTarget = dest.clone();
        directSpeed = speed;
        directUntilTick = org.bukkit.Bukkit.getCurrentTick() + ScavController.STEP_TICKS + 1;
        tickDirectMove();
        return true;
    }

    /** 今の位置からまっすぐ歩いて行けるか。段差の上り下りはまっすぐには調べられないため、行き先に立てるかだけ見る */
    private boolean canStepStraight(Location dest) {
        Location here = scav.getLocation();
        if (dest.getWorld() != here.getWorld()) return false;
        if (Math.abs(dest.getY() - here.getY()) > 0.5) return isSafeStep(dest);
        return TacticalPositioning.walkableStraight(here, dest);
    }

    /** 毎tick、照準を合わせた後に呼ぶ。直接移動中なら、今の向きを基準にした前後左右の移動を MoveControl に指示し直す */
    public void tickDirectMove() {
        if (directTarget == null) return;
        if (org.bukkit.Bukkit.getCurrentTick() > directUntilTick || directTarget.getWorld() != scav.getWorld()
                || scav.getLocation().distanceSquared(directTarget) < DIRECT_ARRIVE_DISTANCE * DIRECT_ARRIVE_DISTANCE) {
            endStrafe();
            return;
        }
        net.minecraft.world.entity.Mob handle = ((org.bukkit.craftbukkit.entity.CraftMob) scav).getHandle();
        double dx = directTarget.getX() - handle.getX();
        double dz = directTarget.getZ() - handle.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-4) return;
        dx /= length;
        dz /= length;
        // 向き (yaw) の前方は (-sin, cos)、MoveControl の横方向 (xxa の正) は (cos, sin)
        double yaw = Math.toRadians(handle.getYRot());
        float forward = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
        float right = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw));
        net.minecraft.world.entity.ai.control.MoveControl control = handle.getMoveControl();
        control.strafe(forward, right);
        setStrafeSpeed(control, directSpeed);
    }

    private static java.lang.reflect.Field strafeSpeedField;

    /** MoveControl#strafe は速さの倍率を 0.25 に固定するため、指定の倍率に設定し直す */
    private static void setStrafeSpeed(net.minecraft.world.entity.ai.control.MoveControl control, double speed) {
        try {
            if (strafeSpeedField == null) {
                strafeSpeedField = net.minecraft.world.entity.ai.control.MoveControl.class.getDeclaredField("speedModifier");
                strafeSpeedField.setAccessible(true);
            }
            strafeSpeedField.setDouble(control, speed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MoveControl.speedModifier にアクセスできません", e);
        }
    }

    /**
     * ストレイフをやめる。MoveControl は待機中に横方向の入力 (xxa) を戻さず、少しずつ減らすだけなので、
     * そのままだと止めた後や経路探索に切り替えた後も横に流れ続ける
     */
    private void endStrafe() {
        if (directTarget == null) return;
        directTarget = null;
        net.minecraft.world.entity.Mob handle = ((org.bukkit.craftbukkit.entity.CraftMob) scav).getHandle();
        handle.setXxa(0.0F);
        handle.setZza(0.0F);
    }

    /** 経路探索と直接移動の両方を止める */
    public void stopMoving() {
        endStrafe();
        scav.getPathfinder().stopPathfinding();
        Location here = scav.getLocation();
        ((org.bukkit.craftbukkit.entity.CraftMob) scav).getHandle().getMoveControl()
                .setWantedPosition(here.getX(), here.getY(), here.getZ(), 0.0);
    }

    private static boolean clearLine(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 1.0E-3) return true;
        var hit = from.getWorld().rayTraceBlocks(from, direction.multiply(1.0 / length), length, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    /** 足と頭の高さが通れて、1段下までに立てる床がある (崖から落ちない・壁に突っ込まない) */
    private boolean isSafeStep(Location dest) {
        if (dest.getWorld() != scav.getWorld()) return false;
        org.bukkit.block.Block feet = dest.getBlock();
        if (!feet.isPassable() || !feet.getRelative(org.bukkit.block.BlockFace.UP).isPassable()) return false;
        org.bukkit.block.Block below = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return below.getType().isSolid() || below.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid();
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
    /**
     * @param spread 弾のばらつき (通常の射撃と同じ、ランク別の基本値 + 制圧による乱れ)
     */
    public void handlePeekManeuver(boolean targetVisible, boolean fireAllowed, Runnable aim, ScavWeapon weapon, double spread, boolean isSprinting, long lastShotTime, java.util.function.Consumer<Long> shotTimeSetter) {
        peekTicks++;
        if (peekPhase == 1) { // Moving out
            if (!moveDirect(peekLocation, PEEK_SPEED)) {
                // 出る途中で行けなくなった (押された・相手の位置が変わったなど): 引っ込む
                peekPhase = 2;
                peekTicks = 0;
                return;
            }
            // 出て、止まって、撃つ (横へ出ている最中に撃つと当たらない)
            boolean settled = scav.getLocation().distanceSquared(peekLocation) < PEEK_SETTLED_DISTANCE * PEEK_SETTLED_DISTANCE;
            if (targetVisible) {
                long now = System.currentTimeMillis();
                long interval = (long) (60000.0 / weapon.rpm());
                if (settled && fireAllowed && now - lastShotTime >= interval) {
                    aim.run();
                    // 顔出しは単発で撃つ
                    if (weapon.fire(spread, false)) {
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
            // 撃てなくなった (撃ち切った・ボルト操作・リロード) なら、出たままにせず引っ込む
            if (peekPhase == 1 && weapon.ticksUntilReady() > ScavController.PEEK_READY_TICKS) {
                peekPhase = 2;
                peekTicks = 0;
            }
            // 連射の遅い銃でも出っぱなしにならないようにする
            if (peekPhase == 1 && peekTicks > peekOutLimit + 8) {
                peekPhase = 2;
                peekTicks = 0;
            }
        } else if (peekPhase == 2) { // Moving back
            // 戻りは来た道をまっすぐ戻る。押されてまっすぐ戻れない時だけ経路探索で遮蔽へ戻る
            if (!moveDirect(coverLocation, PEEK_SPEED)) scav.getPathfinder().moveTo(coverLocation, PEEK_SPEED);
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
    /**
     * 決めておいた顔出し位置へ出て撃ち、今の位置 (遮蔽) へ戻る
     *
     * @return 始めた場合true。前回の顔出しから間が空いていなければfalse
     */
    public boolean startPeekTo(Location peek) {
        if (peekRestTicks > 0) return false;
        // 立ち位置から少しずれて立っていると、顔出し位置との間に遮蔽の角が入ることがある
        if (!canStepStraight(peek)) return false;
        coverLocation = scav.getLocation().clone();
        peekLocation = peek.clone();
        peekShotsRemaining = 1 + (int) (Math.random() * 3);
        peekOutLimit = 4 + (int) (Math.random() * 6);
        peekPhase = 1;
        peekTicks = 0;
        moveDirect(peekLocation, PEEK_SPEED);
        return true;
    }

    public boolean startPeek(Location lastKnownLocation, boolean isSprinting, boolean jump) {
        if (peekRestTicks > 0) return false;
        coverLocation = scav.getLocation().clone();
        Vector toTarget = lastKnownLocation.toVector().subtract(coverLocation.toVector()).setY(0);
        if (toTarget.lengthSquared() < 1.0E-6) return false;
        toTarget.normalize();
        Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
        // 前回と逆側から出ることが多いが、同じ側から出直すこともある
        int side;
        if (lastPeekSide == 0) side = Math.random() < 0.5 ? 1 : -1;
        else side = Math.random() < 0.65 ? -lastPeekSide : lastPeekSide;
        lastPeekSide = side;
        // 横へまっすぐ出られて、そこから相手のいそうな方向が見える所だけを顔出し位置にする。
        // 決めた側で見つからなければ逆側を試し、どちらも無ければ顔を出さない
        Location threatEye = lastKnownLocation.clone().add(0, 1.5, 0);
        peekLocation = null;
        for (int s : new int[]{side, -side}) {
            for (double offset : new double[]{1.0 + Math.random() * 2.0, 1.5, 1.0}) {
                Location candidate = coverLocation.clone().add(tangent.clone().multiply(s * offset));
                if (canStepStraight(candidate) && clearLine(candidate.clone().add(0, 1.6, 0), threatEye)) {
                    peekLocation = candidate;
                    lastPeekSide = s;
                    break;
                }
            }
            if (peekLocation != null) break;
        }
        if (peekLocation == null) return false;
        peekShotsRemaining = 1 + (int) (Math.random() * 3);
        peekOutLimit = 3 + (int) (Math.random() * 6);
        peekPhase = 1;
        peekTicks = 0;
        moveDirect(peekLocation, PEEK_SPEED);
        if (jump && scav.isOnGround() && jumpCooldown <= 0) {
            scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
            jumpCooldown = 20;
        }
        return true;
    }

    /** 確認する地点がこれより遠ければ小走りで向かう (ブロック) と、その速さ */
    private static final double SEARCH_JOG_DISTANCE = 8.0;
    private static final double SEARCH_JOG_SPEED = 1.5;

    public void handleSearching(Location lastKnownLocation, int searchTicks, boolean isSprinting, java.util.function.Consumer<Location> preAimer) {
        if (slicingPoint == null || searchTicks % 40 == 0) {
            slicingPoint = TacticalVision.findSlicingPoint(scav.getLocation(), lastKnownLocation);
        }

        double distToLast = scav.getLocation().distance(lastKnownLocation);
        double speed = isSprinting ? 1.4 : 1.0;
        // 遠くの物音 (銃声など) へは小走りで近づき、近づいたら慎重に確認する
        if (slicingPoint != null && scav.getLocation().distance(slicingPoint) > SEARCH_JOG_DISTANCE) speed = Math.max(speed, SEARCH_JOG_SPEED);

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
