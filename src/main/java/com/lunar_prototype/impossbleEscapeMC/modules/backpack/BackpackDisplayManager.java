package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * バックパックの背中ビジュアル表示を担当するマネージャー。
 * PacketEventsを使ってパケットだけのアイテムディスプレイ (バックパック・リグで1つずつ) をスポーンさせ、
 * プレイヤーにマウントすることで背中表示を実現する。インベントリロジックには一切触れない。
 * <p>
 * 前はアーマースタンドの頭にかぶせていた。体の向きを送るとクライアントが約3tickかけて追いかけ、向きも約1.4度刻みだったので、
 * 振り向くと遅れてずれた。ディスプレイは向きを変形 (left_rotation、小数の精度) で持ち、1tickで補間する。
 * 見た目の位置と大きさは、アーマースタンドの頭にかぶせた時と同じになるようにしてある
 * (アイテムのモデルの head の表示設定をそのまま使う。{@link #HEAD_TRANSLATION_Y}・{@link #HEAD_SCALE})
 */
public class BackpackDisplayManager {

    // ---- アイテムディスプレイのデータの番号 (26.3。Entity 0〜7、Display 8〜22、ItemDisplay 23〜24) ----
    private static final int DATA_INTERPOLATION_START = 8;
    private static final int DATA_INTERPOLATION_DURATION = 9;
    private static final int DATA_TRANSLATION = 11;
    private static final int DATA_SCALE = 12;
    private static final int DATA_LEFT_ROTATION = 13;
    private static final int DATA_ITEM = 23;
    private static final int DATA_ITEM_CONTEXT = 24;
    /** ItemDisplayContext.HEAD (アーマースタンドの頭と同じ表示設定) */
    private static final byte CONTEXT_HEAD = 5;
    /**
     * アーマースタンドの頭のアイテムの位置と大きさ (26.3 のクライアント): 足元から 1.501 - 1/16 (頭の軸) + 0.25 (頭の上) 上、0.625 倍。
     * アーマースタンドはこの後で体の向きに回るので、ディスプレイでは変形の回転 (left_rotation) に体の向きを入れる
     */
    static final float HEAD_TRANSLATION_Y = 1.501f - 1f / 16f + 0.25f;
    static final float HEAD_SCALE = 0.625f;
    /** 向きを変えた時の補間 (tick) */
    private static final int TURN_INTERPOLATION_TICKS = 1;

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
        /** 最後に送った体の向き */
        float sentYaw = Float.NaN;

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
        // 3D 装備アセットで描くリグは、従来の item display と二重に表示しない。
        if (rigItem != null && rigItem.hasItemMeta()
                && rigItem.getItemMeta().getEquippable() != null
                && (NamespacedKey.fromString("iem:micro_rig_3d").equals(rigItem.getItemMeta().getEquippable().getModel())
                    || NamespacedKey.fromString("iem:d3rcx_3d").equals(rigItem.getItemMeta().getEquippable().getModel())
                    || NamespacedKey.fromString("iem:sp_v_3d").equals(rigItem.getItemMeta().getEquippable().getModel()))) {
            rigItem = null;
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

        // 毎tick体の向きを見て、変わっていれば送るタスク
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
            if (bodyYaw == activeSession.sentYaw) return;
            activeSession.sentYaw = bodyYaw;
            // 向きだけ送り直す (補間は今から TURN_INTERPOLATION_TICKS で)
            List<EntityData<?>> turn = List.of(
                    new EntityData<>(DATA_INTERPOLATION_START, EntityDataTypes.INT, 0),
                    new EntityData<>(DATA_INTERPOLATION_DURATION, EntityDataTypes.INT, TURN_INTERPOLATION_TICKS),
                    new EntityData<>(DATA_LEFT_ROTATION, EntityDataTypes.QUATERNION, bodyRotation(bodyYaw)));
            WrapperPlayServerEntityMetadata baseTurn = new WrapperPlayServerEntityMetadata(baseEntityId, turn);
            WrapperPlayServerEntityMetadata rigTurn = rigEntityId != null ? new WrapperPlayServerEntityMetadata(rigEntityId, turn) : null;

            for (Player p : player.getWorld().getPlayers()) {
                if (!shouldShowToReceiver(player, p)) continue;
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, baseTurn);
                if (rigTurn != null) PacketEvents.getAPI().getPlayerManager().sendPacket(p, rigTurn);
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
        session.sentYaw = player.getBodyYaw();
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
        float yaw = Float.isNaN(session.sentYaw) ? owner.getBodyYaw() : session.sentYaw;
        List<Integer> mounted = new ArrayList<>();
        if (session.backpackHelmetItem != null) {
            spawnDisplay(receiver, session.baseEntityId, loc, session.backpackHelmetItem, yaw);
            mounted.add(session.baseEntityId);
        }
        if (session.rigEntityId != null && session.rigHelmetItem != null) {
            spawnDisplay(receiver, session.rigEntityId, loc, session.rigHelmetItem, yaw);
            mounted.add(session.rigEntityId);
        }
        // どちらもプレイヤーに直接乗せる (乗る位置はアーマースタンドの時と同じ: プレイヤーの乗せる位置)
        int[] passengers = mounted.stream().mapToInt(Integer::intValue).toArray();
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, new WrapperPlayServerSetPassengers(owner.getEntityId(), passengers));
    }

    private void spawnDisplay(Player receiver, int entityId, Location loc,
                              com.github.retrooper.packetevents.protocol.item.ItemStack item, float yaw) {
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.ITEM_DISPLAY,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                0f,
                0f,
                0f,
                0,
                Optional.empty()
        );
        // 出した時は補間しない (0 から伸びて見えないように)。向きは変形の回転で持つ (エンティティの向きは 0 のまま)
        List<EntityData<?>> metadata = List.of(
                new EntityData<>(DATA_INTERPOLATION_START, EntityDataTypes.INT, 0),
                new EntityData<>(DATA_INTERPOLATION_DURATION, EntityDataTypes.INT, 0),
                new EntityData<>(DATA_TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0f, HEAD_TRANSLATION_Y, 0f)),
                new EntityData<>(DATA_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE)),
                new EntityData<>(DATA_LEFT_ROTATION, EntityDataTypes.QUATERNION, bodyRotation(yaw)),
                new EntityData<>(DATA_ITEM, EntityDataTypes.ITEMSTACK, item),
                new EntityData<>(DATA_ITEM_CONTEXT, EntityDataTypes.BYTE, CONTEXT_HEAD));
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, spawn);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    /** 体の向き (度) の回転: アーマースタンドの「180 - 体の向き」と、ディスプレイがアイテムを 180 度回す分を合わせると Y 軸で -体の向き */
    static Quaternion4f bodyRotation(float bodyYaw) {
        double half = Math.toRadians(-bodyYaw) / 2.0;
        return new Quaternion4f(0f, (float) Math.sin(half), 0f, (float) Math.cos(half));
    }
}
