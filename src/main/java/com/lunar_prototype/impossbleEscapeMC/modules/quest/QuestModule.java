package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestEventBus;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * クエストシステムを管理するモジュール
 */
public class QuestModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final Map<String, QuestDefinition> quests = new HashMap<>();
    private final QuestEventBus eventBus = new QuestEventBus();
    private PlayerDataModule dataModule;

    public QuestModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable(ServiceContainer container) {
        this.dataModule = container.get(PlayerDataModule.class);
        
        loadQuests();
        setupEventBus();
        
        // リスナーの登録
        plugin.getServer().getPluginManager().registerEvents(new QuestListener(this, dataModule), plugin);
        
        // 位置チェックタスクの開始 (20 ticks = 1秒ごと)
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerLocations, 20L, 20L);
        
        container.register(QuestModule.class, this);
    }

    private void checkPlayerLocations() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data == null || data.getActiveQuests().isEmpty()) continue;

            for (ActiveQuest active : data.getActiveQuests().values()) {
                QuestDefinition def = getQuest(active.getQuestId());
                if (def == null) continue;

                for (int i = 0; i < def.getObjectives().size(); i++) {
                    com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective obj = def.getObjectives().get(i);
                    if (obj instanceof ReachLocationObjective) {
                        ReachLocationObjective reach = (ReachLocationObjective) obj;
                        if (reach.isCompleted(active, i)) continue;

                        org.bukkit.Location loc = player.getLocation();
                        if (loc.getWorld().getName().equalsIgnoreCase(reach.getWorldName())) {
                            double dx = loc.getX() - reach.getX();
                            double dy = loc.getY() - reach.getY();
                            double dz = loc.getZ() - reach.getZ();
                            double distSq = dx*dx + dy*dy + dz*dz;

                            if (distSq <= reach.getRadiusSquared()) {
                                Map<String, Object> params = new HashMap<>();
                                params.put("locationName", reach.getLocationName());
                                eventBus.fire(player, data, QuestTrigger.LOCATION_REACHED, params);
                                player.sendMessage(net.kyori.adventure.text.Component.text("地点に到達しました: " + reach.getLocationName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                            }
                        }
                    }
                }
            }
        }
    }

    private void setupEventBus() {
        for (QuestTrigger trigger : QuestTrigger.values()) {
            eventBus.register(trigger, (player, data, t, params) -> {
                boolean changed = false;
                for (ActiveQuest activeQuest : data.getActiveQuests().values()) {
                    QuestDefinition def = getQuest(activeQuest.getQuestId());
                    if (def == null) continue;

                    for (int i = 0; i < def.getObjectives().size(); i++) {
                        QuestObjective objective = def.getObjectives().get(i);
                        if (objective.updateProgress(player, data, activeQuest, i, t, params)) {
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    data.setDirty(true);
                }
            });
        }
    }

    @Override
    public void onDisable() {
        quests.clear();
    }

    /**
     * クエストを完了させ、報酬を付与する
     */
    public void completeQuest(Player player, PlayerData data, QuestDefinition quest) {
        if (!data.getActiveQuests().containsKey(quest.getId())) return;
        
        ActiveQuest active = data.getActiveQuests().get(quest.getId());
        
        // 全ての目標が完了しているか最終チェック
        for (int i = 0; i < quest.getObjectives().size(); i++) {
            if (!quest.getObjectives().get(i).isCompleted(active, i)) {
                return;
            }
        }

        // 報酬の付与
        for (com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.QuestReward reward : quest.getRewards()) {
            reward.apply(player, data, this);
        }

        // 状態の更新
        data.completeQuest(quest.getId());
        data.setDirty(true);
        
        player.sendMessage(net.kyori.adventure.text.Component.text("クエストを完了しました: " + quest.getDisplayName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public void loadQuests() {
        quests.clear();
        File questDir = new File(plugin.getDataFolder(), "quests");
        if (!questDir.exists()) {
            questDir.mkdirs();
        }

        File[] files = questDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().replace(".yml", "");
            QuestDefinition def = QuestParser.parse(id, config, this);
            if (def != null) {
                quests.put(id, def);
            }
        }
        plugin.getLogger().info("Loaded " + quests.size() + " quests.");
    }

    public QuestEventBus getEventBus() {
        return eventBus;
    }

    public QuestDefinition getQuest(String id) {
        return quests.get(id);
    }

    public Collection<QuestDefinition> getAllQuests() {
        return quests.values();
    }

    public List<QuestDefinition> getQuestsByTrader(String traderId) {
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : quests.values()) {
            if (q.getTraderId().equalsIgnoreCase(traderId)) {
                result.add(q);
            }
        }
        return result;
    }

    /**
     * プレイヤーがクエストを開始できるか確認
     */
    public boolean canStart(PlayerData data, QuestDefinition quest) {
        if (data.isQuestCompleted(quest.getId()) || data.getActiveQuests().containsKey(quest.getId())) {
            return false;
        }
        
        for (QuestCondition condition : quest.getConditions()) {
            if (!condition.isMet(data)) {
                return false;
            }
        }
        return true;
    }
}
