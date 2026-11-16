package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class RaidWeaponPresentationTest {
    @BeforeEach void start() { MockBukkit.mock(); }
    @AfterEach void stop() { MockBukkit.unmock(); }

    private ItemStack named(String name) {
        // MockBukkitのeffectiveNameは未実装なので、このテストでは付けた名前を返す。
        var item = new ItemStack(Material.CROSSBOW, 2) {
            @Override public Component effectiveName() { return getItemMeta().customName(); }
        };
        var meta = item.getItemMeta();
        meta.customName(Component.text(name));
        meta.setCustomModelData(37);
        item.setItemMeta(meta);
        return item;
    }

    private String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    @Test
    void catalogNameReplacesTheEntireDynamicNameWithoutChangingTheRealGun() {
        var gun = named("AK-74M [17 / 30] AUTO 5.45x39mm");
        var originalName = gun.getItemMeta().customName();
        var result = RaidWeaponPresentation.capture(gun, "ak74", "AK-74M");
        assertEquals("AK-74M", result.name());
        assertEquals("AK-74M", plain(result.icon().getItemMeta().customName()));
        assertEquals("AK-74M", plain(result.icon().getItemMeta().itemName()));
        assertEquals(37, result.icon().getItemMeta().getCustomModelData());
        assertEquals(Material.CROSSBOW, result.icon().getType());
        assertEquals(1, result.icon().getAmount());
        assertEquals(originalName, gun.getItemMeta().customName());
        assertEquals(2, gun.getAmount());
    }

    @Test
    void missingCatalogEntryUsesStableIdAndKeepsNumbersInRealModelNames() {
        var gun = named("0/8 RELOADING");
        assertEquals("custom_gun_590", RaidWeaponPresentation.capture(gun, "custom_gun_590", null).name());
        assertEquals("custom_gun_590", RaidWeaponPresentation.capture(gun, "custom_gun_590", " ").name());
        assertEquals("Mossberg 590", RaidWeaponPresentation.capture(gun, "mossberg_590", "Mossberg 590").name());
        assertEquals("USP 45", RaidWeaponPresentation.capture(gun, "usp45", "USP 45").name());
    }

    @Test
    void ordinaryItemsKeepTheirNamesAndEmptyHandsStayUnknown() {
        var item = named("Special blade 2/3");
        assertEquals("Special blade 2/3", RaidWeaponPresentation.capture(item, null, null).name());
        assertEquals(item.getItemMeta().customName(), RaidWeaponPresentation.capture(item, null, null).icon().getItemMeta().customName());
        assertNull(RaidWeaponPresentation.capture(null, null, null).name());
        assertNull(RaidWeaponPresentation.capture(new ItemStack(Material.AIR), null, null).icon());
    }
}
