package com.lunar_prototype.impossbleEscapeMC.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Toi's Armory データパックの作業用エンティティ (item_display {@code 0-0-0-0-1ae1e}、タグ {@link #TAG}) を守る。
 * <p>
 * データパックはこれをオーバーワールドの 0,0,0 に置く前提だが、銃を持つプレイヤーごとに毎 tick
 * {@code tp 0-0-0-0-1ae1e @s} → {@code tp 0-0-0-0-1ae1e 0.0 0.0 0.0} をプレイヤーのいるワールドで行うため、レイドのワールドへ移る。
 * そこで 0,0 のチャンクが外れると見失われてチャンクに保存され、データパックがオーバーワールドに出し直すので、同じ UUID の物が
 * いくつものワールドに残り、UUID で古い物を掴むと銃のアイテムの預け先を取り違える。データパックには手を入れず、次の 3 つで防ぐ。
 * <ul>
 *   <li>保存しない: 作業用エンティティがワールドに入るたびに persistent を外す。チャンクが外れたら消え、データパックが出し直す</li>
 *   <li>見失わない: すべてのワールド (後から読み込むレイドのワールドも) の 0,0 のチャンクを読み込んだままにする</li>
 *   <li>古い物を消す: チャンクから読み込まれた物 (この対策の前に保存された物) と、UUID が重なって Paper が UUID を振り直した物を消す</li>
 * </ul>
 */
public final class DatapackWorkEntityGuard implements Listener {

    /** データパックが UUID:[I;0,0,0,110110] で出す作業用エンティティ */
    static final UUID WORK_UUID = new UUID(0L, 110110L);
    static final String TAG = "toisarm.util_display";

    private static DatapackWorkEntityGuard instance;

    private final JavaPlugin plugin;

    private DatapackWorkEntityGuard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(JavaPlugin plugin) {
        if (instance != null) return;
        instance = new DatapackWorkEntityGuard(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        // 起動時に読み込まれているワールド (ワールドの読み込みが終わってから)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (instance == null) return;
            for (World world : Bukkit.getWorlds()) instance.keepLoaded(world);
            instance.sweep();
        });
    }

    public static void shutdown() {
        if (instance == null) return;
        HandlerList.unregisterAll(instance);
        for (World world : Bukkit.getWorlds()) world.removePluginChunkTicket(0, 0, instance.plugin);
        instance = null;
    }

    static boolean isWorkEntity(Entity entity) {
        return entity instanceof ItemDisplay && entity.getScoreboardTags().contains(TAG);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        keepLoaded(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!isWorkEntity(entity)) return;
        if (WORK_UUID.equals(entity.getUniqueId())) {
            entity.setPersistent(false);
        } else {
            // UUID が重なって振り直された古い物
            removeLater(entity);
        }
    }

    /** チャンクから読み込まれた作業用エンティティは、前に保存された古い物 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isWorkEntity(entity)) removeLater(entity);
        }
    }

    private void keepLoaded(World world) {
        world.addPluginChunkTicket(0, 0, plugin);
    }

    /** 読み込みの最中に消さない。次の tick の始め (データパックの tick より前) に消す */
    private void removeLater(Entity entity) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) entity.remove();
        });
    }

    /** 起動時: 今いる作業用エンティティを保存しない物にし、UUID の違う物と、2 つ目以降 (オーバーワールドの物を残す) を消す */
    private void sweep() {
        List<Entity> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isWorkEntity(display)) found.add(display);
            }
        }
        World main = Bukkit.getWorlds().getFirst();
        Entity keep = null;
        for (Entity entity : found) {
            if (!WORK_UUID.equals(entity.getUniqueId())) continue;
            if (keep == null || entity.getWorld().equals(main) && !keep.getWorld().equals(main)) keep = entity;
        }
        for (Entity entity : found) {
            if (entity == keep) entity.setPersistent(false);
            else entity.remove();
        }
    }
}
