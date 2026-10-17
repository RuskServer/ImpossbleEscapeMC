package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.minecraft.nbt.CompoundTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Toi's Armory の銃に、プラグイン側の耐久とジャム状態を保存する。 */
public final class DatapackGunCondition {
    public static final int MAX_DURABILITY = 1000;
    private static final int JAM_START_DURABILITY = 600;
    private static final String DURABILITY_KEY = "iemc_durability";
    private static final String JAMMED_KEY = "iemc_jammed";
    private static final String PRD_STREAK_KEY = "iemc_jam_prd_streak";
    private static final String DISPLAYED_DURABILITY_KEY = "iemc_displayed_durability";

    private DatapackGunCondition() {
    }

    public static ItemStack ensure(ItemStack gun) {
        if (DatapackGunCatalog.gunIdOf(gun) == null || state(gun).getIntOr(DURABILITY_KEY, -1) >= 0) return gun;
        return set(gun, MAX_DURABILITY, false, 0);
    }

    public static ItemStack scavGun(ItemStack gun) {
        if (DatapackGunCatalog.gunIdOf(gun) == null) return gun;
        int durability = ThreadLocalRandom.current().nextInt(200, 301);
        return syncDisplay(set(gun, durability, false, 0));
    }

    public static int durability(ItemStack gun) {
        return Math.max(0, Math.min(MAX_DURABILITY, state(gun).getIntOr(DURABILITY_KEY, MAX_DURABILITY)));
    }

    public static boolean isJammed(ItemStack gun) {
        return state(gun).getIntOr(JAMMED_KEY, 0) == 1;
    }

    public static ItemStack clearJam(ItemStack gun) {
        return set(gun, durability(gun), false, 0);
    }

    public static boolean displayOutdated(ItemStack gun) {
        return state(gun).getIntOr(DISPLAYED_DURABILITY_KEY, -1) != durability(gun);
    }

    /** 表示コンポーネントは持ち替え判定に含まれるため、手に持っていない銃だけに呼ぶ。 */
    public static ItemStack syncDisplay(ItemStack gun) {
        int durability = durability(gun);
        ItemStack updated = DatapackAmmo.editState(gun,
                state -> state.putInt(DISPLAYED_DURABILITY_KEY, durability));
        ItemMeta meta = updated.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("銃の耐久: "));
        lore.add(Component.text("銃の耐久: " + durability + " / " + MAX_DURABILITY, NamedTextColor.GRAY));
        meta.lore(lore);
        updated.setItemMeta(meta);
        updated.setData(DataComponentTypes.MAX_DAMAGE, MAX_DURABILITY);
        updated.setData(DataComponentTypes.DAMAGE, MAX_DURABILITY - durability);
        return updated;
    }

    /** 実際に消費された弾数だけ処理する。PRD の連続不発回数は銃ごとに保存する。 */
    public static ItemStack afterShots(ItemStack gun, int shots) {
        int durability = durability(gun);
        int streak = state(gun).getIntOr(PRD_STREAK_KEY, 0);
        boolean jammed = isJammed(gun);
        for (int i = 0; i < Math.min(shots, 30); i++) {
            if (durability > 0 && ThreadLocalRandom.current().nextInt(3) == 0) durability--;
            double target = jamRate(durability);
            if (target == 0.0) {
                streak = 0;
            } else if (!jammed) {
                // 線形に上がる成功率の係数。低確率域では長期平均が target に近い。
                streak++;
                double chance = Math.min(1.0, streak * (Math.PI / 2.0) * target * target);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    jammed = true;
                    streak = 0;
                }
            }
        }
        return set(gun, durability, jammed, streak);
    }

    private static double jamRate(int durability) {
        if (durability >= JAM_START_DURABILITY) return 0.0;
        double worn = (JAM_START_DURABILITY - Math.max(0, durability)) / (double) JAM_START_DURABILITY;
        return 0.002 + 0.018 * worn * worn;
    }

    private static CompoundTag state(ItemStack gun) {
        return DatapackAmmo.state(gun);
    }

    private static ItemStack set(ItemStack gun, int durability, boolean jammed, int streak) {
        return DatapackAmmo.editState(gun, state -> {
            state.putInt(DURABILITY_KEY, durability);
            state.putInt(JAMMED_KEY, jammed ? 1 : 0);
            state.putInt(PRD_STREAK_KEY, streak);
        });
    }
}
