package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.util.DatapackFunctionUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatapackTestCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("op")) {
            player.sendMessage("§cYou must be an OP to run this test command.");
            return true;
        }

        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String subMode = args[0].toLowerCase();

        if (subMode.equals("func")) {
            String functionPath = args[1];
            player.sendMessage("§aExecuting function and capturing items: §f" + functionPath);

            try {
                List<ItemStack> items = DatapackFunctionUtil.captureItemsFromFunction(player.getWorld(), functionPath);
                if (items.isEmpty()) {
                    player.sendMessage("§eNo items were captured from function execution.");
                } else {
                    player.sendMessage("§aCaptured §f" + items.size() + "§a item(s):");
                    for (ItemStack item : items) {
                        player.sendMessage("§7- §f" + item.getType() + " x" + item.getAmount());
                        player.getInventory().addItem(item);
                    }
                }
            } catch (Exception e) {
                player.sendMessage("§cAn error occurred: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (subMode.equals("gun")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /datapacktest gun <gunId> <displayName>");
                return true;
            }

            String gunId = args[1];
            // 3枚目以降の引数を結合して表示名にする
            StringBuilder displayNameBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) displayNameBuilder.append(" ");
                displayNameBuilder.append(args[i]);
            }
            String displayName = displayNameBuilder.toString();

            player.sendMessage("§aGenerating gun item: §f" + gunId + " (" + displayName + ")");

            try {
                ItemStack gun = DatapackFunctionUtil.generateGunItem(player.getWorld(), gunId, displayName);
                if (gun == null) {
                    player.sendMessage("§cFailed to generate the gun item. See console for details.");
                } else {
                    player.getInventory().addItem(gun);
                    player.sendMessage("§aSuccessfully generated and gave you: §f" + gun.getType() + " (Custom model/NBT applied)");
                }
            } catch (Exception e) {
                player.sendMessage("§cAn error occurred: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§7=== Datapack Test Command Help ===");
        player.sendMessage("§f/datapacktest func <namespace:path> §7- Capture items from datapack function");
        player.sendMessage("§f/datapacktest gun <gunId> <displayName> §7- Generate custom gun item stack");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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
