package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.stats.FoodStats;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

/** 消費・中断・空腹時の使用制限は標準の食事処理に任せる。 */
final class FoodItems {
    private FoodItems() {}

    static void apply(ItemStack item, FoodStats stats) {
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(stats.nutrition()).saturation(stats.saturation()).canAlwaysEat(false).build());
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(stats.consumeSeconds()).animation(ItemUseAnimation.EAT)
                .sound(Key.key("minecraft:entity.generic.eat")).hasConsumeParticles(false).build());
        var meta = item.getItemMeta();
        var lore = meta.lore() == null ? new ArrayList<Component>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("空腹度 +" + stats.nutrition() + " / 20", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("右クリック長押しで食べる（" + stats.consumeSeconds() + "秒）", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
