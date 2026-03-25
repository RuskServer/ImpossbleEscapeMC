package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.*;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.*;
import org.bukkit.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * コンポーネント方式のクエストYAML解析を行うクラス
 */
public class QuestParser {

    public static QuestDefinition parse(String id, ConfigurationSection section, QuestModule questModule) {
        if (section == null) return null;

        String traderId = section.getString("trader_id");
        String displayName = section.getString("display_name", id);
        String description = section.getString("description", "");

        // 受領条件の解析 (Conditions)
        List<QuestCondition> conditions = new ArrayList<>();
        if (section.contains("conditions")) {
            for (Map<?, ?> map : section.getMapList("conditions")) {
                conditions.add(parseCondition(map, questModule));
            }
        }

        // 目標の解析 (Objectives)
        List<QuestObjective> objectives = new ArrayList<>();
        if (section.contains("objectives")) {
            for (Map<?, ?> map : section.getMapList("objectives")) {
                objectives.add(parseObjective(map));
            }
        }

        // 報酬の解析 (Rewards)
        List<QuestReward> rewards = new ArrayList<>();
        if (section.contains("rewards")) {
            for (Map<?, ?> map : section.getMapList("rewards")) {
                rewards.add(parseReward(map));
            }
        }

        return new QuestDefinition(id, traderId, displayName, description, conditions, objectives, rewards);
    }

    private static QuestCondition parseCondition(Map<?, ?> map, QuestModule questModule) {
        String type = (String) map.get("type");
        if ("and".equalsIgnoreCase(type)) {
            List<Map<?, ?>> subMaps = (List<Map<?, ?>>) map.get("conditions");
            List<QuestCondition> subConditions = new ArrayList<>();
            for (Map<?, ?> subMap : subMaps) {
                subConditions.add(parseCondition(subMap, questModule));
            }
            return new AndCondition(subConditions);
        } else if ("completed_quest".equalsIgnoreCase(type)) {
            String qId = (String) map.get("quest_id");
            return new CompletedQuestCondition(qId, questModule);
        } else if ("level".equalsIgnoreCase(type)) {
            int level = ((Number) map.get("amount")).intValue();
            return new LevelCondition(level);
        }
        // 他の条件タイプもここに追加
        return data -> true; // デフォルト
    }

    private static QuestObjective parseObjective(Map<?, ?> map) {
        String type = (String) map.get("type");
        if ("kill_entity".equalsIgnoreCase(type)) {
            String entity = (String) map.get("entity");
            int amount = ((Number) map.get("amount")).intValue();
            return new KillEntityObjective(entity, amount);
        } else if ("extract".equalsIgnoreCase(type)) {
            String mapId = (String) map.get("map");
            int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            return new ExtractObjective(mapId, amount);
        } else if ("hand_in".equalsIgnoreCase(type)) {
            String itemId = (String) map.get("item_id");
            String itemType = (String) map.get("item_type");
            int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            return new HandInObjective(itemId, itemType, amount);
        }
        return null;
    }

    private static QuestReward parseReward(Map<?, ?> map) {
        String type = (String) map.get("type");
        if ("unlock_trade".equalsIgnoreCase(type)) {
            return new UnlockTradeReward((String) map.get("trader_id"), (String) map.get("item_id"));
        } else if ("exp".equalsIgnoreCase(type)) {
            return new ExpReward(((Number) map.get("amount")).intValue());
        } else if ("money".equalsIgnoreCase(type)) {
            return new MoneyReward(((Number) map.get("amount")).doubleValue());
        }
        return null;
    }
}
