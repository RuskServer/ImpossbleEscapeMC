package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ScavController extends BukkitRunnable {

    private final ImpossbleEscapeMC plugin;
    private final Mob entity;
    private final ScavBrain brain;
    private final ScavCombatLogic logic;
    private final GunListener gunListener;

    // 銃の状態管理
    private GunStats currentGunStats;
    private int currentAmmo = 0;
    private int maxAmmo = 30;
    private boolean isReloading = false;
    private int reloadTimer = 0;

    // 射撃制御
    private int fireCooldown = 0;
    private int burstCount = 0;

    // スキャン設定
    private static final int SCAN_RADIUS = 32;

    public ScavController(ImpossbleEscapeMC plugin, Mob entity, GunListener gunListener) {
        this.plugin = plugin;
        this.entity = entity;
        this.gunListener = gunListener;
        this.brain = new ScavBrain(); // Native Brainの初期化
        this.logic = new ScavCombatLogic();

        // 初期装備の解析
        updateGunStats();
    }

    private void updateGunStats() {
        ItemStack item = entity.getEquipment().getItemInMainHand();
        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            if (id != null) {
                // ItemRegistryから定義を取得 (GunStatsを保持)
                this.currentGunStats = ItemRegistry.get(id).gunStats;
                this.maxAmmo = this.currentGunStats.magSize;
                // 初回のみフル装填、以降は保持した値を優先すべきだが今回は簡易実装
                if (this.currentAmmo == 0) {
                    this.currentAmmo = this.maxAmmo;
                }
            }
        }
    }

    @Override
    public void run() {
        if (!entity.isValid() || entity.isDead()) {
            brain.dispose(); // Nativeメモリ解放
            this.cancel();
            return;
        }

        // 1. リロード処理 (GunListenerのロジックをSCAV用に簡易化して実行)
        if (isReloading) {
            handleReload();
            return; // リロード中は他の行動をしない
        }

        // 2. 環境スキャン (SensorProviderのロジックをここに統合)
        ScavContext context = scanEnvironment();

        // ターゲット取得 (CombatLogic内で再選定されるが、Controller側での移動制御にも使用)
        Player target = null;
        if (!context.environment.nearby_enemies.isEmpty()) {
            target = context.environment.nearby_enemies.get(0).playerInstance;
        }

        // 3. 思考 (Think) - Rust脳とLogicによる判断
        ScavDecision decision = logic.think(entity, brain, context, currentGunStats, currentAmmo);

        // 4. ボイス再生
        if (decision.communication.voice_line != null) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            entity.setCustomName("§c" + decision.communication.voice_line);
            entity.setCustomNameVisible(true);
        }

        // 5. 行動実行 (Act)
        executeAction(decision, target, context);

        // 射撃レート制御
        if (fireCooldown > 0) fireCooldown--;
    }

    /**
     * SensorProvider.java のロジックをベースに ScavContext を構築する
     */
    private ScavContext scanEnvironment() {
        ScavContext context = new ScavContext();

        // 1. 自身のステータス
        context.entity = new ScavContext.EntityState();
        context.entity.id = entity.getUniqueId().toString();
        double maxHp = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        context.entity.hp_pct = (int) ((entity.getHealth() / maxHp) * 100);
        context.entity.max_hp = (int) maxHp;
        context.entity.stance = "STANDING"; // MobにはStanceがないため仮置き

        // 2. 環境情報の収集
        context.environment = new ScavContext.EnvironmentState();
        List<Entity> nearby = entity.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);

        // 敵スキャン
        context.environment.nearby_enemies = scanEnemies(nearby);

        // 味方スキャン (今回は簡易的に自分と同じ種族を味方とする)
        context.environment.nearby_allies = scanAllies(nearby);

        // 遮蔽物スキャン (重い処理なのでターゲットがいる場合のみ、または間引いて実行推奨)
        if (!context.environment.nearby_enemies.isEmpty()) {
            context.environment.nearest_cover = findNearestCover(context.environment.nearby_enemies.get(0).playerInstance);
        }

        return context;
    }

    private List<ScavContext.EnemyInfo> scanEnemies(List<Entity> nearby) {
        return nearby.stream()
                .filter(e -> e instanceof Player)
                .map(e -> {
                    Player p = (Player) e;
                    ScavContext.EnemyInfo info = new ScavContext.EnemyInfo();
                    info.playerInstance = p;
                    info.dist = entity.getLocation().distance(p.getLocation());

                    // LoS判定 (SensorProvider準拠)
                    if (info.dist <= 15.0) {
                        info.in_sight = true; // 近距離は気配で察知
                    } else {
                        info.in_sight = entity.hasLineOfSight(p);
                    }

                    ItemStack mainHand = p.getInventory().getItemInMainHand();
                    info.holding = (mainHand != null) ? mainHand.getType().name() : "AIR";

                    double healthRatio = p.getHealth() / p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    info.health = healthRatio > 0.7 ? "high" : (healthRatio > 0.3 ? "mid" : "low");

                    return info;
                })
                .sorted(Comparator.comparingDouble(i -> i.dist)) // 近い順
                .collect(Collectors.toList());
    }

    private List<ScavContext.AllyInfo> scanAllies(List<Entity> nearby) {
        return nearby.stream()
                .filter(e -> e.getClass().equals(entity.getClass()) && e != entity) // 同じMob種族
                .map(e -> {
                    LivingEntity ally = (LivingEntity) e;
                    ScavContext.AllyInfo info = new ScavContext.AllyInfo();
                    info.dist = entity.getLocation().distance(ally.getLocation());
                    info.status = (ally.getHealth() / ally.getAttribute(Attribute.MAX_HEALTH).getValue() < 0.5) ? "WOUNDED" : "HEALTHY";
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * SensorProviderのfindBestCoverBlockロジックを移植
     */
    private ScavContext.CoverInfo findNearestCover(Player threat) {
        if (threat == null) return null;

        Location enemyLoc = threat.getLocation();
        Block bestCover = null;
        double minDistance = Double.MAX_VALUE;
        int radius = 8; // 探索範囲

        Location selfLoc = entity.getLocation();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = selfLoc.clone().add(x, y, z).getBlock();
                    if (!block.getType().isOccluding()) continue;

                    // 敵から隠れられる位置（ブロックの反対側）を計算
                    Vector dirToEnemy = enemyLoc.toVector().subtract(block.getLocation().toVector()).normalize();
                    Location hidingSpot = block.getLocation().add(dirToEnemy.multiply(-1.1));

                    // 安全か確認 (RayTrace)
                    if (isSafeSpot(hidingSpot, enemyLoc)) {
                        double dist = selfLoc.distance(hidingSpot);
                        if (dist < minDistance) {
                            minDistance = dist;
                            bestCover = block;
                        }
                    }
                }
            }
        }

        if (bestCover != null) {
            ScavContext.CoverInfo info = new ScavContext.CoverInfo();
            info.dist = minDistance;
            info.safety_score = 1.0;
            return info;
        }
        return null;
    }

    private boolean isSafeSpot(Location spot, Location enemyEye) {
        if (!spot.getBlock().isEmpty()) return false;
        RayTraceResult result = spot.getWorld().rayTraceBlocks(
                enemyEye,
                spot.toVector().subtract(enemyEye.toVector()).normalize(),
                enemyEye.distance(spot),
                FluidCollisionMode.NEVER,
                true
        );
        return result != null && result.getHitBlock() != null;
    }

    private void executeAction(ScavDecision d, Player target, ScavContext context) {
        // の定義に基づきアクションを実行
        switch (d.decision.action_type) {
            case "ATTACK", "RUSH" -> {
                if (target != null) {
                    performShooting(target);
                    faceTarget(target);

                    // 興奮状態やRUSH時は前に出る
                    if ("RUSH".equals(d.movement.strategy)) {
                        entity.setTarget(target);
                        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 2, 0.1, 0.1, 0.1, 0.05);
                    } else if ("STRAFE_ATTACK".equals(d.movement.strategy)) {
                        // 撃ちながら横移動
                        Vector dir = entity.getLocation().getDirection().crossProduct(new Vector(0,1,0)).normalize();
                        entity.setVelocity(dir.multiply(0.15));
                    }
                }
            }
            case "RELOAD" -> {
                // 隠れながらリロード
                if (context.environment.nearest_cover != null && target != null) {
                    navigateToCover(target);
                }
                startReload();
            }
            case "EVADE", "RETREAT" -> {
                if (target != null) {
                    // 遮蔽物があればそちらへ、なければバックステップ
                    if (context.environment.nearest_cover != null) {
                        navigateToCover(target);
                    } else {
                        // 敵と逆方向へ
                        Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
                        entity.setVelocity(away.multiply(0.4));
                    }
                }
            }
            case "OBSERVE" -> {
                // 待機・索敵 (首を振るなど)
                entity.setTarget(null);
            }
        }
    }

    private void navigateToCover(Player threat) {
        // 簡易実装: カバー位置計算を再利用して移動
        // 本来はPathfinderGoalを使うのが綺麗だが、setVelocity等で代用
        ScavContext.CoverInfo cover = findNearestCover(threat); // 再計算コスト注意
        // ここでは単純化
    }

    private void performShooting(Player target) {
        if (currentGunStats == null || fireCooldown > 0) return;
        if (currentAmmo <= 0) return;

        double inaccuracy = 0.05 + (brain.systemTemperature * 0.15);
        if (entity.getVelocity().length() > 0.1) inaccuracy += 0.1;

        gunListener.executeMobShoot(entity, currentGunStats, 1, inaccuracy);
        currentAmmo--;
        fireCooldown = Math.max(1, 1200 / currentGunStats.rpm);

        if ("BURST".equalsIgnoreCase(currentGunStats.fireMode)) {
            burstCount++;
            if (burstCount >= 3) {
                fireCooldown += 10;
                burstCount = 0;
            }
        }
    }

    private void startReload() {
        if (isReloading) return;
        isReloading = true;
        int reloadTicks = Math.max(20, currentGunStats.reloadTime / 50);
        this.reloadTimer = reloadTicks;
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
    }

    private void handleReload() {
        reloadTimer--;
        if (reloadTimer % 10 == 0) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.5f, 1.5f);
        }
        if (reloadTimer <= 0) {
            currentAmmo = maxAmmo;
            isReloading = false;
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.2f);
            brain.learn(0.5f);
        }
    }

    private void faceTarget(Player target) {
        Location loc = entity.getLocation();
        Vector dir = target.getLocation().toVector().subtract(loc.toVector()).normalize();
        Location lookLoc = loc.clone();
        lookLoc.setDirection(dir);
        entity.setRotation(lookLoc.getYaw(), lookLoc.getPitch());
    }
}