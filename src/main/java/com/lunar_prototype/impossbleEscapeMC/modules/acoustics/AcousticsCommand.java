package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 音響の地図 ({@link AcousticMap}) の確認と手直し。
 * <ul>
 *   <li>/acoustics here: 今いる所の場所 (上書きか自動の判定か) と、足元の材質</li>
 *   <li>/acoustics clear [半径]: 覚えた自動の判定を捨てる (マップを改変した後に。半径を省くとワールド全体)</li>
 *   <li>/acoustics override &lt;outdoor|indoor|hall|underground&gt; &lt;半径&gt;: 今いる所を中心に、立方体の範囲の場所を決める</li>
 *   <li>/acoustics override remove: 今いる所を含む上書きを消す</li>
 *   <li>/acoustics overrides: このワールドの上書きの一覧</li>
 * </ul>
 */
@Command(name = "acoustics", description = "音響の地図 (足音などの響きを決める場所の種類) を確認・手直しします。", permission = "op")
public class AcousticsCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.admin.acousticscommand.execute.notice_001", MessageText.of("iem.admin.acousticscommand.execute.notice_001", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_001.text_01"))));
            return;
        }
        AcousticMap map = AcousticMap.get();
        if (map == null) {
            Messages.chat(player, "iem.admin.acousticscommand.execute.notice_002", MessageText.of("iem.admin.acousticscommand.execute.notice_002", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "here";
        switch (sub) {
            case "here" -> {
                var location = player.getLocation();
                var override = map.overrideAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
                AcousticZone detected = map.detectNow(location);
                AcousticZone current = map.zone(location);
                Messages.chat(player, "iem.admin.acousticscommand.execute.notice_003", MessageText.of("iem.admin.acousticscommand.execute.notice_003", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_003.text_01", MessageText.of("iem.admin.acoustics.zone." + current.id), current.id, (override != null ? MessageText.of("iem.admin.acousticscommand.overridden", MessageText.of("iem.admin.acoustics.zone." + detected.id)) : MessageText.of("iem.admin.acousticscommand.detected")), FootstepSounds.materialAt(player)).color(NamedTextColor.AQUA))));
            }
            case "clear" -> {
                int radius = args.length > 1 ? parseInt(args[1], -1) : -1;
                int count = map.forget(player.getWorld(), player.getLocation(), radius);
                Messages.chat(player, "iem.admin.acousticscommand.execute.notice_004", MessageText.of("iem.admin.acousticscommand.execute.notice_004", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_004.text_01", (radius < 0 ? MessageText.of("iem.admin.acousticscommand.world") : MessageText.of("iem.admin.acousticscommand.radius", radius)), count).color(NamedTextColor.GREEN))));
            }
            case "override" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("remove")) {
                    int count = map.removeOverridesAt(player.getLocation());
                    Messages.chat(player, "iem.admin.acousticscommand.execute.notice_005", MessageText.of("iem.admin.acousticscommand.execute.notice_005", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_005.text_01", count).color(NamedTextColor.GREEN))));
                    return;
                }
                AcousticZone zone = args.length > 1 ? AcousticZone.byId(args[1]) : null;
                int radius = args.length > 2 ? parseInt(args[2], -1) : -1;
                if (zone == null || radius < 0) {
                    Messages.chat(player, "iem.admin.acousticscommand.execute.notice_006", MessageText.of("iem.admin.acousticscommand.execute.notice_006", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_006.text_01").color(NamedTextColor.RED))));
                    return;
                }
                var override = map.addOverride(player.getLocation(), radius, zone);
                Messages.chat(player, "iem.admin.acousticscommand.execute.notice_007", MessageText.of("iem.admin.acousticscommand.execute.notice_007", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_007.text_01", MessageText.of("iem.admin.acoustics.zone." + zone.id), override.minX(), override.minY(), override.minZ(), override.maxX(), override.maxY(), override.maxZ()).color(NamedTextColor.GREEN))));
            }
            case "overrides" -> {
                var list = map.overrides(player.getWorld().getName());
                Messages.chat(player, "iem.admin.acousticscommand.execute.notice_008", MessageText.of("iem.admin.acousticscommand.execute.notice_008", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_008.text_01", list.size()).color(NamedTextColor.AQUA))));
                for (var o : list) {
                    AcousticZone zone = AcousticZone.byId(o.zone());
                    Messages.chat(player, "iem.admin.acousticscommand.execute.notice_009", MessageText.of("iem.admin.acousticscommand.execute.notice_009", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_009.text_01", (zone != null ? MessageText.of("iem.admin.acoustics.zone." + zone.id) : o.zone()), o.minX(), o.minY(), o.minZ(), o.maxX(), o.maxY(), o.maxZ()).color(NamedTextColor.GRAY))));
                }
            }
            default -> Messages.chat(player, "iem.admin.acousticscommand.execute.notice_010", MessageText.of("iem.admin.acousticscommand.execute.notice_010", MessageText.payload(MessageText.of("iem.admin.acousticscommand.execute.notice_010.text_01").color(NamedTextColor.RED))));
        }
        return;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("here", "clear", "override", "overrides"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("override")) {
            options.add("remove");
            Arrays.stream(AcousticZone.values()).map(z -> z.id).forEach(options::add);
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return options.stream().filter(o -> o.startsWith(prefix)).toList();
    }
}
