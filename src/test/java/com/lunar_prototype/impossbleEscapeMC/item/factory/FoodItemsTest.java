package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.stats.FoodStats;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoodItemsTest {
    @Test
    void beefCanUsesNativeFoodAndSurvivesMetadataRefresh() {
        MockBukkit.mock();
        try {
            var def = ItemCatalog.create().stream().filter(d -> d.id.equals("beef_can")).findFirst().orElseThrow();
            var item = new ItemStack(Material.IRON_INGOT);
            var meta = item.getItemMeta();
            meta.setCustomModelData(def.customModelData);
            meta.lore(List.of(Component.text("existing lore")));
            item.setItemMeta(meta);
            FoodItems.apply(item, def.foodStats);
            var food = item.getData(DataComponentTypes.FOOD);
            assertNotNull(food);
            assertEquals(8, food.nutrition());
            assertEquals(8.0f, food.saturation());
            assertFalse(food.canAlwaysEat());
            var consumable = item.getData(DataComponentTypes.CONSUMABLE);
            assertNotNull(consumable);
            assertEquals(3.0f, consumable.consumeSeconds());
            assertEquals(ItemUseAnimation.EAT, consumable.animation());
            assertFalse(consumable.hasConsumeParticles());
            assertEquals(28, item.getItemMeta().getCustomModelData());
            assertEquals(Component.text("existing lore"), item.getItemMeta().lore().getFirst());
            item.setItemMeta(item.getItemMeta());
            assertEquals(food, item.getData(DataComponentTypes.FOOD));
            assertEquals(consumable, item.getData(DataComponentTypes.CONSUMABLE));
            assertEquals(350, def.weight);
            assertEquals(1, def.cost);
            assertEquals("FOOD", def.type);
            assertNull(new ItemStack(Material.IRON_INGOT).getData(DataComponentTypes.CONSUMABLE));
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void invalidFoodStatsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FoodStats(0, 8, 3));
        assertThrows(IllegalArgumentException.class, () -> new FoodStats(8, Float.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> new FoodStats(8, 8, -1));
    }
}
