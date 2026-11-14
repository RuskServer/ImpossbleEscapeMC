package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.util.DatapackFunctionUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Command(name = "datapacktest", description = "Test datapack function item capturing.", permission = "op")
public class DatapackTestCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.system.datapacktestcommand.execute.notice_001", MessageText.of("iem.system.datapacktestcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_001.text_01"))));
            return;
        }

        if (!player.hasPermission("op")) {
            Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_002", MessageText.of("iem.system.datapacktestcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_002.text_01"))));
            return;
        }

        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        String subMode = args[0].toLowerCase();

        if (subMode.equals("func")) {
            String functionPath = args[1];
            Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_003", MessageText.of("iem.system.datapacktestcommand.execute.notice_003", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_003.text_01", functionPath))));

            try {
                List<ItemStack> items = DatapackFunctionUtil.captureItemsFromFunction(player.getWorld(), functionPath);
                if (items.isEmpty()) {
                    Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_004", MessageText.of("iem.system.datapacktestcommand.execute.notice_004", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_004.text_01"))));
                } else {
                    Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_005", MessageText.of("iem.system.datapacktestcommand.execute.notice_005", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_005.text_01", items.size()))));
                    for (ItemStack item : items) {
                        Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_006", MessageText.of("iem.system.datapacktestcommand.execute.notice_006", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_006.text_01", item.getType(), item.getAmount()))));
                        player.getInventory().addItem(item);
                    }
                }
            } catch (Exception e) {
                Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_007", MessageText.of("iem.system.datapacktestcommand.execute.notice_007", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_007.text_01", e.getMessage()))));
                e.printStackTrace();
            }
        } else if (subMode.equals("gun")) {
            if (args.length < 3) {
                Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_008", MessageText.of("iem.system.datapacktestcommand.execute.notice_008", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_008.text_01"))));
                return;
            }

            String gunId = args[1];
            // 3枚目以降の引数を結合して表示名にする
            StringBuilder displayNameBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) displayNameBuilder.append(" ");
                displayNameBuilder.append(args[i]);
            }
            String displayName = displayNameBuilder.toString();

            Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_009", MessageText.of("iem.system.datapacktestcommand.execute.notice_009", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_009.text_01", gunId, displayName))));

            try {
                ItemStack gun = DatapackFunctionUtil.generateGunItem(player.getWorld(), gunId, displayName);
                if (gun == null) {
                    Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_010", MessageText.of("iem.system.datapacktestcommand.execute.notice_010", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_010.text_01"))));
                } else {
                    player.getInventory().addItem(gun);
                    Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_011", MessageText.of("iem.system.datapacktestcommand.execute.notice_011", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_011.text_01", gun.getType()))));
                }
            } catch (Exception e) {
                Messages.chat(player, "iem.system.datapacktestcommand.execute.notice_012", MessageText.of("iem.system.datapacktestcommand.execute.notice_012", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.execute.notice_012.text_01", e.getMessage()))));
                e.printStackTrace();
            }
        } else {
            sendHelp(player);
        }

        return;
    }

    private void sendHelp(Player player) {
        Messages.chat(player, "iem.system.datapacktestcommand.sendhelp.notice_001", MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_001", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_001.text_01"))));
        Messages.chat(player, "iem.system.datapacktestcommand.sendhelp.notice_002", MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_002", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_002.text_01"))));
        Messages.chat(player, "iem.system.datapacktestcommand.sendhelp.notice_003", MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_003", MessageText.payload(MessageText.of("iem.system.datapacktestcommand.sendhelp.notice_003.text_01"))));
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if ("func".startsWith(args[0].toLowerCase())) completions.add("func");
            if ("gun".startsWith(args[0].toLowerCase())) completions.add("gun");
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("func")) {
            return List.of("mypack:give_item");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("gun")) {
            return List.of("m4a1", "ak47");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("gun")) {
            return List.of("M4A1", "AK-47");
        }
        return Collections.emptyList();
    }
}
