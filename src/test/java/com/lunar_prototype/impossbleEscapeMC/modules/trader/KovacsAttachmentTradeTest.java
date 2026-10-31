package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.UnlockTradeReward;
import java.util.*;

/** 取引表示だけでなく、実際の購入ゲートと回収品の入手経路を検証する。 */
public final class KovacsAttachmentTradeTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void visit(String id,Map<String,QuestDefinition> quests,Set<String> visiting,Set<String> done) {
        if(done.contains(id))return;
        check(visiting.add(id),"quest cycle: "+id);
        for(var condition:quests.get(id).getConditions())if(condition instanceof CompletedQuestCondition c) {
            check(quests.containsKey(c.getRequiredQuestId()),"missing prerequisite");
            visit(c.getRequiredQuestId(),quests,visiting,done);
        }
        visiting.remove(id);done.add(id);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var create=Class.forName("com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestCatalog")
                .getDeclaredMethod("create",Class.forName("com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule"));
        create.setAccessible(true);
        var quests=new HashMap<String,QuestDefinition>();
        for(var q:(List<QuestDefinition>)create.invoke(null,new Object[]{null}))
            check(quests.put(q.getId(),q)==null,"unique quest ID");
        var done=new HashSet<String>();
        for(String id:quests.keySet())visit(id,quests,new HashSet<>(),done);
        var lootMethod=Class.forName("com.lunar_prototype.impossbleEscapeMC.loot.LootCatalog").getDeclaredMethod("tables");
        lootMethod.setAccessible(true);
        var loot=new HashSet<String>();
        for(var table:(List<com.lunar_prototype.impossbleEscapeMC.loot.LootTable>)lootMethod.invoke(null))
            for(var entry:table.items)loot.add(entry.itemId);
        var expected=Map.of("pso_1","kovacs_optic_reserve","uzi_short_magazine","kovacs_uzi_parts",
                "uzi_folded_stock","kovacs_uzi_parts","2_stage_suppressor","kovacs_suppressor_supply",
                "9x39mm_30round_magazine","kovacs_val_magazines");
        var kovacs=TraderCatalog.create().stream().filter(t->t.id.equals("kovacs")).findFirst().orElseThrow();
        var module=new TraderModule(null);
        for(var entry:expected.entrySet()) {
            var sales=kovacs.items.stream().filter(i->i.itemId.equals(entry.getKey())).toList();
            check(sales.size()==1,"one sale per attachment");var sale=sales.getFirst();
            check(entry.getValue().equals(sale.requiredQuestId),"sale points to unlocking quest");
            var quest=quests.get(sale.requiredQuestId);check(quest!=null&&quest.getTraderId().equals("kovacs"),"Kovacs quest exists");
            check(quest.getRewards().stream().anyMatch(r->r instanceof UnlockTradeReward u
                    &&u.getTraderId().equals("kovacs")&&u.getItemId().equals(sale.itemId)),"reward matches real sale");
            var data=new PlayerData(UUID.randomUUID());data.setLevel(100);
            check(!module.isUnlocked(data,sale),"level alone cannot bypass quest");
            data.completeQuest("kovacs_workbench");check(!module.isUnlocked(data,sale),"unrelated quest cannot unlock");
            data.completeQuest(sale.requiredQuestId);check(module.isUnlocked(data,sale),"completion opens purchase");
            data.setLevel(sale.requiredLevel-1);check(!module.isUnlocked(data,sale),"level gate still enforced");
            check(sale.dailyLimit>0&&sale.price>0,"limited paid supply");
        }
        for(String id:Set.of("kovacs_uzi_parts","kovacs_suppressor_supply","kovacs_val_magazines")) {
            var quest=quests.get(id);
            check(quest.getObjectives().stream().noneMatch(o->o instanceof KillEntityObjective),"recovery branches do not add kill grind");
            for(var objective:quest.getObjectives())if(objective instanceof HandInObjective h) {
                check(h.isRequireFIR()&&loot.contains(h.getItemId()),"FIR objective has a loot source");
                check(!h.matches(h.getItemId(),"ATTACHMENT",false),"purchased items cannot satisfy recovery");
            }
        }
        check(kovacs.items.stream().noneMatch(i->Set.of("ekp_8","infinity_magazine","no_recoil_grip",
                "scar_h_drum_magazine","svd_long_barrel","uzi_mini_barrel").contains(i.itemId)),"legacy/debug and loot-exclusive parts are not sold");
        System.out.println("KovacsAttachmentTradeTest passed: quest graph, FIR loot sources, reward-sale links, purchase gates and limited stock");
    }
}
