package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Datapack owns identity/name/slot; plugin owns inventory specs and descriptive text. */
public final class AttachmentItems {
    public record Definition(String id, String slot, Component name, int weight, int cost,
                             int rarity, List<String> description, boolean datapack) {}
    private AttachmentItems() {}

    public static Definition resolve(String id) {
        if (id == null) return null;
        var info = Bukkit.getServer() == null ? null : DatapackAttachments.info(id);
        var legacy = ItemRegistry.getAttachment(id);
        return definition(id, info, legacy);
    }

    static Definition definition(String id, DatapackAttachments.Info info, AttachmentDefinition legacy) {
        if (info == null && legacy == null) return null;
        int rarity = legacy != null ? legacy.rarity : 1;
        Component name = info != null
                ? (info.translationKey().isEmpty() ? Component.text(info.fallbackName())
                   : Component.translatable(info.translationKey(), info.fallbackName()))
                : LegacyComponentSerializer.legacyAmpersand().deserialize(legacy.displayName != null ? legacy.displayName : id);
        return new Definition(id, info != null ? info.slot() : legacy.slot != null ? legacy.slot.name() : "UNKNOWN",
                name.color(color(rarity)).decoration(TextDecoration.ITALIC, false),
                legacy != null ? legacy.weight : DatapackItemSpecs.attachmentWeight(id), legacy != null ? Math.max(1, legacy.cost) : 1, rarity,
                legacy != null ? List.copyOf(legacy.description) : List.of(), info != null);
    }

    public static int weight(String id) {
        var legacy = ItemRegistry.getAttachment(id);
        return legacy != null ? legacy.weight : DatapackItemSpecs.attachmentWeight(id);
    }
    public static int cost(String id) {
        var legacy = ItemRegistry.getAttachment(id);
        return legacy != null ? Math.max(1, legacy.cost) : 1;
    }
    private static NamedTextColor color(int rarity) {
        return switch (rarity) {
            case 2 -> NamedTextColor.GREEN; case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE; case 5 -> NamedTextColor.GOLD;
            default -> NamedTextColor.WHITE;
        };
    }

    public static ItemStack create(World world, String id) {
        Definition def = resolve(id);
        if (def == null || !def.datapack() || world == null) return null;
        ItemStack item = DatapackAttachments.createItem(world, id);
        return item;
    }

    public static ItemStack wrap(ItemStack item) {
        String id = DatapackAttachments.attachmentIdOf(item);
        Definition def = resolve(id);
        if (def == null || !def.datapack()) return item;
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, id);
        pdc.set(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, def.weight());
        pdc.set(PDCKeys.ITEM_COST, PDCKeys.INTEGER, def.cost());
        meta.displayName(def.name());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("アタッチメント", NamedTextColor.GRAY));
        lore.add(Component.text("Slot: " + def.slot(), NamedTextColor.GRAY));
        lore.add(Component.text("★".repeat(Math.max(1, Math.min(5, def.rarity()))), color(def.rarity())));
        lore.add(Component.text(def.weight() + "g | Size: " + def.cost(), NamedTextColor.WHITE));
        for (String line : def.description()) lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        if (pdc.getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1)
            lore.add(Component.text("Find in Raid", NamedTextColor.GOLD));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
