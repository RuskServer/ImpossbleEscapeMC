package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsCommand implements CommandExecutor, TabCompleter {

    private final PlayerDataModule playerDataModule;

    public SettingsCommand(PlayerDataModule playerDataModule) {
        this.playerDataModule = playerDataModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        PlayerData data = playerDataModule.getPlayerData(player.getUniqueId());

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "ads_sprint":
                if (args.length < 2) {
                    player.sendMessage(Component.text("現在の ADSダッシュ解除: ", NamedTextColor.YELLOW)
                            .append(Component.text(data.isCancelAdsOnSprint() ? "ON" : "OFF",
                                    data.isCancelAdsOnSprint() ? NamedTextColor.GREEN : NamedTextColor.RED)));
                    return true;
                }
                boolean on = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
                data.setCancelAdsOnSprint(on);
                player.sendMessage(Component.text("ADSダッシュ解除を設定しました: ", NamedTextColor.GREEN)
                        .append(Component.text(on ? "ON" : "OFF", NamedTextColor.YELLOW)));
                break;

            case "keybind":
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用方法: /settings keybind <action> <key>", NamedTextColor.RED));
                    player.sendMessage(Component.text("現在のアクション設定:", NamedTextColor.YELLOW));
                    data.getKeybinds().forEach((action, key) -> {
                        player.sendMessage(Component.text("- " + action + ": " + key, NamedTextColor.WHITE));
                    });
                    return true;
                }
                String action = args[1].toUpperCase();
                String key = args[2].toUpperCase();

                // 許可されているアクションかチェック
                List<String> validActions = Arrays.asList("RELOAD", "FIREMODE");
                if (!validActions.contains(action)) {
                    player.sendMessage(Component.text("無効なアクションです。有効なアクション: " + String.join(", ", validActions), NamedTextColor.RED));
                    return true;
                }

                // 許可されているキーかチェック
                List<String> validKeys = Arrays.asList("DROP", "SWAP_HAND", "LEFT_CLICK_SNEAK");
                if (!validKeys.contains(key)) {
                    player.sendMessage(Component.text("無効なキーです。有効なキー: " + String.join(", ", validKeys), NamedTextColor.RED));
                    return true;
                }

                data.setKeybind(action, key);
                player.sendMessage(Component.text(action + " の操作を " + key + " に設定しました。", NamedTextColor.GREEN));
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== 操作設定 (Settings) ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/settings ads_sprint <on|off> - ダッシュでADSを解除するかどうか", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/settings keybind <action> <key> - キーバインドを変更", NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;

        if (args.length == 1) {
            String[] subCommands = {"ads_sprint", "keybind"};
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("ads_sprint")) {
                completions.addAll(Arrays.asList("on", "off"));
            } else if (args[0].equalsIgnoreCase("keybind")) {
                completions.addAll(Arrays.asList("RELOAD", "FIREMODE"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("keybind")) {
            completions.addAll(Arrays.asList("DROP", "SWAP_HAND", "LEFT_CLICK_SNEAK"));
        }

        return completions;
    }
}
