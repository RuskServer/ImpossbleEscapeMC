package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class ScavSpawner {

    private final ImpossbleEscapeMC plugin;
    private final GunListener gunListener;
    private final Random random = new Random();

    // SCAVが持つ可能性がある武器リスト (ItemRegistryのID)
    private static final String[] WEAPON_POOL = {
            "ak74",
            "m4a1",
    };

    // ヘルメットのバリエーション
    private static final Material[] HELMET_POOL = {
            Material.IRON_HELMET,
            Material.CHAINMAIL_HELMET,
            Material.LEATHER_HELMET,
            null // ノーヘル
    };

    // アーマー（チェストプレート）のバリエーション
    private static final Material[] ARMOR_POOL = {
            Material.IRON_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE,
            Material.LEATHER_CHESTPLATE,
            null // アーマーなし
    };

    public ScavSpawner(ImpossbleEscapeMC plugin, GunListener gunListener) {
        this.plugin = plugin;
        this.gunListener = gunListener;
    }

    /**
     * 指定した座標にAI搭載SCAVをスポーンさせる
     */
    public Mob spawnScav(Location loc) {
        // 1. エンティティの生成 (ゾンビをベースにする例)
        Mob scav = (Mob) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        // 2. 基本ステータスの設定
        scav.setCustomName("SCAV");
        scav.setCustomNameVisible(false);
        scav.setRemoveWhenFarAway(true);

        // 3. 装備のランダム付与
        applyRandomEquipment(scav);

        // 4. ScavController (AI) の起動
        // 毎チック実行するタスクとして登録
        ScavController controller = new ScavController(plugin, scav, gunListener);
        controller.runTaskTimer(plugin, 1L, 1L);

        return scav;
    }

    /**
     * SCAVにランダムな武器と防具を着せる
     */
    private void applyRandomEquipment(Mob scav) {
        EntityEquipment equip = scav.getEquipment();
        if (equip == null) return;

        // --- 武器の付与 ---
        String weaponId = WEAPON_POOL[random.nextInt(WEAPON_POOL.length)];
        ItemStack weapon = ItemFactory.create(weaponId);
        if (weapon != null) {
            equip.setItemInMainHand(weapon);
            equip.setItemInMainHandDropChance(0.1f); // 10%でドロップ
        }

        // --- 防具の付与 ---
        // ヘルメット
        Material helmetMat = HELMET_POOL[random.nextInt(HELMET_POOL.length)];
        if (helmetMat != null) {
            equip.setHelmet(new ItemStack(helmetMat));
            equip.setHelmetDropChance(0.05f);
        }

        // アーマー
        Material armorMat = ARMOR_POOL[random.nextInt(ARMOR_POOL.length)];
        if (armorMat != null) {
            equip.setChestplate(new ItemStack(armorMat));
            equip.setChestplateDropChance(0.05f);
        }

        // バニラのゾンビが勝手に装備を拾わないように設定
        scav.setCanPickupItems(false);
    }
}