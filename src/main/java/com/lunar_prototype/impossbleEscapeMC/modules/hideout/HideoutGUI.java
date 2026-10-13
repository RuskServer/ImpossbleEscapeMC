package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** 隠れ家の画面: 建物 (ベースの建築改良) と各設備のレベル。クリックで 1 レベル上げる (その建築の差分を重ねる) */
public class HideoutGUI implements Listener {
    /** 建物は上の段の真ん中、設備は 2 段目に並べる */
    private static final int BASE_SLOT = 4;
    private static final int FIRST_FACILITY_SLOT = 9;

    private final HideoutModule module;
    private final Player player;
    private final Inventory inventory;

    public HideoutGUI(HideoutModule module, Player player) {
        this.module = module;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("Hideout - 隠れ家管理"));
    }

    public void open() {
        updateInventory();
        Bukkit.getPluginManager().registerEvents(this, module.getPlugin());
        player.openInventory(inventory);
    }

    private static int slotOf(HideoutFacility facility) {
        return facility == HideoutFacility.BASE ? BASE_SLOT : FIRST_FACILITY_SLOT + facility.ordinal() - 1;
    }

    private void updateInventory() {
        inventory.clear();
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());

        // 背景
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, bg);

        for (HideoutFacility facility : HideoutFacility.values()) {
            inventory.setItem(slotOf(facility), icon(facility, data));
        }
    }

    /** 上げられない理由 (上げられるなら null) */
    private String blocking(HideoutFacility facility, PlayerData data) {
        int level = data.getHideoutLevel(facility.id);
        int max = facility.maxLevel(module.getLayout());
        if (level >= max) return max == 0 ? "まだ建てられません" : "最大レベルです";
        if (!module.getLayout().hasLayer(facility, level + 1)) return "Lv." + (level + 1) + " の建築はまだ作られていません";
        return null;
    }

    private ItemStack icon(HideoutFacility facility, PlayerData data) {
        int level = data.getHideoutLevel(facility.id);
        int max = facility.maxLevel(module.getLayout());
        ItemStack item = new ItemStack(facility.icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(facility.label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        // 建物は Lv.0 でもベースが建っている
        String state = facility == HideoutFacility.BASE ? "Lv." + level + (level == 0 ? " (初期)" : "") + " / " + max
                : level == 0 ? "未設置" : "Lv." + level + " / " + max;
        lore.add(Component.text(state, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        String blocking = blocking(facility, data);
        lore.add(blocking == null
                ? Component.text("クリックで " + (level == 0 && facility != HideoutFacility.BASE ? "設置" : "Lv." + (level + 1) + " にアップグレード"), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                : Component.text(blocking, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        for (HideoutFacility facility : HideoutFacility.values()) {
            if (slotOf(facility) == slot) handleUpgrade(facility);
        }
    }

    private void handleUpgrade(HideoutFacility facility) {
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        String blocking = blocking(facility, data);
        if (blocking != null) {
            player.sendMessage(Component.text(facility.label + ": " + blocking, NamedTextColor.RED));
            return;
        }
        try {
            module.upgrade(player, data, facility);
        } catch (IOException e) {
            module.getPlugin().getLogger().log(Level.WARNING, "隠れ家の設備を建てられませんでした", e);
            player.sendMessage(Component.text("レベルは上がりましたが、建築を反映できませんでした (次に入った時に建て直します): "
                    + e.getMessage(), NamedTextColor.RED));
            updateInventory();
            return;
        }
        int level = data.getHideoutLevel(facility.id);
        player.sendMessage(Component.text(facility.label + " を " + (level == 1 && facility != HideoutFacility.BASE ? "設置しました" : "Lv." + level + " にアップグレードしました"),
                NamedTextColor.GREEN));
        updateInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
