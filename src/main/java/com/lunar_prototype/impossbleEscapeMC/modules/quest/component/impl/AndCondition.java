package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 複数の条件が全て満たされていることを要求する論理条件コンポーネント
 */
public class AndCondition implements QuestCondition {
    private final List<QuestCondition> conditions;

    public AndCondition(List<QuestCondition> conditions) {
        this.conditions = conditions;
    }

    @Override
    public boolean isMet(PlayerData data) {
        return conditions.stream().allMatch(c -> c.isMet(data));
    }

    @Override
    public String getDescription() {
        return conditions.stream().map(QuestCondition::getDescription).collect(Collectors.joining(" かつ "));
    }
}
