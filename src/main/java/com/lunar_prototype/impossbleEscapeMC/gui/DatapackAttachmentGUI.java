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

    /** 銃を置く場所。スロットは銃の形に合わせて銃のまわりに並べる ({@link AttachmentLayout}) */
    static final int GUN_SLOT = AttachmentLayout.GUN_SLOT;

    private final Player player;
    private final int heldSlot;
    private final String gunId;
    private final Inventory inventory;
    private final Map<Integer, String> slotByPosition = new HashMap<>();

    private DatapackAttachmentGUI(Player player, int heldSlot, String gunId, ItemStack gun) {
        this.player = player;
        this.heldSlot = heldSlot;
        this.gunId = gunId;
        arrange(gun);
        // 背景と、銃から並べたスロットへの線 (タイトルに描くので、並べる場所は開く前に決める)
        this.inventory = Bukkit.createInventory(this, AttachmentLayout.SIZE, AttachmentLayout.title(slotByPosition.keySet()));
    }

    /** 手に持っているデータパック銃の画面を開く。データパック銃でなければfalse */
    public static boolean open(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String gunId = DatapackGunCatalog.gunIdOf(held);
        if (gunId == null) return false;
        DatapackAttachmentGUI gui = new DatapackAttachmentGUI(player, player.getInventory().getHeldItemSlot(), gunId, held);
        if (!gui.refresh()) {
            player.sendMessage(Component.text("この銃に付けられるアタッチメントはありません", NamedTextColor.RED));
            return true;
        }
        player.openInventory(gui.inventory);
        return true;
    }

    /**
     * 並べるスロット (付けられるアタッチメントがあるか、何か付いているもの) と場所を決める。
     * 場所は銃の形に合わせた位置 ({@link AttachmentLayout})、位置の決まっていない種類は空いている場所
     */
    private void arrange(ItemStack gun) {
        Map<String, ItemStack> attached = DatapackAttachments.attached(gun);
        int[] spare = AttachmentLayout.sparePositions();
        int nextSpare = 0;
        for (DatapackAttachments.SlotDef slot : DatapackAttachments.slotsOf(gunId)) {
            if (slot.options().isEmpty() && attached.get(slot.slot()) == null) continue;
            int position = AttachmentLayout.position(slot.slot());
            if (position < 0 || slotByPosition.containsKey(position)) {
                while (nextSpare < spare.length && slotByPosition.containsKey(spare[nextSpare])) nextSpare++;
                if (nextSpare >= spare.length) continue;
                position = spare[nextSpare++];
            }
            slotByPosition.put(position, slot.slot());
        }
    }

    /** 今の銃を読み直して並べ直す (場所は開いた時のまま)。並べるスロットが1つも無ければfalse */
    boolean refresh() {
        ItemStack gun = currentGun();
        if (gun == null || slotByPosition.isEmpty()) return false;
        // 背景 (作業台の整備マット) はタイトルの画像で描くので、空きスロットは空のままにする
        inventory.clear();
        inventory.setItem(GUN_SLOT, gun.clone());

        Map<String, ItemStack> attached = DatapackAttachments.attached(gun);
        Map<String, DatapackAttachments.SlotDef> defs = new HashMap<>();
        for (DatapackAttachments.SlotDef slot : DatapackAttachments.slotsOf(gunId)) defs.put(slot.slot(), slot);
        slotByPosition.forEach((position, name) -> {
            DatapackAttachments.SlotDef slot = defs.get(name);
            if (slot == null) return;
            ItemStack current = attached.get(name);
            inventory.setItem(position, current != null ? attachedIcon(slot, current) : emptyIcon(slot));
        });
        return true;
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
        ItemStack icon = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, line(slotLabel(slot.slot()), NamedTextColor.WHITE), lore);
        // 見た目はパーツの絵文字 (リソースパック)
        ItemMeta meta = icon.getItemMeta();
        meta.setItemModel(AttachmentLayout.slotIcon(slot.slot()));
        icon.setItemMeta(meta);
        return icon;
    }

    /** アタッチメントの名前 (データパックの翻訳キー。リソースパックに翻訳が無ければデータパックの英名) */
    static Component name(String attachmentId) {
        var definition = com.lunar_prototype.impossbleEscapeMC.item.AttachmentItems.resolve(attachmentId);
        return definition != null ? definition.name() : Component.text(attachmentId, NamedTextColor.WHITE);
    }

    static String slotLabel(String slot) {
        return AttachmentLayout.label(slot);
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
