package com.lunar_prototype.impossbleEscapeMC.modules.grenade;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.party.Party;
import com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.HandInPlaces;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探知グレネード UNRG-7K "Ekho" (国連軍が Spectre の SP-E を写した UNRG-7 の、さらに写し)。
 * <ul>
 *   <li>レイドの中で、右クリックで投げる (1 個使う)。見た目はアイテムのモデルのまま飛ぶ</li>
 *   <li>着弾から {@link #FUSE_TICKS} の間、電子音を鳴らす (周りにも聞こえるので、狙われた側も気付ける)</li>
 *   <li>その後、周囲 {@link #RANGE} ブロック (壁越し) の、パーティーの外のプレイヤーと SCAV を、投げた人とそのパーティーの画面でだけ
 *       {@link #REVEAL_TICKS} の間赤く光らせる ({@link ScanReveal})。映ったプレイヤーには知らせる</li>
 *   <li>レイドに持ち込めるのは {@link #MAX_CARRY} 個まで ({@link #overCarryLimit}。出撃の時に RaidModule が確かめる)</li>
 * </ul>
 */
public final class ReconGrenade implements Listener {

    public static final String ITEM_ID = "unrg7k_ekho";
    public static final int MAX_CARRY = 2;
    static final double RANGE = 15.0;
    static final int FUSE_TICKS = 20, REVEAL_TICKS = 60;
    /** 投げる速さ (雪玉と同じ投げ方。ブロック/tick) */
    private static final double THROW_SPEED = 1.0;
    /** 続けて投げられないようにする間 (tick) */
    private static final int COOLDOWN_TICKS = 10;

    private static ReconGrenade instance;

    private final ImpossbleEscapeMC plugin;
    /** 飛んでいるグレネード → 投げた人 */
    private final Map<UUID, UUID> inFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastThrow = new ConcurrentHashMap<>();

    private ReconGrenade(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new ReconGrenade(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        ScanReveal.init(plugin);
    }

    public static void shutdown() {
        ScanReveal.shutdown();
        instance = null;
    }

    static boolean isGrenade(ItemStack item) {
        return item != null && item.hasItemMeta()
                && ITEM_ID.equals(item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING));
    }

    /** レイドに持ち込もうとしている数が上限を超えているか (着ている物・持ち物・持っているバックパックの中を数える) */
    public static boolean overCarryLimit(Player player) {
        PlayerData data = dataOf(player);
        if (data == null) return false;
        int count = 0;
        for (HandInPlaces.Entry entry : HandInPlaces.scan(player, data, true, false)) {
            if (isGrenade(entry.item())) count += entry.item().getAmount();
        }
        return count > MAX_CARRY;
    }

    /** 持ち込みの上限を超えている時の知らせ */
    public static void sendCarryLimitWarning(Player player) {
        player.sendMessage(Component.text("UNRG-7K \"Ekho\" は 1 回のレイドに " + MAX_CARRY + " 個までしか持ち込めません。", NamedTextColor.RED));
    }

    // ---- 投げる ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGrenade(item)) return;
        event.setCancelled(true);
        RaidModule raids = plugin.getRaidModule();
        if (raids == null || !raids.isInRaid(player)) {
            player.sendActionBar(Component.text("レイドの中でだけ使えます", NamedTextColor.RED));
            return;
        }
        int now = Bukkit.getCurrentTick();
        Integer last = lastThrow.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        lastThrow.put(player.getUniqueId(), now);

        ItemStack shown = item.asOne();
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item.getAmount() > 0 ? item : null);
        Snowball grenade = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(THROW_SPEED));
        grenade.setItem(shown);
        inFlight.put(grenade.getUniqueId(), player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 0.7f);
    }

    /** グレネードが人に当たっても、雪玉のノックバックは無し */
    @EventHandler(priority = EventPriority.LOW)
    public void onHitEntity(EntityDamageByEntityEvent event) {
        if (inFlight.containsKey(event.getDamager().getUniqueId())) event.setCancelled(true);
    }

    // ---- 着弾 → 鳴る → 探知 ----

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        UUID thrower = inFlight.remove(event.getEntity().getUniqueId());
        if (thrower == null || !(event.getEntity() instanceof Snowball snowball)) return;
        Location at = snowball.getLocation();
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            at = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0.05, 0.5);
        }
        ItemStack look = snowball.getItem();
        Location place = at;
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        });
        for (int i = 0; i < 4; i++) {
            float pitch = 1.6f + i * 0.15f;
            Bukkit.getScheduler().runTaskLater(plugin, () -> place.getWorld().playSound(place, Sound.BLOCK_NOTE_BLOCK_BIT,
                    SoundCategory.PLAYERS, 1.2f, pitch), i * (FUSE_TICKS / 4L));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scan(thrower, place);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (display.isValid()) display.remove();
            }, 20L);
        }, FUSE_TICKS);
    }

    private void scan(UUID throwerId, Location at) {
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.8f);
        Player thrower = Bukkit.getPlayer(throwerId);
        RaidModule raids = plugin.getRaidModule();
        RaidInstance raid = thrower != null && raids != null ? raids.getRaidOf(thrower) : null;
        if (thrower == null || raid == null) return;

        // 見る人: 投げた人と、同じレイドにいるパーティーの味方
        Set<UUID> friends = new HashSet<>();
        friends.add(throwerId);
        PartyModule parties = PartyModule.get();
        Party party = parties != null ? parties.getParty(throwerId) : null;
        if (party != null) friends.addAll(party.getMembers());
        List<Player> viewers = new ArrayList<>();
        for (UUID id : friends) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(at.getWorld()) && raids.getRaidOf(p) == raid) viewers.add(p);
        }

        int found = 0;
        for (Entity entity : at.getWorld().getNearbyEntities(at, RANGE, RANGE, RANGE)) {
            if (!(entity instanceof LivingEntity target) || target.isDead() || friends.contains(target.getUniqueId())) continue;
            if (target.getLocation().distanceSquared(at) > RANGE * RANGE) continue;
            boolean enemyPlayer = target instanceof Player p && p.getGameMode() != GameMode.SPECTATOR
                    && !DatapackGunnerManager.isGunner(p) && raids.getRaidOf(p) == raid;
            boolean scav = ScavSpawner.isScav(target.getUniqueId());
            if (!enemyPlayer && !scav) continue;
            found++;
            for (Player viewer : viewers) ScanReveal.reveal(viewer, target, REVEAL_TICKS);
            if (target instanceof Player revealed) {
                revealed.sendActionBar(Component.text("スキャンされた", NamedTextColor.RED));
                revealed.playSound(revealed, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 1.0f, 0.5f);
            }
        }
        for (Player viewer : viewers) {
            viewer.sendActionBar(Component.text(found > 0 ? "探知: " + found + " 体" : "探知: 反応なし",
                    found > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        }
    }

    static PlayerData dataOf(Player player) {
        PlayerDataModule module = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        return module != null ? module.getPlayerData(player.getUniqueId()) : null;
    }
}
