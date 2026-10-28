package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.SpawnContactSummary.*;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginDisableEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.stamina.StaminaTravelModel;
import com.lunar_prototype.impossbleEscapeMC.modules.stamina.StaminaModule;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.SpawnRouteSearch.Point;

/** 管理コマンド用。範囲を非同期読込した後、地形・射線検査を複数tickへ分ける。 */
final class SpawnDiagnostics implements Listener {
    private final ImpossbleEscapeMC plugin;
    private final RaidModule manager;
    private final Map<String, Job> jobs = new HashMap<>();
    private final Map<String, Report> cache = new HashMap<>();
    private int budgetTick = -1;
    private long usedNanos;
    private record Settings(double speed, double urgent, double targetMin, double targetMax,
                            int nodes, int checks, int operations, double millis, double maxPath, double timeout,
                            int weight, int food, double reserve, double walkSpeed, int margin, int chunks, int parallel, double lowerLevelDrop) {
        StaminaTravelModel travel() {return StaminaModule.diagnosticTravel(weight,food,reserve,speed,walkSpeed);}
    }
    private record Surroundings(int cover, int exits, boolean unknown, boolean unsafe) {}
    private record Report(List<RaidMap.SpawnEntry> entries, UUID world, Settings settings,
                          List<Pair> pairs, List<Surroundings> surroundings, long created) {}

    SpawnDiagnostics(RaidModule manager) {
        this.manager=manager; this.plugin=ImpossbleEscapeMC.getInstance();
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler public void onDisable(PluginDisableEvent event) {
        if(event.getPlugin()!=plugin)return;
        for(Job job:List.copyOf(jobs.values()))job.cancel();
        jobs.clear();cache.clear();
    }
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
                (int)option("max-contact-checks",4096,100,20000),
                (int)option("operations-per-tick",48,1,256),option("millis-per-tick",2,0.25,5),
                option("max-path-blocks",1024,32,4096), option("max-duration-seconds",120,5,600),
                (int)option("reference-weight-grams",20000,0,100000), (int)option("food-level",20,0,20),
                option("stamina-reserve",5,1,50),option("walk-speed-blocks-per-second",4.3,0.1,20),
                (int)option("chunk-margin-blocks",32,0,256),(int)option("max-chunks",2048,1,8192),
                (int)option("parallel-chunk-loads",2,1,8),option("underground-height-difference",3,1,32));
    }
    void invalidate(RaidMap map) {
        cache.remove(map.getMapId());
        Job old=jobs.remove(map.getMapId());
        if (old!=null) { old.cancel(); old.message("スポーン配置が変わったため、実行中の診断を破棄しました。"); }
    }
    void request(Player player,RaidMap map,int page,boolean fresh) {
        List<RaidMap.SpawnEntry> entries=map.spawnEntries();
        if (page<0 || page>Math.max(1,(entries.size()+11)/12)) {
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
        if (!jobs.containsKey(map.getMapId()) && jobs.size()>=2) {
            say(player,"診断は同時2マップまでです。完了後に再実行してください。",NamedTextColor.YELLOW);return;
        }
        invalidate(map);
        Job job;
        try {job=new Job(map,world,entries,settings);}
        catch(IllegalArgumentException ex) {
            say(player,ex.getMessage(),NamedTextColor.RED);
            print(player,map.getMapId(),new Report(entries,null,settings,List.of(),List.of(),System.currentTimeMillis()),page);return;
        }
        job.viewers.put(player.getUniqueId(),page); jobs.put(map.getMapId(),job);
        say(player,"診断範囲のチャンクを非同期読込中。既存地形のみ読み込み、診断終了後に保持を解除します。",NamedTextColor.YELLOW);
        job.runTaskTimer(plugin,1,1);
    }
    private static void say(Player p,String text,NamedTextColor color) { p.sendMessage(Component.text(text,color)); }
    private static String f(double n) { return String.format(Locale.ROOT,"%.1f",n); }
    private void print(Player p,String map,Report r,int page) {
        int count=r.entries.size(), pages=Math.max(1,(count+11)/12);
        p.sendMessage(Component.text("スポーン配置  ",NamedTextColor.GOLD)
                .append(Component.text(map+"  |  "+count+"地点  |  "+(page==0?"全地点":page+" / "+pages),NamedTextColor.WHITE))
                .append(Component.text("  [再計算]",NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/raid spawn analyze "+map))
                        .hoverEvent(Component.text("地形・遮蔽物の変更後に再計算"))));
        var travel=r.settings.travel();
        Component conditions=Component.text("目標 "+f(r.settings.targetMin)+"〜"+f(r.settings.targetMax)+"秒  |  両者接近・スタミナ込み  ⓘ",NamedTextColor.GRAY)
                .hoverEvent(Component.text("基準 "+f(r.settings.weight/1000.0)+"kg / 満腹度 "+r.settings.food+" / 満タン開始・スキルなし\n"
                        +"走行 "+f(travel.sprintSpeed())+" / 歩行 "+f(travel.walkSpeed())+"m/s\n"
                        +"消費 "+f(travel.drainPerSecond())+"/秒 / 回復 "+f(travel.recoveryPerSecond())+"/秒 / 待ち "+f(travel.recoveryDelay())+"秒\n"
                        +"予備 "+f(r.settings.reserve)+"で歩行、満回復して再走行。重量によりダッシュ不可の場合あり。\n"
                        +"診断結果："+Math.max(0,(System.currentTimeMillis()-r.created)/1000)+"秒前"));
        p.sendMessage(conditions);
        Pair fastest=r.pairs.stream().filter(pair->pair.seconds()!=null)
                .min(Comparator.comparingDouble(pair->pair.seconds())).orElse(null);
        long complete=r.pairs.stream().filter(pair->pair.seconds()!=null&&pair.note().isEmpty()).count();
        long unknown=Math.max(0,(long)count*(count-1)/2-complete);
        say(p,"全体の推定最速"+(unknown>0&&fastest!=null&&fastest.seconds()>0?"（暫定）":"")+": "+(fastest==null?(count<2?"比較対象なし":"未確定"):
                f(fastest.seconds())+"秒  #"+fastest.a()+" ↔ #"+fastest.b())
                +"  |  未確定 "+unknown+"組",NamedTextColor.YELLOW);
        var reasons=unknownReasons(count,r.pairs);
        reasons.entrySet().stream().limit(6).forEach(reason->
                say(p,"  未確定の理由："+reason.getKey()+" ×"+reason.getValue()+"組",NamedTextColor.GRAY));
        if(count==0) say(p,"スポーン地点はありません。",NamedTextColor.GRAY);
        int from=page==0?0:(page-1)*12, to=page==0?count:Math.min(count,page*12);
        for(int i=from;i<to;i++) {
            RaidMap.SpawnEntry e=r.entries.get(i);
            Site site=site(e.id(),count,r.pairs);
            Pair earliest=site.earliest();
            double nearest=Double.POSITIVE_INFINITY; int nearestId=-1,near=0;
            for(var other:r.entries) if(other.id()!=e.id()) {
                double d=point(e).distance(point(other));
                if(d<nearest){nearest=d;nearestId=other.id();} if(d<=100)near++;
            }
            String rating=earliest==null?(count<2?"比較なし":"未確定"):
                    SpawnRouteSearch.rating(earliest.seconds(),r.settings.urgent,r.settings.targetMin,r.settings.targetMax);
            if(site.unknown()>0 && earliest!=null && earliest.seconds()>=r.settings.targetMin) rating="確認推奨";
            Surroundings local=i<r.surroundings.size()?r.surroundings.get(i):null;
            RaidMap.SpawnEntry upper=local==null?null:SpawnSiteAssessment.upperReference(e,r.entries,local.exits,local.unknown,local.unsafe,r.settings.lowerLevelDrop);
            if(local!=null) {
                if(local.unsafe&&!local.unknown)rating="足場要調整";
                else if(upper!=null&&site.unknown()==0&&(earliest==null||earliest.seconds()>=r.settings.targetMin))rating="地下候補";
                else if(upper==null&&local.exits<=2&&!local.unknown&&earliest!=null&&earliest.seconds()>=r.settings.targetMin)rating="退避要確認";
            }
            NamedTextColor color=earliest==null?NamedTextColor.GRAY:
                    earliest.seconds()<r.settings.urgent?NamedTextColor.RED:
                    earliest.seconds()<r.settings.targetMin?NamedTextColor.YELLOW:
                    earliest.seconds()<=r.settings.targetMax?NamedTextColor.GREEN:NamedTextColor.AQUA;
            if(site.unknown()>0 && (earliest==null||earliest.seconds()>=r.settings.targetMin))color=NamedTextColor.YELLOW;
            if(local!=null&&local.unsafe&&!local.unknown)color=NamedTextColor.RED;
            String time=earliest==null?(count<2?"—":"未確定"):f(earliest.seconds())+"秒 → #"+site.opponent();
            Component detail=Component.text("#"+e.id()+"  ("+f(e.x())+", "+f(e.y())+", "+f(e.z())+")",NamedTextColor.WHITE)
                    .append(Component.text("\n推定最速接敵: "+time+"\n判定: "+rating, color))
                    .append(Component.text("\n最寄り: "+(nearestId<0?"なし":"#"+nearestId+" / "+f(nearest)+"m")+" / 100m内 "+near+"地点"
                            +"\n開始時射線: "+site.direct()+"組 / 未確定: "+site.unknown()+"組",NamedTextColor.GRAY));
            if(local!=null)detail=detail.append(Component.text("\n遮蔽(8m): "+local.cover+"/8 / 退避(4m): "+local.exits+"/8"
                    +(local.unsafe?"\n足場または頭上空間に問題":"")+(local.unknown?"\n周辺読込失敗／範囲外あり":""),NamedTextColor.GRAY));
            if(upper!=null)detail=detail.append(Component.text("\n地下・立体通路候補: #"+upper.id()+"より"+f(upper.y()-e.y())+"m低い。\n退避0は水平検査の結果。階段・曲がった出口を確認。",NamedTextColor.AQUA));
            List<Pair> notes=r.pairs.stream().filter(pair->(pair.a()==e.id()||pair.b()==e.id())&&!pair.note().isEmpty()).toList();
            for(Pair pair:notes.stream().limit(8).toList()) detail=detail.append(Component.text("\n相手 #"+(pair.a()==e.id()?pair.b():pair.a())+": "+pair.note(),NamedTextColor.DARK_GRAY));
            if(notes.size()>8)detail=detail.append(Component.text("\nほか "+(notes.size()-8)+"組も未確定",NamedTextColor.DARK_GRAY));
            if(site.unknown()>0)detail=detail.append(Component.text("\n未確定の相手を含むため、表示時間より早い接敵の可能性あり。",NamedTextColor.YELLOW));
            p.sendMessage(Component.text(String.format(Locale.ROOT,"#%-3d  ",e.id()),NamedTextColor.WHITE)
                    .append(Component.text(time,color))
                    .append(Component.text("  ["+rating.replace("（経路近似）","")+"]",color))
                    .append(site.unknown()>0?Component.text("  未確定"+site.unknown(),NamedTextColor.DARK_GRAY):Component.empty())
                    .hoverEvent(detail));
        }
        if(page>0) {
            Component navigation=Component.text("[全地点]",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/raid spawn list "+map));
            if(page>1)navigation=navigation.append(Component.text("  [前へ]",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/raid spawn list "+map+" "+(page-1))));
            if(page<pages)navigation=navigation.append(Component.text("  [次へ]",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/raid spawn list "+map+" "+(page+1))));
            p.sendMessage(navigation);
        }
        say(p,"各行にホバーで詳細。推定最速は一経路上の近似（射線が通るまで）。未確定・40秒超も安全保証なし。",NamedTextColor.DARK_GRAY);
    }
    private static Point point(RaidMap.SpawnEntry e) { return new Point(e.x(),e.y(),e.z()); }

    private final class Job extends BukkitRunnable {
        final RaidMap map; final World world; final List<RaidMap.SpawnEntry> entries; final Settings settings;
        final Map<UUID,Integer> viewers=new LinkedHashMap<>();
        final List<Pair> results=new ArrayList<>(); final List<Surroundings> surroundings=new ArrayList<>();
        int surroundingIndex, direction,cover,exits; boolean localUnknown;
        final List<Comparison> comparisons;
        final List<Comparison> routes=new ArrayList<>();
        final Set<Comparison> unknownInitialSight=new HashSet<>();
        int directIndex,routeCompleted;
        SpawnDiagnosticQueue<Comparison> routeQueue;
        final Map<Comparison,PairWork> activeRoutes=new HashMap<>();
        final long started = System.nanoTime();
        final SpawnChunkLoader loader;
        long lastProgress;
        Job(RaidMap map,World world,List<RaidMap.SpawnEntry> entries,Settings settings) {
            this.map=map;this.world=world;this.entries=entries;this.settings=settings;
            comparisons=comparisons(entries.size());
            loader=new SpawnChunkLoader(world,plugin,entries,settings.margin,settings.chunks,settings.parallel);
        }
        @Override public synchronized void cancel() {
            try {super.cancel();} finally {loader.close();}
        }
        void message(String text) { for (UUID id:viewers.keySet()) {Player p=Bukkit.getPlayer(id);if(p!=null)say(p,text,NamedTextColor.YELLOW);} }
        @Override public void run() {
            if (Bukkit.getWorld(world.getUID())!=world || manager.getMap(map.getMapId())!=map || !entries.equals(map.spawnEntries())) {
                jobs.remove(map.getMapId(),this); cancel(); message("ワールドまたは配置が変わったため診断を中断しました。"); return;
            }
            if (viewers.keySet().stream().noneMatch(id->Bukkit.getPlayer(id)!=null)) {jobs.remove(map.getMapId(),this);cancel();return;}
            if (System.nanoTime()-started > settings.timeout*1_000_000_000) {
                for(int index=directIndex;index<comparisons.size();index++) addResult(comparisons.get(index),null,"診断時間の上限（開始時射線未検査）");
                List<Comparison> unfinished=routeQueue==null?routes:routeQueue.remaining();
                for(Comparison c:unfinished) addResult(c,null,activeRoutes.containsKey(c)
                        ?"診断時間の上限（経路探索中）":"診断時間の上限（経路未着手）");
                finish(); return;
            }
            int tick=Bukkit.getCurrentTick();
            if (budgetTick!=tick) { budgetTick=tick; usedNanos=0; }
            long begin=System.nanoTime();
            long available=(long)(settings.millis*1_000_000)-usedNanos;
            if (available<=0) return;
            long deadline=begin+available;
            try {
                if(loader.done()&&System.nanoTime()-lastProgress>5_000_000_000L) {
                    lastProgress=System.nanoTime();
                    message("診断中：開始時射線 "+directIndex+"/"+comparisons.size()+"組、経路 "+routeCompleted+"/"+routes.size()+"組");
                }
                for(int op=0;op<settings.operations && System.nanoTime()<deadline;op++) {
                    if (!loader.done()) {
                        loader.step();
                        if(System.nanoTime()-lastProgress>5_000_000_000L) {
                            lastProgress=System.nanoTime();message("チャンク読込 "+loader.finished()+"/"+loader.total()+"（失敗・未生成 "+loader.failed()+"）");
                        }
                        continue;
                    }
                    if (surroundingIndex<entries.size()) { surroundingsStep(); continue; }
                    // 全地点ペアの開始時射線を先に確認。遠いIDの危険も経路探索の待ち列で埋もれない。
                    if(directIndex<comparisons.size()) {
                        Comparison c=comparisons.get(directIndex++);
                        Boolean visible=new Terrain(world,loader).visible(point(entries.get(c.a())),point(entries.get(c.b())));
                        if(Boolean.TRUE.equals(visible))addResult(c,0.0,"");
                        else {
                            routes.add(c);
                            if(visible==null)unknownInitialSight.add(c);
                        }
                        continue;
                    }
                    if(routeQueue==null)routeQueue=new SpawnDiagnosticQueue<>(routes,8);
                    Comparison c=routeQueue.next();
                    if(c==null){finish();return;}
                    PairWork pair=activeRoutes.get(c);
                    if(pair==null) {
                        pair=new PairWork(point(entries.get(c.a())),point(entries.get(c.b())),world,settings,loader);
                        // 開始時の射線が未確定なら、迂回経路が見つかっても最速時間は確定しない。
                        pair.terrain.unknown=unknownInitialSight.contains(c);
                        activeRoutes.put(c,pair);
                    }
                    pair.step();
                    if(pair.done) {
                        addResult(c,pair.time,pair.note);
                        activeRoutes.remove(c);routeQueue.complete(c);routeCompleted++;
                    }
                }
            } catch (RuntimeException ex) {
                jobs.remove(map.getMapId(),this);cancel();message("診断中にエラーが発生しました。サーバーログを確認してください。");
                plugin.getLogger().log(java.util.logging.Level.WARNING,"Spawn diagnosis failed for "+map.getMapId(),ex);
            } finally {
                usedNanos+=System.nanoTime()-begin;
            }
        }
        void addResult(Comparison c,Double time,String note) {
            RaidMap.SpawnEntry a=entries.get(c.a()),b=entries.get(c.b());
            results.add(new Pair(a.id(),b.id(),point(a).distance(point(b)),time,note));
        }
        void surroundingsStep() {
            Terrain terrain=new Terrain(world,loader); Point p=point(entries.get(surroundingIndex));
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
            long total=(long)entries.size()*(entries.size()-1)/2;
            long complete=results.stream().filter(p->p.seconds()!=null&&p.note().isEmpty()).count();
            message("診断終了：接敵時間 "+complete+"/"+total+"組を確認、未確定 "+(total-complete)+"組。チャンク "+loader.finished()+"/"+loader.total()+"（失敗・未生成 "+loader.failed()+"）。保持を解除します。");
            cache.put(map.getMapId(),report);jobs.remove(map.getMapId(),this);cancel();
            for(var e:viewers.entrySet()){Player p=Bukkit.getPlayer(e.getKey());if(p!=null)print(p,map.getMapId(),report,e.getValue());}
        }
    }

    private static final class Terrain implements SpawnRouteSearch.Terrain {
        final World world; final SpawnChunkLoader loader; boolean unknown;
        Terrain(World world,SpawnChunkLoader loader) {this.world=world;this.loader=loader;}
        boolean chunkAvailable(int x,int z) {return loader.available(x,z) && world.isChunkLoaded(x,z);}
        boolean loaded(double x,double z) {
            boolean loaded=chunkAvailable(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4);
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
                if(!chunkAvailable(cx,cz)){unknown=true;return null;}
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
        final Point start,goal; final Settings settings; final Terrain terrain; final StaminaTravelModel travel;
        SpawnRouteSearch route; List<Point> path; double[] distance;
        record Contact(int i,int j,double time) {}
        final PriorityQueue<Contact> contacts=new PriorityQueue<>(Comparator.comparingDouble(Contact::time));
        boolean initialized,done; Double time;String note="";int checks;
        PairWork(Point start,Point goal,World world,Settings settings,SpawnChunkLoader loader) {this.start=start;this.goal=goal;this.settings=settings;terrain=new Terrain(world,loader);travel=settings.travel();}
        void step() {
            if(!initialized) {
                initialized=true;
                if(!terrain.standable(start)||!terrain.standable(goal)){note="足場不正またはチャンク未読込";done=true;return;}
                route=new SpawnRouteSearch(start,goal,terrain,settings.nodes,settings.maxPath);return;
            }
            if(path==null) {
                route.step();if(!route.done())return;
                if(route.path()==null){note=route.failure()+(terrain.unknown?" / 読込失敗／範囲外あり":"");done=true;return;}
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
            if(Boolean.TRUE.equals(visible)){time=c.time;note=terrain.unknown?"探索範囲に読込失敗／範囲外あり":"";done=true;return;}
            if(c.j>c.i)contacts.add(contact(c.i,c.j-1));
        }
        Contact contact(int i,int j){return new Contact(i,j,Math.max(travel.seconds(distance[i]),travel.seconds(distance[distance.length-1]-distance[j])));}
    }
}
