package com.lunar_prototype.impossbleEscapeMC.party;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class PartyManager {
    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>(); // Invited -> Inviter

    public PartyManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID playerUUID) {
        return playerPartyMap.get(playerUUID);
    }

    public void createParty(Player leader) {
        if (playerPartyMap.containsKey(leader.getUniqueId())) {
            leader.sendMessage(Component.text("あなたは既にパーティーに所属しています。", NamedTextColor.RED));
            return;
        }
        Party party = new Party(leader.getUniqueId());
        playerPartyMap.put(leader.getUniqueId(), party);
        leader.sendMessage(Component.text("パーティーを作成しました。", NamedTextColor.GREEN));
    }

    public void invitePlayer(Player inviter, Player invited) {
        Party party = getParty(inviter.getUniqueId());
        if (party == null) {
            createParty(inviter);
            party = getParty(inviter.getUniqueId());
        }

        if (!party.isLeader(inviter.getUniqueId())) {
            inviter.sendMessage(Component.text("リーダーのみが招待を送信できます。", NamedTextColor.RED));
            return;
        }

        if (playerPartyMap.containsKey(invited.getUniqueId())) {
            inviter.sendMessage(Component.text(invited.getName() + " は既にパーティーに所属しています。", NamedTextColor.RED));
            return;
        }

        pendingInvites.put(invited.getUniqueId(), inviter.getUniqueId());
        inviter.sendMessage(Component.text(invited.getName() + " に招待を送信しました。", NamedTextColor.GREEN));
        invited.sendMessage(Component.text(inviter.getName() + " からパーティーへの招待が届きました。", NamedTextColor.YELLOW)
                .append(Component.text(" [参加する]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("クリックでパーティーに参加", NamedTextColor.GREEN))))
                .append(Component.text(" [拒否する]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party decline"))
                        .hoverEvent(HoverEvent.showText(Component.text("クリックで招待を拒否", NamedTextColor.RED)))));

        // 60秒後に招待を無効化
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingInvites.remove(invited.getUniqueId(), inviter.getUniqueId()), 1200L);
    }

    public void acceptInvite(Player player, String inviterName) {
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null) {
            player.sendMessage(Component.text("招待したプレイヤーがオフラインです。", NamedTextColor.RED));
            return;
        }

        UUID inviterUUID = pendingInvites.get(player.getUniqueId());
        if (inviterUUID == null || !inviterUUID.equals(inviter.getUniqueId())) {
            player.sendMessage(Component.text("有効な招待が見つかりません。", NamedTextColor.RED));
            return;
        }

        Party party = getParty(inviter.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("パーティーが既に解散されています。", NamedTextColor.RED));
            pendingInvites.remove(player.getUniqueId());
            return;
        }

        pendingInvites.remove(player.getUniqueId());
        joinParty(player, party);
    }

    /** 届いている招待の送り主。招待が無ければnull */
    public UUID getPendingInviter(UUID invited) {
        return pendingInvites.get(invited);
    }

    public void declineInvite(Player player) {
        UUID inviterUUID = pendingInvites.remove(player.getUniqueId());
        if (inviterUUID == null) {
            player.sendMessage(Component.text("有効な招待が見つかりません。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("招待を拒否しました。", NamedTextColor.GRAY));
        Player inviter = Bukkit.getPlayer(inviterUUID);
        if (inviter != null) {
            inviter.sendMessage(Component.text(player.getName() + " が招待を拒否しました。", NamedTextColor.YELLOW));
        }
    }

    /** リーダーがメンバーをパーティーから外す */
    public void kickMember(Player leader, UUID targetUUID) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(Component.text("リーダーのみがメンバーを外せます。", NamedTextColor.RED));
            return;
        }
        if (targetUUID.equals(leader.getUniqueId()) || !party.isMember(targetUUID)) {
            leader.sendMessage(Component.text("そのプレイヤーはパーティーのメンバーではありません。", NamedTextColor.RED));
            return;
        }

        String name = nameOf(targetUUID);
        party.removeMember(targetUUID);
        playerPartyMap.remove(targetUUID);
        broadcast(party, Component.text(name + " をパーティーから外しました。", NamedTextColor.YELLOW));
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Component.text("パーティーから外されました。", NamedTextColor.RED));
        }
    }

    /** リーダーを別のメンバーに渡す */
    public void transferLeader(Player leader, UUID targetUUID) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(Component.text("リーダーのみがリーダーを渡せます。", NamedTextColor.RED));
            return;
        }
        if (targetUUID.equals(leader.getUniqueId()) || !party.isMember(targetUUID)) {
            leader.sendMessage(Component.text("そのプレイヤーはパーティーのメンバーではありません。", NamedTextColor.RED));
            return;
        }

        party.setLeader(targetUUID);
        broadcast(party, Component.text(nameOf(targetUUID) + " が新しいリーダーになりました。", NamedTextColor.GREEN));
    }

    private static String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    private void joinParty(Player player, Party party) {
        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), party);

        broadcast(party, Component.text(player.getName() + " がパーティーに参加しました。", NamedTextColor.GREEN));
    }

    public void leaveParty(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("パーティーに所属していません。", NamedTextColor.RED));
            return;
        }

        if (party.isLeader(player.getUniqueId())) {
            disbandParty(party);
        } else {
            party.removeMember(player.getUniqueId());
            playerPartyMap.remove(player.getUniqueId());
            player.sendMessage(Component.text("パーティーを脱退しました。", NamedTextColor.YELLOW));
            broadcast(party, Component.text(player.getName() + " がパーティーを脱退しました。", NamedTextColor.YELLOW));
        }
    }

    public void disbandParty(Party party) {
        broadcast(party, Component.text("パーティーが解散されました。", NamedTextColor.RED));
        for (UUID uuid : party.getMembers()) {
            playerPartyMap.remove(uuid);
        }
    }

    public void broadcast(Party party, Component message) {
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }
}
