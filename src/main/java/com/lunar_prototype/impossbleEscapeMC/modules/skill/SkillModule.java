package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * スキルツリー: お金と素材を払ってノードを習得する (docs/SKILL_TREE.md)。
 * 各モジュールは {@link #value(UUID, SkillStat)} で、習得したノードの値の合計を聞いて自分の数値に掛ける・足す
 */
public final class SkillModule implements IModule, Listener {
    private static SkillModule instance;

    private final ImpossbleEscapeMC plugin;
    private ServiceContainer container;
    private PlayerDataModule dataModule;
    /** 習得したノードの値の合計 (習得した時・抜けた時に作り直す) */
    private final Map<UUID, EnumMap<SkillStat, Double>> totals = new ConcurrentHashMap<>();

    public SkillModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable(ServiceContainer container) {
        this.container = container;
        this.dataModule = container.get(PlayerDataModule.class);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        SkillTreeDialog.init(this);
        instance = this;
        for (Player player : Bukkit.getOnlinePlayers()) syncGunScores(player);
    }

    @Override
    public void onDisable() {
        instance = null;
        totals.clear();
    }

    /** 習得したノードの、その値の合計 (スキルが無い・データが無い・オンラインのプレイヤーでない時は 0) */
    public static double value(UUID player, SkillStat stat) {
        SkillModule module = instance;
        if (module == null || player == null) return 0;
        EnumMap<SkillStat, Double> map = module.totals.get(player);
        if (map == null) {
            // SCAV などプレイヤーでない物のデータを作らない
            if (Bukkit.getPlayer(player) == null) return 0;
            map = module.totals.computeIfAbsent(player, module::compute);
        }
        return map.getOrDefault(stat, 0.0);
    }

    public static double value(Player player, SkillStat stat) {
        return player == null ? 0 : value(player.getUniqueId(), stat);
    }

    /** データから直接数える (オフラインのプレイヤーにも使える。キャッシュしない) */
    public static double value(PlayerData data, SkillStat stat) {
        if (data == null) return 0;
        double sum = 0;
        for (String id : data.getSkills()) {
            SkillNode node = SkillCatalog.get(id);
            if (node != null) sum += node.stats().getOrDefault(stat, 0.0);
        }
        return sum;
    }

    /** 割合の値を倍率にする (1 + 合計) */
    public static double multiplier(UUID player, SkillStat stat) {
        return 1 + value(player, stat);
    }

    public static double multiplier(Player player, SkillStat stat) {
        return 1 + value(player, stat);
    }

    /** 重さの段階 (スキルで「軽い」の上限が上がる) */
    public static WeightStage weightStage(Player player, int weightGrams) {
        WeightStage stage = WeightStage.getFromWeight(weightGrams);
        if (stage == WeightStage.NORMAL && weightGrams <= WeightStage.LIGHT.getUpperLimit() + value(player, SkillStat.LIGHT_LIMIT_G)) {
            return WeightStage.LIGHT;
        }
        return stage;
    }

    private EnumMap<SkillStat, Double> compute(UUID player) {
        EnumMap<SkillStat, Double> map = new EnumMap<>(SkillStat.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player) : null;
        if (data == null) return map;
        for (String id : data.getSkills()) {
            SkillNode node = SkillCatalog.get(id);
            if (node == null) continue;
            node.stats().forEach((stat, v) -> map.merge(stat, v, Double::sum));
        }
        return map;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        totals.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        syncGunScores(event.getPlayer());
    }

    // ---- 射撃のスキル (データパック Toi's Armory の銃の数値) ----

    /**
     * 射撃のスキルの値と、データパックの倍率のスコア (toisarm.mod.<名前>、千分率) の対応。データパックは銃を読み込んだ時に
     * toisarm:player/load_gun_data/get/modifiers でこの倍率を掛ける (次に銃を持ち替えた時から効く)
     */
    private static final Map<SkillStat, String> GUN_SCORES = Map.ofEntries(
            Map.entry(SkillStat.RELOAD_TIME, "reload"), Map.entry(SkillStat.EMPTY_RELOAD_TIME, "empty_reload"),
            Map.entry(SkillStat.SWAP_TIME, "swap"), Map.entry(SkillStat.ADS_TIME, "ads"), Map.entry(SkillStat.SPRINT_TO_FIRE, "sprint"),
            Map.entry(SkillStat.BOLT_ACTION, "bolt_action"), Map.entry(SkillStat.RECOIL_PITCH, "pitch_recoil"),
            Map.entry(SkillStat.RECOIL_YAW, "yaw_recoil"), Map.entry(SkillStat.HIPFIRE_SPREAD, "accuracy_idle"),
            Map.entry(SkillStat.MOVE_SPREAD, "accuracy_move"), Map.entry(SkillStat.BLOOM_INCREASE, "bloom_increase"),
            Map.entry(SkillStat.BLOOM_DECAY, "bloom_decay"), Map.entry(SkillStat.SWAY, "sway"), Map.entry(SkillStat.SHAKE, "shake"));

    /** プレイヤーの射撃のスキルを、データパックの倍率のスコアに書く (スキルが無ければスコアを消す = そのまま) */
    void syncGunScores(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Map.Entry<SkillStat, String> e : GUN_SCORES.entrySet()) {
            String name = "toisarm.mod." + e.getValue();
            Objective objective = board.getObjective(name);
            if (objective == null) objective = board.registerNewObjective(name, Criteria.DUMMY, Component.text(name));
            double multiplier = Math.max(0.05, multiplier(player, e.getKey()));
            Score score = objective.getScore(player);
            if (Math.abs(multiplier - 1) < 1e-9) {
                score.resetScore();
            } else {
                score.setScore((int) Math.round(multiplier * 1000));
            }
        }
    }

    // ---- 習得 ----

    /** ノードの状態 (習得済み・設備が足りない・レベルが足りない・前提が足りない・習得できる) */
    public enum Status {
        LEARNED, FACILITY, LEVEL, PREREQUISITE, AVAILABLE
    }

    public static Status status(PlayerData data, SkillNode node) {
        if (data.hasSkill(node.id())) return Status.LEARNED;
        if (data.getHideoutLevel(HideoutFacility.SKILL_TREE.id) < SkillCatalog.facilityLevel(node.tier())) return Status.FACILITY;
        if (!prerequisitesMet(data, node)) return Status.PREREQUISITE;
        if (data.getLevel() < SkillCatalog.playerLevel(node.tier())) return Status.LEVEL;
        return Status.AVAILABLE;
    }

    public static boolean prerequisitesMet(PlayerData data, SkillNode node) {
        if (node.requires().isEmpty()) return true;
        int met = 0;
        for (String id : node.requires()) {
            if (data.hasSkill(id)) met++;
        }
        return node.anyOf() ? met > 0 : met == node.requires().size();
    }

    PlayerDataModule dataModule() {
        return dataModule;
    }

    EconomyModule economy() {
        return container.get(EconomyModule.class);
    }

    HideoutModule hideout() {
        return container.get(HideoutModule.class);
    }

    ImpossbleEscapeMC plugin() {
        return plugin;
    }

    /**
     * ノードを習得する: お金を取り、素材を取り (取れなければお金を返す)、保存する。
     *
     * @return 習得できなかった理由 (習得したら null)
     */
    String learn(Player player, PlayerData data, SkillNode node) {
        Status status = status(data, node);
        switch (status) {
            case LEARNED -> {
                return "習得済みです";
            }
            case FACILITY -> {
                return "スキルツリーの設備 Lv" + SkillCatalog.facilityLevel(node.tier()) + " が必要です";
            }
            case PREREQUISITE -> {
                return "前のスキルが必要です";
            }
            case LEVEL -> {
                return "レベル " + SkillCatalog.playerLevel(node.tier()) + " が必要です";
            }
            default -> {
            }
        }
        EconomyModule economy = economy();
        HideoutModule hideout = hideout();
        if (economy == null || hideout == null) return "今は習得できません";
        Map<String, Integer> have = hideout.countMaterials(player, data, node.materials());
        for (HideoutFacility.ItemCost cost : node.materials()) {
            if (have.getOrDefault(cost.itemId(), 0) < cost.amount()) return "素材が足りません";
        }
        if (!economy.withdraw(player.getUniqueId(), node.money())) return "所持金が足りません";
        try {
            if (!hideout.takeMaterials(player, data, node.materials())) {
                economy.deposit(player.getUniqueId(), node.money());
                return "素材が足りません";
            }
        } catch (IOException e) {
            economy.deposit(player.getUniqueId(), node.money());
            plugin.getLogger().log(Level.WARNING, "スキルの素材を取れませんでした", e);
            return "素材を取れませんでした";
        }
        data.addSkill(node.id());
        totals.remove(player.getUniqueId());
        syncGunScores(player);
        dataModule.saveAsync(player.getUniqueId());
        return null;
    }
}
