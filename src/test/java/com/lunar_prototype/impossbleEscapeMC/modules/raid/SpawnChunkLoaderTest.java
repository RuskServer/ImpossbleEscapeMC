package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.util.ChunkLease;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SpawnChunkLoaderTest {
    public static void main(String[] args) {
        Fixture f=new Fixture();
        ChunkLease a=ChunkLease.acquire(f.chunk,f.plugin), b=ChunkLease.acquire(f.chunk,f.plugin);
        check(f.ticket,"acquire ticket");a.close();a.close();check(f.ticket,"other lease retains ticket");b.close();check(!f.ticket,"last lease releases ticket");
        f.ticket=true;ChunkLease existing=ChunkLease.acquire(f.chunk,f.plugin);existing.close();check(f.ticket,"existing external ticket survives");f.ticket=false;
        var loader=f.loader();loader.step();check(!loader.done()&&!f.ticket,"asynchronous load must not block or hold early");
        f.future.complete(f.chunk);loader.step();check(loader.done()&&loader.available(0,0)&&f.ticket,"loaded chunk held during diagnosis");
        loader.close();check(!f.ticket,"completion cleanup releases ticket");
        f.future=new CompletableFuture<>();loader=f.loader();loader.step();loader.close();f.future.complete(f.chunk);loader.step();check(!f.ticket,"completion after cancellation must not acquire ticket");
        f.future=CompletableFuture.completedFuture(null);loader=f.loader();loader.step();loader.step();check(loader.done()&&loader.failed()==1,"ungenerated chunk explicitly fails");loader.close();
        f.future=CompletableFuture.failedFuture(new IllegalStateException("load failed"));loader=f.loader();loader.step();loader.step();check(loader.done()&&loader.failed()==1,"exception explicitly fails");loader.close();
        f.loads=0;f.future=new CompletableFuture<>();
        loader=new SpawnChunkLoader(f.world,f.plugin,List.of(point(0),point(32)),0,4,2);
        for(int i=0;i<8;i++)loader.step();
        check(f.loads==2,"concurrent requests must respect limit");loader.close();
        boolean rejected=false;
        try {new SpawnChunkLoader(f.world,f.plugin,List.of(point(0),point(10000)),32,1,2);}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"oversize area rejected before loading");
        System.out.println("SpawnChunkLoaderTest OK");
    }
    private static RaidMap.SpawnEntry point(double x){return new RaidMap.SpawnEntry(1,x,1,0,0,0);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class Fixture {
        boolean ticket;
        int loads;
        CompletableFuture<Chunk> future=new CompletableFuture<>();
        final Plugin plugin=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class[]{Plugin.class},(o,m,a)->switch(m.getName()){
            case "hashCode"->1;case "equals"->o==a[0];case "getName"->"test";default->null;
        });
        final UUID uuid=UUID.randomUUID();
        final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->switch(m.getName()){
            case "getUID"->uuid;case "getName"->"world";case "isChunkLoaded"->true;
            case "getPluginChunkTickets"->ticket?List.of(plugin):List.of();
            case "addPluginChunkTicket"->{ticket=true;yield true;}
            case "removePluginChunkTicket"->{ticket=false;yield true;}
            case "getChunkAtAsync"->{loads++;check(a.length==3&&Boolean.FALSE.equals(a[2]),"must not generate chunks");yield future;}
            case "hashCode"->2;case "equals"->o==a[0];default->null;
        });
        final Chunk chunk=(Chunk)Proxy.newProxyInstance(Chunk.class.getClassLoader(),new Class[]{Chunk.class},(o,m,a)->switch(m.getName()){
            case "getWorld"->world;case "getX","getZ"->0;default->null;
        });
        SpawnChunkLoader loader(){return new SpawnChunkLoader(world,plugin,List.of(point(0)),0,4,2);}
    }
}
