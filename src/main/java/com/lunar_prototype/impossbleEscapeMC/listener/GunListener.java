package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.util.BloodEffect;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class GunListener implements Listener {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastRightClickMap = new HashMap<>();
    private final Map<UUID, Long> lastShotTimeMap = new HashMap<>();
    private final Set<UUID> shootingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> consecutiveShots = new HashMap<>();

    // 射撃継続判定のタイムアウト（ms）
    // Minecraftの右クリックパケット間隔は約200msだが、タップ撃ち時の余韻を短くするために150msに設定
    private static final long SHOOTING_KEEP_ALIVE_MS = 150;

    // State machine storage
    private final Map<UUID, com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine> stateMachines = new HashMap<>();
    private final Map<UUID, BukkitRunnable> stateTasks = new HashMap<>();
    private final Map<UUID, Location> lastLocationMap = new HashMap<>();

    // Lag Compensation History
    private static final Map<UUID, NavigableMap<Long, BoundingBox>> entityHistory = new HashMap<>();
    private static final long HISTORY_DURATION_MS = 1000;

    private static final int MODEL_ADD_SCOPE = 1000;
    private static final int MODEL_ADD_SPRINT = 2000;

    public GunListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        // 既にオンラインのプレイヤーに監視タスクを開始
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            startStateTask(p);
        }
        startHistoryTask();
    }

    private void startHistoryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (World world : Bukkit.getWorlds()) {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        if (!(entity instanceof Player) && !(entity instanceof Mob))
                            continue;

                        var history = entityHistory.computeIfAbsent(entity.getUniqueId(), k -> new TreeMap<>());
                        history.put(now, entity.getBoundingBox().clone());

                        while (!history.isEmpty() && history.firstKey() < now - HISTORY_DURATION_MS) {
                            history.pollFirstEntry();
                        }
                    }
                }
                // クリーンアップ: オンラインでない/存在しないエンティティの履歴を削除
                entityHistory.entrySet().removeIf(entry -> Bukkit.getEntity(entry.getKey()) == null);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * ラグ補填された当たり判定ボックスを取得します。
     * BulletTaskから呼び出されます。
     */
    public static BoundingBox getCompensatedBox(LivingEntity entity, long targetTime) {
        NavigableMap<Long, BoundingBox> history = entityHistory.get(entity.getUniqueId());
        if (history == null || history.isEmpty())
            return entity.getBoundingBox();

        Map.Entry<Long, BoundingBox> floor = history.floorEntry(targetTime);
        Map.Entry<Long, BoundingBox> ceil = history.ceilingEntry(targetTime);

        if (floor == null)
            return ceil.getValue();
        if (ceil == null)
            return floor.getValue();

        // 近い方のデータを採用
        return (targetTime - floor.getKey() < ceil.getKey() - targetTime) ? floor.getValue() : ceil.getValue();
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        startStateTask(event.getPlayer());
    }

    private void cleanup(Player player, boolean quit) {
        stopShooting(player.getUniqueId());
        if (quit) {
            stopStateTask(player);
        } else {
            stateMachines.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onShoot(PlayerInteractEvent event) {
        // 右クリック判定
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();

        // 【追加】ダッシュ中やリロード中、ボルトアクション中は射撃不可
        if (player.isSprinting() || isReloading(player.getUniqueId()) || isBolting(player.getUniqueId()))
            return;

        UUID uuid = player.getUniqueId();

        // 1. クリック時刻を更新
        lastRightClickMap.put(uuid, System.currentTimeMillis());

        // 2. 既にタスクが動いているなら無視
        if (activeTasks.containsKey(uuid))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta())
            return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.ITEM_ID,
                com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.STRING);
        com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition def = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry
                .get(itemId);

        if (def == null || !"GUN".equalsIgnoreCase(def.type) || def.gunStats == null)
            return;

        // バニラのアイテム動作（クロスボウの発射など）をキャンセル
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        long lastShot = lastShotTimeMap.getOrDefault(uuid, 0L);
        long requiredIntervalMs = (long) (60000.0 / def.gunStats.rpm);

        // 3. 射撃ループ開始
        if (now - lastShot >= requiredIntervalMs) {
            // クールダウンが完了している場合、1発目を即座に発射
            consecutiveShots.put(uuid, 0); // バースト開始
            executeShoot(player, pdc, def.gunStats);
            lastShotTimeMap.put(uuid, now);

            if ("SEMI".equalsIgnoreCase(def.gunStats.fireMode)) {
                return;
            }
            // フルオートの場合は、次弾のタイミングを指定してループタスクを開始
            startShooting(player, pdc, def.gunStats, requiredIntervalMs);
        } else {
            // クールダウン中の場合は、最短で発射できるミリ秒後を指定して開始
            startShooting(player, pdc, def.gunStats, requiredIntervalMs - (now - lastShot));
        }
    }

    // --- 左クリックでエイム切り替え (トグル) ---

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isGun(item))
            return;

        event.setCancelled(true);

        var sm = getOrCreateStateMachine(player);
        sm.handleInput(com.lunar_prototype.impossbleEscapeMC.animation.state.InputType.LEFT_CLICK);
    }

    // --- ダッシュ状態の監視 ---
    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        var sm = getOrCreateStateMachine(player);

        if (event.isSprinting()) {
            sm.handleInput(com.lunar_prototype.impossbleEscapeMC.animation.state.InputType.SPRINT_START);
            // 射撃も強制停止
            if (activeTasks.containsKey(player.getUniqueId())) {
                stopShooting(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 2.0f);
            }
        } else {
            sm.handleInput(com.lunar_prototype.impossbleEscapeMC.animation.state.InputType.SPRINT_END);
        }
    }

    // --- 持ち替え時の処理 ---
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 射撃を強制停止
        stopShooting(uuid);

        // 次のティックを待たずに、新しいスロットのアイテムで即座に更新
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        updateWeaponModel(player, newItem);
    }

    private void startShooting(Player player, PersistentDataContainer pdc, GunStats stats, long firstShotDelayMs) {
        UUID uuid = player.getUniqueId();

        // Burst開始時にカウントをリセット
        consecutiveShots.putIfAbsent(uuid, 0);

        double msPerShot = 60000.0 / stats.rpm;

        BukkitRunnable task = new BukkitRunnable() {
            private double nextShotTime = (double) System.currentTimeMillis() + firstShotDelayMs;
            private boolean firstShot = true;

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long lastClick = lastRightClickMap.getOrDefault(uuid, 0L);

                // タイムアウト判定
                if (now - lastClick > SHOOTING_KEEP_ALIVE_MS) {
                    stopShooting(uuid);
                    return;
                }

                // その他の停止条件
                if (!player.isOnline() || player.isDead() || player.isSprinting() || !isHoldingSameGun(player, pdc)) {
                    stopShooting(uuid);
                    return;
                }

                // 時間が来ている分だけ射撃実行
                int shotsFiredThisTick = 0;
                while ((double) now >= nextShotTime && shotsFiredThisTick < 5) {
                    executeShoot(player, pdc, stats);
                    lastShotTimeMap.put(uuid, now);
                    nextShotTime += msPerShot;
                    firstShot = false;
                    shotsFiredThisTick++;

                    if ("SEMI".equalsIgnoreCase(stats.fireMode)) {
                        // セミオートの場合は、1回のクリック(正確なタイミング)につき1発まで。
                        // 長押しし続けている間はタスク自体は残す（アニメーション進行などのため）が、ここから先の弾は撃たない
                        break;
                    }
                }
            }
        };

        activeTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0, 1);
    }

    private void stopShooting(UUID uuid) {
        BukkitRunnable task = activeTasks.remove(uuid);
        if (task != null)
            task.cancel();
        lastRightClickMap.remove(uuid);
        consecutiveShots.remove(uuid);
    }

    private boolean isHoldingSameGun(Player player, PersistentDataContainer originalPdc) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta())
            return false;

        // 開始時のPDC（アイテムID）と現在持っているアイテムのIDが一致するか確認
        String currentId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        String startId = originalPdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);

        return startId != null && startId.equals(currentId);
    }

    @EventHandler
    public void onReload(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // クリエイティブや他のインベントリ（チェスト等）を開いている時は除外
        if (player.getOpenInventory().getType() != InventoryType.CRAFTING &&
                player.getOpenInventory().getType() != InventoryType.CREATIVE) {
            return;
        }

        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (!isGun(droppedItem))
            return;

        // 【修正】メインハンドにアイテムが残っている場合、それはインベントリ内の別のスロットから
        // 投げ出された（ドラッグ等）と判断し、リロードではなく通常のドロップとして扱う。
        // Qキーで手持ちの銃を落とした場合は、この時点でメインハンドは一時的にAIRになるため、
        // 以下のチェックを通過してリロード処理（キャンセル）が行われる。
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            return;
        }

        // 通常プレイ中のドロップ（リロード）処理
        event.setCancelled(true);
        int slotIndex = player.getInventory().getHeldItemSlot();

        // キャンセル直後はインベントリにアイテムが戻っていない可能性があるため、1tick後に処理する
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline())
                    return;

                // 1ティックの間にスロットが切り替わっていたら中断
                if (player.getInventory().getHeldItemSlot() != slotIndex)
                    return;

                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (currentItem == null || currentItem.getType() == Material.AIR)
                    return;

                // ジャムの解消
                ItemMeta meta = currentItem.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(PDCKeys.JAMMED, PDCKeys.BOOLEAN, (byte) 0);
                    currentItem.setItemMeta(meta);
                }

                // ステートマシンのアイテム参照を最新に更新
                updateWeaponModel(player, currentItem);

                var sm = getOrCreateStateMachine(player);
                sm.handleInput(com.lunar_prototype.impossbleEscapeMC.animation.state.InputType.RELOAD);
            }
        }.runTaskLater(plugin, 1);
    }

    // 【重要】タルコフ式反動ロジック
    private void executeShoot(Player player, PersistentDataContainer pdc, GunStats stats) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();

        // 0. ジャム判定
        boolean isJammed = itemPdc.getOrDefault(PDCKeys.JAMMED, PDCKeys.BOOLEAN, (byte) 0) == 1;
        if (isJammed) {
            stopShooting(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 0.5f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c§l>>> WEAPON JAMMED <<<", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // 1. 実物のアイテムのチェンバーから弾を確認
        boolean isChamberLoaded = itemPdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;

        if (!isChamberLoaded) {
            stopShooting(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 0.5f);

            // ボルトアクションでチャンバーが空、かつマガジンに弾がある場合、オートコッキング処理に入れるか考慮
            // ここではクリック時に空なら単にカチッと鳴るだけ（リロードまたはコッキングが必要）
            return;
        }

        // 2. チャンバーの弾を消費
        itemPdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0);

        // 撃った弾のIDを取得（後で弾薬生成に使う）
        String chamberAmmoId = itemPdc.get(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING);
        if (chamberAmmoId == null) {
            chamberAmmoId = itemPdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING); // フォールバック
        }

        // 3. 次弾の装填処理（オートマチック/クローズドボルトの場合）
        boolean isBoltAction = "BOLT_ACTION".equalsIgnoreCase(stats.boltType)
                || "PUMP_ACTION".equalsIgnoreCase(stats.boltType);

        if (!isBoltAction) { // フルオートやセミオートなど、自動で次弾が装填される銃
            int currentAmmo = itemPdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            if (currentAmmo > 0) {
                itemPdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, currentAmmo - 1);
                itemPdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 1);
                String currentAmmoId = itemPdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
                if (currentAmmoId != null) {
                    itemPdc.set(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING, currentAmmoId);
                }
            }
        }

        // 4. メタデータを実物のアイテムにセットし直す
        item.setItemMeta(meta);

        // --- 耐久値減少とジャム判定 ---
        String itemId = itemPdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);
        double durabilityRatio = 1.0;
        if (def != null && def.maxDurability > 0) {
            int currentDur = itemPdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
            currentDur = Math.max(0, currentDur - 1);
            itemPdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, currentDur);
            durabilityRatio = (double) currentDur / def.maxDurability;
            
            // ジャム判定 (50%以下から確率発生、0%で最大10%の確率)
            if (durabilityRatio < 0.5) {
                double jamProb = (0.5 - durabilityRatio) * 0.2; // (0.5 - 0.0) * 0.2 = 0.1 (10%)
                if (Math.random() < jamProb) {
                    itemPdc.set(PDCKeys.JAMMED, PDCKeys.BOOLEAN, (byte) 1);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§c§l>>> WEAPON JAMMED <<<", net.kyori.adventure.text.format.NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_WOOD_BREAK, 1.0f, 0.5f);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
                    stopShooting(player.getUniqueId());
                }
            }
            
            // ItemMetaを再度反映 (耐久値とジャムフラグを保存)
            item.setItemMeta(meta);
        }

        // --- 精度（拡散率）の計算 ---
        double inaccuracy = 0.0;
        boolean isAiming = isAiming(player.getUniqueId());

        if (isAiming) {
            // ショットガンの場合はエイム時でも一定の拡散を残す (8%程度)
            inaccuracy = (stats.pelletCount > 1) ? 0.15 : 0.005;
        } else {
            // 腰撃ち
            inaccuracy = 0.25; // 基本の拡散率 (2.5ブロック相当 / 10m)

            // 停止判定
            Location lastLoc = lastLocationMap.get(player.getUniqueId());
            if (lastLoc != null && lastLoc.getWorld().equals(player.getWorld())) {
                double distSq = lastLoc.distanceSquared(player.getLocation());
                // 1ティック(0.05秒)の移動距離が極めて小さければ停止とみなす
                if (distSq < 0.001) {
                    inaccuracy *= 0.5; // 停止時は精度50%向上
                }
            }
        }

        // --- 耐久度による精度の低下 (30%以下から拡散がひどくなる) ---
        if (durabilityRatio < 0.3) {
            double durabilityPenalty = (0.3 - durabilityRatio) * 1.5; // 0%時に+0.45の拡散
            inaccuracy += durabilityPenalty;
        }

        // --- 状態異常による補正 ---
        com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule dataModule = 
            plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule.class);
        if (dataModule != null) {
            com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data.hasArmFracture() && !data.isPainkillerActive()) {
                inaccuracy += 0.08; // 腕骨折で精度低下
                if (isAiming) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text("腕の骨折によりエイムが安定しません！", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }

        double damage = pdc.getOrDefault(PDCKeys.affix("damage"), PDCKeys.DOUBLE, stats.damage);
        double baseRecoil = pdc.getOrDefault(PDCKeys.affix("recoil"), PDCKeys.DOUBLE, stats.recoil);

        // 現在の連射数を取得して加算
        int shots = consecutiveShots.getOrDefault(player.getUniqueId(), 0);
        consecutiveShots.put(player.getUniqueId(), shots + 1);

        // --- 改良版 反動ロジック (Initial Kick -> Proportional Stability) ---
        double verticalMultiplier;
        double horizontalFactor;

        if (shots == 0) {
            // 【初弾】強烈な跳ね上がり
            verticalMultiplier = 4.2;
            horizontalFactor = 0.8;
        } else if (shots < 4) {
            // 【2-4発目】激しく暴れる
            verticalMultiplier = 3.2 - (shots * 0.5);
            horizontalFactor = 1.2;
        } else if (shots < 8) {
            // 【5-8発目】急激に反動が収束
            verticalMultiplier = 1.2 - ((shots - 4) * 0.1);
            horizontalFactor = 1.6;
        } else {
            // 【9発目以降】安定期（縦は抑えられるが、横ブレは続く）
            verticalMultiplier = 0.8;
            horizontalFactor = 2.0;
        }

        // 最終的な反動値（縦）
        double verticalNoise = (Math.random() - 0.5) * (baseRecoil * 0.2);
        double finalVerticalRecoil = (baseRecoil * verticalMultiplier) + verticalNoise;

        // 横反動 (縦反動に対して乖離しすぎないように制限)
        double rawHorizontal = (Math.random() - 0.5) * (baseRecoil * 0.6) * horizontalFactor;
        double maxHorizontal = Math.abs(finalVerticalRecoil) * 1.2; // 縦の1.2倍までに制限
        double horizontalRecoil = Math.max(-maxHorizontal, Math.min(maxHorizontal, rawHorizontal));

        // --- 処理の適用 ---
        spawnMuzzleFlash(player);
        ItemFactory.updateLore(item);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(chamberAmmoId);

        if (ammoDef != null) {
            int ammoClass = ammoDef.ammoClass;
            double totalDamage = ammoDef.damage * damage;
            int pellets = Math.max(1, stats.pelletCount);
            for (int i = 0; i < pellets; i++) {
                new BulletTask(ImpossbleEscapeMC.getInstance(),player, totalDamage, ammoClass, inaccuracy).start();
            }
        }

        // 縦反動: 銃は跳ね上がる(視線は上に行く)ため、ピッチはマイナス方向へ動かす
        float recoilPitch = (float) -finalVerticalRecoil;

        // 横反動: そのまま適用
        float recoilYaw = (float) horizontalRecoil;

        // 相対パケットを送信
        sendRecoilPacket(player, recoilYaw, recoilPitch);

        // --- 独立アニメーション（射撃/特定モーション）のトリガー ---
        com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine sm = stateMachines
                .get(player.getUniqueId());
        if (sm != null && sm.getContext() != null) {
            sm.getContext().startIndependentAnimation();
        }

        // --- 視覚的・音響的メタデータの付与 (AI用) ---
        player.setMetadata("last_fired_tick",
                new org.bukkit.metadata.FixedMetadataValue(plugin, Bukkit.getCurrentTick()));

        // --- SCAVへの音響通知 ---
        for (Entity entity : player.getNearbyEntities(64, 64, 64)) {
            if (entity instanceof Mob mob) {
                ScavController controller = ScavSpawner.getController(mob.getUniqueId());
                if (controller != null) {
                    controller.onSoundHeard(player.getLocation());
                }
            }
        }

        String soundName = stats.shotSound;
        float shotPitch = 1.8f + (float) ((Math.random() - 0.5) * 0.1); // 銃声のピッチにノイズを追加

        try {
            // Bukkit標準のサウンドにあるかチェック
            Sound standardSound = Sound.valueOf(soundName.toUpperCase());
            // 音量8.0で約128ブロックまで聞こえるように拡大
            player.getWorld().playSound(player.getLocation(), standardSound, 8.0f, shotPitch);
        } catch (IllegalArgumentException e) {
            // 標準にない場合はカスタムサウンド(リソースパック)として再生
            player.getWorld().playSound(player.getLocation(), soundName, 8.0f, shotPitch);
        }

        // 1秒後に薬莢の落ちる音を再生
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isValid()) {
                    float pitch = 1.0f + (float) ((Math.random() - 0.5) * 0.1);
                    player.getWorld().playSound(player.getLocation(), "minecraft:custom.gunshell", 0.5f, pitch);
                }
            }
        }.runTaskLater(plugin, 20);

        // --- 射撃後の状態遷移 (オートコッキングなど) ---
        if (isBoltAction) {
            // ボルトアクションのオートコッキング
            stopShooting(player.getUniqueId()); // まず射撃ループを止める
            if (sm != null) {
                // ボルトアクションステートに移行 (内部エンターで次弾があればコッキングアニメに入る)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sm.transitionTo(new com.lunar_prototype.impossbleEscapeMC.animation.state.BoltingState());
                    }
                }.runTaskLater(plugin, 1);
            }
        }
    }

    /**
     * モブが射撃するための簡易メソッド
     * 
     * @param shooter    撃つモブ
     * @param stats      銃のステータス
     * @param ammoClass  使用する弾のクラス
     * @param inaccuracy 集弾率 (0で正確、数値が大きいほどバラける)
     */
    public void executeMobShoot(LivingEntity shooter, GunStats stats, int ammoClass, double inaccuracy) {
        // 1. マズルフラッシュと音
        spawnMuzzleFlash(shooter);
        float shotPitch = 1.8f + (float) ((Math.random() - 0.5) * 0.1); // 銃声のピッチにノイズを追加
        shooter.getWorld().playSound(shooter.getLocation(), stats.shotSound, 8.0f, shotPitch);

        // 2. 弾丸の生成
        com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition ammoDef = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.getWeakestAmmoForCaliber(stats.caliber);
        double baseDamage = (ammoDef != null) ? ammoDef.damage : 5.0;
        double totalDamage = baseDamage * stats.damage;

        int pellets = Math.max(1, stats.pelletCount);
        for (int i = 0; i < pellets; i++) {
            new BulletTask(plugin, shooter, totalDamage, ammoClass, inaccuracy).start();
        }

        // 1秒後に薬莢の落ちる音を再生 (AI用)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (shooter.isValid()) {
                    float pitch = 1.0f + (float) ((Math.random() - 0.5) * 0.1);
                    shooter.getWorld().playSound(shooter.getLocation(), "minecraft:custom.gunshell", 0.5f, pitch);
                }
            }
        }.runTaskLater(plugin, 20);
    }

    /**
     * PacketEventsを使用してプレイヤーに相対的な視点変更（リコイル）を送信します。
     *
     * @param player      対象プレイヤー
     * @param yawRecoil   横方向の変化量 (右が正)
     * @param pitchRecoil 縦方向の変化量 (下が正。リコイルで上に跳ねるならマイナスを指定)
     */
    private void sendRecoilPacket(Player player, float yawRecoil, float pitchRecoil) {

        WrapperPlayServerPlayerRotation packet = new WrapperPlayServerPlayerRotation(
                yawRecoil,
                true,
                pitchRecoil,
                true);

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void spawnMuzzleFlash(LivingEntity player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        boolean isAiming = isAiming(player.getUniqueId());

        // マズルの位置計算
        Location muzzleLoc;
        if (isAiming) {
            // ADS（エイム）時は少し前方
            muzzleLoc = eye.clone().add(dir.clone().multiply(2.2));
        } else {
            // 腰撃ち時は右下へオフセット（右利き想定）
            Vector offset = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.3);
            offset.add(new Vector(0, -0.2, 0));
            muzzleLoc = eye.clone().add(dir.clone().multiply(1.0)).add(offset);
        }

        // --- 硝煙パーティクル (white_smoke 的な見た目) ---
        // Particle.CLOUD は白くてふわっと広がるので、1粒でも十分「撃った感」が出ます
        player.getWorld().spawnParticle(
                Particle.WHITE_SMOKE,
                muzzleLoc,
                1, // 個数（1個で十分）
                0.0, 0.0, 0.0, // 散らばり（0にすると一点から出る）
                0.05 // 速度（少しだけ動かすとリアル）
        );

        // --- 光源処理（一瞬だけ光らせる） ---
        handleMuzzleLight(muzzleLoc);
    }

    /**
     * 光源処理を別メソッドに切り出してスッキリさせました
     */
    private void handleMuzzleLight(Location loc) {
        if (loc.getBlock().getType() == Material.AIR) {
            loc.getBlock().setType(Material.LIGHT);
            if (loc.getBlock().getBlockData() instanceof org.bukkit.block.data.Levelled light) {
                light.setLevel(12);
                loc.getBlock().setBlockData(light);
            }

            // 1回(1tick)後に消去
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (loc.getBlock().getType() == Material.LIGHT) {
                        loc.getBlock().setType(Material.AIR);
                    }
                }
            }.runTaskLater(plugin, 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
        cleanup(event.getPlayer(), true);
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        cleanup(event.getEntity());
    }

    private void cleanup(Player player) {
        stopShooting(player.getUniqueId());
        stopStateTask(player);
    }

    // アニメーション管理

    private com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine getOrCreateStateMachine(
            Player player) {
        UUID uuid = player.getUniqueId();
        if (stateMachines.containsKey(uuid)) {
            return stateMachines.get(uuid);
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        GunStats stats = null;
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def != null)
                stats = def.gunStats;
        }

        var ctx = new com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponContext(plugin, player, item, stats);
        // Default to Idle
        var sm = new com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine(ctx,
                new com.lunar_prototype.impossbleEscapeMC.animation.state.IdleState());
        stateMachines.put(uuid, sm);

        startStateTask(player);

        return sm;
    }

    private void updateWeaponModel(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        if (!isGun(item)) {
            stopShooting(uuid);
            stopStateTask(player);
            stateMachines.remove(uuid);
            return;
        }

        var sm = getOrCreateStateMachine(player);

        // Update Context Item
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def != null && def.gunStats != null) {
            ItemStack oldItem = sm.getContext().getItem();

            // アイテムが物理的に異なる（またはスロットが違う、メタが違う等）場合はリセット
            if (oldItem == null || !oldItem.equals(item)) {
                // 持ち替え時は強制的にリセットして Idle 状態へ
                sm.getContext().setItem(item);
                sm.getContext().setStats(def.gunStats);
                sm.reset();
            } else {
                // 同じアイテムならコンテキストの参照だけ更新（メタ情報の同期のため）
                sm.getContext().setItem(item);
            }

            // 監視タスクが止まっていれば再開
            if (!stateTasks.containsKey(uuid)) {
                startStateTask(player);
            }
        }
    }

    private void startStateTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (stateTasks.containsKey(uuid))
            return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopStateTask(player);
                    return;
                }

                // 現在持っているアイテムを取得
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!isGun(current)) {
                    stopStateTask(player);
                    stateMachines.remove(uuid);
                    return;
                }

                if (stateMachines.containsKey(uuid)) {
                    stateMachines.get(uuid).update();
                }

                // ティックの終わりに現在地を記録
                lastLocationMap.put(uuid, player.getLocation());
            }
        };
        task.runTaskTimer(plugin, 0, 1);
        stateTasks.put(uuid, task);
    }

    private void stopStateTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (stateTasks.containsKey(uuid)) {
            stateTasks.get(uuid).cancel();
            stateTasks.remove(uuid);
        }

        var sm = stateMachines.get(uuid);
        if (sm != null) {
            sm.transitionTo(null);
        }

        stateMachines.remove(uuid);
        lastLocationMap.remove(uuid);
    }

    // Helpers used by shooting logic
    private boolean isAiming(UUID uuid) {
        if (!stateMachines.containsKey(uuid))
            return false;
        return stateMachines.get(uuid)
                .getCurrentState() instanceof com.lunar_prototype.impossbleEscapeMC.animation.state.AimingState;
    }

    private boolean isReloading(UUID uuid) {
        if (!stateMachines.containsKey(uuid))
            return false;
        return stateMachines.get(uuid)
                .getCurrentState() instanceof com.lunar_prototype.impossbleEscapeMC.animation.state.ReloadingState;
    }

    private boolean isBolting(UUID uuid) {
        if (!stateMachines.containsKey(uuid))
            return false;
        return stateMachines.get(uuid)
                .getCurrentState() instanceof com.lunar_prototype.impossbleEscapeMC.animation.state.BoltingState;
    }

    // ユーティリティ: 銃かどうか判定
    private boolean isGun(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta())
            return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId == null)
            return false;

        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null && "GUN".equalsIgnoreCase(def.type);
    }

    @EventHandler
    public void onKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (event.getEntity().hasMetadata("no_knockback")) {
            event.setCancelled(true);
            event.getEntity().removeMetadata("no_knockback", plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata("bypass_armor")) {
            event.getEntity().removeMetadata("bypass_armor", plugin);

            // バニラ防具等のダメージ修正を0にする
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
                if (event.isApplicable(modifier) && modifier != EntityDamageEvent.DamageModifier.BASE) {
                    event.setDamage(modifier, 0.0);
                }
            }
        }
    }

    private List<String> getAttachments(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return Collections.emptyList();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
        if (joined == null || joined.isEmpty())
            return Collections.emptyList();
        return Arrays.asList(joined.split(","));
    }
}