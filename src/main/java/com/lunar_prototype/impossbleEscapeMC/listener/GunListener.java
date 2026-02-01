package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
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
    private final Set<UUID> aimingPlayers = new HashSet<>();
    private final Set<UUID> reloadingPlayers = new HashSet<>();

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
        // 左クリック (空気・ブロック両方)
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 銃を持っているか確認
        if (!isGun(item))
            return;
        if (reloadingPlayers.contains(uuid))
            return;

        // イベントキャンセル (ブロック破壊や誤爆を防ぐ)
        event.setCancelled(true);
        // トグル処理: 既にエイム中なら解除、そうでなければ追加
        if (aimingPlayers.contains(uuid)) {
            aimingPlayers.remove(uuid);
            // エイム解除音 (例)
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
        } else {
            aimingPlayers.add(uuid);
            // エイム開始音 (例)
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f);
        }

        // モデル更新
        updateWeaponModel(player);
    }

    // --- ダッシュ状態の監視 ---
    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // ダッシュを開始しようとしている場合
        if (event.isSprinting()) {
            // 【追加】エイム解除
            aimingPlayers.remove(uuid);

            // 【追加】射撃も強制停止
            if (activeTasks.containsKey(uuid)) {
                stopShooting(uuid);
                // プレイヤーにフィードバック（任意: カチッという音など）
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 2.0f);
            }
        }

        // 1ティック後にモデル更新（ダッシュ/通常/エイムの見た目を反映）
        new BukkitRunnable() {
            @Override
            public void run() {
                updateWeaponModel(player);
            }
        }.runTaskLater(plugin, 1);
    }

    // --- 持ち替え時の処理 ---
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // 持ち替えたらエイムは解除するのが一般的 (お好みで削除可)
        aimingPlayers.remove(player.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                updateWeaponModel(player);
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

        // インベントリ（サバイバル、クリエイティブ、チェスト等）を開いている間は
        // 通常のドロップとして扱い、リロード処理をスキップする
        if (player.getOpenInventory().getType() != InventoryType.CRAFTING &&
                player.getOpenInventory().getType() != InventoryType.CREATIVE) {
            // ここでリターンすれば、イベントはキャンセルされず、アイテムが捨てられます
            return;
        }

        // 補足：標準のインベントリ画面は InventoryType.CRAFTING (2x2) です。
        // もし「インベントリを開いている時だけは捨てさせたい」のであれば、
        // 以下のように「トップインベントリ」が「自分の手元(CRAFTING/CREATIVE)」以外なら
        // 他の画面（チェスト等）を開いていると判断できます。
        // ※単純に「インベントリを閉じている時のみ」に絞るなら下記が確実です。

        // インベントリを開いていない状態（通常画面）の時だけリロードを実行
        // 通常画面時は getOpenInventory().getTopInventory() がプレイヤー自身の製作枠などになります。
        // 実装スタイルによりますが、以下の判定が一般的です。

        ItemStack item = event.getItemDrop().getItemStack();
        if (!isGun(item))
            return;

        // 通常プレイ中のドロップ（リロード）処理
        event.setCancelled(true);
        startReload(player, item);
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
     * @param shooter 撃つモブ
     * @param stats 銃のステータス
     * @param ammoClass 使用する弾のクラス
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
                    (Math.random() - 0.5) * inaccuracy
            );
            task.velocity.add(spread).normalize().multiply(6.0); // SPEEDに合わせて再加速
        }

        task.runTaskTimer(plugin, 0, 1);
    }

    /**
     * PacketEventsを使用してプレイヤーに相対的な視点変更（リコイル）を送信します。
     * これにより、setRotationのようなカクつきがなくなり、マウス操作とリコイルが自然に合成されます。
     *
     * @param player      対象プレイヤー
     * @param yawRecoil   横方向の変化量 (右が正)
     * @param pitchRecoil 縦方向の変化量 (下が正。リコイルで上に跳ねるならマイナスを指定)
     */
    private void sendRecoilPacket(Player player, float yawRecoil, float pitchRecoil) {
        // 1. 現在の視点を取得
        Location currentLoc = player.getLocation();
        float currentYaw = currentLoc.getYaw();
        float currentPitch = currentLoc.getPitch();

        // 2. 反動を加算 (Pitchはマイナスで上を向く)
        float newYaw = currentYaw + yawRecoil;
        float newPitch = currentPitch + pitchRecoil;

        // 3. Pitchの制限 (-90度〜90度)
        if (newPitch < -90)
            newPitch = -90;
        if (newPitch > 90)
            newPitch = 90;

        // 4. 回転専用パケットの作成 (MC 1.19.4+)
        // 引数: yaw, pitch, onGround
        WrapperPlayServerPlayerRotation packet = new WrapperPlayServerPlayerRotation(
                newYaw,
                newPitch);

        // 5. 送信
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void spawnMuzzleFlash(LivingEntity player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        UUID uuid = player.getUniqueId();

        boolean isAiming = aimingPlayers.contains(uuid);

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
                            0.6f);

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
    private final Map<UUID, PlayerAnimationTask> animTasks = new HashMap<>();

    /**
     * Enum for Animation State Priority
     */
    private enum AnimState {
        RELOAD(3),
        SPRINT(2),
        AIM(1),
        IDLE(0);

        final int priority;

        AnimState(int priority) {
            this.priority = priority;
        }
    }

    private void updateWeaponModel(Player player) {
        UUID uuid = player.getUniqueId();

        // 既にタスクがあれば、それは自律的に状態をチェックするので何もしなくて良い
        // タスクがない場合（持ち替え直後など）のみ起動
        if (!animTasks.containsKey(uuid)) {
            startAnimationTask(player);
        }
    }

    private void startAnimationTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopAnimation(player);

        PlayerAnimationTask task = new PlayerAnimationTask(player);
        task.runTaskTimer(plugin, 0, 1);
        animTasks.put(uuid, task);
    }

    private void stopAnimation(Player player) {
        UUID uuid = player.getUniqueId();
        if (animTasks.containsKey(uuid)) {
            animTasks.get(uuid).cancel();
            animTasks.remove(uuid);
        }
    }

    // --- 統合アニメーションタスク ---
    private class PlayerAnimationTask extends BukkitRunnable {
        private final Player player;
        private final UUID uuid;

        // 進行度 (0.0 ~ 1.0)
        private double aimProgress = 0.0;
        private double sprintProgress = 0.0;

        private int idleTick = 0;

        // 直前のモデル状態キャッシュ（無駄な更新を防ぐ用）
        private String lastModelKey = "";
        private int lastFrame = -1;

        public PlayerAnimationTask(Player player) {
            this.player = player;
            this.uuid = player.getUniqueId();

            // 初期状態の同期 (途中からタスク開始した場合のため)
            if (aimingPlayers.contains(uuid))
                aimProgress = 1.0;
            if (player.isSprinting())
                sprintProgress = 1.0;
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                stopAnimation(player);
                return;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isGun(item)) {
                stopAnimation(player);
                return;
            }

            // リロード中はアニメーション更新を停止（リロードタスクに任せる）
            // ただしProgressはリセットせず維持するか、あるいは即座に0にするか
            // ここでは描画のみスキップ
            if (reloadingPlayers.contains(uuid)) {
                lastModelKey = ""; // リロード明けに強制更新させるためリセット
                return;
            }

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def == null || def.gunStats == null)
                return;

            // 1. 目標状態の確認
            boolean isSprinting = player.isSprinting();
            boolean isAiming = aimingPlayers.contains(uuid);

            // スプリント中はエイム解除される仕様なので、もし両方フラグが立っててもスプリント優先でエイムは下がる
            if (isSprinting)
                isAiming = false;

            // 2. 進行度の更新
            updateProgress(def.gunStats, isAiming, isSprinting);

            // 3. 描画
            render(item, def.gunStats);

            idleTick++;
        }

        private void updateProgress(GunStats stats, boolean targetAim, boolean targetSprint) {
            // --- AIM ---
            double aimTimeTicks = (stats.adsTime > 0) ? (stats.adsTime / 50.0) : 1.0;
            double aimStep = 1.0 / aimTimeTicks;

            if (targetAim) {
                aimProgress = Math.min(1.0, aimProgress + aimStep);
            } else {
                aimProgress = Math.max(0.0, aimProgress - aimStep);
            }

            // --- SPRINT ---
            // スプリントのin/out時間は定義がないので、仮にAIMと同じか、固定値(0.2秒=4tick)くらいにする
            // ここでは固定値 5tick (0.25s) で遷移させるとスムーズ
            double sprintStep = 0.2;

            if (targetSprint) {
                sprintProgress = Math.min(1.0, sprintProgress + sprintStep);
            } else {
                sprintProgress = Math.max(0.0, sprintProgress - sprintStep);
            }
        }

        private void render(ItemStack item, GunStats stats) {
            GunStats.AnimationStats currentAnim = null;
            double progress = 0.0;
            boolean isLooping = false;

            // 優先度: SPRINT > AIM > IDLE
            // ただし逆再生(解除中)も見せるため、Progress > 0 で判定

            if (sprintProgress > 0.001) {
                // SPRINT
                currentAnim = stats.sprintAnimation;
                progress = sprintProgress;
                isLooping = false;
            } else if (aimProgress > 0.001) {
                // AIM
                currentAnim = stats.aimAnimation;
                progress = aimProgress;
                isLooping = false;
            } else {
                // IDLE
                currentAnim = stats.idleAnimation;
                isLooping = true;
            }

            // アニメーション設定がない場合のフォールバック（レガシー挙動）
            if (currentAnim == null) {
                renderLegacy(item, stats);
                return;
            }

            // フレーム計算
            int frameIndex;
            if (isLooping) {
                // IDLEループ
                if (currentAnim.fps > 0 && currentAnim.frameCount > 1) {
                    frameIndex = (int) ((idleTick / 20.0) * currentAnim.fps) % currentAnim.frameCount;
                } else {
                    frameIndex = 0;
                }
            } else {
                // Progress (0.0 - 1.0) -> Frame
                // frameCount=5 の場合、Indexは 0, 1, 2, 3, 4
                // 1.0 のとき 4 になるようにマッピング
                int maxFrame = Math.max(0, currentAnim.frameCount - 1);
                frameIndex = (int) Math.round(progress * maxFrame);
            }

            applyModel(item, currentAnim, frameIndex);
        }

        private void renderLegacy(ItemStack item, GunStats stats) {
            // 従来のCustomModelData加算方式
            int base = stats.customModelData;
            int add = 0;

            if (sprintProgress > 0.5)
                add = MODEL_ADD_SPRINT;
            else if (aimProgress > 0.5)
                add = MODEL_ADD_SCOPE;

            int finalData = base + add;

            // キャッシュチェック
            if (lastModelKey.equals("LEGACY") && lastFrame == finalData)
                return;

            item.resetData(DataComponentTypes.ITEM_MODEL); // 新方式が残っていたら消す

            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(finalData);
            item.setItemMeta(meta);

            lastModelKey = "LEGACY";
            lastFrame = finalData;
        }

        private void applyModel(ItemStack item, GunStats.AnimationStats anim, int frameIndex) {
            // キャッシュチェック (同じモデルの同じフレームなら更新しない)
            if (anim.model.equals(lastModelKey) && frameIndex == lastFrame)
                return;

            // リロードバグ対策:
            // 確実にITEM_MODELとCUSTOM_MODEL_DATAの両方をセットする

            // リロードバグ対策:
            // 確実にITEM_MODELとCUSTOM_MODEL_DATAの両方をセットする

            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(anim.model));
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                    .addFloat((float) frameIndex)
                    .addStrings(getAttachments(item))
                    .build());

            lastModelKey = anim.model;
            lastFrame = frameIndex;
        }
    }

    // ユーティリティ: 銃かどうか判定
    private boolean isGun(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta())
            return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(PDCKeys.ITEM_ID, PDCKeys.STRING);
    }

    private void startReload(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (reloadingPlayers.contains(uuid))
            return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null)
            return;

        // 1. インベントリから同じ口径の弾を「種類ごと」にグループ化して集める
        String targetCaliber = def.gunStats.caliber;
        Map<String, List<ItemStack>> ammoPool = new HashMap<>(); // AmmoID -> スタックリスト

        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || invItem.getType() == Material.AIR || !invItem.hasItemMeta()) continue;

            String id = invItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            AmmoDefinition foundAmmo = ItemRegistry.getAmmo(id);

            if (foundAmmo != null && foundAmmo.caliber.equalsIgnoreCase(targetCaliber)) {
                ammoPool.computeIfAbsent(id, k -> new ArrayList<>()).add(invItem);
            }
        }

        if (ammoPool.isEmpty()) {
            player.sendMessage("§c一致する口径の弾薬がありません: " + targetCaliber);
            return;
        }

        // 2. 最適な弾種（合計数が一番多いもの）を選択する
        String bestAmmoId = null;
        int maxCount = -1;
        for (Map.Entry<String, List<ItemStack>> entry : ammoPool.entrySet()) {
            int total = entry.getValue().stream().mapToInt(ItemStack::getAmount).sum();
            if (total > maxCount) {
                maxCount = total;
                bestAmmoId = entry.getKey();
            }
        }

        final AmmoDefinition finalAmmoData = ItemRegistry.getAmmo(bestAmmoId);
        final List<ItemStack> finalAmmoStacks = ammoPool.get(bestAmmoId); // 使う弾のスタックリスト

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int maxAmmo = def.gunStats.magSize;
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

        // 【QOL向上】弾が満タン、かつ現在装填中の弾と同じ種類ならリロード不要
        // 逆に満タンでも種類が違う(PS->BS)ならリロードを続行する
        if (currentAmmo >= maxAmmo && finalAmmoData.id.equals(currentAmmoId)) return;

        // リロードタイプ判定
        // CLOSEDボルト かつ 残弾0の場合 -> タクティカルリロード (コッキング動作含む)
        boolean isClosedBolt = "CLOSED".equalsIgnoreCase(def.gunStats.boltType);
        boolean isEmpty = currentAmmo <= 0;

        GunStats.AnimationStats animStats = def.gunStats.reloadAnimation; // デフォルトは通常リロード

        if (isClosedBolt && isEmpty && def.gunStats.tacticalReloadAnimation != null) {
            animStats = def.gunStats.tacticalReloadAnimation;
        }

        // リロード時間の計算
        int totalTicks;
        if (animStats != null && animStats.fps > 0) {
            // アニメーション時間から算出 (秒 -> Tick)
            double durationSeconds = (double) animStats.frameCount / animStats.fps;
            totalTicks = (int) Math.ceil(durationSeconds * 20);
        } else {
            // 設定がない場合はConfigの値 (ミリ秒 -> Tick)
            totalTicks = Math.max(1, def.gunStats.reloadTime / 50);
        }

        reloadingPlayers.add(uuid);
        stopShooting(uuid);
        aimingPlayers.remove(uuid);

        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);

        final GunStats.AnimationStats finalAnimStats = animStats;
        final int finalTotalTicks = totalTicks;

        if (finalAnimStats != null) {
            // リロード開始時にITEM_MODELをセット
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(finalAnimStats.model));
        }

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                // アニメーションでコンポーネントが書き換わるため、equals()ではなくIDで判定する
                if (!player.isOnline() || !isHoldingSameGun(player, pdc)) {
                    reloadingPlayers.remove(uuid);
                    // クリーンアップ: 自動復帰に任せる
                    updateWeaponModel(player);
                    if (player.isOnline()) {
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent("§cReload Cancelled"));
                    }
                    this.cancel();
                    return;
                }

                elapsed++;

                // アニメーション更新
                if (finalAnimStats != null) {
                    // 経過秒数 * FPS
                    int frameIndex = (int) ((elapsed / 20.0) * finalAnimStats.fps);

                    // タクティカルの場合は最後まで再生したら止める (ループしない)
                    if (frameIndex >= finalAnimStats.frameCount) {
                        frameIndex = finalAnimStats.frameCount - 1;
                    }

                    final int currentFrame = frameIndex;

                    player.getInventory().getItemInMainHand().setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                            CustomModelData.customModelData()
                                    .addFloat((float) currentFrame)
                                    .addStrings(getAttachments(player.getInventory().getItemInMainHand()))
                                    .build());
                }

                // プログレスバー表示 (省略: 前回のWMC風)
                displayReloadBar(player, elapsed, finalTotalTicks);

                if (elapsed >= finalTotalTicks) {
                    ItemStack currentItem = player.getInventory().getItemInMainHand();
                    if (isGun(currentItem)) {
                        ItemMeta finishedMeta = currentItem.getItemMeta();
                        PersistentDataContainer fPdc = finishedMeta.getPersistentDataContainer();

                        // 3. 残弾の排出 (Eject)
                        int leftover = fPdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
                        String oldAmmoId = fPdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

                        if (leftover > 0 && oldAmmoId != null) {
                            ItemStack ejected = ItemFactory.create(oldAmmoId);
                            if (ejected != null) {
                                ejected.setAmount(leftover);
                                // インベントリに戻す。入らなければドロップ
                                player.getInventory().addItem(ejected).forEach(
                                        (i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                            }
                        }

                        // 4. 【改良】複数スタックから弾を消費して装填
                        int magSize = def.gunStats.magSize;
                        int needed = magSize;
                        int loaded = 0;

                        // 収集しておいたスタックリストから順に引いていく
                        for (ItemStack ammoStack : finalAmmoStacks) {
                            if (needed <= 0) break;
                            if (ammoStack == null || ammoStack.getType() == Material.AIR) continue;

                            int amount = ammoStack.getAmount();
                            int take = Math.min(amount, needed);

                            ammoStack.setAmount(amount - take);
                            loaded += take;
                            needed -= take;
                        }

                        // 5. 銃のPDCを完全に更新 (弾数と弾薬IDの両方)
                        fPdc.set(PDCKeys.AMMO, PDCKeys.INTEGER,loaded);
                        fPdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, finalAmmoData.id);

                        currentItem.setItemMeta(finishedMeta);
                        ItemFactory.updateLore(currentItem);
                    }

                    reloadingPlayers.remove(uuid);
                    // クリーンアップ
                    // cleanupAnimation(currentItem);
                    updateWeaponModel(player);

                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent("§a§lRELOAD COMPLETE"));
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.2f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void cleanupAnimation(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return;

        item.resetData(DataComponentTypes.ITEM_MODEL);
        // CUSTOM_MODEL_DATAはupdateWeaponModelで上書きされるのでここではリセットしても良いし、
        // updateWeaponModelに任せても良いが、明示的に消すなら:
        // m.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        // ただし updateWeaponModel が int ベースの setCustomModelData を使う場合、
        // コンポーネント定義と int 定義が混ざるとアレなので、一旦リセット推奨。
        item.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
    }

    /**
     * WMCスタイルのリロード進行状況をアクションバーに表示します
     * * @param player 表示対象のプレイヤー
     * 
     * @param elapsed    経過ティック数
     * @param totalTicks 総リロードティック数
     */
    private void displayReloadBar(Player player, int elapsed, int totalTicks) {
        int barCount = 30; // Symbol_Amount
        int progress = (int) ((double) elapsed / totalTicks * barCount);

        // 残り秒数の計算 (1tick = 0.05s)
        double remainingSeconds = Math.max(0, (totalTicks - elapsed) / 20.0);
        String timeStr = String.format("%.1f", remainingSeconds);

        StringBuilder bar = new StringBuilder("§7Reloading ");

        // 左側 (完了分: Gray '|')
        bar.append("§7");
        for (int i = 0; i < progress; i++) {
            bar.append("|");
        }

        // 右側 (未完了分: Red '|')
        bar.append("§c");
        for (int i = progress; i < barCount; i++) {
            bar.append("|");
        }

        // 残り時間の表示
        bar.append(" §7").append(timeStr).append("s");

        // アクションバーへ送信
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(bar.toString()));
    }

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

    private void handleDamage(LivingEntity victim, LivingEntity shooter, double baseDamage, int ammoClass, Location hitLoc) {
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