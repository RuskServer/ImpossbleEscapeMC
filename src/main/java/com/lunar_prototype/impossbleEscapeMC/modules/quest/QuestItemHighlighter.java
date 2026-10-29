package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutMaterialNeeds;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import static com.lunar_prototype.impossbleEscapeMC.modules.quest.ItemNeedPresentation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クエスト納品と隠れ家の次段階の素材を、そのプレイヤーの画面でだけ強調する。
 * <p>
 * アイテムそのもの (サーバーの持ち物) は変えず、そのプレイヤーへ送るアイテムの表示だけを書き換える
 * (インベントリ・スタッシュ・コンテナ・カーソル)。プレイヤーごとに受けているクエストが違うため。
 * <ul>
 *   <li>納品に使えるアイテム: 光らせ (エンチャントの光)、説明の最後に「クエスト「名前」で納品 (あと N)」</li>
 *   <li>FIR 品だけを受け付ける目標で、FIR でないアイテム: 光らせず、「FIR 品のみ (このアイテムは対象外)」と出す</li>
 * </ul>
 * 必要な物の一覧はメインスレッドで {@link #REFRESH_TICKS} ごとに作り直し、変わった説明の欄だけ送り直す
 * (パケットは別のスレッドで書き換えるので、クエストの進捗は直接読まない)。
 * アイテムの種類も、プラグインのアイテム・弾は登録表から読むが、データパックの銃・アタッチメントは
 * サーバーのデータを読むので、メインスレッドで持ち物 (と開いているコンテナ) を見て先に調べておく ({@link #DATAPACK_DEFINITIONS})。
 * クリエイティブではクライアントが表示どおりのアイテムを送り返してくるので、書き換えない
 */
public final class QuestItemHighlighter extends PacketListenerAbstract implements org.bukkit.event.Listener {

    private static final int REFRESH_TICKS = 20;

    private static final Snapshot EMPTY = new Snapshot(List.of(),Map.of());

    private static QuestItemHighlighter instance;
    /** データパックの銃・アタッチメントの種類 (メインスレッドで調べた物)。種類の無い物は NONE */
    private static final Map<String, QuestItems.Definition> DATAPACK_DEFINITIONS = new ConcurrentHashMap<>();
    private static final QuestItems.Definition NONE = new QuestItems.Definition("", "", "");

    private final ImpossbleEscapeMC plugin;
    private final QuestModule questModule;
    private final Map<UUID, Snapshot> needs = new ConcurrentHashMap<>();
    private final BukkitTask task;

    private QuestItemHighlighter(ImpossbleEscapeMC plugin, QuestModule questModule) {
        this.plugin = plugin;
        this.questModule = questModule;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin, QuestModule questModule) {
        if (instance != null || questModule == null) return;
        instance = new QuestItemHighlighter(plugin, questModule);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
        Bukkit.getPluginManager().registerEvents(instance,plugin);
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.task.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance);
        org.bukkit.event.HandlerList.unregisterAll(instance);
        instance.needs.clear();
        instance = null;
    }

    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.CREATIVE) return;
        Snapshot previous = needs.remove(event.getPlayer().getUniqueId());
        if (previous != null) refreshChangedSlots(event.getPlayer(),previous,EMPTY,java.util.Set.of());
    }

    private void refreshAll() {
        needs.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Map<Player,Snapshot> currentByPlayer = new java.util.LinkedHashMap<>();
        java.util.Set<String> learned = new java.util.HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Snapshot current = collect(player);
            currentByPlayer.put(player,current);
            if (!current.empty()) learned.addAll(learnDatapackItems(player));
        }
        currentByPlayer.forEach((player,current) -> {
            Snapshot previous = current.empty() ? needs.remove(player.getUniqueId()) : needs.put(player.getUniqueId(),current);
            if (!learned.isEmpty() || !current.equals(previous == null ? EMPTY : previous))
                refreshChangedSlots(player,previous == null ? EMPTY : previous,current,learned);
        });
    }

    /** 表示が変わった欄だけ送り直す。カーソル・状態ID・メニューの実体は変更しない。 */
    private static void refreshChangedSlots(Player player, Snapshot previous, Snapshot current, java.util.Set<String> learned) {
        if (!(player instanceof CraftPlayer craft)) return;
        var menu = craft.getHandle().containerMenu;
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            ItemStack item = CraftItemStack.asBukkitCopy(menu.getSlot(slot).getItem());
            var packet = SpigotConversionUtil.fromBukkitItemStack(item);
            if (!displayChanged(packet,previous,current,learned)) continue;
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerSetSlot(menu.containerId,menu.getStateId(),slot,packet));
        }
        var cursor = SpigotConversionUtil.fromBukkitItemStack(player.getItemOnCursor());
        if (displayChanged(cursor,previous,current,learned)) PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerSetCursorItem(cursor));
    }

    private static boolean displayChanged(com.github.retrooper.packetevents.protocol.item.ItemStack item, Snapshot previous, Snapshot current, java.util.Set<String> learned) {
        var after = highlight(item,current);
        if (!java.util.Objects.equals(highlight(item,previous),after)) return true;
        if (after == null || learned.isEmpty()) return false;
        String id = QuestHandInDialog.handInIdOf(SpigotConversionUtil.toBukkitItemStack(item));
        return id != null && learned.contains(id);
    }

    /** 持ち物と開いているコンテナのデータパックの銃・アタッチメントの種類を調べておく。新しく調べたIDを返す */
    private static java.util.Set<String> learnDatapackItems(Player player) {
        java.util.Set<String> learned = new java.util.HashSet<>();
        List<ItemStack> items = new ArrayList<>(java.util.Arrays.asList(player.getInventory().getContents()));
        items.addAll(java.util.Arrays.asList(player.getOpenInventory().getTopInventory().getContents()));
        items.add(player.getItemOnCursor());
        for (ItemStack item : items) {
            String id = QuestHandInDialog.handInIdOf(item);
            if (id == null || registryDefinition(id) != null || DATAPACK_DEFINITIONS.containsKey(id)) continue;
            QuestItems.Definition def = QuestItems.resolve(id);
            DATAPACK_DEFINITIONS.put(id, def != null ? def : NONE);
            learned.add(id);
        }
        return learned;
    }

    /** プラグインの登録表 (読むだけで、どのスレッドからでもよい) にあるアイテム・弾・アタッチメントの種類 (QuestItems と同じ) */
    private static QuestItems.Definition registryDefinition(String id) {
        var attachment = com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry.getAttachment(id);
        if (attachment != null) return new QuestItems.Definition(id, "ATTACHMENT", attachment.displayName);
        var item = com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry.get(id);
        if (item != null) return new QuestItems.Definition(id, item.type, item.displayName);
        var ammo = com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry.getAmmo(id);
        return ammo != null ? new QuestItems.Definition(id, "AMMO", ammo.displayName) : null;
    }

    /** パケットのスレッドで使う種類。まだ調べていないデータパックの物はnull */
    private static QuestItems.Definition definition(String id) {
        QuestItems.Definition def = registryDefinition(id);
        if (def != null) return def;
        def = DATAPACK_DEFINITIONS.get(id);
        return def == NONE ? null : def;
    }

    /** 受けているクエストの、まだ終わっていない納品の目標 */
    private Snapshot collect(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return EMPTY;
        PlayerDataModule dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        if (data == null) return EMPTY;
        List<Need> result = new ArrayList<>();
        for (ActiveQuest active : data.getActiveQuests().values()) {
            if (active.isCompleted()) continue;
            QuestDefinition quest = questModule.getQuest(active.getQuestId());
            if (quest == null) continue;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!(objectives.get(i) instanceof HandInObjective handIn) || handIn.isCompleted(active, i)) continue;
                result.add(new Need(quest.getDisplayName(), handIn, handIn.getTargetAmount() - active.getProgress(i)));
            }
        }
        HideoutModule hideout = plugin.getServiceContainer().get(HideoutModule.class);
        return new Snapshot(result,hideout == null ? Map.of() : HideoutMaterialNeeds.collect(player,data,hideout));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;
        Snapshot list = needs.get(user.getUUID());
        if (list == null || list.empty()) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            var shown = highlight(packet.getItem(), list);
            if (shown != null) {
                packet.setItem(shown);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(packet.getItems());
            boolean changed = false;
            for (int i = 0; i < items.size(); i++) {
                var shown = highlight(items.get(i), list);
                if (shown != null) {
                    items.set(i, shown);
                    changed = true;
                }
            }
            var carried = packet.getCarriedItem().map(item -> highlight(item, list)).orElse(null);
            if (carried != null) {
                packet.setCarriedItem(carried);
                changed = true;
            }
            if (changed) {
                packet.setItems(items);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
            var shown = highlight(packet.getStack(), list);
            if (shown != null) {
                packet.setStack(shown);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
            WrapperPlayServerSetCursorItem packet = new WrapperPlayServerSetCursorItem(event);
            var shown = highlight(packet.getStack(), list);
            if (shown != null) {
                packet.setStack(shown);
                event.markForReEncode(true);
            }
        }
    }

    /** 実体へ保存せず、送信するコピーだけに表示を追加する。 */
    private static com.github.retrooper.packetevents.protocol.item.ItemStack highlight(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem, Snapshot snapshot) {
        if (snapshot.empty() || packetItem == null || packetItem.isEmpty()) return null;
        ItemStack item = SpigotConversionUtil.toBukkitItemStack(packetItem).clone();
        String itemId = QuestHandInDialog.handInIdOf(item);
        if (itemId == null) return null;
        QuestItems.Definition def = definition(itemId);
        boolean fir = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
        Decoration decoration = describe(itemId,def == null ? null : def.type(),fir,snapshot);
        if (decoration.lines().isEmpty()) return null;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(decoration.lines());
        meta.lore(lore);
        if (decoration.glow()) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }

}
