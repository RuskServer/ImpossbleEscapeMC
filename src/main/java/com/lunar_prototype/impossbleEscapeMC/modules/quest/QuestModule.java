package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataLoadedEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestEventBus;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Sound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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

    public enum NotificationSource {
        PLAYER_JOIN,
        RAID_EXTRACT,
        RAID_MIA,
        RAID_DEATH_FAILURE
    }

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
        plugin.getServer().getPluginManager().registerEvents(new PlayerDataLoadedJoinListener(), plugin);
        
        // 位置チェックタスクの開始 (20 ticks = 1秒ごと)
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerLocations, 20L, 20L);
        
        container.register(QuestModule.class, this);
    }

    private class PlayerDataLoadedJoinListener implements Listener {
        @EventHandler
        public void onPlayerDataLoaded(PlayerDataLoadedEvent event) {
            notifyQuestAvailability(event.getPlayer(), event.getPlayerData(), NotificationSource.PLAYER_JOIN);
        }
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
                        String targetWorld = reach.getWorldName();
                        if (targetWorld != null && loc.getWorld().getName().equalsIgnoreCase(targetWorld)) {
                            double dx = loc.getX() - reach.getX();
                            double dy = loc.getY() - reach.getY();
                            double dz = loc.getZ() - reach.getZ();
                            double distSq = dx*dx + dy*dy + dz*dz;

                            if (distSq <= reach.getRadiusSquared()) {
                                String name = reach.getLocationName() != null ? reach.getLocationName() : "指定地点";
                                Map<String, Object> params = new HashMap<>();
                                params.put("locationName", reach.getLocationName());
                                eventBus.fire(player, data, QuestTrigger.LOCATION_REACHED, params);
                                player.sendMessage(net.kyori.adventure.text.Component.text("地点に到達しました: " + name, net.kyori.adventure.text.format.NamedTextColor.GREEN));
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

    /**
     * GUI上の「報告可能」（= ActiveQuest の全objectiveが完了済み）かどうか。
     */
    public boolean isAllObjectivesMet(QuestDefinition q, ActiveQuest active) {
        for (int i = 0; i < q.getObjectives().size(); i++) {
            if (!q.getObjectives().get(i).isCompleted(active, i)) return false;
        }
        return true;
    }

    public void notifyQuestAvailability(Player player, NotificationSource source) {
        if (dataModule == null || player == null) return;
        notifyQuestAvailability(player, dataModule.getPlayerData(player.getUniqueId()), source);
    }

    public void notifyQuestAvailability(Player player, PlayerData data, NotificationSource source) {
        if (player == null || data == null) return;

        List<QuestDefinition> startable = getStartableQuests(data);
        List<QuestDefinition> reportable = getReportableQuests(data);
        if (startable.isEmpty() && reportable.isEmpty()) return;

        String header;
        NamedTextColor headerColor;
        switch (source) {
            case PLAYER_JOIN -> {
                header = "クエスト状況を確認しました。";
                headerColor = NamedTextColor.AQUA;
            }
            case RAID_EXTRACT -> {
                header = "レイド帰還後、クエストに進展があります。";
                headerColor = NamedTextColor.GOLD;
            }
            case RAID_MIA -> {
                header = "レイド終了後、クエストに報告可能なものがあります。";
                headerColor = NamedTextColor.RED;
            }
            case RAID_DEATH_FAILURE -> {
                header = "死亡後の復帰中、クエストに報告可能なものがあります。";
                headerColor = NamedTextColor.RED;
            }
            default -> {
                header = "クエスト情報";
                headerColor = NamedTextColor.GRAY;
            }
        }

        player.sendMessage(Component.text(header, headerColor));

        if (!startable.isEmpty()) {
            player.sendMessage(Component.text(
                    "受領できるクエストがあります: " + formatQuestTitles(startable) +
                            "。PDAのトレーダー一覧→クエスト一覧で確認してください。",
                    NamedTextColor.GREEN
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        if (!reportable.isEmpty()) {
            player.sendMessage(Component.text(
                    "報酬を受け取れる（報告可能）クエストがあります: " + formatQuestTitles(reportable) +
                            "。トレーダーのクエスト一覧で左クリックして完了できます。",
                    NamedTextColor.YELLOW
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    public List<QuestDefinition> getStartableQuests(PlayerData data) {
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : quests.values()) {
            if (canStart(data, q)) result.add(q);
        }
        return result;
    }

    public List<QuestDefinition> getReportableQuests(PlayerData data) {
        List<QuestDefinition> result = new ArrayList<>();
        for (Map.Entry<String, ActiveQuest> entry : data.getActiveQuests().entrySet()) {
            QuestDefinition def = getQuest(entry.getKey());
            if (def == null) continue;
            if (isAllObjectivesMet(def, entry.getValue())) result.add(def);
        }
        return result;
    }

    private String formatQuestTitles(List<QuestDefinition> quests) {
        int limit = 3;
        int realLimit = Math.min(limit, quests.size());
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < realLimit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(quests.get(i).getDisplayName());
        }

        if (quests.size() > limit) sb.append(" ほか");
        return sb.toString();
    }
}
