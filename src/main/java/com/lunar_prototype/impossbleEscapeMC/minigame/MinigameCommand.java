package com.lunar_prototype.impossbleEscapeMC.minigame;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

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
            Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_001", MessageText.of("iem.minigame.minigamecommand.execute.notice_001", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_001.text_01"))));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "loadout":
                if (args.length < 2) {
                    Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_002", MessageText.of("iem.minigame.minigamecommand.execute.notice_002", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_002.text_01"))));
                    return;
                }
                manager.setLoadout(player, args[1]);
                break;
            case "create":
                if (args.length < 2) {
                    Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_003", MessageText.of("iem.minigame.minigamecommand.execute.notice_003", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_003.text_01"))));
                    return;
                }
                manager.createMap(args[1]);
                Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_004", MessageText.of("iem.minigame.minigamecommand.execute.notice_004", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_004.text_01", args[1]))));
                break;
            case "setspawn":
                if (args.length < 3) {
                    Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_005", MessageText.of("iem.minigame.minigamecommand.execute.notice_005", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_005.text_01"))));
                    return;
                }
                MinigameMap map = manager.getMap(args[1]);
                if (map == null) {
                    Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_006", MessageText.of("iem.minigame.minigamecommand.execute.notice_006", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_006.text_01"))));
                    return;
                }
                int team = Integer.parseInt(args[2]);
                map.addSpawn(team, player.getLocation());
                manager.saveMaps();
                Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_007", MessageText.of("iem.minigame.minigamecommand.execute.notice_007", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_007.text_01", team, args[1]))));
                break;
            case "split":
                manager.splitTeams();
                break;
            case "start":
                if (args.length < 2) {
                    Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_008", MessageText.of("iem.minigame.minigamecommand.execute.notice_008", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_008.text_01"))));
                    return;
                }
                int rounds = 2;
                if (args.length >= 3) {
                    try {
                        rounds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_009", MessageText.of("iem.minigame.minigamecommand.execute.notice_009", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_009.text_01"))));
                        return;
                    }
                }
                manager.startGame(args[1], rounds);
                break;
            case "stop":
                manager.stopGame(true);
                Messages.chat(player, "iem.minigame.minigamecommand.execute.notice_010", MessageText.of("iem.minigame.minigamecommand.execute.notice_010", MessageText.payload(MessageText.of("iem.minigame.minigamecommand.execute.notice_010.text_01"))));
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
