package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Command(name = "market", description = "グローバルマーケットを利用します。")
public class MarketCommand implements BasicCommand {
    private final MarketModule marketModule;
    private final LevelModule levelModule;

    public MarketCommand(MarketModule marketModule, LevelModule levelModule) {
        this.marketModule = marketModule;
        this.levelModule = levelModule;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return;
        }

        int level = levelModule.getLevel(player.getUniqueId());
        if (level < 10) {
            player.sendMessage(Component.text("グローバルマーケットはレベル10から利用可能です。(現在のレベル: " + level + ")", NamedTextColor.RED));
            return;
        }

        new MarketMainGUI(player, marketModule).open();
        return;
    }
}
