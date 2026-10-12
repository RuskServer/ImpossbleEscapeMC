package com.lunar_prototype.impossbleEscapeMC.modules.supply;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 支給装備: 装備を失ってもレイドに出られるよう、無料で受け取れる最低限の装備 (SCAV に勝てる程度)。
 * <ul>
 *   <li>中身: 選んだ武器 (弾を込めた状態) と予備の弾 {@link #SPARE_MAGAZINES} マガジン分 (貫通クラス {@link #AMMO_CLASS})、
 *       耐久の減ったクラス2のヘルメット、スリングバッグ、マイクロリグ、医療品</li>
 *   <li>持ち物が空の時だけ受け取れる。次に受け取れるのは {@link #COOLDOWN_MILLIS} 後</li>
 *   <li>中身はすべて支給品の印 ({@link PDCKeys#SUPPLIED}) 付き。売れない・マーケットに出せない・クエストに納品できない・
 *       FIR にならない。スタッシュには入れられない (閉じた時に持ち物へ戻す)</li>
 *   <li>生還・MIA・離脱でロビーに戻る時に回収する ({@link #reclaim})。支給品のバッグの中身や、
 *       支給品の銃に付けたアタッチメントのうち、支給品でない物は持ち主に戻す</li>
 * </ul>
 */
public final class SupplyLoadout {

    /** 選べる武器 (データパック銃のID) */
    public enum Weapon {
        SHOTGUN("mossberg_590", "散弾銃", "近距離で手足に当てやすい"),
        SMG("mp5", "短機関銃", "連射で手足を狙える"),
        PISTOL("usp45", "拳銃", "軽く、予備の弾が多い");

        public final String gunId;
        public final String label;
        public final String description;

        Weapon(String gunId, String label, String description) {
            this.gunId = gunId;
            this.label = label;
            this.description = description;
        }
    }

    public static final long COOLDOWN_MILLIS = 15 * 60 * 1000L;
    /** 弾の貫通クラス (SCAV の防具のクラス2 なら 70% で抜ける) */
    static final int AMMO_CLASS = 2;
    static final int SPARE_MAGAZINES = 2;
    /** 拳銃は弾が少ないので、予備を多めにする */
    static final int PISTOL_SPARE_MAGAZINES = 4;
    static final String HELMET = "UNTAR";
    static final double HELMET_DURABILITY_RATIO = 0.45;
    static final String BAG = "tactical_sling_bag";
    static final String RIG = "micro_rig";
    static final String MED = "ai2";
    /** 医療品 (AI-2) の耐久。回復量は 耐久 / 2 × 2 */
    static final int MED_DURABILITY = 20;
    static final String TOURNIQUET = "CAT";

    private SupplyLoadout() {
    }

    // --- 支給品の印 ---

    public static boolean isSupplied(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte) 0) == 1;
    }

    /** 支給品の印を付ける (説明にも出す)。データパックの銃は説明をプラグインが作らないので、ここで足す */
    static ItemStack mark(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PDCKeys.SUPPLIED, PDCKeys.BOOLEAN, (byte) 1);
        item.setItemMeta(meta);
        ItemStack result = ItemFactory.updateLore(item);
        ItemMeta resultMeta = result.getItemMeta();
        List<Component> lore = resultMeta.hasLore() ? new ArrayList<>(resultMeta.lore()) : new ArrayList<>();
        Component line = Component.text(ItemFactory.SUPPLIED_LORE_LINE).decoration(TextDecoration.ITALIC, false);
        boolean present = lore.stream().anyMatch(c -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(c).contains("支給品"));
        if (!present) {
            lore.add(line);
            resultMeta.lore(lore);
            result.setItemMeta(resultMeta);
        }
        return result;
    }

    // --- 受け取り ---

    /** 次に受け取れるまでのミリ秒 (受け取れるなら 0) */
    public static long remainingCooldown(PlayerData data) {
        if (data == null || data.getLastSupplyTime() <= 0) return 0;
        return Math.max(0, data.getLastSupplyTime() + COOLDOWN_MILLIS - System.currentTimeMillis());
    }

    /** 受け取れない理由。受け取れるなら null */
    public static String blockingReason(Player player, PlayerData data) {
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getRaidModule();
        if (raidModule != null && raidModule.isInRaid(player)) return "レイド中は受け取れません";
        if (data == null) return "プレイヤーデータを読み込み中です";
        long remaining = remainingCooldown(data);
        if (remaining > 0) return "次に受け取れるまで " + formatDuration(remaining);
        String blocking = blockingItem(player);
        if (blocking != null) return "持ち物 (装備を含む) を空にしてから受け取ってください: " + blocking;
        return null;
    }

    public static String formatDuration(long millis) {
        long seconds = (millis + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * 受け取りの邪魔になっている持ち物 (持ち物・ホットバー・防具・オフハンド) を「欄の 名前」で返す。無ければ null。
     * 地図 (地図の欄の地図は名前に関係なく)・占有スロット・リグで塞がれたスロットは数えない。
     * 鞍・体の欄は見ない (銃のデータパックが疲労の仕組みに見えない鞍を入れるため)。
     * 地図を手に持っている間は、本人の画面ではオフハンドが空に見える (MapOffhandHider) ので、その時はそう書き添える
     */
    static String blockingItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            // 地図の欄の地図は、名前に関係なく数えない (古い版で作られた地図など、名前で見分けられないことがある)
            if (slot == RaidMapManager.MAP_SLOT && item != null && (item.getType() == Material.MAP || item.getType() == Material.FILLED_MAP)) continue;
            if (blocks(item)) return (slot < 9 ? "ホットバー " + (slot + 1) : "持ち物") + "の " + itemName(item);
        }
        String[] armorSlots = {"足", "脚", "胴", "頭"};
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length && i < armorSlots.length; i++) {
            if (blocks(armor[i])) return armorSlots[i] + "の " + itemName(armor[i]);
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (blocks(offhand)) {
            String found = "オフハンドの " + itemName(offhand);
            if (RaidMapManager.isTacticalMapItem(inventory.getItemInMainHand())) {
                found += " (地図を持っている間はオフハンドが表示されません)";
            }
            return found;
        }
        return null;
    }

    /** 受け取りの邪魔になる物か (空・地図・占有スロット・リグで塞がれたスロットは邪魔にならない) */
    private static boolean blocks(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return !RaidMapManager.isTacticalMapItem(item) && !ItemFactory.isCostSlotPlaceholder(item)
                && !RigModule.isLockedSlotPlaceholder(item);
    }

    private static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return item.getType().name().toLowerCase();
    }

    /** 武器の銃に込める弾: その口径の貫通クラス AMMO_CLASS の弾 (無ければ一番弱い弾)。口径が分からなければ null */
    static AmmoDefinition ammoFor(Weapon weapon) {
        String caliber = DatapackAmmo.caliberOf(weapon.gunId);
        if (caliber == null) return null;
        AmmoDefinition chosen = null;
        for (String id : ItemRegistry.getAmmoIds()) {
            AmmoDefinition ammo = ItemRegistry.getAmmo(id);
            if (ammo != null && ammo.caliber.equalsIgnoreCase(caliber) && ammo.ammoClass == AMMO_CLASS
                    && (chosen == null || ammo.damage < chosen.damage)) {
                chosen = ammo;
            }
        }
        return chosen != null ? chosen : ItemRegistry.getWeakestAmmoForCaliber(caliber);
    }

    /** 武器を選ぶ所に出す銃 (弾は込めない)。作れなければバリア */
    public static ItemStack gunIcon(World world, Weapon weapon) {
        ItemStack gun = DatapackGunCatalog.get(weapon.gunId) != null ? GameItems.create(world, weapon.gunId, null) : null;
        return gun != null ? gun : new ItemStack(Material.BARRIER);
    }

    /** 中身 (表示用と受け取り用で同じ)。並び: 銃, 弾 (1つ以上), ヘルメット, バッグ, リグ, 医療品, 止血帯 */
    public static List<ItemStack> contents(World world, Weapon weapon) {
        List<ItemStack> items = new ArrayList<>();
        DatapackGunProfile profile = DatapackGunCatalog.get(weapon.gunId);
        ItemStack gun = profile != null ? GameItems.create(world, weapon.gunId, null) : null;
        AmmoDefinition ammo = ammoFor(weapon);
        if (gun != null) {
            if (ammo != null) gun = DatapackAmmo.withMagazine(gun, profile.magazineSize(), ammo.id);
            items.add(mark(gun));
        }
        if (ammo != null && profile != null) {
            int magazines = weapon == Weapon.PISTOL ? PISTOL_SPARE_MAGAZINES : SPARE_MAGAZINES;
            int remaining = profile.magazineSize() * magazines;
            while (remaining > 0) {
                ItemStack stack = ItemFactory.create(ammo.id);
                if (stack == null) break;
                int amount = Math.min(remaining, stack.getMaxStackSize());
                stack.setAmount(amount);
                items.add(mark(stack));
                remaining -= amount;
            }
        }
        ItemStack helmet = ItemFactory.create(HELMET);
        if (helmet != null) items.add(mark(damaged(helmet, HELMET_DURABILITY_RATIO)));
        for (String id : new String[]{BAG, RIG}) {
            ItemStack item = ItemFactory.create(id);
            if (item != null) items.add(mark(item));
        }
        ItemStack med = ItemFactory.create(MED);
        if (med != null) items.add(mark(withDurability(med, MED_DURABILITY)));
        ItemStack tourniquet = ItemFactory.create(TOURNIQUET);
        if (tourniquet != null) items.add(mark(tourniquet));
        return items;
    }

    /**
     * 受け取る: ヘルメットは頭、リグは脚、バッグはオフハンド、銃・弾・医療品はホットバーへ。
     * 受け取れなければ理由を返す (受け取れたら null)
     */
    public static String give(Player player, PlayerData data, Weapon weapon) {
        String reason = blockingReason(player, data);
        if (reason != null) return reason;
        List<ItemStack> items = contents(player.getWorld(), weapon);
        if (items.isEmpty() || DatapackGunCatalog.gunIdOf(items.getFirst()) == null) return "支給装備を用意できませんでした";

        PlayerInventory inventory = player.getInventory();
        int hotbar = 0;
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : items) {
            String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            if (HELMET.equals(id)) inventory.setHelmet(item);
            else if (RIG.equals(id)) inventory.setLeggings(item);
            else if (BAG.equals(id)) inventory.setItemInOffHand(item);
            else if (hotbar < RaidMapManager.MAP_SLOT) inventory.setItem(hotbar++, item);
            else rest.add(item);
        }
        rest.forEach(item -> inventory.addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
        data.setLastSupplyTime(System.currentTimeMillis());
        return null;
    }

    private static ItemStack damaged(ItemStack armor, double ratio) {
        String id = armor.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(id);
        return def != null && def.maxDurability > 0 ? withDurability(armor, (int) Math.round(def.maxDurability * ratio)) : armor;
    }

    private static ItemStack withDurability(ItemStack item, int durability) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PDCKeys.DURABILITY, PDCKeys.INTEGER, durability);
        item.setItemMeta(meta);
        return item;
    }

    // --- 回収 ---

    /**
     * ロビーに戻る時に、持ち物 (装備・オフハンド・カーソル・バッグの中) から支給品を回収する。
     * 支給品のバッグの中身と支給品の銃に付けたアタッチメントのうち、支給品でない物は持ち主に戻す。
     *
     * @return 回収した支給品の数
     */
    public static int reclaim(Player player) {
        BackpackModule backpacks = ImpossbleEscapeMC.getInstance().getServiceContainer().get(BackpackModule.class);
        if (backpacks != null) backpacks.saveBackpackFromOpenInventory(player);
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> returned = new ArrayList<>();
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            if (isSupplied(item)) {
                returned.addAll(salvage(item, backpacks));
                inventory.setItem(i, null);
                removed++;
            } else if (backpacks != null && backpacks.isBackpackItem(item)) {
                int inside = stripBackpack(item, backpacks, returned);
                if (inside > 0) {
                    inventory.setItem(i, item);
                    removed += inside;
                }
            }
        }
        if (isSupplied(player.getItemOnCursor())) {
            returned.addAll(salvage(player.getItemOnCursor(), backpacks));
            player.setItemOnCursor(null);
            removed++;
        }
        give(player, returned);
        if (removed > 0) {
            player.sendMessage(Component.text("支給装備を回収しました (" + removed + " 点)", NamedTextColor.GRAY));
        }
        return removed;
    }

    /** スタッシュ (支給品を入れられない入れ物) から支給品を取り出して持ち主に戻す。取り出した数 */
    public static int ejectFrom(Inventory storage, Player owner) {
        BackpackModule backpacks = ImpossbleEscapeMC.getInstance().getServiceContainer().get(BackpackModule.class);
        List<ItemStack> moved = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < storage.getSize(); i++) {
            ItemStack item = storage.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            if (isSupplied(item)) {
                moved.add(item);
                storage.setItem(i, null);
                count++;
            } else if (backpacks != null && backpacks.isBackpackItem(item)) {
                List<ItemStack> inside = new ArrayList<>();
                int n = takeSuppliedFromBackpack(item, backpacks, inside);
                if (n > 0) {
                    storage.setItem(i, item);
                    moved.addAll(inside);
                    count += n;
                }
            }
        }
        give(owner, moved);
        if (count > 0) {
            owner.sendMessage(Component.text("支給品はスタッシュに入れられないため、持ち物に戻しました", NamedTextColor.RED));
        }
        return count;
    }

    /** 支給品を消す時に、持ち主に戻す物 (バッグの中の支給品でない物、銃に付けた支給品でないアタッチメント) */
    private static List<ItemStack> salvage(ItemStack item, BackpackModule backpacks) {
        List<ItemStack> result = new ArrayList<>();
        if (backpacks != null && backpacks.isBackpackItem(item)) {
            for (ItemStack content : backpacks.loadBackpackInventory(item).getContents()) {
                if (content == null || content.getType().isAir()) continue;
                if (isSupplied(content)) result.addAll(salvage(content, backpacks));
                else result.add(content);
            }
        }
        if (DatapackGunCatalog.gunIdOf(item) != null) {
            for (ItemStack attachment : DatapackAttachments.attached(item).values()) {
                if (attachment != null && !isSupplied(attachment)) result.add(attachment);
            }
        }
        return result;
    }

    /** 支給品でないバッグの中の支給品を消し、その中の戻す物を returned に足す。消した数 */
    private static int stripBackpack(ItemStack backpack, BackpackModule backpacks, List<ItemStack> returned) {
        Inventory inside = backpacks.loadBackpackInventory(backpack);
        int removed = 0;
        for (int i = 0; i < inside.getSize(); i++) {
            ItemStack content = inside.getItem(i);
            if (!isSupplied(content)) continue;
            returned.addAll(salvage(content, backpacks));
            inside.setItem(i, null);
            removed++;
        }
        if (removed > 0) backpacks.saveBackpackInventory(backpack, inside);
        return removed;
    }

    /** 支給品でないバッグの中の支給品を取り出して taken に足す。取り出した数 */
    private static int takeSuppliedFromBackpack(ItemStack backpack, BackpackModule backpacks, List<ItemStack> taken) {
        Inventory inside = backpacks.loadBackpackInventory(backpack);
        int count = 0;
        for (int i = 0; i < inside.getSize(); i++) {
            ItemStack content = inside.getItem(i);
            if (!isSupplied(content)) continue;
            taken.add(content);
            inside.setItem(i, null);
            count++;
        }
        if (count > 0) backpacks.saveBackpackInventory(backpack, inside);
        return count;
    }

    private static void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }
}
