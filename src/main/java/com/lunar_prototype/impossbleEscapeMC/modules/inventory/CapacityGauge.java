package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.listener.CraftingGridButtons;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持ち物の容量のゲージ。インベントリのクラフトの出力の位置 ({@link #SLOT}) に、使っているマスの割合を見せる。
 * <ul>
 *   <li>見た目はリソースパックの iem:capacity_gauge (IEM_shader/tools/capacity_icons.py)。
 *       custom_model_data の floats[0] が目盛り (0〜{@link #LEVELS})、flags[0] が赤く光らせるか</li>
 *   <li>名前に「容量 使用 / 容量」、説明に 2 マス以上の物の内訳と、容量の内訳 (ホットバー + リグ)</li>
 *   <li>容量が足りずに止められた時は少しの間赤く光らせる ({@link #flash})</li>
 *   <li>クリックすると内訳をチャットに出す</li>
 * </ul>
 */
public final class CapacityGauge {

    /** クラフトの出力 (0)。グリッドは 1: PDA、2: アタッチメント、3・4: バックパック (横長の 1 つのボタン) */
    public static final int SLOT = 0;
    /** ゲージの目盛りの数 (リソースパックの絵の数 - 1 と同じ) */
    public static final int LEVELS = 12;
    private static final int FLASH_TICKS = 20;
    /** 説明に並べる大きい物の数 */
    private static final int LORE_ITEMS = 6;
    private static final NamespacedKey MODEL = new NamespacedKey("iem", "capacity_gauge");

    /** 赤く光らせる最後の tick */
    private static final Map<UUID, Integer> flashUntil = new ConcurrentHashMap<>();
    /** ロビーで通知済みの容量超過数。変化時だけ action bar を出す */
    private static final Map<UUID, Integer> lobbyOverages = new ConcurrentHashMap<>();

    private CapacityGauge() {
    }

    public static void init() {
        CraftingGridButtons.get().register(new CraftingGridButtons.Button(
                SLOT, new ItemStack(Material.PAPER), CapacityGauge::isShown, CapacityGauge::printBreakdown, CapacityGauge::icon));
    }

    /** 容量が足りずに止められた: 音を鳴らし、ゲージを少しの間赤くする */
    public static void flash(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT_FAIL, SoundCategory.PLAYERS, 0.8f, 1.0f);
        flashUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + FLASH_TICKS);
        CraftingGridButtons.get().refreshNow(player);
        Bukkit.getScheduler().runTaskLater(ImpossbleEscapeMC.getInstance(), () -> CraftingGridButtons.get().refreshNow(player), FLASH_TICKS + 1L);
    }

    /** ロビーの容量超過が変化した時だけ、出撃準備の状態を action bar で知らせる。 */
    public static void refreshLobbyStatus(Player player) {
        if (!isShown(player)) {
            lobbyOverages.remove(player.getUniqueId());
            return;
        }
        RaidModule raid = ImpossbleEscapeMC.getInstance().getRaidModule();
        if (raid != null && raid.isInRaid(player)) {
            lobbyOverages.remove(player.getUniqueId());
            return;
        }
        int capacity = CostSlotManager.capacity(player, player.getInventory());
        int over = Math.max(0, CostSlotManager.usedCost(player, player.getInventory()) - capacity);
        Integer previous = lobbyOverages.put(player.getUniqueId(), over);
        if (previous != null && previous == over) return;
        if (over > 0) {
            Messages.actionBar(player, "iem.inventory.capacitygauge.refreshlobbystatus.notice_001", MessageText.of("iem.inventory.capacitygauge.refreshlobbystatus.notice_001", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.refreshlobbystatus.support_001", over), NamedTextColor.RED))));
        } else if (previous != null && previous > 0) {
            Messages.actionBar(player, "iem.inventory.capacitygauge.refreshlobbystatus.notice_002", MessageText.of("iem.inventory.capacitygauge.refreshlobbystatus.notice_002", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.refreshlobbystatus.support_002"), NamedTextColor.GREEN))));
        }
    }

    /** 0〜LEVELS の目盛り。少しでも使っていれば 1 以上、満杯の時だけ LEVELS */
    static int level(int used, int capacity) {
        if (capacity <= 0 || used <= 0) return 0;
        if (used >= capacity) return LEVELS;
        return Math.max(1, Math.min(LEVELS - 1, used * LEVELS / capacity));
    }

    private static boolean isShown(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return false;
        if (DatapackGunnerManager.isGunner(player)) return false;
        return CostSlotManager.capacity(player, player.getInventory()) > 0;
    }

    /** 2 マス以上使う物 (大きい順) */
    private record Big(String name, int cost) {
    }

    private static ItemStack icon(Player player) {
        PlayerInventory inventory = player.getInventory();
        int capacity = CostSlotManager.capacity(player, inventory);
        int used = CostSlotManager.usedCost(player, inventory);
        Integer until = flashUntil.get(player.getUniqueId());
        boolean flashing = until != null && Bukkit.getCurrentTick() <= until;
        if (!flashing && until != null) flashUntil.remove(player.getUniqueId());

        ItemStack icon = new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        meta.setItemModel(MODEL);
        CustomModelDataComponent model = meta.getCustomModelDataComponent();
        model.setFloats(List.of((float) level(used, capacity)));
        model.setFlags(List.of(flashing || used > capacity));
        meta.setCustomModelDataComponent(model);
        meta.setMaxStackSize(1);

        int over = Math.max(0, used - capacity);
        boolean preparing = isPreparing(player);
        NamedTextColor color = over > 0 ? NamedTextColor.RED : level(used, capacity) >= LEVELS - 2 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        meta.displayName(text(over > 0 && preparing
                ? "出撃不可: " + used + " / " + capacity + " マス"
                : (preparing ? "出撃容量 " : "容量 ") + used + " / " + capacity + " マス", color));
        List<Component> lore = new ArrayList<>();
        if (over > 0 && preparing) {
            lore.add(text(over + " マス超過しています", NamedTextColor.RED));
            lore.add(text("ロビーでは装備を追加・整理できます", NamedTextColor.GRAY));
            lore.add(text("超過を解消すると出撃できます", NamedTextColor.YELLOW));
        } else {
            lore.add(over > 0
                    ? text(over + " マス超えています", NamedTextColor.RED)
                    : text("空き " + (capacity - used) + " マス", NamedTextColor.GRAY));
        }
        List<Big> big = bigItems(player);
        if (!big.isEmpty()) {
            lore.add(Component.empty());
            lore.add(text("大きい物", NamedTextColor.DARK_GRAY));
            for (int i = 0; i < Math.min(LORE_ITEMS, big.size()); i++) {
                lore.add(text(" " + big.get(i).name() + "  " + big.get(i).cost() + " マス", NamedTextColor.GRAY));
            }
            if (big.size() > LORE_ITEMS) lore.add(text(" ほか " + (big.size() - LORE_ITEMS) + " 個", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(text(capacityBreakdown(player, capacity), NamedTextColor.DARK_GRAY));
        lore.add(text("クリックで内訳をチャットに表示", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /** 容量の内訳 (ホットバー + リグ) */
    private static String capacityBreakdown(Player player, int capacity) {
        RigModule rigModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
        int rig = rigModule != null ? rigModule.getEnabledMainInventorySlots(player) : 0;
        int hotbar = capacity - rig;
        if (rig <= 0) return "ホットバー " + hotbar + " マス (リグ無し)";
        String rigName = PlainTextComponentSerializer.plainText().serialize(rigModule.getEquippedRig(player).effectiveName());
        return "ホットバー " + hotbar + " + " + rigName + " " + rig + " マス";
    }

    /** 通知用の内訳。GUIの既存表示とは分離して受信者ごとに翻訳する。 */
    private static Component capacityBreakdownText(Player player, int capacity) {
        RigModule rigModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
        int rig = rigModule != null ? rigModule.getEnabledMainInventorySlots(player) : 0;
        int hotbar = capacity - rig;
        if (rig <= 0) return MessageText.of("iem.inventory.capacitygauge.hotbar_only", hotbar);
        return MessageText.of("iem.inventory.capacitygauge.with_rig", hotbar, rigModule.getEquippedRig(player).effectiveName(), rig);
    }

    private static List<Big> bigItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Big> big = new ArrayList<>();
        for (int slot = 0; slot <= RigModule.MAIN_INVENTORY_END; slot++) {
            if (!CostSlotManager.isStorageSlot(player, inventory, slot)) continue;
            ItemStack item = inventory.getItem(slot);
            int cost = CostSlotManager.stackCost(item);
            if (cost >= 2) big.add(new Big(PlainTextComponentSerializer.plainText().serialize(item.effectiveName()), cost));
        }
        big.sort((a, b) -> Integer.compare(b.cost(), a.cost()));
        return big;
    }

    /** クリック: 内訳をチャットに出す */
    private static void printBreakdown(Player player) {
        PlayerInventory inventory = player.getInventory();
        int capacity = CostSlotManager.capacity(player, inventory);
        int used = CostSlotManager.usedCost(player, inventory);
        int over = Math.max(0, used - capacity);
        boolean preparing = isPreparing(player);
        Messages.chat(player, "iem.inventory.capacitygauge.printbreakdown.notice_001", MessageText.of("iem.inventory.capacitygauge.printbreakdown.notice_001", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.printbreakdown.support_001", (preparing ? MessageText.of("iem.inventory.capacitygauge.label.1") : MessageText.of("iem.inventory.capacitygauge.label.2")), used, capacity, capacityBreakdownText(player, capacity)),
                used > capacity ? NamedTextColor.RED : NamedTextColor.GREEN))));
        if (over > 0 && preparing) {
            Messages.chat(player, "iem.inventory.capacitygauge.printbreakdown.notice_002", MessageText.of("iem.inventory.capacitygauge.printbreakdown.notice_002", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.printbreakdown.support_002", over), NamedTextColor.RED))));
        }
        int small = 0;
        for (int slot = 0; slot <= RigModule.MAIN_INVENTORY_END; slot++) {
            if (slot == RaidMapManager.MAP_SLOT || !CostSlotManager.isStorageSlot(player, inventory, slot)) continue;
            if (CostSlotManager.stackCost(inventory.getItem(slot)) == 1) small++;
        }
        for (Big item : bigItems(player)) {
            Messages.chat(player, "iem.inventory.capacitygauge.printbreakdown.notice_003", MessageText.of("iem.inventory.capacitygauge.printbreakdown.notice_003", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.printbreakdown.support_003", item.name(), item.cost()), NamedTextColor.GRAY))));
        }
        if (small > 0) Messages.chat(player, "iem.inventory.capacitygauge.printbreakdown.notice_004", MessageText.of("iem.inventory.capacitygauge.printbreakdown.notice_004", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.printbreakdown.support_004", small), NamedTextColor.GRAY))));
        Messages.chat(player, "iem.inventory.capacitygauge.printbreakdown.notice_005", MessageText.of("iem.inventory.capacitygauge.printbreakdown.notice_005", MessageText.payload(text(MessageText.of("iem.inventory.capacitygauge.printbreakdown.support_005"), NamedTextColor.DARK_GRAY))));
    }

    private static Component text(String content, NamedTextColor color) {
        return text(Component.text(content), color);
    }

    private static Component text(Component content, NamedTextColor color) {
        return content.color(color).decoration(TextDecoration.ITALIC, false);
    }

    private static boolean isPreparing(Player player) {
        RaidModule raid = ImpossbleEscapeMC.getInstance().getRaidModule();
        return raid == null || !raid.isInRaid(player);
    }
}
