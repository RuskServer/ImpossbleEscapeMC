package com.lunar_prototype.impossbleEscapeMC.map;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RaidMapRenderer extends MapRenderer {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastUpdate = new HashMap<>();
    private final Map<UUID, String> lastLoc = new HashMap<>();

    public RaidMapRenderer(ImpossbleEscapeMC plugin) {
        super(true); // contextual = true (player specific)
        this.plugin = plugin;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        // 現在のズーム設定をアイテムから取得
        int zoomIndex = getZoomIndex(player, map);

        // 地図の中心をプレイヤーに合わせる（リアルタイム更新）
        map.setCenterX(player.getLocation().getBlockX());
        map.setCenterZ(player.getLocation().getBlockZ());

        // SUPER_ZOOM (2:1) の場合は地形を自前で描画
        if (zoomIndex == 0) {
            updateSuperZoomTerrain(canvas, player);
        }

        MapCursorCollection cursors = canvas.getCursors();

        RaidInstance raid = plugin.getRaidModule().getActiveRaids().stream()
                .filter(r -> r.isParticipant(player.getUniqueId()))
                .findFirst().orElse(null);

        if (raid == null) return;

        // 描画ごとにクリアして、アイコンを再構築（Cursorsはオーバーレイなので）
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0));
        }

        // 1. 自分の位置を描画 (Green pointer)
        // 中心座標に合わせているので (0, 0)
        byte direction = (byte) (Math.round(player.getLocation().getYaw() * 16 / 360) & 0xF);
        cursors.addCursor(new MapCursor((byte) 0, (byte) 0, direction, MapCursor.Type.PLAYER, true));

        // 2. 脱出地点を描画 (Red flags / Portals)
        drawExtractions(cursors, map, player, raid, zoomIndex);
    }

    private int getZoomIndex(Player player, MapView map) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isTargetMap(item, map)) {
            return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.MAP_ZOOM, PDCKeys.INTEGER, 1);
        }
        item = player.getInventory().getItemInOffHand();
        if (isTargetMap(item, map)) {
            return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.MAP_ZOOM, PDCKeys.INTEGER, 1);
        }
        return 1;
    }

    private boolean isTargetMap(ItemStack item, MapView map) {
        if (item == null || item.getType() != Material.FILLED_MAP) return false;
        org.bukkit.inventory.meta.MapMeta meta = (org.bukkit.inventory.meta.MapMeta) item.getItemMeta();
        return meta.hasMapView() && meta.getMapView().getId() == map.getId();
    }

    private void updateSuperZoomTerrain(MapCanvas canvas, Player player) {
        long now = System.currentTimeMillis();
        String locKey = player.getWorld().getName() + ":" + player.getLocation().getBlockX() + ":" + player.getLocation().getBlockZ();
        
        // 100ms ごとか、座標が変わった時のみ更新
        if (now - lastUpdate.getOrDefault(player.getUniqueId(), 0L) < 100 && locKey.equals(lastLoc.get(player.getUniqueId()))) {
            return;
        }
        
        lastUpdate.put(player.getUniqueId(), now);
        lastLoc.put(player.getUniqueId(), locKey);

        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        org.bukkit.World world = player.getWorld();

        for (int bx = -32; bx < 32; bx++) {
            for (int bz = -32; bz < 32; bz++) {
                int wx = centerX + bx;
                int wz = centerZ + bz;
                // 高速化のため、ある程度の高さから探索開始
                org.bukkit.block.Block block = world.getHighestBlockAt(wx, wz);
                byte color = getBlockColor(block.getType());
                
                int px = (bx + 32) * 2;
                int pz = (bz + 32) * 2;
                
                canvas.setPixel(px, pz, color);
                canvas.setPixel(px + 1, pz, color);
                canvas.setPixel(px, pz + 1, color);
                canvas.setPixel(px + 1, pz + 1, color);
            }
        }
    }

    private byte getBlockColor(Material type) {
        String name = type.name();
        if (name.contains("GRASS")) return MapPalette.DARK_GREEN;
        if (name.contains("WATER")) return MapPalette.BLUE;
        if (name.contains("STONE") || name.contains("COBBLE")) return MapPalette.GRAY_2;
        if (name.contains("DIRT") || name.contains("PATH")) return MapPalette.BROWN;
        if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANKS")) return MapPalette.DARK_BROWN;
        if (name.contains("LEAVES")) return MapPalette.LIGHT_GREEN;
        if (name.contains("SAND")) return MapPalette.LIGHT_BROWN;
        if (name.contains("SNOW")) return MapPalette.WHITE;
        if (name.contains("ICE")) return MapPalette.PALE_BLUE;
        if (name.contains("IRON") || name.contains("METAL")) return MapPalette.GRAY_1;
        if (name.contains("BRICK")) return MapPalette.RED;
        return MapPalette.PALE_BLUE;
    }

    private void drawExtractions(MapCursorCollection cursors, MapView map, Player player, RaidInstance raid, int zoomIndex) {
        RaidMap raidMap = plugin.getRaidModule().getMaps().values().stream()
                .filter(m -> m.getWorldName().equals(player.getWorld().getName()))
                .findFirst().orElse(null);
        
        if (raidMap == null) return;

        for (RaidMap.ExtractionPoint ep : raidMap.getExtractionPoints()) {
            org.bukkit.Location loc = ep.getLocation(raidMap.getWorldName());
            if (loc == null) continue;

            int x = clampCoordinate(calculateMapX(map, loc.getX(), zoomIndex));
            int z = clampCoordinate(calculateMapZ(map, loc.getZ(), zoomIndex));
            
            cursors.addCursor(new MapCursor((byte) x, (byte) z, (byte) 0, MapCursor.Type.BANNER_RED, true, ep.getName()));
        }
    }

    private int calculateMapX(MapView map, double worldX, int zoomIndex) {
        return (int) (((worldX - map.getCenterX()) / getScaleFactor(zoomIndex)) * 2);
    }

    private int calculateMapZ(MapView map, double worldZ, int zoomIndex) {
        return (int) (((worldZ - map.getCenterZ()) / getScaleFactor(zoomIndex)) * 2);
    }

    private double getScaleFactor(int zoomIndex) {
        return switch (zoomIndex) {
            case 0 -> 0.5; // 2:1 (Super Zoom)
            case 1 -> 1.0; // 1:1
            case 2 -> 2.0; // 1:2
            case 3 -> 4.0; // 1:4
            case 4 -> 8.0; // 1:8
            case 5 -> 16.0; // 1:16
            default -> 1.0;
        };
    }

    private int clampCoordinate(int coord) {
        return Math.max(-128, Math.min(127, coord));
    }
}
