package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * データパック銃のリロードに弾を使わせる (データパックを書き換えずに)。
 * <ul>
 *   <li>データパックの「リロードに弾が要る」設定を有効にし、口径の合う弾を持っているプレイヤーにだけ毎tick ENOUGH_AMMO を付ける
 *       (データパックはこのタグがある時だけリロードを始める。弾アイテムを調べるデータパック側の関数は空のため、プラグインが代わりに付ける)</li>
 *   <li>リロードで弾数が増えたら、そのぶんインベントリの弾を消費する。足りなければ弾数を持っていたぶんまで下げる。
 *       別の種類の弾を込める時は、残っていた弾をインベントリに戻してマガジンごと入れ替える (プラグイン銃と同じ)</li>
 *   <li>弾の種類は、インベントリにいちばん多くある物 (プラグイン銃と同じ)</li>
 * </ul>
 * 弾が定義されていない口径の銃は、今までどおり弾無しでリロードできる。SCAVの銃 (FakePlayer) はプラグインが装填するため対象外。
 * プラグインのタスクはデータパックの tick より先に動くため、付けたタグはそのtickのデータパックの判定に間に合う
 */
public final class DatapackAmmoTracker implements Runnable, Listener {

    private static final String ENOUGH_AMMO_TAG = "ENOUGH_AMMO";
    private static final String UPDATE_NAME_TAG = "toisarm.update_item_name";
    private static final String RELOAD_INPUT_TAG = "toisarm.input.reload";
    private static final String SETTING_HOLDER = "#toisarm.settings.reload_requires_ammo";
    private static final String SETTING_OBJECTIVE = "_";
    private static final int SETTING_REFRESH_TICKS = 100;
    private static final int NO_AMMO_MESSAGE_INTERVAL_TICKS = 20;
    private static final String[] RELOAD_TIMERS = {"toisarm.timer.reload", "toisarm.timer.empty_reload", "toisarm.timer.reload_loop"};

    /** 前のtickに見た、手に持った銃の弾数 */
    private record Seen(int slot, String gunId, int rounds, boolean reloading) {
    }

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Integer> lastNoAmmoMessage = new HashMap<>();
    private int tick;

    @Override
    public void run() {
        if (tick++ % SETTING_REFRESH_TICKS == 0) enableAmmoRequirement();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (DatapackGunnerManager.isGunner(player)) {
                player.addScoreboardTag(ENOUGH_AMMO_TAG);
                continue;
            }
            update(player);
        }
    }

    private void update(Player player) {
        UUID id = player.getUniqueId();
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack gun = player.getInventory().getItemInMainHand();
        String gunId = DatapackGunCatalog.gunIdOf(gun);
        if (gunId == null) {
            seen.remove(id);
            return;
        }
        String caliber = DatapackAmmo.caliberOf(gunId);
        boolean limited = DatapackAmmo.hasAmmoFor(caliber);

        int rounds = rounds(gun);
        Seen previous = seen.get(id);
        if (limited && previous != null && previous.reloading() && previous.slot() == slot
                && previous.gunId().equals(gunId) && rounds > previous.rounds()) {
            gun = loadFromInventory(player, gun, caliber, previous.rounds(), rounds - previous.rounds());
            rounds = rounds(gun);
        }

        boolean hasAmmo = !limited || !DatapackAmmo.findAmmo(player, caliber).isEmpty();
        if (hasAmmo) {
            player.addScoreboardTag(ENOUGH_AMMO_TAG);
        } else {
            player.removeScoreboardTag(ENOUGH_AMMO_TAG);
            if (player.getScoreboardTags().contains(RELOAD_INPUT_TAG)) {
                int last = lastNoAmmoMessage.getOrDefault(id, Integer.MIN_VALUE / 2);
                if (tick - last >= NO_AMMO_MESSAGE_INTERVAL_TICKS) {
                    lastNoAmmoMessage.put(id, tick);
                    player.sendActionBar(Component.text("弾がありません (" + caliber + ")", NamedTextColor.RED));
                }
            }
        }
        seen.put(id, new Seen(slot, gunId, rounds, reloading(player)));
    }

    /**
     * リロードで増えた弾を、インベントリの弾から込める。込めた後の銃を手に持たせて返す
     *
     * @param before リロード前の弾数 (マガジン + 薬室)
     * @param added  データパックが増やした弾数
     */
    private ItemStack loadFromInventory(Player player, ItemStack gun, String caliber, int before, int added) {
        Map<String, List<ItemStack>> pool = DatapackAmmo.findAmmo(player, caliber);
        String ammoId = null;
        int available = 0;
        for (Map.Entry<String, List<ItemStack>> entry : pool.entrySet()) {
            int count = entry.getValue().stream().mapToInt(ItemStack::getAmount).sum();
            if (count > available) {
                available = count;
                ammoId = entry.getKey();
            }
        }

        String loaded = DatapackAmmo.loadedAmmoId(gun);
        int kept = before;
        int needed = added;
        if (ammoId != null && loaded != null && !loaded.equals(ammoId) && before > 0) {
            // 別の種類を込める: 残っていた弾を戻し、全部を新しい弾で込め直す
            giveRounds(player, loaded, before);
            kept = 0;
            needed = before + added;
        }
        int taken = ammoId == null ? 0 : take(pool.get(ammoId), Math.min(needed, available));

        int total = kept + taken;
        int chamber = DatapackAmmo.chambered(gun) ? 1 : 0;
        int magazine = Math.max(0, total - chamber);
        ItemStack updated = DatapackAmmo.withMagazine(gun, magazine, ammoId != null ? ammoId : loaded);
        player.getInventory().setItemInMainHand(updated);
        setScore(player, "toisarm.ammo_remaining", magazine);
        player.addScoreboardTag(UPDATE_NAME_TAG);
        return updated;
    }

    private static int take(List<ItemStack> stacks, int amount) {
        int taken = 0;
        for (ItemStack stack : stacks) {
            if (taken >= amount) break;
            int use = Math.min(stack.getAmount(), amount - taken);
            stack.setAmount(stack.getAmount() - use);
            taken += use;
        }
        return taken;
    }

    private static void giveRounds(Player player, String ammoId, int count) {
        AmmoDefinition ammo = ItemRegistry.getAmmo(ammoId);
        if (ammo == null) return;
        while (count > 0) {
            ItemStack stack = ItemFactory.create(ammoId);
            if (stack == null) return;
            int amount = Math.min(count, stack.getMaxStackSize());
            stack.setAmount(amount);
            player.getInventory().addItem(stack).forEach((i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            count -= amount;
        }
    }

    private static int rounds(ItemStack gun) {
        return DatapackAmmo.magazine(gun) + (DatapackAmmo.chambered(gun) ? 1 : 0);
    }

    private static boolean reloading(Player player) {
        for (String timer : RELOAD_TIMERS) {
            if (score(player, timer, -1) >= 0) return true;
        }
        return false;
    }

    private static int score(Player player, String objectiveName, int fallback) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
        if (objective == null) return fallback;
        Score score = objective.getScore(player.getName());
        return score.isScoreSet() ? score.getScore() : fallback;
    }

    private static void setScore(Player player, String objectiveName, int value) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
        if (objective != null) objective.getScore(player.getName()).setScore(value);
    }

    /** データパックの「リロードに弾が要る」設定を有効にする (データパックの再読み込みで消えても戻るよう、定期的に) */
    private static void enableAmmoRequirement() {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(SETTING_OBJECTIVE);
        if (objective != null) objective.getScore(SETTING_HOLDER).setScore(1);
        DatapackAmmo.clearCache();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        seen.remove(id);
        lastNoAmmoMessage.remove(id);
        DatapackAmmo.forget(id);
    }
}
