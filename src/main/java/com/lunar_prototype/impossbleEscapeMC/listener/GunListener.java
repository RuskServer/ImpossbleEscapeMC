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
    private final Set<UUID> shootingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> consecutiveShots = new HashMap<>();

    // State machine storage
    private final Map<UUID, com.lunar_prototype.impossbleEscapeMC.animation.state.WeaponStateMachine> stateMachines = new HashMap<>();
    private final Map<UUID, BukkitRunnable> stateTasks = new HashMap<>();

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

        // 【追加】ダッシュ中は射撃不可
        if (player.isSprinting())
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
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || !"GUN".equalsIgnoreCase(def.type) || def.gunStats == null)
            return;

        // バニラのアイテム動作（クロスボウの発射など）をキャンセル
        event.setCancelled(true);

        // 3. 射撃ループ開始
        startShooting(player, pdc, def.gunStats);
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
        // Reset state on switch? Or maintain?
        // Usually creating a new machine or resetting current one is best.
        // Let updateWeaponModel handle it.
        new BukkitRunnable() {
            @Override
            public void run() {
                updateWeaponModel(event.getPlayer());
            }
        }.runTaskLater(plugin, 1);
    }

    private void startShooting(Player player, PersistentDataContainer pdc, GunStats stats) {
        UUID uuid = player.getUniqueId();

        consecutiveShots.put(uuid, 0);

        long intervalTicks = Math.max(1, 1200 / stats.rpm);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // --- 離したことの検知ロジック ---
                long lastClick = lastRightClickMap.getOrDefault(uuid, 0L);
                long now = System.currentTimeMillis();

                // マイクラの右クリック連打（長押し時）は約200ms間隔
                // 300ms以上イベントが来なければ「指を離した」とみなす
                if (now - lastClick > 300) {
                    stopShooting(uuid);
                    return;
                }

                // その他の停止条件
                if (!player.isOnline() || player.isDead() || !isHoldingSameGun(player, pdc)) {
                    stopShooting(uuid);
                    return;
                }

                // 射撃実行
                executeShoot(player, pdc, stats);

                // セミオートの場合は1回で終了
                if ("SEMI".equalsIgnoreCase(stats.fireMode)) {
                    stopShooting(uuid);
                }
            }
        };

        activeTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0, intervalTicks);
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

    // 武器を切り替えたら強制停止
    @EventHandler
    public void onItemChange(PlayerItemHeldEvent event) {
        shootingPlayers.remove(event.getPlayer().getUniqueId());

        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = player.getInventory().getItemInMainHand();
                boolean holdingGun = isGun(item);

                updateWeaponModel(player);
            }
        }.runTaskLater(plugin, 1);
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

        // 1. 実物のアイテムから弾数を取得
        int currentAmmo = itemPdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);

        if (currentAmmo <= 0) {
            stopShooting(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 0.5f);
            return;
        }

        // 2. 弾数を減らして「メタデータ」に保存
        currentAmmo--;
        itemPdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, currentAmmo);

        // 3. 【重要】ここが抜けていました：メタデータを実物のアイテムにセットし直す
        item.setItemMeta(meta);

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
        String ammoId = itemPdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
        AmmoDefinition ammoDef = ItemRegistry.getAmmo(ammoId);

        if (ammoDef != null) {
            int ammoClass = ammoDef.ammoClass;

            new BulletTask(player, damage, ammoClass).runTaskTimer(plugin, 0, 1);
        }

        // 縦反動: 銃は跳ね上がる(視線は上に行く)ため、ピッチはマイナス方向へ動かす
        float recoilPitch = (float) -finalVerticalRecoil;

        // 横反動: そのまま適用
        float recoilYaw = (float) horizontalRecoil;

        // 相対パケットを送信
        sendRecoilPacket(player, recoilYaw, recoilPitch);

        String soundName = stats.shotSound;

        try {
            // Bukkit標準のサウンドにあるかチェック
            Sound standardSound = Sound.valueOf(soundName.toUpperCase());
            player.getWorld().playSound(player.getLocation(), standardSound, 1.0f, 1.8f);
        } catch (IllegalArgumentException e) {
            // 標準にない場合はカスタムサウンド(リソースパック)として再生
            player.getWorld().playSound(player.getLocation(), soundName, 1.0f, 1.8f);
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
        shooter.getWorld().playSound(shooter.getLocation(), stats.shotSound, 1.0f, 1.8f);

        // 2. 弾丸の生成
        BulletTask task = new BulletTask(shooter, stats.damage, ammoClass);

        // 3. モブらしい「ガバガバな精度」を再現するためにベクトルを少しずらす
        if (inaccuracy > 0) {
            Vector spread = new Vector(
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy);
            task.velocity.add(spread).normalize().multiply(6.0); // SPEEDに合わせて再加速
        }

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
            // 【修正】エイム時：距離を 1.2 -> 2.2 に延長
            // これで銃口に埋まることなく、先端から出ているように見えます
            muzzleLoc = eye.clone().add(dir.clone().multiply(2.2));
        } else {
            // 腰溜め時：右下オフセット + 距離1.0
            Vector offset = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.3);
            offset.add(new Vector(0, -0.2, 0));
            muzzleLoc = eye.clone().add(dir.clone().multiply(1.0)).add(offset);
        }

        // --- 1. 芯の部分 (非常に明るい黄色/白) ---
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc,
                3, 0.01, 0.01, 0.01, 0.05,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 220), 0.6f));

        // --- 2. 広がる火花 (オレンジ/赤) ---
        // ここも少し前方に飛ばす距離を調整 (multiply 0.1 -> 0.2)
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc.clone().add(dir.clone().multiply(0.2)),
                4, 0.03, 0.03, 0.03, 0.1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 20), 0.4f));

        // --- 3. 硝煙 (薄い灰色) ---
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc,
                2, 0.05, 0.05, 0.05, 0.02,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 0.8f));

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

    // 弾丸の挙動を管理するインナークラス
    private class BulletTask extends BukkitRunnable {
        private final LivingEntity shooter;
        private final double damage;
        private Location currentLoc;
        private Vector velocity;
        private int ticksAlive = 0;

        // 設定値 (後でYAMLから読み込めるようにすると良い)
        private final double GRAVITY = 0.015; // 1ティックあたりの落下量
        private final double SPEED = 6.0; // 1ティックに進む距離
        private final int ammoClass;

        public BulletTask(LivingEntity shooter, double damage, int ammoClass) {
            this.shooter = shooter;
            this.damage = damage;
            this.currentLoc = shooter.getEyeLocation();
            this.velocity = shooter.getEyeLocation().getDirection().multiply(SPEED);
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

            // 当たり判定 (ブロック & エンティティ)
            var rayTrace = currentLoc.getWorld().rayTrace(
                    prevLoc,
                    velocity,
                    SPEED,
                    FluidCollisionMode.NEVER,
                    true,
                    0.2,
                    (e) -> e instanceof LivingEntity && !e.equals(shooter));

            if (rayTrace != null) {
                if (rayTrace.getHitEntity() instanceof LivingEntity victim) {
                    Vector bulletDir = velocity.clone().normalize();
                    handleDamage(victim, shooter, damage, ammoClass, currentLoc);
                    BloodEffect.spawn(
                            rayTrace.getHitPosition().toLocation(currentLoc.getWorld()),
                            bulletDir,
                            damage);
                    victim.getWorld().spawnParticle(Particle.BLOCK,
                            rayTrace.getHitPosition().toLocation(currentLoc.getWorld()), 10,
                            Material.REDSTONE_BLOCK.createBlockData());
                } else if (rayTrace.getHitBlock() != null) {
                    // 壁に着弾
                    rayTrace.getHitBlock().getWorld().playEffect(
                            rayTrace.getHitPosition().toLocation(currentLoc.getWorld()), Effect.STEP_SOUND,
                            rayTrace.getHitBlock().getType());
                }
                this.cancel();
            }
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

        // Check if gun changed

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
            sm.getContext().setItem(item);
            sm.getContext().setStats(def.gunStats);
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
        stateMachines.remove(uuid);
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

    private void handleDamage(LivingEntity victim, LivingEntity shooter, double baseDamage, int ammoClass,
            Location hitLoc) {
        double finalDamage = baseDamage;

        // 1. ヘッドショット判定 (1.2倍)
        // 目の高さと着弾点の距離が近ければヘッドショットとみなす
        boolean isHeadshot = hitLoc.getY() >= victim.getEyeLocation().getY() - 0.25;
        if (isHeadshot) {
            finalDamage *= 1.2;
            victim.getWorld().spawnParticle(Particle.CRIT, hitLoc, 15, 0.1, 0.1, 0.1, 0.2);
        }

        // 2. 防具貫通判定
        int armorClass = getArmorClass(victim);
        boolean isPenetrated = calculatePenetration(ammoClass, armorClass);

        if (!isPenetrated) {
            // 貫通失敗: ダメージを大幅にカット (例: 10~20%程度しか通らない)
            finalDamage *= 0.15;
            victim.getWorld().playSound(hitLoc, Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.8f);
            shooter.sendMessage("§7[!] 弾が装甲に弾かれました");
        } else {
            // 貫通成功
            victim.getWorld().playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.5f);
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

        // 最終ダメージの適用
        victim.damage(finalDamage, shooter);
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

    private int getArmorClass(LivingEntity entity) {
        ItemStack chest = entity.getEquipment().getChestplate();
        if (chest == null)
            return 0;

        // バニラ装備への仮クラス割り当て
        return switch (chest.getType()) {
            case NETHERITE_CHESTPLATE -> 5;
            case DIAMOND_CHESTPLATE -> 4;
            case IRON_CHESTPLATE -> 3;
            case CHAINMAIL_CHESTPLATE -> 2;
            case LEATHER_CHESTPLATE -> 1;
            default -> 0;
        };
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