package com.lunar_prototype.impossbleEscapeMC.modules.quest.reward;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import org.bukkit.entity.Player;

/**
 * クエストの報酬を表す抽象クラス
 */
public abstract class QuestReward {
    protected final String type;

    public QuestReward(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * 報酬をプレイヤーに付与する
     */
    public abstract void apply(Player player, PlayerData data, QuestModule module);

    /**
     * 報酬の説明文を取得
     */
    public abstract String getDescription();
}
