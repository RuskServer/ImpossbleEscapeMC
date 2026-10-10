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
 *   <li>弾の種類はSCAVの強さで選ぶ (LOW はその口径でいちばん弱い弾、MID はたまに一段上、HIGH はいちばん強い弾)</li>
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
    /** MID が一段上の弾を持つ確率 */
    private static final double MID_BETTER_AMMO_CHANCE = 0.25;

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
        choices.sort(Comparator.comparingInt((AmmoDefinition a) -> a.ammoClass).thenComparingDouble(a -> a.damage));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int index = switch (level) {
            case LOW -> 0;
            case MID -> random.nextDouble() < MID_BETTER_AMMO_CHANCE ? Math.min(1, choices.size() - 1) : 0;
            case HIGH -> choices.size() - 1;
        };
        int magazines = MIN_SPARE_MAGAZINES + random.nextInt(MAX_SPARE_MAGAZINES - MIN_SPARE_MAGAZINES + 1);
        ScavAmmoSupply supply = new ScavAmmoSupply(choices.get(index).id, profile.magazineSize() * magazines);
        SUPPLIES.put(scav.getUniqueId(), supply);
        return supply;
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
