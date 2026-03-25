package com.lunar_prototype.impossbleEscapeMC.modules.quest.objective;

import org.bukkit.configuration.ConfigurationSection;

/**
 * クエストの目標を表す抽象クラス
 */
public abstract class QuestObjective {
    protected final String type;
    protected final int amount;

    public QuestObjective(String type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    /**
     * 目標の説明文を取得
     */
    public abstract String getDescription();
}
