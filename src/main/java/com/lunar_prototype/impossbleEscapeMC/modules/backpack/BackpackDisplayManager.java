package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
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
        final int baseEntityId;
        final Integer rigEntityId;
        final int taskId;
        final com.github.retrooper.packetevents.protocol.item.ItemStack backpackHelmetItem;
        final com.github.retrooper.packetevents.protocol.item.ItemStack rigHelmetItem;
        final UUID worldUuid;
        boolean visibleToOwner;

        DisplaySession(int baseEntityId,
                       Integer rigEntityId,
                       int taskId,
                       com.github.retrooper.packetevents.protocol.item.ItemStack backpackHelmetItem,
                       com.github.retrooper.packetevents.protocol.item.ItemStack rigHelmetItem,
                       UUID worldUuid,
                       boolean visibleToOwner) {
            this.baseEntityId = baseEntityId;
            this.rigEntityId = rigEntityId;
            this.taskId = taskId;
            this.backpackHelmetItem = backpackHelmetItem;
            this.rigHelmetItem = rigHelmetItem;
            this.worldUuid = worldUuid;
            this.visibleToOwner = visibleToOwner;
        }
    }

    public BackpackDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sync(Player player) {
        BackpackModule backpackModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(BackpackModule.class);
        RigModule rigModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
        if (backpackModule == null) {
            clearDisplay(player);
            return;
        }

        ItemStack backpackItem = player.getInventory().getItemInOffHand();
        if (!backpackModule.isBackpackItem(backpackItem)) {
            backpackItem = null;
        }

        ItemStack rigItem = null;
        if (rigModule != null) {
            rigItem = rigModule.getEquippedRig(player);
        }

        if (backpackItem == null && rigItem == null) {
            clearDisplay(player);
            return;
        }

        equip(player, backpackItem, rigItem);
    }

    /**
     * バックパック／リグを背中に表示する。
     * リグだけ装備している場合も、土台用の空アーマースタンドを先に生成する。
     */
    public void equip(Player player, ItemStack backpackItem, ItemStack rigItem) {
        clearDisplay(player);

        int baseEntityId = ThreadLocalRandom.current().nextInt(1_000_000, Integer.MAX_VALUE);
        Integer rigEntityId = rigItem != null
                ? ThreadLocalRandom.current().nextInt(1_000_000, Integer.MAX_VALUE)
                : null;

        com.github.retrooper.packetevents.protocol.item.ItemStack backpackPeItem =
                backpackItem != null ? SpigotConversionUtil.fromBukkitItemStack(backpackItem) : null;
        com.github.retrooper.packetevents.protocol.item.ItemStack rigPeItem =
                rigItem != null ? SpigotConversionUtil.fromBukkitItemStack(rigItem) : null;

        // 毎tick体の向きを同期するタスク
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                clearDisplay(player);
                return;
            }
            DisplaySession activeSession = activeSessions.get(player.getUniqueId());
            if (activeSession == null) return;

            boolean shouldShowToOwner = shouldShowToReceiver(player, player);
            if (activeSession.visibleToOwner != shouldShowToOwner) {
                if (shouldShowToOwner) {
                    sendSpawnPackets(player, activeSession, player);
                } else {
                    sendDestroyPackets(activeSession, Collections.singleton(player));
                }
                activeSession.visibleToOwner = shouldShowToOwner;
            }

            float bodyYaw = player.getBodyYaw();
            WrapperPlayServerEntityHeadLook headLook =
                    new WrapperPlayServerEntityHeadLook(baseEntityId, bodyYaw);
            WrapperPlayServerEntityRotation rotation =
                    new WrapperPlayServerEntityRotation(baseEntityId, bodyYaw, 0f, true);

            WrapperPlayServerEntityHeadLook rigHeadLook = null;
            WrapperPlayServerEntityRotation rigRotation = null;
            if (rigEntityId != null) {
                rigHeadLook = new WrapperPlayServerEntityHeadLook(rigEntityId, bodyYaw);
                rigRotation = new WrapperPlayServerEntityRotation(rigEntityId, bodyYaw, 0f, true);
            }

            for (Player p : player.getWorld().getPlayers()) {
                if (!shouldShowToReceiver(player, p)) continue;
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, headLook);
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, rotation);
                if (rigHeadLook != null && rigRotation != null) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(p, rigHeadLook);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(p, rigRotation);
                }
            }
        }, 0L, 1L);

        DisplaySession session = new DisplaySession(
                baseEntityId,
                rigEntityId,
                task.getTaskId(),
                backpackPeItem,
                rigPeItem,
                player.getWorld().getUID(),
                shouldShowToReceiver(player, player)
        );
        activeSessions.put(player.getUniqueId(), session);

        // ワールド内の全プレイヤー（自分含む）へスポーンパケットを送信
        for (Player p : player.getWorld().getPlayers()) {
            if (!shouldShowToReceiver(player, p)) continue;
            sendSpawnPackets(player, session, p);
        }
    }

    /**
     * 背中のバックパック表示を消す。
     *
     * @param player 対象プレイヤー
     */
    public void clearDisplay(Player player) {
        DisplaySession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);

        // セッションが作成されたワールドの全プレイヤーにパケットを送信
        org.bukkit.World world = Bukkit.getWorld(session.worldUuid);
        if (world != null) {
            sendDestroyPackets(session, world.getPlayers());
        }
        
        // もし現在のワールドが異なるなら、現在のワールドのプレイヤーにも送信しておく（念のため）
        if (player.isOnline() && !player.getWorld().getUID().equals(session.worldUuid)) {
            sendDestroyPackets(session, player.getWorld().getPlayers());
        }
    }

    public void unequip(Player player) {
        clearDisplay(player);
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
        if (!shouldShowToReceiver(owner, receiver)) return;
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

    private boolean shouldShowToReceiver(Player owner, Player receiver) {
        if (!owner.equals(receiver)) return true;
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RaidModule.class);
        return raidModule == null || !raidModule.isInRaid(owner);
    }

    private void sendDestroyPackets(DisplaySession session, Collection<? extends Player> receivers) {
        WrapperPlayServerDestroyEntities destroy =
                session.rigEntityId != null
                        ? new WrapperPlayServerDestroyEntities(new int[]{session.baseEntityId, session.rigEntityId})
                        : new WrapperPlayServerDestroyEntities(new int[]{session.baseEntityId});

        for (Player receiver : receivers) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, destroy);
        }
    }

    private void sendSpawnPackets(Player owner, DisplaySession session, Player receiver) {
        Location loc = owner.getLocation();
        float bodyYaw = owner.getBodyYaw();

        // 1. SpawnEntity
        WrapperPlayServerSpawnEntity baseSpawn = new WrapperPlayServerSpawnEntity(
                session.baseEntityId,
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
        WrapperPlayServerEntityMetadata baseMeta =
                new WrapperPlayServerEntityMetadata(session.baseEntityId, metadata);

        WrapperPlayServerEntityEquipment baseEquipment = null;
        if (session.backpackHelmetItem != null) {
            List<Equipment> equipmentList = Collections.singletonList(
                    new Equipment(EquipmentSlot.HELMET, session.backpackHelmetItem)
            );
            baseEquipment = new WrapperPlayServerEntityEquipment(session.baseEntityId, equipmentList);
        }

        WrapperPlayServerSetPassengers playerMount = new WrapperPlayServerSetPassengers(
                owner.getEntityId(),
                new int[]{session.baseEntityId}
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, baseSpawn);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, baseMeta);
        if (baseEquipment != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, baseEquipment);
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, playerMount);

        if (session.rigEntityId != null) {
            WrapperPlayServerSpawnEntity rigSpawn = new WrapperPlayServerSpawnEntity(
                    session.rigEntityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ARMOR_STAND,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    loc.getPitch(),
                    bodyYaw,
                    bodyYaw,
                    0,
                    Optional.empty()
            );
            WrapperPlayServerEntityMetadata rigMeta =
                    new WrapperPlayServerEntityMetadata(session.rigEntityId, metadata);
            WrapperPlayServerEntityEquipment rigEquipment = null;
            if (session.rigHelmetItem != null) {
                List<Equipment> rigEquipmentList = Collections.singletonList(
                        new Equipment(EquipmentSlot.HELMET, session.rigHelmetItem)
                );
                rigEquipment = new WrapperPlayServerEntityEquipment(session.rigEntityId, rigEquipmentList);
            }
            WrapperPlayServerSetPassengers rigMount = new WrapperPlayServerSetPassengers(
                    session.baseEntityId,
                    new int[]{session.rigEntityId}
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, rigSpawn);
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, rigMeta);
            if (rigEquipment != null) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, rigEquipment);
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, rigMount);
        }
    }
}
