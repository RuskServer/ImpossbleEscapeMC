package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TraderCommand implements CommandExecutor, TabCompleter {
    private final TraderModule traderModule;

    public TraderCommand(TraderModule traderModule) {
        this.traderModule = traderModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("使用法: /trader <open|reload> [traderId]", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("impossbleescape.admin")) {
                player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                return true;
            }
            traderModule.loadTraders();
            player.sendMessage(Component.text("トレーダー設定をリロードしました。", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("open")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("トレーダーIDを指定してください。", NamedTextColor.RED));
                return true;
            }

            TraderDefinition trader = traderModule.getTrader(args[1]);
            if (trader == null) {
                player.sendMessage(Component.text("指定されたトレーダーが見つかりません。", NamedTextColor.RED));
                return true;
            }

            new TraderGUI(traderModule, trader, player).open();
            return true;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
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
