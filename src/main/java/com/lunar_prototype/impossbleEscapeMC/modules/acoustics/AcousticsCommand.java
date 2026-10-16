package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

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
            sender.sendMessage("プレイヤーだけが使えます");
            return;
        }
        AcousticMap map = AcousticMap.get();
        if (map == null) {
            player.sendMessage(Component.text("音響の地図が読み込まれていません", NamedTextColor.RED));
            return;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "here";
        switch (sub) {
            case "here" -> {
                var location = player.getLocation();
                var override = map.overrideAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
                AcousticZone detected = map.detectNow(location);
                AcousticZone current = map.zone(location);
                player.sendMessage(Component.text("場所: " + current.label + " (" + current.id + ")"
                        + (override != null ? " ← 上書き。自動の判定は " + detected.label : " ← 自動の判定")
                        + " / 足元の材質: " + FootstepSounds.materialAt(player), NamedTextColor.AQUA));
            }
            case "clear" -> {
                int radius = args.length > 1 ? parseInt(args[1], -1) : -1;
                int count = map.forget(player.getWorld(), player.getLocation(), radius);
                player.sendMessage(Component.text((radius < 0 ? "このワールド" : "半径 " + radius + " ブロック") + "の判定を " + count
                        + " マス捨てました。次に音が鳴った時に判定し直します", NamedTextColor.GREEN));
            }
            case "override" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("remove")) {
                    int count = map.removeOverridesAt(player.getLocation());
                    player.sendMessage(Component.text("上書きを " + count + " 個消しました", NamedTextColor.GREEN));
                    return;
                }
                AcousticZone zone = args.length > 1 ? AcousticZone.byId(args[1]) : null;
                int radius = args.length > 2 ? parseInt(args[2], -1) : -1;
                if (zone == null || radius < 0) {
                    player.sendMessage(Component.text("/acoustics override <outdoor|indoor|hall|underground> <半径> | remove", NamedTextColor.RED));
                    return;
                }
                var override = map.addOverride(player.getLocation(), radius, zone);
                player.sendMessage(Component.text("上書きしました: " + zone.label + " (" + override.minX() + ", " + override.minY() + ", " + override.minZ()
                        + ") 〜 (" + override.maxX() + ", " + override.maxY() + ", " + override.maxZ() + ")", NamedTextColor.GREEN));
            }
            case "overrides" -> {
                var list = map.overrides(player.getWorld().getName());
                player.sendMessage(Component.text("上書き " + list.size() + " 個", NamedTextColor.AQUA));
                for (var o : list) {
                    AcousticZone zone = AcousticZone.byId(o.zone());
                    player.sendMessage(Component.text(" " + (zone != null ? zone.label : o.zone()) + ": (" + o.minX() + ", " + o.minY() + ", " + o.minZ()
                            + ") 〜 (" + o.maxX() + ", " + o.maxY() + ", " + o.maxZ() + ")", NamedTextColor.GRAY));
                }
            }
            default -> player.sendMessage(Component.text("/acoustics <here|clear [半径]|override <場所> <半径>|override remove|overrides>", NamedTextColor.RED));
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
