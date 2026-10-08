package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

import com.lunar_prototype.impossbleEscapeMC.util.DatapackFunctionUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Toi's Armoryデータパックの銃データを読む。
 * 銃の一覧と性能はデータパックの storage toisarm:data gun[] (ガンパックのimportで登録される) が正となる。
 */
public final class DatapackGunCatalog {

    /** データパックの射撃モード値 (toisarm.fire_mode) のうちフルオート */
    private static final int FIRE_MODE_AUTO = 1;

    private DatapackGunCatalog() {
    }

    /** 銃IDに対応する銃データ。データパックが無い・その銃が無い場合はnull */
    public static DatapackGunProfile get(String gunId) {
        if (gunId == null) return null;
        return guns().compoundStream()
                .filter(gun -> gunId.equals(gun.getStringOr("id", "")))
                .findFirst()
                .map(DatapackGunCatalog::toProfile)
                .orElse(null);
    }

    /** データパックの銃なら銃ID、それ以外はnull (データパック銃は custom_data.toisarm.type が "gun") */
    public static String gunIdOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        CustomData customData = CraftItemStack.unwrap(item).get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag toisarm = customData.copyTag().getCompoundOrEmpty("toisarm");
        if (!"gun".equals(toisarm.getStringOr("type", ""))) return null;
        String id = toisarm.getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    /** データパックの銃付与functionで銃アイテムを生成する */
    public static ItemStack createItem(World world, DatapackGunProfile profile) {
        return DatapackFunctionUtil.generateGunItem(world, profile.id(), profile.displayName());
    }

    private static net.minecraft.nbt.ListTag guns() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return server.getCommandStorage().get(Identifier.fromNamespaceAndPath("toisarm", "data")).getListOrEmpty("gun");
    }

    private static DatapackGunProfile toProfile(CompoundTag gun) {
        CompoundTag state = gun.getCompoundOrEmpty("state");
        String id = gun.getStringOr("id", "");
        // modes[0] が銃を受け取った時の射撃モード (modes_index 0)
        int defaultMode = state.getListOrEmpty("modes").compoundStream()
                .findFirst()
                .map(mode -> mode.getIntOr("value", 0))
                .orElse(0);
        int reloadTicks = state.getIntOr("reload_time", 40);
        return new DatapackGunProfile(
                id,
                gun.getStringOr("display_name", id),
                state.getDoubleOr("round_per_minute", 600),
                defaultMode == FIRE_MODE_AUTO,
                state.getBooleanOr("manual_bolt_action", false),
                state.getIntOr("ammo_capacity", 30),
                reloadTicks,
                state.getIntOr("empty_reload_time", reloadTicks),
                state.getDoubleOr("max_range", 100));
    }
}
