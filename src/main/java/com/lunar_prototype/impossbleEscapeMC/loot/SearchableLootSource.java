package com.lunar_prototype.impossbleEscapeMC.loot;

import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;

/** ブロックを持たない家具も、既存の検索・移動処理を共有するための境界。 */
public interface SearchableLootSource {
    Inventory inventory();
    PersistentDataContainer data();
    void persist();
}
