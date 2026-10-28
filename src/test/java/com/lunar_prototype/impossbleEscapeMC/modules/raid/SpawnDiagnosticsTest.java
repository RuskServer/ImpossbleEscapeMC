package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import org.bukkit.Location;
import org.bukkit.World;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.SpawnRouteSearch.Point;

public final class SpawnDiagnosticsTest {
    public static void main(String[] args) {
        stableIds(); routes(); thresholds();
        System.out.println("SpawnDiagnosticsTest OK");
    }
    private static void stableIds() {
        Gson gson=new Gson();
        RaidMap map=gson.fromJson("{\"mapId\":\"legacy\",\"worldName\":\"world\",\"spawnPoints\":[[0,1,0,0,0],[20,1,0,0,0],[40,1,0,0,0]]}",RaidMap.class);
        check(map.initializeSpawnIds(),"legacy migration must be persisted");
        check(!map.initializeSpawnIds(),"migration must be idempotent");
        check(map.removeSpawnPoint(2),"remove existing ID");
        check(map.spawnEntries().get(1).id()==3,"remaining IDs must not shift");
        check(!map.removeSpawnPoint(2),"removed ID must be absent");
        map=gson.fromJson(gson.toJson(map),RaidMap.class);
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->switch(m.getName()) {
            case "getName" -> "world"; case "getUID" -> new UUID(0,1); case "toString" -> "test world";
            case "hashCode" -> 1; case "equals" -> o==a[0]; default -> null;
        });
        map.addSpawnPoint(new Location(world,60,1,0));
        check(map.spawnEntries().getLast().id()==4,"ID must survive round trip and not be reused");
        map.clearSpawnPoints();map.addSpawnPoint(new Location(world,80,1,0));
        check(map.spawnEntries().getFirst().id()==5,"clear must not reuse old IDs");
    }
    private static void routes() {
        SpawnRouteSearch.Terrain wall=(from,x,z)->{
            for(int i=0;i<=20;i++) {
                double px=from.x()+(x-from.x())*i/20, pz=from.z()+(z-from.z())*i/20;
                if(px>=8&&px<=12&&Math.abs(pz)<=8)return null;
            }
            return new Point(x,0,z);
        };
        var search=new SpawnRouteSearch(new Point(0,0,0),new Point(20,0,0),wall,3000,100);
        run(search);
        check(search.path()!=null,"must find detour around wall");
        double length=0;for(int i=1;i<search.path().size();i++)length+=search.path().get(i-1).distance(search.path().get(i));
        check(length>25,"route must go around, not through wall");
        var blocked=new SpawnRouteSearch(new Point(0,0,0),new Point(20,0,0),(f,x,z)->null,100,100);
        run(blocked);check(blocked.path()==null&&blocked.failure()!=null,"blocked route must be unknown, not safe");
        var limited=new SpawnRouteSearch(new Point(0,0,0),new Point(200,0,0),(f,x,z)->new Point(x,0,z),1,1000);
        run(limited);check(limited.path()==null,"node budget must stop search");
    }
    private static void run(SpawnRouteSearch search) {
        for(int i=0;i<50000&&!search.done();i++)search.step();
        check(search.done(),"incremental search must terminate");
    }
    private static void thresholds() {
        check(SpawnRouteSearch.contactTime(56,112,5.6)==20,"contact uses slower arrival, not sum of travel times");
        check(SpawnRouteSearch.rating(0,15,30,40).equals("要調整"),"initial LOS");
        check(SpawnRouteSearch.rating(14.99,15,30,40).equals("要調整"),"under 15s");
        check(SpawnRouteSearch.rating(15,15,30,40).equals("確認推奨"),"15s boundary");
        check(SpawnRouteSearch.rating(30,15,30,40).startsWith("目標範囲"),"30s boundary");
        check(SpawnRouteSearch.rating(40,15,30,40).startsWith("目標範囲"),"40s inclusive");
        check(SpawnRouteSearch.rating(40.01,15,30,40).startsWith("早期接敵"),"over 40s");
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
