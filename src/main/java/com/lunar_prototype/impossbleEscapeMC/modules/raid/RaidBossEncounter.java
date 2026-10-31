package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBossPost;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;

/** レイドに1部隊だけ。近接による再抽選、通常SCAVの再湧き・遠距離消去から独立する。 */
final class RaidBossEncounter {
    private final ImpossbleEscapeMC plugin;
    private final String mapId,worldName,session;
    private final RaidBossGroup group;
    private final RaidRadioZone zone;
    private final RaidBossRoll roll;
    private final List<UUID> members=new ArrayList<>();
    RaidBossEncounter(ImpossbleEscapeMC plugin,RaidMap map,String session,boolean restored) {
        this.plugin=plugin;mapId=map.getMapId();worldName=map.getWorldName();this.session=session;
        List<RaidBossGroup> eligible=new ArrayList<>();
        for(var g:map.bossGroups()) {
            RaidRadioZone z=map.highThreatZones().stream().filter(v->v.id().equals(g.zoneId())).findFirst().orElse(null);
            if(g.problem(z)==null)eligible.add(g);
        }
        var random=ThreadLocalRandom.current();
        group=eligible.isEmpty()?null:eligible.get(random.nextInt(eligible.size()));
        zone=group==null?null:map.highThreatZones().stream().filter(v->v.id().equals(group.zoneId())).findFirst().orElse(null);
        boolean enabled=plugin.getConfig().getBoolean("raid-boss.enabled",true)
                &&mapId.equals(plugin.getConfig().getString("raid-boss.map-id","ノヴォザリエ薬品工場"));
        roll=new RaidBossRoll(plugin.getConfig().getDouble("raid-boss.chance",.125),random.nextDouble(),enabled&&group!=null,restored);
    }
    void tick(BiPredicate<Player,Location> visible) {
        if(!roll.waiting())return;
        World world=Bukkit.getWorld(worldName);if(world==null)return;
        List<Location> points=new ArrayList<>();
        for(String role:RaidBossGroup.ROLES) {
            double[] p=group.position(role);points.add(new Location(world,p[0],p[1],p[2],(float)p[3],(float)p[4]));
        }
        // 視線判定より先に未読込地点を除外し、レイキャストでチャンクを読み込まない。
        if(points.stream().anyMatch(p->!safePosition(p)))return;
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
            members.addAll(spawnAll(i->{
                Location p=points.get(i);
                ScavBossPost post=new ScavBossPost(zone.minX(),zone.minY(),zone.minZ(),zone.maxX(),zone.maxY(),zone.maxZ(),p.getX(),p.getY(),p.getZ(),i==0?8:12,i==0,unit);
                return plugin.getScavSpawner().spawnBossMember(p,session,mapId,RaidBossGroup.ROLES.get(i),post);
            },id->plugin.getScavSpawner().removeScav(id)));
            plugin.getLogger().info("[Raid boss] Spawned boss and two guards on map "+mapId);
        } catch(RuntimeException ex) {
            cleanup();plugin.getLogger().log(java.util.logging.Level.WARNING,"ボス部隊の生成に失敗したため、生成途中の全員を回収しました（このレイドでは再試行しません）",ex);
        }
    }
    static List<UUID> spawnAll(java.util.function.IntFunction<UUID> spawn,java.util.function.Consumer<UUID> remove) {
        List<UUID> created=new ArrayList<>();
        try {
            for(int i=0;i<3;i++) {
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
    /** 未読込チャンクは触らない。幅・頭上と足元のVoxelShapeを確認する。 */
    static boolean safePosition(Location p) {
        World w=p.getWorld();if(w==null||!Double.isFinite(p.getX())||!Double.isFinite(p.getY())||!Double.isFinite(p.getZ()))return false;
        int minX=(int)Math.floor(p.getX()-.31),maxX=(int)Math.floor(p.getX()+.31),minZ=(int)Math.floor(p.getZ()-.31),maxZ=(int)Math.floor(p.getZ()+.31);
        int minY=(int)Math.floor(p.getY()-.08),maxY=(int)Math.floor(p.getY()+1.95);
        if(minY<w.getMinHeight()||maxY>=w.getMaxHeight())return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!w.isChunkLoaded(x>>4,z>>4))return false;
        BoundingBox body=new BoundingBox(p.getX()-.3,p.getY()+.015,p.getZ()-.3,p.getX()+.3,p.getY()+1.95,p.getZ()+.3);
        boolean support=false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            var block=w.getBlockAt(x,y,z);if(block.isLiquid())return false;
            for(var b:block.getCollisionShape().getBoundingBoxes()) {
                BoundingBox box=b.clone().shift(x,y,z);
                if(box.overlaps(body))return false;
                if(box.getMaxY()<=p.getY()+.02&&box.getMaxY()>=p.getY()-.08&&box.getMinX()<=p.getX()&&box.getMaxX()>=p.getX()&&box.getMinZ()<=p.getZ()&&box.getMaxZ()>=p.getZ())support=true;
            }
        }
        return support;
    }
    void cleanup() {
        roll.consume();
        for(UUID id:members)try{plugin.getScavSpawner().removeScav(id);}
        catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"ボス部隊員の回収に失敗しました",ex);}
        members.clear();
    }
}
