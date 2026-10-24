package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 通常のプレイ中、左上に出す操作ヒント。レイド中は RaidHud と同じボスバーに載せる。 */
public final class OperationHintHud implements Listener {
    static final Hint LOBBY = new Hint('', 118);
    static final Hint MAP = new Hint('', 79);
    static final Hint ZOOM = new Hint('', 88);
    static final Hint SEARCH = new Hint('', 49);

    record Hint(char glyph, int width) {}

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, BossBar> lobbyBars = new HashMap<>();
    private final BukkitTask task;

    public OperationHintHud(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (DatapackGunnerManager.isGunner(player)) continue;
            UUID id = player.getUniqueId();
            boolean playable = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
            boolean inRaid = plugin.getRaidModule() != null && plugin.getRaidModule().isInRaid(player);
            boolean intro = ScreenEffectService.isPlaying(player, ScreenEffectService.PDA_INTRO);
            if (!playable || inRaid || intro) {
                hide(player);
                continue;
            }
            BossBar bar = lobbyBars.get(id);
            if (bar == null) {
                bar = BossBar.bossBar(RaidHud.lobbyHintTitle(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                lobbyBars.put(id, bar);
                player.showBossBar(bar);
            }
        }
        lobbyBars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void hide(Player player) {
        BossBar bar = lobbyBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.getRaidModule().isInRaid(event.getPlayer())) hide(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    public void stop() {
        task.cancel();
        HandlerList.unregisterAll(this);
        for (UUID id : lobbyBars.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.hideBossBar(lobbyBars.get(id));
        }
        lobbyBars.clear();
    }

    static Hint raidHint(Player player, ImpossbleEscapeMC plugin) {
        if (player.getTargetEntity(4) instanceof Mannequin mannequin
                && (mannequin.getPersistentDataContainer().has(PDCKeys.CORPSE_INVENTORY, PDCKeys.BYTE_ARRAY)
                || mannequin.getPersistentDataContainer().has(PDCKeys.CORPSE_INVENTORY, PDCKeys.STRING))) {
            return SEARCH;
        }
        org.bukkit.block.Block block = player.getTargetBlockExact(4);
        if (block != null && block.getState() instanceof Container container
                && container.getPersistentDataContainer().has(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING)) {
            return SEARCH;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.FILLED_MAP && plugin.getRaidMapManager().isMapSlotItem(held)) {
            return ZOOM;
        }
        return MAP;
    }
}
