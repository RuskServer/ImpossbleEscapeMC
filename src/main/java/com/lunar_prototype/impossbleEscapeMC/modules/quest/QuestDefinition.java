package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.QuestReward;
import java.util.List;

/**
 * クエストの静的な定義 (コンポーネント方式)
 */
public class QuestDefinition {
    private final String id;
    private final String traderId;
    private final String displayName;
    private final String description;
    
    private final List<QuestCondition> conditions; // 受領条件
    private final List<QuestObjective> objectives; // 目標
    private final List<QuestReward> rewards;       // 報酬

    public QuestDefinition(String id, String traderId, String displayName, String description, 
                           List<QuestCondition> conditions, 
                           List<QuestObjective> objectives, List<QuestReward> rewards) {
        this.id = id;
        this.traderId = traderId;
        this.displayName = displayName;
        this.description = description;
        this.conditions = conditions;
        this.objectives = objectives;
        this.rewards = rewards;
    }

    public String getId() { return id; }
    public String getTraderId() { return traderId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public List<QuestCondition> getConditions() { return conditions; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public List<QuestReward> getRewards() { return rewards; }
}
