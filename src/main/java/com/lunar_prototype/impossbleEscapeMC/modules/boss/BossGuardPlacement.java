package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.bukkit.Location;
import java.util.*;

/** 本体から連結した足場を幅優先で探す。未読込チャンクには触れず、探索を更新間で分割する。 */
final class BossGuardPlacement {
    private static final int MAX_VISITED=512,MAX_PATH_STEPS=24;
    private static final int[][] DIRECTIONS={{1,0},{0,1},{-1,0},{0,-1}};
    private record Node(Location point,Node parent,int depth) {}
    private final BossDefinition definition;
    private final BossTerritory territory;
    private final Location anchor;
    private final ArrayDeque<Node> frontier=new ArrayDeque<>();
    private final Set<String> visited=new HashSet<>();
    private final List<Node> candidates=new ArrayList<>();
    private List<Node> selected;
    private boolean done;

    BossGuardPlacement(BossDefinition definition,BossTerritory territory,Location anchor) {
        this.definition=definition;this.territory=territory;this.anchor=anchor.clone();
        frontier.add(new Node(this.anchor,null,0));visited.add(key(this.anchor));
    }
    /** 1回あたりのノード数と約1msを上限にする。少なくとも1ノードは進める。 */
    void advance(int budget) {
        if(done)return;
        long deadline=System.nanoTime()+1_000_000;
        for(int i=0;i<budget&&!frontier.isEmpty();i++) {
            Node node=frontier.removeFirst();
            if(horizontal(node.point,anchor)>=4)candidates.add(node);
            int guards=definition.members().size()-1;
            if((guards==0||candidates.size()>=Math.max(8,guards*4))&&select(guards)) {done=true;return;}
            if(node.depth<MAX_PATH_STEPS&&visited.size()<MAX_VISITED)for(int[] direction:DIRECTIONS) {
                double x=node.point.getX()+direction[0],z=node.point.getZ()+direction[1];
                if(Math.pow(x-anchor.getX(),2)+Math.pow(z-anchor.getZ(),2)>Math.pow(definition.guardSpawnRadius(),2))continue;
                for(double y:BossSpawnSafety.floorHeights(anchor.getWorld(),x,z,
                        Math.max(anchor.getY()-2,node.point.getY()-.6),Math.min(anchor.getY()+2,node.point.getY()+.6))) {
                    Location next=new Location(anchor.getWorld(),x,y,z,anchor.getYaw(),anchor.getPitch());
                    String key=key(next);
                    if(visited.contains(key)||!contains(next)||!BossSpawnSafety.safePosition(next)||!BossSpawnSafety.walkable(node.point,next))continue;
                    if(visited.size()>=MAX_VISITED)break;
                    visited.add(key);frontier.addLast(new Node(next,node,node.depth+1));
                }
            }
            if(System.nanoTime()>=deadline)break;
        }
        if(frontier.isEmpty()){select(definition.members().size()-1);done=true;}
    }
    private boolean select(int guards) {
        List<Node> chosen=new ArrayList<>();
        for(int i=0;i<guards;i++) {
            Node best=null;double score=-1;
            for(Node candidate:candidates) {
                double nearest=horizontal(candidate.point,anchor),separation=0;
                for(Node other:chosen){double d=horizontal(candidate.point,other.point);nearest=Math.min(nearest,d);separation+=d;}
                if(nearest<2.25)continue;
                // 左右へ散らしつつ、狭い場所でも近傍の有効な足場を使える。
                double candidateScore=nearest+separation;
                if(candidateScore>score){best=candidate;score=candidateScore;}
            }
            if(best==null)return false;
            chosen.add(best);
        }
        selected=List.copyOf(chosen);return true;
    }
    boolean done(){return done;}
    List<Location> points() {
        if(!done||selected==null)return null;
        List<Location> points=new ArrayList<>();int guard=0;
        for(var member:definition.members())points.add((member.leader()?anchor:selected.get(guard++).point).clone());
        return List.copyOf(points);
    }
    /** 待機中に地形が変化した場合も、生成直前に選択経路を再検証する。 */
    boolean valid() {
        if(selected==null||!BossSpawnSafety.safePosition(anchor))return false;
        for(Node end:selected)for(Node node=end;node.parent!=null;node=node.parent)
            if(!BossSpawnSafety.safePosition(node.point)||!BossSpawnSafety.walkable(node.parent.point,node.point))return false;
        return true;
    }
    private boolean contains(Location p){return territory.contains(new double[]{p.getX(),p.getY(),p.getZ()});}
    private static double horizontal(Location a,Location b){return Math.pow(a.getX()-b.getX(),2)+Math.pow(a.getZ()-b.getZ(),2);}
    private static String key(Location p){return p.getBlockX()+":"+Math.round(p.getY()*10000)+":"+p.getBlockZ();}
}
