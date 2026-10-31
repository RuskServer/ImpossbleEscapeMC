package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.*;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.*;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import java.util.*;

/** 報酬・導線と、未実装の目標を混入していないことを確認する。 */
public final class QuestlineRebuildTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static Set<String> prerequisites(QuestDefinition q) {
        var ids=new HashSet<String>();
        for(var c:q.getConditions())if(c instanceof CompletedQuestCondition p)ids.add(p.getRequiredQuestId());
        return ids;
    }
    public static void main(String[] args) {
        var quests=QuestCatalog.create(null);var byId=new HashMap<String,QuestDefinition>();
        long money=0,exp=0;int extended=0;
        Set<String> knownPoints=Set.of("factory:100.0,-33.0,98.0","factory:178.0,-33.0,119.0",
                "factory:195.0,-34.0,107.0","factory:160.0,-34.0,184.0","factory:142.0,-34.0,208.0",
                "costal:-192.0,1.0,84.0","costal:-256.0,1.0,84.0");
        for(var q:quests) {
            byId.put(q.getId(),q);
            for(var reward:q.getRewards()) {
                if(reward instanceof MoneyReward m)money+=(long)m.getAmount();
                else if(reward instanceof ExpReward e)exp+=e.getAmount();
            }
            for(var o:q.getObjectives()) {
                check(o instanceof HandInObjective||o instanceof KillEntityObjective||o instanceof ReachLocationObjective||o instanceof ExtractObjective,"existing objective types only");
                if(o instanceof ReachLocationObjective point)check(knownPoints.contains(point.getWorldName()+":"+point.getX()+","+point.getY()+","+point.getZ()),"known location only");
                if(o instanceof ExtractObjective e)check(e.getMapId()==null||Set.of("ノヴォザリエ薬品工場","ノヴォザリエ沿岸工業地区").contains(e.getMapId()),"known maps only");
            }
            if(q.getObjectives().size()>=4)extended++;
        }
        check(quests.size()==51&&money==1806000&&exp==170250,"all fifty-one approved reward proposals applied");
        check(extended>=10,"multi-objective work added to short middle-game tasks");
        check(prerequisites(byId.get("kovacs_recon_supply")).equals(Set.of("kovacs_data_recovery")),"recon mainline bypasses optional headshot drill");
        check(prerequisites(byId.get("bastion_spectre_camp_recon")).equals(Set.of("bastion_coastal_warehouses")),"camp mainline bypasses weapon drill");
        check(prerequisites(byId.get("kovacs_data_recovery")).equals(Set.of("kovacs_eastern_supply","kovacs_western_supply")),"both supply branches feed data work");
        check(prerequisites(byId.get("pharmakon_emergency_stock")).equals(Set.of("pharmakon_quiet_escort")),"UN line ends in continued activity stock");
        check(byId.get("kovacs_data_recovery").getDescription().contains("Spectre")
                &&byId.get("pharmakon_emergency_stock").getDescription().contains("管理")
                &&byId.get("bastion_monolith_prototype").getDescription().contains("施設"),"faction goals represented");
        var extracts=byId.get("kovacs_penetration").getObjectives().stream().filter(o->o instanceof ExtractObjective).map(o->(ExtractObjective)o).toList();
        check(extracts.size()==2,"separate factory and coast extraction objectives");
        var active=new ActiveQuest("kovacs_penetration");
        for(int i=0;i<extracts.size();i++)extracts.get(i).updateProgress(null,null,active,i,QuestTrigger.RAID_EXTRACT,Map.of("mapId","ノヴォザリエ薬品工場"));
        check(active.getProgress(0)+active.getProgress(1)==1,"one map cannot satisfy both extraction objectives");
        for(int i=0;i<extracts.size();i++)extracts.get(i).updateProgress(null,null,active,i,QuestTrigger.RAID_EXTRACT,Map.of("mapId","ノヴォザリエ沿岸工業地区"));
        check(active.getProgress(0)+active.getProgress(1)==2,"second map completes pair");
        System.out.println("QuestlineRebuildTest passed: 51 quests, reward totals, faction progression, known points and per-map extraction");
    }
}
