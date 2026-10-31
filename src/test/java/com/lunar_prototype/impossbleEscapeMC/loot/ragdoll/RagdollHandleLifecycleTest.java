package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import com.lunar_prototype.impossbleEscapeMC.loot.event.CorpseCreatedEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** 実Actorの生成・更新・部分欠損・回収を、サーバーを動かさず検証する。 */
public final class RagdollHandleLifecycleTest {
    private static final List<Handle> spawned=new ArrayList<>();
    private static boolean blocked;
    private static final class Handle implements InvocationHandler {
        boolean valid=true;float width,height;Location location;Interaction entity;
        final UUID id=UUID.randomUUID();
        Handle(Location location){this.location=location.clone();entity=proxy(Interaction.class,this);}
        @Override public Object invoke(Object p,Method m,Object[] a) {
            return switch(m.getName()) {
                case "isValid"->valid;
                case "getUniqueId"->id;
                case "setInteractionWidth"->{width=(float)a[0];yield null;}
                case "setInteractionHeight"->{height=(float)a[0];yield null;}
                case "getInteractionWidth"->width;
                case "getInteractionHeight"->height;
                case "getLocation"->location.clone();
                case "getBoundingBox"->new org.bukkit.util.BoundingBox(location.getX()-width/2,location.getY(),location.getZ()-width/2,location.getX()+width/2,location.getY()+height,location.getZ()+width/2);
                case "teleport"->{location=((Location)a[0]).clone();yield true;}
                case "setPersistent","setGravity","setResponsive","setInvulnerable"->null;
                case "getPersistentDataContainer"->proxy(PersistentDataContainer.class,(q,n,b)->null);
                case "remove"->{valid=false;yield null;}
                default->throw new AssertionError("unexpected interaction: "+m.getName());
            };
        }
    }
    public static void main(String[] args) throws Exception {
        Field server=Bukkit.class.getDeclaredField("server");server.setAccessible(true);
        server.set(null,proxy(Server.class,(p,m,a)->switch(m.getName()) {
            case "getCurrentTick"->100;
            case "getOnlinePlayers"->List.of();
            case "getLogger"->java.util.logging.Logger.getLogger("RagdollHandleLifecycleTest");
            case "getName","getVersion","getBukkitVersion"->"test";
            default->throw new AssertionError("unexpected server: "+m.getName());
        }));
        World world=proxy(World.class,(p,m,a)->{
            if(m.getName().equals("rayTraceBlocks")) {
                if(!blocked)return null;
                return new org.bukkit.util.RayTraceResult(((Location)a[0]).toVector().add(((Vector)a[1]).clone().multiply(Math.min(.2,(double)a[2]))));
            }
            if(m.getName().equals("spawn")) {
                check(a[1]==Interaction.class,"only interaction handles spawned");
                Handle handle=new Handle((Location)a[0]);spawned.add(handle);
                @SuppressWarnings("unchecked") Consumer<Interaction> consumer=(Consumer<Interaction>)a[a.length-1];
                consumer.accept(handle.entity);return handle.entity;
            }
            throw new AssertionError("unexpected world: "+m.getName());
        });
        UUID id=new UUID(12,34);
        Mannequin corpse=proxy(Mannequin.class,(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->id;
            case "hasGravity","hasAI","isCollidable","isValid"->true;
            case "isInvulnerable"->false;
            default->throw new AssertionError("unexpected corpse: "+m.getName());
        });
        LivingEntity victim=proxy(LivingEntity.class,(p,m,a)->switch(m.getName()) {
            case "getLocation"->new Location(world,12000,-34,-8000);
            case "getVelocity"->new Vector();
            default->throw new AssertionError("unexpected victim: "+m.getName());
        });
        ScavRagdollModule module=new ScavRagdollModule(null);
        Field key=ScavRagdollModule.class.getDeclaredField("corpseKey");key.setAccessible(true);key.set(module,new NamespacedKey("iem","ragdoll_corpse"));
        Class<?> actorClass=Class.forName(ScavRagdollModule.class.getName()+"$Actor");
        var constructor=actorClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object actor=constructor.newInstance(module,new CorpseCreatedEvent(victim,corpse,null),null);
        var update=actorClass.getDeclaredMethod("updateHandles");update.setAccessible(true);
        update.invoke(actor);check(spawned.size()==11,"11 native handles");
        for(Handle handle:spawned)check(handle.width>0&&handle.height>0&&handle.location.getWorld()==world,"dimensions and world");
        var physicsField=actorClass.getDeclaredField("physics");physicsField.setAccessible(true);
        var physics=(RagdollPhysics)physicsField.get(actor);
        var select=actorClass.getDeclaredMethod("interaction",RagdollPhysics.Part.class,Vector.class);select.setAccessible(true);
        for(var part:RagdollPhysics.Part.values()) {
            var center=physics.pose(part).center();
            check(select.invoke(actor,part,new Vector(center.x,center.y,center.z))!=null,"forwarded event targets a handle at the clicked body part");
        }
        // 実際のネイティブイベントから位置を保存し、別向きの視線でも距離・遮蔽だけで検証する。
        var torso=physics.pose(RagdollPhysics.Part.TORSO).center();
        Location eye=new Location(world,torso.x-1,torso.y,torso.z+2,90,0);
        Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
            case "getWorld"->world;
            case "getAttribute"->null;
            case "getGameMode"->GameMode.SURVIVAL;
            case "getEyeLocation"->eye.clone();
            case "getUniqueId"->new UUID(56,78);
            default->throw new AssertionError("unexpected player: "+m.getName());
        });
        Handle nativeHandle=spawned.get(1);
        var hitBox=nativeHandle.entity.getBoundingBox();
        Vector worldHit=new Vector(hitBox.getMaxX()-.001,hitBox.getCenterY(),hitBox.getMaxZ());
        var clickEvent=new org.bukkit.event.player.PlayerInteractAtEntityEvent(player,nativeHandle.entity,worldHit.clone().subtract(nativeHandle.location.toVector()),org.bukkit.inventory.EquipmentSlot.HAND);
        var capture=ScavRagdollModule.class.getDeclaredMethod("captureClick",org.bukkit.event.player.PlayerInteractEntityEvent.class,actorClass);capture.setAccessible(true);
        Object saved=capture.invoke(module,clickEvent,actor);check(saved!=null,"actual event captured even without looking through the inner box");
        var clickClass=Class.forName(ScavRagdollModule.class.getName()+"$LootClick");
        var validate=ScavRagdollModule.class.getDeclaredMethod("validateClickInRange",Player.class,actorClass,clickClass,double.class);validate.setAccessible(true);
        check((boolean)validate.invoke(module,player,actor,saved,3.0),"native click does not require an exact camera recast");
        eye.setYaw(-90);check((boolean)validate.invoke(module,player,actor,saved,3.0),"next-tick camera turn retains captured target");
        blocked=true;check(!(boolean)validate.invoke(module,player,actor,saved,3.0),"same actual event blocked by wall");blocked=false;
        eye.add(10,0,0);check(!(boolean)validate.invoke(module,player,actor,saved,3.0),"same actual event rejected after leaving range");
        eye.subtract(10,0,0);
        // 操作箱の角は回転身体の内側を通らなくても、通常クリックと補助クリックで一致する。
        eye.setX(hitBox.getMaxX()-.001);eye.setY(hitBox.getCenterY());eye.setZ(hitBox.getMaxZ()+1);eye.setYaw(180);eye.setPitch(0);
        var pick=ScavRagdollModule.class.getDeclaredMethod("probeInRange",Player.class,actorClass,double.class);pick.setAccessible(true);
        var resultHit=Class.forName(ScavRagdollModule.class.getName()+"$PickResult").getDeclaredMethod("hit");resultHit.setAccessible(true);
        check(resultHit.invoke(pick.invoke(module,player,actor,3.0))!=null,"fallback accepts the native handle corner");
        blocked=true;check(resultHit.invoke(pick.invoke(module,player,actor,3.0))==null,"fallback rejects a wall before the native handle");blocked=false;
        eye.add(0,0,10);check(resultHit.invoke(pick.invoke(module,player,actor,3.0))==null,"fallback rejects native handles beyond reach");
        update.invoke(actor);check(spawned.size()==11,"updates reuse existing handles");
        spawned.get(4).valid=false;update.invoke(actor);
        check(spawned.size()==12&&spawned.stream().filter(h->h.valid).count()==11,"only missing handle repaired");
        var remove=ScavRagdollModule.class.getDeclaredMethod("remove",actorClass,boolean.class);remove.setAccessible(true);remove.invoke(module,actor,false);
        check(spawned.stream().noneMatch(h->h.valid),"all handles removed during fallback/cleanup");
        System.out.println("RagdollHandleLifecycleTest passed: native click capture/validation, creation, reuse, missing-handle repair and complete cleanup");
    }
    private static <T> T proxy(Class<T> type,InvocationHandler handler){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
