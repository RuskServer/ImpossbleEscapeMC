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
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class GunListener implements Listener {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastRightClickMap = new HashMap<>();
    private final Map<UUID, Long> lastShotTimeMap = new HashMap<>();
    private final Set<UUID> shootingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> consecutiveShots = new HashMap<>();

    // State machine storage
    private final Map<UUID, com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine> stateMachines = new HashMap<>();
    private final Map<UUID, BukkitRunnable> stateTasks = new HashMap<>();
    private final Map<UUID, Location> lastLocationMap = new HashMap<>();

    private static final int MODEL_ADD_SCOPE = 1000;
    private static final int MODEL_ADD_SPRINT = 2000;

    public GunListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
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
        long delayTicks = 0;

        if (now - lastShot < requiredIntervalMs) {
            long remainingMs = requiredIntervalMs - (now - lastShot);
            delayTicks = remainingMs / 50;
            if (delayTicks < 1)
                delayTicks = 1;
        }

        // 3. 射撃ループ開始
        startShooting(player, pdc, def.gunStats, delayTicks);
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

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                updateWeaponModel(player);
            }
        }.runTaskLater(plugin, 1);
    }

    private void startShooting(Player player, PersistentDataContainer pdc, GunStats stats, long delayTicks) {
        UUID uuid = player.getUniqueId();

        consecutiveShots.put(uuid, 0);

        double msPerShot = 60000.0 / stats.rpm;

        BukkitRunnable task = new BukkitRunnable() {
            private double nextShotTime = (double) System.currentTimeMillis() + (delayTicks * 50);
            private boolean firstShot = true;

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long lastClick = lastRightClickMap.getOrDefault(uuid, 0L);

                // 300ms以上イベントが来なければ「指を離した」とみなす
                if (!firstShot && now - lastClick > 300) {
                    stopShooting(uuid);
                    return;
                }

                // その他の停止条件
                if (!player.isOnline() || player.isDead() || !isHoldingSameGun(player, pdc)) {
                    stopShooting(uuid);
                    return;
                }

                // 時間が来ている分だけ射撃実行 (RPM > 1200 の複数回射撃にも対応)
                int shotsFiredThisTick = 0;
                while ((double) now >= nextShotTime && shotsFiredThisTick < 5) {
                    executeShoot(player, pdc, stats);
                    lastShotTimeMap.put(uuid, now);
                    nextShotTime += msPerShot;
                    firstShot = false;
                    shotsFiredThisTick++;

                    // セミオートの場合は1回で終了
                    if ("SEMI".equalsIgnoreCase(stats.fireMode)) {
                        stopShooting(uuid);
                        return;
                    }
                }
            }
        };

        activeTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0, 1); // 常に1tick間隔でチェック
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

        if (player.getOpenInventory().getType() != InventoryType.CRAFTING &&
                player.getOpenInventory().getType() != InventoryType.CREATIVE) {
            return;
        }

        ItemStack item = event.getItemDrop().getItemStack();
        if (!isGun(item))
            return;

        // 通常プレイ中のドロップ（リロード）処理
        event.setCancelled(true);

        // キャンセル直後はインベントリにアイテムが戻っていない可能性があるため、1tick後に処理する
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline())
                    return;

                var sm = getOrCreateStateMachine(player);
                ItemStack currentItem = player.getInventory().getItemInMainHand();

                // 射撃停止
                stopShooting(player.getUniqueId());

                // アイテムが戻っているか確認 (AIRなら何もしない)
                if (currentItem == null || currentItem.getType() == Material.AIR) {
                    return;
                }

                sm.getContext().setItem(currentItem);
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

        // --- 精度（拡散率）の計算 ---
        double inaccuracy = 0.0;
        boolean isAiming = isAiming(player.getUniqueId());

        if (isAiming) {
            inaccuracy = 0.005; // エイム時はほぼ正確
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

        double damage = pdc.getOrDefault(PDCKeys.affix("damage"), PDCKeys.DOUBLE, stats.damage);
        double baseRecoil = pdc.getOrDefault(PDCKeys.affix("recoil"), PDCKeys.DOUBLE, stats.recoil);

        // 現在の連射数を取得して加算
        int shots = consecutiveShots.getOrDefault(player.getUniqueId(), 0);
        consecutiveShots.put(player.getUniqueId(), shots + 1);

        // --- タルコフ式 反動係数の計算 ---
        double recoilMultiplier;
        double horizontalMultiplier = 1.0;

        if (shots == 0) {
            // 【1発目】 初弾は強烈な跳ね上がり (例: 2.5倍)
            recoilMultiplier = 2.5;
            horizontalMultiplier = 0.5; // 初弾は横ブレ少なめ
        } else if (shots < 5) {
            // 【2~5発目】 まだ暴れるが、急速に制御しようとする (線形補間的に下げる)
            // shots=1 -> 2.0, shots=4 -> 1.1 みたいなイメージ
            recoilMultiplier = 2.0 - (shots * 0.3);
        } else {
            // 【6発目以降】 完全に制御下にある状態 (例: 0.6倍)
            recoilMultiplier = 0.6;
            // 逆に安定時は横ブレが少し目立つようにする
            horizontalMultiplier = 1.5;
        }

        // 最低値ガード
        recoilMultiplier = Math.max(0.4, recoilMultiplier);

        // 最終的な反動値
        double finalVerticalRecoil = baseRecoil * recoilMultiplier;

        // 横反動 (ランダムに左右に振る: -1.0 ~ 1.0)
        double horizontalRecoil = (Math.random() - 0.5) * (baseRecoil * 0.5) * horizontalMultiplier;

        // --- 処理の適用 ---
        spawnMuzzleFlash(player);
        ItemFactory.updateLore(item);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(chamberAmmoId);

        if (ammoDef != null) {
            int ammoClass = ammoDef.ammoClass;

            new BulletTask(player, damage, ammoClass, inaccuracy).runTaskTimer(plugin, 0, 1);
        }

        // 縦反動: 銃は跳ね上がる(視線は上に行く)ため、ピッチはマイナス方向へ動かす
        float recoilPitch = (float) -finalVerticalRecoil;

        // 横反動: そのまま適用
        float recoilYaw = (float) horizontalRecoil;

        // 相対パケットを送信
        sendRecoilPacket(player, recoilYaw, recoilPitch);

        // --- 視覚的・音響的メタデータの付与 (AI用) ---
        player.setMetadata("last_fired_tick", new org.bukkit.metadata.FixedMetadataValue(plugin, Bukkit.getCurrentTick()));

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

        try {
            // Bukkit標準のサウンドにあるかチェック
            Sound standardSound = Sound.valueOf(soundName.toUpperCase());
            // 音量8.0で約128ブロックまで聞こえるように拡大
            player.getWorld().playSound(player.getLocation(), standardSound, 8.0f, 1.8f);
        } catch (IllegalArgumentException e) {
            // 標準にない場合はカスタムサウンド(リソースパック)として再生
            player.getWorld().playSound(player.getLocation(), soundName, 8.0f, 1.8f);
        }

        // --- 射撃後の状態遷移 (オートコッキングなど) ---
        if (isBoltAction) {
            // ボルトアクションのオートコッキング
            stopShooting(player.getUniqueId()); // まず射撃ループを止める
            com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine sm = stateMachines
                    .get(player.getUniqueId());
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
        shooter.getWorld().playSound(shooter.getLocation(), stats.shotSound, 8.0f, 1.8f);

        // 2. 弾丸の生成 (inaccuracyをコンストラクタで渡すように変更)
        BulletTask task = new BulletTask(shooter, stats.damage, ammoClass, inaccuracy);

        task.runTaskTimer(plugin, 0, 1);
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
        UUID uuid = player.getUniqueId();

        boolean isAiming = isAiming(player.getUniqueId());

        Location muzzleLoc;
        if (isAiming) {
            muzzleLoc = eye.clone().add(dir.clone().multiply(2.2));
        } else {
            Vector offset = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.3);
            offset.add(new Vector(0, -0.2, 0));
            muzzleLoc = eye.clone().add(dir.clone().multiply(1.0)).add(offset);
        }

        // 確率でパーティクルを間引く
        double rand = Math.random();

        // --- 1. 芯の部分 (50%の確率で1粒) ---
        if (rand < 0.5) {
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    muzzleLoc,
                    1, 0.01, 0.01, 0.01, 0.05,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 220), 0.6f));
        }

        // --- 2. 広がる火花 (常に1粒) ---
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc.clone().add(dir.clone().multiply(0.2)),
                1, 0.03, 0.03, 0.03, 0.1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 20), 0.4f));

        // --- 3. 硝煙 (30%の確率で1粒) ---
        if (rand < 0.3) {
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    muzzleLoc,
                    1, 0.05, 0.05, 0.05, 0.02,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 0.8f));
        }

        // --- 光源処理 ---
        if (muzzleLoc.getBlock().getType() == Material.AIR) {
            muzzleLoc.getBlock().setType(Material.LIGHT);
            if (muzzleLoc.getBlock().getBlockData() instanceof org.bukkit.block.data.Levelled light) {
                light.setLevel(12);
                muzzleLoc.getBlock().setBlockData(light);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (muzzleLoc.getBlock().getType() == Material.LIGHT)
                        muzzleLoc.getBlock().setType(Material.AIR);
                }
            }.runTaskLater(plugin, 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        cleanup(event.getEntity());
    }

    private void cleanup(Player player) {
        stopShooting(player.getUniqueId());
        stopStateTask(player);
    }

    // 弾丸の挙動を管理するインナークラス
    private class BulletTask extends BukkitRunnable {
        private final LivingEntity shooter;
        private final double damage;
        private final Vector origin; // 射撃開始地点を保存
        private Location currentLoc;
        private Vector velocity;
        private int ticksAlive = 0;

        // 設定値 (後でYAMLから読み込めるようにすると良い)
        private final double GRAVITY = 0.015; // 1ティックあたりの落下量
        private final double SPEED = 6.0; // 1ティックに進む距離
        private final int ammoClass;

        public BulletTask(LivingEntity shooter, double damage, int ammoClass, double inaccuracy) {
            this.shooter = shooter;
            this.damage = damage;
            this.currentLoc = shooter.getEyeLocation();
            this.origin = this.currentLoc.toVector(); // 初期位置を保存
            
            Vector dir = shooter.getEyeLocation().getDirection();
            
            // 拡散（インナキュラシー）の適用
            if (inaccuracy > 0) {
                dir.add(new Vector(
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy
                ));
                dir.normalize();
            }
            
            this.velocity = dir.multiply(SPEED);
            this.ammoClass = ammoClass;
        }

        @Override
        public void run() {
            ticksAlive++;
            if (ticksAlive > 100) { // 5秒経ったら消滅
                this.cancel();
                return;
            }

            // 前回の地点を保存
            Location prevLoc = currentLoc.clone();

            // 重力の適用
            velocity.add(new Vector(0, -GRAVITY, 0));
            currentLoc.add(velocity);

            // ニアミス判定 (回避報酬)
            // 弾丸の軌道近くにいるスカブに「避けた」報酬を与える
            if (shooter instanceof Player) {
                for (Entity nearby : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)) {
                    if (nearby instanceof Mob scav && !nearby.equals(shooter)) {
                        ScavController controller = ScavSpawner.getController(scav.getUniqueId());
                        if (controller != null) {
                            // 弾が近くを通る = 回避成功
                            controller.getBrain().reward(0.05f);
                            controller.addSuppression(0.1f); // 制圧値加算
                        }
                    }
                }
            }

            // 曳光弾のエフェクト (前回の地点から今回の地点まで線を引くようにパーティクルを出す)
            spawnTracer(prevLoc, currentLoc);

            // 当たり判定 (ループ化して貫通に対応)
            double distanceRemaining = SPEED;
            Location rayStart = prevLoc.clone();
            Vector rayDir = velocity.clone().normalize();

            while (distanceRemaining > 0.01) {
                var rayTrace = currentLoc.getWorld().rayTrace(
                        rayStart,
                        rayDir,
                        distanceRemaining,
                        FluidCollisionMode.NEVER,
                        true,
                        0.2,
                        (e) -> e instanceof LivingEntity && !e.equals(shooter));

                if (rayTrace == null) break;

                if (rayTrace.getHitEntity() instanceof LivingEntity victim) {
                    Vector bulletDir = velocity.clone().normalize();
                    handleDamage(victim, shooter, damage, ammoClass, rayTrace.getHitPosition().toLocation(currentLoc.getWorld()));
                    BloodEffect.spawn(
                            rayTrace.getHitPosition().toLocation(currentLoc.getWorld()),
                            bulletDir,
                            damage);
                    this.cancel();
                    return;
                } else if (rayTrace.getHitBlock() != null) {
                    Block block = rayTrace.getHitBlock();
                    Location hitLoc = rayTrace.getHitPosition().toLocation(currentLoc.getWorld());
                    org.bukkit.block.BlockFace face = rayTrace.getHitBlockFace();

                    // 70%の確率で貫通可能なブロックか判定
                    if (isPenetrable(block.getType()) && Math.random() < 0.7) {
                        // 貫通処理: エフェクトだけ出して続行
                        block.getWorld().playEffect(hitLoc, Effect.STEP_SOUND, block.getType());
                        
                        double hitDist = rayTrace.getHitPosition().distance(rayStart.toVector());
                        // ヒット地点から少し先に進めて再開
                        rayStart = hitLoc.clone().add(rayDir.clone().multiply(0.01));
                        distanceRemaining -= (hitDist + 0.01);
                        continue;
                    } else {
                        // 壁に着弾 (または貫通失敗)
                        spawnBulletHole(hitLoc, face);
                        this.cancel();
                        return;
                    }
                } else {
                    break;
                }
            }
        }

        private void spawnBulletHole(Location loc, org.bukkit.block.BlockFace face) {
            if (face == null) return;
            
            // 壁から0.5ブロック離す
            Vector offset = face.getDirection().multiply(0.5);
            Location holeLoc = loc.clone().add(offset);
            
            // 指定された色: [0.149, 0.137, 0.122] -> 約 RGB(38, 35, 31)
            org.bukkit.Color color = org.bukkit.Color.fromRGB(38, 35, 31);
            // targetを現在地と同じにすることで移動させず、duration: 600 (30秒) 残留させる
            Particle.Trail trailData = new Particle.Trail(holeLoc, color, 600);
            
            loc.getWorld().spawnParticle(Particle.TRAIL, holeLoc, 1, 0, 0, 0, 0, trailData);
            
            // 着弾時の火花
            loc.getWorld().spawnParticle(Particle.DUST, holeLoc, 2, 0.02, 0.02, 0.02, 0.05,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 150, 150), 0.4f));
        }

        private boolean isPenetrable(Material material) {
            String name = material.name();
            return name.contains("GLASS") || name.equals("IRON_BARS") || name.contains("COPPER_BARS");
        }

        private void spawnTracer(Location from, Location to) {
            // スレッドセーフにするため、必要なデータは事前にコピー/計算して渡す
            final World world = from.getWorld();
            final Vector start = from.toVector();
            final Vector end = to.toVector();
            final Vector direction = end.clone().subtract(start);
            final double distance = direction.length();

            // 距離が近すぎる場合は描画しない (または正規化でエラーになるのを防ぐ)
            if (distance < 0.05)
                return;

            direction.normalize();

            // 非同期で描画
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 0.25ブロック刻みで補間
                    double gap = 0.25;
                    // 明るいオレンジ (RGB: 255, 180, 50), サイズ 0.6
                    Particle.DustOptions dustOption = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 180, 50),
                            0.2f);

                    for (double d = 0; d < distance; d += gap) {
                        Vector pos = start.clone().add(direction.clone().multiply(d));

                        // 【追加】発射地点から3ブロック以内は描画しない (距離の2乗で比較)
                        if (pos.distanceSquared(origin) < 9.0) continue;

                        try {
                            // count=1, offsets=0, extra=0
                            world.spawnParticle(Particle.DUST, pos.getX(), pos.getY(), pos.getZ(),
                                    1, 0, 0, 0, 0,
                                    dustOption);
                        } catch (Exception e) {
                            // 非同期パーティクル呼び出しでの万が一の安全策
                        }
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
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

    private void updateWeaponModel(Player player) {
        // Update context with current item
        var sm = getOrCreateStateMachine(player);
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isGun(item)) {
            stopStateTask(player);
            stateMachines.remove(player.getUniqueId());
            return;
        }

        // Update Context Item
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def != null && def.gunStats != null) {
            // 現在のコンテキストにあるアイテムと、新しく持ったアイテムが異なる場合
            ItemStack oldItem = sm.getContext().getItem();
            boolean itemChanged = true;

            if (oldItem != null && oldItem.getType() != Material.AIR && oldItem.hasItemMeta()) {
                String oldId = oldItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                if (itemId.equals(oldId)) {
                    // 同じIDでも、メタ（例えば弾数）が違う可能性はあるが、
                    // ここでは「銃の種類が変わったか」を主眼に置く。
                    // もし「全く同じアイテムインスタンス」かチェックしたいなら item.equals(oldItem)
                    itemChanged = !item.equals(oldItem);
                }
            }

            sm.getContext().setItem(item);
            sm.getContext().setStats(def.gunStats);

            if (itemChanged) {
                sm.reset();
            }

            // Also ensure task is running
            if (!stateTasks.containsKey(player.getUniqueId())) {
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
                if (stateMachines.containsKey(uuid)) {
                    stateMachines.get(uuid).update();
                } else {
                    this.cancel();
                }

                // ティックの終わりに現在地を記録（次のティックでの移動判定用）
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
        return pdc.has(PDCKeys.ITEM_ID, PDCKeys.STRING);
    }

    // startReload and helper methods removed (moved to ReloadingState)

    private void applySuppression(Player victim, Location bulletLoc) {
        // 1. 視覚的ペナルティ (短時間の暗闇と鈍足)
        // 1.21ならDARKNESSが非常に「視界が狭まる」感じでリアルです
        victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false, false));

        // 2. ニアミス音 (弾がかすめる音)
        // 左右どちらを通り過ぎたか分かるように、弾の位置で音を鳴らす
        victim.playSound(bulletLoc, Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.8f);

        // 3. 心音（パニック表現）
        // 既に鳴っている場合は重ならないように注意
        victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f);

        // 4. 画面を少し揺らす（カメラシェイク）
        // 垂直方向の反動をわずかに与えることで衝撃を表現
        Location loc = victim.getLocation();
        victim.setRotation(loc.getYaw(), loc.getPitch() + (float) (Math.random() * 2 - 1));
    }

    @EventHandler
    public void onKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (event.getEntity().hasMetadata("no_knockback")) {
            event.setCancelled(true);
            event.getEntity().removeMetadata("no_knockback", plugin);
        }
    }

    private void handleDamage(LivingEntity victim, LivingEntity shooter, double baseDamage, int ammoClass,
            Location hitLoc) {
        double finalDamage = baseDamage;

        // 身長による座標基準の計算
        double footY = victim.getLocation().getY();
        double headY = victim.getEyeLocation().getY();
        double hitY = hitLoc.getY();
        double height = headY - footY; // 目の高さまでの距離 (約1.62m for player)

        // 部位判定
        boolean isHeadshot = hitY >= (headY - 0.25);
        boolean isLegShot = hitY <= (footY + (height * 0.45)); // 足元から45%の高さ以下なら足とみなす

        int armorClass = 0;

        if (isHeadshot) {
            // ヘッドショット (1.2倍 + ヘルメット参照)
            finalDamage *= 1.2;
            armorClass = getArmorClassFromSlot(victim, EquipmentSlot.HEAD);
        } else if (isLegShot) {
            // レッグショット (ダメージ60% + アーマー無視)
            // 「そのままダメージを与えられる」 = アーマー計算をスキップして確定貫通扱いとする
            finalDamage *= 0.6;
            // armorClass = 0 のまま (貫通確定)
        } else {
            // ボディ (アーマー参照)
            armorClass = getArmorClassFromSlot(victim, EquipmentSlot.CHEST);
        }

        // アーマー貫通判定 (足はアーマー0なので必ず貫通する)
        boolean isPenetrated = calculatePenetration(ammoClass, armorClass);
        Player shooterPlayer = (shooter instanceof Player p) ? p : null;

        if (!isPenetrated) {
            // 貫通失敗: ダメージを大幅にカット (例: 10~20%程度しか通らない)
            finalDamage *= 0.15;
            playConfiguredSound(shooterPlayer, hitLoc, "hit-sounds.headshot", "ENTITY_ARROW_HIT_PLAYER", 1.0f, 1.0f);
            shooter.sendMessage("§7[!] 弾が装甲に弾かれました");
        } else {
            // 貫通成功
            playConfiguredSound(shooterPlayer, hitLoc, "hit-sounds.headshot", "ENTITY_ARROW_HIT_PLAYER", 1.0f, 1.0f);
            
            // 更にヘッドショットだった場合は別音を追加で鳴らす（既存処理維持ならここでヘッドショットの音も鳴らす）
            if (isHeadshot) {
                //playConfiguredSound(shooterPlayer, hitLoc, "hit-sounds.headshot", "ENTITY_ARROW_HIT_PLAYER", 1.0f, 1.0f);
            }
        }

        ScavController controller = ScavSpawner.getController(shooter.getUniqueId());
        if (controller != null) {
            float reward = 0.0f;

            // 1. ダメージ量に応じた基本報酬
            reward += (float) (finalDamage * 0.15);

            // 2. ヘッドショットボーナス (強化)
            if (isHeadshot) {
                reward += 1.2f;
                shooter.sendMessage("§a[AI] Nice Headshot! Learning...");
            }

            // 3. 移動射撃ボーナス (回避行動中に当てた場合)
            int[] lastActions = controller.getBrain().getLastActions();
            if (lastActions != null && lastActions[0] >= 3) {
                reward += 0.5f;
                shooter.sendMessage("§b[AI] Mobile Hit Bonus! Learning...");
            }

            // 4. 距離ボーナス (15m付近の適正距離での命中)
            double dist = shooter.getLocation().distance(victim.getLocation());
            if (dist >= 10 && dist <= 20) {
                reward += 0.3f;
            }

            controller.getBrain().reward(reward); // AIに学習させる
        }

        // 最終ダメージの適用 (ノックバックを一時的に無効化)
        victim.setMetadata("no_knockback", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        victim.damage(finalDamage, shooter);
    }

    private void playConfiguredSound(Player player, Location loc, String configKey, String defaultSoundName, float volume, float pitch) {
        String soundName = plugin.getConfig().getString(configKey, defaultSoundName);
        if (soundName == null || soundName.isEmpty()) return;

        // プレイヤーが指定されている場合は、耳元（現在地）で鳴らすことで距離に関わらず聞こえるようにする
        Location playAt = (player != null) ? player.getLocation() : loc;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            if (player != null) {
                player.playSound(playAt, sound, volume, pitch);
            } else {
                playAt.getWorld().playSound(playAt, sound, volume, pitch);
            }
        } catch (IllegalArgumentException e) {
            // Not a built-in sound, play as a custom resource pack sound
            if (player != null) {
                player.playSound(playAt, soundName, volume, pitch);
            } else {
                playAt.getWorld().playSound(playAt, soundName, volume, pitch);
            }
        }
    }

    private boolean calculatePenetration(int ammo, int armor) {
        if (armor <= 0)
            return true; // 無装甲は100%貫通

        double chance = 0.0;

        if (ammo > armor) {
            // 弾のクラスが高い: ほぼ貫通 (95%)
            chance = 0.95;
        } else if (ammo == armor) {
            // クラスが同じ: 高確率で貫通 (70%)
            chance = 0.70;
        } else {
            // 防具の方が強い: 低確率で貫通 (15%)
            chance = 0.15;
        }

        return Math.random() < chance;
    }

    private int getArmorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return 0;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        // アーマークラス (int) を取得。PDCになければ0
        return pdc.getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
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