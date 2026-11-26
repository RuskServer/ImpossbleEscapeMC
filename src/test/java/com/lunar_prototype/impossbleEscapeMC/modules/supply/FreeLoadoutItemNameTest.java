package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class FreeLoadoutItemNameTest {
    @Test void readsDatapackItemNameAndPreservesCustomNamePriority() {
        MockBukkit.mock();
        try {
            var gun = new ItemStack(Material.CROSSBOW);
            gun.editMeta(meta -> meta.itemName(Component.text("Mossberg 590")));
            assertNull(gun.getItemMeta().displayName());
            assertEquals("Mossberg 590", PlainTextComponentSerializer.plainText().serialize(SupplyLoadoutGUI.itemName(gun)));
            assertNull(gun.getItemMeta().customName());
            gun.editMeta(meta -> meta.customName(Component.text("Custom weapon")));
            assertEquals("Custom weapon", PlainTextComponentSerializer.plainText().serialize(SupplyLoadoutGUI.itemName(gun)));
            assertEquals(Component.text("Mossberg 590"), gun.getItemMeta().itemName());
        } finally { MockBukkit.unmock(); }
    }
}
