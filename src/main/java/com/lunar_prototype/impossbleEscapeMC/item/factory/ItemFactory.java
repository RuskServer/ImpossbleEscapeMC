package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.ArmorDurability;
import com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackAttachments;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AttachmentDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.stats.Affix;
import com.lunar_prototype.impossbleEscapeMC.item.tooltip.TooltipStyle;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;

public class ItemFactory {
    private static final Random random = new Random();

    public static ItemStack create(org.bukkit.World world, String id) {
        var attachment = AttachmentItems.resolve(id);
        if (attachment != null && attachment.datapack()) return AttachmentItems.create(world, id);
        return create(id);
    }

    public static ItemStack create(String id) {
        var attachment = AttachmentItems.resolve(id);
        if (attachment != null && attachment.datapack()) {
            var worlds = org.bukkit.Bukkit.getWorlds();
            return worlds.isEmpty() ? null : AttachmentItems.create(worlds.get(0), id);
        }
        ItemDefinition def = ItemRegistry.get(id);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(id);
        AttachmentDefinition attDef = ItemRegistry.getAttachment(id);

        if (def == null && ammoDef == null && attDef == null)
            return null;

        ItemStack item;
        ItemMeta meta;

        // --- アタッチメントアイテムの生成 ---
        if (attDef != null) {
            Material mat = Material.matchMaterial(attDef.material);
            item = new ItemStack(mat == null ? Material.IRON_NUGGET : mat);
            item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, attDef.id);
            pdc.set(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, attDef.weight);
            pdc.set(PDCKeys.ITEM_COST, PDCKeys.INTEGER, attDef.cost);

            String rarityColor = getRarityColor(attDef.rarity);
            meta.setDisplayName(rarityColor + ChatColor.translateAlternateColorCodes('&', attDef.displayName));

            if (attDef.customModelData != 0) {
                meta.setCustomModelData(attDef.customModelData);
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7Type: §fATTACHMENT");
            lore.add("§7Slot: §e" + (attDef.slot != null ? attDef.slot.name() : "UNKNOWN"));
            lore.add("§7Rarity: " + getRarityStars(attDef.rarity));
            meta.setLore(lore);

            meta.setMaxStackSize(1);
            TooltipStyle.forRarity(attDef.rarity).applyTo(meta);
            item.setItemMeta(meta);
            return item;
        }

        if (ammoDef != null) {
            // --- 弾薬アイテムの生成 ---
            Material mat = Material.matchMaterial(ammoDef.material);
            item = new ItemStack(mat == null ? Material.IRON_NUGGET : mat);
            item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, ammoDef.id);
            pdc.set(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, ammoDef.weight);
            pdc.set(PDCKeys.ITEM_COST, PDCKeys.INTEGER, 1);

            String rarityColor = getRarityColor(ammoDef.rarity);
            meta.setDisplayName(rarityColor + ChatColor.translateAlternateColorCodes('&', ammoDef.displayName));
            if (ammoDef.customModelData != 0) {
                meta.setCustomModelData(ammoDef.customModelData);
            }
            meta.setItemModel(ammoModel(ammoDef.id));
            TooltipStyle.forRarity(ammoDef.rarity).applyTo(meta);
            item.setItemMeta(meta);
        } else {
            Material mat = Material.matchMaterial(def.material);
            if (mat == null)
                mat = Material.BARRIER;

            item = new ItemStack(mat);
            meta = item.getItemMeta();

            item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            if (def.customModelData != 0) {
                meta.setCustomModelData(def.customModelData);
            }

            // クロスボウの場合、最初からチャーチ済みにする
            if (mat == Material.CROSSBOW && meta instanceof org.bukkit.inventory.meta.CrossbowMeta crossbowMeta) {
                crossbowMeta.addChargedProjectile(new ItemStack(Material.ARROW));
                meta = crossbowMeta;
            }

            if (meta == null)
                return item;

            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, def.id);
            pdc.set(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, def.weight);
            pdc.set(PDCKeys.ITEM_COST, PDCKeys.INTEGER, def.cost);
            pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);

            // Stackability enforcement (Basically only ammo can stack)
            meta.setMaxStackSize(1);

            // --- Armor / Rig Configuration ---
            if (def.armorStats != null) {
                if (def.armorStats.customModelData != 0) {
                    meta.setCustomModelData(def.armorStats.customModelData);
                }
                pdc.set(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, def.armorStats.armorClass);
            }

            String equippableSlot = null;
            if (def.armorStats != null && def.armorStats.slot != null) {
                equippableSlot = def.armorStats.slot;
            } else if (def.rigStats != null) {
                equippableSlot = "LEGS";
            }

            if (equippableSlot != null) {
                try {
                    EquippableComponent equippable = meta.getEquippable();
                    try {
                        equippable.setSlot(EquipmentSlot.valueOf(equippableSlot.toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                    }

                    if (def.armorStats != null) {
                        if (def.armorStats.equipSound != null) {
                            String soundName = def.armorStats.equipSound;
                            try {
                                NamespacedKey key = soundName.contains(":") ?
                                        NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT)) :
                                        NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT).replace("_", "."));
                                if (key != null) {
                                    Sound sound = Registry.SOUNDS.get(key);
                                    if (sound != null) equippable.setEquipSound(sound);
                                }
                            } catch (Exception ignored) {}
                        }
                        if (def.armorStats.model != null && !def.armorStats.model.isEmpty()) {
                            NamespacedKey key = NamespacedKey.fromString(def.armorStats.model);
                            if (key != null)
                                equippable.setModel(key);
                        }
                        if (def.armorStats.cameraOverlay != null && !def.armorStats.cameraOverlay.isEmpty()) {
                            NamespacedKey key = NamespacedKey.fromString(def.armorStats.cameraOverlay);
                            if (key != null)
                                equippable.setCameraOverlay(key);
                        }
                        equippable.setDispensable(def.armorStats.dispensable);
                        equippable.setSwappable(def.armorStats.swappable);
                        equippable.setDamageOnHurt(def.armorStats.damageOnHurt);
                    }
                    if ("micro_rig".equals(def.id) || "d3rcx".equals(def.id) || "sp_v".equals(def.id)) {
                        equippable.setModel(NamespacedKey.fromString("iem:" + def.id + "_3d"));
                    }

                    meta.setEquippable(equippable);
                } catch (Throwable t) {
                    // Method might not be available on older versions
                }
            }

            // Affixの抽選
            if (def.affixes != null) {
                for (Affix a : def.affixes) {
                    double value = a.min + (a.max - a.min) * random.nextDouble();
                    pdc.set(PDCKeys.affix(a.stat), PDCKeys.DOUBLE, value);
                }
            }

            String rarityColor = getRarityColor(def.rarity);
            String finalName = rarityColor + ChatColor.translateAlternateColorCodes('&', def.displayName);
            meta.setDisplayName(finalName);

            TooltipStyle.forRarity(def.rarity).applyTo(meta);
            item.setItemMeta(meta);
        }

        item.setItemMeta(meta);

        // --- Lore (説明欄) の生成 ---
        return updateLore(item);
    }

    public static void applyFIR(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // すでにFIRが付いている、または持ち込み品（RAID_BROUGHT_IN）、またはプレイヤーが一度落としたものの場合は何もしない
        if (pdc.getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1) return;
        if (pdc.getOrDefault(PDCKeys.RAID_BROUGHT_IN, PDCKeys.BOOLEAN, (byte) 0) == 1) return;
        if (pdc.getOrDefault(PDCKeys.DROPPED_BY_PLAYER, PDCKeys.BOOLEAN, (byte) 0) == 1) return;
        // 支給品は FIR にしない (拾われても)
        if (pdc.getOrDefault(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte) 0) == 1) return;

        // 弾薬にも付ける (FIR の弾を納品するクエストがあるため)。FIR の弾と FIR でない弾は別のスタックになる
        pdc.set(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 1);
        item.setItemMeta(meta);
        updateLore(item);
    }

    public static ItemStack updateLore(ItemStack item) {
        if (DatapackAttachments.attachmentIdOf(item) != null) return AttachmentItems.wrap(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);

        ItemDefinition def = ItemRegistry.get(itemId);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(itemId);
        AttachmentDefinition attDef = ItemRegistry.getAttachment(itemId);

        if (def == null && ammoDef == null && attDef == null)
            return item;

        List<String> lore = new ArrayList<>();

        // --- 重量 ---
        int weightGrams = pdc.getOrDefault(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, 0);
        int cost = pdc.getOrDefault(PDCKeys.ITEM_COST, PDCKeys.INTEGER, 1);
        String weightText = weightGrams >= 1000
                ? String.format("%.2fkg", weightGrams / 1000.0)
                : weightGrams + "g";

        // --- タイプ ---
        String typeName = "UNKNOWN";
        int rarity = 0;

        if (def != null) {
            typeName = def.type;
            rarity = def.rarity;
        } else if (ammoDef != null) {
            typeName = "弾薬";
            rarity = ammoDef.rarity;
        } else if (attDef != null) {
            typeName = "アタッチメント";
            rarity = attDef.rarity;
        }

        lore.add("§7" + typeName);
        lore.add(getRarityStars(rarity));
        lore.add("§f" + weightText + " §8| §7Size: §f" + cost);

        if (pdc.getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1) {
            lore.add("§6Find in Raid");
        }
        if (pdc.getOrDefault(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte) 0) == 1) {
            lore.add(SUPPLIED_LORE_LINE);
        }

        lore.add("");

        // =========================
        // 弾薬
        // =========================
        if (ammoDef != null) {
            lore.add("§7口径 §f" + ammoDef.caliber);
            lore.add("§7貫通 §fClass " + ammoDef.ammoClass);
            lore.add("§7" + ("12x70mm".equalsIgnoreCase(ammoDef.caliber) ? "理論ダメージ" : "ダメージ")
                    + " §f" + ammoDef.damage);
            for (String line : com.lunar_prototype.impossbleEscapeMC.util.LoreWrap.wrap(ammoDef.description)) lore.add("§8§o" + line);
            lore.add("");
        }

        // =========================
        // 防具
        // =========================
        if (def != null && def.armorStats != null) {
            lore.add("§7クラス §f" + def.armorStats.armorClass);
            if (def.armorStats.coverage != null && !def.armorStats.coverage.isEmpty()) {
                lore.add("§7防護範囲 §f" + def.armorStats.coverage.stream()
                        .map(zone -> zone.label).collect(java.util.stream.Collectors.joining("・")));
            }
            
            if (def.maxDurability > 0) {
                // バニラの耐久バーだけが減っている古い防具も、表示と PDC を今の耐久に合わせる
                int durability = ArmorDurability.current(item, def);
                pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, durability);
                lore.add("§7耐久値 §f" + durability + " §8/ §7" + def.maxDurability);
            }
            if (def.armorStats.ricochetChance > 0) {
                lore.add("§7正面頭部の跳弾率 §f" + Math.round(def.armorStats.ricochetChance * 100) + "%");
            }
            if (def.armorStats.visors != null && !def.armorStats.visors.isEmpty()) {
                String visorId = pdc.get(PDCKeys.HELMET_VISOR, PDCKeys.STRING);
                ItemDefinition visor = visorId != null ? ItemRegistry.get(visorId) : null;
                lore.add("§7顔面装備 " + (visor != null ? visor.displayName : "§8なし"));
            }
            lore.add("");
        }

        // =========================
        // ヘルメットの顔面装備 (バイザー・暗視装置)
        // =========================
        if (def != null && def.visorStats != null) {
            List<String> helmets = new ArrayList<>();
            for (String id : ItemRegistry.getAllItemIds()) {
                ItemDefinition helmet = ItemRegistry.get(id);
                if (HelmetVisors.fits(helmet, def.id)) helmets.add(helmet.displayName);
            }
            if (def.visorStats.ricochetChance > 0) {
                lore.add("§7正面頭部の跳弾率 §f" + Math.round(def.visorStats.ricochetChance * 100) + "%");
            }
            if (!helmets.isEmpty()) lore.add("§7付けられるヘルメット " + String.join("§7, ", helmets));
            lore.add("§8ヘルメットのアタッチメント画面で装着");
            lore.add("");
        }

        // =========================
        // 医療
        // =========================
        if (def != null && "MED".equalsIgnoreCase(def.type) && def.medStats != null && def.medStats.continuous) {
            int durability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            lore.add("§7耐久値 §f" + durability + " §8/ §7" + def.maxDurability);
            lore.add("");
        }

        // =========================
        // バックパック
        // =========================
        if (def != null && def.backpackStats != null) {
            lore.add("§7容量 §f" + def.backpackStats.size);
            lore.add("§7軽減 §f" + String.format("%.0f%%", def.backpackStats.reduction * 100));
            lore.add("");
        }

        // =========================
        // リグ
        // =========================
        if (def != null && def.rigStats != null) {
            lore.add("§7スロット §f" + def.rigStats.size);
            lore.add("§7軽減 §f" + String.format("%.0f%%", def.rigStats.reduction * 100));
            lore.add("");
        }

        if (def != null && def.description != null) {
            for (String line : com.lunar_prototype.impossbleEscapeMC.util.LoreWrap.wrap(def.description)) lore.add("§8§o" + line);
            lore.add("");
        }

        // =========================
        // アタッチメント
        // =========================
        if (attDef != null) {
            lore.add("§7スロット §f" + attDef.slot.name());
            lore.add("");
        }

        // =========================
        // Affix
        // =========================
        if (def != null && def.affixes != null && !def.affixes.isEmpty()) {
            for (Affix a : def.affixes) {
                double val = pdc.getOrDefault(PDCKeys.affix(a.stat), PDCKeys.DOUBLE, 0.0);
                String sign = val >= 0 ? "+" : "";
                lore.add("§7" + a.stat.toUpperCase() + " §f" + sign + String.format("%.1f", val));
            }
            lore.add("");
        }

        meta.setLore(lore);
        TooltipStyle.forRarity(rarity).applyTo(meta);
        item.setItemMeta(meta);

        // --- 耐久値バー同期 ---
        if (def != null && def.maxDurability > 0) {
            int current = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            int damage = Math.max(0, def.maxDurability - current);
            item.setData(DataComponentTypes.DAMAGE, damage);
            item.setData(DataComponentTypes.MAX_DAMAGE, def.maxDurability);
        }

        return item;
    }

    /** 支給品 (PDCKeys.SUPPLIED) の説明の行 */
    public static final String SUPPLIED_LORE_LINE = "§c支給品 §8(売値 0₽、納品不可)";

    /** 弾のアイコン (リソースパックの assets/iem/items/ammo/&lt;id&gt;.json、IEM_shader/tools/ammo_icons.py が作る) */
    public static NamespacedKey ammoModel(String ammoId) {
        return new NamespacedKey("iem", "ammo/" + ammoId);
    }

    private static String getRarityColor(int rarity) {
        return switch (rarity) {
            case 2 -> "§a"; // Common
            case 3 -> "§b"; // Rare
            case 4 -> "§d"; // Epic
            case 5 -> "§6"; // Legendary
            default -> "§f"; // Trash/Normal
        };
    }

    private static String getRarityStars(int rarity) {
        String color = getRarityColor(rarity);
        return color + "★".repeat(Math.max(1, rarity)) + "§8" + "☆".repeat(Math.max(0, 5 - rarity));
    }

    /**
     * 大きい物 (コスト 2 以上) が使う、本体以外のスロットに置く占有スロット。
     * 見た目はリソースパックの iem:cost_slot (IEM_shader/tools/capacity_icons.py。無ければ赤いガラス板)
     *
     * @param ownerName どの物の分か
     * @param ownerCost その物が使うスロットの数
     */
    public static ItemStack createCostSlotPlaceholder(String ownerName, int ownerCost) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("[ " + ownerName + " の分 ]",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    net.kyori.adventure.text.Component.text(ownerName + " は " + ownerCost + " マス使います",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    net.kyori.adventure.text.Component.text("動かすと、このマスも一緒に付いていきます",
                            net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            meta.setItemModel(new NamespacedKey("iem", "cost_slot"));
            TooltipStyle.RED.applyTo(meta);
            meta.getPersistentDataContainer().set(PDCKeys.COST_SLOT_PLACEHOLDER, PDCKeys.BOOLEAN, (byte) 1);
            meta.setMaxStackSize(1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isCostSlotPlaceholder(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.COST_SLOT_PLACEHOLDER, PDCKeys.BOOLEAN);
        return marker != null && marker == 1;
    }
}
