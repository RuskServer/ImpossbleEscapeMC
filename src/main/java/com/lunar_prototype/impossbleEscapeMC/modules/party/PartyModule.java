package com.lunar_prototype.impossbleEscapeMC.modules.party;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * パーティー (メモリだけに持つ。再起動で解散)。/party コマンドと、抜けた人をパーティーから外す処理もここ。
 * リーダーがログアウトしても解散しない (メンバーはリーダーの隠れ家に集まり続けられる。明示的な
 * /party leave と /party disband は今まで通り解散する)
 */
public class PartyModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>(); // Invited -> Inviter

    public PartyModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    /** 動いているパーティーのモジュール (起動前は null) */
    public static PartyModule get() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(PartyModule.class);
    }

    @Override
    public void onEnable(ServiceContainer container) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        container.get(CommandRegistry.class).register(new PartyCommand(this));
    }

    @Override
    public void onDisable() {
        playerPartyMap.clear();
        pendingInvites.clear();
    }

    // レイドの抜けた時の処理 (PlayerListener・NORMAL) の後に外す (前はそこで続けて呼んでいた)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Party party = getParty(event.getPlayer().getUniqueId());
        // リーダーのログアウトでは解散しない (メンバーはリーダーの隠れ家に集まり続けられる。戻ればそのままリーダー)。
        // 解散するのは明示的な抜け方 (/party leave と /party disband) の時だけ
        if (party != null && party.isLeader(event.getPlayer().getUniqueId())) {
            broadcast(party, MessageText.of("iem.party.partymodule.member_offline", event.getPlayer().getName()).color(NamedTextColor.YELLOW));
            return;
        }
        leaveParty(event.getPlayer());
    }

    public Party getParty(UUID playerUUID) {
        return playerPartyMap.get(playerUUID);
    }

    public void createParty(Player leader) {
        if (playerPartyMap.containsKey(leader.getUniqueId())) {
            Messages.chat(leader, "iem.party.partymodule.createparty.notice_001", MessageText.of("iem.party.partymodule.createparty.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.createparty.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        Party party = new Party(leader.getUniqueId());
        playerPartyMap.put(leader.getUniqueId(), party);
        Messages.chat(leader, "iem.party.partymodule.createparty.notice_002", MessageText.of("iem.party.partymodule.createparty.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.createparty.notice_002.text_01").color(NamedTextColor.GREEN))));
    }

    public void invitePlayer(Player inviter, Player invited) {
        Party party = getParty(inviter.getUniqueId());
        if (party == null) {
            createParty(inviter);
            party = getParty(inviter.getUniqueId());
        }

        if (!party.isLeader(inviter.getUniqueId())) {
            Messages.chat(inviter, "iem.party.partymodule.inviteplayer.notice_001", MessageText.of("iem.party.partymodule.inviteplayer.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.inviteplayer.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        if (playerPartyMap.containsKey(invited.getUniqueId())) {
            Messages.chat(inviter, "iem.party.partymodule.inviteplayer.notice_002", MessageText.of("iem.party.partymodule.inviteplayer.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.inviteplayer.notice_002.text_01", invited.getName()).color(NamedTextColor.RED))));
            return;
        }

        pendingInvites.put(invited.getUniqueId(), inviter.getUniqueId());
        Messages.chat(inviter, "iem.party.partymodule.inviteplayer.notice_003", MessageText.of("iem.party.partymodule.inviteplayer.notice_003", MessageText.payload(MessageText.of("iem.party.partymodule.inviteplayer.notice_003.text_01", invited.getName()).color(NamedTextColor.GREEN))));
        Messages.chat(invited, "iem.party.partymodule.inviteplayer.notice_004", MessageText.of("iem.party.partymodule.inviteplayer.notice_004", MessageText.payload(MessageText.of("iem.party.partymodule.inviteplayer.notice_004.text_01", inviter.getName()).color(NamedTextColor.YELLOW)
                .append(MessageText.of("iem.party.partymodule.inviteplayer.notice_004.text_02").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName()))
                        .hoverEvent(HoverEvent.showText(MessageText.of("iem.party.partymodule.inviteplayer.notice_004.text_03").color(NamedTextColor.GREEN))))
                .append(MessageText.of("iem.party.partymodule.inviteplayer.notice_004.text_04").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party decline"))
                        .hoverEvent(HoverEvent.showText(MessageText.of("iem.party.partymodule.inviteplayer.notice_004.text_05").color(NamedTextColor.RED)))))));

        // 60秒後に招待を無効化
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingInvites.remove(invited.getUniqueId(), inviter.getUniqueId()), 1200L);
    }

    public void acceptInvite(Player player, String inviterName) {
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null) {
            Messages.chat(player, "iem.party.partymodule.acceptinvite.notice_001", MessageText.of("iem.party.partymodule.acceptinvite.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.acceptinvite.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        UUID inviterUUID = pendingInvites.get(player.getUniqueId());
        if (inviterUUID == null || !inviterUUID.equals(inviter.getUniqueId())) {
            Messages.chat(player, "iem.party.partymodule.acceptinvite.notice_002", MessageText.of("iem.party.partymodule.acceptinvite.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.acceptinvite.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        // 招待を受けてから受け入れるまでに、別のパーティーに入った・作った (2 つのパーティーに入らない)
        if (getParty(player.getUniqueId()) != null) {
            Messages.chat(player, "iem.party.partymodule.acceptinvite.notice_003", MessageText.of("iem.party.partymodule.acceptinvite.notice_003", MessageText.payload(MessageText.of("iem.party.partymodule.acceptinvite.notice_003.text_01").color(NamedTextColor.RED))));
            return;
        }

        Party party = getParty(inviter.getUniqueId());
        if (party == null) {
            Messages.chat(player, "iem.party.partymodule.acceptinvite.notice_004", MessageText.of("iem.party.partymodule.acceptinvite.notice_004", MessageText.payload(MessageText.of("iem.party.partymodule.acceptinvite.notice_004.text_01").color(NamedTextColor.RED))));
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
            Messages.chat(player, "iem.party.partymodule.declineinvite.notice_001", MessageText.of("iem.party.partymodule.declineinvite.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.declineinvite.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        Messages.chat(player, "iem.party.partymodule.declineinvite.notice_002", MessageText.of("iem.party.partymodule.declineinvite.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.declineinvite.notice_002.text_01").color(NamedTextColor.GRAY))));
        Player inviter = Bukkit.getPlayer(inviterUUID);
        if (inviter != null) {
            Messages.chat(inviter, "iem.party.partymodule.declineinvite.notice_003", MessageText.of("iem.party.partymodule.declineinvite.notice_003", MessageText.payload(MessageText.of("iem.party.partymodule.declineinvite.notice_003.text_01", player.getName()).color(NamedTextColor.YELLOW))));
        }
    }

    /** リーダーがメンバーをパーティーから外す */
    public void kickMember(Player leader, UUID targetUUID) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Messages.chat(leader, "iem.party.partymodule.kickmember.notice_001", MessageText.of("iem.party.partymodule.kickmember.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.kickmember.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        if (targetUUID.equals(leader.getUniqueId()) || !party.isMember(targetUUID)) {
            Messages.chat(leader, "iem.party.partymodule.kickmember.notice_002", MessageText.of("iem.party.partymodule.kickmember.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.kickmember.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        String name = nameOf(targetUUID);
        party.removeMember(targetUUID);
        playerPartyMap.remove(targetUUID);
        broadcast(party, MessageText.of("iem.party.partymodule.member_removed", name).color(NamedTextColor.YELLOW));
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            Messages.chat(target, "iem.party.partymodule.kickmember.notice_003", MessageText.of("iem.party.partymodule.kickmember.notice_003", MessageText.payload(MessageText.of("iem.party.partymodule.kickmember.notice_003.text_01").color(NamedTextColor.RED))));
        }
    }

    /** リーダーを別のメンバーに渡す */
    public void transferLeader(Player leader, UUID targetUUID) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Messages.chat(leader, "iem.party.partymodule.transferleader.notice_001", MessageText.of("iem.party.partymodule.transferleader.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.transferleader.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        if (targetUUID.equals(leader.getUniqueId()) || !party.isMember(targetUUID)) {
            Messages.chat(leader, "iem.party.partymodule.transferleader.notice_002", MessageText.of("iem.party.partymodule.transferleader.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.transferleader.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        party.setLeader(targetUUID);
        broadcast(party, MessageText.of("iem.party.partymodule.leader_changed", nameOf(targetUUID)).color(NamedTextColor.GREEN));
    }

    private static String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    private void joinParty(Player player, Party party) {
        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), party);

        broadcast(party, MessageText.of("iem.party.partymodule.member_joined", player.getName()).color(NamedTextColor.GREEN));
    }

    /** 抜ける。リーダーが抜けると解散する (リーダーのログアウトは {@link #onQuit} が維持するので、ここに来るのは明示的な抜け方) */
    public void leaveParty(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            Messages.chat(player, "iem.party.partymodule.leaveparty.notice_001", MessageText.of("iem.party.partymodule.leaveparty.notice_001", MessageText.payload(MessageText.of("iem.party.partymodule.leaveparty.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        if (party.isLeader(player.getUniqueId())) {
            disbandParty(party);
        } else {
            party.removeMember(player.getUniqueId());
            playerPartyMap.remove(player.getUniqueId());
            Messages.chat(player, "iem.party.partymodule.leaveparty.notice_002", MessageText.of("iem.party.partymodule.leaveparty.notice_002", MessageText.payload(MessageText.of("iem.party.partymodule.leaveparty.notice_002.text_01").color(NamedTextColor.YELLOW))));
            broadcast(party, MessageText.of("iem.party.partymodule.member_left", player.getName()).color(NamedTextColor.YELLOW));
        }
    }

    public void disbandParty(Party party) {
        broadcast(party, MessageText.of("iem.party.partymodule.disbanded").color(NamedTextColor.RED));
        for (UUID uuid : party.getMembers()) {
            playerPartyMap.remove(uuid);
        }
    }

    public void broadcast(Party party, Component message) {
        java.util.List<Player> recipients=new java.util.ArrayList<>();
        for(UUID uuid:party.getMembers()) { Player p=Bukkit.getPlayer(uuid);if(p!=null)recipients.add(p); }
        Messages.group(recipients,"iem.party.partymodule.broadcast.notice_001",MessageText.of("iem.party.partymodule.broadcast.notice_001",message));
    }
}
