package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "trader", description = "トレーダーシステム管理・利用コマンド", permission = "op")
public class TraderCommand implements BasicCommand {
    private final TraderModule traderModule;

    public TraderCommand(TraderModule traderModule) {
        this.traderModule = traderModule;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.trader.tradercommand.execute.notice_001", MessageText.of("iem.trader.tradercommand.execute.notice_001", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_001.text_01"))));
            return;
        }

        if (args.length < 1) {
            Messages.chat(player, "iem.trader.tradercommand.execute.notice_002", MessageText.of("iem.trader.tradercommand.execute.notice_002", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("impossbleescape.admin")) {
                Messages.chat(player, "iem.trader.tradercommand.execute.notice_003", MessageText.of("iem.trader.tradercommand.execute.notice_003", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_003.text_01").color(NamedTextColor.RED))));
                return;
            }
            traderModule.loadTraders();
            Messages.chat(player, "iem.trader.tradercommand.execute.notice_004", MessageText.of("iem.trader.tradercommand.execute.notice_004", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_004.text_01").color(NamedTextColor.GREEN))));
            return;
        }

        if (args[0].equalsIgnoreCase("open")) {
            if (args.length < 2) {
                Messages.chat(player, "iem.trader.tradercommand.execute.notice_005", MessageText.of("iem.trader.tradercommand.execute.notice_005", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_005.text_01").color(NamedTextColor.RED))));
                return;
            }

            TraderDefinition trader = traderModule.getTrader(args[1]);
            if (trader == null) {
                Messages.chat(player, "iem.trader.tradercommand.execute.notice_006", MessageText.of("iem.trader.tradercommand.execute.notice_006", MessageText.payload(MessageText.of("iem.trader.tradercommand.execute.notice_006.text_01").color(NamedTextColor.RED))));
                return;
            }

            TraderDialog.open(traderModule, trader, player);
            return;
        }

        return;
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            return List.of("open", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return traderModule.getAllTraders().stream()
                    .map(t -> t.id)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
