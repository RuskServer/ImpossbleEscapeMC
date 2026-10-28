package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.Comparator;
import java.util.List;

/** 水平の退避検査で出口を拾えない低層地点を、地下・立体通路の候補として分ける。 */
final class SpawnSiteAssessment {
    static RaidMap.SpawnEntry upperReference(RaidMap.SpawnEntry site,List<RaidMap.SpawnEntry> others,
                                            int exits,boolean unknown,boolean unsafe,double minDrop) {
        if(exits!=0||unknown||unsafe||!Double.isFinite(site.y())||!Double.isFinite(minDrop)||minDrop<=0)return null;
        return others.stream().filter(other->other.id()!=site.id()&&Double.isFinite(other.y())&&other.y()-site.y()>=minDrop)
                .min(Comparator.comparingDouble(other->other.y()-site.y())).orElse(null);
    }
}
