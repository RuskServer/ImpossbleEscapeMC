package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * フラッシュライト (アイテム ID flashlight)。手 (メイン・オフハンド) に持っている間は点いていて、
 * 視線の先を照らす (ScreenEffectService の「フラッシュライト」の層、ポストエフェクト iem:flashlight)。
 * <ul>
 *   <li>メインの手で右クリックすると点ける・消す (消した状態はサーバーを再起動するまで覚える)</li>
 *   <li>点いている間は、暗がりにいても SCAV から明るい所にいるのと同じに見える (ScavVision)</li>
 * </ul>
 */
public final class Flashlight implements Listener {

    public static final String ITEM_ID = "flashlight";

    /** 消しているプレイヤー (持っていても照らさない) */
    private static final Set<UUID> SWITCHED_OFF = ConcurrentHashMap.newKeySet();
    private static Flashlight instance;

    private Flashlight() {
    }

    public static void init(JavaPlugin plugin) {
        if (instance != null) return;
        instance = new Flashlight();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    public static boolean isFlashlight(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return ITEM_ID.equals(item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING));
    }

    /** 点いたフラッシュライトを手 (メイン・オフハンド) に持っているか */
    public static boolean isLit(LivingEntity entity) {
        if (entity == null || SWITCHED_OFF.contains(entity.getUniqueId())) return false;
        if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
        EntityEquipment equipment = entity.getEquipment();
        return equipment != null && (isFlashlight(equipment.getItemInMainHand()) || isFlashlight(equipment.getItemInOffHand()));
    }

    /** メインの手で右クリック: 点ける・消す */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isFlashlight(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        boolean on = SWITCHED_OFF.remove(player.getUniqueId());
        if (!on) SWITCHED_OFF.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.5f, on ? 1.8f : 1.3f);
        player.sendActionBar(Component.text(on ? "フラッシュライト: ON" : "フラッシュライト: OFF", on ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
    }
}
