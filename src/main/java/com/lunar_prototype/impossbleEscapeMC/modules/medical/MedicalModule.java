package com.lunar_prototype.impossbleEscapeMC.modules.medical;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MedicalModule implements IModule, Listener {
    private ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastInteract = new HashMap<>();
    private final Map<UUID, Integer> activeTasks = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 監視タスク: 右クリックが離されたかチェック
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : new java.util.HashSet<>(lastInteract.keySet())) {
                if (now - lastInteract.get(uuid) > 250) { // 250ms (約5チック) 経過で離したと判定
                    stopUsing(Bukkit.getPlayer(uuid));
                }
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (int taskId : activeTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        activeTasks.clear();
        lastInteract.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def != null && "MED".equalsIgnoreCase(def.type) && def.medStats != null && def.medStats.continuous) {
            event.setCancelled(true);
            startUsing(player, item, def);
        }
    }

    private void startUsing(Player player, ItemStack item, ItemDefinition def) {
        UUID uuid = player.getUniqueId();
        lastInteract.put(uuid, System.currentTimeMillis());

        // モデル切り替え
        ItemMeta meta = item.getItemMeta();
        if (meta.getCustomModelData() != def.medStats.usingCustomModelData) {
            meta.setCustomModelData(def.medStats.usingCustomModelData);
            item.setItemMeta(meta);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.5f);
        }

        // 回復タスクがなければ開始 (0.5s = 10ticks 周期)
        if (!activeTasks.containsKey(uuid)) {
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                handleHeal(player, def);
            }, 10L, 10L).getTaskId();
            activeTasks.put(uuid, taskId);
        }
    }

    private void handleHeal(Player player, ItemDefinition def) {
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            stopUsing(player);
            return;
        }

        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (!def.id.equals(itemId)) {
            stopUsing(player);
            return;
        }

        // 回復
        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + def.medStats.healPerUse);
        player.setHealth(newHealth);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.0f);
        player.spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0.1);

        // 耐久消費
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int currentDurability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        int nextDurability = currentDurability - def.medStats.durabilityPerUse;

        if (nextDurability <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            stopUsing(player);
        } else {
            pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, nextDurability);
            item.setItemMeta(meta);
            ItemFactory.updateLore(item);
        }
    }

    private void stopUsing(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastInteract.remove(uuid);
        
        Integer taskId = activeTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // モデルを元に戻す
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def != null && def.medStats != null && def.medStats.continuous) {
                if (meta.getCustomModelData() != def.customModelData) {
                    meta.setCustomModelData(def.customModelData);
                    item.setItemMeta(meta);
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.5f);
                }
            }
        }
    }
}
