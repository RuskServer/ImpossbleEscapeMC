package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.util.BloodEffect;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // 【追加】ダッシュ中は射撃不可
        if (player.isSprinting()) return;

        UUID uuid = player.getUniqueId();

        // 1. クリック時刻を更新
        lastRightClickMap.put(uuid, System.currentTimeMillis());

        // 2. 既にタスクが動いているなら無視
        if (activeTasks.containsKey(uuid)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || !"GUN".equalsIgnoreCase(def.type) || def.gunStats == null) return;

        // 3. 射撃ループ開始
        startShooting(player, pdc, def.gunStats);
    }

    // --- 左クリックでエイム切り替え (トグル) ---

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        // 左クリック (空気・ブロック両方)
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 銃を持っているか確認
        if (!isGun(item)) return;
        if (reloadingPlayers.contains(uuid)) return;

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
        if (task != null) task.cancel();
        lastRightClickMap.remove(uuid);
        consecutiveShots.remove(uuid);
    }

    private boolean isHoldingSameGun(Player player, PersistentDataContainer originalPdc) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return false;

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
                updateHasteEffect(player, holdingGun);
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
        if (!isGun(item)) return;

        // 通常プレイ中のドロップ（リロード）処理
        event.setCancelled(true);
        startReload(player, item);
    }

    // 【重要】タルコフ式反動ロジック
    private void executeShoot(Player player, PersistentDataContainer pdc, GunStats stats) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

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

            new BulletTask(player, damage,ammoClass).runTaskTimer(plugin, 0, 1);
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
     * PacketEventsを使用してプレイヤーに相対的な視点変更（リコイル）を送信します。
     * これにより、setRotationのようなカクつきがなくなり、マウス操作とリコイルが自然に合成されます。
     *
     * @param player 対象プレイヤー
     * @param yawRecoil    横方向の変化量 (右が正)
     * @param pitchRecoil  縦方向の変化量 (下が正。リコイルで上に跳ねるならマイナスを指定)
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
        if (newPitch < -90) newPitch = -90;
        if (newPitch > 90) newPitch = 90;

        // 4. 回転専用パケットの作成 (MC 1.19.4+)
        // 引数: yaw, pitch, onGround
        WrapperPlayServerPlayerRotation packet = new WrapperPlayServerPlayerRotation(
                newYaw,
                newPitch
        );

        // 5. 送信
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void spawnMuzzleFlash(Player player) {
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
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 220), 0.6f)
        );

        // --- 2. 広がる火花 (オレンジ/赤) ---
        // ここも少し前方に飛ばす距離を調整 (multiply 0.1 -> 0.2)
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc.clone().add(dir.clone().multiply(0.2)),
                4, 0.03, 0.03, 0.03, 0.1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 20), 0.4f)
        );

        // --- 3. 硝煙 (薄い灰色) ---
        player.getWorld().spawnParticle(
                Particle.DUST,
                muzzleLoc,
                2, 0.05, 0.05, 0.05, 0.02,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 0.8f)
        );

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
                    if (muzzleLoc.getBlock().getType() == Material.LIGHT) muzzleLoc.getBlock().setType(Material.AIR);
                }
            }.runTaskLater(plugin, 1);
        }
    }

    // 弾丸の挙動を管理するインナークラス
    private class BulletTask extends BukkitRunnable {
        private final Player shooter;
        private final double damage;
        private Location currentLoc;
        private Vector velocity;
        private int ticksAlive = 0;

        // 設定値 (後でYAMLから読み込めるようにすると良い)
        private final double GRAVITY = 0.015; // 1ティックあたりの落下量
        private final double SPEED = 6.0;    // 1ティックに進む距離
        private final int ammoClass;

        public BulletTask(Player shooter, double damage,int ammoClass) {
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
                    (e) -> e instanceof LivingEntity && !e.equals(shooter)
            );

            if (rayTrace != null) {
                if (rayTrace.getHitEntity() instanceof LivingEntity victim) {
                    Vector bulletDir = velocity.clone().normalize();
                    handleDamage(victim,shooter,damage,ammoClass,currentLoc);
                    BloodEffect.spawn(
                            rayTrace.getHitPosition().toLocation(currentLoc.getWorld()),
                            bulletDir,
                            damage
                    );
                    victim.getWorld().spawnParticle(Particle.BLOCK, rayTrace.getHitPosition().toLocation(currentLoc.getWorld()), 10, Material.REDSTONE_BLOCK.createBlockData());
                } else if (rayTrace.getHitBlock() != null) {
                    // 壁に着弾
                    rayTrace.getHitBlock().getWorld().playEffect(rayTrace.getHitPosition().toLocation(currentLoc.getWorld()), Effect.STEP_SOUND, rayTrace.getHitBlock().getType());
                }
                this.cancel();
            }
        }

        private void spawnTracer(Location from, Location to) {
            // 曳光弾らしく、進行方向に明るいパーティクルを配置
            shooter.getWorld().spawnParticle(Particle.FIREWORK, to, 1, 0, 0, 0, 0);
            shooter.getWorld().spawnParticle(Particle.SMALL_FLAME, to, 1, 0, 0, 0, 0.02);
        }
    }

    /**
     * プレイヤーの状態に合わせて手に持っている銃のモデルを更新します
     */
    private void updateWeaponModel(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGun(item)) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) return;

        // 1. 基本値 (YAMLから取得)
        int baseModelData = def.gunStats.customModelData;

        // 2. 状態による加算 (スプリント優先)
        int finalModelData = baseModelData;

        if (player.isSprinting()) {
            finalModelData += MODEL_ADD_SPRINT; // +2000
        } else if (aimingPlayers.contains(player.getUniqueId())) {
            finalModelData += MODEL_ADD_SCOPE;  // +1000
        }

        // 3. 適用 (変更が必要な場合のみ)
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData() && meta.getCustomModelData() == finalModelData) return;

        meta.setCustomModelData(finalModelData);
        item.setItemMeta(meta);
    }

    // ユーティリティ: 銃かどうか判定
    private boolean isGun(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(PDCKeys.ITEM_ID, PDCKeys.STRING);
    }

    /**
     * 腕振りを無効化するためのエフェクト管理
     */
    private void updateHasteEffect(Player player, boolean holdingGun) {
        if (holdingGun) {
            // 採掘速度上昇 255 (アンビエント=true, パーティクル=false で静かに付与)
            // 期間は無限ではないが、持ち替えるたびに更新されるので長めでOK
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 254, false, false, false));
        } else {
            // 銃を離したら解除
            if (player.hasPotionEffect(PotionEffectType.HASTE)) {
                // レベルが254(内部値)のものだけを消す（他の正規バフを消さないため）
                PotionEffect effect = player.getPotionEffect(PotionEffectType.HASTE);
                if (effect != null && effect.getAmplifier() == 254) {
                    player.removePotionEffect(PotionEffectType.HASTE);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 退出時は一応エフェクトを消しておく
        event.getPlayer().removePotionEffect(PotionEffectType.HASTE);
    }

    private void startReload(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (reloadingPlayers.contains(uuid)) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) return;

        // 1. インベントリから適切な弾を探す
        String targetCaliber = def.gunStats.caliber;
        ItemStack ammoItem = null;
        AmmoDefinition ammoData = null;

        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || !invItem.hasItemMeta()) continue;
            String id = invItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            AmmoDefinition foundAmmo = ItemRegistry.getAmmo(id);

            if (foundAmmo != null && foundAmmo.caliber.equalsIgnoreCase(targetCaliber)) {
                ammoItem = invItem;
                ammoData = foundAmmo;
                break;
            }
        }

        if (ammoItem == null) {
            player.sendMessage("§c一致する口径の弾薬がありません: " + targetCaliber);
            return;
        }

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int maxAmmo = def.gunStats.magSize;
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

        // 【QOL向上】弾が満タン、かつ現在装填中の弾と同じ種類ならリロード不要
        // 逆に満タンでも種類が違う(PS->BS)ならリロードを続行する
        if (currentAmmo >= maxAmmo && ammoData.id.equals(currentAmmoId)) return;

        reloadingPlayers.add(uuid);
        stopShooting(uuid);
        aimingPlayers.remove(uuid);

        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
        int totalTicks = Math.max(1, def.gunStats.reloadTime / 50);

        // クロージャで使用するためにfinal化（または実質的final）
        final ItemStack finalAmmoItem = ammoItem;
        final AmmoDefinition finalAmmoData = ammoData;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !player.getInventory().getItemInMainHand().equals(item)) {
                    reloadingPlayers.remove(uuid);
                    updateWeaponModel(player);
                    if (player.isOnline()) {
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent("§cReload Cancelled"));
                    }
                    this.cancel();
                    return;
                }

                elapsed++;

                // プログレスバー表示 (省略: 前回のWMC風)
                displayReloadBar(player, elapsed, totalTicks);

                if (elapsed >= totalTicks) {
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
                                player.getInventory().addItem(ejected).forEach((i, drop) ->
                                        player.getWorld().dropItemNaturally(player.getLocation(), drop));
                            }
                        }

                        // 4. 新しい弾の消費計算
                        int magSize = def.gunStats.magSize;
                        int available = finalAmmoItem.getAmount();
                        // 装填できる量は「マガジンサイズ」と「手持ちの弾数」の小さい方
                        int toLoad = Math.min(magSize, available);

                        // インベントリの弾を減らす
                        finalAmmoItem.setAmount(available - toLoad);

                        // 5. 銃のPDCを完全に更新 (弾数と弾薬IDの両方)
                        fPdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, toLoad);
                        fPdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, finalAmmoData.id);

                        currentItem.setItemMeta(finishedMeta);
                        ItemFactory.updateLore(currentItem);
                    }

                    reloadingPlayers.remove(uuid);
                    updateWeaponModel(player);
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent("§a§lRELOAD COMPLETE"));
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.2f);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * WMCスタイルのリロード進行状況をアクションバーに表示します
     * * @param player 表示対象のプレイヤー
     * @param elapsed 経過ティック数
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
        victim.setRotation(loc.getYaw(), loc.getPitch() + (float)(Math.random() * 2 - 1));
    }

    private void handleDamage(LivingEntity victim, Player shooter, double baseDamage, int ammoClass, Location hitLoc) {
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

        // 最終ダメージの適用
        victim.damage(finalDamage, shooter);
    }

    private boolean calculatePenetration(int ammo, int armor) {
        if (armor <= 0) return true; // 無装甲は100%貫通

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
        if (chest == null) return 0;

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
}