package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * データパック銃を持つSCAVの弾。込めている弾の種類と、予備の弾数。
 * <ul>
 *   <li>弾の種類はSCAVの強さに応じた貫通クラスの確率で選ぶ ({@link #CLASS_WEIGHTS})。その口径に無いクラスなら近いクラス</li>
 *   <li>撃つ側 ({@link com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunner}) は、最初のマガジンは満タンで受け取り、
 *       リロードのたびに予備から込める。予備が尽きると込められない</li>
 *   <li>倒された時、残った予備は弾アイテムとして死体に入る</li>
 * </ul>
 * 口径に合う弾が定義されていない銃のSCAVには割り当てない (今までどおり弾は尽きない)
 */
public final class ScavAmmoSupply {

    /** 予備のマガジン数 (最小〜最大) */
    private static final int MIN_SPARE_MAGAZINES = 1;
    private static final int MAX_SPARE_MAGAZINES = 3;
    /**
     * SCAVの強さごとの、弾の貫通クラス (1〜6) の重み。LOW は粗悪な弾から標準弾、MID は標準弾が中心、
     * HIGH は貫通弾が中心で、ごくまれに最新の弾 (クラス6) を持つ
     */
    private static final Map<ScavBrain.BrainLevel, double[]> CLASS_WEIGHTS = Map.of(
            //                                 class:  1     2     3     4     5     6
            ScavBrain.BrainLevel.LOW, new double[]{0.15, 0.30, 0.55, 0, 0, 0},
            ScavBrain.BrainLevel.MID, new double[]{0, 0.10, 0.55, 0.35, 0, 0},
            ScavBrain.BrainLevel.HIGH, new double[]{0, 0, 0, 0.50, 0.48, 0.02});

    private static final Map<UUID, ScavAmmoSupply> SUPPLIES = new ConcurrentHashMap<>();

    private final String ammoId;
    private int spare;

    private ScavAmmoSupply(String ammoId, int spare) {
        this.ammoId = ammoId;
        this.spare = spare;
    }

    /** 手に持っているデータパック銃に合わせて弾を割り当てる。データパック銃でない・弾が無い口径ならnull */
    public static ScavAmmoSupply assign(Mob scav, ScavBrain.BrainLevel level) {
        String gunId = DatapackGunCatalog.gunIdOf(scav.getEquipment().getItemInMainHand());
        DatapackGunProfile profile = DatapackGunCatalog.get(gunId);
        String caliber = DatapackAmmo.caliberOf(gunId);
        if (profile == null || !DatapackAmmo.hasAmmoFor(caliber)) return null;

        List<AmmoDefinition> choices = new ArrayList<>();
        for (String id : ItemRegistry.getAmmoIds()) {
            AmmoDefinition ammo = ItemRegistry.getAmmo(id);
            if (ammo != null && ammo.caliber.equalsIgnoreCase(caliber)) choices.add(ammo);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int ammoClass = rollClass(CLASS_WEIGHTS.get(level), random);
        // そのクラスに近い弾 (同じ近さなら低いクラス、同じクラスが複数あればダメージの低い方)
        AmmoDefinition chosen = choices.stream()
                .min(Comparator.comparingInt((AmmoDefinition a) -> Math.abs(a.ammoClass - ammoClass))
                        .thenComparingInt(a -> a.ammoClass)
                        .thenComparingDouble(a -> a.damage))
                .orElseThrow();
        int magazines = MIN_SPARE_MAGAZINES + random.nextInt(MAX_SPARE_MAGAZINES - MIN_SPARE_MAGAZINES + 1);
        ScavAmmoSupply supply = new ScavAmmoSupply(chosen.id, profile.magazineSize() * magazines);
        SUPPLIES.put(scav.getUniqueId(), supply);
        return supply;
    }

    /** 重みで貫通クラス (1〜6) を選ぶ */
    private static int rollClass(double[] weights, ThreadLocalRandom random) {
        double total = 0;
        for (double w : weights) total += w;
        double r = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return i + 1;
        }
        return weights.length;
    }

    public static ScavAmmoSupply of(UUID scavId) {
        return scavId == null ? null : SUPPLIES.get(scavId);
    }

    public static ScavAmmoSupply remove(UUID scavId) {
        return SUPPLIES.remove(scavId);
    }

    public String ammoId() {
        return ammoId;
    }

    public AmmoDefinition ammo() {
        return ItemRegistry.getAmmo(ammoId);
    }

    public synchronized int spare() {
        return spare;
    }

    /** 予備から最大 amount 発取り出す。取り出せた数を返す */
    public synchronized int take(int amount) {
        int taken = Math.max(0, Math.min(amount, spare));
        spare -= taken;
        return taken;
    }

    /** 残った予備を弾アイテムにする (死体に入れる) */
    public List<ItemStack> toItems() {
        List<ItemStack> items = new ArrayList<>();
        int remaining = spare();
        while (remaining > 0) {
            ItemStack stack = ItemFactory.create(ammoId);
            if (stack == null) break;
            int amount = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(amount);
            items.add(stack);
            remaining -= amount;
        }
        return items;
    }
}
