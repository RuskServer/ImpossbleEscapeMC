package com.lunar_prototype.impossbleEscapeMC.map;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RaidMapRenderer extends MapRenderer {

    private final ImpossbleEscapeMC plugin;

    public RaidMapRenderer(ImpossbleEscapeMC plugin) {
        super(true); // contextual = true (player specific)
        this.plugin = plugin;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        MapCursorCollection cursors = canvas.getCursors();
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0));
        }

        RaidInstance raid = plugin.getRaidModule().getActiveRaids().stream()
                .filter(r -> r.isParticipant(player.getUniqueId()))
                .findFirst().orElse(null);

        if (raid == null) return;

        // 1. 自分の位置を描画 (Green pointer)
        // バニラの MapView が tracking している場合、自動で追加されることもあるが、
        // ここでは明示的に追加して制御する
        addPlayerCursor(cursors, map, player);

        // 2. 脱出地点を描画 (Red flags / Portals)
        drawExtractions(cursors, map, player, raid);
    }

    private void addPlayerCursor(MapCursorCollection cursors, MapView map, Player player) {
        int x = clampCoordinate(calculateMapX(map, player.getLocation().getX()));
        int z = clampCoordinate(calculateMapZ(map, player.getLocation().getZ()));
        
        // Direction (0-15)
        byte direction = (byte) (Math.round(player.getLocation().getYaw() * 16 / 360) & 0xF);
        
        cursors.addCursor(new MapCursor((byte) x, (byte) z, direction, MapCursor.Type.PLAYER, true));
    }

    private void drawExtractions(MapCursorCollection cursors, MapView map, Player player, RaidInstance raid) {
        // RaidInstance に直接アクセスして脱出地点を取得する口が必要（RaidInstanceを拡張する必要があるかもしれない）
        // 現状 RaidInstance は activeExtractions を private で持っているため、まずは RaidMap から取得する
        // ※ 本来は RaidInstance が持つ「そのプレイヤーに割り当てられた脱出地点」を表示すべき
        
        // とりあえずマップに登録されている全脱出地点を表示（後で RaidInstance 経由に修正検討）
        RaidMap raidMap = plugin.getRaidModule().getMaps().values().stream()
                .filter(m -> m.getWorldName().equals(player.getWorld().getName()))
                .findFirst().orElse(null);
        
        if (raidMap == null) return;

        for (RaidMap.ExtractionPoint ep : raidMap.getExtractionPoints()) {
            org.bukkit.Location loc = ep.getLocation(raidMap.getWorldName());
            if (loc == null) continue;

            int x = clampCoordinate(calculateMapX(map, loc.getX()));
            int z = clampCoordinate(calculateMapZ(map, loc.getZ()));
            
            // RED_X or MANSION or TARGET_POINT
            cursors.addCursor(new MapCursor((byte) x, (byte) z, (byte) 0, MapCursor.Type.BANNER_RED, true, ep.getName()));
        }
    }

    private int calculateMapX(MapView map, double worldX) {
        return (int) ((worldX - map.getCenterX()) / getScaleFactor(map.getScale()));
    }

    private int calculateMapZ(MapView map, double worldZ) {
        return (int) ((worldZ - map.getCenterZ()) / getScaleFactor(map.getScale()));
    }

    private int getScaleFactor(MapView.Scale scale) {
        return switch (scale) {
            case CLOSEST -> 1;
            case CLOSE -> 2;
            case NORMAL -> 4;
            case FAR -> 8;
            case FARTHEST -> 16;
        };
    }

    private int clampCoordinate(int coord) {
        return Math.max(-128, Math.min(127, coord));
    }
}
