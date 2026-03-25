package com.lunar_prototype.impossbleEscapeMC.modules.market;

import java.util.UUID;

/**
 * マーケットの出品情報を保持するデータクラス
 */
public class MarketListing {
    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final String itemBase64;
    private final double price;
    private final long listDate;

    public MarketListing(UUID id, UUID sellerUuid, String sellerName, String itemBase64, double price, long listDate) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemBase64 = itemBase64;
        this.price = price;
        this.listDate = listDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getItemBase64() {
        return itemBase64;
    }

    public double getPrice() {
        return price;
    }

    public long getListDate() {
        return listDate;
    }
}
