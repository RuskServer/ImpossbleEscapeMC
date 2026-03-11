package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import java.util.List;

/**
 * 個別のトレーダー設定
 */
public class TraderDefinition {
    public String id;
    public String displayName;
    public TraderType type;
    public List<TraderItem> items;

    public TraderDefinition(String id, String displayName, TraderType type, List<TraderItem> items) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.items = items;
    }
}
