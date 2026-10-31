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
        final Actor actor;final List<LootClick> clicks=new ArrayList<>();
        PendingOpen(Actor actor){this.actor=actor;}
    }
    private record LootClick(PlayerInteractEntityEvent event,BodyHandle handle,RagdollLootTarget target) {}
    private record Diagnostic(int tick,String result) {}
    private final Map<UUID,PendingOpen> pendingOpens=new HashMap<>();
    private final Map<UUID,Diagnostic> lastClicks=new HashMap<>();
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
        active.clear();impacts.clear();deaths.clear();pendingOpens.clear();lastClicks.clear();HandlerList.unregisterAll(this);
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
    private record PickResult(BodyPick hit,String reason) {}
    /** 補助経路もクライアントと同じ操作箱へ当て、保存した部位位置の遮蔽は開く前に再確認する。 */
    private BodyPick pick(Player player,Actor only){return pick(player,only,true);}
    private BodyPick pick(Player player,Actor only,boolean report) {
        PickResult result=probe(player,only);
        if(report&&result.hit==null)remember(player,result.reason);
        return result.hit;
    }
    private PickResult probe(Player player,Actor only) {
        if(player.getGameMode()==GameMode.SPECTATOR)return new PickResult(null,"観戦中は操作できません");
        var attribute=player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
        double range=attribute==null?3.0:attribute.getValue();
        return probeInRange(player,only,range);
    }
    private PickResult probeInRange(Player player,Actor only,double range) {
        if(!Double.isFinite(range)||range<=0)return new PickResult(null,"操作射程が無効です");
        Location eye=player.getEyeLocation();var direction=eye.getDirection();
        Actor best=null;RagdollPhysics.Part bestPart=null;double nearest=Double.POSITIVE_INFINITY;
        for(Actor actor:only==null?actors.values():List.of(only)) {
            if(actor.world!=player.getWorld()||!actor.corpse.isValid())continue;
            for(BodyHandle handle:actor.handles) {
                if(handle.entity==null||!handle.entity.isValid())continue;
                var hit=handle.entity.getBoundingBox().rayTrace(eye.toVector(),direction,Math.max(range,64));
                if(hit==null)continue;
                double distance=hit.getHitPosition().distance(eye.toVector());
                if(distance<nearest){nearest=distance;best=actor;bestPart=handle.part;}
            }
        }
        if(best==null)return new PickResult(null,"視線が操作箱に未交差");
        if(nearest>range)return new PickResult(null,String.format(Locale.ROOT,"操作箱まで %.2fブロック／射程 %.2fブロック",nearest,range));
        var wall=player.getWorld().rayTraceBlocks(eye,direction,Math.max(nearest,.001),FluidCollisionMode.NEVER,true);
        double blocked=wall==null?Double.POSITIVE_INFINITY:wall.getHitPosition().distance(eye.toVector());
        if(!RagdollInteractionGeometry.visible(nearest,blocked,range))return new PickResult(null,String.format(Locale.ROOT,"操作箱より手前にブロック（箱 %.2f／ブロック %.2f）",nearest,blocked));
        return new PickResult(new BodyPick(best,bestPart,nearest),"操作箱に交差: "+bestPart);
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
    private void remember(Player player,String result) {
        lastClicks.put(player.getUniqueId(),new Diagnostic(Bukkit.getCurrentTick(),result));trace(result);
    }
    private LootClick captureClick(PlayerInteractEntityEvent event,Actor actor) {
        // 実際にこの死体へ割り当てた操作箱だけを認める。表示用PDCだけでは開かせない。
        BodyHandle handle=actor.handles.stream().filter(h->h.entity!=null&&h.entity.isValid()
                &&h.entity.getUniqueId().equals(event.getRightClicked().getUniqueId())).findFirst().orElse(null);
        if(handle==null){remember(event.getPlayer(),"操作判定の所属が見つかりません");return null;}
        org.bukkit.util.Vector hit;
        var box=handle.entity.getBoundingBox();
        if(event instanceof PlayerInteractAtEntityEvent at) {
            hit=at.getClickedPosition().clone().add(handle.entity.getLocation().toVector());
            // 動いている操作箱の通信遅延を少し許容する。射程と遮蔽は実際の部位で別途検証。
            if((!Double.isFinite(hit.getX())||!Double.isFinite(hit.getY())||!Double.isFinite(hit.getZ()))||!box.clone().expand(.35).contains(hit)){remember(event.getPlayer(),"操作位置が判定箱から離れています");return null;}
        } else {
            var eye=event.getPlayer().getEyeLocation();
            hit=new org.bukkit.util.Vector(Math.clamp(eye.getX(),box.getMinX(),box.getMaxX()),Math.clamp(eye.getY(),box.getMinY(),box.getMaxY()),Math.clamp(eye.getZ(),box.getMinZ(),box.getMaxZ()));
        }
        RagdollLootTarget target=RagdollLootTarget.capture(actor.physics.pose(handle.part),RagdollPhysics.halfSize(handle.part),new Vector3d(hit.getX(),hit.getY(),hit.getZ()));
        if(target==null){remember(event.getPlayer(),"部位の姿勢が無効です");return null;}
        return new LootClick(event,handle,target);
    }
    /** 視線を引き直さず、クリックした部位への距離と遮蔽を検証する。 */
    private boolean validateClick(Player player,Actor actor,LootClick click) {
        if(player.getGameMode()==GameMode.SPECTATOR||player.getWorld()!=actor.world||!actor.corpse.isValid()){remember(player,"プレイヤー／死体の状態が変わりました");return false;}
        var attribute=player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
        double range=attribute==null?3.0:attribute.getValue();
        return validateClickInRange(player,actor,click,range);
    }
    private boolean validateClickInRange(Player player,Actor actor,LootClick click,double range) {
        if(player.getGameMode()==GameMode.SPECTATOR||player.getWorld()!=actor.world||!actor.corpse.isValid()){remember(player,"プレイヤー／死体の状態が変わりました");return false;}
        Location eye=player.getEyeLocation();Vector3d start=new Vector3d(eye.getX(),eye.getY(),eye.getZ());
        var pose=actor.physics.pose(click.handle.part);
        var bounds=RagdollInteractionGeometry.bounds(pose,RagdollPhysics.halfSize(click.handle.part),click.handle.section,RagdollInteractionGeometry.sections(click.handle.part));
        double reach=RagdollLootTarget.distanceToBox(bounds,start);
        if(!Double.isFinite(range)||range<=0||!Double.isFinite(reach)||reach>range+.05){remember(player,"クリックした部位が射程外です");return false;}
        Vector3d point=click.target.world(pose);if(!point.isFinite()){remember(player,"部位の姿勢が無効です");return false;}
        var direction=new org.bukkit.util.Vector(point.x-eye.getX(),point.y-eye.getY(),point.z-eye.getZ());double distance=direction.length();
        double blocked=Double.POSITIVE_INFINITY;
        if(distance>1e-6) {
            var wall=actor.world.rayTraceBlocks(eye,direction.multiply(1/distance),distance,FluidCollisionMode.NEVER,true);
            if(wall!=null)blocked=wall.getHitPosition().distance(eye.toVector());
        }
        if(!RagdollInteractionGeometry.visible(distance,blocked,Double.MAX_VALUE)){remember(player,"クリックした部位がブロックに遮られています");return false;}
        return true;
    }
    private void requestOpen(PlayerInteractEntityEvent event) {
        Actor actor=actorOf(event.getRightClicked());if(actor==null)return;
        if(event.isCancelled()){remember(event.getPlayer(),"エンティティ操作イベントがキャンセルされています");return;}
        if(event.getHand()!=EquipmentSlot.HAND)return;
        PendingOpen existing=pendingOpens.get(event.getPlayer().getUniqueId());
        if(existing!=null&&existing.actor==actor&&existing.clicks.stream().anyMatch(c->c.event==event))return;
        LootClick click=captureClick(event,actor);if(click==null||!validateClick(event.getPlayer(),actor,click))return;
        remember(event.getPlayer(),"部位クリック受信: "+click.handle.part+"／GUI待ち");
        UUID playerId=event.getPlayer().getUniqueId();PendingOpen pending=pendingOpens.get(playerId);
        if(pending!=null&&pending.actor==actor){pending.clicks.add(click);return;}
        pending=new PendingOpen(actor);pending.clicks.add(click);pendingOpens.put(playerId,pending);
        PendingOpen queued=pending;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!pendingOpens.remove(playerId,queued))return;
            Player player=event.getPlayer();
            if(!player.isOnline())return;
            if(actors.get(actor.corpse.getUniqueId())!=actor){remember(player,"GUI待機中に死体が削除されました");return;}
            boolean allowed=false;
            for(LootClick request:queued.clicks)if(!request.event.isCancelled()&&validateClick(player,actor,request)){allowed=true;break;}
            if(!allowed){if(queued.clicks.stream().allMatch(c->c.event.isCancelled()))remember(player,"処理完了後に操作イベントがキャンセルされています");return;}
            if(player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse){remember(player,"同じ死体のGUIは既に開いています");return;}
            try{plugin.getSearchGUI().open(player,actor.corpse);}
            catch(RuntimeException ex){remember(player,"GUIを開く際の例外: "+ex.getClass().getSimpleName());plugin.getLogger().log(java.util.logging.Level.WARNING,"SCAV死体のGUIを開けませんでした",ex);return;}
            boolean opened=player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder h&&h.getSource()==actor.corpse;
            remember(player,opened?"死体GUIを開きました":"GUIを開けませんでした（中身データ／InventoryOpenの拒否）");
        });
    }
    /** OP用の読取専用診断。拒否を解除したり、GUIを強制的に開いたりしない。 */
    public List<String> diagnose(Player player) {
        if(corpseKey==null)return List.of("ラグドール機能は無効です。");
        List<String> lines=new ArrayList<>();Diagnostic last=lastClicks.get(player.getUniqueId());
        lines.add("最終クリック: "+(last==null?"記録なし":last.result+"（"+Math.max(0,(Bukkit.getCurrentTick()-last.tick)/20)+"秒前）"));
        Actor nearest=null;double distance=64;
        for(Actor actor:actors.values())if(actor.world==player.getWorld()&&actor.corpse.isValid()) {
            var center=actor.physics.pose(RagdollPhysics.Part.TORSO).center();double d=player.getLocation().toVector().distanceSquared(new org.bukkit.util.Vector(center.x,center.y,center.z));
            if(d<distance){distance=d;nearest=actor;}
        }
        if(nearest==null){lines.add("8ブロック以内に登録済みのラグドール死体がありません。通常死体・旧jar・清掃済みも確認してください。");return lines;}
        int valid=0,visible=0;for(BodyHandle handle:nearest.handles)if(handle.entity!=null&&handle.entity.isValid()){valid++;if(player.canSee(handle.entity))visible++;}
        lines.add("操作箱: "+valid+"/"+nearest.handles.size()+"／本人への表示許可: "+visible);
        var reach=player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
        lines.add(String.format(Locale.ROOT,"操作射程: %.2fブロック",reach==null?3.0:reach.getValue()));
        var pdc=nearest.corpse.getPersistentDataContainer();
        lines.add("中身データ: "+(pdc.has(PDCKeys.CORPSE_INVENTORY,org.bukkit.persistence.PersistentDataType.BYTE_ARRAY)||pdc.has(PDCKeys.CORPSE_INVENTORY,org.bukkit.persistence.PersistentDataType.STRING)?"あり":"なし"));
        lines.add("現在の視線: "+probe(player,nearest).reason);
        var listeners=new TreeSet<String>();
        for(var listener:PlayerInteractAtEntityEvent.getHandlerList().getRegisteredListeners())listeners.add(listener.getPlugin().getName());
        for(var listener:PlayerInteractEntityEvent.getHandlerList().getRegisteredListeners())listeners.add(listener.getPlugin().getName());
        lines.add("操作イベントの登録プラグイン（拒否元の断定ではありません）: "+String.join(", ",listeners));
        return lines;
    }
    @EventHandler public void quit(PlayerQuitEvent event){lastClicks.remove(event.getPlayer().getUniqueId());pendingOpens.remove(event.getPlayer().getUniqueId());}
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
