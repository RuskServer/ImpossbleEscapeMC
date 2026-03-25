package com.lunar_prototype.impossbleEscapeMC.modules.quest.reward;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import org.bukkit.entity.Player;

/**
 * 経験値を付与する報酬
 */
public class ExpReward extends QuestReward {
    private final int amount;

    public ExpReward(int amount) {
        super("EXP");
        this.amount = amount;
    }

    @Override
    public void apply(Player player, PlayerData data, QuestModule module) {
        data.setExperience(data.getExperience() + amount);
        // レベルアップ処理が必要ならここで行う
    }

    @Override
    public String getDescription() {
        return "経験値: " + amount;
    }
}
