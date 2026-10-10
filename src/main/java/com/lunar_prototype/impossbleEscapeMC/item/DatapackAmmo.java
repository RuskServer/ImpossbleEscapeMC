package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * データパック銃 (Toi's Armory) の弾。データパックには口径 (state.ammo_type) はあるが弾の種類が無いため、
 * プラグインの弾 (ammo/*.yml の AmmoDefinition) を口径で対応させる。
 * <ul>
 *   <li>銃に込めた弾の種類は custom_data.toisarm.state に書く (データパックは持ち替えの判定で state を見ないため、
 *       書いても持ち替え扱いにならない)</li>
 *   <li>弾数はマガジン (state.ammo_remaining) と薬室 (state.chamber が 1 なら1発)</li>
 * </ul>
 */
public final class DatapackAmmo {

    /** 込めた弾の種類 (custom_data.toisarm.state の中) */
    private static final String LOADED_AMMO_KEY = "iemc_loaded_ammo";
    /** 銃1丁ごとのID (同じ種類の銃を入れ替えた時と、同じ銃のマガジンを付け替えた時を見分ける) */
    private static final String INSTANCE_ID_KEY = "iemc_id";
    /** データパックとプラグインで書き方が違う口径 (大文字小文字の違いは同じとみなす) */
    private static final Map<String, String> CALIBER_ALIASES = Map.of("12ga", "12x70mm");
    /** 撃った弾がこのtick以内に当たった時、撃った時の弾の種類を使う */
    private static final int SHOT_MEMORY_TICKS = 200;

    private record Shot(String ammoId, int tick) {
    }

    private static final Map<String, String> caliberCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Shot> lastShots = new ConcurrentHashMap<>();

    private DatapackAmmo() {
    }

    /** データパック銃の口径を、プラグインの弾の書き方で返す。分からなければnull */
    public static String caliberOf(String gunId) {
        if (gunId == null) return null;
        String cached = caliberCache.get(gunId);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String raw = ((CraftServer) Bukkit.getServer()).getServer().getCommandStorage()
                .get(Identifier.fromNamespaceAndPath("toisarm", "data")).getListOrEmpty("gun").compoundStream()
                .filter(g -> gunId.equals(g.getStringOr("id", "")))
                .findFirst()
                .map(g -> g.getCompoundOrEmpty("state").getStringOr("ammo_type", ""))
                .orElse("");
        String caliber = raw.isEmpty() ? "" : CALIBER_ALIASES.getOrDefault(raw.toLowerCase(java.util.Locale.ROOT), raw);
        caliberCache.put(gunId, caliber);
        return caliber.isEmpty() ? null : caliber;
    }

    /** その口径の弾がプラグインに定義されているか (無い口径の銃は、今までどおり弾無しでリロードできる) */
    public static boolean hasAmmoFor(String caliber) {
        return caliber != null && ItemRegistry.getWeakestAmmoForCaliber(caliber) != null;
    }

    /** インベントリにある、その口径の弾 (弾の種類 → スタック) */
    public static Map<String, List<ItemStack>> findAmmo(Player player, String caliber) {
        Map<String, List<ItemStack>> pool = new LinkedHashMap<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            String id = ammoIdOf(item);
            if (id == null) continue;
            AmmoDefinition ammo = ItemRegistry.getAmmo(id);
            if (ammo != null && ammo.caliber.equalsIgnoreCase(caliber)) {
                pool.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(item);
            }
        }
        return pool;
    }

    /** 弾アイテムなら弾の種類 */
    public static String ammoIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        return id != null && ItemRegistry.getAmmo(id) != null ? id : null;
    }

    /** マガジンの弾数 */
    public static int magazine(ItemStack gun) {
        return state(gun).getIntOr("ammo_remaining", 0);
    }

    /** 薬室に弾があるか */
    public static boolean chambered(ItemStack gun) {
        return state(gun).getIntOr("chamber", 0) == 1;
    }

    /** 込めた弾の種類。まだ込めていなければnull */
    public static String loadedAmmoId(ItemStack gun) {
        String id = state(gun).getStringOr(LOADED_AMMO_KEY, "");
        return id.isEmpty() ? null : id;
    }

    /** マガジンの弾数と込めた弾の種類を書き換えた銃を返す (state の他の値には触らない) */
    public static ItemStack withMagazine(ItemStack gun, int magazine, String ammoId) {
        return editState(gun, state -> {
            state.putInt("ammo_remaining", magazine);
            if (ammoId != null) state.putString(LOADED_AMMO_KEY, ammoId);
        });
    }

    /** 銃1丁ごとのID。まだ無ければnull */
    public static String instanceId(ItemStack gun) {
        String id = state(gun).getStringOr(INSTANCE_ID_KEY, "");
        return id.isEmpty() ? null : id;
    }

    /** 銃1丁ごとのIDを付けた銃を返す */
    public static ItemStack withInstanceId(ItemStack gun) {
        String id = UUID.randomUUID().toString();
        return editState(gun, state -> state.putString(INSTANCE_ID_KEY, id));
    }

    private static ItemStack editState(ItemStack gun, java.util.function.Consumer<CompoundTag> edit) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(gun);
        CompoundTag root = nms.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag toisarm = root.getCompoundOrEmpty("toisarm");
        CompoundTag state = toisarm.getCompoundOrEmpty("state");
        edit.accept(state);
        toisarm.put("state", state);
        root.put("toisarm", toisarm);
        nms.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** プレイヤーが撃った時に呼ぶ。手に持った銃に込めた弾を、その弾が当たった時のために覚える */
    public static void recordShot(Player player) {
        ItemStack gun = player.getInventory().getItemInMainHand();
        String ammoId = loadedAmmoId(gun);
        if (ammoId == null) {
            lastShots.remove(player.getUniqueId());
        } else {
            lastShots.put(player.getUniqueId(), new Shot(ammoId, Bukkit.getCurrentTick()));
        }
    }

    /** そのプレイヤーが最近撃った弾。分からなければnull */
    public static AmmoDefinition lastShotAmmo(UUID player) {
        Shot shot = lastShots.get(player);
        if (shot == null || Bukkit.getCurrentTick() - shot.tick() > SHOT_MEMORY_TICKS) return null;
        return ItemRegistry.getAmmo(shot.ammoId());
    }

    /** 弾のダメージ倍率 (同じ口径のいちばん弱い弾に対する比。データパックの銃のダメージに掛ける) */
    public static double damageMultiplier(AmmoDefinition ammo) {
        if (ammo == null) return 1.0;
        AmmoDefinition base = ItemRegistry.getWeakestAmmoForCaliber(ammo.caliber);
        if (base == null || base.damage <= 0) return 1.0;
        return ammo.damage / base.damage;
    }

    public static void forget(UUID player) {
        lastShots.remove(player);
    }

    /** データパックが再読み込みされた時 (銃の口径が変わりうる) */
    public static void clearCache() {
        caliberCache.clear();
    }

    private static CompoundTag state(ItemStack gun) {
        if (gun == null || gun.getType().isAir()) return new CompoundTag();
        CustomData customData = CraftItemStack.unwrap(gun).get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag().getCompoundOrEmpty("toisarm").getCompoundOrEmpty("state");
    }

    static Map<String, Integer> countByType(Map<String, List<ItemStack>> pool) {
        Map<String, Integer> counts = new HashMap<>();
        pool.forEach((id, stacks) -> counts.put(id, stacks.stream().mapToInt(ItemStack::getAmount).sum()));
        return counts;
    }
}
