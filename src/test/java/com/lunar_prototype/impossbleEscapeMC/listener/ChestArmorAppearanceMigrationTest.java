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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class ChestArmorAppearanceMigrationTest {
    @ParameterizedTest
    @CsvSource({"Trooper, IRON_CHESTPLATE, 30, 3, 500, 8200, 6, 2", "Slick, DIAMOND_CHESTPLATE, 31, 4, 600, 6500, 9, 1"})
    void newAndExistingArmorUsesSameModelWithoutResettingPersistentState(String id, Material oldMaterial,
            int cmd, int armorClass, int durability, int weight, int cost, int oldCmd) throws Exception {
        MockBukkit.mock();
        var field = ItemRegistry.class.getDeclaredField("ITEM_MAP"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var catalog = (Map<String,ItemDefinition>) field.get(null);
        var previous = catalog.get(id);
        try {
            var def = ItemCatalog.create().stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow();
            catalog.put(def.id, def);
            assertEquals(armorClass, def.armorStats.armorClass);
            assertEquals(durability, def.maxDurability);
            assertEquals(weight, def.weight);
            assertEquals(cost, def.cost);
            assertEquals(List.of(ArmorStats.ProtectedZone.BODY), def.armorStats.coverage);
            assertEquals(Material.IRON_INGOT.name(), def.material);
            assertEquals(cmd, def.customModelData);
            assertEquals("CHEST", def.armorStats.slot);
            assertEquals("iem:" + id.toLowerCase(java.util.Locale.ROOT) + "_3d", def.armorStats.model);
            var legacy = new ItemStack(oldMaterial);
            legacy.editMeta(meta -> {
                meta.setCustomModelData(oldCmd);
                meta.setLore(List.of("§7防護範囲 §f胴体"));
                var pdc = meta.getPersistentDataContainer();
                pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, id);
                pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, 137);
                pdc.set(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, armorClass);
                pdc.set(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte)1);
                pdc.set(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte)1);
            });
            assertTrue(TooltipStyleMigration.fix(legacy));
            assertAppearance(legacy, cmd, def.armorStats.model);
            var pdc = legacy.getItemMeta().getPersistentDataContainer();
            assertEquals(137, pdc.get(PDCKeys.DURABILITY, PDCKeys.INTEGER));
            assertEquals(armorClass, pdc.get(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER));
            assertEquals((byte)1, pdc.get(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN));
            assertEquals((byte)1, pdc.get(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN));
            assertFalse(TooltipStyleMigration.fix(legacy));
            var vanilla = new ItemStack(oldMaterial);
            assertFalse(TooltipStyleMigration.fix(vanilla));
            assertEquals(oldMaterial, vanilla.getType());
        } finally {
            if (previous == null) catalog.remove(id); else catalog.put(id, previous);
            MockBukkit.unmock();
        }
    }

    private void assertAppearance(ItemStack item, int cmd, String model) {
        assertEquals(Material.IRON_INGOT, item.getType());
        assertEquals(cmd, item.getItemMeta().getCustomModelData());
        var equippable = item.getItemMeta().getEquippable();
        assertEquals(EquipmentSlot.CHEST, equippable.getSlot());
        assertEquals(NamespacedKey.fromString(model), equippable.getModel());
    }
}
