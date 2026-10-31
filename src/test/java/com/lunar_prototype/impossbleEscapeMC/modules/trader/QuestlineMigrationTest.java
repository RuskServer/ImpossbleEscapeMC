package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.google.gson.*;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.*;
import java.util.*;

/** 旧JSON・全取引資格・交換の分離・リセット範囲・再読込を検証する。 */
public final class QuestlineMigrationTest {
    private static final Gson GSON=new Gson();
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static PlayerData legacy(String... completed) {
        var data=new PlayerData(UUID.randomUUID());data.setLevel(20);data.setBalance(123456);data.setExperience(4321);
        data.getDailyPurchases().put("bastion_PVS-14",1);
        data.getHideoutLevels().put("workbench",2);
        for(String id:completed)data.completeQuest(id);
        var active=new ActiveQuest("supply_route");active.setProgress(1,2);data.getActiveQuests().put("supply_route",active);
        var json=GSON.toJsonTree(data).getAsJsonObject();json.remove("questlineVersion");json.remove("retainedTradeUnlocks");
        return GSON.fromJson(json,PlayerData.class);
    }
    private static JsonObject nonQuest(PlayerData data) {
        var json=GSON.toJsonTree(data).getAsJsonObject();
        for(String field:List.of("questlineVersion","retainedTradeUnlocks","activeQuests","completedQuests"))json.remove(field);
        return json;
    }
    private static TraderItem offer(String trader,String item,boolean barter) {
        return TraderCatalog.create().stream().filter(t->t.id.equals(trader)).flatMap(t->t.items.stream())
                .filter(i->i.itemId.equals(item)&&i.isBarter()==barter).findFirst().orElseThrow();
    }
    public static void main(String[] args) {
        var module=new TraderModule(null);
        var data=legacy("bastion_night_optics_trial","kovacs_rewiring","kovacs_uzi_parts");
        var before=nonQuest(data);check(data.getQuestlineVersion()==0,"missing JSON version is legacy");
        check(QuestlineMigration.migrate(data),"legacy migrates");
        check(data.getActiveQuests().isEmpty()&&data.getCompletedQuests().isEmpty(),"only quest histories reset");
        check(nonQuest(data).equals(before),"all non-quest fields including cash, EXP, hideout and purchase counts preserved");
        check(module.isUnlocked(data,offer("bastion","PVS-14",true)),"earned NVG barter retained");
        check(!module.isUnlocked(data,offer("bastion","PVS-14",false)),"NVG cash sale not invented");
        check(module.isUnlocked(data,offer("kovacs","556x45_m855",true)),"earned ammo barter retained");
        check(!module.isUnlocked(data,offer("kovacs","556x45_m855",false)),"ammo cash sale remains locked");
        check(module.isUnlocked(data,offer("kovacs","uzi_folded_stock",false)),"second reward from one quest retained");
        check(module.getUnlockRequirements(data,offer("bastion","PVS-14",true)).contains("取引解放済み"),"retained qualification is not displayed as an incomplete quest");
        data.setLevel(1);check(!module.isUnlocked(data,offer("bastion","PVS-14",true)),"retained grants do not waive level");data.setLevel(20);
        data.getActiveQuests().put("supply_route",new ActiveQuest("supply_route"));data.completeQuest("field_deployment");
        String persisted=GSON.toJson(data);var loaded=GSON.fromJson(persisted,PlayerData.class);
        check(!QuestlineMigration.migrate(loaded),"migration is one-time after JSON round trip");
        check(GSON.toJsonTree(loaded).equals(JsonParser.parseString(persisted)),"new progress not reset on rerun");
        check(module.isUnlocked(loaded,offer("bastion","PVS-14",true)),"retained unlock survives serialization");
        var fresh=new PlayerData(UUID.randomUUID());fresh.completeQuest("supply_route");
        check(!QuestlineMigration.migrate(fresh)&&fresh.isQuestCompleted("supply_route"),"fresh players start on current version");
        var future=legacy();future.setQuestlineVersion(QuestlineMigration.CURRENT_VERSION+1);
        check(!QuestlineMigration.migrate(future)&&!future.getActiveQuests().isEmpty(),"future version is not downgraded");
        var all=TraderCatalog.create();var ids=new HashSet<String>();
        for(var trader:all)for(var item:trader.items)if(item.requiredQuestId!=null)ids.add(item.requiredQuestId);
        var every=legacy(ids.toArray(String[]::new));QuestlineMigration.migrate(every);int grants=0;
        for(var trader:all)for(var item:trader.items)if(item.requiredQuestId!=null) {
            grants++;check(every.hasRetainedTradeUnlock(item.retainedUnlockKey),"legacy table covers "+item.retainedUnlockKey);
        }
        check(grants==40,"all forty legacy gated offers covered");
        var unknown=legacy("unknown_quest");QuestlineMigration.migrate(unknown);
        check(!module.isUnlocked(unknown,offer("bastion","PVS-14",true)),"unknown completion cannot grant a sale");
        System.out.println("QuestlineMigrationTest passed: old JSON, forty offers, cash/barter isolation, persistence, level gate and one-time reset");
    }
}
