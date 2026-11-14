package com.lunar_prototype.impossbleEscapeMC.modules.party;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Command(name = "party", description = "パーティーシステムコマンド")
public class PartyCommand implements BasicCommand {
    private final PartyModule manager;

    public PartyCommand(PartyModule manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) return;

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                manager.createParty(player);
                break;
            case "invite":
                if (args.length < 2) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_001", MessageText.of("iem.party.partycommand.execute.notice_001", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_001.text_01").color(NamedTextColor.RED))));
                    return;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_002", MessageText.of("iem.party.partycommand.execute.notice_002", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_002.text_01").color(NamedTextColor.RED))));
                    return;
                }
                manager.invitePlayer(player, target);
                break;
            case "accept":
                if (args.length < 2) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_003", MessageText.of("iem.party.partycommand.execute.notice_003", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_003.text_01").color(NamedTextColor.RED))));
                    return;
                }
                manager.acceptInvite(player, args[1]);
                break;
            case "decline":
                manager.declineInvite(player);
                break;
            case "kick":
            case "leader": {
                if (args.length < 2) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_004", MessageText.of("iem.party.partycommand.execute.notice_004", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_004.text_01", args[0].toLowerCase()).color(NamedTextColor.RED))));
                    return;
                }
                Player member = Bukkit.getPlayer(args[1]);
                if (member == null) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_005", MessageText.of("iem.party.partycommand.execute.notice_005", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_005.text_01").color(NamedTextColor.RED))));
                    return;
                }
                if (args[0].equalsIgnoreCase("kick")) {
                    manager.kickMember(player, member.getUniqueId());
                } else {
                    manager.transferLeader(player, member.getUniqueId());
                }
                break;
            }
            case "leave":
                manager.leaveParty(player);
                break;
            case "disband":
                Party party = manager.getParty(player.getUniqueId());
                if (party != null && party.isLeader(player.getUniqueId())) {
                    manager.disbandParty(party);
                } else {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_006", MessageText.of("iem.party.partycommand.execute.notice_006", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_006.text_01").color(NamedTextColor.RED))));
                }
                break;
            case "info":
                sendInfo(player);
                break;
            case "chat":
                if (args.length < 2) {
                    Messages.chat(player, "iem.party.partycommand.execute.notice_007", MessageText.of("iem.party.partycommand.execute.notice_007", MessageText.payload(MessageText.of("iem.party.partycommand.execute.notice_007.text_01").color(NamedTextColor.RED))));
                    return;
                }
                handleChat(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }

        return;
    }

    private void handleChat(Player player, String[] args) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.chat(player, "iem.party.partycommand.handlechat.notice_001", MessageText.of("iem.party.partycommand.handlechat.notice_001", MessageText.payload(MessageText.of("iem.party.partycommand.handlechat.notice_001.text_01").color(NamedTextColor.RED))));
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
            Messages.chat(player, "iem.party.partycommand.sendinfo.notice_001", MessageText.of("iem.party.partycommand.sendinfo.notice_001", MessageText.payload(MessageText.of("iem.party.partycommand.sendinfo.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        Messages.chat(player, "iem.party.partycommand.sendinfo.notice_002", MessageText.of("iem.party.partycommand.sendinfo.notice_002", MessageText.payload(MessageText.of("iem.party.partycommand.sendinfo.notice_002.text_01").color(NamedTextColor.YELLOW))));
        Messages.chat(player, "iem.party.partycommand.sendinfo.notice_003", MessageText.of("iem.party.partycommand.sendinfo.notice_003", MessageText.payload(MessageText.of("iem.party.partycommand.sendinfo.notice_003.text_01").color(NamedTextColor.GRAY).append(MessageText.of("iem.party.partycommand.sendinfo.notice_003.text_02", Bukkit.getOfflinePlayer(party.getLeader()).getName() != null ? Bukkit.getOfflinePlayer(party.getLeader()).getName() : "Unknown").color(NamedTextColor.WHITE)))));
        Messages.chat(player, "iem.party.partycommand.sendinfo.notice_004", MessageText.of("iem.party.partycommand.sendinfo.notice_004", MessageText.payload(MessageText.of("iem.party.partycommand.sendinfo.notice_004.text_01").color(NamedTextColor.GRAY))));
        for (UUID uuid : party.getMembers()) {
            Messages.chat(player, "iem.party.partycommand.sendinfo.notice_005", MessageText.of("iem.party.partycommand.sendinfo.notice_005", MessageText.payload(MessageText.of("iem.party.partycommand.sendinfo.notice_005.text_01", (Bukkit.getOfflinePlayer(uuid).getName() != null ? Bukkit.getOfflinePlayer(uuid).getName() : "Unknown")).color(NamedTextColor.WHITE))));
        }
    }

    private void sendHelp(Player player) {
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_001", MessageText.of("iem.party.partycommand.sendhelp.notice_001", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_001.text_01").color(NamedTextColor.YELLOW))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_002", MessageText.of("iem.party.partycommand.sendhelp.notice_002", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_002.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_003", MessageText.of("iem.party.partycommand.sendhelp.notice_003", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_003.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_004", MessageText.of("iem.party.partycommand.sendhelp.notice_004", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_004.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_005", MessageText.of("iem.party.partycommand.sendhelp.notice_005", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_005.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_006", MessageText.of("iem.party.partycommand.sendhelp.notice_006", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_006.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_007", MessageText.of("iem.party.partycommand.sendhelp.notice_007", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_007.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_008", MessageText.of("iem.party.partycommand.sendhelp.notice_008", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_008.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_009", MessageText.of("iem.party.partycommand.sendhelp.notice_009", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_009.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.party.partycommand.sendhelp.notice_010", MessageText.of("iem.party.partycommand.sendhelp.notice_010", MessageText.payload(MessageText.of("iem.party.partycommand.sendhelp.notice_010.text_01").color(NamedTextColor.WHITE))));
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "decline", "kick", "leader", "leave", "disband", "info", "chat").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("leader")) {
                Party party = sender instanceof Player p ? manager.getParty(p.getUniqueId()) : null;
                if (party == null) return new ArrayList<>();
                return party.getMembers().stream()
                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                        .filter(name -> name != null && !name.equals(sender.getName()))
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("accept")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.isGunner(p))
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
