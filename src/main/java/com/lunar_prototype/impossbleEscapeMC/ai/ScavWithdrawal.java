package com.lunar_prototype.impossbleEscapeMC.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;
import java.util.*;

/** 敵の既知位置に対する撤退。選んだ経路を維持し、失敗した逃げ先は一時的に除外する。 */
final class ScavWithdrawal {
    record Plan(Location destination, Pathfinder.PathResult path, boolean hidden) {}
    private record Candidate(Location at, boolean hidden, double preliminary) {}
    private record Rejected(Location at,int until) {}
    private final Mob mob;
    private Plan plan;
    private Location threatWhenPlanned;
    private WithdrawalRouteCost.Progress progress;
    private final List<Rejected> rejected=new ArrayList<>();
    private int retryAt;
    private boolean arrived;
    private boolean directMovement;
    private static int searchTick=Integer.MIN_VALUE;
    private static int searches;

    ScavWithdrawal(Mob mob){this.mob=mob;}
    boolean active(){return plan!=null;}
    boolean arrived(){return arrived;}
    void directMovement(boolean direct){directMovement=direct;}
    void clear(){plan=null;arrived=false;directMovement=false;progress=null;retryAt=0;rejected.clear();}
    void reject(int now) {
        if(plan!=null)rejected.add(new Rejected(plan.destination.clone(),now+80));
        plan=null;arrived=false;directMovement=false;progress=null;retryAt=now+3;
    }
    Plan update(Location threat,int now) {
        rejected.removeIf(r->now>=r.until);
        if(plan!=null) {
            Location here=mob.getLocation();
            if(plan.destination.getWorld()!=here.getWorld() || !standable(plan.destination)) {reject(now);return null;}
            double remaining=directMovement?here.distance(plan.destination):remaining(here,plan.path,plan.destination);
            arrived=here.distanceSquared(plan.destination)<=1.0 && (!plan.hidden || hidden(here,threat));
            boolean exposed=!hidden(plan.destination,threat);
            if((plan.hidden && exposed) || (threatWhenPlanned.distanceSquared(threat)>16 && exposed)
                    || (!arrived && progress.stuck(now,remaining))) {reject(now);return null;}
            // 露出した非常用退避は到着したら次の遮蔽を探す。
            if(arrived&&!plan.hidden){reject(now);return null;}
            return plan;
        }
        if(now<retryAt)return null;
        if(searchTick!=now){searchTick=now;searches=0;}
        // 多数が同時に撃たれても、候補・経路検査を同一tickへ集中させない。
        if(searches>=1){retryAt=now+3;return null;}
        searches++;
        plan=choose(threat,now);retryAt=now+20;
        if(plan!=null){
            threatWhenPlanned=threat.clone();progress=new WithdrawalRouteCost.Progress(now);
            arrived=mob.getLocation().distanceSquared(plan.destination)<=1 && (!plan.hidden || hidden(mob.getLocation(),threat));
        }
        return plan;
    }
    private Plan choose(Location threat,int now) {
        Location here=mob.getLocation();
        List<Candidate> candidates=new ArrayList<>();
        if(hidden(here,threat)&&standable(here))candidates.add(new Candidate(here.clone(),true,100));
        for(int radius:new int[]{2,4,8,12,16}) {
            for(int dir=0;dir<12;dir++) {
                double angle=dir*Math.PI/6;
                Location at=floor(here.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
                if(at==null || horizontal(at,threat)<3 || rejected.stream().anyMatch(r->r.at.getWorld()==at.getWorld()&&r.at.distanceSquared(at)<4))continue;
                boolean hidden=hidden(at,threat);
                double closer=Math.max(0,horizontal(here,threat)-horizontal(at,threat));
                candidates.add(new Candidate(at,hidden,-here.distance(at)*.15-closer*.5-CombatHeatmapManager.danger(at)*.4));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::preliminary).reversed());
        // 遮蔽候補6件と、遮蔽が全て到達不能だった場合の退避候補2件まで。
        List<Candidate> shortlist=new ArrayList<>();
        candidates.stream().filter(Candidate::hidden).limit(6).forEach(shortlist::add);
        candidates.stream().filter(c->!c.hidden).filter(c->horizontal(c.at,threat)>=horizontal(here,threat)+1).limit(2).forEach(shortlist::add);
        Plan best=null;double bestScore=-Double.MAX_VALUE;
        for(Candidate candidate:shortlist) {
            if(candidate.hidden && here.distanceSquared(candidate.at)<=1)return new Plan(candidate.at,null,true);
            Pathfinder.PathResult path=mob.getPathfinder().findPath(candidate.at);
            if(path==null||!path.canReachFinalPoint()||path.getPoints().isEmpty())continue;
            List<WithdrawalRouteCost.Sample> samples=sample(here,path,candidate.at,threat);
            if(samples==null)continue;
            int exits=exits(candidate.at);
            double score=WithdrawalRouteCost.score(samples,horizontal(here,threat),exits)+candidate.preliminary*.2;
            // 射線が切れる目的地を、露出したまま遠ざかる非常手段より必ず先に選ぶ。
            if(best==null || candidate.hidden&&!best.hidden || candidate.hidden==best.hidden&&score>bestScore) {
                best=new Plan(candidate.at,path,candidate.hidden);bestScore=score;
            }
        }
        return best;
    }
    private List<WithdrawalRouteCost.Sample> sample(Location start,Pathfinder.PathResult path,Location end,Location threat) {
        List<Location> points=new ArrayList<>(path.getPoints());points.add(end);
        List<WithdrawalRouteCost.Sample> samples=new ArrayList<>();
        samples.add(new WithdrawalRouteCost.Sample(0,!hidden(start,threat),CombatHeatmapManager.danger(start),horizontal(start,threat)));
        Location previous=start;double length=0;
        for(Location next:points) {
            if(next.getWorld()!=start.getWorld())return null;
            double segment=previous.distance(next);
            if(length+segment>40)return null;
            int count=Math.max(1,(int)Math.ceil(segment));
            for(int i=1;i<=count;i++) {
                Location p=previous.clone().add(next.toVector().subtract(previous.toVector()).multiply((double)i/count));
                if(!loaded(p))return null;
                samples.add(new WithdrawalRouteCost.Sample(length+segment*i/count,!hidden(p,threat),CombatHeatmapManager.danger(p),horizontal(p,threat)));
            }
            length+=segment;previous=next;
        }
        return samples;
    }
    private static double remaining(Location here,Pathfinder.PathResult path,Location end) {
        if(path==null)return here.distance(end);
        List<Location> points=path.getPoints();int index=Math.min(points.size(),Math.max(0,path.getNextPointIndex()));
        double length=0;Location previous=here;
        for(int i=index;i<points.size();i++){length+=previous.distance(points.get(i));previous=points.get(i);}
        return length+previous.distance(end);
    }
    private int exits(Location from) {
        int n=0;
        for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            Location at=floor(from.clone().add(dir[0]*2,0,dir[1]*2));
            if(at!=null && TacticalPositioning.walkableStraight(from,at))n++;
        }
        return n;
    }
    private static boolean loaded(Location at){return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static Location floor(Location near) {
        if(!loaded(near))return null;
        for(int dy:new int[]{0,1,-1,2,-2,3,-3}) {
            Location p=new Location(near.getWorld(),near.getBlockX()+.5,near.getBlockY()+dy,near.getBlockZ()+.5);
            if(standable(p))return p;
        }
        return null;
    }
    private static boolean standable(Location at) {
        if(!loaded(at))return false;
        Block b=at.getBlock();
        return b.isPassable()&&b.getRelative(0,1,0).isPassable()&&b.getRelative(0,-1,0).getType().isSolid();
    }
    static boolean hidden(Location at,Location threat) {
        if(at.getWorld()!=threat.getWorld())return false;
        for(double h:new double[]{1.0,1.6}) {
            Location from=at.clone().add(0,h,0);Vector vector=threat.toVector().subtract(from.toVector());double length=vector.length();
            if(length<.01)return false;
            // 未読込範囲の遮蔽を安全の根拠にしない。角をかすめるチャンクも境界ごとに確認する。
            if(length>512 || !rayLoaded(from,threat))return false;
            if(at.getWorld().rayTraceBlocks(from,vector,length,FluidCollisionMode.NEVER,true)==null)return false;
        }
        return true;
    }
    private static boolean rayLoaded(Location a,Location b) {
        int x=a.getBlockX()>>4,z=a.getBlockZ()>>4,endX=b.getBlockX()>>4,endZ=b.getBlockZ()>>4;
        double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ();
        int sx=Double.compare(dx,0),sz=Double.compare(dz,0);
        double tx=sx==0?Double.POSITIVE_INFINITY:(((sx>0?x+1:x)*16.0)-a.getX())/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:(((sz>0?z+1:z)*16.0)-a.getZ())/dz;
        double stepX=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx),stepZ=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
        while(true) {
            if(!a.getWorld().isChunkLoaded(x,z))return false;
            if(x==endX&&z==endZ)return true;
            if(tx<tz){x+=sx;tx+=stepX;}
            else if(tz<tx){z+=sz;tz+=stepZ;}
            else{x+=sx;z+=sz;tx+=stepX;tz+=stepZ;}
        }
    }
    private static double horizontal(Location a,Location b){return Math.hypot(a.getX()-b.getX(),a.getZ()-b.getZ());}
}
