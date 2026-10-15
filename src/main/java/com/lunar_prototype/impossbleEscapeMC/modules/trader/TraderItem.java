package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;

import java.util.List;

/**
 * トレーダーが取り扱うアイテムの設定。
 * barter が空でなければ交換の取引: その品物 (持ち物 → スタッシュの順に取る) と price (0 なら無し) を渡して、count 個もらう
 */
public class TraderItem {
    public String itemId;    // ItemRegistry内のID
    public String displayName;
    public double price;     // 価格
    public int dailyLimit;   // 1日の購入制限 (0なら無制限)
    public int requiredLevel; // 解放に必要なプレイヤーレベル
    public String requiredQuestId; // 解放に必要なクエストID (nullなら無条件)
    /** 交換で渡す品物 (1 回の取引の分。空ならルーブルの取引) */
    public List<HideoutFacility.ItemCost> barter = List.of();
    /** 1 回の取引でもらう数 (交換の取引だけ。ルーブルの取引は 1 個ずつ) */
    public int count = 1;

    public TraderItem(String itemId, double price, int dailyLimit, int requiredLevel) {
        this(itemId, price, dailyLimit, requiredLevel, null);
    }

    public TraderItem(String itemId, double price, int dailyLimit, int requiredLevel, String requiredQuestId) {
        this.itemId = itemId;
        this.price = price;
        this.dailyLimit = dailyLimit;
        this.requiredLevel = Math.max(1, requiredLevel);
        this.requiredQuestId = requiredQuestId;
    }

    public boolean isBarter() {
        return barter != null && !barter.isEmpty();
    }
}
