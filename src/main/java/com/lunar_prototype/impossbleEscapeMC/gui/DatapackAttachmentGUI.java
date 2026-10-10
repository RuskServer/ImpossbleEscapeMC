package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * データパック銃 (Toi's Armory) のアタッチメント画面。プラグイン銃の {@link AttachmentGUI} と同じ見た目・操作で、
 * 中身はデータパックのスロットとアタッチメントを使う。
 * 付けられるアタッチメントがあるスロットと、何か付いているスロットだけを並べる。
 * 編集するのは開いた時に手に持っていたホットバーの銃 (毎回その場所の今の銃を読み、書き戻す)
 */
public class DatapackAttachmentGUI implements InventoryHolder {

    /** 銃を置く場所と、スロットを並べる場所 (スロットの並び順に使う) */
    static final int GUN_SLOT = 13;
    private static final int[] SLOT_POSITIONS = {10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25};

    private final Player player;
    private final int heldSlot;
    private final String gunId;
    private final Inventory inventory;
    private final Map<Integer, String> slotByPosition = new HashMap<>();

    private DatapackAttachmentGUI(Player player, int heldSlot, String gunId) {
        this.player = player;
        this.heldSlot = heldSlot;
        this.gunId = gunId;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("アタッチメント", NamedTextColor.DARK_GRAY));
    }

    /** 手に持っているデータパック銃の画面を開く。データパック銃でなければfalse */
    public static boolean open(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String gunId = DatapackGunCatalog.gunIdOf(held);
        if (gunId == null) return false;
        DatapackAttachmentGUI gui = new DatapackAttachmentGUI(player, player.getInventory().getHeldItemSlot(), gunId);
        if (!gui.refresh()) {
            player.sendMessage(Component.text("この銃に付けられるアタッチメントはありません", NamedTextColor.RED));
            return true;
        }
        player.openInventory(gui.inventory);
        return true;
    }

    /** 今の銃を読み直して並べ直す。並べるスロットが1つも無ければfalse */
    boolean refresh() {
        ItemStack gun = currentGun();
        if (gun == null) return false;
        inventory.clear();
        slotByPosition.clear();
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        inventory.setItem(GUN_SLOT, gun.clone());

        Map<String, ItemStack> attached = DatapackAttachments.attached(gun);
        int next = 0;
        for (DatapackAttachments.SlotDef slot : DatapackAttachments.slotsOf(gunId)) {
            ItemStack current = attached.get(slot.slot());
            if (slot.options().isEmpty() && current == null) continue;
            if (next >= SLOT_POSITIONS.length) break;
            int position = SLOT_POSITIONS[next++];
            slotByPosition.put(position, slot.slot());
            inventory.setItem(position, current != null ? attachedIcon(slot, current) : emptyIcon(slot));
        }
        return !slotByPosition.isEmpty();
    }

    /** 編集中の銃 (開いた時のホットバーの場所にある、同じ種類のデータパック銃)。無くなっていればnull */
    ItemStack currentGun() {
        ItemStack item = player.getInventory().getItem(heldSlot);
        return gunId.equals(DatapackGunCatalog.gunIdOf(item)) ? item : null;
    }

    void setGun(ItemStack gun) {
        player.getInventory().setItem(heldSlot, gun);
    }

    String slotAt(int position) {
        return slotByPosition.get(position);
    }

    String gunId() {
        return gunId;
    }

    int heldSlot() {
        return heldSlot;
    }

    Player player() {
        return player;
    }

    private ItemStack attachedIcon(DatapackAttachments.SlotDef slot, ItemStack attachment) {
        ItemStack icon = attachment.clone();
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line(slotLabel(slot.slot()), NamedTextColor.GRAY));
        DatapackAttachments.Option option = slot.option(DatapackAttachments.attachmentIdOf(attachment));
        if (option != null) {
            for (DatapackAttachments.Modifier modifier : option.modifiers()) {
                String text = DatapackAttachments.describe(modifier);
                if (text != null) lore.add(line("  " + text, NamedTextColor.AQUA));
            }
        }
        lore.add(Component.empty());
        lore.add(line("左クリック: 取り外し", NamedTextColor.YELLOW));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack emptyIcon(DatapackAttachments.SlotDef slot) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("空きスロット", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(line("付けられるアタッチメント:", NamedTextColor.GRAY));
        for (DatapackAttachments.Option option : slot.options()) {
            lore.add(Component.text("・", NamedTextColor.WHITE).append(name(option.attachmentId())).decoration(TextDecoration.ITALIC, false));
            for (DatapackAttachments.Modifier modifier : option.modifiers()) {
                String text = DatapackAttachments.describe(modifier);
                if (text != null) lore.add(line("    " + text, NamedTextColor.AQUA));
            }
        }
        lore.add(Component.empty());
        lore.add(line("アタッチメントをカーソルに持ってクリックで装着", NamedTextColor.YELLOW));
        lore.add(line("(インベントリからシフトクリックでも装着)", NamedTextColor.YELLOW));
        return pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, line(slotLabel(slot.slot()), NamedTextColor.WHITE), lore);
    }

    /** アタッチメントの名前 (データパックの翻訳キー。リソースパックに翻訳が無ければデータパックの英名) */
    static Component name(String attachmentId) {
        DatapackAttachments.Info info = DatapackAttachments.info(attachmentId);
        if (info == null) return Component.text(attachmentId, NamedTextColor.WHITE);
        if (info.translationKey().isEmpty()) return Component.text(info.fallbackName(), NamedTextColor.WHITE);
        return Component.translatable(info.translationKey(), info.fallbackName()).color(NamedTextColor.WHITE);
    }

    static String slotLabel(String slot) {
        return switch (slot) {
            case "sight" -> "サイト";
            case "barrel" -> "バレル";
            case "muzzle" -> "マズル";
            case "magazine" -> "マガジン";
            case "rear_grip" -> "グリップ";
            case "stock" -> "ストック";
            case "underbarrel" -> "アンダーバレル";
            case "handguard" -> "ハンドガード";
            case "receiver" -> "レシーバー";
            case "accessory" -> "アクセサリー";
            case "slide" -> "スライド";
            case "shell" -> "シェル";
            default -> slot;
        };
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack pane(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
