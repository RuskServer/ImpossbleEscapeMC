package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.stats.ArmorStats;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class TrooperAppearanceMigrationTest {
    @Test void newAndExistingTroopersUseSameModelWithoutResettingPersistentState() throws Exception {
        MockBukkit.mock();
        var field = ItemRegistry.class.getDeclaredField("ITEM_MAP"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var catalog = (Map<String,ItemDefinition>) field.get(null);
        var previous = catalog.get("Trooper");
        try {
            var def = ItemCatalog.create().stream().filter(d -> d.id.equals("Trooper")).findFirst().orElseThrow();
            catalog.put(def.id, def);
            assertEquals(3, def.armorStats.armorClass);
            assertEquals(500, def.maxDurability);
            assertEquals(8200, def.weight);
            assertEquals(6, def.cost);
            assertEquals(List.of(ArmorStats.ProtectedZone.BODY), def.armorStats.coverage);
            assertEquals(Material.IRON_INGOT.name(), def.material);
            assertEquals(30, def.customModelData);
            assertEquals("CHEST", def.armorStats.slot);
            assertEquals("iem:trooper_3d", def.armorStats.model);
            var legacy = new ItemStack(Material.IRON_CHESTPLATE);
            legacy.editMeta(meta -> {
                meta.setCustomModelData(2);
                meta.setLore(List.of("§7防護範囲 §f胴体"));
                var pdc = meta.getPersistentDataContainer();
                pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, "Trooper");
                pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, 137);
                pdc.set(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 3);
                pdc.set(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte)1);
                pdc.set(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte)1);
            });
            assertTrue(TooltipStyleMigration.fix(legacy));
            assertAppearance(legacy);
            var pdc = legacy.getItemMeta().getPersistentDataContainer();
            assertEquals(137, pdc.get(PDCKeys.DURABILITY, PDCKeys.INTEGER));
            assertEquals(3, pdc.get(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER));
            assertEquals((byte)1, pdc.get(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN));
            assertEquals((byte)1, pdc.get(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN));
            assertFalse(TooltipStyleMigration.fix(legacy));
            var vanilla = new ItemStack(Material.IRON_CHESTPLATE);
            assertFalse(TooltipStyleMigration.fix(vanilla));
            assertEquals(Material.IRON_CHESTPLATE, vanilla.getType());
        } finally {
            if (previous == null) catalog.remove("Trooper"); else catalog.put("Trooper", previous);
            MockBukkit.unmock();
        }
    }

    private void assertAppearance(ItemStack item) {
        assertEquals(Material.IRON_INGOT, item.getType());
        assertEquals(30, item.getItemMeta().getCustomModelData());
        var equippable = item.getItemMeta().getEquippable();
        assertEquals(EquipmentSlot.CHEST, equippable.getSlot());
        assertEquals(NamespacedKey.fromString("iem:trooper_3d"), equippable.getModel());
    }
}
