package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.core.*;
import com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI;
import com.lunar_prototype.impossbleEscapeMC.loot.event.*;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** SCAVだけの短時間ラグドール。中身の所有者は既存Mannequinのまま。 */
public final class ScavRagdollModule implements IModule,Listener {
    private record Impact(Vector3d direction,int tick) {}
    private final ImpossbleEscapeMC plugin;
    private final Map<UUID,Impact> impacts=new HashMap<>();
    private final Map<UUID,Integer> deaths=new HashMap<>();
    private final Map<UUID,Actor> actors=new HashMap<>();
    private final ArrayDeque<UUID> active=new ArrayDeque<>();
    private NamespacedKey corpseKey;
    private BukkitTask task;
    private int maxActive,maxCorpses,animationTicks;
    private long budgetNanos;
    private boolean debug;
    public ScavRagdollModule(ImpossbleEscapeMC plugin){this.plugin=plugin;}
    @Override public void onEnable(ServiceContainer services) {
        if(!plugin.getConfig().getBoolean("scav-ragdoll.enabled",true))return;
        corpseKey=new NamespacedKey(plugin,"ragdoll_corpse");
        debug=plugin.getConfig().getBoolean("scav-ragdoll.debug",false);
        maxActive=Math.clamp(plugin.getConfig().getInt("scav-ragdoll.max-active",6),1,16);
        maxCorpses=Math.clamp(plugin.getConfig().getInt("scav-ragdoll.max-corpses",64),1,256);
        animationTicks=Math.clamp(plugin.getConfig().getInt("scav-ragdoll.max-animation-ticks",48),20,80);
        double millis=plugin.getConfig().getDouble("scav-ragdoll.millis-per-tick",3);
        budgetNanos=(long)((Double.isFinite(millis)?Math.clamp(millis,.5,10):3)*1_000_000);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        plugin.getLogger().info("SCAV ragdoll enabled (active="+maxActive+", corpses="+maxCorpses+")");
    }
    @Override public void onDisable() {
        if(task!=null)task.cancel();task=null;
        for(Actor actor:List.copyOf(actors.values()))fallback(actor);
        active.clear();impacts.clear();deaths.clear();HandlerList.unregisterAll(this);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void impact(EntityDamageByEntityEvent event) {
        if(!ScavSpawner.isScav(event.getEntity().getUniqueId())||event.getFinalDamage()<=0)return;
        Entity shooter=DatapackGunnerManager.resolveShooter(event.getDamager());
        Vector3d direction;
        if(event.getDamager() instanceof Projectile projectile)direction=new Vector3d(projectile.getVelocity().getX(),projectile.getVelocity().getY(),projectile.getVelocity().getZ());
        else if(shooter!=null&&shooter.getWorld()==event.getEntity().getWorld()) {
            var from=shooter.getLocation();var to=event.getEntity().getLocation();
            direction=new Vector3d(to.getX()-from.getX(),0,to.getZ()-from.getZ());
        } else direction=new Vector3d();
        impacts.put(event.getEntity().getUniqueId(),new Impact(direction,Bukkit.getCurrentTick()));
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void death(EntityDeathEvent event) {
        // SpawnerがControllerを外す前にSCAVであることを記録する。プレイヤー死体へは適用しない。
        if(ScavSpawner.isScav(event.getEntity().getUniqueId()))deaths.put(event.getEntity().getUniqueId(),Bukkit.getCurrentTick());
    }
    @EventHandler public void created(CorpseCreatedEvent event) {
        UUID id=event.getVictim().getUniqueId();Integer died=deaths.remove(id);
        if(died==null||Bukkit.getCurrentTick()-died>2){trace("Skipped: SCAV death marker missing or stale");return;}
        Impact impact=impacts.remove(id);
        if(actors.size()>=maxCorpses||active.size()>=maxActive){trace("Skipped: active/corpse limit");return;}
        if(!supported(event.getInventory())){trace("Skipped: unsupported equipment in corpse slots 1-3");return;}
        Actor actor=null;
        try {
            actor=new Actor(event,impact);actors.put(event.getCorpse().getUniqueId(),actor);
            actor.equip(event.getInventory());actor.render(false);
            actor.corpse.setAI(false);actor.corpse.setGravity(false);actor.corpse.setCollidable(false);actor.corpse.setInvulnerable(true);
            actor.corpse.setVisibleByDefault(false);
            for(Player p:Bukkit.getOnlinePlayers()){p.hideEntity(plugin,actor.corpse);p.hideEntity(plugin,event.getVictim());}
            event.getVictim().setVisibleByDefault(false);
            active.addLast(actor.corpse.getUniqueId());
            trace("Created display corpse");
        } catch(RuntimeException ex) {
            if(actor!=null)fallback(actor);
            plugin.getLogger().log(java.util.logging.Level.WARNING,"SCAV死体の表示を通常の死体へ戻しました",ex);
        }
    }
    private void trace(String message){if(debug)plugin.getLogger().info("[SCAV ragdoll] "+message);}
    private static String itemId(ItemStack item) {
        return item!=null&&item.hasItemMeta()?item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID,PDCKeys.STRING):null;
    }
    private static boolean present(ItemStack item){return item!=null&&!item.isEmpty();}
    private static boolean supported(Inventory inventory) {
        // 未対応の防具を黙って消さない。通常SCAVの装備をまず対応範囲とする。
        if(present(inventory.getItem(2))||present(inventory.getItem(3)))return false;
        ItemStack chest=inventory.getItem(1);
        return !present(chest)||Set.of("Slick","Trooper","MF-UNTAR").contains(Objects.toString(itemId(chest),""));
    }
    @EventHandler public void equipment(CorpseAppearanceEvent event) {
        Actor actor=actors.get(event.getCorpse().getUniqueId());if(actor==null)return;
        try {actor.equip(event.getInventory());actor.render(false);}
        catch(RuntimeException ex){fallback(actor);}
    }
    private void tick() {
        int now=Bukkit.getCurrentTick();
        if(now%20==0) {
            impacts.entrySet().removeIf(e->now-e.getValue().tick>100);
            deaths.entrySet().removeIf(e->now-e.getValue()>2);
            for(Actor actor:List.copyOf(actors.values()))if(!actor.corpse.isValid()||!actor.handle.isValid())remove(actor,true);
        }
        long start=System.nanoTime();int count=active.size();
        while(count-->0&&!active.isEmpty()) {
            if(System.nanoTime()-start>=budgetNanos)break;
            UUID id=active.removeFirst();Actor actor=actors.get(id);if(actor==null)continue;
            if(!actor.corpse.isValid()){remove(actor,false);continue;}
            if(now-actor.born<2){active.addLast(id);continue;}
            try {
                actor.physics.step(new RagdollTerrain(actor.world));
                if(!actor.physics.valid()||now-actor.born>160){fallback(actor);continue;}
                actor.render(true);
                if(actor.physics.sleeping()||actor.physics.ticks()>=animationTicks)actor.physics.freeze();
                else active.addLast(id);
            } catch(RuntimeException ex) {
                fallback(actor);plugin.getLogger().log(java.util.logging.Level.WARNING,"SCAV死体の物理更新を中止しました",ex);
            }
        }
    }
    private final class Visual {
        final ItemDisplay display;final RagdollPhysics.Part part;final Vector3f offset;final Quaternionf rotation;final float scale;
        Visual(Actor actor,RagdollPhysics.Part part,ItemStack item,ItemDisplay.ItemDisplayTransform context,Vector3f offset,Quaternionf rotation,float scale) {
            this.part=part;this.offset=offset;this.rotation=rotation;this.scale=scale;
            display=actor.world.spawn(actor.origin,ItemDisplay.class,d->{
                d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);d.setItemDisplayTransform(context);d.setItemStack(item.clone());
                d.setDisplayWidth(12);d.setDisplayHeight(12);d.setViewRange(1);d.setShadowRadius(0);
                d.getPersistentDataContainer().set(corpseKey,org.bukkit.persistence.PersistentDataType.STRING,actor.corpse.getUniqueId().toString());
            });
        }
        void render(Actor actor,boolean interpolate) {
            var pose=actor.physics.pose(part);Quaternionf q=new Quaternionf(pose.rotation());
            Vector3f translation=new Vector3f((float)(pose.center().x-actor.origin.getX()),(float)(pose.center().y-actor.origin.getY()),(float)(pose.center().z-actor.origin.getZ()));
            translation.add(q.transform(new Vector3f(offset)));q.mul(rotation);
            display.setInterpolationDelay(0);display.setInterpolationDuration(interpolate?Math.min(5,Math.max(1,Bukkit.getCurrentTick()-actor.lastRendered)):0);
            display.setTransformation(new Transformation(translation,q,new Vector3f(scale),new Quaternionf()));
        }
    }
    private final class Actor {
        final Mannequin corpse;final World world;final Location origin;final RagdollPhysics physics;final Interaction handle;
        final boolean originalGravity,originalAI,originalCollidable,originalInvulnerable;
        final int born=Bukkit.getCurrentTick();int lastRendered=born;
        final List<Visual> skin=new ArrayList<>(),gear=new ArrayList<>();
        Actor(CorpseCreatedEvent event,Impact impact) {
            corpse=event.getCorpse();origin=event.getVictim().getLocation().clone();world=origin.getWorld();
            originalGravity=corpse.hasGravity();originalAI=corpse.hasAI();originalCollidable=corpse.isCollidable();originalInvulnerable=corpse.isInvulnerable();
            var v=event.getVictim().getVelocity();Vector3d direction=impact!=null&&born-impact.tick<100?impact.direction:new Vector3d();
            physics=new RagdollPhysics(new Vector3d(origin.getX(),origin.getY(),origin.getZ()),origin.getYaw(),new Vector3d(v.getX(),v.getY(),v.getZ()),direction,corpse.getUniqueId().getLeastSignificantBits());
            // 操作判定を先に作り、表示生成で失敗しても通常死体へ戻す際に片付けられるようにする。
            handle=world.spawn(origin,Interaction.class,e->{e.setPersistent(false);e.setInteractionWidth(1.1f);e.setInteractionHeight(.6f);e.setResponsive(true);e.setInvulnerable(true);
                e.getPersistentDataContainer().set(corpseKey,org.bukkit.persistence.PersistentDataType.STRING,corpse.getUniqueId().toString());});
        }
        void equip(Inventory inventory) {
            if(skin.isEmpty())for(var part:RagdollPhysics.Part.values())skin.add(new Visual(this,part,model("scav/"+part.name().toLowerCase(Locale.ROOT)),ItemDisplay.ItemDisplayTransform.NONE,new Vector3f(),new Quaternionf(),1));
            for(Visual visual:gear)visual.display.remove();gear.clear();
            if(present(inventory.getItem(0)))gear.add(new Visual(this,RagdollPhysics.Part.HEAD,inventory.getItem(0),ItemDisplay.ItemDisplayTransform.HEAD,new Vector3f(),new Quaternionf().rotateY((float)Math.PI),.625f));
            ItemStack chest=inventory.getItem(1);
            if(present(chest)) {
                String material=switch(Objects.toString(itemId(chest),"")){case "Slick"->"diamond";case "Trooper"->"iron";default->"mf_untar";};
                gear.add(new Visual(this,RagdollPhysics.Part.TORSO,model("armor/"+material+"/torso"),ItemDisplay.ItemDisplayTransform.NONE,new Vector3f(),new Quaternionf(),1));
                if(!material.equals("mf_untar"))for(var part:List.of(RagdollPhysics.Part.RIGHT_ARM,RagdollPhysics.Part.LEFT_ARM))
                    gear.add(new Visual(this,part,model("armor/"+material+"/"+part.name().toLowerCase(Locale.ROOT)),ItemDisplay.ItemDisplayTransform.NONE,new Vector3f(),new Quaternionf(),1));
            }
            for(int slot=4;slot<=5;slot++)if(present(inventory.getItem(slot))) {
                boolean right=slot==4;
                gear.add(new Visual(this,right?RagdollPhysics.Part.RIGHT_ARM:RagdollPhysics.Part.LEFT_ARM,inventory.getItem(slot),right?ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND:ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND,
                        new Vector3f(0,-.32f,-.08f),new Quaternionf().rotateX((float)-Math.PI/2),.625f));
            }
        }
        void render(boolean interpolate) {
            for(Visual visual:skin)visual.render(this,interpolate);for(Visual visual:gear)visual.render(this,interpolate);
            var center=physics.pose(RagdollPhysics.Part.TORSO).center();
            handle.teleport(new Location(world,center.x,center.y-.25,center.z));lastRendered=Bukkit.getCurrentTick();
        }
    }
    private ItemStack model(String key) {
        ItemStack stack=new ItemStack(Material.PAPER);var meta=stack.getItemMeta();meta.setItemModel(new NamespacedKey("iem","corpse/"+key));stack.setItemMeta(meta);return stack;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event){open(event);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interactAt(PlayerInteractAtEntityEvent event){open(event);}
    private void open(PlayerInteractEntityEvent event) {
        String key=event.getRightClicked().getPersistentDataContainer().get(corpseKey,org.bukkit.persistence.PersistentDataType.STRING);if(key==null)return;
        event.setCancelled(true);if(event.getHand()!=EquipmentSlot.HAND)return;
        Actor actor;try {actor=actors.get(UUID.fromString(key));}catch(IllegalArgumentException ex){return;}
        if(actor==null||!actor.corpse.isValid()||event.getPlayer().getLocation().distanceSquared(actor.handle.getLocation())>36)return;
        if(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse)return;
        plugin.getSearchGUI().open(event.getPlayer(),actor.corpse);
    }
    private void remove(Actor actor,boolean removeCorpse) {
        actors.remove(actor.corpse.getUniqueId());active.remove(actor.corpse.getUniqueId());
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse)p.closeInventory();
        for(Visual visual:actor.skin)visual.display.remove();for(Visual visual:actor.gear)visual.display.remove();actor.handle.remove();
        if(removeCorpse&&actor.corpse.isValid())actor.corpse.remove();
    }
    private void fallback(Actor actor) {
        remove(actor,false);
        if(actor.corpse.isValid()) {
            actor.corpse.setGravity(actor.originalGravity);actor.corpse.setAI(actor.originalAI);actor.corpse.setCollidable(actor.originalCollidable);
            actor.corpse.setVisibleByDefault(true);actor.corpse.setInvulnerable(actor.originalInvulnerable);
            for(Player player:Bukkit.getOnlinePlayers())player.showEntity(plugin,actor.corpse);
        }
    }
    @EventHandler public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        Actor actor=actors.get(event.getEntity().getUniqueId());if(actor!=null)remove(actor,false);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chunkUnloaded(ChunkUnloadEvent event) {
        for(Actor actor:List.copyOf(actors.values()))if(actor.world==event.getWorld()&&((actor.origin.getBlockX()>>4)==event.getChunk().getX()&&(actor.origin.getBlockZ()>>4)==event.getChunk().getZ()
                ||(actor.handle.getLocation().getBlockX()>>4)==event.getChunk().getX()&&(actor.handle.getLocation().getBlockZ()>>4)==event.getChunk().getZ()))remove(actor,true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void worldUnloaded(WorldUnloadEvent event) {
        for(Actor actor:List.copyOf(actors.values()))if(actor.world==event.getWorld())remove(actor,true);
    }
}
