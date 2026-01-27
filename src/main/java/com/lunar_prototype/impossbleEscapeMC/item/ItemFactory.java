package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemFactory {
    private static final Random random = new Random();

    public static ItemStack create(String id) {
        ItemDefinition def = ItemRegistry.get(id);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(id);

        if (def == null && ammoDef == null) return null;

        ItemStack item;
        ItemMeta meta;

        if (ammoDef != null) {
            // --- 弾薬アイテムの生成 ---
            Material mat = Material.matchMaterial(ammoDef.material);
            item = new ItemStack(mat == null ? Material.IRON_NUGGET : mat);
            meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, ammoDef.id);

            String rarityColor = getRarityColor(ammoDef.rarity);
            meta.setDisplayName(rarityColor + ChatColor.translateAlternateColorCodes('&', ammoDef.displayName));
        } else {

            Material mat = Material.matchMaterial(def.material);
            if (mat == null) mat = Material.BARRIER;

            item = new ItemStack(mat);
            meta = item.getItemMeta();
            if (meta == null) return item;

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.ITEM_ID, PDCKeys.STRING, def.id);
            pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);

            if ("GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {
                meta.setCustomModelData(def.gunStats.customModelData);
                pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, def.gunStats.magSize);

                AmmoDefinition defaultAmmo = ItemRegistry.getWeakestAmmoForCaliber(def.gunStats.caliber);
                if (defaultAmmo != null) {
                    pdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, defaultAmmo.id);
                }
            }

            // Affixの抽選
            if (def.affixes != null) {
                for (Affix a : def.affixes) {
                    double value = a.min + (a.max - a.min) * random.nextDouble();
                    pdc.set(PDCKeys.affix(a.stat), PDCKeys.DOUBLE, value);
                }
            }

            // --- 名前の設定 (レアリティカラー適用) ---
            String rarityColor = getRarityColor(def.rarity);
            String finalName = rarityColor + ChatColor.translateAlternateColorCodes('&', def.displayName);
            meta.setDisplayName(finalName);
        }

        item.setItemMeta(meta);

        // --- Lore (説明欄) の生成 ---
        return updateLore(item);
    }

    /**
     * アイテムの現在の状態(PDC)に基づいてLoreを再構築します
     */
    public static ItemStack updateLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(itemId);
        if (def == null) return item;

        List<String> lore = new ArrayList<>();

        // 1. タイプとレアリティ
        lore.add("§7Type: §f" + def.type);
        lore.add("§7Rarity: " + getRarityStars(def.rarity));
        lore.add("");

        if (ammoDef != null) {
            lore.add("§7Type: §fAMMO");
            lore.add("§7Caliber: §e" + ammoDef.caliber);
            lore.add("");
            lore.add("§6§l<< AMMO STATS >>");
            lore.add("§7Penetration: §fClass " + ammoDef.ammoClass);
            lore.add("§7Base Damage: §f" + ammoDef.damage);
        }

        // 2. 銃ステータス (GUNの場合)
        if ("GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {
            int ammo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);

            lore.add("§6§l<< GUN STATS >>");
            // 弾数は視認性重視
            lore.add("§7Ammo: §e" + ammo + " §8/ §7" + def.gunStats.magSize);
            lore.add("§7Damage: §f" + String.format("%.1f", pdc.getOrDefault(PDCKeys.affix("damage"), PDCKeys.DOUBLE, def.gunStats.damage)));
            lore.add("§7RPM: §f" + def.gunStats.rpm);
            lore.add("§7Mode: §f" + def.gunStats.fireMode);
            String ammoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
            AmmoDefinition currentAmmo = ItemRegistry.getAmmo(ammoId);
            String ammoName = (currentAmmo != null) ? currentAmmo.displayName : "None";

            lore.add("§7Chambered: §f" + ammoName); // 装填中の弾薬名を表示
            lore.add("");
        }

        // 3. 特殊効果 (Affixes)
        if (def.affixes != null && !def.affixes.isEmpty()) {
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
}