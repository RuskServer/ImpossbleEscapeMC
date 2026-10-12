package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.listener.CraftingGridButtons;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
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
 * 持ち物の容量のゲージ。インベントリのクラフトグリッドの左下 ({@link #SLOT}) に、使っているマスの割合を見せる。
 * <ul>
 *   <li>見た目はリソースパックの iem:capacity_gauge (IEM_shader/tools/capacity_icons.py)。
 *       custom_model_data の floats[0] が目盛り (0〜{@link #LEVELS})、flags[0] が赤く光らせるか</li>
 *   <li>名前に「容量 使用 / 容量」、説明に 2 マス以上の物の内訳と、容量の内訳 (ホットバー + リグ)</li>
 *   <li>容量が足りずに止められた時は少しの間赤く光らせる ({@link #flash})</li>
 *   <li>クリックすると内訳をチャットに出す</li>
 * </ul>
 */
public final class CapacityGauge {

    /** クラフトグリッドの左下 (1: PDA、2: バックパック、4: アタッチメント) */
    public static final int SLOT = 3;
    /** ゲージの目盛りの数 (リソースパックの絵の数 - 1 と同じ) */
    public static final int LEVELS = 12;
    private static final int FLASH_TICKS = 20;
    /** 説明に並べる大きい物の数 */
    private static final int LORE_ITEMS = 6;
    private static final NamespacedKey MODEL = new NamespacedKey("iem", "capacity_gauge");

    /** 赤く光らせる最後の tick */
    private static final Map<UUID, Integer> flashUntil = new ConcurrentHashMap<>();

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

        NamedTextColor color = used > capacity ? NamedTextColor.RED : level(used, capacity) >= LEVELS - 2 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        meta.displayName(text("容量 " + used + " / " + capacity + " マス", color));
        List<Component> lore = new ArrayList<>();
        lore.add(used > capacity
                ? text(used - capacity + " マス超えています", NamedTextColor.RED)
                : text("空き " + (capacity - used) + " マス", NamedTextColor.GRAY));
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
        player.sendMessage(text("容量 " + used + " / " + capacity + " マス (" + capacityBreakdown(player, capacity) + ")",
                used > capacity ? NamedTextColor.RED : NamedTextColor.GREEN));
        int small = 0;
        for (int slot = 0; slot <= RigModule.MAIN_INVENTORY_END; slot++) {
            if (slot == RaidMapManager.MAP_SLOT || !CostSlotManager.isStorageSlot(player, inventory, slot)) continue;
            if (CostSlotManager.stackCost(inventory.getItem(slot)) == 1) small++;
        }
        for (Big item : bigItems(player)) {
            player.sendMessage(text(" " + item.name() + "  " + item.cost() + " マス", NamedTextColor.GRAY));
        }
        if (small > 0) player.sendMessage(text(" 1 マスの物  " + small + " 個", NamedTextColor.GRAY));
        player.sendMessage(text("大きい物は近くの空きマスも使います (斜線のマス。ホットバーの物の分は右下)", NamedTextColor.DARK_GRAY));
    }

    private static Component text(String content, NamedTextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }
}
