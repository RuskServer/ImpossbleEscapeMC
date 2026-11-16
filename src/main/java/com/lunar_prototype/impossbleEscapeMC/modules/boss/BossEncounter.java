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
    private final boolean selected;
    private final Runnable onClose;
    private final Set<UUID> members=new LinkedHashSet<>();
    private boolean closed;
    private BossGuardPlacement search;
    private int searchCooldown;
    BossEncounter(java.util.logging.Logger logger, java.util.function.Function<SpawnRequest,UUID> spawn,
            java.util.function.Consumer<UUID> remove, String mapId, String worldName,
            boolean restored, BossPlacement group, BossDefinition definition,
            BossTerritory zone, double draw, Runnable onClose) {
        this.logger=logger; this.spawn=spawn; this.remove=remove; this.mapId=mapId; this.worldName=worldName;
        this.group=group; this.definition=definition; this.zone=zone; this.onClose=onClose;
        roll=new BossRoll(definition==null?0:definition.chance(),draw,group!=null,restored);
        selected=roll.waiting();
    }
    /** 抽選結果は生成待ち・生成失敗・討伐後も変えない。復元セッションは常に対象外。 */
    public BossDefinition selectedBoss(){return selected?definition:null;}
    void memberDied(UUID id) { members.remove(id); }
    public void tick(BiPredicate<Player,Location> visible) {
        if(closed||!roll.waiting())return;
        World world=Bukkit.getWorld(worldName);if(world==null)return;
        if(searchCooldown>0){searchCooldown--;return;}
        double[] coordinates=group.anchor(definition);
        Location anchor=new Location(world,coordinates[0],coordinates[1],coordinates[2],(float)coordinates[3],(float)coordinates[4]);
        if(!BossSpawnSafety.safePosition(anchor)){search=null;return;}
        boolean withinSearchRange=false;
        for(Player player:world.getPlayers()) {
            if(player.getGameMode()==GameMode.SPECTATOR||com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.isGunner(player))continue;
            double d=player.getLocation().distanceSquared(anchor);
            if(d<Math.pow(64+definition.guardSpawnRadius(),2))withinSearchRange=true;
            if(d<24*24||d<80*80&&(visible.test(player,anchor)||visible.test(player,anchor.clone().add(0,.9,0))||visible.test(player,anchor.clone().add(0,1.65,0))))return;
        }
        if(!withinSearchRange)return;
        if(search==null)search=new BossGuardPlacement(definition,zone,anchor);
        search.advance(24);
        if(!search.done())return;
        List<Location> points=search.points();
        if(points==null||points.stream().anyMatch(p->!BossSpawnSafety.safePosition(p))){search=null;searchCooldown=5;return;}
        // 全員分が揃うまで抽選権を消費しない。既存の目の前湧き防止は全地点に適用する。
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
        if(!search.valid()){search=null;searchCooldown=5;return;}
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
        search=null;
    }
}
