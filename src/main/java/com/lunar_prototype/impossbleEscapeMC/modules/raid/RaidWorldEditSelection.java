package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.entity.Player;

/** WorldEdit/FAWE がある場合だけ呼ぶ境界。ブロック選択の最大端も領域に含める。 */
final class RaidWorldEditSelection {
    static RaidRadioZone zone(Player player,String map,String id,String name) {
        var world=BukkitAdapter.adapt(player.getWorld());
        try {
        var selection=WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(world);
        if(!(selection instanceof CuboidRegion))throw new IllegalArgumentException("直方体の選択を使ってください（//sel cuboid）。");
        var min=selection.getMinimumPoint();var max=selection.getMaximumPoint();
        return new RaidRadioZone(id,map,name,min.x(),min.y(),min.z(),max.x()+1.0,max.y()+1.0,max.z()+1.0);
        } catch(com.sk89q.worldedit.IncompleteRegionException ex) {
            throw new IllegalArgumentException("//wand で2点を選択してください。",ex);
        }
    }
}
