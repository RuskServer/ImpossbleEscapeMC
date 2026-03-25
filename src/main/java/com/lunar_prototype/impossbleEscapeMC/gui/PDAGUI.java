package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.market.MarketMainGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.market.MarketModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI;
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
import org.bukkit.inventory.meta.SkullMeta;

import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import java.util.ArrayList;
import java.util.List;

public class PDAGUI implements Listener {
    private final Player player;
    private final Inventory inventory;

    public PDAGUI(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("PDA - Player Information").decoration(TextDecoration.ITALIC, false));
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();

        // 背景
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        LevelModule levelModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(LevelModule.class);
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());

        // プレイヤーの頭
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.displayName(Component.text(player.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        head.setItemMeta(headMeta);
        inventory.setItem(4, head);

        // レベル情報
        ItemStack stats = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.displayName(Component.text("統計情報", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("レベル: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(data.getLevel(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        
        long currentExp = data.getExperience();
        long requiredExp = levelModule.getRequiredExperience(data.getLevel());
        double progress = (double) currentExp / requiredExp;
        int barLength = 10;
        int completed = (int) (progress * barLength);
        
        StringBuilder progressBar = new StringBuilder("§a");
        progressBar.append("■".repeat(Math.max(0, completed)));
        progressBar.append("§7");
        progressBar.append("■".repeat(Math.max(0, barLength - completed)));
        
        lore.add(Component.text("経験値: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(currentExp + " / " + requiredExp, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("進捗: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(progressBar.toString() + " " + String.format("%.1f", progress * 100) + "%", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("脱出成功回数: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(data.getExtractions(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("所持金: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.1f", data.getBalance()) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        
        statsMeta.lore(lore);
        stats.setItemMeta(statsMeta);
        inventory.setItem(13, stats);

        // モジュール
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
        boolean inRaid = raidModule.isInRaid(player);

        // Stashボタン
        ItemStack stash = new ItemStack(inRaid ? Material.BARRIER : Material.CHEST);
        ItemMeta stashMeta = stash.getItemMeta();
        stashMeta.displayName(Component.text("Stash (倉庫)", inRaid ? NamedTextColor.RED : NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> stashLore = new ArrayList<>();
        stashLore.add(Component.text("アイテムを安全に保管します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stashLore.add(Component.text("レベルに応じて容量が増加します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stashLore.add(Component.empty());
        if (inRaid) {
            stashLore.add(Component.text("レイド中は開くことができません！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            stashLore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        stashMeta.lore(stashLore);
        stash.setItemMeta(stashMeta);
        inventory.setItem(11, stash);

        // Raidボタン
        ItemStack raid = new ItemStack(inRaid ? Material.BARRIER : Material.FILLED_MAP);
        ItemMeta raidMeta = raid.getItemMeta();
        raidMeta.displayName(Component.text("Raid (出撃)", inRaid ? NamedTextColor.RED : NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> raidLore = new ArrayList<>();
        raidLore.add(Component.text("出撃先を選択してレイド待機列に参加します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        raidLore.add(Component.empty());
        if (inRaid) {
            raidLore.add(Component.text("レイド中は開くことができません！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            raidLore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        raidMeta.lore(raidLore);
        raid.setItemMeta(raidMeta);
        inventory.setItem(14, raid);

        // Tradersボタン
        ItemStack traders = new ItemStack(inRaid ? Material.BARRIER : Material.EMERALD);
        ItemMeta tradersMeta = traders.getItemMeta();
        tradersMeta.displayName(Component.text("Traders (商人)", inRaid ? NamedTextColor.RED : NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> tradersLore = new ArrayList<>();
        tradersLore.add(Component.text("商人からアイテムを購入・売却します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        tradersLore.add(Component.empty());
        if (inRaid) {
            tradersLore.add(Component.text("レイド中は開くことができません！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            tradersLore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        tradersMeta.lore(tradersLore);
        traders.setItemMeta(tradersMeta);
        inventory.setItem(15, traders);

        // Questsボタン
        ItemStack quests = new ItemStack(Material.BOOK);
        ItemMeta questsMeta = quests.getItemMeta();
        questsMeta.displayName(Component.text("Quests (クエスト進捗)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> questsLore = new ArrayList<>();
        questsLore.add(Component.text("現在進行中のクエストを確認します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        questsLore.add(Component.empty());
        questsLore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        questsMeta.lore(questsLore);
        quests.setItemMeta(questsMeta);
        inventory.setItem(16, quests);

        // Marketボタン
        int level = data.getLevel();
        ItemStack market = new ItemStack(level >= 10 ? Material.GOLD_INGOT : Material.BARRIER);
        ItemMeta marketMeta = market.getItemMeta();
        marketMeta.displayName(Component.text("Market (マーケット)", level >= 10 ? NamedTextColor.YELLOW : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> marketLore = new ArrayList<>();
        marketLore.add(Component.text("プレイヤー間でFIRアイテムを売買します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        marketLore.add(Component.empty());
        if (level < 10) {
            marketLore.add(Component.text("解放レベル: Lv.10 (現在のレベル: " + level + ")", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else if (inRaid) {
            marketLore.add(Component.text("レイド中は開くことができません！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            marketLore.add(Component.text("クリックして開く", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        marketMeta.lore(marketLore);
        market.setItemMeta(marketMeta);
        inventory.setItem(12, market);
        
        // 閉じるボタン
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inventory.setItem(22, close);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot == 22) {
            player.closeInventory();
        } else if (slot == 11) {
            RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
            if (raidModule.isInRaid(player)) {
                player.sendMessage(Component.text("§cレイド中はStashを開くことができません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            player.closeInventory();
            com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule stashModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule.class);
            new com.lunar_prototype.impossbleEscapeMC.modules.stash.StashPageSelectorGUI(player, stashModule).open();
        } else if (slot == 14) {
            RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
            if (raidModule.isInRaid(player)) {
                player.sendMessage(Component.text("§cレイド中は出撃画面を開くことができません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            player.closeInventory();
            new RaidSelectionGUI(raidModule).open(player);
        } else if (slot == 15) {
            RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
            if (raidModule.isInRaid(player)) {
                player.sendMessage(Component.text("§cレイド中は商人と取引することができません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            player.closeInventory();
            TraderModule traderModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(TraderModule.class);
            new TraderSelectionGUI(player, traderModule).open();
        } else if (slot == 16) {
            player.closeInventory();
            com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule questModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
            new com.lunar_prototype.impossbleEscapeMC.modules.quest.PDAQuestGUI(player, questModule).open();
        } else if (slot == 12) {
            LevelModule levelModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(LevelModule.class);
            if (levelModule.getLevel(player.getUniqueId()) < 10) {
                player.sendMessage(Component.text("§cグローバルマーケットはレベル10から利用可能です。", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
            if (raidModule.isInRaid(player)) {
                player.sendMessage(Component.text("§cレイド中はマーケットを開くことができません！", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            player.closeInventory();
            MarketModule marketModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(MarketModule.class);
            new MarketMainGUI(player, marketModule).open();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
