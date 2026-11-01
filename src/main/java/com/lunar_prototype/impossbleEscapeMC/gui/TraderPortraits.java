package com.lunar_prototype.impossbleEscapeMC.gui;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/** Citizensに保存されたスキンだけを使う。名前からの照会やNPCの変更・スポーンは行わない。 */
final class TraderPortraits {
    private TraderPortraits() { }

    static ItemStack icon(TraderDefinition trader) {
        ProfileProperty texture = null;
        if (trader.npcId >= 0 && Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                texture = CitizensSkin.byId(trader.npcId);
            } catch (LinkageError | IllegalStateException unavailable) {
                Bukkit.getLogger().log(Level.FINE, "Trader portrait unavailable: " + trader.id, unavailable);
            }
        }
        if (texture == null) return new ItemStack(trader.type == TraderType.BUY ? Material.EMERALD : Material.GOLD_INGOT);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        // テクスチャ変更時にも別のプロフィールとなり、古い頭のキャッシュを引き継がない。
        var id = UUID.nameUUIDFromBytes(texture.getValue().getBytes(StandardCharsets.UTF_8));
        var profile = Bukkit.createProfileExact(id, null);
        profile.setProperty(texture);
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /** optional依存のAPIはCitizensが有効な時だけ読み込む。 */
    static final class CitizensSkin {
        static ProfileProperty byId(int id) {
            var registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            var npc = registry != null ? registry.getById(id) : null;
            return npc != null ? texture(npc) : null;
        }

        static ProfileProperty texture(net.citizensnpcs.api.npc.NPC npc) {
            // SkinTraitはcitizensapiに含まれないため、既存Traitの公開getterだけを使う。
            // NPCが未スポーンでも保存済みのtexture/signatureを取得できる。
            for (var trait : npc.getTraits()) {
                if (!"skintrait".equals(trait.getName())) continue;
                try {
                    Object value = trait.getClass().getMethod("getTexture").invoke(trait);
                    if (!(value instanceof String data) || data.isBlank()) continue;
                    Object signature = trait.getClass().getMethod("getSignature").invoke(trait);
                    return signature instanceof String signed && !signed.isBlank()
                            ? new ProfileProperty("textures", data, signed) : new ProfileProperty("textures", data);
                } catch (ReflectiveOperationException incompatible) {
                    Bukkit.getLogger().log(Level.FINE, "Cannot read Citizens skin trait", incompatible);
                }
            }
            // キャッシュがまだ空の場合は、スポーン中のプレイヤーNPCのプロフィールを参照する。
            if (npc.getEntity() instanceof Player player) {
                for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
                    if ("textures".equals(property.getName()) && !property.getValue().isBlank()) return property;
                }
            }
            return null;
        }
    }
}
