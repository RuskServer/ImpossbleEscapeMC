package com.lunar_prototype.impossbleEscapeMC.modules.scoreboard;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤー個別のスコアボードを管理するクラス
 */
public class ScoreboardHandler {
    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Team> lines = new ArrayList<>();

    private final Team nameTagTeam;

    public ScoreboardHandler(Player player) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("sidebar", Criteria.DUMMY, 
            Component.empty()); // タイトルを空にする
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // ネームプレート非表示用のチーム
        this.nameTagTeam = scoreboard.registerNewTeam("hide_tags");
        this.nameTagTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        this.nameTagTeam.setCanSeeFriendlyInvisibles(false);

        // 15行の空の行を初期化（チームとエントリーの登録のみ）
        for (int i = 0; i < 15; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team team = scoreboard.registerNewTeam("line_" + i);
            team.addEntry(entry);
            lines.add(team);
        }

        player.setScoreboard(scoreboard);
    }

    /**
     * ネームプレートの表示状態を更新
     */
    public void updateNameTags(boolean hideAll) {
        if (hideAll) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                if (!nameTagTeam.hasEntry(other.getName())) {
                    nameTagTeam.addEntry(other.getName());
                }
            }
        } else {
            for (String entry : new ArrayList<>(nameTagTeam.getEntries())) {
                nameTagTeam.removeEntry(entry);
            }
        }
    }

    /**
     * スコアボードの内容を更新
     */
    public void update(PlayerData data, boolean isInRaid) {
        List<Component> content = new ArrayList<>();
        content.add(Component.empty());

        if (!isInRaid) {
            content.add(Component.text("Level: ", NamedTextColor.GRAY).append(Component.text(data.getLevel(), NamedTextColor.WHITE)));
            content.add(Component.text("Balance: ", NamedTextColor.GRAY).append(Component.text("$" + (int) data.getBalance(), NamedTextColor.GOLD)));
            content.add(Component.empty());
        }

        // 重量表示
        double weightKg = data.getCurrentWeight() / 1000.0;
        content.add(Component.text("Weight: ", NamedTextColor.GRAY)
            .append(Component.text(String.format("%.1f", weightKg) + "kg", data.getWeightStage().getColor())));
        
        content.add(Component.empty());

        content.add(Component.text("STATUS:", NamedTextColor.YELLOW, TextDecoration.BOLD));

        boolean hasStatus = false;
        
        // 状態異常のリスト構築
        if (data.getBleedingLevel() > 0) {
            content.add(Component.text(" - Bleeding (Lvl " + data.getBleedingLevel() + ")", NamedTextColor.RED));
            hasStatus = true;
        }
        if (data.hasLegFracture()) {
            content.add(Component.text(" - Leg Fracture", NamedTextColor.RED));
            hasStatus = true;
        }
        if (data.hasArmFracture()) {
            content.add(Component.text(" - Arm Fracture", NamedTextColor.RED));
            hasStatus = true;
        }
        if (data.isPainkillerActive()) {
            long remaining = (data.getPainkillerUntil() - System.currentTimeMillis()) / 1000;
            content.add(Component.text(" - Painkiller (" + remaining + "s)", NamedTextColor.AQUA));
            hasStatus = true;
        }

        if (!hasStatus) {
            content.add(Component.text(" - None", NamedTextColor.GRAY));
        }

        // 実際にTeamのPrefixを更新して画面に反映
        for (int i = 0; i < 15; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team team = lines.get(i);
            
            if (i < content.size()) {
                team.prefix(content.get(i));
                // スコアを設定することで行を表示させる
                objective.getScore(entry).setScore(content.size() - i);
            } else {
                // スコアをリセットすることで行を非表示にする
                scoreboard.resetScores(entry);
            }
        }
    }
}
