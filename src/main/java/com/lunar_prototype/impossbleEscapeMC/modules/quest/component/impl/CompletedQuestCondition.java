package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;

/**
 * 特定のクエストが完了していることを要求する条件
 */
public class CompletedQuestCondition implements QuestCondition {
    private final String requiredQuestId;
    private final QuestModule questModule;

    public CompletedQuestCondition(String requiredQuestId, QuestModule questModule) {
        this.requiredQuestId = requiredQuestId;
        this.questModule = questModule;
    }

    @Override
    public boolean isMet(PlayerData data) {
        return data.isQuestCompleted(requiredQuestId);
    }

    @Override
    public String getDescription() {
        QuestDefinition def = questModule.getQuest(requiredQuestId);
        String name = (def != null) ? def.getDisplayName() : requiredQuestId;
        return "クエスト「" + name + "」の完了";
    }

    public String getRequiredQuestId() {
        return requiredQuestId;
    }
}
