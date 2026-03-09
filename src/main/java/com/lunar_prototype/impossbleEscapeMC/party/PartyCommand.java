package com.lunar_prototype.impossbleEscapeMC.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PartyCommand implements CommandExecutor, TabCompleter {
    private final PartyManager manager;

    public PartyCommand(PartyManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                manager.createParty(player);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(Component.text("使用法: /party invite <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
                    return true;
                }
                manager.invitePlayer(player, target);
                break;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(Component.text("使用法: /party accept <player>", NamedTextColor.RED));
                    return true;
                }
                manager.acceptInvite(player, args[1]);
                break;
            case "leave":
                manager.leaveParty(player);
                break;
            case "disband":
                Party party = manager.getParty(player.getUniqueId());
                if (party != null && party.isLeader(player.getUniqueId())) {
                    manager.disbandParty(party);
                } else {
                    player.sendMessage(Component.text("リーダーのみが解散できます。", NamedTextColor.RED));
                }
                break;
            case "info":
                sendInfo(player);
                break;
            case "chat":
                if (args.length < 2) {
                    player.sendMessage(Component.text("使用法: /party chat <message>", NamedTextColor.RED));
                    return true;
                }
                handleChat(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleChat(Player player, String[] args) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("パーティーに所属していません。", NamedTextColor.RED));
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        manager.broadcast(party, Component.text("[Party] ", NamedTextColor.BLUE)
                .append(Component.text(player.getName() + ": ", NamedTextColor.AQUA))
                .append(Component.text(message, NamedTextColor.WHITE)));
    }

    private void sendInfo(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("パーティーに所属していません。", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("--- Party Info ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Leader: ", NamedTextColor.GRAY).append(Component.text(Bukkit.getOfflinePlayer(party.getLeader()).getName() != null ? Bukkit.getOfflinePlayer(party.getLeader()).getName() : "Unknown", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Members:", NamedTextColor.GRAY));
        for (UUID uuid : party.getMembers()) {
            player.sendMessage(Component.text("- " + (Bukkit.getOfflinePlayer(uuid).getName() != null ? Bukkit.getOfflinePlayer(uuid).getName() : "Unknown"), NamedTextColor.WHITE));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Party Commands ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/party create", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/party invite <player>", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/party accept <player>", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/party leave", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/party info", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/party chat <message>", NamedTextColor.WHITE));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "leave", "disband", "info", "chat").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("accept")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
