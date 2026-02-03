package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;

public class AttachmentGUI implements InventoryHolder {

    private final Inventory inventory;
    private final ItemStack gunItem;
    private final Player player;

    // スロット位置とアタッチメントスロットのマッピング
    private static final Map<Integer, AttachmentSlot> SLOT_MAP = new HashMap<>();

    static {
        for (AttachmentSlot slot : AttachmentSlot.values()) {
            SLOT_MAP.put(slot.getGuiSlot(), slot);
        }
    }

    public AttachmentGUI(Player player, ItemStack gunItem) {
        this.player = player;
        this.gunItem = gunItem;
        this.inventory = Bukkit.createInventory(this, 27, "§8アタッチメント");

        initializeGUI();
    }

    private void initializeGUI() {
        // 背景をグレーガラスで埋める
        ItemStack filler = createFiller();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // 中央に銃アイテムを表示 (クリック不可の表示用)
        inventory.setItem(13, gunItem.clone());

        // 各スロットにプレースホルダーまたは装着済みアタッチメントを配置
        List<String> attachments = getAttachmentList();

        for (AttachmentSlot slot : AttachmentSlot.values()) {
            int guiSlot = slot.getGuiSlot();
            String attachmentId = (slot.getId() < attachments.size()) ? attachments.get(slot.getId()) : null;

            if (attachmentId != null && !attachmentId.isEmpty()) {
                // 装着済みアタッチメントを表示
                AttachmentDefinition attDef = ItemRegistry.getAttachment(attachmentId);
                if (attDef != null) {
                    ItemStack attItem = ItemFactory.create(attachmentId);
                    if (attItem != null) {
                        ItemMeta meta = attItem.getItemMeta();
                        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore())
                                : new ArrayList<>();
                        lore.add("");
                        lore.add("§e左クリック: 取り外し");
                        meta.setLore(lore);
                        attItem.setItemMeta(meta);
                        inventory.setItem(guiSlot, attItem);
                        continue;
                    }
                }
            }

            // 空きスロットのプレースホルダー
            inventory.setItem(guiSlot, createSlotPlaceholder(slot));
        }
    }

    private List<String> getAttachmentList() {
        if (gunItem == null || !gunItem.hasItemMeta())
            return Collections.emptyList();
        PersistentDataContainer pdc = gunItem.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
        if (joined == null || joined.isEmpty())
            return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(joined.split(",")));
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        return filler;
    }

    private ItemStack createSlotPlaceholder(AttachmentSlot slot) {
        ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        meta.setDisplayName("§7" + slot.name());
        List<String> lore = new ArrayList<>();
        lore.add("§8空きスロット");
        lore.add("");
        lore.add("§eアタッチメントをカーソルに持って");
        lore.add("§eクリックで装着");
        meta.setLore(lore);
        placeholder.setItemMeta(meta);
        return placeholder;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ItemStack getGunItem() {
        return gunItem;
    }

    public Player getPlayer() {
        return player;
    }

    public static AttachmentSlot getSlotFromGuiSlot(int guiSlot) {
        return SLOT_MAP.get(guiSlot);
    }
}
