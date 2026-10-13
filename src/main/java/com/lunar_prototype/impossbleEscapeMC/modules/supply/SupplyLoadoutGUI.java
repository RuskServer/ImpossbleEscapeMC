package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.GuiBackground;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 支給装備の画面 (出撃画面から開く。戻るか受け取ると出撃画面に戻る)。背景は GuiBackground.SUPPLY (IEM_shader/tools/gui_backgrounds.py の "supply")。
 * <ul>
 *   <li>上の段 ({@link #WEAPON_SLOTS}): 武器を選ぶ (選んでいる物は光る)</li>
 *   <li>中の段 ({@link #CONTENT_SLOTS}): 選んだ武器での中身</li>
 *   <li>下の段: 戻る ({@link #BACK_SLOT})、受け取る ({@link #RECEIVE_SLOT})。液晶には次に受け取れるまでの時間</li>
 * </ul>
 */
public class SupplyLoadoutGUI implements Listener {

    static final int[] WEAPON_SLOTS = {11, 13, 15};
    static final int[] CONTENT_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    static final int BACK_SLOT = 27;
    static final int RECEIVE_SLOT = 35;

    private final Player player;
    private final PlayerDataModule dataModule;
    private SupplyLoadout.Weapon selected;
    private Inventory inventory;

    public SupplyLoadoutGUI(Player player) {
        this(player, SupplyLoadout.Weapon.SHOTGUN);
    }

    private SupplyLoadoutGUI(Player player, SupplyLoadout.Weapon selected) {
        this.player = player;
        this.selected = selected;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
    }

    public void open() {
        PlayerData data = data();
        long remaining = SupplyLoadout.remainingCooldown(data);
        // 液晶には次に受け取れるまでの時間 (開いた時点)
        inventory = Bukkit.createInventory(null, 36,
                GuiBackground.SUPPLY.title(remaining > 0 ? SupplyLoadout.formatDuration(remaining) : "OK"));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        fill();
        player.openInventory(inventory);
    }

    private PlayerData data() {
        return dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
    }

    private void fill() {
        // 背景はタイトルの画像で描くので、空きスロットは空のままにする
        inventory.clear();
        SupplyLoadout.Weapon[] weapons = SupplyLoadout.Weapon.values();
        List<ItemStack> contents = SupplyLoadout.contents(player.getWorld(), selected);
        for (int i = 0; i < weapons.length && i < WEAPON_SLOTS.length; i++) {
            SupplyLoadout.Weapon weapon = weapons[i];
            ItemStack icon = weapon == selected && !contents.isEmpty() ? contents.getFirst().clone()
                    : SupplyLoadout.gunIcon(player.getWorld(), weapon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(line((weapon == selected ? "▶ " : "") + weapon.label, weapon == selected ? NamedTextColor.GOLD : NamedTextColor.WHITE));
            List<Component> lore = new ArrayList<>();
            lore.add(line(weapon.description, NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(weapon == selected ? line("選択中", NamedTextColor.GREEN) : line("クリックで選ぶ", NamedTextColor.AQUA));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(weapon == selected);
            icon.setItemMeta(meta);
            inventory.setItem(WEAPON_SLOTS[i], icon);
        }
        for (int i = 0; i < contents.size() && i < CONTENT_SLOTS.length; i++) {
            inventory.setItem(CONTENT_SLOTS[i], contents.get(i));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(line("戻る", NamedTextColor.RED));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        String reason = SupplyLoadout.blockingReason(player, data());
        ItemStack receive = new ItemStack(reason == null ? Material.EMERALD : Material.BARRIER);
        ItemMeta receiveMeta = receive.getItemMeta();
        receiveMeta.displayName(line("受け取る", reason == null ? NamedTextColor.GREEN : NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        lore.add(line("無料の最低限の装備 (弾は貫通クラス" + SupplyLoadout.AMMO_CLASS + ")", NamedTextColor.GRAY));
        lore.add(line("防具の無い手足を狙えば SCAV に勝てます", NamedTextColor.GRAY));
        lore.add(line("支給品は売却・納品できず、生還すると回収されます", NamedTextColor.GRAY));
        lore.add(line("次に受け取れるのは " + SupplyLoadout.COOLDOWN_MILLIS / 60000 + " 分後", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(reason == null ? line("クリックで受け取る", NamedTextColor.AQUA) : line(reason, NamedTextColor.RED));
        receiveMeta.lore(lore);
        receive.setItemMeta(receiveMeta);
        inventory.setItem(RECEIVE_SLOT, receive);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            player.closeInventory();
            openRaidSelection();
            return;
        }
        for (int i = 0; i < WEAPON_SLOTS.length; i++) {
            if (slot == WEAPON_SLOTS[i] && i < SupplyLoadout.Weapon.values().length) {
                selected = SupplyLoadout.Weapon.values()[i];
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                fill();
                return;
            }
        }
        if (slot == RECEIVE_SLOT) {
            PlayerData data = data();
            String reason = SupplyLoadout.give(player, data, selected);
            if (reason != null) {
                player.sendMessage(Component.text(reason, NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                fill();
                return;
            }
            if (dataModule != null) dataModule.saveAsync(player.getUniqueId());
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f);
            player.sendMessage(Component.text("支給装備 (" + selected.label + ") を受け取りました", NamedTextColor.GREEN));
            openRaidSelection();
        }
    }

    /** 出撃画面に戻る */
    private void openRaidSelection() {
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
        if (raidModule != null) new RaidSelectionGUI(raidModule).open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) HandlerList.unregisterAll(this);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
