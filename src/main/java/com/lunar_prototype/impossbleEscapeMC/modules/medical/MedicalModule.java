package com.lunar_prototype.impossbleEscapeMC.modules.medical;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MedicalModule implements IModule, Listener {
    private ImpossbleEscapeMC plugin;
    private final Map<UUID, Long> lastInteract = new HashMap<>();
    private final Map<UUID, Integer> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> usingSlots = new HashMap<>();
    /** 使い切り: 使い始めてからのチック数 / 押し続け: 回復した回数 */
    private final Map<UUID, Integer> progressTicks = new HashMap<>();
    private com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule dataModule;
    private StatusEffectManager statusEffectManager;

    // CAT と AI-2 を使う時の音 (リソースパックの iem:med.*、IEM_shader/tools/med_sounds.py が作る)
    private static final String CAT_ID = "CAT";
    private static final String AI2_ID = "ai2";
    /** CAT: 使い始めてからのチック数と鳴らす音 (マジックテープ → ベルトを締める → 巻き上げ棒 → 留め具) */
    private static final Map<Integer, String> CAT_STEPS = Map.of(
            9, "iem:med.cat.strap",
            20, "iem:med.cat.windlass",
            38, "iem:med.cat.clip");

    public MedicalModule() {
    }

    /** 使っている本人にだけ、高さを少し揺らして鳴らす */
    private static void playMedSound(Player player, String sound) {
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 1.0f,
                0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
    }

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        this.dataModule = container.get(com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule.class);
        this.statusEffectManager = new StatusEffectManager(plugin, dataModule);
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(statusEffectManager, plugin);

        // 監視タスク: 右クリックが離されたかチェック
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : new java.util.HashSet<>(lastInteract.keySet())) {
                // 250ms (約5チック) 経過で離したと判定 (スキルで延びる)
                if (now - lastInteract.get(uuid) > 250 + SkillModule.value(uuid, SkillStat.USE_GRACE_MS)) {
                    stopUsing(Bukkit.getPlayer(uuid));
                }
            }
        }, 1L, 1L);

        // 状態異常更新タスク (1秒ごと)
        Bukkit.getScheduler().runTaskTimer(plugin, statusEffectManager::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (UUID uuid : new java.util.HashSet<>(activeTasks.keySet())) {
            stopUsing(Bukkit.getPlayer(uuid));
        }
        activeTasks.clear();
        lastInteract.clear();
        usingSlots.clear();
        progressTicks.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def != null && "MED".equalsIgnoreCase(def.type) && def.medStats != null) {
            if (def.medStats.continuous) {
                // 継続回復キット (Medkit等)
                if (player.getHealth() >= player.getMaxHealth()) return;
                event.setCancelled(true);
                startUsing(player, item, def);
            } else if (def.medStats.oneTime) {
                // 一括使用アイテム (CAT等)
                event.setCancelled(true);
                startOneTimeUsing(player, item, def);
            }
        }
    }

    private void startOneTimeUsing(Player player, ItemStack item, ItemDefinition def) {
        UUID uuid = player.getUniqueId();
        lastInteract.put(uuid, System.currentTimeMillis());
        usingSlots.put(uuid, player.getInventory().getHeldItemSlot());

        if (!activeTasks.containsKey(uuid)) {
            progressTicks.put(uuid, 0);
            boolean cat = CAT_ID.equals(def.id);
            if (cat) playMedSound(player, "iem:med.cat.velcro");
            // CAT の使用時間はスキルで短くなる
            int duration = cat ? Math.max(1, def.medStats.durationTicks + (int) SkillModule.value(player, SkillStat.CAT_TICKS))
                    : def.medStats.durationTicks;
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                int current = progressTicks.getOrDefault(uuid, 0) + 1;
                progressTicks.put(uuid, current);

                // 残り時間をアクションバーに表示 (撃ち合いの中で「あと何秒で動けるか」が分かるように)
                double remainingSeconds = Math.max(0, duration - current) / 20.0;
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        String.format("使用中… %.1fs", remainingSeconds), net.kyori.adventure.text.format.NamedTextColor.YELLOW));

                if (current >= duration) {
                    finishOneTimeUsing(player, def);
                } else if (cat) {
                    String step = CAT_STEPS.get(current);
                    if (step != null) playMedSound(player, step);
                } else if (current % 10 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 0.5f, 1.2f);
                }
            }, 1L, 1L).getTaskId();
            activeTasks.put(uuid, taskId);
        }
    }

    private void finishOneTimeUsing(Player player, ItemDefinition def) {
        // 使っている欄の物が、使い始めた医療品のままか (画面で入れ替えると欄の切り替えの知らせが来ないので、ここで確かめる)。
        // 違えば中断した扱い (手当もしない・何も減らさない)
        Integer slot = usingSlots.get(player.getUniqueId());
        ItemStack item = slot != null ? player.getInventory().getItem(slot) : null;
        String heldId = item != null && item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        if (!def.id.equals(heldId)) {
            stopUsing(player);
            return;
        }

        com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data != null) {
            if (def.medStats.cureBleeding) data.setBleedingLevel(0);
            if (def.medStats.cureLegFracture) data.setLegFracture(false);
            if (def.medStats.cureArmFracture) data.setArmFracture(false);
            StatusEffectIcons.sync(player, data);
            
            player.sendMessage(net.kyori.adventure.text.Component.text(def.displayName + " を使用して手当を行いました。", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            // CAT は留め具の音で終わる
            if (!CAT_ID.equals(def.id)) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        // アイテムを一個消費
        item.setAmount(item.getAmount() - 1);
        
        stopUsing(player);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        stopUsing(event.getPlayer());
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        stopUsing(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        stopUsing(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopUsing(event.getPlayer());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        stopUsing(event.getEntity());
    }

    private void startUsing(Player player, ItemStack item, ItemDefinition def) {
        UUID uuid = player.getUniqueId();
        lastInteract.put(uuid, System.currentTimeMillis());
        usingSlots.put(uuid, player.getInventory().getHeldItemSlot());

        // モデル切り替え
        ItemMeta meta = item.getItemMeta();
        if (meta.getCustomModelData() != def.medStats.usingCustomModelData) {
            meta.setCustomModelData(def.medStats.usingCustomModelData);
            item.setItemMeta(meta);
            if (AI2_ID.equals(def.id)) {
                playMedSound(player, "iem:med.ai2.open");
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.5f);
            }
        }

        // 回復タスクがなければ開始 (0.5s = 10ticks 周期。AI-2 はスキルで短くなる)
        if (!activeTasks.containsKey(uuid)) {
            progressTicks.put(uuid, 0);
            long period = AI2_ID.equals(def.id) ? Math.max(1, 10 + (long) SkillModule.value(player, SkillStat.AI2_INTERVAL_TICKS)) : 10L;
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                handleHeal(player, def);
            }, period, period).getTaskId();
            activeTasks.put(uuid, taskId);
        }
    }

    private void handleHeal(Player player, ItemDefinition def) {
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = (item != null) ? item.getItemMeta() : null;
        if (meta == null) {
            stopUsing(player);
            return;
        }

        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (!def.id.equals(itemId)) {
            stopUsing(player);
            return;
        }

        // 回復
        double maxHealth = player.getMaxHealth();
        double currentHealth = player.getHealth();
        
        if (currentHealth >= maxHealth) {
            stopUsing(player);
            return;
        }

        boolean ai2 = AI2_ID.equals(def.id);
        double heal = def.medStats.healPerUse + (ai2 ? SkillModule.value(player, SkillStat.AI2_HEAL) : 0);
        double newHealth = Math.min(maxHealth, currentHealth + heal);
        player.setHealth(newHealth);
        if (AI2_ID.equals(def.id)) {
            // 錠剤を振る・シートから押し出すを交互に
            int heals = progressTicks.merge(player.getUniqueId(), 1, Integer::sum);
            playMedSound(player, heals % 2 == 1 ? "iem:med.ai2.pills" : "iem:med.ai2.blister");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.0f);
        }
        player.spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0.1);

        // 回復後に満タンになったら停止
        if (newHealth >= maxHealth) {
            stopUsing(player);
        }

        // 耐久消費 (AI-2 はスキルで、確率で減らさない)
        if (ai2 && Math.random() < SkillModule.value(player, SkillStat.AI2_SAVE_CHANCE)) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int currentDurability = pdc.getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        int nextDurability = currentDurability - def.medStats.durabilityPerUse;

        if (nextDurability <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            stopUsing(player);
        } else {
            pdc.set(PDCKeys.DURABILITY, PDCKeys.INTEGER, nextDurability);
            item.setItemMeta(meta);
            ItemFactory.updateLore(item);
        }
    }

    private void stopUsing(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastInteract.remove(uuid);
        progressTicks.remove(uuid);
        
        Integer taskId = activeTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        Integer slot = usingSlots.remove(uuid);
        if (slot == null) return;

        // 指定されたスロットのアイテムを元に戻す
        ItemStack item = player.getInventory().getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def != null && def.medStats != null && def.medStats.continuous) {
                if (meta.getCustomModelData() == def.medStats.usingCustomModelData) {
                    meta.setCustomModelData(def.customModelData);
                    item.setItemMeta(meta);
                    if (AI2_ID.equals(def.id)) {
                        playMedSound(player, "iem:med.ai2.close");
                    } else {
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.5f);
                    }
                }
            }
        }
    }
}
