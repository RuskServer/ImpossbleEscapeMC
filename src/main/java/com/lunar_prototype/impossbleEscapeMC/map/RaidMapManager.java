package com.lunar_prototype.impossbleEscapeMC.map;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

public class RaidMapManager {

    private final ImpossbleEscapeMC plugin;
    private final RaidMapRenderer renderer;

    public RaidMapManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.renderer = new RaidMapRenderer(plugin);
    }

    public void updateMapSlot(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            // クリエ等の場合はスロット8を保護しない（空にするわけではないが、管理対象外とする）
            return;
        }

        RaidInstance raid = plugin.getRaidModule().getActiveRaids().stream()
                .filter(r -> r.isParticipant(player.getUniqueId()))
                .findFirst().orElse(null);

        if (raid != null) {
            // レイド中: 機能するマップを渡す
            ItemStack map = createRaidMap(player);
            player.getInventory().setItem(8, map);
        } else {
            // ロビー: 待機中アイテムを渡す
            player.getInventory().setItem(8, createLobbyItem());
        }
    }

    private ItemStack createLobbyItem() {
        ItemStack item = new ItemStack(Material.MAP);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("[ 地図データ受信中... ]", NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("レイド出撃時に自動的に展開されます。", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRaidMap(Player player) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();

        MapView view = Bukkit.createMap(player.getWorld());
        view.setTrackingPosition(false); // バニラのトラッキング（他プレイヤー含む）を無効化
        view.setUnlimitedTracking(false);
        view.setScale(MapView.Scale.NORMAL);
        
        // 既存のレンダラーを削除
        for (org.bukkit.map.MapRenderer r : view.getRenderers()) {
            view.removeRenderer(r);
        }
        // 自作レンダラーを追加
        view.addRenderer(renderer);

        meta.setMapView(view);
        meta.displayName(Component.text("タクティカルマップ", NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMapSlotItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Component name = item.getItemMeta().displayName();
        if (name == null) return false;
        
        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return plainName.contains("地図データ受信中") || plainName.contains("タクティカルマップ");
    }
}
