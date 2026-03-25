package com.lunar_prototype.impossbleEscapeMC.modules.quest.reward;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import org.bukkit.entity.Player;

/**
 * 取引アイテムを解放する報酬
 */
public class UnlockTradeReward extends QuestReward {
    private final String traderId;
    private final String itemId;

    public UnlockTradeReward(String traderId, String itemId) {
        super("UNLOCK_TRADE");
        this.traderId = traderId;
        this.itemId = itemId;
    }

    @Override
    public void apply(Player player, PlayerData data, QuestModule module) {
        // 取引の解放は TraderModule 側で PlayerData の完了済みクエストを参照して判定されるため、
        // ここでは特に状態変更は不要。完了リストに追加されるだけで機能する。
    }

    @Override
    public String getDescription() {
        return "取引解放: " + traderId + " (" + itemId + ")";
    }

    public String getTraderId() {
        return traderId;
    }

    public String getItemId() {
        return itemId;
    }
}
