package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(name = "settings", description = "プレイヤーの操作設定やキーバインドを変更します。")
public class SettingsCommand implements BasicCommand {

    private final PlayerDataModule playerDataModule;

    public SettingsCommand(PlayerDataModule playerDataModule) {
        this.playerDataModule = playerDataModule;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.system.settingscommand.execute.notice_001", MessageText.of("iem.system.settingscommand.execute.notice_001", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_001.text_01"))));
            return;
        }

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        PlayerData data = playerDataModule.getPlayerData(player.getUniqueId());

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "ads_sprint":
                if (args.length < 2) {
                    Messages.chat(player, "iem.system.settingscommand.execute.notice_002", MessageText.of("iem.system.settingscommand.execute.notice_002", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_002.text_01").color(NamedTextColor.YELLOW)
                            .append(MessageText.of("iem.system.settingscommand.execute.notice_002.text_02", data.isCancelAdsOnSprint() ? "ON" : "OFF").color(data.isCancelAdsOnSprint() ? NamedTextColor.GREEN : NamedTextColor.RED)))));
                    return;
                }
                boolean on = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
                data.setCancelAdsOnSprint(on);
                Messages.chat(player, "iem.system.settingscommand.execute.notice_003", MessageText.of("iem.system.settingscommand.execute.notice_003", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_003.text_01").color(NamedTextColor.GREEN)
                        .append(MessageText.of("iem.system.settingscommand.execute.notice_003.text_02", on ? "ON" : "OFF").color(NamedTextColor.YELLOW)))));
                break;

            case "keybind":
                if (args.length < 3) {
                    Messages.chat(player, "iem.system.settingscommand.execute.notice_004", MessageText.of("iem.system.settingscommand.execute.notice_004", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_004.text_01").color(NamedTextColor.RED))));
                    Messages.chat(player, "iem.system.settingscommand.execute.notice_005", MessageText.of("iem.system.settingscommand.execute.notice_005", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_005.text_01").color(NamedTextColor.YELLOW))));
                    data.getKeybinds().forEach((action, key) -> {
                        Messages.chat(player, "iem.system.settingscommand.execute.notice_006", MessageText.of("iem.system.settingscommand.execute.notice_006", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_006.text_01", action, key).color(NamedTextColor.WHITE))));
                    });
                    return;
                }
                String action = args[1].toUpperCase();
                String key = args[2].toUpperCase();

                // 許可されているアクションかチェック
                List<String> validActions = Arrays.asList("RELOAD", "FIREMODE");
                if (!validActions.contains(action)) {
                    Messages.chat(player, "iem.system.settingscommand.execute.notice_007", MessageText.of("iem.system.settingscommand.execute.notice_007", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_007.text_01", String.join(", ", validActions)).color(NamedTextColor.RED))));
                    return;
                }

                // 許可されているキーかチェック
                List<String> validKeys = Arrays.asList("DROP", "SWAP_HAND", "LEFT_CLICK_SNEAK");
                if (!validKeys.contains(key)) {
                    Messages.chat(player, "iem.system.settingscommand.execute.notice_008", MessageText.of("iem.system.settingscommand.execute.notice_008", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_008.text_01", String.join(", ", validKeys)).color(NamedTextColor.RED))));
                    return;
                }

                data.setKeybind(action, key);
                Messages.chat(player, "iem.system.settingscommand.execute.notice_009", MessageText.of("iem.system.settingscommand.execute.notice_009", MessageText.payload(MessageText.of("iem.system.settingscommand.execute.notice_009.text_01", action, key).color(NamedTextColor.GREEN))));
                break;

            default:
                sendHelp(player);
                break;
        }

        return;
    }

    private void sendHelp(Player player) {
        Messages.chat(player, "iem.system.settingscommand.sendhelp.notice_001", MessageText.of("iem.system.settingscommand.sendhelp.notice_001", MessageText.payload(MessageText.of("iem.system.settingscommand.sendhelp.notice_001.text_01").color(NamedTextColor.AQUA))));
        Messages.chat(player, "iem.system.settingscommand.sendhelp.notice_002", MessageText.of("iem.system.settingscommand.sendhelp.notice_002", MessageText.payload(MessageText.of("iem.system.settingscommand.sendhelp.notice_002.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.system.settingscommand.sendhelp.notice_003", MessageText.of("iem.system.settingscommand.sendhelp.notice_003", MessageText.payload(MessageText.of("iem.system.settingscommand.sendhelp.notice_003.text_01").color(NamedTextColor.WHITE))));
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
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
