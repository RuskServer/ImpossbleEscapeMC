package com.lunar_prototype.impossbleEscapeMC.modules.starter;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataLoadedEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * 初期物資: 初めて参加したプレイヤー (プレイヤーデータのファイルも控えも無かった) のスタッシュに、装備 2 回分と現金を入れる。
 * <ul>
 *   <li>中身は {@link #CONTENTS} と {@link #MONEY}。銃は弾を込めた状態。普通の品物 (支給品の印も FIR も付けない) なので売れる</li>
 *   <li>スタッシュの 1 ページ目 (レベル 1 で 27 マス) の空きに入れる。スタッシュには大きさ (コスト) の制限が無く、1 スタック 1 マス</li>
 *   <li>入れるまではプレイヤーデータの印 ({@link PlayerData#isStarterKitPending()}) が残るので、入れる前に落ちても次の参加で入れる</li>
 * </ul>
 */
public final class StarterKit implements Listener {

    public static final double MONEY = 50_000;
    static final int PAGE = 1;

    /** 中身の 1 行: 銃 (ammoId を込める) か、品物 amount 個 */
    record Entry(String id, int amount, String ammoId) {
        boolean gun() {
            return ammoId != null;
        }
    }

    /** 中身: AK-74M 2 丁と USP45 (弾を込めた状態)・予備の弾・防具・リグとバッグ・医療品・フラッシュライト。弾はトレーダーで最初から買える物 */
    static final List<Entry> CONTENTS = List.of(
            gun("ak74", "545x39_hp"),
            gun("ak74", "545x39_hp"),
            gun("usp45", "45acp_hydra_shok"),
            item("545x39_hp", 120),        // AK の予備 4 マガジン分 (込めた分と合わせて 180 発)
            item("45acp_hydra_shok", 24),  // USP45 の予備 2 マガジン分
            item("UNTAR", 1),
            item("MF-UNTAR", 1),
            item("micro_rig", 1),
            item("tactical_sling_bag", 1),
            item("CAT", 3),
            item("ai2", 2),
            item("flashlight", 1));

    private static StarterKit instance;

    private final ImpossbleEscapeMC plugin;

    private StarterKit(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new StarterKit(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    private static Entry gun(String id, String ammoId) {
        return new Entry(id, 1, ammoId);
    }

    private static Entry item(String id, int amount) {
        return new Entry(id, amount, null);
    }

    /** 中身が使うスタッシュのマス数 (maxStackOf: 品物 1 スタックの上限) */
    static int slotsNeeded(ToIntFunction<String> maxStackOf) {
        int slots = 0;
        for (Entry entry : CONTENTS) {
            slots += entry.gun() ? 1 : (entry.amount() + maxStackOf.applyAsInt(entry.id()) - 1) / maxStackOf.applyAsInt(entry.id());
        }
        return slots;
    }

    @EventHandler
    public void onDataLoaded(PlayerDataLoadedEvent event) {
        PlayerData data = event.getPlayerData();
        if (data == null || !data.isStarterKitPending()) return;
        Player player = event.getPlayer();
        var services = plugin.getServiceContainer();
        StashModule stash = services.get(StashModule.class);
        EconomyModule economy = services.get(EconomyModule.class);
        PlayerDataModule dataModule = services.get(PlayerDataModule.class);
        if (stash == null || economy == null || dataModule == null) return;

        List<ItemStack> items = build(player.getWorld());
        List<ItemStack> left;
        try {
            left = putIntoStash(stash, data, items);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "初期物資をスタッシュに入れられませんでした: " + player.getName(), e);
            return;   // 印は残っているので、次の参加でもう一度入れる
        }
        // 収まらなかった分 (中身を増やした時など) は持ち物へ。それも無理なら足元に落とす
        if (!left.isEmpty()) {
            plugin.getLogger().warning("初期物資のうち " + left.size() + " スタックがスタッシュに収まらず、持ち物に入れました: " + player.getName());
            left.forEach(item -> player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest)));
        }
        economy.deposit(player.getUniqueId(), MONEY);
        data.setStarterKitPending(false);
        dataModule.saveAsync(player.getUniqueId());
        player.sendMessage(Component.text(String.format("初期物資をスタッシュに入れました。所持金 +%,.0f₽", MONEY),
                NamedTextColor.GREEN));
    }

    /** 中身を作る。作れなかった品物は飛ばす (記録は残す) */
    private List<ItemStack> build(World world) {
        List<ItemStack> items = new ArrayList<>();
        for (Entry entry : CONTENTS) {
            ItemStack sample = GameItems.create(world, entry.id(), null);
            if (sample == null) {
                plugin.getLogger().warning("初期物資の " + entry.id() + " を作れませんでした");
                continue;
            }
            if (entry.gun()) {
                DatapackGunProfile profile = DatapackGunCatalog.get(entry.id());
                items.add(profile != null ? DatapackAmmo.withMagazine(sample, profile.magazineSize(), entry.ammoId()) : sample);
                continue;
            }
            int remaining = entry.amount();
            while (remaining > 0) {
                ItemStack stack = sample.clone();
                stack.setAmount(Math.min(remaining, sample.getMaxStackSize()));
                remaining -= stack.getAmount();
                items.add(stack);
            }
        }
        return items;
    }

    /** スタッシュの 1 ページ目の空きに入れて保存する。収まらなかった物を返す */
    private static List<ItemStack> putIntoStash(StashModule stash, PlayerData data, List<ItemStack> items) throws Exception {
        int size = Math.max(1, stash.getRows(data.getStashLevel(), PAGE)) * 9;
        String serialized = data.getStashPages().get(PAGE);
        Inventory page = Bukkit.createInventory(null, size);
        if (serialized != null) {
            Inventory saved = SerializationUtil.deserializeInventory(serialized, Component.empty());
            for (int i = 0; i < Math.min(size, saved.getSize()); i++) page.setItem(i, saved.getItem(i));
        }
        List<ItemStack> left = new ArrayList<>();
        int slot = 0;
        for (ItemStack item : items) {
            while (slot < size && page.getItem(slot) != null && !page.getItem(slot).getType().isAir()) slot++;
            if (slot < size) {
                page.setItem(slot++, item);
            } else {
                left.add(item);
            }
        }
        data.setStashPage(PAGE, SerializationUtil.serializeInventory(page));
        return left;
    }
}
