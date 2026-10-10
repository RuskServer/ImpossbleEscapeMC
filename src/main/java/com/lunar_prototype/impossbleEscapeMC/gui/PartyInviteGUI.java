package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.lunar_prototype.impossbleEscapeMC.gui.PartyGUI.head;
import static com.lunar_prototype.impossbleEscapeMC.gui.PartyGUI.item;
import static com.lunar_prototype.impossbleEscapeMC.gui.PartyGUI.text;

/**
 * パーティーに招待する相手を選ぶ画面。オンラインでパーティーに入っていないプレイヤーを並べる
 */
public class PartyInviteGUI implements Listener {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Player player;
    private final PartyManager manager;
    /** タイトルにページ数を出すので、開くときに作る */
    private Inventory inventory;
    private final Map<Integer, UUID> targetBySlot = new HashMap<>();
    private int page;
    private int pageCount = 1;

    public PartyInviteGUI(Player player, int page) {
        this.player = player;
        this.manager = ImpossbleEscapeMC.getInstance().getPartyManager();
        this.page = page;
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        updatePages(candidates());
        this.inventory = Bukkit.createInventory(null, SIZE, GuiBackground.PARTY_INVITE.pagedTitle(page, pageCount));
        setupGUI();
        player.openInventory(inventory);
    }

    private List<Player> candidates() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> !DatapackGunnerManager.isGunner(p))
                .filter(p -> manager.getParty(p.getUniqueId()) == null)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> (Player) p)
                .toList();
    }

    private void setupGUI() {
        inventory.clear();
        targetBySlot.clear();

        List<Player> candidates = candidates();
        updatePages(candidates);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < candidates.size(); i++) {
            Player target = candidates.get(start + i);
            targetBySlot.put(i, target.getUniqueId());
            inventory.setItem(i, head(target, text(target.getName(), NamedTextColor.WHITE),
                    text("クリックで招待を送る", NamedTextColor.AQUA)));
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, text("招待できるプレイヤーがいません", NamedTextColor.GRAY),
                    text("オンラインで、パーティーに入っていない", NamedTextColor.GRAY),
                    text("プレイヤーを招待できます。", NamedTextColor.GRAY)));
        }

        if (page > 0) inventory.setItem(PREV_SLOT, item(Material.ARROW, text("前のページ", NamedTextColor.WHITE)));
        if (page < pageCount - 1) inventory.setItem(NEXT_SLOT, item(Material.ARROW, text("次のページ", NamedTextColor.WHITE)));
        inventory.setItem(BACK_SLOT, item(Material.BARRIER, text("パーティー画面に戻る", NamedTextColor.RED),
                text((page + 1) + " / " + pageCount + " ページ", NamedTextColor.GRAY)));
    }

    private void updatePages(List<Player> candidates) {
        pageCount = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pageCount - 1));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            player.closeInventory();
            new PartyGUI(player).open();
        } else if (slot == PREV_SLOT && page > 0) {
            // ページ数はタイトルに出ているので、開き直して更新する
            new PartyInviteGUI(player, page - 1).open();
        } else if (slot == NEXT_SLOT && page < pageCount - 1) {
            new PartyInviteGUI(player, page + 1).open();
        } else if (targetBySlot.containsKey(slot)) {
            Player target = Bukkit.getPlayer(targetBySlot.get(slot));
            if (target == null) {
                player.sendMessage(Component.text("そのプレイヤーはオフラインです。", NamedTextColor.RED));
                setupGUI();
                return;
            }
            // 招待するとパーティーが無ければ作られる (/party invite と同じ)
            manager.invitePlayer(player, target);
            player.closeInventory();
            new PartyGUI(player).open();
        }
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
}
