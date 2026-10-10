package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.*;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.UnlockTradeReward;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.*;
import java.util.*;

/** Standalone catalog and progression regression checks; does not start Bukkit. */
public final class QuestExpansionTest {
    private static final Set<String> LEGACY = Set.of("supply_route", "field_deployment", "optic_calibration",
            "kovacs_marksman_01", "kovacs_marksman_02", "first_aid_basics", "stop_the_bleed", "sanitary_maintenance",
            "bastion_logistics_01", "bastion_logistics_02", "bastion_logistics_03");
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    @SuppressWarnings("unchecked")
    private static <T> List<T> catalog(String name, String method) throws Exception {
        var m = Class.forName(name).getDeclaredMethod(method); m.setAccessible(true); return (List<T>) m.invoke(null);
    }
    @SuppressWarnings("unchecked")
    private static <T> Map<String,T> registry(String name) throws Exception {
        var f = ItemRegistry.class.getDeclaredField(name); f.setAccessible(true); return (Map<String,T>) f.get(null);
    }
    private static void visit(String id, Map<String,QuestDefinition> quests, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) return;
        check(visiting.add(id), "prerequisite cycle: " + id);
        for (var condition : quests.get(id).getConditions()) if (condition instanceof CompletedQuestCondition c) {
            check(quests.containsKey(c.getRequiredQuestId()), "unknown prerequisite");
            visit(c.getRequiredQuestId(), quests, visiting, done);
        }
        visiting.remove(id); done.add(id);
    }
    public static void main(String[] args) throws Exception {
        List<ItemDefinition> items = catalog("com.lunar_prototype.impossbleEscapeMC.item.ItemCatalog", "create");
        List<AmmoDefinition> ammo = catalog("com.lunar_prototype.impossbleEscapeMC.item.AmmoCatalog", "create");
        Map<String,ItemDefinition> itemMap = registry("ITEM_MAP");
        Map<String,AmmoDefinition> ammoMap = registry("AMMO_MAP");
        items.forEach(i -> itemMap.put(i.id, i)); ammo.forEach(i -> ammoMap.put(i.id, i));
        var definitions = QuestCatalog.create(null);
        check(definitions.size() == 30, "30 quests");
        Map<String,QuestDefinition> byId = new HashMap<>();
        Map<String,Integer> counts = new HashMap<>();
        Set<String> calibers = new HashSet<>();
        List<com.lunar_prototype.impossbleEscapeMC.loot.LootTable> loot = catalog(
                "com.lunar_prototype.impossbleEscapeMC.loot.LootCatalog", "tables");
        Set<String> available = new HashSet<>(); loot.forEach(t -> t.items.forEach(e -> available.add(e.itemId)));
        for (var q : definitions) {
            check(byId.put(q.getId(),q) == null, "duplicate ID");
            counts.merge(q.getTraderId(),1,Integer::sum);
            check(!q.getTraderId().equals("broker"), "broker has no quests");
            if (LEGACY.contains(q.getId())) continue;
            for (var condition : q.getConditions()) if (condition instanceof CompletedQuestCondition c)
                check(!LEGACY.contains(c.getRequiredQuestId()), "new quest depends on old map chain");
            for (var objective : q.getObjectives()) {
                check(!(objective instanceof ReachLocationObjective), "new quest has location objective");
                if (objective instanceof ExtractObjective) check(field(objective,"mapId") == null,"new quest map restricted");
                if (objective instanceof HandInObjective h) {
                    check(h.isRequireFIR(), "new hand-in requires FIR");
                    if (h.getItemId() != null) check(available.contains(h.getItemId()), "missing loot source: " + h.getItemId());
                    if (h.getCaliber() != null) calibers.add(h.getCaliber());
                    check(available.stream().anyMatch(id -> h.matches(id, ammoMap.containsKey(id) ? "AMMO"
                            : itemMap.containsKey(id) ? itemMap.get(id).type : "ATTACHMENT", true)), "no matching loot: " + q.getId());
                }
            }
        }
        check(byId.keySet().containsAll(LEGACY), "legacy IDs preserved");
        check(counts.equals(Map.of("kovacs",14,"pharmakon",6,"bastion",10)), "trader quest counts");
        check(calibers.size()==8, "all eight calibers covered");
        for (String id : byId.keySet()) visit(id,byId,new HashSet<>(),new HashSet<>());
        List<TraderDefinition> traders = catalog("com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderCatalog", "create");
        var traderModule = new TraderModule(null);
        for (var q : definitions) for (var reward : q.getRewards()) if (reward instanceof UnlockTradeReward unlock) {
            var trader = traders.stream().filter(t -> t.id.equals(unlock.getTraderId())).findFirst().orElseThrow();
            var sale = trader.items.stream().filter(i -> i.itemId.equals(unlock.getItemId())
                    && q.getId().equals(i.requiredQuestId)).findFirst().orElseThrow();
            var data = new PlayerData(new UUID(0,1)); data.setLevel(100);
            check(!traderModule.isUnlocked(data,sale),"level alone must not unlock");
            data.completeQuest(q.getId()); check(traderModule.isUnlocked(data,sale),"quest completion unlocks");
            data.setLevel(1); check(traderModule.isUnlocked(data,sale)==(sale.requiredLevel<=1),"level gate retained");
        }
        for (var trader : traders) for (var sale : trader.items) if (sale.requiredQuestId != null)
            check(byId.containsKey(sale.requiredQuestId), "sale prerequisite exists");
        var h = new HandInObjective(null,"AMMO",60,true,"5.45x39mm",3,null);
        check(!h.matches("545x39_ps","AMMO",false),"non FIR rejected");
        check(!h.matches("556x45_hp","AMMO",true),"wrong caliber rejected");
        check(!h.matches("545x39_hp","AMMO",true),"low class rejected");
        check(!h.matches("missing","AMMO",true),"unknown ammo rejected");
        var active = new ActiveQuest("test");
        check(h.updateProgress(null,null,active,0,QuestTrigger.HAND_IN,Map.of("itemId","545x39_ps","itemType","AMMO","isFIR",true,"amount",40)),"first ammo stack");
        check(h.updateProgress(null,null,active,0,QuestTrigger.HAND_IN,Map.of("itemId","545x39_pp","itemType","AMMO","isFIR",true,"amount",30)),"mixed ammo stacks");
        check(active.getProgress(0)==60,"progress capped at remaining amount");
        check(!h.updateProgress(null,null,active,0,QuestTrigger.HAND_IN,Map.of("itemId","545x39_ps","itemType","AMMO","isFIR",true,"amount",10)),"completed does not consume");
        var invalid = new ActiveQuest("invalid");
        check(!h.updateProgress(null,null,invalid,0,QuestTrigger.HAND_IN,Map.of("itemId","545x39_ps","itemType","AMMO","isFIR",true,"amount",-1)),"negative amount rejected");
        var armor = new HandInObjective(null,"ARMOR",1,true,null,null,3);
        check(!armor.matches("UNTAR","ARMOR",true),"class2 armor rejected");
        check(armor.matches("FAST-MT","ARMOR",true)&&armor.matches("Slick","ARMOR",true),"class3 and class4 accepted");
        var med = new HandInObjective(null,"MED",6,true);
        check(med.matches("ai2","MED",true)&&med.matches("CAT","MED",true),"mixed medicine accepted");
        var extract = new ExtractObjective(null,2); var extraction = new ActiveQuest("extract");
        extract.updateProgress(null,null,extraction,0,QuestTrigger.RAID_EXTRACT,Map.of("mapId","map_a"));
        check(!extract.isCompleted(extraction,0),"first extract partial");
        extract.updateProgress(null,null,extraction,0,QuestTrigger.RAID_EXTRACT,Map.of("mapId","map_b"));
        check(extract.isCompleted(extraction,0),"different maps counted");
        System.out.println("QuestExpansionTest: all checks passed (30 quests, 19 independent additions, 8 calibers, loot, unlocks, FIR progression)");
    }
}
