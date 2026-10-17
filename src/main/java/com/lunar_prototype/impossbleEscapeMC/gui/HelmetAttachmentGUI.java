package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.item.HelmetVisors;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ヘルメットのアタッチメント画面 (いまはバイザーだけ)。同じ作業台の見た目・操作で、
 * 中央にヘルメット、上にバイザーのスロットを置く。
 * 編集するのは、手に持っているヘルメット、手に持っていなければかぶっているヘルメット
 * (開いた時の場所を覚えて、毎回その場所の今のヘルメットを読み、書き戻す)
 */
public class HelmetAttachmentGUI implements InventoryHolder {

    static final int HELMET_SLOT = AttachmentLayout.GUN_SLOT;
    static final int VISOR_SLOT = AttachmentLayout.position("visor");
    /** プレイヤーのインベントリでの、かぶっているヘルメットの場所 */
    static final int HEAD_INVENTORY_SLOT = 39;

    private final Player player;
    private final int inventorySlot;
    private final String helmetId;
    private final Inventory inventory;

    private HelmetAttachmentGUI(Player player, int inventorySlot, String helmetId) {
        this.player = player;
        this.inventorySlot = inventorySlot;
        this.helmetId = helmetId;
        this.inventory = Bukkit.createInventory(this, AttachmentLayout.SIZE, AttachmentLayout.title(List.of(VISOR_SLOT)));
    }

    /**
     * 手に持っている (無ければかぶっている) バイザーを付けられるヘルメットの画面を開く。
     * どちらにも無ければfalse (何もしない)
     */
    public static boolean open(Player player) {
        int held = player.getInventory().getHeldItemSlot();
        int slot = HelmetVisors.helmetDef(player.getInventory().getItem(held)) != null ? held
                : HelmetVisors.helmetDef(player.getInventory().getItem(HEAD_INVENTORY_SLOT)) != null ? HEAD_INVENTORY_SLOT
                : -1;
        if (slot < 0) return false;
        ItemDefinition def = HelmetVisors.helmetDef(player.getInventory().getItem(slot));
        HelmetAttachmentGUI gui = new HelmetAttachmentGUI(player, slot, def.id);
        gui.refresh();
        player.openInventory(gui.inventory);
        return true;
    }

    /** 今のヘルメットを読み直して並べ直す。ヘルメットが無くなっていればfalse */
    boolean refresh() {
        ItemStack helmet = currentHelmet();
        if (helmet == null) return false;
        // 背景 (作業台の整備マット) はタイトルの画像で描くので、空きスロットは空のままにする
        inventory.clear();
        inventory.setItem(HELMET_SLOT, helmet.clone());
        String visorId = HelmetVisors.visorOf(helmet);
        inventory.setItem(VISOR_SLOT, visorId != null ? attachedIcon(visorId) : emptyIcon());
        return true;
    }

    /** 編集中のヘルメット (開いた時の場所にある、同じ種類のヘルメット)。無くなっていればnull */
    ItemStack currentHelmet() {
        ItemStack item = player.getInventory().getItem(inventorySlot);
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PersistentDataType.STRING);
        return Objects.equals(helmetId, id) ? item : null;
    }

    void setHelmet(ItemStack helmet) {
        player.getInventory().setItem(inventorySlot, helmet);
    }

    ItemDefinition helmetDef() {
        return ItemRegistry.get(helmetId);
    }

    /** 編集中のヘルメットの、プレイヤーのインベントリでの場所 (ホットバー 0〜8、かぶっていれば 39) */
    int inventorySlot() {
        return inventorySlot;
    }

    Player player() {
        return player;
    }

    private ItemStack attachedIcon(String visorId) {
        ItemStack icon = ItemFactory.create(visorId);
        if (icon == null) return emptyIcon();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line(AttachmentLayout.label("visor"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("左クリック: 取り外し", NamedTextColor.YELLOW));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack emptyIcon() {
        ItemStack icon = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(line(AttachmentLayout.label("visor"), NamedTextColor.WHITE));
        List<Component> lore = new ArrayList<>();
        lore.add(line("空きスロット", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(line("付けられる顔面装備:", NamedTextColor.GRAY));
        ItemDefinition def = helmetDef();
        if (def != null && def.armorStats.visors != null) {
            for (String visorId : def.armorStats.visors) {
                ItemDefinition visor = ItemRegistry.get(visorId);
                lore.add(line("・" + (visor != null ? visor.displayName : visorId), NamedTextColor.WHITE));
            }
        }
        lore.add(Component.empty());
        lore.add(line("装着品をカーソルに持ってクリックで装着", NamedTextColor.YELLOW));
        lore.add(line("(インベントリからシフトクリックでも装着)", NamedTextColor.YELLOW));
        meta.lore(lore);
        // 見た目はパーツの絵文字 (リソースパック)
        meta.setItemModel(AttachmentLayout.slotIcon("visor"));
        icon.setItemMeta(meta);
        return icon;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
