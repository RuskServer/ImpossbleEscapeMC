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
    private static final class Handle implements InvocationHandler {
        boolean valid=true;float width,height;Location location;Interaction entity;
        Handle(Location location){this.location=location.clone();entity=proxy(Interaction.class,this);}
        @Override public Object invoke(Object p,Method m,Object[] a) {
            return switch(m.getName()) {
                case "isValid"->valid;
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
        update.invoke(actor);check(spawned.size()==11,"updates reuse existing handles");
        spawned.get(4).valid=false;update.invoke(actor);
        check(spawned.size()==12&&spawned.stream().filter(h->h.valid).count()==11,"only missing handle repaired");
        var remove=ScavRagdollModule.class.getDeclaredMethod("remove",actorClass,boolean.class);remove.setAccessible(true);remove.invoke(module,actor,false);
        check(spawned.stream().noneMatch(h->h.valid),"all handles removed during fallback/cleanup");
        System.out.println("RagdollHandleLifecycleTest passed: native creation, reuse, missing-handle repair and complete cleanup");
    }
    private static <T> T proxy(Class<T> type,InvocationHandler handler){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
