package com.lunar_prototype.impossbleEscapeMC.minigame;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "mg", description = "ミニゲーム管理コマンド", permission = "op")
public class MinigameCommand implements BasicCommand {
    private final MinigameManager manager;

    public MinigameCommand(MinigameManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) return;
        if (!player.isOp()) return;

        if (args.length == 0) {
            player.sendMessage("§c/mg <create/setspawn/split/start/stop/loadout>");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "loadout":
                if (args.length < 2) {
                    player.sendMessage("§c/mg loadout <m4a1/ak74/m700>");
                    return;
                }
                manager.setLoadout(player, args[1]);
                break;
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§c/mg create <name>");
                    return;
                }
                manager.createMap(args[1]);
                player.sendMessage("§aMap '" + args[1] + "' created.");
                break;
            case "setspawn":
                if (args.length < 3) {
                    player.sendMessage("§c/mg setspawn <name> <1/2>");
                    return;
                }
                MinigameMap map = manager.getMap(args[1]);
                if (map == null) {
                    player.sendMessage("§cMap not found.");
                    return;
                }
                int team = Integer.parseInt(args[2]);
                map.addSpawn(team, player.getLocation());
                manager.saveMaps();
                player.sendMessage("§aSpawn added for Team " + team + " in map " + args[1]);
                break;
            case "split":
                manager.splitTeams();
                break;
            case "start":
                if (args.length < 2) {
                    player.sendMessage("§c/mg start <name> [rounds]");
                    return;
                }
                int rounds = 2;
                if (args.length >= 3) {
                    try {
                        rounds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid rounds number.");
                        return;
                    }
                }
                manager.startGame(args[1], rounds);
                break;
            case "stop":
                manager.stopGame(true);
                player.sendMessage("§cGame stopped.");
                break;
        }

        return;
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            return Arrays.asList("create", "setspawn", "split", "start", "stop", "loadout").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setspawn") || sub.equals("start")) {
                return manager.getMapNames().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (sub.equals("loadout")) {
                return Arrays.asList("m4a1", "ak74", "m700", "as_val", "mossberg_590", "mp5", "scar_h", "scorpion_evo3", "svd", "uzi").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setspawn")) {
                return Arrays.asList("1", "2");
            } else if (args[0].equalsIgnoreCase("start")) {
                return Arrays.asList("1", "2", "3", "5");
            }
        }

        return new ArrayList<>();
    }
}
