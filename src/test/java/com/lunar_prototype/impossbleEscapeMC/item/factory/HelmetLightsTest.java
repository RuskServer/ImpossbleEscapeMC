package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.Flashlight;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class HelmetLightsTest {
    @Test
    void keepsFaceEquipmentAndOnlyLightsWhileWorn() throws Exception {
        var server = MockBukkit.mock();
        var field = ItemRegistry.class.getDeclaredField("ITEM_MAP");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var catalog = (Map<String, ItemDefinition>) field.get(null);
        var previous = new HashMap<>(catalog);
        try {
            for (ItemDefinition def : ItemCatalog.create()) catalog.put(def.id, def);
            var helmet = item(Material.DIAMOND, "FAST-MT");
            var light = item(Material.IRON_INGOT, "flashlight");
            light.setAmount(2);
            light.editMeta(meta -> meta.getPersistentDataContainer().set(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 1));

            var equipped = HelmetLights.withLight(helmet, light);
            assertNotNull(equipped);
            assertFalse(HelmetLights.isAttached(helmet));
            assertEquals(2, light.getAmount());
            assertEquals(13, equipped.getItemMeta().getCustomModelData());
            assertEquals(1350, equipped.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER));
            assertNull(HelmetLights.withLight(equipped, light));

            var visor = HelmetVisors.withVisor(equipped, "FAST-MT_visor");
            assertEquals(14, visor.getItemMeta().getCustomModelData());
            assertEquals(1950, visor.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER));
            var pvs = HelmetVisors.withVisor(visor, "PVS-14");
            assertEquals(15, pvs.getItemMeta().getCustomModelData());
            assertEquals(13, HelmetVisors.withoutVisor(pvs).getItemMeta().getCustomModelData());
            assertEquals(10, HelmetLights.withoutLight(pvs).getItemMeta().getCustomModelData());
            assertEquals("PVS-14", HelmetVisors.visorOf(HelmetLights.withoutLight(pvs)));
            var recovered = HelmetLights.attachedItem(pvs);
            assertNotNull(recovered);
            assertEquals(1, recovered.getAmount());
            assertTrue(recovered.getItemMeta().getPersistentDataContainer().has(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN));

            var player = server.addPlayer();
            player.getInventory().setItemInMainHand(pvs);
            assertFalse(Flashlight.isLit(player));
            player.getInventory().setHelmet(pvs);
            assertTrue(Flashlight.isLit(player));
            player.setGameMode(GameMode.SPECTATOR);
            assertFalse(Flashlight.isLit(player));
        } finally {
            catalog.clear();
            catalog.putAll(previous);
            MockBukkit.unmock();
        }
    }

    private ItemStack item(Material type, String id) {
        var item = new ItemStack(type);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, id));
        return item;
    }
}
