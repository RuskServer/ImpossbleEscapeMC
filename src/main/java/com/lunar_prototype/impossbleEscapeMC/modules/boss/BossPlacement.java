package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import java.util.*;

/** 編集中の不完全な配置も保持する。配列は境界でコピーする。 */
public record BossPlacement(String bossId, String zoneId, Map<String, double[]> positions) {
    public BossPlacement {
        Objects.requireNonNull(bossId); Objects.requireNonNull(zoneId);
        Map<String, double[]> copy = new LinkedHashMap<>();
        positions.forEach((role, p) -> {
            if (role == null || p == null || p.length != 5 || Arrays.stream(p).anyMatch(v -> !Double.isFinite(v)))
                throw new IllegalArgumentException("Invalid boss position");
            copy.put(role, p.clone());
        });
        positions = Collections.unmodifiableMap(copy);
    }
    @Override public Map<String, double[]> positions() {
        Map<String, double[]> copy = new LinkedHashMap<>(); positions.forEach((k,v) -> copy.put(k,v.clone()));
        return Collections.unmodifiableMap(copy);
    }
    public double[] position(String role) { double[] p=positions.get(role); return p==null?null:p.clone(); }
    public String problem(BossDefinition definition, BossTerritory zone) {
        if (definition == null || !bossId.equals(definition.id())) return "ボス定義がありません";
        if (zone == null || !zoneId.equals(zone.id())) return "高脅威区域がありません";
        double[] anchor=anchor(definition);
        if(anchor==null)return "ボス本体の地点が未登録です";
        if(!zone.contains(anchor))return "区域外の地点があります";
        return null;
    }
    public double[] anchor(BossDefinition definition){return position(definition.leader().role());}
}
