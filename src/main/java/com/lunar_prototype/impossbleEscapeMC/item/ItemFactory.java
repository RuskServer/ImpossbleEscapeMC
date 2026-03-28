package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    public static ItemStack create(String id) {
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
            pdc.set(PDCKeys.ITEM_COST, PDCKeys.INTEGER, 1);

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

            item.setItemMeta(meta);
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            applyTooltipStyle(item, attDef.rarity);
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
            item.setItemMeta(meta);
            applyTooltipStyle(item, ammoDef.rarity);
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

            // Stackability enforcement
            if (!def.stackable) {
                item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            }

            if ("GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {
                // 1.20.5+ のシステムでは setData で後書きするため、ここでは基本的なメタデータのみセット
                if (def.gunStats.customModelData != 0) {
                    meta.setCustomModelData(def.gunStats.customModelData);
                }
                pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, def.gunStats.magSize);

                AmmoDefinition defaultAmmo = ItemRegistry.getWeakestAmmoForCaliber(def.gunStats.caliber);
                if (defaultAmmo != null) {
                    pdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, defaultAmmo.id);

                    // クローズドボルト系の武器の場合、最初からチャンバーに1発装填する
                    if ("CLOSED".equalsIgnoreCase(def.gunStats.boltType)
                            || "BOLT_ACTION".equalsIgnoreCase(def.gunStats.boltType)
                            || "PUMP_ACTION".equalsIgnoreCase(def.gunStats.boltType)) {
                        pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 1);
                        pdc.set(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING, defaultAmmo.id);
                    } else {
                        pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0);
                    }
                }

                if (def.gunStats.defaultAttachments != null && !def.gunStats.defaultAttachments.isEmpty()) {
                    String joined = String.join(",", def.gunStats.defaultAttachments);
                    pdc.set(PDCKeys.ATTACHMENTS, PDCKeys.STRING, joined);
                }
            }

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
                            try {
                                equippable.setEquipSound(Sound.valueOf(def.armorStats.equipSound.toUpperCase()));
                            } catch (IllegalArgumentException ignored) {
                            }
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

            item.setItemMeta(meta);
            applyTooltipStyle(item, def.rarity);
        }

        item.setItemMeta(meta);

        // --- 初期モデル設定 (銃の場合、Idleアニメーションがあれば適用) ---
        if (def != null && "GUN".equalsIgnoreCase(def.type) && def.gunStats != null && def.gunStats.idleAnimation != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(def.gunStats.idleAnimation.model));
            
            // WeaponContext と同様にアタッチメント情報を含めて CustomModelData を構成
            List<String> attachments = new ArrayList<>();
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
            if (joined != null && !joined.isEmpty()) {
                attachments = Arrays.asList(joined.split(","));
            }

            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                    .addFloat(0.0f) // Frame 0
                    .addStrings(attachments)
                    .build());
        }

        // --- Lore (説明欄) の生成 ---
        return updateLore(item);
    }

    public static ItemStack updateLore(ItemStack item) {
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

        lore.add("");

        // =========================
        // 銃ステータス
        // =========================
        if (def != null && "GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {

            int ammo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            boolean chamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;

            GunStats effective = GunStatsCalculator.calculateEffectiveStats(item, def.gunStats);

            // --- モード ---
            String[] modes = effective.fireMode.split(",");
            StringBuilder modeLine = new StringBuilder();

            for (int i = 0; i < modes.length; i++) {
                if (i == 0) {
                    modeLine.append("§f").append(modes[i].trim());
                } else {
                    modeLine.append("§8・§7").append(modes[i].trim());
                }
            }

            String chamberText = chamberLoaded ? " §a(+1)" : "";

            lore.add(modeLine + "   §f" + ammo + chamberText + " §8/ §7" + def.gunStats.magSize);

            // --- 弾薬 ---
            String ammoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
            AmmoDefinition currentAmmo = ItemRegistry.getAmmo(ammoId);

            String ammoName = (currentAmmo != null) ? currentAmmo.displayName : "なし";
            String caliber = (currentAmmo != null) ? currentAmmo.caliber : def.gunStats.caliber;

            if (currentAmmo != null) {
                lore.add("§7装填中 §f" + ammoName + " §8/ §7装填可能 §f" + caliber);
            } else {
                lore.add("§7装填可能 §f" + caliber);
            }

            // --- ダメージ ---
            double baseDamage = (currentAmmo != null) ? currentAmmo.damage : 0.0;
            double totalDamage = baseDamage * effective.damage;
            lore.add("§7ダメージ §f" + String.format("%.0f", totalDamage));

            // --- RPM ---
            lore.add("§7" + effective.rpm + " RPM");

            // --- 耐久値 ---
            int durability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            lore.add("§7耐久値 §f" + durability + " §8/ §7" + def.maxDurability);

            lore.add("");
        }

        // =========================
        // 弾薬
        // =========================
        if (ammoDef != null) {
            lore.add("§7口径 §f" + ammoDef.caliber);
            lore.add("§7貫通 §fClass " + ammoDef.ammoClass);
            lore.add("§7ダメージ §f" + ammoDef.damage);
            lore.add("");
        }

        // =========================
        // 防具
        // =========================
        if (def != null && def.armorStats != null) {
            lore.add("§7クラス §f" + def.armorStats.armorClass);
            lore.add("§7防御 §f" + def.armorStats.defense);
            
            if (def.maxDurability > 0) {
                int durability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
                lore.add("§7耐久値 §f" + durability + " §8/ §7" + def.maxDurability);
            }
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

        // =========================
        // アタッチメント
        // =========================
        if (attDef != null) {
            lore.add("§7スロット §f" + attDef.slot.name());

            if (!attDef.modifiers.isEmpty()) {
                for (Map.Entry<String, Double> entry : attDef.modifiers.entrySet()) {
                    String stat = entry.getKey();
                    double val = entry.getValue();

                    String color = val < 0 ? "§a" : "§c";
                    if (stat.equals("damage")) color = val > 0 ? "§a" : "§c";

                    String sign = val >= 0 ? "+" : "";

                    String unit;
                    if (stat.equals("recoil") || stat.equals("damage")) {
                        unit = String.format("%.0f%%", val * 100);
                    } else {
                        unit = val + "ms";
                    }

                    lore.add("§7" + stat.toUpperCase() + " " + color + sign + unit);
                }
            }
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

    private static void applyTooltipStyle(ItemStack item, int rarity) {
        String styleId = switch (rarity) {
            case 2 -> "green_frame.png";
            case 3 -> "blue_frame.png";
            case 4 -> "purple_frame.png";
            case 5 -> "gold_frame.png";
            default -> "white_frame.png";
        };
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", styleId));
    }

    public static ItemStack createCostSlotPlaceholder() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8[ サイズ制限 ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7他のアイテムのサイズによって占有されています");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.COST_SLOT_PLACEHOLDER, PDCKeys.BOOLEAN, (byte) 1);
            item.setItemMeta(meta);
        }
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        return item;
    }

    public static boolean isCostSlotPlaceholder(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.COST_SLOT_PLACEHOLDER, PDCKeys.BOOLEAN);
        return marker != null && marker == 1;
    }
}
