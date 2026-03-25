package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MarketCommand implements CommandExecutor {
    private final MarketModule marketModule;
    private final LevelModule levelModule;

    public MarketCommand(MarketModule marketModule, LevelModule levelModule) {
        this.marketModule = marketModule;
        this.levelModule = levelModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        int level = levelModule.getLevel(player.getUniqueId());
        if (level < 10) {
            player.sendMessage(Component.text("グローバルマーケットはレベル10から利用可能です。(現在のレベル: " + level + ")", NamedTextColor.RED));
            return true;
        }

        new MarketMainGUI(player, marketModule).open();
        return true;
    }
}
