package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** 全地点の比較順と、未計算の相手も含む接敵時間の集計。 */
final class SpawnContactSummary {
    record Comparison(int a, int b) {}
    record Pair(int a, int b, double distance, Double seconds, String note) {}
    record Site(Pair earliest, int opponent, int unknown, int direct) {}

    // 最初のラウンドから各地点を比較する。ID順の総当たりでは時間切れが後半に偏る。
    static List<Comparison> comparisons(int count) {
        List<Comparison> result = new ArrayList<>();
        int size = count + count % 2;
        if (size < 2) return result;
        int[] ring = new int[size];
        for (int i = 0; i < size; i++) ring[i] = i;
        for (int round = 0; round < size - 1; round++) {
            for (int i = 0; i < size / 2; i++) {
                int a = ring[i], b = ring[size - 1 - i];
                if (a < count && b < count) result.add(new Comparison(Math.min(a, b), Math.max(a, b)));
            }
            int last = ring[size - 1];
            for (int i = size - 1; i > 1; i--) ring[i] = ring[i - 1];
            ring[1] = last;
        }
        return List.copyOf(result);
    }

    static Map<String,Integer> unknownReasons(int count,List<Pair> pairs) {
        Map<String,Integer> reasons=new LinkedHashMap<>();
        for(Pair pair:pairs) {
            if(pair.seconds!=null&&pair.note.isEmpty())continue;
            String reason=pair.note.isEmpty()?"理由未取得":pair.note;
            reasons.merge(reason,1,Integer::sum);
        }
        int missing=Math.max(0,count*(count-1)/2-pairs.size());
        if(missing>0)reasons.put("診断未実施（ワールド・範囲などを確認）",missing);
        return reasons;
    }

    static Site site(int id, int count, List<Pair> pairs) {
        Pair earliest = null;
        int opponent = -1, complete = 0, direct = 0;
        for (Pair pair : pairs) {
            if (pair.a != id && pair.b != id) continue;
            if (pair.seconds == null) continue;
            if (pair.note.isEmpty()) complete++;
            if (pair.seconds == 0) direct++;
            int other = pair.a == id ? pair.b : pair.a;
            if (earliest == null || pair.seconds < earliest.seconds
                    || (pair.seconds.equals(earliest.seconds) && other < opponent)) {
                earliest = pair;
                opponent = other;
            }
        }
        return new Site(earliest, opponent, Math.max(0, count - 1 - complete), direct);
    }
}
