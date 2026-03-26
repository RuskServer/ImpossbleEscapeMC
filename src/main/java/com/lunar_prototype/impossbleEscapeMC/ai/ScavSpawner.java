package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScavSpawner implements Listener {
    private static final Random RANDOM = new Random();
    private static final double BRAIN_LOW_CHANCE = 0.60;
    private static final double BRAIN_MID_CHANCE = 0.40;
    private static final double ARMOR_BOTH_EQUIP_CHANCE = 0.60;
    private static final double ARMOR_SINGLE_EQUIP_CHANCE = 0.35;
    // 完全未装備は低確率
    private static final double ARMOR_NONE_EQUIP_CHANCE = 0.05;
    private static final double ARMOR_CLASS_2_CHANCE = 0.45;
    private static final double ARMOR_CLASS_3_CHANCE = 0.45;
    // class4 はレア
    private static final double ARMOR_CLASS_4_CHANCE = 0.10;

    private final ImpossbleEscapeMC plugin;
    private final GunListener gunListener;
    // UUIDごとにAIコントローラーを保持
    private static final Map<UUID, ScavController> controllers = new ConcurrentHashMap<>();
    private static final Map<UUID, String> scavRaidSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, String> scavRaidMaps = new ConcurrentHashMap<>();
    private static final Map<String, Integer> raidClass4Count = new ConcurrentHashMap<>();

    public ScavSpawner(ImpossbleEscapeMC plugin, GunListener gunListener) {
        this.plugin = plugin;
        this.gunListener = gunListener;

        // AIの更新タスクを開始 (例: 5ティック = 0.25秒ごと)
        startAiTick();
    }

    /**
     * 指定した座標にAI SCAVをスポーンさせる
     * @return スポーンしたエンティティのUUID
     */
    public UUID spawnScav(Location loc) {
        return spawnScav(loc, null, null);
    }

    public UUID spawnScav(Location loc, String raidSessionId, String mapId) {
        Mob scav = (Mob) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        // 体力を40 (バニラの2倍) に固定、移動速度を 0.1 に設定
        var healthAttr = scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(40.0);
            scav.setHealth(40.0);
        }

        var speedAttr = scav.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.15);
        }

        // タルコフ風の装備設定
        setupScavEquipment(scav, raidSessionId);

        // 例: プレイヤー風に偽装（名前は"SCAV"）
        Disguise disguise = DisguiseAPI.getCustomDisguise("SCAV");

        // 適用
        DisguiseAPI.disguiseToAll(scav, disguise);

        // コントローラーを生成して登録
        ScavBrain.BrainLevel brainLevel = rollBrainLevel();
        ScavController controller = new ScavController(plugin, scav, gunListener, brainLevel);
        controllers.put(scav.getUniqueId(), controller);
        if (raidSessionId != null && !raidSessionId.isEmpty()) {
            scavRaidSessions.put(scav.getUniqueId(), raidSessionId);
        }
        if (mapId != null && !mapId.isEmpty()) {
            scavRaidMaps.put(scav.getUniqueId(), mapId);
        }

        Bukkit.getLogger().info("[SCAV] Spawned with AI: " + scav.getUniqueId() + " level=" + brainLevel);
        return scav.getUniqueId();
    }

    private ScavBrain.BrainLevel rollBrainLevel() {
        double roll = RANDOM.nextDouble();
        if (roll < BRAIN_LOW_CHANCE) {
            return ScavBrain.BrainLevel.LOW;
        }
        if (roll < BRAIN_LOW_CHANCE + BRAIN_MID_CHANCE) {
            return ScavBrain.BrainLevel.MID;
        }
        // 通常スポーンでHIGHは出さない（明示要件）
        return ScavBrain.BrainLevel.MID;
    }

    private void setupScavEquipment(Mob scav, String raidSessionId) {
        // 1. スポーン時に持たせる銃の候補リスト
        String[] gunPool = { "ak74", "m4a1", "m700", "mossberg_590" };
        String randomGunId = gunPool[new java.util.Random().nextInt(gunPool.length)];

        // 2. ItemFactoryで銃を生成 (ここでPDCにAMMOやITEM_IDが書き込まれる)
        ItemStack gun = ItemFactory.create(randomGunId);
        if (gun != null && gun.hasItemMeta()) {
            var meta = gun.getItemMeta();
            var pdc = meta.getPersistentDataContainer();
            var itemId = pdc.get(com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.ITEM_ID, com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.STRING);
            var def = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.get(itemId);
            
            if (def != null && def.maxDurability > 0) {
                java.util.Random rand = new java.util.Random();
                double roll = rand.nextDouble();
                double durabilityPercentage;

                if (roll < 0.05) {
                    // 5%の確率で 75% - 85% (約80%)
                    durabilityPercentage = 0.75 + (rand.nextDouble() * 0.1);
                } else {
                    // 95%の確率で 30% - 50%
                    durabilityPercentage = 0.3 + (rand.nextDouble() * 0.2);
                }

                int newDurability = (int) (def.maxDurability * durabilityPercentage);
                pdc.set(com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.DURABILITY, com.lunar_prototype.impossbleEscapeMC.util.PDCKeys.INTEGER, newDurability);
                gun.setItemMeta(meta);
                
                // バー表示を同期させる
                ItemFactory.updateLore(gun);
            }

            scav.getEquipment().setItemInMainHand(gun);
            scav.getEquipment().setItemInMainHandDropChance(0.1f); // プレイヤーが拾える確率(10%)
        }

        // 3. 防具のランダム装備
        setupRandomArmor(scav, raidSessionId);
    }

    private void setupRandomArmor(Mob scav, String raidSessionId) {
        EntityEquipment inv = scav.getEquipment();
        if (inv == null) {
            return;
        }

        // まずは全個体を空にして、未装備個体も自然に混ざるようにする
        inv.setHelmet(null);
        inv.setChestplate(null);

        double equipRoll = RANDOM.nextDouble();
        boolean equipBoth = equipRoll < ARMOR_BOTH_EQUIP_CHANCE;
        boolean equipSingle = !equipBoth && equipRoll < (ARMOR_BOTH_EQUIP_CHANCE + ARMOR_SINGLE_EQUIP_CHANCE);
        boolean equipNone = !equipBoth && !equipSingle;

        if (equipNone || !hasValidArmorEquipRateConfig()) {
            inv.setHelmetDropChance(0.0f);
            inv.setChestplateDropChance(0.0f);
            return;
        }

        int armorClass = rollArmorClass(raidSessionId);
        List<ItemDefinition> classArmors = ItemRegistry.getArmorItemsByClass(armorClass);
        Map<Integer, List<ItemDefinition>> armorsByClass = ItemRegistry.getArmorItemsGroupedByClass();

        String helmetId = pickArmorIdBySlot(classArmors, true);
        String chestId = pickArmorIdBySlot(classArmors, false);

        // 指定クラスに該当スロットが無い場合のフォールバック
        if (helmetId == null) {
            helmetId = pickArmorIdBySlot(armorsByClass.getOrDefault(2, Collections.emptyList()), true);
        }
        if (helmetId == null) {
            helmetId = pickArmorIdBySlot(armorsByClass.getOrDefault(3, Collections.emptyList()), true);
        }
        if (chestId == null) {
            chestId = pickArmorIdBySlot(armorsByClass.getOrDefault(2, Collections.emptyList()), false);
        }
        if (chestId == null) {
            chestId = pickArmorIdBySlot(armorsByClass.getOrDefault(3, Collections.emptyList()), false);
        }

        boolean equipHelmetOnly = equipSingle && RANDOM.nextBoolean();
        boolean equipChestOnly = equipSingle && !equipHelmetOnly;

        if (helmetId != null && (equipBoth || equipHelmetOnly)) {
            ItemStack helmet = ItemFactory.create(helmetId);
            if (helmet != null) {
                inv.setHelmet(helmet);
            }
        }
        if (chestId != null && (equipBoth || equipChestOnly)) {
            ItemStack chest = ItemFactory.create(chestId);
            if (chest != null) {
                inv.setChestplate(chest);
            }
        }

        // 防具のドロップ率は低め
        inv.setHelmetDropChance(inv.getHelmet() != null ? 0.05f : 0.0f);
        inv.setChestplateDropChance(inv.getChestplate() != null ? 0.05f : 0.0f);
    }

    private int rollArmorClass(String raidSessionId) {
        double roll = RANDOM.nextDouble();
        if (roll < ARMOR_CLASS_2_CHANCE) {
            return 2;
        }
        if (roll < ARMOR_CLASS_2_CHANCE + ARMOR_CLASS_3_CHANCE) {
            return 3;
        }
        if (roll < ARMOR_CLASS_2_CHANCE + ARMOR_CLASS_3_CHANCE + ARMOR_CLASS_4_CHANCE) {
            // クラス4の制限チェック
            if (raidSessionId != null && !raidSessionId.isEmpty()) {
                int currentCount = raidClass4Count.getOrDefault(raidSessionId, 0);
                if (currentCount < 2) {
                    raidClass4Count.put(raidSessionId, currentCount + 1);
                    return 4;
                } else {
                    // 上限に達した場合はクラス3へフォールバック
                    return 3;
                }
            }
            return 4;
        }
        return 3;
    }

    private String pickArmorIdBySlot(List<ItemDefinition> armors, boolean helmet) {
        if (armors == null || armors.isEmpty()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        for (ItemDefinition def : armors) {
            if (def == null || def.id == null || def.armorStats == null) {
                continue;
            }
            if (matchesSlot(def, helmet)) {
                candidates.add(def.id);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private boolean matchesSlot(ItemDefinition def, boolean helmet) {
        String slot = def.armorStats.slot != null ? def.armorStats.slot.toUpperCase() : "";
        if (helmet) {
            if ("HEAD".equals(slot) || "HELMET".equals(slot)) {
                return true;
            }
            return containsIgnoreCase(def.id, "helmet") || containsIgnoreCase(def.material, "HELMET");
        }
        if ("CHEST".equals(slot) || "CHESTPLATE".equals(slot)) {
            return true;
        }
        return containsIgnoreCase(def.id, "chestplate") || containsIgnoreCase(def.material, "CHESTPLATE");
    }

    private boolean containsIgnoreCase(String value, String token) {
        return value != null && value.toLowerCase().contains(token.toLowerCase());
    }

    private boolean hasValidArmorEquipRateConfig() {
        double total = ARMOR_BOTH_EQUIP_CHANCE + ARMOR_SINGLE_EQUIP_CHANCE + ARMOR_NONE_EQUIP_CHANCE;
        return Math.abs(total - 1.0) < 0.000001;
    }

    /**
     * 指定したUUIDのSCAVを削除する (非アクティブ化用)
     */
    public void removeScav(UUID uuid) {
        ScavController controller = controllers.remove(uuid);
        scavRaidSessions.remove(uuid);
        scavRaidMaps.remove(uuid);
        if (controller != null) {
            if (controller.getScav() != null && !controller.getScav().isDead()) {
                controller.getScav().remove();
            }
            controller.terminate();
        }
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
                        controller.terminate(); // メモリ解放 & チャンク解放
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

    public static String getRaidSessionId(UUID uuid) {
        return scavRaidSessions.get(uuid);
    }

    public static String getRaidMapId(UUID uuid) {
        return scavRaidMaps.get(uuid);
    }

    /**
     * SCAVが死亡したときに関連するゲーム内処理（キル通知、経験値付与、AI終了、レイド記録、遺体生成、バニラドロップ抑制）を行う。
     *
     * <p>動作概要:
     * <ul>
     *   <li>被害者を殺したエンティティが別のSCAVであれば、そのSCAVのコントローラにonKillを通知する。</li>
     *   <li>死亡したエンティティが登録済みのSCAVであれば、そのSCAVのコントローラとレイド関連メタデータを削除する。</li>
     *   <li>キラーがプレイヤーの場合は50 EXPを付与する。SCAVがレイドに紐づきかつキラーがレイド参加中であればレイドモジュール側で処理し（付与はレイド終了時扱い）、そうでなければLevelModuleに直接経験値を追加する。</li>
     *   <li>該当コントローラを終了させログ出力を行う。</li>
     *   <li>レイドセッションが存在すればAiRaidLoggerに「DIED」イベントを送信する。</li>
     *   <li>プラグインのレイドモジュールにSCAV死亡を通知する。</li>
     *   <li>バニラのドロップを消去し、コープスマネージャで遺体を生成する。</li>
     * </ul>
     *
     * @param event 死亡したエンティティに関するイベント
     */
    @EventHandler
    public void onScavDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        
        // --- 1. SCAVが誰かを殺したかチェック ---
        LivingEntity killer = victim.getKiller();
        if (killer != null) {
            ScavController killerController = controllers.get(killer.getUniqueId());
            if (killerController != null) {
                killerController.onKill(victim);
            }
        }

        // --- 2. 死んだのがSCAV自身かチェック ---
        UUID uuid = victim.getUniqueId();
        if (controllers.containsKey(uuid)) {
            ScavController controller = controllers.remove(uuid);
            String raidSessionId = scavRaidSessions.remove(uuid);
            String raidMapId = scavRaidMaps.remove(uuid);

            // キラーがプレイヤーなら経験値付与 (50 EXP)
            if (killer instanceof org.bukkit.entity.Player killerPlayer) {
                boolean isRaidScav = raidSessionId != null && raidMapId != null;
                boolean killerInRaid = plugin.getRaidModule() != null && plugin.getRaidModule().isInRaid(killerPlayer);

                if (isRaidScav && killerInRaid) {
                    plugin.getRaidModule().onScavKilledByPlayer(raidMapId, uuid, killerPlayer.getUniqueId(), controller.getBrain().getBrainLevel());
                    killerPlayer.sendMessage(Component.text("§a+EXP Kill ※レイド終了時に付与"));
                } else {
                    com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                            plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
                    if (levelModule != null) {
                        levelModule.addExperience(killerPlayer.getUniqueId(), 50);
                        killerPlayer.sendMessage(Component.text("§a+50 EXP (SCAV Kill)"));
                    }
                }
            }
            if (controller != null) {
                controller.terminate();
                Bukkit.getLogger().info("[SCAV] AI Terminated: " + uuid);
            }

            if (raidSessionId != null && plugin.getAiRaidLogger() != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("killerId", killer != null ? killer.getUniqueId().toString() : null);
                payload.put("cause", "ENTITY_DEATH");
                plugin.getAiRaidLogger().logEvent(raidSessionId, uuid, "DIED", payload);
            }

            plugin.getRaidModule().onScavDeath(uuid);

            // Spawn Corpse and cancel vanilla drops
            event.getDrops().clear();
            plugin.getCorpseManager().spawnCorpse(victim);
        }
    }

    public void cleanup() {
        for (ScavController controller : controllers.values()) {
            controller.terminate();
        }
        controllers.clear();
        scavRaidSessions.clear();
        scavRaidMaps.clear();
        raidClass4Count.clear();
    }
}
