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
            return;
        }

        RaidInstance raid = plugin.getRaidModule().getActiveRaids().stream()
                .filter(r -> r.isParticipant(player.getUniqueId()))
                .findFirst().orElse(null);

        ItemStack current = player.getInventory().getItem(8);

        if (raid != null) {
            // すでにタクティカルマップを持っているなら更新しない
            if (current != null && current.getType() == Material.FILLED_MAP) {
                if (current.hasItemMeta() && current.getItemMeta().displayName() != null) {
                    String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(current.getItemMeta().displayName());
                    if (name.contains("タクティカルマップ")) return;
                }
            }
            // レイド中: 機能するマップを渡す
            ItemStack map = createRaidMap(player);
            player.getInventory().setItem(8, map);
        } else {
            // すでにロビー用アイテムを持っているなら更新しない
            if (current != null && current.getType() == Material.MAP) {
                if (current.hasItemMeta() && current.getItemMeta().displayName() != null) {
                    String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(current.getItemMeta().displayName());
                    if (name.contains("地図データ受信中")) return;
                }
            }
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
        view.setScale(MapView.Scale.CLOSEST); // 最も詳細 (1:1)
        
        // 既存のレンダラーは風景描画のために残す（trackingPosition=false により他プレイヤーは非表示）
        // ただし、もし同じレンダラーが既に登録されている場合は重複しないようにする
        boolean hasRenderer = false;
        for (org.bukkit.map.MapRenderer r : view.getRenderers()) {
            if (r instanceof RaidMapRenderer) {
                hasRenderer = true;
                break;
            }
        }
        
        if (!hasRenderer) {
            view.addRenderer(renderer);
        }

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
