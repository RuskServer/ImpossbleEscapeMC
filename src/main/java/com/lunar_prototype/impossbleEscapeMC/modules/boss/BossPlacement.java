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
        var roles=definition.members().stream().map(BossDefinition.Member::role).toList();
        if (!positions.keySet().containsAll(roles)) return "部隊の地点が未登録です";
        if (!roles.containsAll(positions.keySet())) return "定義にない部隊員の地点があります";
        var points=roles.stream().map(positions::get).toList();
        for (double[] p:points) if (!zone.contains(p)) return "区域外の地点があります";
        for (int i=0;i<points.size();i++) for (int j=0;j<i;j++)
            if (distance(points.get(i),points.get(j)) < 2.25) return "地点間は1.5ブロック以上離してください";
        double[] leader=positions.get(definition.members().stream().filter(BossDefinition.Member::leader).findFirst().orElseThrow().role());
        for (double[] p:points) if (distance(leader,p)>144) return "護衛はボスから12ブロック以内に配置してください";
        return null;
    }
    private static double distance(double[] a,double[] b) {
        return Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2);
    }
}
