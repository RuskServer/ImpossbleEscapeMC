package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Stoppable;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.market.MarketMainGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.market.MarketModule;
import com.lunar_prototype.impossbleEscapeMC.modules.party.PartyGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidSelectionGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashPageSelectorGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.*;
import static com.lunar_prototype.impossbleEscapeMC.gui.PdaHomeModel.Action;

/** 従来のPDA背景と27枠のチェスト画面を維持し、隠れ家の二つの操作を分ける。 */
@Service
final class PdaHomeScreen implements Listener, PdaHomeController.Port, Startable, Stoppable {
    private record HomeInventory(Inventory inventory, String token, PdaHomeController.Page page) {}
    private final Map<UUID, HomeInventory> inventories = new HashMap<>();
    private static PdaHomeScreen instance;
    private final JavaPlugin plugin;
    private final PlayerDataModule dataModule;
    private final LevelModule levels;
    private final RaidModule raids;
    private final HideoutModule hideout;
    private final PartyModule parties;
    private final QuestModule quests;
    private final StashModule stash;
    private final TraderModule traders;
    private final com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule backpacks;
    private final MarketModule market;
    private final com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule economy;

    private final PdaHomeController controller = new PdaHomeController(this);
    @Inject
    public PdaHomeScreen(JavaPlugin plugin, PlayerDataModule dataModule, LevelModule levels, RaidModule raids, HideoutModule hideout, PartyModule parties, QuestModule quests, StashModule stash, TraderModule traders, MarketModule market, com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule backpacks, com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule economy) {
        this.plugin = plugin;
        this.dataModule = dataModule;
        this.levels = levels;
        this.raids = raids;
        this.hideout = hideout;
        this.parties = parties;
        this.quests = quests;
        this.stash = stash;
        this.traders = traders;
        this.market = market;
        this.backpacks = backpacks;
        this.economy = economy;
    }

    @Override public void start() { instance = this; }
    @Override public void stop() { if (instance == this) shutdown(); }

    static void open(Player player) {
        if (instance == null || !instance.plugin.isEnabled()) return;
        PdaHomeScreen screen = instance;
        // 他のインベントリのクリック処理・返却が終わってから開く。
        Bukkit.getScheduler().runTask(screen.plugin, () -> {
            if (instance != screen || !screen.plugin.isEnabled() || !player.isOnline() || player.isDead()) return;
            player.closeInventory();
            screen.controller.open(player);
        });
    }

    static void shutdown() {
        if (instance == null) return;
        instance.controller.clear();
        for (var id : List.copyOf(instance.inventories.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) instance.close(player);
        }
        instance.inventories.clear();
        HandlerList.unregisterAll(instance);
        instance = null;
    }

    @Override public PdaHomeModel.View snapshot(Player player) {
        if (dataModule == null || levels == null) return null;
        var data = dataModule.getPlayerData(player.getUniqueId());
        if (data == null) return null;
        boolean inRaid = raids != null && raids.isInRaid(player);
        boolean inHideout = hideout != null && hideout.getWorldManager().isPlayerWorld(player.getWorld());
        var available = EnumSet.of(Action.SETTINGS, Action.REFRESH, Action.CLOSE);
        if (raids != null) available.add(Action.RAID);
        if (quests != null) available.add(Action.QUESTS);
        if (stash != null) available.add(Action.STASH);
        if (traders != null) available.add(Action.TRADERS);
        if (market != null) available.add(Action.MARKET);
        if (hideout != null) {available.add(Action.HIDEOUT_MENU);available.add(Action.HIDEOUT);available.add(Action.FACILITIES);}
        String partyText = "パーティー未所属", invitation = null;
        if (parties != null) {
            available.add(Action.PARTY);
            var party = parties.getParty(player.getUniqueId());
            if (party != null) partyText = party.getSize() + "人 / " + (party.isLeader(player.getUniqueId()) ? "リーダー" : "メンバー");
            var inviterId = parties.getPendingInviter(player.getUniqueId());
            Player inviter = inviterId != null ? Bukkit.getPlayer(inviterId) : null;
            if (inviter != null) invitation = inviter.getName();
        }
        return new PdaHomeModel.View(player.getName(), data.getLevel(), data.getExperience(),
                levels.getRequiredExperience(data.getLevel()), data.getBalance(), data.getExtractions(),
                inRaid, inHideout, partyText, invitation, available);
    }

    static Component buttonLabel(PdaHomeModel.Entry entry) {
        return Component.text(entry.title(), entry.enabled() ? NAME : DIM)
                .append(Component.text(" ｜ ", AMBER_DIM))
                .append(Component.text(entry.detail(), entry.enabled() ? NOTE : NG));
    }

    static List<Component> header(PdaHomeModel.View view) {
        double progress = Math.clamp(view.experience() * 100.0 / Math.max(1, view.requiredExperience()), 0, 100);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(view.operator() + "  /  " + view.location(), AMBER));
        lines.add(Component.text("Lv." + view.level() + "   EXP " + view.experience() + " / " + view.requiredExperience()
                + String.format(Locale.ROOT, " (%.0f%%)", progress), INK));
        lines.add(Component.text(String.format(Locale.ROOT, "所持金 %,.1f ₽  /  脱出 %,d回", view.balance(), view.extractions()), INK));
        Component party = Component.text(view.party(), NOTE);
        if (view.invitation() != null) party = party.append(Component.text("  /  招待：" + view.invitation(), OK));
        lines.add(party);
        return List.copyOf(lines);
    }

    static int slot(PdaHomeController.Page page, Action action) {
        if (page == PdaHomeController.Page.HIDEOUT) return switch (action) {
            case HIDEOUT -> 11; case FACILITIES -> 15; case BACK -> 22; default -> -1;
        };
        return switch (action) {
            case HIDEOUT_MENU -> 9; case PARTY -> 10; case STASH -> 11; case MARKET -> 12;
            case RAID -> 14; case TRADERS -> 15; case QUESTS -> 16; case SETTINGS -> 17;
            case REFRESH -> 26; case CLOSE -> 22; default -> -1;
        };
    }

    static Action actionAt(PdaHomeController.Page page, int slot) {
        if (slot < 0 || slot >= 27) return null;
        for (Action action : Action.values()) if (slot(page, action) == slot) return action;
        return null;
    }

    private static Material icon(Action action) {
        return switch (action) {
            case RAID -> Material.FILLED_MAP; case QUESTS -> Material.BOOK;
            case STASH -> Material.CHEST; case TRADERS -> Material.EMERALD;
            case HIDEOUT_MENU, HIDEOUT -> Material.CAMPFIRE; case FACILITIES -> Material.CRAFTING_TABLE;
            case MARKET -> Material.GOLD_INGOT; case PARTY -> Material.WHITE_BANNER;
            case SETTINGS -> Material.REPEATER; case REFRESH -> Material.CLOCK;
            case CLOSE -> Material.BARRIER; case BACK -> Material.ARROW;
        };
    }

    /** ボタンのピクトグラム (リソースパックの iem:pda_<名前>) */
    private static String pictogram(Action action) {
        return switch (action) {
            case RAID -> "raid"; case QUESTS -> "quests"; case STASH -> "stash"; case TRADERS -> "traders";
            case HIDEOUT_MENU -> "hideout"; case HIDEOUT -> "move"; case FACILITIES -> "facilities";
            case MARKET -> "market"; case PARTY -> "party"; case SETTINGS -> "settings"; case REFRESH -> "refresh";
            case CLOSE -> "close"; case BACK -> "back";
        };
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override public void show(Player player, String token, PdaHomeModel.View view, PdaHomeController.Page page) {
        boolean hideoutPage = page == PdaHomeController.Page.HIDEOUT;
        Inventory inventory = Bukkit.createInventory(null, 27, (hideoutPage ? GuiBackground.PDA_HIDEOUT : GuiBackground.PDA).title());
        ItemStack head = item(Material.PLAYER_HEAD, Component.text(view.operator(), AMBER), header(view));
        var skull = (SkullMeta) head.getItemMeta();
        skull.setOwningPlayer(player);
        head.setItemMeta(skull);
        if (!hideoutPage) inventory.setItem(4, head);
        if (!hideoutPage) inventory.setItem(13, PdaIcons.of(item(Material.EXPERIENCE_BOTTLE, Component.text("統計情報 / Lv." + view.level(), AMBER), header(view)), "stats"));
        for (var entry : hideoutPage ? PdaHomeModel.hideoutEntries(view) : PdaHomeModel.entries(view)) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(entry.description(), NOTE));
            if (entry.action() == Action.PARTY) {
                lore.add(Component.text(view.party(), NOTE));
                if (view.invitation() != null) lore.add(Component.text("招待：" + view.invitation(), OK));
            }
            lore.add(Component.empty());
            lore.add(Component.text(entry.enabled() ? "クリックして開く" : entry.reason(), entry.enabled() ? OK : NG));
            ItemStack button = item(entry.enabled() ? icon(entry.action()) : Material.BARRIER, buttonLabel(entry), lore);
            inventory.setItem(slot(page, entry.action()), entry.enabled() ? PdaIcons.of(button, pictogram(entry.action()))
                    : PdaIcons.off(button, pictogram(entry.action())));
        }
        Action exit = hideoutPage ? Action.BACK : Action.CLOSE;
        inventory.setItem(slot(page, exit), PdaIcons.of(item(hideoutPage ? Material.ARROW : Material.BARRIER,
                Component.text(hideoutPage ? "PDAへ戻る" : "閉じる", NOTE), List.of()), pictogram(exit)));
        // 先に新しい画面を登録し、古い画面のCloseイベントによる新セッション削除を防ぐ。
        inventories.put(player.getUniqueId(), new HomeInventory(inventory, token, page));
        player.openInventory(inventory);
    }

    @Override public void unavailable(Player player) {
        Messages.chat(player, "iem.system.pdahomescreen.unavailable.notice_001", MessageText.of("iem.system.pdahomescreen.unavailable.notice_001", MessageText.payload(MessageText.of("iem.system.pdahomescreen.unavailable.notice_001.text_01").color(NG))));
    }
    @Override public void denied(Player player, String reason) {
        Messages.chat(player, "iem.system.pdahomescreen.denied.notice_001", MessageText.of("iem.system.pdahomescreen.denied.notice_001", MessageText.payload(MessageText.of("iem.system.pdahomescreen.denied.notice_001.text_01", reason).color(NG))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }
    @Override public void close(Player player) {
        HomeInventory home = inventories.remove(player.getUniqueId());
        if (home != null && player.getOpenInventory().getTopInventory() == home.inventory()) player.closeInventory();
    }

    @Override public void dispatch(Player player, Action action, PdaHomeModel.View current) {
        player.playSound(player, "iem:pda.click", SoundCategory.MASTER, 1.0f, 1.0f);
        switch (action) {
            case RAID -> RaidSelectionGUI.open(player);
            case QUESTS -> QuestDialog.openPda(player);
            case STASH -> new StashPageSelectorGUI(player, stash, plugin, dataModule).open();
            case TRADERS -> new TraderSelectionGUI(player, traders, backpacks).open();
            case HIDEOUT -> {
                if (current.inHideout()) hideout.returnToLobby(player);else hideout.visit(player);
            }
            case FACILITIES -> HideoutDialog.open(player);
            case MARKET -> new MarketMainGUI(player, market, plugin, economy).open();
            case PARTY -> new PartyGUI(player, parties, plugin).open();
            case SETTINGS -> GunSettingsDialog.open(player);
            default -> { }
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        HomeInventory home = inventories.get(player.getUniqueId());
        if (home == null || event.getView().getTopInventory() != home.inventory()) return;
        // 下段からのShift移動・数字キー・ダブルクリックも含め、表示中は移動させない。
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= home.inventory().getSize()
                || !(event.isLeftClick() || event.isRightClick()) || event.isShiftClick()) return;
        Action action = actionAt(home.page(), event.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (instance == this && inventories.get(player.getUniqueId()) == home
                    && player.getOpenInventory().getTopInventory() == home.inventory())
                controller.handle(player, home.token(), action);
        });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        HomeInventory home = inventories.get(event.getWhoClicked().getUniqueId());
        if (home != null && event.getView().getTopInventory() == home.inventory()
                && event.getRawSlots().stream().anyMatch(slot -> slot < home.inventory().getSize())) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        HomeInventory home = inventories.get(id);
        if (home != null && event.getInventory() == home.inventory()) {
            inventories.remove(id);
            controller.forget(id);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        inventories.remove(event.getPlayer().getUniqueId());
        controller.forget(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        inventories.remove(event.getEntity().getUniqueId());
        controller.forget(event.getEntity().getUniqueId());
    }
}
