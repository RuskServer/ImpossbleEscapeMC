package com.lunar_prototype.impossbleEscapeMC.modules.quest.reward;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import org.bukkit.entity.Player;

/**
 * 資金を付与する報酬
 */
public class MoneyReward extends QuestReward {
    private final double amount;

    public MoneyReward(double amount) {
        super("MONEY");
        this.amount = amount;
    }

    @Override
    public void apply(Player player, PlayerData data, QuestModule module) {
        data.setBalance(data.getBalance() + amount);
    }

    @Override
    public String getDescription() {
        return "報酬金: " + amount + " ₽";
    }
}
