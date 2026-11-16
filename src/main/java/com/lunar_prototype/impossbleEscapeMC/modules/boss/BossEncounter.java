package com.lunar_prototype.impossbleEscapeMC.modules.boss;


import com.lunar_prototype.impossbleEscapeMC.ai.ScavBossPost;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.BiPredicate;

/** レイドに1部隊だけ。近接による再抽選、通常SCAVの再湧き・遠距離消去から独立する。 */
public final class BossEncounter {
    record SpawnRequest(Location location, BossDefinition.Member member, BossIdentity identity, ScavBossPost post) {}
    private final java.util.logging.Logger logger;
    private final java.util.function.Function<SpawnRequest,UUID> spawn;
    private final java.util.function.Consumer<UUID> remove;
    private final String mapId,worldName;
    private final BossPlacement group;
    private final BossDefinition definition;
    private final BossTerritory zone;
    private final BossRoll roll;
    private final Runnable onClose;
    private final Set<UUID> members=new LinkedHashSet<>();
    private boolean closed;
    BossEncounter(java.util.logging.Logger logger, java.util.function.Function<SpawnRequest,UUID> spawn,
            java.util.function.Consumer<UUID> remove, String mapId, String worldName,
            boolean restored, BossPlacement group, BossDefinition definition,
            BossTerritory zone, double draw, Runnable onClose) {
        this.logger=logger; this.spawn=spawn; this.remove=remove; this.mapId=mapId; this.worldName=worldName;
        this.group=group; this.definition=definition; this.zone=zone; this.onClose=onClose;
        roll=new BossRoll(definition==null?0:definition.chance(),draw,group!=null,restored);
    }
    void memberDied(UUID id) { members.remove(id); }
    public void tick(BiPredicate<Player,Location> visible) {
        if(closed||!roll.waiting())return;
        World world=Bukkit.getWorld(worldName);if(world==null)return;
        List<Location> points=new ArrayList<>();
        for(String role:definition.members().stream().map(BossDefinition.Member::role).toList()) {
            double[] p=group.position(role);points.add(new Location(world,p[0],p[1],p[2],(float)p[3],(float)p[4]));
        }
        // 視線判定より先に未読込地点を除外し、レイキャストでチャンクを読み込まない。
        if(points.stream().anyMatch(p->!BossSpawnSafety.safePosition(p)))return;
        boolean nearby=false;
        for(Player player:world.getPlayers()) {
            if(player.getGameMode()==GameMode.SPECTATOR||com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.isGunner(player))continue;
            for(Location point:points) {
                double d=player.getLocation().distanceSquared(point);
                if(d<64*64)nearby=true;
                if(d<24*24||d<80*80&&(visible.test(player,point)||visible.test(player,point.clone().add(0,.9,0))||visible.test(player,point.clone().add(0,1.65,0))))return;
            }
        }
        if(!nearby)return;
        if(!roll.consume())return;
        UUID unit=UUID.randomUUID();
        try {
            members.addAll(spawnAll(definition.members().size(),i->{
                Location p=points.get(i);
                var member=definition.members().get(i);
                ScavBossPost post=new ScavBossPost(zone.minX(),zone.minY(),zone.minZ(),zone.maxX(),zone.maxY(),zone.maxZ(),p.getX(),p.getY(),p.getZ(),member.radius(),member.leader(),unit);
                return spawn.apply(new SpawnRequest(p,member,new BossIdentity(definition.id(),unit,member.role(),member.leader()),post));
            },remove));
            logger.info("[Boss] Spawned "+definition.id()+" ("+members.size()+" members) on map "+mapId);
        } catch(RuntimeException ex) {
            cleanup();logger.log(java.util.logging.Level.WARNING,"ボス部隊の生成に失敗したため、生成途中の全員を回収しました（このレイドでは再試行しません）",ex);
        }
    }
    static List<UUID> spawnAll(int count,java.util.function.IntFunction<UUID> spawn,java.util.function.Consumer<UUID> remove) {
        if(count<=0)throw new IllegalArgumentException("Empty boss group");
        List<UUID> created=new ArrayList<>();
        try {
            for(int i=0;i<count;i++) {
                UUID id=spawn.apply(i);
                if(id==null)throw new IllegalStateException("Boss equipment or ammunition unavailable");
                created.add(id);
            }
            return List.copyOf(created);
        }catch(RuntimeException failure) {
            for(UUID id:created)try{remove.accept(id);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    public void cleanup() {
        if(closed)return; closed=true;
        roll.consume();
        onClose.run();
        for(UUID id:members)try{remove.accept(id);}
        catch(RuntimeException ex){logger.log(java.util.logging.Level.WARNING,"ボス部隊員の回収に失敗しました",ex);}
        members.clear();
    }
}
