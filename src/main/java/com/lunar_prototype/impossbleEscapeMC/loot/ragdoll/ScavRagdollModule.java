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
    private PlayerInteractEntityEvent forwardedClick;
    private record BodyPick(Actor actor,RagdollPhysics.Part part,double distance) {}
    private final class PendingOpen {
        final Actor actor;final List<PlayerInteractEntityEvent> events=new ArrayList<>();
        PendingOpen(Actor actor){this.actor=actor;}
    }
    private final Map<UUID,PendingOpen> pendingOpens=new HashMap<>();
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
        active.clear();impacts.clear();deaths.clear();pendingOpens.clear();HandlerList.unregisterAll(this);
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
            actor.updateHandles();actor.equip(event.getInventory());actor.render(false);
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
            for(Actor actor:List.copyOf(actors.values())) {
                if(!actor.corpse.isValid()){remove(actor,true);continue;}
                try {actor.updateHandles();}
                catch(RuntimeException ex){fallback(actor);plugin.getLogger().log(java.util.logging.Level.WARNING,"SCAV死体の操作判定を通常の死体へ戻しました",ex);}
            }
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
    private final class BodyHandle {
        final RagdollPhysics.Part part;final int section;Interaction entity;
        BodyHandle(RagdollPhysics.Part part,int section){this.part=part;this.section=section;}
        void update(Actor actor) {
            var bounds=RagdollInteractionGeometry.bounds(actor.physics.pose(part),RagdollPhysics.halfSize(part),section,RagdollInteractionGeometry.sections(part));
            var bottom=bounds.bottom();Location location=new Location(actor.world,bottom.x,bottom.y,bottom.z);
            if(entity==null||!entity.isValid()) {
                entity=actor.world.spawn(location,Interaction.class,e->{e.setPersistent(false);e.setGravity(false);e.setResponsive(true);e.setInvulnerable(true);
                    e.setInteractionWidth(bounds.width());e.setInteractionHeight(bounds.height());
                    e.getPersistentDataContainer().set(corpseKey,org.bukkit.persistence.PersistentDataType.STRING,actor.corpse.getUniqueId().toString());});
            } else {
                if(Math.abs(entity.getInteractionWidth()-bounds.width())>.002)entity.setInteractionWidth(bounds.width());
                if(Math.abs(entity.getInteractionHeight()-bounds.height())>.002)entity.setInteractionHeight(bounds.height());
                if(entity.getLocation().distanceSquared(location)>1e-8)entity.teleport(location);
            }
        }
    }
    private final class Actor {
        final Mannequin corpse;final World world;final Location origin;final RagdollPhysics physics;final List<BodyHandle> handles=new ArrayList<>();
        final boolean originalGravity,originalAI,originalCollidable,originalInvulnerable;
        final int born=Bukkit.getCurrentTick();int lastRendered=born;
        final List<Visual> skin=new ArrayList<>(),gear=new ArrayList<>();
        Actor(CorpseCreatedEvent event,Impact impact) {
            corpse=event.getCorpse();origin=event.getVictim().getLocation().clone();world=origin.getWorld();
            originalGravity=corpse.hasGravity();originalAI=corpse.hasAI();originalCollidable=corpse.isCollidable();originalInvulnerable=corpse.isInvulnerable();
            var v=event.getVictim().getVelocity();Vector3d direction=impact!=null&&born-impact.tick<100?impact.direction:new Vector3d();
            physics=new RagdollPhysics(new Vector3d(origin.getX(),origin.getY(),origin.getZ()),origin.getYaw(),new Vector3d(v.getX(),v.getY(),v.getZ()),direction,corpse.getUniqueId().getLeastSignificantBits());
            // 生成途中で失敗しても、登録済みActorからすべての操作判定を回収できる。
            for(var part:RagdollPhysics.Part.values())for(int section=0;section<RagdollInteractionGeometry.sections(part);section++)
                handles.add(new BodyHandle(part,section));
        }
        void updateHandles(){for(BodyHandle handle:handles)handle.update(this);}
        Interaction interaction(RagdollPhysics.Part part,org.bukkit.util.Vector point) {
            Interaction closest=null;double distance=Double.POSITIVE_INFINITY;
            for(BodyHandle handle:handles) {
                if(handle.part!=part||handle.entity==null||!handle.entity.isValid())continue;
                var box=handle.entity.getBoundingBox();
                if(!box.clone().expand(.0001).contains(point))continue;
                double d=box.getCenter().distanceSquared(point);
                if(d<distance){closest=handle.entity;distance=d;}
            }
            return closest;
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
                // 手持ち表示の座標系を腕へ合わせる。Y半回転がないと銃口・銃床が逆になる。
                gear.add(new Visual(this,right?RagdollPhysics.Part.RIGHT_ARM:RagdollPhysics.Part.LEFT_ARM,inventory.getItem(slot),right?ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND:ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND,
                        new Vector3f(0,-.32f,-.08f),new Quaternionf().rotateX((float)-Math.PI/2).rotateY((float)Math.PI),.625f));
            }
        }
        void render(boolean interpolate) {
            for(Visual visual:skin)visual.render(this,interpolate);for(Visual visual:gear)visual.render(this,interpolate);
            updateHandles();lastRendered=Bukkit.getCurrentTick();
        }
    }
    private ItemStack model(String key) {
        ItemStack stack=new ItemStack(Material.PAPER);var meta=stack.getItemMeta();meta.setItemModel(new NamespacedKey("iem","corpse/"+key));stack.setItemMeta(meta);return stack;
    }
    private Actor actorOf(Entity entity) {
        String key=entity.getPersistentDataContainer().get(corpseKey,org.bukkit.persistence.PersistentDataType.STRING);
        if(key==null)return null;
        try{return actors.get(UUID.fromString(key));}catch(IllegalArgumentException ex){return null;}
    }
    /** 通常のInteractionと補助経路で同じ射程・外層・遮蔽条件を使う。 */
    private BodyPick pick(Player player,Actor only) {
        if(player.getGameMode()==GameMode.SPECTATOR)return null;
        var attribute=player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
        double range=attribute==null?3.0:attribute.getValue();
        if(!Double.isFinite(range)||range<=0)return null;
        Location eye=player.getEyeLocation();var direction=eye.getDirection();
        Vector3d start=new Vector3d(eye.getX(),eye.getY(),eye.getZ());
        Vector3d ray=new Vector3d(direction.getX(),direction.getY(),direction.getZ());
        Actor best=null;RagdollPhysics.Part bestPart=null;double nearest=range;
        for(Actor actor:only==null?actors.values():List.of(only)) {
            if(actor.world!=player.getWorld()||!actor.corpse.isValid())continue;
            for(var part:RagdollPhysics.Part.values()) {
                double distance=RagdollInteractionGeometry.distance(actor.physics.pose(part),RagdollPhysics.halfSize(part),start,ray,range);
                if(Double.isFinite(distance)&&distance<=nearest){nearest=distance;best=actor;bestPart=part;}
            }
        }
        if(best==null){trace("Loot click rejected: no visible body part in reach");return null;}
        var wall=player.getWorld().rayTraceBlocks(eye,direction,Math.max(nearest,.001),FluidCollisionMode.NEVER,true);
        double blocked=wall==null?Double.POSITIVE_INFINITY:wall.getHitPosition().distance(eye.toVector());
        if(!RagdollInteractionGeometry.visible(nearest,blocked,range)){trace("Loot click rejected: block occludes body");return null;}
        return new BodyPick(best,bestPart,nearest);
    }
    // 空・地面への元の拒否は解除せず、死体のエンティティ操作として保護側へ確認する。
    @EventHandler(priority=EventPriority.HIGH)
    public void interactBody(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND||!event.getAction().isRightClick()||actors.isEmpty())return;
        BodyPick picked=pick(event.getPlayer(),null);if(picked==null)return;
        var eye=event.getPlayer().getEyeLocation();
        var point=eye.toVector().add(eye.getDirection().multiply(picked.distance));
        Interaction handle=picked.actor.interaction(picked.part,point);if(handle==null)return;
        var hit=point.subtract(handle.getLocation().toVector());
        // floatの幅・高さへの丸めで、合成イベントの接触点が箱の外へ出ないようにする。
        double width=handle.getInteractionWidth()/2;
        hit.setX(Math.clamp(hit.getX(),-width,width));hit.setY(Math.clamp(hit.getY(),0,handle.getInteractionHeight()));hit.setZ(Math.clamp(hit.getZ(),-width,width));
        var forwarded=new PlayerInteractAtEntityEvent(event.getPlayer(),handle,hit,EquipmentSlot.HAND);
        PlayerInteractEntityEvent previous=forwardedClick;forwardedClick=forwarded;
        try{Bukkit.getPluginManager().callEvent(forwarded);}
        finally{forwardedClick=previous;}
        requestOpen(forwarded);
        // 死体に対する操作として許可された時だけ、元のブロック使用を抑える。
        if(!forwarded.isCancelled())event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void interact(PlayerInteractEntityEvent event){if(event!=forwardedClick)requestOpen(event);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void interactAt(PlayerInteractAtEntityEvent event){if(event!=forwardedClick)requestOpen(event);}
    private void requestOpen(PlayerInteractEntityEvent event) {
        Actor actor=actorOf(event.getRightClicked());if(actor==null)return;
        if(event.isCancelled()){trace("Loot click blocked: interaction event cancelled");return;}
        if(event.getHand()!=EquipmentSlot.HAND||pick(event.getPlayer(),actor)==null)return;
        UUID playerId=event.getPlayer().getUniqueId();PendingOpen pending=pendingOpens.get(playerId);
        if(pending!=null&&pending.actor==actor){pending.events.add(event);return;}
        pending=new PendingOpen(actor);pending.events.add(event);pendingOpens.put(playerId,pending);
        PendingOpen queued=pending;
        // 全ハンドラーの完了後に開く。INTERACT_AT/INTERACT等の重複も1回へまとめる。
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!pendingOpens.remove(playerId,queued))return;
            Player player=event.getPlayer();
            if(!player.isOnline()||actors.get(actor.corpse.getUniqueId())!=actor)return;
            if(queued.events.stream().allMatch(PlayerInteractEntityEvent::isCancelled)){trace("Loot click blocked: late interaction cancellation");return;}
            if(pick(player,actor)==null)return;
            if(player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse)return;
            plugin.getSearchGUI().open(player,actor.corpse);
            boolean opened=player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse;
            trace(opened?"Corpse loot opened":"Corpse loot did not open: inventory data/open-event cancellation");
        });
    }
    private void remove(Actor actor,boolean removeCorpse) {
        actors.remove(actor.corpse.getUniqueId());active.remove(actor.corpse.getUniqueId());
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse)p.closeInventory();
        for(Visual visual:actor.skin)visual.display.remove();for(Visual visual:actor.gear)visual.display.remove();for(BodyHandle handle:actor.handles)if(handle.entity!=null)handle.entity.remove();
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
                ||actor.handles.stream().anyMatch(h->h.entity!=null&&(h.entity.getLocation().getBlockX()>>4)==event.getChunk().getX()&&(h.entity.getLocation().getBlockZ()>>4)==event.getChunk().getZ())))remove(actor,true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void worldUnloaded(WorldUnloadEvent event) {
        for(Actor actor:List.copyOf(actors.values()))if(actor.world==event.getWorld())remove(actor,true);
    }
}
