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
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受けているクエストで納品するアイテムを、そのプレイヤーの画面でだけ強調する。
 * <p>
 * アイテムそのもの (サーバーの持ち物) は変えず、そのプレイヤーへ送るアイテムの表示だけを書き換える
 * (インベントリ・スタッシュ・コンテナ・カーソル)。プレイヤーごとに受けているクエストが違うため。
 * <ul>
 *   <li>納品に使えるアイテム: 光らせ (エンチャントの光)、説明の最後に「クエスト「名前」で納品 (あと N)」</li>
 *   <li>FIR 品だけを受け付ける目標で、FIR でないアイテム: 光らせず、「FIR 品のみ (このアイテムは対象外)」と出す</li>
 * </ul>
 * 必要な物の一覧はメインスレッドで {@link #REFRESH_TICKS} ごとに作り直し、変わったら持ち物を送り直す
 * (パケットは別のスレッドで書き換えるので、クエストの進捗は直接読まない)。
 * アイテムの種類も、プラグインのアイテム・弾は登録表から読むが、データパックの銃・アタッチメントは
 * サーバーのデータを読むので、メインスレッドで持ち物 (と開いているコンテナ) を見て先に調べておく ({@link #DATAPACK_DEFINITIONS})。
 * クリエイティブではクライアントが表示どおりのアイテムを送り返してくるので、書き換えない
 */
public final class QuestItemHighlighter extends PacketListenerAbstract {

    private static final int REFRESH_TICKS = 20;

    /** 納品の目標 1 つ (クエストの名前、あと何個) */
    private record Need(String questName, HandInObjective objective, int remaining) {
    }

    private static QuestItemHighlighter instance;
    /** データパックの銃・アタッチメントの種類 (メインスレッドで調べた物)。種類の無い物は NONE */
    private static final Map<String, QuestItems.Definition> DATAPACK_DEFINITIONS = new ConcurrentHashMap<>();
    private static final QuestItems.Definition NONE = new QuestItems.Definition("", "", "");

    private final ImpossbleEscapeMC plugin;
    private final QuestModule questModule;
    private final Map<UUID, List<Need>> needs = new ConcurrentHashMap<>();
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
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.task.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance);
        instance.needs.clear();
        instance = null;
    }

    private void refreshAll() {
        needs.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Need> current = collect(player);
            List<Need> previous = current.isEmpty() ? needs.remove(player.getUniqueId()) : needs.put(player.getUniqueId(), current);
            boolean learned = !current.isEmpty() && learnDatapackItems(player);
            if (learned || !current.equals(previous == null ? List.of() : previous)) player.updateInventory();
        }
    }

    /** 持ち物と開いているコンテナのデータパックの銃・アタッチメントの種類を調べておく。新しく調べた物があれば true */
    private static boolean learnDatapackItems(Player player) {
        boolean learned = false;
        List<ItemStack> items = new ArrayList<>(java.util.Arrays.asList(player.getInventory().getContents()));
        items.addAll(java.util.Arrays.asList(player.getOpenInventory().getTopInventory().getContents()));
        items.add(player.getItemOnCursor());
        for (ItemStack item : items) {
            String id = QuestHandInDialog.handInIdOf(item);
            if (id == null || registryDefinition(id) != null || DATAPACK_DEFINITIONS.containsKey(id)) continue;
            QuestItems.Definition def = QuestItems.resolve(id);
            DATAPACK_DEFINITIONS.put(id, def != null ? def : NONE);
            learned = true;
        }
        return learned;
    }

    /** プラグインの登録表 (読むだけで、どのスレッドからでもよい) にあるアイテム・弾・アタッチメントの種類 (QuestItems と同じ) */
    private static QuestItems.Definition registryDefinition(String id) {
        var attachment = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.getAttachment(id);
        if (attachment != null) return new QuestItems.Definition(id, "ATTACHMENT", attachment.displayName);
        var item = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.get(id);
        if (item != null) return new QuestItems.Definition(id, item.type, item.displayName);
        var ammo = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.getAmmo(id);
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
    private List<Need> collect(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return List.of();
        PlayerDataModule dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        if (data == null) return List.of();
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
        return result;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;
        List<Need> list = needs.get(user.getUUID());
        if (list == null || list.isEmpty()) return;

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

    /** 強調した表示のアイテム。納品に関係なければnull (書き換えない) */
    private static com.github.retrooper.packetevents.protocol.item.ItemStack highlight(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem, List<Need> list) {
        if (packetItem == null || packetItem.isEmpty()) return null;
        ItemStack item = SpigotConversionUtil.toBukkitItemStack(packetItem);
        String itemId = QuestHandInDialog.handInIdOf(item);
        QuestItems.Definition def = itemId != null ? definition(itemId) : null;
        if (def == null) return null;
        boolean fir = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;

        List<Component> lines = new ArrayList<>();
        boolean usable = false;
        for (Need need : list) {
            if (need.objective().matches(itemId, def.type(), fir)) {
                usable = true;
                lines.add(line("★ クエスト「" + need.questName() + "」で納品 (あと " + need.remaining() + ")", NamedTextColor.GOLD));
            } else if (!fir && need.objective().isRequireFIR() && need.objective().matches(itemId, def.type(), true)) {
                lines.add(line("クエスト「" + need.questName() + "」は FIR 品のみ (このアイテムは対象外)", NamedTextColor.DARK_GRAY));
            }
        }
        if (lines.isEmpty()) return null;

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(lines);
        meta.lore(lore);
        if (usable) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
