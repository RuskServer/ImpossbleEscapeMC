package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.List;

/** 高脅威区域の1部隊。3地点が揃うまでスポーン対象にしない。 */
record RaidBossGroup(String zoneId,double[] boss,double[] guard1,double[] guard2) {
    static final List<String> ROLES=List.of("boss","guard1","guard2");
    RaidBossGroup {
        if(zoneId==null||zoneId.isBlank())throw new IllegalArgumentException("Invalid boss zone");
        boss=copy(boss);guard1=copy(guard1);guard2=copy(guard2);
    }
    private static double[] copy(double[] p) {
        if(p==null)return null;
        if(p.length!=5)throw new IllegalArgumentException("Invalid boss position");
        for(double v:p)if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid boss coordinate");
        return p.clone();
    }
    @Override public double[] boss(){return copy(boss);}
    @Override public double[] guard1(){return copy(guard1);}
    @Override public double[] guard2(){return copy(guard2);}
    double[] position(String role){return switch(role){case "boss"->boss();case "guard1"->guard1();case "guard2"->guard2();default->throw new IllegalArgumentException("Unknown boss role");};}
    RaidBossGroup with(String role,double[] position) {
        if(!ROLES.contains(role))throw new IllegalArgumentException("Unknown boss role");
        return new RaidBossGroup(zoneId,role.equals("boss")?position:boss,role.equals("guard1")?position:guard1,role.equals("guard2")?position:guard2);
    }
    String problem(RaidRadioZone zone) {
        if(zone==null||!zoneId.equals(zone.id()))return "高脅威区域がありません";
        if(boss==null||guard1==null||guard2==null)return "3地点が未登録です";
        double[][] points={boss,guard1,guard2};
        for(double[] p:points)if(!zone.contains(p[0],p[1],p[2],0))return "区域外の地点があります";
        for(int i=0;i<3;i++)for(int j=0;j<i;j++) {
            double d=distanceSquared(points[i],points[j]);
            if(d<2.25)return "地点間は1.5ブロック以上離してください";
        }
        if(distanceSquared(boss,guard1)>144||distanceSquared(boss,guard2)>144)return "護衛はボスから12ブロック以内に配置してください";
        return null;
    }
    private static double distanceSquared(double[] a,double[] b){return Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2);}
}
