package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class DogTagWeaponNameTest {
    @Test void dogTagReadsDatapackItemNameWithoutChangingTheWeapon() throws Exception {
        var server = MockBukkit.mock();
        try {
            var victim = server.addPlayer(); var killer = server.addPlayer();
            var gun = new ItemStack(Material.CROSSBOW);
            gun.editMeta(meta -> meta.itemName(Component.text("Mossberg 590")));
            killer.getInventory().setItemInMainHand(gun);
            var create = CorpseManager.class.getDeclaredMethod("createDogTag", Player.class, LivingEntity.class);
            create.setAccessible(true);
            var tag = (ItemStack) create.invoke(new CorpseManager(null), victim, killer);
            var lore = tag.getItemMeta().lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
            assertTrue(lore.contains("使用武器: Mossberg 590"));
            assertFalse(lore.stream().anyMatch(line -> line.contains("CROSSBOW")));
            assertNull(killer.getInventory().getItemInMainHand().getItemMeta().customName());
            var unknown = (ItemStack) create.invoke(new CorpseManager(null), victim, null);
            assertTrue(unknown.getItemMeta().lore().stream().map(PlainTextComponentSerializer.plainText()::serialize)
                    .anyMatch(line -> line.equals("使用武器: 素手 / 不明")));
        } finally { MockBukkit.unmock(); }
    }
}
