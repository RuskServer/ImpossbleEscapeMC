package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.*;
import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossPlacement;

/** マップJSONの互換DTO。旧3配列をBossPlacementへ変換し、編集時は汎用のpositionsへ移す。 */
record RaidBossGroup(String zoneId,double[] boss,double[] guard1,double[] guard2,String bossId,Map<String,double[]> positions,int spawnId) {
    RaidBossGroup(String zoneId,double[] boss,double[] guard1,double[] guard2) {this(zoneId,boss,guard1,guard2,null,null,0);}
    RaidBossGroup(String zoneId,double[] boss,double[] guard1,double[] guard2,String bossId,Map<String,double[]> positions) {
        this(zoneId,boss,guard1,guard2,bossId,positions,0);
    }
    RaidBossGroup withId(int id){return new RaidBossGroup(zoneId,boss,guard1,guard2,bossId,positions,id);}
    @Override public String bossId(){return bossId==null?BossDefinition.DEFAULT_ID:bossId;}
    BossPlacement placement(){
        Map<String,double[]> points=new LinkedHashMap<>();
        if(boss!=null)points.put("boss",boss());if(guard1!=null)points.put("guard1",guard1());if(guard2!=null)points.put("guard2",guard2());
        if(positions!=null)positions.forEach((k,v)->points.put(k,copy(v)));
        return new BossPlacement(bossId(),zoneId,points);
    }
    static RaidBossGroup empty(String zoneId,String bossId){return new RaidBossGroup(zoneId,null,null,null,bossId,Map.of());}
    RaidBossGroup withMember(String role,double[] p){
        var points=new LinkedHashMap<>(placement().positions()); points.put(role,copy(p));
        return new RaidBossGroup(zoneId,null,null,null,bossId(),points,spawnId);
    }
    RaidBossGroup {
        if(zoneId==null||zoneId.isBlank())throw new IllegalArgumentException("Invalid boss zone");
        boss=copy(boss);guard1=copy(guard1);guard2=copy(guard2);
        if(positions!=null)positions=new BossPlacement(bossId==null?BossDefinition.DEFAULT_ID:bossId,zoneId,positions).positions();
    }
    private static double[] copy(double[] p) {
        if(p==null)return null;
        if(p.length!=5)throw new IllegalArgumentException("Invalid boss position");
        for(double v:p)if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid boss coordinate");
        return p.clone();
    }
    @Override public double[] boss(){return positions!=null&&positions.containsKey("boss")?copy(positions.get("boss")):copy(boss);}
    @Override public double[] guard1(){return positions!=null&&positions.containsKey("guard1")?copy(positions.get("guard1")):copy(guard1);}
    @Override public double[] guard2(){return positions!=null&&positions.containsKey("guard2")?copy(positions.get("guard2")):copy(guard2);}
    @Override public Map<String,double[]> positions(){return positions==null?Map.of():new BossPlacement(bossId(),zoneId,positions).positions();}
    double[] position(String role){return placement().position(role);}
}
