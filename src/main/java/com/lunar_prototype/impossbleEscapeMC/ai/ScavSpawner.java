package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScavSpawner implements Listener {

    private final ImpossbleEscapeMC plugin;
    private final GunListener gunListener;
    // UUIDごとにAIコントローラーを保持
    private static final Map<UUID, ScavController> controllers = new HashMap<>();

    public ScavSpawner(ImpossbleEscapeMC plugin, GunListener gunListener) {
        this.plugin = plugin;
        this.gunListener = gunListener;

        // AIの更新タスクを開始 (例: 5ティック = 0.25秒ごと)
        startAiTick();
    }

    /**
     * 指定した座標にAI SCAVをスポーンさせる
     */
    public void spawnScav(Location loc) {
        Mob scav = (Mob) loc.getWorld().spawnEntity(loc, EntityType.PILLAGER); // または独自のEntityType

        // タルコフ風の装備設定 (必要に応じてItemRegistryから取得)
        setupScavEquipment(scav);

        // コントローラーを生成して登録
        ScavController controller = new ScavController(scav, gunListener);
        controllers.put(scav.getUniqueId(), controller);

        Bukkit.getLogger().info("[SCAV] Spawned with AI: " + scav.getUniqueId());
    }

    private void setupScavEquipment(Mob scav) {
        // 1. スポーン時に持たせる銃の候補リスト
        String[] gunPool = {"ak74", "m4a1"};
        String randomGunId = gunPool[new java.util.Random().nextInt(gunPool.length)];

        // 2. ItemFactoryで銃を生成 (ここでPDCにAMMOやITEM_IDが書き込まれる)
        ItemStack gun = ItemFactory.create(randomGunId);
        if (gun != null) {
            scav.getEquipment().setItemInMainHand(gun);
            scav.getEquipment().setItemInMainHandDropChance(0.1f); // プレイヤーが拾える確率(10%)
        }

        // 3. 防具のランダム装備
        setupRandomArmor(scav);
    }

    private void setupRandomArmor(Mob scav) {
        java.util.Random rand = new java.util.Random();
        var inv = scav.getEquipment();

        // アーマークラスを考慮して、バニラ素材を割り当て
        // GunListener.getArmorClass() がこれを見て貫通判定を行う
        Material[] helmets = {Material.IRON_HELMET, Material.CHAINMAIL_HELMET};
        Material[] chests = {Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.LEATHER_CHESTPLATE};

        inv.setHelmet(new ItemStack(helmets[rand.nextInt(helmets.length)]));
        inv.setChestplate(new ItemStack(chests[rand.nextInt(chests.length)]));

        // 防具のドロップ率も低めに設定
        inv.setHelmetDropChance(0.05f);
        inv.setChestplateDropChance(0.05f);
    }

    /**
     * 全てのSCAV AIを1ステップ進める
     */
    private void startAiTick() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 実行中にMapが変更されないよう、Iterator等で制御
                controllers.values().removeIf(controller -> {
                    if (controller.getScav().isDead() || !controller.getScav().isValid()) {
                        controller.getBrain().terminate(); // メモリ解放
                        return true;
                    }
                    controller.onTick();
                    return false;
                });
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }

    /**
     * GunListenerなどからAIインスタンスを取得するためのメソッド
     */
    public static ScavController getController(UUID uuid) {
        return controllers.get(uuid);
    }

    @EventHandler
    public void onScavDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (controllers.containsKey(uuid)) {
            ScavController controller = controllers.remove(uuid);
            if (controller != null) {
                // 1. 死亡時の大きなマイナス報酬（死なないように学習させる）
                controller.getBrain().reward(-5.0f);
                
                // 2. 成果をグローバル・ブレインに報告（保存）
                //controller.onDeath();
                
                // 3. メモリ解放
                controller.getBrain().terminate();
                Bukkit.getLogger().info("[SCAV] AI Terminated and Knowledge Saved: " + uuid);
            }
        }
    }

    /**
     * プラグイン終了時に全てのネイティブハンドルを解放する
     */
    public void cleanup() {
        for (ScavController controller : controllers.values()) {
            controller.onDeath(); // 終了時も一応保存を試みる
            controller.getBrain().terminate();
        }
        controllers.clear();
    }
}