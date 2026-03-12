package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;

public class ScavSquad {
    private final Mob scav;
    private final List<ScavController> nearbyAllies = new ArrayList<>();
    
    public enum SquadRole { NONE, POINTMAN, COVERMAN }
    private SquadRole myRole = SquadRole.NONE;

    public ScavSquad(Mob scav) {
        this.scav = scav;
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
        for (Entity e : scav.getNearbyEntities(20, 10, 20)) {
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

    public void shareTargetWithAllies(Location loc, String voiceLine) {
        if (nearbyAllies.isEmpty()) return;
        
        scav.getWorld().playSound(scav.getLocation(), voiceLine, 1.0f, 1.0f);
        double distToTarget = scav.getLocation().distance(loc);
        for (ScavController ally : nearbyAllies) {
            if (ally.getScav().getTarget() != null && ally.getScav().hasLineOfSight(ally.getScav().getTarget())) continue;
            double errorRange = Math.min(8.0, distToTarget * 0.15);
            double offsetX = (Math.random() - 0.5) * 2.0 * errorRange;
            double offsetZ = (Math.random() - 0.5) * 2.0 * errorRange;
            ally.setLastKnownLocation(loc.clone().add(offsetX, 0, offsetZ));
            ally.setAlerted(true);
            if (ally.getSearchTicks() > 200) ally.setSearchTicks(200); 
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
