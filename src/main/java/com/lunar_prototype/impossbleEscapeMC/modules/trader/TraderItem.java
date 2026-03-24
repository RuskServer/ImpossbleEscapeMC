package com.lunar_prototype.impossbleEscapeMC.modules.trader;

/**
 * トレーダーが取り扱うアイテムの設定
 */
public class TraderItem {
    public String itemId;    // ItemRegistry内のID
    public double price;     // 価格
    public int dailyLimit;   // 1日の購入制限 (0なら無制限)
    public int requiredLevel; // 解放に必要なプレイヤーレベル

    public TraderItem(String itemId, double price, int dailyLimit, int requiredLevel) {
        this.itemId = itemId;
        this.price = price;
        this.dailyLimit = dailyLimit;
        this.requiredLevel = Math.max(1, requiredLevel);
    }
}
