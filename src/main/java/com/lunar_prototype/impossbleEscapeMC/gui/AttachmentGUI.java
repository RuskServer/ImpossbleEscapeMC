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
        // 背景と、銃から各スロットへの線 (プラグイン銃はスロットがいつも全部ある)
        List<Integer> positions = new ArrayList<>();
        for (AttachmentSlot slot : AttachmentSlot.values()) positions.add(slot.getGuiSlot());
        this.inventory = Bukkit.createInventory(this, AttachmentLayout.SIZE, AttachmentLayout.title(positions));

        initializeGUI();
    }

    private void initializeGUI() {
        // 背景 (作業台の整備マット) はタイトルの画像で描くので、空きスロットは空のままにする

        // 中央に銃アイテムを表示 (クリック不可の表示用)
        inventory.setItem(AttachmentLayout.GUN_SLOT, gunItem.clone());

        // 各スロットにプレースホルダーまたは装着済みアタッチメントを配置
        List<String> attachments = getAttachmentList();

        for (AttachmentSlot slot : AttachmentSlot.values()) {
            int guiSlot = slot.getGuiSlot();
            String attachmentId = (slot.getId() < attachments.size()) ? attachments.get(slot.getId()) : null;

            // デフォルトのアタッチメントを取得
            String defaultAtt = "";
            String gunId = gunItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition gunDef = ItemRegistry.get(gunId);
            if (gunDef != null && gunDef.gunStats != null && gunDef.gunStats.defaultAttachments != null) {
                if (gunDef.gunStats.defaultAttachments.size() > slot.getId()) {
                    defaultAtt = gunDef.gunStats.defaultAttachments.get(slot.getId());
                }
            }

            // 装着済みかつデフォルトでないアタッチメントを表示
            if (attachmentId != null && !attachmentId.isEmpty() && !attachmentId.equals(defaultAtt)) {
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

            // 空きスロット（またはデフォルトパーツ装着中）のプレースホルダー
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

    private ItemStack createSlotPlaceholder(AttachmentSlot slot) {
        ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        // 見た目はパーツの絵文字 (リソースパック)
        meta.setItemModel(AttachmentLayout.slotIcon(slot.key()));
        meta.setDisplayName("§7" + AttachmentLayout.label(slot.key()));
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
