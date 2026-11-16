package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.core.*;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.*;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 編成定義と遭遇の所有者。Raidを参照せず、呼出元の終了時にも回収できる。 */
@Service
public final class BossModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final ScavSpawner scavs;
    private final Map<String,BossDefinition> definitions=BossCatalog.create();
    private final Set<BossEncounter> encounters=new HashSet<>();
    private boolean active;
    @Inject public BossModule(ImpossbleEscapeMC plugin, ScavSpawner scavs) {
        this.plugin=plugin; this.scavs=scavs;
    }
    @Override public void onEnable(ServiceContainer container) {
        active=true;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public BossDefinition definition(String id) { return definitions.get(id); }
    public Set<String> definitionIds() { return definitions.keySet(); }
    public BossEncounter open(String mapId, String worldName, String sessionId, boolean restored,
            List<BossPlacement> placements, List<BossTerritory> territories) {
        if(!active)throw new IllegalStateException("BossModule is disabled");
        Map<String,BossTerritory> zones=new HashMap<>(); territories.forEach(z->zones.put(z.id(),z));
        List<BossPlacement> eligible=placements.stream().filter(p->{
            BossDefinition d=definition(p.bossId());
            return d!=null && d.enabled() && d.maps().contains(mapId) && p.problem(d,zones.get(p.zoneId()))==null;
        }).toList();
        BossPlacement chosen=eligible.isEmpty()?null:eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        // 1セッション1抽選を維持し、複数定義でも全編成が同時に抽選されない。
        BossEncounter[] holder=new BossEncounter[1];
        BossEncounter encounter=new BossEncounter(plugin.getLogger(),
                request->scavs.spawnBossMember(request.location(),sessionId,mapId,request.member(),request.identity(),request.post()),
                scavs::removeScav,mapId,worldName,restored,chosen,
                chosen==null?null:definition(chosen.bossId()),chosen==null?null:zones.get(chosen.zoneId()),ThreadLocalRandom.current().nextDouble(),
                ()->encounters.remove(holder[0]));
        holder[0]=encounter; encounters.add(encounter); return encounter;
    }
    @EventHandler(priority=EventPriority.MONITOR) public void onDeath(EntityDeathEvent event) {
        UUID id=event.getEntity().getUniqueId();
        for(BossEncounter encounter:List.copyOf(encounters))encounter.memberDied(id);
    }
    @Override public void onDisable() {
        active=false;
        for(BossEncounter encounter:List.copyOf(encounters))encounter.cleanup();
        encounters.clear(); HandlerList.unregisterAll(this);
    }
}
