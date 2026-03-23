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
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;

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
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setScale(MapView.Scale.CLOSEST); // バニラ側の最小スケール
        
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
        
        // デフォルトは 1:1 (インデックス1)
        meta.getPersistentDataContainer().set(PDCKeys.MAP_ZOOM, PDCKeys.INTEGER, 1);
        meta.lore(createMapLore(1));
        
        item.setItemMeta(meta);
        return item;
    }

    public void toggleZoom(Player player, ItemStack item) {
        if (item.getType() != Material.FILLED_MAP) return;
        MapMeta meta = (MapMeta) item.getItemMeta();
        MapView view = meta.getMapView();
        if (view == null) return;

        int currentZoom = meta.getPersistentDataContainer().getOrDefault(PDCKeys.MAP_ZOOM, PDCKeys.INTEGER, 1);
        int nextZoom = (currentZoom + 1) % 6;
        
        meta.getPersistentDataContainer().set(PDCKeys.MAP_ZOOM, PDCKeys.INTEGER, nextZoom);
        
        // バニラのスケールも同期させる (SUPER_ZOOM の時は CLOSEST にしておく)
        MapView.Scale vanillaScale = getVanillaScale(nextZoom);
        view.setScale(vanillaScale);
        
        meta.lore(createMapLore(nextZoom));
        item.setItemMeta(meta);
        
        player.sendMessage(Component.text("マップ倍率を " + getScaleName(nextZoom) + " に変更しました。", NamedTextColor.GREEN));
    }

    private MapView.Scale getVanillaScale(int zoomIndex) {
        return switch (zoomIndex) {
            case 0, 1 -> MapView.Scale.CLOSEST;
            case 2 -> MapView.Scale.CLOSE;
            case 3 -> MapView.Scale.NORMAL;
            case 4 -> MapView.Scale.FAR;
            case 5 -> MapView.Scale.FARTHEST;
            default -> MapView.Scale.CLOSEST;
        };
    }

    private List<Component> createMapLore(int zoomIndex) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("現在の詳細度: ", NamedTextColor.GRAY)
                .append(Component.text(getScaleName(zoomIndex), NamedTextColor.AQUA)));
        lore.add(Component.text("右クリックでズーム倍率を変更", NamedTextColor.DARK_GRAY));
        return lore;
    }

    private String getScaleName(int zoomIndex) {
        return switch (zoomIndex) {
            case 0 -> "2:1 (最大拡大)";
            case 1 -> "1:1 (詳細)";
            case 2 -> "1:2";
            case 3 -> "1:4";
            case 4 -> "1:8";
            case 5 -> "1:16 (広域)";
            default -> "不明";
        };
    }

    public boolean isMapSlotItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Component name = item.getItemMeta().displayName();
        if (name == null) return false;
        
        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return plainName.contains("地図データ受信中") || plainName.contains("タクティカルマップ");
    }
}
