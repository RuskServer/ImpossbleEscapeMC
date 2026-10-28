package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.SpawnRouteSearch.Point;

/** 管理コマンド用。チャンクをロードせず、地形・射線検査をメインスレッドの複数tickへ分ける。 */
final class SpawnDiagnostics {
    private final ImpossbleEscapeMC plugin;
    private final RaidModule manager;
    private final Map<String, Job> jobs = new HashMap<>();
    private final Map<String, Report> cache = new HashMap<>();
    private int budgetTick = -1;
    private long usedNanos;
    private record Settings(double speed, double urgent, double targetMin, double targetMax,
                            int nodes, int pairs, int checks, int operations, double millis, double maxPath, double timeout) {}
    private record Pair(int a, int b, double distance, Double seconds, String note) {}
    private record Surroundings(int cover, int exits, boolean unknown, boolean unsafe) {}
    private record Report(List<RaidMap.SpawnEntry> entries, UUID world, Settings settings,
                          List<Pair> pairs, List<Surroundings> surroundings, long created) {}

    SpawnDiagnostics(RaidModule manager) { this.manager=manager; this.plugin=ImpossbleEscapeMC.getInstance(); }
    private double option(String key,double fallback,double lo,double hi) {
        double n=plugin.getConfig().getDouble("raid-spawn-diagnostics."+key,fallback);
        return Double.isFinite(n)?Math.max(lo,Math.min(hi,n)):fallback;
    }
    private Settings settings() {
        double urgent=option("urgent-seconds",15,1,120);
        double min=option("target-min-seconds",30,urgent,180);
        return new Settings(option("speed-blocks-per-second",5.6,1,20),urgent,min,
                option("target-max-seconds",40,min,240),
                (int)option("max-nodes-per-pair",6000,100,20000),
                (int)option("max-pairs",256,1,1024),
                (int)option("max-contact-checks",4096,100,20000),
                (int)option("operations-per-tick",48,1,256),option("millis-per-tick",2,0.25,5),
                option("max-path-blocks",1024,32,4096), option("max-duration-seconds",120,5,600));
    }
    void invalidate(RaidMap map) {
        cache.remove(map.getMapId());
        Job old=jobs.remove(map.getMapId());
        if (old!=null) { old.cancel(); old.message("スポーン配置が変わったため、実行中の診断を破棄しました。"); }
    }
    void request(Player player,RaidMap map,int page,boolean fresh) {
        List<RaidMap.SpawnEntry> entries=map.spawnEntries();
        if (page<1 || page>Math.max(1,(entries.size()+5)/6)) {
            say(player,"ページ番号が範囲外です。",NamedTextColor.RED); return;
        }
        World world=map.getWorldName()==null?null:Bukkit.getWorld(map.getWorldName());
        Settings settings=settings();
        if (world==null || entries.size()>64) {
            say(player,world==null?"ワールド未読込：座標だけ表示します。":"地点数が64を超えるため診断できません。座標だけ表示します。",NamedTextColor.YELLOW);
            print(player,map.getMapId(),new Report(entries,null,settings,List.of(),List.of(),System.currentTimeMillis()),page); return;
        }
        Report prior=cache.get(map.getMapId());
        if (!fresh && prior!=null && prior.entries.equals(entries) && prior.world.equals(world.getUID())
                && prior.settings.equals(settings) && System.currentTimeMillis()-prior.created<60000) {
            print(player,map.getMapId(),prior,page); return;
        }
        Job running=jobs.get(map.getMapId());
        if (running!=null && !fresh && running.entries.equals(entries) && running.settings.equals(settings)) {
            running.viewers.put(player.getUniqueId(),page);
            say(player,"診断中です。完了すると一覧を表示します。",NamedTextColor.YELLOW); return;
        }
        invalidate(map);
        Job job=new Job(map,world,entries,settings);
        job.viewers.put(player.getUniqueId(),page); jobs.put(map.getMapId(),job);
        say(player,"地形・射線を診断中（チャンクの追加読込なし）。結果は経路近似です。",NamedTextColor.YELLOW);
        job.runTaskTimer(plugin,1,1);
    }
    private static void say(Player p,String text,NamedTextColor color) { p.sendMessage(Component.text(text,color)); }
    private static String f(double n) { return String.format(Locale.ROOT,"%.1f",n); }
    private void print(Player p,String map,Report r,int page) {
        say(p,"スポーン "+map+" — "+r.entries.size()+"地点 / "+page+"ページ",NamedTextColor.GOLD);
        say(p,"目標 "+f(r.settings.targetMin)+"〜"+f(r.settings.targetMax)+"秒、走行 "+f(r.settings.speed)+"m/s。両者接近・経路近似。",NamedTextColor.GRAY);
        say(p,"地形キャッシュ："+Math.max(0,(System.currentTimeMillis()-r.created)/1000)+"秒前 / 再計算: /raid spawn analyze "+map,NamedTextColor.GRAY);
        for (int i=(page-1)*6;i<Math.min(page*6,r.entries.size());i++) {
            RaidMap.SpawnEntry e=r.entries.get(i);
            double nearest=Double.POSITIVE_INFINITY; int nearestId=-1,near=0;
            for (var other:r.entries) if (other.id()!=e.id()) {
                double d=point(e).distance(point(other));
                if (d<nearest) {nearest=d;nearestId=other.id();} if (d<=100) near++;
            }
            Pair earliest=null; int direct=0,unknown=0;
            for (Pair pair:r.pairs) if (pair.a==e.id() || pair.b==e.id()) {
                if (pair.seconds==null || !pair.note.isEmpty()) unknown++;
                if (pair.seconds!=null) {
                    if (pair.seconds==0) direct++;
                    if (earliest==null || pair.seconds<earliest.seconds) earliest=pair;
                }
            }
            if (r.pairs.isEmpty() && r.entries.size()>1) unknown=r.entries.size()-1;
            say(p,"#"+e.id()+" ("+f(e.x())+", "+f(e.y())+", "+f(e.z())+")",NamedTextColor.WHITE);
            say(p,"  最寄り "+(nearestId<0?"なし":"#"+nearestId+" / "+f(nearest)+"m")+"、100m内 "+near+"地点",NamedTextColor.GRAY);
            String rating=earliest==null?(r.entries.size()<2?"比較対象なし":"判定不能"):
                    SpawnRouteSearch.rating(earliest.seconds,r.settings.urgent,r.settings.targetMin,r.settings.targetMax);
            if (unknown>0 && (earliest==null || earliest.seconds>=r.settings.targetMin)) rating="確認推奨（未確定な組み合わせあり）";
            String contact=earliest==null?"未確定":"約"+f(earliest.seconds)+"秒 / 相手 #"+(earliest.a==e.id()?earliest.b:earliest.a);
            if (i<r.surroundings.size()) {
                Surroundings local=r.surroundings.get(i);
                if (local.unsafe && !local.unknown) rating="要調整（足場または頭上空間に問題）";
                else if (local.exits<=2 && !local.unknown && (earliest==null || earliest.seconds>=r.settings.targetMin)) rating="確認推奨（退避方向が少ない）";
            }
            say(p,"  "+rating+"：最短射線接触 "+contact+"、開始時射線の検出 "+direct+"組、未確定 "+unknown+"組",
                    earliest!=null && earliest.seconds<r.settings.urgent?NamedTextColor.RED:NamedTextColor.YELLOW);
            if (i<r.surroundings.size()) {
                Surroundings s=r.surroundings.get(i);
                say(p,"  遮蔽(8m) "+s.cover+"/8方向、退避(4m) "+s.exits+"/8方向"+
                        (s.unsafe?" / 足場または頭上空間に問題":"")+(s.unknown?" / 周辺未読込あり":""),NamedTextColor.GRAY);
            }
            for (Pair pair:r.pairs) if ((pair.a==e.id() || pair.b==e.id()) && !pair.note.isEmpty()) {
                say(p,"  相手 #"+(pair.a==e.id()?pair.b:pair.a)+"："+pair.note,NamedTextColor.DARK_GRAY);
            }
        }
        if (r.entries.size()>page*6) say(p,"次: /raid spawn list "+map+" "+(page+1),NamedTextColor.GRAY);
        say(p,"推定は2m格子の一経路。ジャンプ・扉操作・別ルート・スタミナは未評価。40秒超も安全保証ではありません。",NamedTextColor.DARK_GRAY);
    }
    private static Point point(RaidMap.SpawnEntry e) { return new Point(e.x(),e.y(),e.z()); }

    private final class Job extends BukkitRunnable {
        final RaidMap map; final World world; final List<RaidMap.SpawnEntry> entries; final Settings settings;
        final Map<UUID,Integer> viewers=new LinkedHashMap<>();
        final List<Pair> results=new ArrayList<>(); final List<Surroundings> surroundings=new ArrayList<>();
        int surroundingIndex, direction,cover,exits; boolean localUnknown;
        int a=0,b=1,pairCount; PairWork pair;
        final long started = System.nanoTime();
        Job(RaidMap map,World world,List<RaidMap.SpawnEntry> entries,Settings settings) {
            this.map=map;this.world=world;this.entries=entries;this.settings=settings;
        }
        void message(String text) { for (UUID id:viewers.keySet()) {Player p=Bukkit.getPlayer(id);if(p!=null)say(p,text,NamedTextColor.YELLOW);} }
        @Override public void run() {
            if (Bukkit.getWorld(world.getUID())!=world || manager.getMap(map.getMapId())!=map || !entries.equals(map.spawnEntries())) {
                jobs.remove(map.getMapId(),this); cancel(); message("ワールドまたは配置が変わったため診断を中断しました。"); return;
            }
            if (viewers.keySet().stream().noneMatch(id->Bukkit.getPlayer(id)!=null)) {jobs.remove(map.getMapId(),this);cancel();return;}
            if (System.nanoTime()-started > settings.timeout*1_000_000_000) {
                while (a<entries.size()-1) {
                    results.add(new Pair(entries.get(a).id(),entries.get(b).id(),point(entries.get(a)).distance(point(entries.get(b))),null,"診断時間の上限")); next();
                }
                finish(); return;
            }
            int tick=Bukkit.getCurrentTick();
            if (budgetTick!=tick) { budgetTick=tick; usedNanos=0; }
            long begin=System.nanoTime();
            long available=(long)(settings.millis*1_000_000)-usedNanos;
            if (available<=0) return;
            long deadline=begin+available;
            try {
                for(int op=0;op<settings.operations && System.nanoTime()<deadline;op++) {
                    if (surroundingIndex<entries.size()) { surroundingsStep(); continue; }
                    if (a>=entries.size()-1) { finish();return; }
                    if (pair==null) {
                        if (++pairCount>settings.pairs) {
                            results.add(new Pair(entries.get(a).id(),entries.get(b).id(),point(entries.get(a)).distance(point(entries.get(b))),null,"組み合わせ数の上限")); next(); continue;
                        }
                        pair=new PairWork(point(entries.get(a)),point(entries.get(b)),world,settings);
                    }
                    pair.step();
                    if (pair.done) {
                        results.add(new Pair(entries.get(a).id(),entries.get(b).id(),pair.start.distance(pair.goal),pair.time,pair.note)); next();
                    }
                }
            } catch (RuntimeException ex) {
                jobs.remove(map.getMapId(),this);cancel();message("診断中にエラーが発生しました。サーバーログを確認してください。");
                plugin.getLogger().log(java.util.logging.Level.WARNING,"Spawn diagnosis failed for "+map.getMapId(),ex);
            } finally {
                usedNanos+=System.nanoTime()-begin;
            }
        }
        void next() {pair=null;if(++b>=entries.size()){a++;b=a+1;}}
        void surroundingsStep() {
            Terrain terrain=new Terrain(world); Point p=point(entries.get(surroundingIndex));
            double angle=direction*Math.PI/4;
            Point far=new Point(p.x()+Math.cos(angle)*8,p.y(),p.z()+Math.sin(angle)*8);
            Boolean visible=terrain.visible(p,far);
            if (Boolean.FALSE.equals(visible)) cover++;
            if (terrain.move(p,p.x()+Math.cos(angle)*4,p.z()+Math.sin(angle)*4)!=null) exits++;
            localUnknown|=terrain.unknown;
            if (++direction==8) {
                boolean unsafe=!terrain.standable(p);
                surroundings.add(new Surroundings(cover,exits,localUnknown||terrain.unknown,unsafe));
                surroundingIndex++;direction=cover=exits=0;localUnknown=false;
            }
        }
        void finish() {
            Report report=new Report(entries,world.getUID(),settings,List.copyOf(results),List.copyOf(surroundings),System.currentTimeMillis());
            cache.put(map.getMapId(),report);jobs.remove(map.getMapId(),this);cancel();
            for(var e:viewers.entrySet()){Player p=Bukkit.getPlayer(e.getKey());if(p!=null)print(p,map.getMapId(),report,e.getValue());}
        }
    }

    private static final class Terrain implements SpawnRouteSearch.Terrain {
        final World world; boolean unknown;
        Terrain(World world) {this.world=world;}
        boolean loaded(double x,double z) {
            boolean loaded=world.isChunkLoaded(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4);
            if(!loaded) unknown=true;return loaded;
        }
        boolean area(Point p) {
            return loaded(p.x()-.31,p.z()-.31)&loaded(p.x()+.31,p.z()-.31)&loaded(p.x()-.31,p.z()+.31)&loaded(p.x()+.31,p.z()+.31);
        }
        boolean standable(Point p) {
            if(!area(p) || p.y()<world.getMinHeight()+1 || p.y()+1.81>=world.getMaxHeight())return false;
            if(world.hasCollisionsIn(new BoundingBox(p.x()-.3,p.y()+.02,p.z()-.3,p.x()+.3,p.y()+1.8,p.z()+.3)))return false;
            var floor=world.rayTraceBlocks(new Location(world,p.x(),p.y()+.1,p.z()),new Vector(0,-1,0),.3,FluidCollisionMode.NEVER,true);
            return floor!=null && !world.getBlockAt((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z())).isLiquid();
        }
        @Override public Point move(Point from,double x,double z) {
            int steps=Math.max(1,(int)Math.ceil(Math.hypot(x-from.x(),z-from.z())/.4));
            Point prev=from;
            if(!standable(from))return null;
            for(int i=1;i<=steps;i++) {
                double xx=from.x()+(x-from.x())*i/steps, zz=from.z()+(z-from.z())*i/steps;
                if(!area(new Point(xx,prev.y(),zz)))return null;
                var hit=world.rayTraceBlocks(new Location(world,xx,prev.y()+.61,zz),new Vector(0,-1,0),1.85,FluidCollisionMode.NEVER,true);
                if(hit==null)return null;
                Point next=new Point(xx,hit.getHitPosition().getY(),zz);
                if(!standable(next))return null;
                // 上りは先に体を持ち上げる。0.6mを超える段差・ジャンプ・水泳は探索しない。
                double high=Math.max(prev.y(),next.y());
                BoundingBox sweep=new BoundingBox(Math.min(prev.x(),xx)-.3,high+.02,Math.min(prev.z(),zz)-.3,
                        Math.max(prev.x(),xx)+.3,high+1.8,Math.max(prev.z(),zz)+.3);
                if(world.hasCollisionsIn(sweep))return null;
                prev=next;
            }
            return prev;
        }
        Boolean visible(Point a,Point b) {
            double length=a.distance(b);
            if(length>2048){unknown=true;return null;}
            // チャンク境界をDDAで全てたどる。点サンプリングでは角を横切る未読込チャンクを見落とす。
            int cx=((int)Math.floor(a.x()))>>4, cz=((int)Math.floor(a.z()))>>4;
            int endX=((int)Math.floor(b.x()))>>4, endZ=((int)Math.floor(b.z()))>>4;
            double dx=b.x()-a.x(), dz=b.z()-a.z();
            int sx=Double.compare(dx,0), sz=Double.compare(dz,0);
            double tx=sx==0?Double.POSITIVE_INFINITY:(((sx>0?cx+1:cx)*16.0)-a.x())/dx;
            double tz=sz==0?Double.POSITIVE_INFINITY:(((sz>0?cz+1:cz)*16.0)-a.z())/dz;
            double stepX=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx);
            double stepZ=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
            while(true) {
                if(!world.isChunkLoaded(cx,cz)){unknown=true;return null;}
                if(cx==endX && cz==endZ)break;
                if(tx<tz){cx+=sx;tx+=stepX;}
                else if(tz<tx){cz+=sz;tz+=stepZ;}
                else {cx+=sx;cz+=sz;tx+=stepX;tz+=stepZ;}
            }
            if(length<.01)return true;
            // 頭同士だけでは、窓や低い遮蔽物越しに胴体が見えるケースを落とす。
            for (double sourceHeight : new double[]{1.0, 1.27, 1.62}) {
                for (double targetHeight : new double[]{1.0, 1.27, 1.62}) {
                    if (sourceHeight == 1.0 && targetHeight == 1.0) continue;
                    Vector direction=new Vector(b.x()-a.x(),b.y()+targetHeight-a.y()-sourceHeight,b.z()-a.z());
                    if(world.rayTraceBlocks(new Location(world,a.x(),a.y()+sourceHeight,a.z()),direction,
                            direction.length(),FluidCollisionMode.NEVER,true)==null)return true;
                }
            }
            return false;
        }
    }

    private static final class PairWork {
        final Point start,goal; final Settings settings; final Terrain terrain;
        SpawnRouteSearch route; List<Point> path; double[] distance;
        record Contact(int i,int j,double time) {}
        final PriorityQueue<Contact> contacts=new PriorityQueue<>(Comparator.comparingDouble(Contact::time));
        boolean initialized,done; Double time;String note="";int checks;
        PairWork(Point start,Point goal,World world,Settings settings) {this.start=start;this.goal=goal;this.settings=settings;terrain=new Terrain(world);}
        void step() {
            if(!initialized) {
                initialized=true;
                Boolean direct=terrain.visible(start,goal);
                if(Boolean.TRUE.equals(direct)){time=0.0;done=true;return;}
                if(!terrain.standable(start)||!terrain.standable(goal)){note="足場不正またはチャンク未読込";done=true;return;}
                route=new SpawnRouteSearch(start,goal,terrain,settings.nodes,settings.maxPath);return;
            }
            if(path==null) {
                route.step();if(!route.done())return;
                if(route.path()==null){note=route.failure()+(terrain.unknown?" / 未読込あり":"");done=true;return;}
                // 経路上の候補を約4m間隔にする。距離は元経路の長さを保持する。
                List<Point> full=route.path();List<Point> sampled=new ArrayList<>();List<Double> lengths=new ArrayList<>();double sum=0;
                for(int i=0;i<full.size();i++) {
                    if(i>0)sum+=full.get(i-1).distance(full.get(i));
                    if(i%2==0||i==full.size()-1){sampled.add(full.get(i));lengths.add(sum);}
                }
                path=sampled;distance=lengths.stream().mapToDouble(Double::doubleValue).toArray();
                for(int i=0;i<path.size();i++)contacts.add(contact(i,path.size()-1));return;
            }
            if(contacts.isEmpty() || checks++>=settings.checks){note="射線候補探索の上限";done=true;return;}
            Contact c=contacts.remove();
            Boolean visible=terrain.visible(path.get(c.i),path.get(c.j));
            if(Boolean.TRUE.equals(visible)){time=c.time;note=terrain.unknown?"探索範囲に未読込／射線範囲外あり":"";done=true;return;}
            if(c.j>c.i)contacts.add(contact(c.i,c.j-1));
        }
        Contact contact(int i,int j){return new Contact(i,j,SpawnRouteSearch.contactTime(distance[i],distance[distance.length-1]-distance[j],settings.speed));}
    }
}
