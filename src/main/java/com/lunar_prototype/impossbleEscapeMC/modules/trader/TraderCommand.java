package com.lunar_prototype.impossbleEscapeMC.modules.trader;

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
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("使用法: /trader <open|reload> [traderId]", NamedTextColor.RED));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("impossbleescape.admin")) {
                player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                return;
            }
            traderModule.loadTraders();
            player.sendMessage(Component.text("トレーダー設定をリロードしました。", NamedTextColor.GREEN));
            return;
        }

        if (args[0].equalsIgnoreCase("open")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("トレーダーIDを指定してください。", NamedTextColor.RED));
                return;
            }

            TraderDefinition trader = traderModule.getTrader(args[1]);
            if (trader == null) {
                player.sendMessage(Component.text("指定されたトレーダーが見つかりません。", NamedTextColor.RED));
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
