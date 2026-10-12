package com.lunar_prototype.impossbleEscapeMC.modules.grounditem;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 落ちているアイテム。
 * <ul>
 *   <li>見た目: 地面で止まったアイテムは、本物を見えなくして (setVisibleByDefault)、同じ物を地面に寝かせた item_display に差し替える。
 *       向きはランダム。ブロックは寝かせずに置く。転がっている・水に浮いている間は普通の見た目に戻す</li>
 *   <li>拾い方: 近づいただけでは拾わず (サバイバル・アドベンチャー)、左クリックした時に視線の先 (PICKUP_RANGE、壁越しは不可) のアイテムを拾う。
 *       拾う時は EntityPickupItemEvent を自分で呼ぶので、リグで塞がれた欄・容量・FIR の印などの今の決まりはそのまま効く</li>
 * </ul>
 */
public final class GroundItemService implements Listener {

    private static final double PICKUP_RANGE = 3.5;
    /** 視線とアイテムの当たりの太さ (ブロック)。寝かせた見た目は本物の当たり判定より広いので、少し太くする */
    private static final double PICKUP_RAY_SIZE = 0.35;
    /** 止まったとみなす速さ (ブロック/tick) */
    private static final double REST_SPEED = 0.02;
    private static final float DISPLAY_SCALE = 0.5f;
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static GroundItemService instance;

    private final ImpossbleEscapeMC plugin;
    /** 寝かせて見せているアイテム → その見た目 */
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();
    /** 見張っているアイテム (落ちてから止まるまで、と、寝かせている間) */
    private final Map<UUID, Item> watched = new HashMap<>();
    /** 右クリックした tick (右クリックでも腕を振るので、その tick の腕振りは左クリックとみなさない) */
    private final Map<UUID, Integer> lastRightClick = new HashMap<>();

    private GroundItemService(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance == null) instance = new GroundItemService(plugin);
    }

    /** 見た目を消し、隠したアイテムを元に戻す (プラグインの停止時) */
    public static void shutdown() {
        if (instance == null) return;
        for (UUID id : new java.util.ArrayList<>(instance.displays.keySet())) {
            Item item = instance.watched.get(id);
            instance.unflatten(id, item);
        }
        instance = null;
    }

    // --- 見た目 ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        watched.put(event.getEntity().getUniqueId(), event.getEntity());
    }

    /** チャンクの読み込み・再起動などで、見張っていないアイテムを拾い上げる */
    private void scan() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                watched.putIfAbsent(item.getUniqueId(), item);
            }
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Item>> it = watched.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Item> entry = it.next();
            Item item = entry.getValue();
            UUID id = entry.getKey();
            if (!item.isValid()) {
                unflatten(id, null);
                it.remove();
                continue;
            }
            boolean resting = item.isOnGround() && item.getVelocity().lengthSquared() < REST_SPEED * REST_SPEED && !item.isInWater();
            ItemDisplay display = displays.get(id);
            if (display != null && !display.isValid()) {
                // 見た目だけが消された (/kill など)。次の tick に作り直す
                displays.remove(id);
                item.setVisibleByDefault(true);
                continue;
            }
            if (resting && display == null) {
                flatten(id, item);
            } else if (!resting && display == null && !item.isVisibleByDefault()) {
                // 隠したまま保存されていたアイテム (止まっていないので見た目を作らない)
                item.setVisibleByDefault(true);
            } else if (!resting && display != null) {
                unflatten(id, item);
            } else if (display != null && display.getLocation().distanceSquared(item.getLocation()) > 0.01) {
                display.teleport(item.getLocation());
            }
        }
    }

    private void flatten(UUID id, Item item) {
        ItemStack stack = item.getItemStack();
        boolean block = stack.getType().isBlock();
        float yaw = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2);
        Quaternionf rotation = new Quaternionf().rotateY(yaw);
        // 平らな物は寝かせる (額縁と同じ FIXED の見た目は南を向いた板なので、上を向くように倒す)。ブロックは立てたまま地面に乗せる
        if (!block) rotation.rotateX((float) (-Math.PI / 2));
        Vector3f translation = new Vector3f(0f, block ? DISPLAY_SCALE / 2f : 0.03f, 0f);
        ItemDisplay display = item.getWorld().spawn(item.getLocation(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(translation, rotation,
                    new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
        });
        displays.put(id, display);
        item.setVisibleByDefault(false);
    }

    private void unflatten(UUID id, Item item) {
        ItemDisplay display = displays.remove(id);
        if (display != null && display.isValid()) display.remove();
        if (item != null && item.isValid()) item.setVisibleByDefault(true);
    }

    // --- 拾い方 ---

    /** 近づいただけでは拾わない (クリエイティブ・観戦は今まで通り) */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        GameMode mode = event.getPlayer().getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        event.setFlyAtPlayer(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction().isRightClick()) lastRightClick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Integer right = lastRightClick.get(player.getUniqueId());
        if (right != null && right == Bukkit.getCurrentTick()) return;
        Item target = targetItem(player);
        if (target != null) pickUp(player, target);
    }

    /** 視線の先のアイテム (壁の向こうは除く) */
    private static Item targetItem(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, direction, PICKUP_RANGE, PICKUP_RAY_SIZE,
                entity -> entity instanceof Item item && item.isValid());
        if (hit == null || !(hit.getHitEntity() instanceof Item item)) return null;
        double itemDistance = hit.getHitPosition().distance(eye.toVector());
        RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, direction, itemDistance, FluidCollisionMode.NEVER, true);
        // 地面に置いてある物は、視線が地面に当たる少し手前で当たるので、少しの差は許す
        if (wall != null && wall.getHitPosition().distance(eye.toVector()) < itemDistance - 0.5) return null;
        return item;
    }

    /** 拾う。バニラが拾う時と同じ EntityPickupItemEvent を呼んで、ほかの決まり (リグ・容量・FIR) を通す */
    private void pickUp(Player player, Item item) {
        ItemStack before = item.getItemStack().clone();
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, item, 0);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // ほかの決まりが自分で入れた (リグで塞がれた欄を避けて入れる等) か、入らなかった
            if (!item.isValid() || item.getItemStack().getAmount() < before.getAmount()) playPickup(player);
            return;
        }
        ItemStack stack = item.getItemStack();
        int left = player.getInventory().addItem(stack.clone()).values().stream().mapToInt(ItemStack::getAmount).sum();
        if (left >= stack.getAmount()) {
            player.sendActionBar(Component.text("持ち物に空きがありません", NamedTextColor.RED));
            return;
        }
        if (left > 0) {
            stack.setAmount(left);
            item.setItemStack(stack);
        } else {
            item.remove();
        }
        playPickup(player);
    }

    private static void playPickup(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.3f,
                1.4f + ThreadLocalRandom.current().nextFloat() * 0.4f);
    }
}
