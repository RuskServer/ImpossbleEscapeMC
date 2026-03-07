package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class ScavController {
    private final Mob scav;
    private final ScavBrain brain;
    private final GunListener gunListener;

    private Location lastKnownLocation = null;
    private int searchTicks = 0;
    private float suppression = 0.0f; // 制圧レベル (0.0 - 1.0)
    private int jumpCooldown = 0;

    // --- Search & Recognition States ---
    private boolean isAlerted = false; // 警戒状態（ターゲットはいないが音などで気づいている）
    private int cornerCheckTicks = 0;

    // --- Tactical Movement States ---
    private int strafeDir = 1; // 1: Right, -1: Left
    private int strafeTicks = 0;

    // --- Squad States ---
    private int lastSquadUpdate = 0;
    private List<ScavController> nearbyAllies = new ArrayList<>();
    private boolean isSprinting = false;

    public boolean isSprinting() {
        return isSprinting;
    }

    private enum SquadRole { NONE, POINTMAN, COVERMAN }
    private SquadRole myRole = SquadRole.NONE;

    // --- Cover & Tactical States ---
    private Location tacticalCoverLoc = null;
    private int coverStayTicks = 0;
    private int coverSearchCooldown = 0;
    private boolean isPreAiming = false;

    // --- Aiming States (Human-like) ---
    private Vector currentAimVector = null;
    private double aimErrorYaw = 0;
    private double aimErrorPitch = 0;
    private long lastMobShotTime = 0;

    // --- Peek & Hide Maneuver States ---
    private int peekPhase = 0; // 0: None, 1: Moving out, 2: Moving back
    private Location coverLocation = null;
    private Location peekLocation = null;
    private int peekTicks = 0;

    private static final double MAX_VISION_DISTANCE = 96.0; // 6 Chunks
    private static final double FOV_ANGLE = 120.0;

    public ScavController(Mob scav, GunListener listener) {
        this.scav = scav;
        this.brain = new ScavBrain(scav);
        this.gunListener = listener;
        this.currentAimVector = scav.getEyeLocation().getDirection();
    }

    public void onTick() {
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        // 各種タイマーの減衰
        if (suppression > 0)
            suppression = Math.max(0, suppression - 0.02f);
        if (jumpCooldown > 0)
            jumpCooldown--;
        if (strafeTicks > 0)
            strafeTicks--;
        if (coverStayTicks > 0)
            coverStayTicks--;
        if (coverSearchCooldown > 0)
            coverSearchCooldown--;

        // --- 1. 分隊ロジック & スイッチング更新 ---
        lastSquadUpdate++;
        if (lastSquadUpdate >= 10) { // 0.5秒ごとに役割を再評価
            updateNearbyAllies();
            handleSquadRoles();
            lastSquadUpdate = 0;
        }

        // --- 2. 能動的な索敵 & 情報共有 ---
        if (target == null) {
            target = scanForTargets();
            if (target != null) {
                scav.setTarget(target);
                shareTargetWithAllies(target.getLocation());
            }
        }

        if (target != null) {
            canSeeTarget = checkVision(target);
            if (canSeeTarget) {
                lastKnownLocation = target.getLocation();
                searchTicks = 0;
                isAlerted = true;
                isPreAiming = false;
                updateHumanAim(target);

                if (Bukkit.getCurrentTick() % 10 == 0) {
                    shareTargetWithAllies(lastKnownLocation);
                }
            } else {
                handleSearching();
                // 視認していないがターゲットが存在し、最後に見失った場所がある場合はプリエイム
                if (lastKnownLocation != null && !isSprinting) {
                    isPreAiming = true;
                    updatePreAim(lastKnownLocation);
                } else {
                    isPreAiming = false;
                }
            }
        } else {
            isPreAiming = false;
            handleSearching();
        }

        // 装備中の銃のステータス取得
        ItemStack item = scav.getEquipment().getItemInMainHand();
        String itemId = (item != null && item.hasItemMeta()) ? 
            item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) {
            return;
        }

        // 状況の判断
        int currentAmmo = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean needsReload = currentAmmo <= 0;
        double healthPercent = scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        // --- 3. スイッチングの意思決定 (前衛がピンチなら後衛と交代) ---
        if (myRole == SquadRole.POINTMAN && (suppression > 0.8f || needsReload || healthPercent < 0.4)) {
            requestRoleSwitch();
        }

        // --- 4. タクティカル・カバー検索の意思決定 ---
        if (target != null && coverSearchCooldown <= 0) {
            if (suppression > 0.6f || healthPercent < 0.4 || needsReload) {
                tacticalCoverLoc = findCover(target);
                coverSearchCooldown = 60;
                if (tacticalCoverLoc != null) coverStayTicks = 100;
            }
        }

        // ダッシュ状態の判定
        float aggression = brain.getAdrenaline();
        float fear = brain.getFrustration();
        if (nearbyAllies.size() >= 2)
            fear *= 0.7f;

        this.isSprinting = aggression > 0.6f || fear > 0.7f || peekPhase > 0 || tacticalCoverLoc != null;

        // --- 5. タクティカル・アドバイスの計算 ---
        float tacticalAdvice = 0.0f;
        boolean hasLos = target != null && scav.hasLineOfSight(target);
        if (!hasLos && lastKnownLocation != null) {
            tacticalAdvice = 0.8f; 
        } else if (suppression > 0.5f) {
            tacticalAdvice = 0.5f; 
        }

        // --- 6. Peek and Hide Maneuver (Priority Override) ---
        if (peekPhase > 0) {
            peekTicks++;
            if (peekPhase == 1) { // 飛び出し
                double speed = isSprinting ? 1.5 : 1.0;
                scav.getPathfinder().moveTo(peekLocation, speed);
                boolean currentLos = target != null && scav.hasLineOfSight(target);
                if (currentLos || peekTicks >= 5) {
                    if (currentLos) updateHumanAim(target);
                    applyAimToEntity();
                    
                    long now = System.currentTimeMillis();
                    long interval = (long) (60000.0 / def.gunStats.rpm);
                    if (now - lastMobShotTime >= interval) {
                        gunListener.executeMobShoot(scav, def.gunStats, 1, 0.1 + (suppression * 0.1));
                        lastMobShotTime = now;
                        peekPhase = 2;
                        peekTicks = 0;
                    }
                }
            } else if (peekPhase == 2) { // 戻り
                double speed = isSprinting ? 1.5 : 1.0;
                scav.getPathfinder().moveTo(coverLocation, speed);
                if (scav.getLocation().distance(coverLocation) < 1.0 || peekTicks >= 5) {
                    peekPhase = 0;
                }
            }
            brain.decide(canSeeTarget ? target : null, def.gunStats, suppression, tacticalAdvice);
            return;
        }

        // --- 7. AI思考 & 移動実行 ---
        brain.updateConditions(healthPercent < 0.3, needsReload, suppression > 0.5f, tacticalAdvice > 0.5f);
        int[] actions = brain.decide(canSeeTarget ? target : null, def.gunStats, suppression, tacticalAdvice);
        if (actions.length < 2) return;

        int moveAction = actions[0];
        float[] neurons = brain.getNeuronStates();
        float agg = neurons[0], f = neurons[1], tac = neurons[2];

        if (f > 0.7f || (suppression > 0.8f && agg < 0.4f)) {
            moveAction = (Math.random() > 0.5) ? 3 : 4;
        }
        if (tac > 0.7f && !hasLos && lastKnownLocation != null) {
            if (agg > 0.6f && Math.random() > 0.6) {
                moveAction = 7; 
            } else if (Math.random() > 0.8) {
                moveAction = 6; 
            }
        }

        // 移動の実行
        if (tacticalCoverLoc != null && coverStayTicks > 0) {
            double distToCover = scav.getLocation().distance(tacticalCoverLoc);
            if (distToCover > 1.0) {
                scav.getPathfinder().moveTo(tacticalCoverLoc, isSprinting ? 1.5 : 1.0);
            } else {
                if (needsReload) {
                    scav.setRotation(scav.getLocation().getYaw(), 0);
                } else if (canSeeTarget) {
                    applyAimToEntity();
                }
            }
            if (suppression < 0.2f && healthPercent > 0.6 && !needsReload && Math.random() < 0.05) {
                tacticalCoverLoc = null;
            }
        } else if (canSeeTarget) {
            handleCombatMovement(moveAction, target);
        } else if (lastKnownLocation != null) {
            if (moveAction == 7) {
                coverLocation = scav.getLocation().clone();
                Vector toTarget = lastKnownLocation.toVector().subtract(coverLocation.toVector()).normalize();
                Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
                if (Math.random() > 0.5) tangent.multiply(-1);
                peekLocation = coverLocation.clone().add(tangent.multiply(2.0));
                peekPhase = 1;
                peekTicks = 0;
                scav.getPathfinder().moveTo(peekLocation, isSprinting ? 1.5 : 1.0);
            } else {
                handleSearching(); 
            }
        }

        // 射撃実行
        if (actions[1] == 0) {
            long now = System.currentTimeMillis();
            long interval = (long) (60000.0 / def.gunStats.rpm);

            if (canSeeTarget) {
                applyAimToEntity();
                if (now - lastMobShotTime >= interval) {
                    double dynamicInaccuracy = 0.04 + (suppression * 0.1);
                    if (scav.getVelocity().length() > 0.1) dynamicInaccuracy += 0.04;
                    gunListener.executeMobShoot(scav, def.gunStats, 1, dynamicInaccuracy);
                    lastMobShotTime = now;
                }
            } else if (isPreAiming && Math.random() < 0.05) {
                // プリエイム中に「決め撃ち」を低確率で行う
                applyAimToEntity();
                if (now - lastMobShotTime >= interval) {
                    gunListener.executeMobShoot(scav, def.gunStats, 1, 0.3);
                    lastMobShotTime = now;
                }
            } else if (target != null && scav.isOnGround() && jumpCooldown <= 0 && checkJumpShotVision(target)) {

                if (Math.random() < 0.4) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
                    jumpCooldown = 40;
                }
            }
        }
    }

    private void handleSquadRoles() {
        if (nearbyAllies.isEmpty()) {
            myRole = SquadRole.NONE;
            return;
        }

        ScavController closestAlly = null;
        double minDist = Double.MAX_VALUE;
        for (ScavController ally : nearbyAllies) {
            double d = scav.getLocation().distance(ally.scav.getLocation());
            if (d < minDist) {
                minDist = d;
                closestAlly = ally;
            }
        }

        if (closestAlly != null && minDist < 6.0) {
            if (myRole == SquadRole.NONE) {
                double myDistToTarget = (scav.getTarget() != null) ? scav.getLocation().distance(scav.getTarget().getLocation()) : 100;
                double allyDistToTarget = (closestAlly.scav.getTarget() != null) ? closestAlly.scav.getLocation().distance(closestAlly.scav.getTarget().getLocation()) : 100;
                
                if (myDistToTarget < allyDistToTarget) {
                    myRole = SquadRole.POINTMAN;
                    closestAlly.myRole = SquadRole.COVERMAN;
                } else {
                    myRole = SquadRole.COVERMAN;
                    closestAlly.myRole = SquadRole.POINTMAN;
                }
            }
        } else {
            myRole = SquadRole.NONE;
        }
    }

    private void requestRoleSwitch() {
        for (ScavController ally : nearbyAllies) {
            if (scav.getLocation().distance(ally.scav.getLocation()) < 8.0 && ally.myRole == SquadRole.COVERMAN) {
                this.myRole = SquadRole.COVERMAN;
                ally.myRole = SquadRole.POINTMAN;
                this.tacticalCoverLoc = findCover(scav.getTarget());
                this.coverStayTicks = 60;
                break;
            }
        }
    }

    private void updatePreAim(Location targetLoc) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = targetLoc.clone().add(0, 1.5, 0).toVector().subtract(eye.toVector()).normalize();
        
        if (currentAimVector == null) currentAimVector = idealDir.clone();

        // プリエイム時は慎重に狙いを定める
        double lerpFactor = 0.3; 
        currentAimVector = currentAimVector.clone().add(idealDir.clone().subtract(currentAimVector).multiply(lerpFactor)).normalize();
        
        applyAimToEntity();
    }

    private Location findCover(LivingEntity target) {
        if (target == null) return null;
        Location sLoc = scav.getLocation();
        Location tLoc = target.getEyeLocation();
        World world = scav.getWorld();

        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = -1; y <= 2; y++) {
                    Location checkLoc = sLoc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType().isSolid()) continue;
                    if (!checkLoc.clone().add(0, -1, 0).getBlock().getType().isSolid()) continue;

                    Location eyeAtCheck = checkLoc.clone().add(0, 1.6, 0);
                    Vector toTarget = tLoc.toVector().subtract(eyeAtCheck.toVector());
                    var result = world.rayTraceBlocks(eyeAtCheck, toTarget.normalize(), toTarget.length(), 
                        org.bukkit.FluidCollisionMode.NEVER, true);
                    
                    if (result != null && result.getHitBlock() != null) {
                        return checkLoc;
                    }
                }
            }
        }
        return null;
    }

    private void updateNearbyAllies() {
        nearbyAllies.clear();
        for (Entity e : scav.getNearbyEntities(20, 10, 20)) {
            if (e instanceof Mob mob && !e.equals(scav)) {
                ScavController controller = ScavSpawner.getController(e.getUniqueId());
                if (controller != null) nearbyAllies.add(controller);
            }
        }
    }

    private void shareTargetWithAllies(Location loc) {
        double distToTarget = scav.getLocation().distance(loc);
        for (ScavController ally : nearbyAllies) {
            if (ally.scav.getTarget() != null && ally.scav.hasLineOfSight(ally.scav.getTarget())) continue;
            double errorRange = Math.min(8.0, distToTarget * 0.15);
            double offsetX = (Math.random() - 0.5) * 2.0 * errorRange;
            double offsetZ = (Math.random() - 0.5) * 2.0 * errorRange;
            ally.lastKnownLocation = loc.clone().add(offsetX, 0, offsetZ);
            ally.isAlerted = true;
            if (ally.searchTicks > 200) ally.searchTicks = 200; 
        }
    }

    private void updateHumanAim(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        if (currentAimVector == null) currentAimVector = idealDir.clone();
        double lerpFactor = 0.7 - (suppression * 0.2); 
        currentAimVector = currentAimVector.clone().add(idealDir.clone().subtract(currentAimVector).multiply(lerpFactor)).normalize();
        aimErrorYaw += (Math.random() - 0.5) * 0.04;
        aimErrorPitch += (Math.random() - 0.5) * 0.04;
        if (suppression > 0.3) {
            aimErrorYaw += (Math.random() - 0.5) * suppression * 0.15;
            aimErrorPitch += (Math.random() - 0.5) * suppression * 0.15;
        }
        aimErrorYaw *= 0.4;
        aimErrorPitch *= 0.4;
    }

    private LivingEntity scanForTargets() {
        // Y軸の範囲を拡大 (16 -> 64)
        for (Entity e : scav.getNearbyEntities(MAX_VISION_DISTANCE, 64, MAX_VISION_DISTANCE)) {
            if (e instanceof org.bukkit.entity.Player p) {
                if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL && checkVision(p)) return p;
            }
        }
        return null;
    }

    private void handleSearching() {
        if (lastKnownLocation == null) {
            if (isAlerted && Math.random() < 0.05) {
                float yaw = scav.getLocation().getYaw() + (float) (Math.random() - 0.5) * 90f;
                // 上下も少し見渡す
                float pitch = (float) (Math.random() - 0.5) * 40f;
                scav.setRotation(yaw, pitch);
            }
            return;
        }
        double dist = scav.getLocation().distance(lastKnownLocation);
        double speed = isSprinting ? 1.4 : 1.0;
        if (dist < 2.5) {
            cornerCheckTicks++;
            if (cornerCheckTicks < 80) {
                float angle = (cornerCheckTicks < 40) ? 70f : -70f;
                Location loc = scav.getLocation();
                Vector dir = lastKnownLocation.toVector().subtract(loc.toVector()).normalize();
                float baseYaw = loc.setDirection(dir).getYaw();
                scav.setRotation(baseYaw + angle, 0);
            } else {
                lastKnownLocation = null;
                cornerCheckTicks = 0;
                isAlerted = false;
            }
        } else {
            if (searchTicks == 0) {
                Vector toLast = lastKnownLocation.toVector().subtract(scav.getLocation().toVector()).normalize();
                Vector flank = new Vector(-toLast.getZ(), 0, toLast.getX()).multiply(5.0 * (Math.random() > 0.5 ? 1 : -1));
                scav.getPathfinder().moveTo(lastKnownLocation.clone().add(flank), speed);
            } else if (searchTicks > 20) {
                scav.getPathfinder().moveTo(lastKnownLocation, speed);
            }
            searchTicks++;
            if (searchTicks > 400) {
                lastKnownLocation = null;
                isAlerted = false;
            }
        }
    }

    public void onSoundHeard(Location source) {
        if (scav.getTarget() == null || !scav.hasLineOfSight(scav.getTarget())) {
            isAlerted = true;
            lastKnownLocation = source.clone();
            Vector dir = source.toVector().subtract(scav.getEyeLocation().toVector()).normalize();
            Location loc = scav.getLocation();
            loc.setDirection(dir);
            scav.setRotation(loc.getYaw(), 0);
        }
    }

    private void applyAimToEntity() {
        if (currentAimVector == null) return;
        Location loc = scav.getLocation();
        Vector finalDir = currentAimVector.clone();
        float yaw = loc.setDirection(finalDir).getYaw() + (float) aimErrorYaw;
        float pitch = loc.setDirection(finalDir).getPitch() + (float) aimErrorPitch;
        scav.setRotation(yaw, pitch);
    }

    private boolean checkJumpShotVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location jumpEye = eye.clone().add(0, 1.2, 0);
        Vector direction = target.getEyeLocation().toVector().subtract(jumpEye.toVector());
        var result = jumpEye.getWorld().rayTraceBlocks(jumpEye, direction.normalize(), direction.length(),
                org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null;
    }

    private boolean checkVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        double dist = eye.distance(targetLoc);
        if (dist > MAX_VISION_DISTANCE) return false;

        // --- 1. 発砲状態の確認 ---
        boolean isFiring = false;
        if (target.hasMetadata("last_fired_tick")) {
            int lastFired = target.getMetadata("last_fired_tick").get(0).asInt();
            if (Bukkit.getCurrentTick() - lastFired < 5) {
                isFiring = true;
            }
        }

        // --- 2. 視野角(FOV)チェック ---
        Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
        Vector direction = eye.getDirection();
        double angle = direction.angle(toTarget) * 180 / Math.PI;
        double currentFov = isFiring ? 200.0 : FOV_ANGLE;
        if (angle > currentFov / 2.0) return false;

        // --- 3. 明るさと隠密性の計算 ---
        double visibility = 1.0;
        int light = targetLoc.getBlock().getLightLevel();
        if (light < 4) visibility *= 0.2;
        else if (light < 8) visibility *= 0.5;
        else if (light < 12) visibility *= 0.8;

        if (target instanceof org.bukkit.entity.Player p && p.isSneaking()) visibility *= 0.6;
        if (target.getVelocity().lengthSquared() > 0.05) visibility *= 1.2;
        if (isFiring) visibility = 5.0; 

        double effectiveRange = MAX_VISION_DISTANCE * visibility;
        if (dist > effectiveRange) return false;

        return hasAdvancedLoS(target);
    }

    private boolean hasAdvancedLoS(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        World world = scav.getWorld();
        
        double h = target.getHeight();
        double w = target.getWidth() * 0.45;

        // SCAVから見た横方向のベクトル
        Vector toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        Vector leftVec = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize().multiply(w);
        Vector rightVec = leftVec.clone().multiply(-1);

        // 9地点走査
        List<Location> checkPoints = new ArrayList<>();
        Location base = target.getLocation();
        double[] heights = { h * 0.9, h * 0.5, h * 0.1 };

        for (double y : heights) {
            Location center = base.clone().add(0, y, 0);
            checkPoints.add(center);
            checkPoints.add(center.clone().add(leftVec));
            checkPoints.add(center.clone().add(rightVec));
        }

        for (Location targetPoint : checkPoints) {
            Vector direction = targetPoint.toVector().subtract(eye.toVector());
            double maxDist = direction.length();
            var result = world.rayTraceBlocks(eye, direction.normalize(), maxDist, 
                org.bukkit.FluidCollisionMode.NEVER, true);
            if (result == null || result.getHitBlock() == null) return true;
        }
        return false;
    }

    private void handleCombatMovement(int action, LivingEntity target) {
        Location sLoc = scav.getLocation();
        Location tLoc = target.getLocation();
        Vector toTarget = tLoc.toVector().subtract(sLoc.toVector()).normalize();
        double dist = sLoc.distance(tLoc);
        if (strafeTicks <= 0) {
            strafeDir = (Math.random() > 0.5) ? 1 : -1;
            strafeTicks = 10 + (int) (Math.random() * 20);
        }
        Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        Vector moveVec = new Vector(0, 0, 0);
        Vector repulsion = new Vector(0, 0, 0);
        for (ScavController ally : nearbyAllies) {
            double allyDist = sLoc.distance(ally.scav.getLocation());
            if (allyDist < 4.0) {
                repulsion.add(sLoc.toVector().subtract(ally.scav.getLocation().toVector()).normalize().multiply(4.0 - allyDist));
            }
        }
        switch (action) {
            case 0:
            case 1:
                double flankWeight = 0.5;
                for (ScavController ally : nearbyAllies) {
                    if (ally.scav.getTarget() != null && ally.scav.hasLineOfSight(ally.scav.getTarget())) {
                        flankWeight = 1.2;
                        break;
                    }
                }
                moveVec = toTarget.clone().add(tangent.clone().multiply(strafeDir * flankWeight));
                break;
            case 2:
                moveVec = toTarget.clone().multiply(-1).add(tangent.clone().multiply(strafeDir * 0.3));
                break;
            case 3:
            case 4:
                double targetDist = 12.0;
                double distError = dist - targetDist;
                moveVec = tangent.clone().multiply(strafeDir).add(toTarget.clone().multiply(distError * 0.2));
                break;
            case 5:
                if (scav.isOnGround() && jumpCooldown <= 0) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)).add(tangent.clone().multiply(strafeDir * 0.5)));
                    jumpCooldown = 60;
                }
                return;
        }
        if (myRole == SquadRole.COVERMAN) {
            moveVec.add(tangent.clone().multiply(strafeDir * 1.5));
            moveVec.add(toTarget.clone().multiply(-0.5)); 
        }
        if (moveVec.lengthSquared() > 0 || repulsion.lengthSquared() > 0) {
            Vector finalMove = moveVec.add(repulsion).normalize();
            Location dest = sLoc.clone().add(finalMove.multiply(1.5));
            scav.getPathfinder().moveTo(dest, isSprinting ? 1.5 : 1.0);
        }
    }

    public void onDamage(Entity attacker) {
        suppression = Math.min(1.0f, suppression + 0.3f);
        if (attacker instanceof LivingEntity living) {
            Location loc = scav.getLocation();
            loc.setDirection(living.getLocation().toVector().subtract(loc.toVector()).normalize());
            scav.teleport(loc);
            if (scav.getTarget() == null) {
                scav.setTarget(living);
                lastKnownLocation = living.getLocation();
                shareTargetWithAllies(lastKnownLocation);
            }
        }
    }

    public void addSuppression(float amount) {
        this.suppression = Math.min(1.0f, this.suppression + amount);
    }

    public void onDeath() {
        brain.onDeath();
    }

    public ScavBrain getBrain() {
        return brain;
    }

    public Mob getScav() {
        return this.scav;
    }
}