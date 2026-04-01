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
import java.awt.Color;
import java.util.EnumMap;

public class RaidMapRenderer extends MapRenderer {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastUpdate = new HashMap<>();
    private final Map<UUID, String> lastLoc = new HashMap<>();
    private final Map<Material, Color> baseColorCache = new EnumMap<>(Material.class);

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
                byte color = getBlockColor(block);
                
                int px = (bx + 32) * 2;
                int pz = (bz + 32) * 2;
                
                canvas.setPixel(px, pz, color);
                canvas.setPixel(px + 1, pz, color);
                canvas.setPixel(px, pz + 1, color);
                canvas.setPixel(px + 1, pz + 1, color);
            }
        }
    }

    private byte getBlockColor(org.bukkit.block.Block block) {
        Material type = block.getType();
        Color base = getBaseColor(type);

        // 高度に応じて軽く明暗をつけて、地形の凹凸を見分けやすくする
        int y = block.getY();
        double brightness = clamp(0.78 + ((y - 64) * 0.004), 0.58, 1.16);
        if (type.name().contains("WATER")) {
            brightness *= 0.92;
        }

        int r = clamp((int) Math.round(base.getRed() * brightness), 0, 255);
        int g = clamp((int) Math.round(base.getGreen() * brightness), 0, 255);
        int b = clamp((int) Math.round(base.getBlue() * brightness), 0, 255);
        return MapPalette.matchColor(r, g, b);
    }

    private Color getBaseColor(Material type) {
        Color cached = baseColorCache.get(type);
        if (cached != null) return cached;

        String name = type.name();
        Color color;

        if (name.contains("WATER") || name.contains("KELP") || name.contains("SEAGRASS")) {
            color = new Color(52, 113, 205);
        } else if (name.contains("LAVA") || name.contains("MAGMA")) {
            color = new Color(255, 96, 24);
        } else if (name.contains("SNOW") || name.contains("ICE") || name.contains("PACKED_ICE")) {
            color = new Color(222, 235, 242);
        } else if (name.contains("SAND") || name.contains("SANDSTONE") || name.contains("END_STONE")) {
            color = new Color(222, 208, 149);
        } else if (name.contains("GRASS") || name.contains("MOSS") || name.contains("FERN")) {
            color = new Color(97, 153, 63);
        } else if (name.contains("LEAVES") || name.contains("VINE") || name.contains("AZALEA")) {
            color = new Color(72, 124, 54);
        } else if (name.contains("DIRT") || name.contains("PODZOL") || name.contains("MUD") || name.contains("PATH") || name.contains("FARMLAND")) {
            color = new Color(122, 86, 58);
        } else if (name.contains("STONE") || name.contains("COBBLE") || name.contains("DEEPSLATE") || name.contains("TUFF")
                || name.contains("BASALT") || name.contains("BLACKSTONE") || name.contains("ORE")) {
            color = new Color(112, 112, 112);
        } else if (name.contains("GRAVEL") || name.contains("CLAY") || name.contains("ANDESITE") || name.contains("DIORITE")) {
            color = new Color(150, 150, 150);
        } else if (name.contains("LOG") || name.contains("PLANKS") || name.contains("WOOD") || name.contains("BAMBOO_BLOCK") || name.contains("STRIPPED_")) {
            color = new Color(134, 102, 67);
        } else if (name.contains("BRICK") || name.contains("NETHER_BRICK")) {
            color = new Color(143, 68, 56);
        } else if (name.contains("NETHERRACK") || name.contains("CRIMSON")) {
            color = new Color(124, 38, 38);
        } else if (name.contains("WARPED")) {
            color = new Color(52, 122, 116);
        } else {
            Color dyeLike = resolveDyeColor(name);
            color = dyeLike != null ? dyeLike : new Color(138, 154, 166);
        }

        baseColorCache.put(type, color);
        return color;
    }

    private Color resolveDyeColor(String materialName) {
        if (materialName.startsWith("WHITE_")) return new Color(235, 235, 235);
        if (materialName.startsWith("LIGHT_GRAY_")) return new Color(170, 170, 170);
        if (materialName.startsWith("GRAY_")) return new Color(110, 110, 110);
        if (materialName.startsWith("BLACK_")) return new Color(42, 42, 42);
        if (materialName.startsWith("BROWN_")) return new Color(116, 80, 52);
        if (materialName.startsWith("RED_")) return new Color(172, 45, 45);
        if (materialName.startsWith("ORANGE_")) return new Color(218, 118, 37);
        if (materialName.startsWith("YELLOW_")) return new Color(222, 196, 56);
        if (materialName.startsWith("LIME_")) return new Color(110, 185, 53);
        if (materialName.startsWith("GREEN_")) return new Color(78, 126, 48);
        if (materialName.startsWith("CYAN_")) return new Color(56, 132, 136);
        if (materialName.startsWith("LIGHT_BLUE_")) return new Color(96, 164, 210);
        if (materialName.startsWith("BLUE_")) return new Color(52, 78, 176);
        if (materialName.startsWith("PURPLE_")) return new Color(119, 74, 155);
        if (materialName.startsWith("MAGENTA_")) return new Color(182, 74, 166);
        if (materialName.startsWith("PINK_")) return new Color(214, 132, 153);
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
