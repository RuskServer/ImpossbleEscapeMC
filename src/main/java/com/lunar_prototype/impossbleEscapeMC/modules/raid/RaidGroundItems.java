package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * レイドのマップのワールドに落ちているアイテムを、そのレイドの間だけ残す。
 * <ul>
 *   <li>レイド中に落ちた (落とした・倒れて落ちた) アイテムは、そのレイドが覚えておく</li>
 *   <li>レイドが終わったら、そのワールドの読み込まれているアイテムを消す ({@link #clear})</li>
 *   <li>チャンクが読み込まれた時、レイドのマップのワールドのアイテムで、今のレイドが覚えていない物は消す
 *       (終わった時に読み込まれていなかった所に落ちていた物と、前のレイドの残り)</li>
 * </ul>
 * レイドのマップに最初から置くアイテム (地面の戦利品) は無いので、落ちているアイテムはどれもレイド中に落ちた物
 */
final class RaidGroundItems implements Listener {

    private final RaidModule module;

    RaidGroundItems(RaidModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        track(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        track(event.getItemDrop());
    }

    private void track(Item item) {
        RaidInstance raid = module.getRaidInWorld(item.getWorld());
        if (raid != null) raid.trackGroundItem(item.getUniqueId());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!module.isRaidWorld(event.getWorld())) return;
        RaidInstance raid = module.getRaidInWorld(event.getWorld());
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item && (raid == null || !raid.isGroundItem(item.getUniqueId()))) item.remove();
        }
    }

    /** レイドが終わった時: そのワールドの読み込まれているアイテムを消す (読み込まれていない所の物は、読み込まれた時に消す) */
    static void clear(World world) {
        for (Item item : world.getEntitiesByClass(Item.class)) item.remove();
    }
}
