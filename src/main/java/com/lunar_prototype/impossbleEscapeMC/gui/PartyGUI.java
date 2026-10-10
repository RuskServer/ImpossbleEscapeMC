package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.party.Party;
import com.lunar_prototype.impossbleEscapeMC.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PDAのパーティー画面。パーティーの作成・招待・参加/拒否・メンバー管理・脱退/解散をGUIで行う
 */
public class PartyGUI implements Listener {

    private static final int SIZE = 27;
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int CREATE_SLOT = 11;
    private static final int PENDING_INVITE_SLOT = 13;
    private static final int INVITE_SLOT_SOLO = 15;
    private static final int INVITE_SLOT = 20;
    private static final int LEAVE_SLOT = 24;
    private static final int BACK_SLOT = 22;
    /** 脱退・解散の確認クリックの受付時間 */
    private static final long CONFIRM_WINDOW_MS = 3000;

    private final Player player;
    private final PartyManager manager;
    private final Inventory inventory;
    /** メンバー表示スロット → メンバー */
    private final Map<Integer, UUID> memberBySlot = new HashMap<>();
    private long leaveConfirmUntil = 0;

    public PartyGUI(Player player) {
        this.player = player;
        this.manager = ImpossbleEscapeMC.getInstance().getPartyManager();
        this.inventory = Bukkit.createInventory(null, SIZE, GuiBackground.PARTY.title());
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        memberBySlot.clear();

        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            setupNoParty();
        } else {
            setupParty(party);
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW, text("PDAに戻る", NamedTextColor.WHITE)));
    }

    private void setupNoParty() {
        inventory.setItem(CREATE_SLOT, item(Material.WHITE_BANNER, text("パーティーを作成", NamedTextColor.GREEN),
                text("自分がリーダーのパーティーを作ります。", NamedTextColor.GRAY),
                Component.empty(),
                text("クリックで作成", NamedTextColor.AQUA)));

        UUID inviterId = manager.getPendingInviter(player.getUniqueId());
        Player inviter = inviterId != null ? Bukkit.getPlayer(inviterId) : null;
        if (inviter != null) {
            inventory.setItem(PENDING_INVITE_SLOT, head(inviter, text(inviter.getName() + " からの招待", NamedTextColor.YELLOW),
                    text("パーティーへの招待が届いています。", NamedTextColor.GRAY),
                    Component.empty(),
                    text("左クリック: 参加する", NamedTextColor.GREEN),
                    text("右クリック: 拒否する", NamedTextColor.RED)));
        } else {
            inventory.setItem(PENDING_INVITE_SLOT, item(Material.PAPER, text("招待は届いていません", NamedTextColor.GRAY)));
        }

        inventory.setItem(INVITE_SLOT_SOLO, item(Material.WRITABLE_BOOK, text("プレイヤーを招待", NamedTextColor.AQUA),
                text("招待するとパーティーが作られ、", NamedTextColor.GRAY),
                text("あなたがリーダーになります。", NamedTextColor.GRAY),
                Component.empty(),
                text("クリックで招待する相手を選ぶ", NamedTextColor.AQUA)));
    }

    private void setupParty(Party party) {
        boolean isLeader = party.isLeader(player.getUniqueId());

        // リーダーを先頭に並べる
        List<UUID> members = new ArrayList<>(party.getMembers());
        members.remove(party.getLeader());
        members.add(0, party.getLeader());
        for (int i = 0; i < members.size() && i < MEMBER_SLOTS.length; i++) {
            UUID memberId = members.get(i);
            memberBySlot.put(MEMBER_SLOTS[i], memberId);
            inventory.setItem(MEMBER_SLOTS[i], memberHead(party, memberId, isLeader));
        }
        if (members.size() > MEMBER_SLOTS.length) {
            inventory.setItem(4, item(Material.PAPER, text("ほか " + (members.size() - MEMBER_SLOTS.length) + " 人", NamedTextColor.GRAY)));
        }

        if (isLeader) {
            inventory.setItem(INVITE_SLOT, item(Material.WRITABLE_BOOK, text("プレイヤーを招待", NamedTextColor.AQUA),
                    text("クリックで招待する相手を選ぶ", NamedTextColor.AQUA)));
        } else {
            inventory.setItem(INVITE_SLOT, item(Material.BOOK, text("プレイヤーを招待", NamedTextColor.GRAY),
                    text("リーダーのみが招待できます。", NamedTextColor.RED)));
        }

        boolean confirming = System.currentTimeMillis() < leaveConfirmUntil;
        String action = isLeader ? "パーティーを解散" : "パーティーを脱退";
        inventory.setItem(LEAVE_SLOT, item(confirming ? Material.RED_CONCRETE : Material.IRON_DOOR,
                text(action, NamedTextColor.RED),
                confirming
                        ? text("もう一度クリックで確定", NamedTextColor.YELLOW)
                        : text(isLeader ? "全員のパーティーがなくなります。" : "パーティーから抜けます。", NamedTextColor.GRAY),
                Component.empty(),
                text(confirming ? "クリックで確定" : "クリックして確認", NamedTextColor.AQUA)));
    }

    private ItemStack memberHead(Party party, UUID memberId, boolean viewerIsLeader) {
        OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
        String name = member.getName() != null ? member.getName() : "Unknown";
        boolean leader = party.isLeader(memberId);
        List<Component> lore = new ArrayList<>();
        lore.add(text(leader ? "リーダー" : "メンバー", leader ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        lore.add(text(member.isOnline() ? "オンライン" : "オフライン", member.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        if (viewerIsLeader && !memberId.equals(player.getUniqueId())) {
            lore.add(Component.empty());
            lore.add(text("左クリック: パーティーから外す", NamedTextColor.RED));
            lore.add(text("右クリック: リーダーを渡す", NamedTextColor.GOLD));
        }
        Component title = leader ? text("★ " + name, NamedTextColor.GOLD) : text(name, NamedTextColor.WHITE);
        return head(member, title, lore.toArray(Component[]::new));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        if (slot == BACK_SLOT) {
            player.closeInventory();
            new PDAGUI(player).open();
            return;
        }

        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            handleNoPartyClick(slot, event.getClick());
        } else {
            handlePartyClick(party, slot, event.getClick());
        }
    }

    private void handleNoPartyClick(int slot, ClickType click) {
        if (slot == CREATE_SLOT) {
            manager.createParty(player);
            refresh();
        } else if (slot == PENDING_INVITE_SLOT) {
            UUID inviterId = manager.getPendingInviter(player.getUniqueId());
            Player inviter = inviterId != null ? Bukkit.getPlayer(inviterId) : null;
            if (inviter == null) return;
            if (click.isRightClick()) {
                manager.declineInvite(player);
            } else {
                manager.acceptInvite(player, inviter.getName());
            }
            refresh();
        } else if (slot == INVITE_SLOT_SOLO) {
            player.closeInventory();
            new PartyInviteGUI(player, 0).open();
        }
    }

    private void handlePartyClick(Party party, int slot, ClickType click) {
        boolean isLeader = party.isLeader(player.getUniqueId());
        UUID memberId = memberBySlot.get(slot);
        if (memberId != null) {
            if (!isLeader || memberId.equals(player.getUniqueId())) return;
            if (click.isRightClick()) {
                manager.transferLeader(player, memberId);
            } else if (click.isLeftClick()) {
                manager.kickMember(player, memberId);
            }
            refresh();
        } else if (slot == INVITE_SLOT && isLeader) {
            player.closeInventory();
            new PartyInviteGUI(player, 0).open();
        } else if (slot == LEAVE_SLOT) {
            long now = System.currentTimeMillis();
            if (now < leaveConfirmUntil) {
                leaveConfirmUntil = 0;
                if (isLeader) {
                    manager.disbandParty(party);
                } else {
                    manager.leaveParty(player);
                }
            } else {
                leaveConfirmUntil = now + CONFIRM_WINDOW_MS;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.7f);
                // 確認の受付時間が過ぎたら表示を元に戻す
                Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), () -> {
                    if (player.getOpenInventory().getTopInventory().equals(inventory)) refresh();
                }, CONFIRM_WINDOW_MS / 50 + 1);
            }
            refresh();
        }
    }

    private void refresh() {
        setupGUI();
        player.updateInventory();
    }

    /** 背景の画像を見せるため空きスロットを残しているので、ドラッグで物を置けないようにする */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)
                && event.getRawSlots().stream().anyMatch(slot -> slot < inventory.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    // --- アイテム生成 ---

    static Component text(String content, NamedTextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack head(OfflinePlayer owner, Component name, Component... lore) {
        ItemStack item = item(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        item.setItemMeta(meta);
        return item;
    }
}
