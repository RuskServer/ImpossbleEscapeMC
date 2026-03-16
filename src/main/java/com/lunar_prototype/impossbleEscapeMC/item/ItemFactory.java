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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
            meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, attDef.id);

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
            applyTooltipStyle(item, attDef.rarity);
            return item;
        }

        if (ammoDef != null) {
            // --- 弾薬アイテムの生成 ---
            Material mat = Material.matchMaterial(ammoDef.material);
            item = new ItemStack(mat == null ? Material.IRON_NUGGET : mat);
            meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, ammoDef.id);

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

            item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

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
            pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);

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

            // --- Armor Configuration ---
            if (def.armorStats != null) {
                if (def.armorStats.customModelData != 0) {
                    meta.setCustomModelData(def.armorStats.customModelData);
                }
                pdc.set(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, def.armorStats.armorClass);

                // slot設定がある場合のみ、EquippableComponentを設定する
                if (def.armorStats.slot != null) {
                    try {
                        EquippableComponent equippable = meta.getEquippable();
                        try {
                            equippable.setSlot(EquipmentSlot.valueOf(def.armorStats.slot.toUpperCase()));
                        } catch (IllegalArgumentException ignored) {
                        }

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

                        // 明示的にコンポーネントをセットして反映させる
                        meta.setEquippable(equippable);
                    } catch (Throwable t) {
                        // Method might not be available on older versions
                    }
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

    /**
     * アイテムの現在の状態(PDC)に基づいてLoreを再構築します
     */
    public static ItemStack updateLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        
        ItemDefinition def = ItemRegistry.get(itemId);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(itemId);
        AttachmentDefinition attDef = ItemRegistry.getAttachment(itemId);

        // どれにも該当しない場合は何もしない
        if (def == null && ammoDef == null && attDef == null)
            return item;

        List<String> lore = new ArrayList<>();

        // --- 1. 基本情報 (タイプとレアリティ) ---
        if (def != null) {
            lore.add("§7Type: §f" + def.type);
            lore.add("§7Rarity: " + getRarityStars(def.rarity));
        } else if (ammoDef != null) {
            lore.add("§7Type: §fAMMO");
            lore.add("§7Rarity: " + getRarityStars(ammoDef.rarity));
        } else if (attDef != null) {
            lore.add("§7Type: §fATTACHMENT");
            lore.add("§7Rarity: " + getRarityStars(attDef.rarity));
        }
        lore.add("");

        // --- 2. 弾薬ステータス (弾薬アイテム、または弾薬としての性質を持つ場合) ---
        if (ammoDef != null) {
            lore.add("§6§l<< AMMO STATS >>");
            lore.add("§7Caliber: §e" + ammoDef.caliber);
            lore.add("§7Penetration: §fClass " + ammoDef.ammoClass);
            lore.add("§7Base Damage: §f" + ammoDef.damage);
            lore.add("");
        }

        // --- 3. 銃ステータス (GUNの場合) ---
        if (def != null && "GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {
            int ammo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            boolean chamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;
            String chamberSuffix = chamberLoaded ? " §a(+1)" : "";

            lore.add("§6§l<< GUN STATS >>");
            lore.add("§7Ammo: §e" + ammo + chamberSuffix + " §8/ §7" + def.gunStats.magSize);
            String ammoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
            AmmoDefinition currentAmmo = ItemRegistry.getAmmo(ammoId);
            
            double gunMultiplier = pdc.getOrDefault(PDCKeys.affix("damage"), PDCKeys.DOUBLE, def.gunStats.damage);
            double baseDamage = (currentAmmo != null) ? currentAmmo.damage : 0.0;
            double totalDamage = baseDamage * gunMultiplier;

            lore.add("§7Damage: §f" + String.format("%.1f", totalDamage) + " §8(x" + String.format("%.2f", gunMultiplier) + ")");
            lore.add("§7RPM: §f" + def.gunStats.rpm);
            lore.add("§7Mode: §f" + def.gunStats.fireMode);
            
            String ammoName = (currentAmmo != null) ? currentAmmo.displayName : "None";
            lore.add("§7Chambered: §f" + ammoName);
            lore.add("");
        }

        // --- 4. 防具ステータス (ARMORの場合) ---
        if (def != null && def.armorStats != null) {
            lore.add("§9§l<< ARMOR STATS >>");
            lore.add("§7Class: §f" + def.armorStats.armorClass);
            lore.add("§7Defense: §f" + def.armorStats.defense);
            lore.add("");
        }

        // --- 4.5 医療ステータス (MEDの場合、耐久度表示) ---
        if (def != null && "MED".equalsIgnoreCase(def.type) && def.medStats != null && def.medStats.continuous) {
            int durability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            lore.add("§a§l<< MEDICAL KIT >>");
            lore.add("§7Durability: §e" + durability + " §8/ §7" + def.maxDurability);
            lore.add("");
        }

        // --- 5. アタッチメントステータス ---
        if (attDef != null) {
            lore.add("§e§l<< ATTACHMENT >>");
            lore.add("§7Slot: §f" + attDef.slot.name());
            lore.add("");
        }

        // --- 6. 特殊効果 (Affixes) ---
        if (def != null && def.affixes != null && !def.affixes.isEmpty()) {
            lore.add("§b§l<< MODIFICATIONS >>");
            for (Affix a : def.affixes) {
                double val = pdc.getOrDefault(PDCKeys.affix(a.stat), PDCKeys.DOUBLE, 0.0);
                String sign = val >= 0 ? "+" : "";
                lore.add("§b " + a.stat.toUpperCase() + ": " + sign + String.format("%.1f", val));
            }
            lore.add("");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        // --- バニラ耐久値バーとの同期 ---
        // item.setItemMeta(meta) の後に実行しないと、meta内の古い値で上書きされるため
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
}