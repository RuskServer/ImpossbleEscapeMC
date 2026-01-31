package com.lunar_prototype.impossbleEscapeMC.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class CrossbowTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();

            // クロスボウを持っているか確認
            if (item.getType() == Material.CROSSBOW) {
                CrossbowMeta meta = (CrossbowMeta) item.getItemMeta();

                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,2,255));

                if (meta != null) {
                    // すでに装填されている場合はスキップ（無駄な更新を防ぐ）
                    if (!meta.hasChargedProjectiles()) {
                        ItemStack arrow = new ItemStack(Material.ARROW, 1);
                        meta.addChargedProjectile(arrow);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
    }

    // プラグイン有効化時に呼び出す用
    public static void start(JavaPlugin plugin) {
        // 0秒後から開始し、1tick（0.05秒）ごとに実行
        new CrossbowTask().runTaskTimer(plugin, 0L, 1L);
    }
}