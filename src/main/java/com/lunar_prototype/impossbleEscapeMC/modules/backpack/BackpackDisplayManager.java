package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * バックパックの背中ビジュアル表示を担当するマネージャー。
 * PacketEventsを使って仮想アーマースタンドをスポーンさせ、
 * プレイヤーにマウントすることで背中表示を実現する。
 * インベントリロジックには一切触れない。
 */
public class BackpackDisplayManager {

    private final JavaPlugin plugin;
    private final Map<UUID, DisplaySession> activeSessions = new HashMap<>();

    private static class DisplaySession {
        final int entityId;
        final int taskId;
        /** PacketEventsのItemStack（ヘルメット再送信に使用） */
        final com.github.retrooper.packetevents.protocol.item.ItemStack helmetItem;

        DisplaySession(int entityId, int taskId, com.github.retrooper.packetevents.protocol.item.ItemStack helmetItem) {
            this.entityId = entityId;
            this.taskId = taskId;
            this.helmetItem = helmetItem;
        }
    }

    public BackpackDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * バックパックを背中に表示する。
     * 既に表示中の場合は一度解除してから再表示する。
     *
     * @param player      装備プレイヤー
     * @param backpackItem オフハンドのバックパックBukkitItemStack（ヘルメット装着に使用）
     */
    public void equip(Player player, ItemStack backpackItem) {
        unequip(player); // 既存セッションがあれば破棄

        int entityId = ThreadLocalRandom.current().nextInt(1_000_000, Integer.MAX_VALUE);

        // BukkitのItemStack → PacketEventsのItemStackへ変換
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem =
                SpigotConversionUtil.fromBukkitItemStack(backpackItem);

        // 毎tick体の向きを同期するタスク
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                unequip(player);
                return;
            }
            if (!activeSessions.containsKey(player.getUniqueId())) return;

            float bodyYaw = player.getBodyYaw();
            WrapperPlayServerEntityHeadLook headLook =
                    new WrapperPlayServerEntityHeadLook(entityId, bodyYaw);
            WrapperPlayServerEntityRotation rotation =
                    new WrapperPlayServerEntityRotation(entityId, bodyYaw, 0f, true);

            for (Player p : player.getWorld().getPlayers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, headLook);
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, rotation);
            }
        }, 0L, 1L);

        DisplaySession session = new DisplaySession(entityId, task.getTaskId(), peItem);
        activeSessions.put(player.getUniqueId(), session);

        // ワールド内の全プレイヤー（自分含む）へスポーンパケットを送信
        for (Player p : player.getWorld().getPlayers()) {
            sendSpawnPackets(player, session, p);
        }
    }

    /**
     * 背中のバックパック表示を消す。
     *
     * @param player 対象プレイヤー
     */
    public void unequip(Player player) {
        DisplaySession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);

        WrapperPlayServerDestroyEntities destroy =
                new WrapperPlayServerDestroyEntities(new int[]{session.entityId});

        for (Player p : player.getWorld().getPlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, destroy);
        }
    }

    /**
     * 既にバックパックを装備しているプレイヤーの表示を、
     * 特定のプレイヤー（例：新規参加者）に送信する。
     *
     * @param owner    バックパックを装備しているプレイヤー
     * @param receiver スポーンパケットを受け取るプレイヤー
     */
    public void showToPlayer(Player owner, Player receiver) {
        DisplaySession session = activeSessions.get(owner.getUniqueId());
        if (session == null) return;
        if (!owner.getWorld().equals(receiver.getWorld())) return;
        sendSpawnPackets(owner, session, receiver);
    }

    /**
     * @param player 確認対象プレイヤー
     * @return バックパック表示セッションが存在するか
     */
    public boolean hasDisplay(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * 全セッションを破棄する（サーバー停止時に呼ぶ）。
     */
    public void shutdown() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                unequip(p);
            }
        }
        activeSessions.clear();
    }

    // ---------------------------------------------------------------
    // private helpers
    // ---------------------------------------------------------------

    private void sendSpawnPackets(Player owner, DisplaySession session, Player receiver) {
        Location loc = owner.getLocation();
        float bodyYaw = owner.getBodyYaw();

        // 1. SpawnEntity
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                session.entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.ARMOR_STAND,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                bodyYaw,
                bodyYaw,
                0,
                Optional.empty()
        );

        // 2. Metadata: Invisible + Marker
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20)); // Invisible
        metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x10)); // Marker flag
        WrapperPlayServerEntityMetadata meta =
                new WrapperPlayServerEntityMetadata(session.entityId, metadata);

        // 3. Equipment: ヘルメットスロットにバックパックアイテムを装着
        List<Equipment> equipmentList = Collections.singletonList(
                new Equipment(EquipmentSlot.HELMET, session.helmetItem)
        );
        WrapperPlayServerEntityEquipment equipment =
                new WrapperPlayServerEntityEquipment(session.entityId, equipmentList);

        // 4. Mount: プレイヤーにアーマースタンドをマウント
        WrapperPlayServerSetPassengers mount = new WrapperPlayServerSetPassengers(
                owner.getEntityId(),
                new int[]{session.entityId}
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, spawn);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, meta);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, equipment);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, mount);
    }
}
