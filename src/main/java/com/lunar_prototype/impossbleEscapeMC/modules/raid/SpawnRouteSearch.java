package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.*;

/** 2m格子の経路探索。stepを少しずつ呼び、ワールドの検査をtickに分散する。 */
final class SpawnRouteSearch {
    record Point(double x, double y, double z) {
        double distance(Point b) { return Math.sqrt(Math.pow(x-b.x,2)+Math.pow(y-b.y,2)+Math.pow(z-b.z,2)); }
    }
    interface Terrain {
        /** 連続した足場・体の空間がある時だけ、移動先を返す。 */
        Point move(Point from, double x, double z);
    }
    private record Key(int x, int y, int z) {}
    private record Node(Key key, Point point, double cost, double estimate, Node parent) {}
    private final Point start, goal;
    private final Terrain terrain;
    private final int limit;
    private final double maxLength;
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::estimate));
    private final Map<Key,Double> costs = new HashMap<>();
    private int expanded;
    private Node current;
    private int direction;
    private boolean done;
    private List<Point> path;
    private String failure;
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    SpawnRouteSearch(Point start, Point goal, Terrain terrain, int limit, double maxLength) {
        this.start=start; this.goal=goal; this.terrain=terrain; this.limit=limit; this.maxLength=maxLength;
        Key key = key(start);
        open.add(new Node(key,start,0,start.distance(goal),null)); costs.put(key,0.0);
    }
    private Key key(Point p) {
        return new Key((int)Math.round((p.x-start.x)/2), (int)Math.round(p.y*2), (int)Math.round((p.z-start.z)/2));
    }
    boolean done() { return done; }
    List<Point> path() { return path; }
    String failure() { return failure; }
    int expanded() { return expanded; }

    /** 最大1辺の地形検査だけを行う。 */
    void step() {
        if (done) return;
        if (current == null) {
            if (open.isEmpty() || expanded >= limit) {
                done=true; failure=open.isEmpty()?"経路なし／探索範囲外":"経路探索の上限"; return;
            }
            current=open.poll();
            if (current.cost > costs.getOrDefault(current.key,Double.POSITIVE_INFINITY)+1e-6) { current=null; return; }
            expanded++; direction=0;
            if (current.point.distance(goal) <= 2.9) {
                Point reached=terrain.move(current.point,goal.x,goal.z);
                if (reached!=null && Math.abs(reached.y-goal.y)<.6) {
                    List<Point> result=new ArrayList<>(); result.add(goal);
                    for (Node n=current;n!=null;n=n.parent) result.add(n.point);
                    Collections.reverse(result); path=List.copyOf(result); done=true; return;
                }
            }
            return;
        }
        int[] delta=DIRECTIONS[direction++];
        Point next=terrain.move(current.point,current.point.x+delta[0]*2,current.point.z+delta[1]*2);
        if (next!=null) {
            double cost=current.cost+current.point.distance(next);
            Key key=key(next);
            if (cost<=maxLength && cost<costs.getOrDefault(key,Double.POSITIVE_INFINITY)-1e-6) {
                costs.put(key,cost);
                open.add(new Node(key,next,cost,cost+next.distance(goal),current));
            }
        }
        if (direction==DIRECTIONS.length) current=null;
    }

    /** 両者が走り、先に着いた側が待つ場合の経路上の最早射線時刻。 */
    static double contactTime(double fromA, double fromB, double speed) {
        return Math.max(fromA,fromB)/speed;
    }
    static String rating(double seconds, double urgent, double targetMin, double targetMax) {
        if (seconds < urgent) return "要調整";
        if (seconds < targetMin) return "確認推奨";
        if (seconds <= targetMax) return "目標範囲（経路近似）";
        return "早期接敵リスク低め（経路近似）";
    }
}
