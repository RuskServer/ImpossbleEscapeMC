package com.lunar_prototype.impossbleEscapeMC.effect;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.messaging.Messenger;
import net.kyori.adventure.text.Component;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** 発射によるpostEffects操作なしで、1tick消灯・ディスプレイ再利用・破棄を確認する。 */
public final class MuzzleFlashSignalTest {
    static int tick=100,spawns,removes;
    static boolean mod;
    static Component text;
    static final UUID id=UUID.randomUUID();
    static final List<Integer> packets=new ArrayList<>();
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,InvocationHandler h) {
        return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},(p,m,a)->switch(m.getName()) {
            case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->h.invoke(p,m,a);
        });
    }
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        World[] world={null};
        TextDisplay display=proxy(TextDisplay.class,(p,m,a)->switch(m.getName()) {
            case "text"->{if(a!=null&&a.length==1)text=(Component)a[0];yield text;}
            case "getWorld"->world[0];case "isValid"->true;
            case "getLocation"->new Location(world[0],0,1.5,0);
            case "remove"->{removes++;yield null;}
            default->{if(m.getName().startsWith("set"))yield null;throw new AssertionError(m.getName());}
        });
        world[0]=proxy(World.class,(p,m,a)->{
            if(m.getName().equals("spawn")){spawns++;((Consumer<TextDisplay>)a[2]).accept(display);return display;}
            throw new AssertionError(m.getName());
        });
        Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->id;case "getWorld"->world[0];case "getLocation"->new Location(world[0],0,0,0);
            case "getListeningPluginChannels"->mod?Set.of(MuzzleFlashSignal.CHANNEL):Set.of();
            case "showEntity"->null;
            case "sendPluginMessage"->{packets.add((int)((byte[])a[2])[0]);yield null;}
            default->throw new AssertionError("player."+m.getName());
        });
        Messenger messenger=proxy(Messenger.class,(p,m,a)->null);
        Field server=Bukkit.class.getDeclaredField("server");server.setAccessible(true);
        server.set(null,proxy(Server.class,(p,m,a)->switch(m.getName()) {
            case "getCurrentTick"->tick;case "getPlayer"->player;case "getMessenger"->messenger;
            default->throw new AssertionError(m.getName());
        }));
        var signal=new MuzzleFlashSignal(null);
        signal.flash(player,1);Component on=text;signal.tick(tick);
        check(text==on,"same tick remains lit");
        signal.tick(++tick);check(!text.equals(on),"next tick sends off");
        signal.flash(player,2);signal.tick(++tick);signal.flash(player,1);
        check(spawns==1,"repeated fire reuses one display");
        mod=true;signal.flash(player,3);check(removes==1&&packets.equals(List.of(3)),"native channel retires fallback display and sends mode");
        signal.tick(++tick);check(packets.equals(List.of(3,0)),"native channel turns off next tick");
        signal.flash(player,2);signal.clear(id);check(packets.getLast()==0,"world/death cleanup clears native signal");
        signal.close();
        System.out.println("MuzzleFlashSignalTest passed: one-tick pulse, display reuse, mod routing and cleanup; no postEffects API calls");
    }
}
