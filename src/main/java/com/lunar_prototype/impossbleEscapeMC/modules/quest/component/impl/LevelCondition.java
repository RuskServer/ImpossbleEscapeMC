package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;

/**
 * プレイヤーレベルが一定以上であることを要求する条件
 */
public class LevelCondition implements QuestCondition {
    private final int requiredLevel;

    public LevelCondition(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    @Override
    public boolean isMet(PlayerData data) {
        return data.getLevel() >= requiredLevel;
    }

    @Override
    public String getDescription() {
        return "プレイヤーレベル " + requiredLevel + " 以上";
    }
}
