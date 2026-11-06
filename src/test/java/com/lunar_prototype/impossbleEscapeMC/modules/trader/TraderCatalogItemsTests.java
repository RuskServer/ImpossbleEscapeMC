package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交換の取引の、もらう品物と渡す素材が登録済みのアイテム (ItemCatalog・AmmoCatalog) か。
 * 起動時の警告ログ (TraderModule.validateItems) と同じ確認を、起動なしでする。
 * <p>
 * 銃・アタッチメントは GameItems 経由で NMS (net.minecraft) に触れ、MockBukkit では動かないので、ここでは扱わない。
 */
class TraderCatalogItemsTests {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        ItemRegistry.loadAllItems(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static boolean registered(String id) {
        return ItemRegistry.get(id) != null || ItemRegistry.getAmmo(id) != null;
    }

    @Test
    void barterResultsAndMaterialsAreRegisteredItems() {
        List<String> missing = new ArrayList<>();
        int barters = 0;
        for (TraderDefinition trader : TraderCatalog.create()) {
            for (TraderItem item : trader.items) {
                if (!item.isBarter()) continue;
                barters++;
                if (!registered(item.itemId)) missing.add(trader.id + ": もらう品物 " + item.itemId);
                for (var cost : item.barter) {
                    if (!registered(cost.itemId())) missing.add(trader.id + ": " + item.itemId + " の素材 " + cost.itemId());
                }
            }
        }
        assertTrue(barters > 0, "交換の取引が1件もない (カタログが変わった?)");
        assertTrue(missing.isEmpty(), "登録されていない品物: " + missing);
    }

    @Test
    void barterMaterialsAreNotTheItemTheyProduce() {
        List<String> same = new ArrayList<>();
        for (TraderDefinition trader : TraderCatalog.create()) {
            for (TraderItem item : trader.items) {
                if (!item.isBarter()) continue;
                for (var cost : item.barter) {
                    if (cost.itemId().equals(item.itemId)) same.add(trader.id + ": " + item.itemId);
                }
            }
        }
        assertTrue(same.isEmpty(), "渡す素材ともらう品物が同じ交換: " + same);
    }
}
