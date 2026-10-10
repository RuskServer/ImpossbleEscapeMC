package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScavSquad {
    /** 助けを呼ぶ声が届く距離 (ブロック)。壁や床を挟むと半分 */
    private static final double HELP_CALL_RANGE = 24.0;
    /** 助けを呼び直す間隔 (tick)。既存の救援時間だけを延ばし、交戦中の枠は補充しない */
    private static final int HELP_CALL_INTERVAL_TICKS = 40;
    private static final ScavHelpEncounter.Registry HELP_ENCOUNTERS = new ScavHelpEncounter.Registry();
    private ScavHelpEncounter helpEncounter;

    private final ScavController owner;
    private final Mob scav;
    private final List<ScavController> nearbyAllies = new ArrayList<>();
    
    /** POINTMAN: 前に出て詰める役。COVERMAN: 後ろで角を押さえ、顔出しで援護する役 (ScavBrainの戦術選択に反映) */
    public enum SquadRole { NONE, POINTMAN, COVERMAN }
    private SquadRole myRole = SquadRole.NONE;
    /** 救援に向かってくれている味方 */
    private final java.util.Set<UUID> responders = new java.util.HashSet<>();
    private int lastHelpCallTick = Integer.MIN_VALUE / 2;

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

    /** 救援参加者の接敵と、同じ敵への独立した接敵で救援枠を共有する。 */
    public void enterCombat(UUID targetId) {
        helpEncounter = HELP_ENCOUNTERS.contact(helpEncounter, scav.getUniqueId(), targetId,
                org.bukkit.Bukkit.getCurrentTick());
    }

    /** 受諾時に枠を消費する。接敵・死亡・離脱しても同じ交戦中は返さない。 */
    public boolean joinHelpEncounter(ScavSquad caller) {
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (caller.helpEncounter == null || !caller.helpEncounter.recruit(scav.getUniqueId(), now)) return false;
        if (helpEncounter != null && !helpEncounter.sameAs(caller.helpEncounter)) releaseSearch();
        helpEncounter = caller.helpEncounter;
        return true;
    }

    int supportSide() { return helpEncounter == null ? 1 : helpEncounter.supportSide(scav.getUniqueId()); }

    public int responderCount() { return responders.size(); }

    Location searchPoint(ScavContactReport report) {
        if (helpEncounter == null) return null;
        return helpEncounter.searchPlan().assign(scav.getUniqueId(), report, org.bukkit.Bukkit.getCurrentTick(), point -> {
            if (point.getWorld() != scav.getWorld() || !point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) return false;
            var path = scav.getPathfinder().findPath(point);
            return path != null && path.canReachFinalPoint();
        });
    }

    void inspected(ScavContactReport report, Location point) {
        if (helpEncounter != null) helpEncounter.searchPlan().inspected(scav.getUniqueId(), report.targetId(), point,
                org.bukkit.Bukkit.getCurrentTick());
    }

    void releaseSearch() {
        if (helpEncounter != null) helpEncounter.searchPlan().release(scav.getUniqueId());
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
            // 未割り当てか、組んでいる味方と役割が重なっていたら (相手が別の味方と組み直した等) 割り振り直す
            if (myRole == SquadRole.NONE || closestAlly.getSquad().getMyRole() == myRole) {
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

    /**
     * 戦っていることと観測時点の敵情報を知らせ、近い味方を救援に呼ぶ。
     * 声は HELP_CALL_RANGE まで届き、壁や床を挟むと半分の距離までしか届かない。
     */
    public void callForHelp() {
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastHelpCallTick < HELP_CALL_INTERVAL_TICKS) return;
        lastHelpCallTick = now;

        UUID myId = scav.getUniqueId();
        responders.removeIf(id -> {
            ScavController responder = ScavSpawner.getController(id);
            return responder == null || !myId.equals(responder.getAssistCallerId());
        });
        // Keep already travelling responders assigned without spending another slot.
        for (UUID id : responders) {
            ScavController responder = ScavSpawner.getController(id);
            if (responder != null) responder.receiveHelpCall(owner);
        }
        if (helpEncounter == null || !helpEncounter.active(now)) return;

        List<ScavController> candidates = new ArrayList<>();
        for (Entity e : scav.getNearbyEntities(HELP_CALL_RANGE, HELP_CALL_RANGE / 2, HELP_CALL_RANGE)) {
            if (!(e instanceof Mob)) continue;
            ScavController ally = ScavSpawner.getController(e.getUniqueId());
            if (ally == null || ally == owner) continue;
            double distance = scav.getLocation().distance(e.getLocation());
            if (distance > HELP_CALL_RANGE) continue;
            if (distance > HELP_CALL_RANGE / 2 && !scav.hasLineOfSight(e)) continue;
            candidates.add(ally);
        }
        // 呼べる味方がいる時だけ声を上げる
        if (!candidates.isEmpty()) owner.playScavVoice(ScavVoice.CALL_HELP);
        candidates.sort(java.util.Comparator.comparingDouble(ally -> ally.getScav().getLocation().distanceSquared(scav.getLocation())));
        for (ScavController ally : candidates) {
            if (ally.receiveHelpCall(owner)) {
                responders.add(ally.getScav().getUniqueId());
                // Existing responders may change caller even when this encounter has spent all slots.
            }
        }
    }

    /**
     * 前衛を近くの援護役と交代する
     *
     * @return 交代した場合true (呼び出し側で物陰探しを始める)
     */
    public boolean requestRoleSwitch() {
        for (ScavController ally : nearbyAllies) {
            if (scav.getLocation().distance(ally.getScav().getLocation()) < 8.0 && ally.getSquad().getMyRole() == SquadRole.COVERMAN) {
                this.myRole = SquadRole.COVERMAN;
                ally.getSquad().setMyRole(SquadRole.POINTMAN);
                return true;
            }
        }
        return false;
    }
}
