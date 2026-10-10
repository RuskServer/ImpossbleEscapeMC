package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;

/** Shared catalog lookup for quest descriptions, validation and hand-in. */
public final class QuestItems {
    public record Definition(String id, String type, String name) {}
    private QuestItems() {}
    public static Definition resolve(String id) {
        if (id == null) return null;
        var attachment = AttachmentItems.resolve(id);
        if (attachment != null) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(attachment.name());
            // Translatable component's fallback is the human-readable server-side name.
            if (attachment.name() instanceof net.kyori.adventure.text.TranslatableComponent translated
                    && translated.fallback() != null) name = translated.fallback();
            return new Definition(id, "ATTACHMENT", name);
        }
        var item = ItemRegistry.get(id);
        if (item != null) return new Definition(id, item.type, item.displayName);
        var ammo = ItemRegistry.getAmmo(id);
        if (ammo != null) return new Definition(id, "AMMO", ammo.displayName);
        var gun = DatapackGunCatalog.get(id);
        return gun != null ? new Definition(id, "GUN", gun.displayName()) : null;
    }
}
