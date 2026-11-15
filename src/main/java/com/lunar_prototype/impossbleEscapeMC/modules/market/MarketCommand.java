package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
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

    private final JavaPlugin plugin;
    private final EconomyModule economy;

    public MarketCommand(MarketModule marketModule, LevelModule levelModule, JavaPlugin plugin, EconomyModule economy) {
        this.marketModule = marketModule;
        this.plugin = plugin;
        this.economy = economy;
        this.levelModule = levelModule;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.market.marketcommand.execute.notice_001", MessageText.of("iem.market.marketcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.market.marketcommand.execute.notice_001.text_01"))));
            return;
        }

        int level = levelModule.getLevel(player.getUniqueId());
        if (level < 10) {
            Messages.chat(player, "iem.market.marketcommand.execute.notice_002", MessageText.of("iem.market.marketcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.market.marketcommand.execute.notice_002.text_01", level).color(NamedTextColor.RED))));
            return;
        }

        new MarketMainGUI(player, marketModule, plugin, economy).open();
        return;
    }
}
