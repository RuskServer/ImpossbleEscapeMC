package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

/** 残弾数などを含む銃の動的なアイテム名を、リザルトへ持ち込まない。 */
record RaidWeaponPresentation(ItemStack icon, String name) {
    static RaidWeaponPresentation capture(ItemStack held, String gunId, String catalogName) {
        if (held == null || held.getType().isAir()) return new RaidWeaponPresentation(null, null);
        ItemStack icon = held.asOne();
        if (gunId == null) {
            return new RaidWeaponPresentation(icon,
                    PlainTextComponentSerializer.plainText().serialize(held.effectiveName()));
        }
        // 登録がなくなった銃でも、動的な表示名には戻さず安定したIDを使う。
        String name = catalogName == null || catalogName.isBlank() ? gunId : catalogName;
        name = PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(name));
        var meta = icon.getItemMeta();
        Component label = Component.text(name).decoration(TextDecoration.ITALIC, false);
        meta.customName(label);
        meta.itemName(label);
        icon.setItemMeta(meta);
        return new RaidWeaponPresentation(icon, name);
    }
}
