package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScavSquad {
    private static final double MAX_INFO_SHARE_RANGE_FROM_ORIGIN = 28.0;
    private static final int MAX_INFO_SHARE_HOPS = 2;

    private final ScavController owner;
    private final Mob scav;
    private final List<ScavController> nearbyAllies = new ArrayList<>();
    
    public enum SquadRole { NONE, POINTMAN, COVERMAN }
    private SquadRole myRole = SquadRole.NONE;

    public ScavSquad(ScavController owner) {
        this.owner = owner;
        this.scav = owner.getScav();
    }

    public List<ScavController> getNearbyAllies() {
        return nearbyAllies;
    }

    public SquadRole getMyRole() {
        return myRole;
    }

    public void setMyRole(SquadRole myRole) {
        this.myRole = myRole;
    }

    public void updateNearbyAllies() {
        nearbyAllies.clear();
        for (Entity e : scav.getNearbyEntities(15, 8, 15)) {
            if (e instanceof Mob mob && !e.equals(scav)) {
                ScavController controller = ScavSpawner.getController(e.getUniqueId());
                if (controller != null) nearbyAllies.add(controller);
            }
        }
    }

    public void handleSquadRoles() {
        if (nearbyAllies.isEmpty()) {
            myRole = SquadRole.NONE;
            return;
        }

        ScavController closestAlly = null;
        double minDist = Double.MAX_VALUE;
        for (ScavController ally : nearbyAllies) {
            double d = scav.getLocation().distance(ally.getScav().getLocation());
            if (d < minDist) {
                minDist = d;
                closestAlly = ally;
            }
        }

        if (closestAlly != null && minDist < 6.0) {
            if (myRole == SquadRole.NONE) {
                double myDistToTarget = (scav.getTarget() != null) ? scav.getLocation().distance(scav.getTarget().getLocation()) : 100;
                double allyDistToTarget = (closestAlly.getScav().getTarget() != null) ? closestAlly.getScav().getLocation().distance(closestAlly.getScav().getTarget().getLocation()) : 100;
                
                if (myDistToTarget < allyDistToTarget) {
                    myRole = SquadRole.POINTMAN;
                    closestAlly.getSquad().setMyRole(SquadRole.COVERMAN);
                } else {
                    myRole = SquadRole.COVERMAN;
                    closestAlly.getSquad().setMyRole(SquadRole.POINTMAN);
                }
            }
        } else {
            myRole = SquadRole.NONE;
        }
    }

    public void shareTargetWithAllies(Location loc) {
        if (nearbyAllies.isEmpty()) return;

        UUID originScavId = owner.getIntelOriginScavId();
        ScavController originController = ScavSpawner.getController(originScavId);
        Location originLocation = (originController != null) ? originController.getScav().getLocation() : scav.getLocation();
        int nextRelayDepth = owner.getIntelRelayDepth() + 1;

        if (nextRelayDepth > MAX_INFO_SHARE_HOPS) return;
        
        double distToTarget = scav.getLocation().distance(loc);
        for (ScavController ally : nearbyAllies) {
            double distToAlly = scav.getLocation().distance(ally.getScav().getLocation());

            if (ally.getScav().getUniqueId().equals(originScavId)) continue;

            double distFromOrigin = originLocation.distance(ally.getScav().getLocation());
            if (distFromOrigin > MAX_INFO_SHARE_RANGE_FROM_ORIGIN) continue;
            
            // 1. 物理的距離制限 (15m以上は叫び声が届かない)
            if (distToAlly > 15.0) continue;

            // 2. 遮蔽物の考慮 (視線が通っていない場合、6m以上離れていると声が届かない)
            boolean hasLosToAlly = scav.hasLineOfSight(ally.getScav());
            if (!hasLosToAlly && distToAlly > 6.0) continue;

            // 既に自力でターゲットを視認している味方は情報を上書きしない
            if (ally.getScav().getTarget() != null && ally.getScav().hasLineOfSight(ally.getScav().getTarget())) continue;

            // 3. 情報の不確実性の向上 (伝言ゲームによる誤差)
            double baseError = Math.min(8.0, distToTarget * 0.15);
            double multiplier = 1.0;
            if (!hasLosToAlly) multiplier *= 2.0; // 壁越しなら聞き取りにくい
            if (distToAlly > 10.0) multiplier *= 1.5; // 距離があるなら不正確
            
            double errorRange = baseError * multiplier;
            double offsetX = (Math.random() - 0.5) * 2.0 * errorRange;
            double offsetZ = (Math.random() - 0.5) * 2.0 * errorRange;
            
            ally.receiveSharedTarget(loc.clone().add(offsetX, 0, offsetZ), originScavId, nextRelayDepth);
            
            // 不確実な情報（壁越しや遠距離）の場合は索敵をあきらめるのも早くする
            int searchThreshold = (multiplier > 1.0) ? 100 : 200;
            if (ally.getSearchTicks() > searchThreshold) ally.setSearchTicks(searchThreshold); 
        }
    }

    public void requestRoleSwitch() {
        for (ScavController ally : nearbyAllies) {
            if (scav.getLocation().distance(ally.getScav().getLocation()) < 8.0 && ally.getSquad().getMyRole() == SquadRole.COVERMAN) {
                this.myRole = SquadRole.COVERMAN;
                ally.getSquad().setMyRole(SquadRole.POINTMAN);
                // コントローラー側でカバー検索をトリガーさせる
                break;
            }
        }
    }
}
