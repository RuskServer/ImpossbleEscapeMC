package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.sk89q.worldedit.math.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * /hideout: 隠れ家の画面。visit で自分の隠れ家へ。
 * 建築を作る (OP):
 * <ul>
 *   <li>/hideout base save: WorldEdit で選んだ範囲をベースとして保存 (原点は範囲の最小の角、立つ位置は今いる所)</li>
 *   <li>/hideout edit &lt;設備&gt; &lt;レベル&gt; [reset]: エディターのその区画へ。初めて (か reset) なら
 *       「ベース + ほかの設備は作ってある最大レベル + その設備の 1 つ前まで」を建てて、その状態を覚える</li>
 *   <li>/hideout save &lt;設備&gt; &lt;レベル&gt;: 区画の今の状態を、覚えた状態との差分 (ブロックとエンティティ) として保存</li>
 *   <li>建物の Lv.0 (edit base 0 / save base 0) はベースそのもの: ベースだけの区画で直し、保存するとベースを置き換える</li>
 * </ul>
 */
public class HideoutCommand implements CommandExecutor, TabCompleter {
    private final HideoutModule module;

    public HideoutCommand(HideoutModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        if (args.length == 0) {
            new HideoutGUI(module, player).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "visit" -> handleVisit(player);
            case "base" -> handleBase(player, args);
            case "edit" -> handleEdit(player, args);
            case "save" -> handleSave(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleVisit(Player player) {
        if (module.getLayout().base() == null) {
            player.sendMessage(Component.text("隠れ家はまだ準備中です。", NamedTextColor.RED));
            return;
        }
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        if (data.getHideoutIndex() < 0) {
            data.setHideoutIndex(Bukkit.getOfflinePlayers().length);
        }
        try {
            module.ensureBuilt(data);
        } catch (IOException e) {
            fail(player, "隠れ家を建てられませんでした", e);
            return;
        }
        module.getDataModule().saveAsync(player.getUniqueId());
        Location spawn = module.spawn(data.getHideoutIndex());
        if (spawn != null) {
            player.teleport(spawn);
            player.sendMessage(Component.text("自分の隠れ家に移動しました。", NamedTextColor.GREEN));
        }
    }

    private void handleBase(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("save")) {
            player.sendMessage(Component.text("使用法: /hideout base save (WorldEdit で範囲を選んでから。立っている所が隠れ家に入った時の位置)", NamedTextColor.RED));
            return;
        }
        try {
            HideoutLayout.Base base = module.getBuilder().saveBase(player);
            player.sendMessage(Component.text("ベースを保存しました (" + base.sizeX + "x" + base.sizeY + "x" + base.sizeZ
                    + "、エンティティ " + base.entities.size() + ")。編集中の区画は reset で作り直してください。", NamedTextColor.GREEN));
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            player.sendMessage(Component.text("WorldEdit で範囲を選んでください (//wand)。", NamedTextColor.RED));
        } catch (Exception e) {
            fail(player, "ベースを保存できませんでした", e);
        }
    }

    private void handleEdit(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }
        Target target = target(player, args, "/hideout edit <設備> <レベル> [reset]");
        if (target == null) return;
        HideoutLayout layout = module.getLayout();
        HideoutLayout.Base base = layout.base();
        World world = module.getWorldManager().getEditorWorld();
        if (base == null || world == null) {
            player.sendMessage(Component.text("先に /hideout base save でベースを保存してください。", NamedTextColor.RED));
            return;
        }
        boolean reset = args.length > 3 && args[3].equalsIgnoreCase("reset");
        HideoutLayout.Reference reference = layout.reference(target.facility, target.level);
        if (reference == null || reset) {
            try {
                module.getBuilder().prepareEditor(world, target.facility, target.level);
            } catch (IOException e) {
                fail(player, "編集の区画を用意できませんでした", e);
                return;
            }
            if (HideoutBuilder.isBaseItself(target.facility, target.level)) {
                player.sendMessage(Component.text("建物の初期状態 (ベース) の区画を用意しました。直したら /hideout save base 0"
                        + " (ベースの範囲の外は保存されません。広げる時は /hideout base save)", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(target.facility.label + " Lv." + target.level
                        + " の区画を用意しました。建て終えたら /hideout save " + target.facility.id + " " + target.level, NamedTextColor.YELLOW));
            }
        } else if (reference.version != layout.version()) {
            player.sendMessage(Component.text("編集を始めた後にベースかほかの差分が保存されています。今の状態に合わせるなら "
                    + "/hideout edit " + target.facility.id + " " + target.level + " reset (この区画の編集は消えます)", NamedTextColor.GOLD));
        }
        BlockVector3 origin = HideoutBuilder.editorOrigin(target.facility, target.level);
        player.teleport(new Location(world, origin.getX() + base.spawnX, origin.getY() + base.spawnY, origin.getZ() + base.spawnZ,
                base.spawnYaw, base.spawnPitch));
    }

    private void handleSave(Player player, String[] args) {
        if (!player.isOp()) return;
        Target target = target(player, args, "/hideout save <設備> <レベル>");
        if (target == null) return;
        World world = module.getWorldManager().getEditorWorld();
        if (HideoutBuilder.isBaseItself(target.facility, target.level)) {
            // 用意していない区画 (空) をベースにしてしまわないよう、edit base 0 で用意した後だけ
            if (module.getLayout().reference(target.facility, target.level) == null) {
                player.sendMessage(Component.text("先に /hideout edit base 0 で区画を用意してください。", NamedTextColor.RED));
                return;
            }
            try {
                HideoutLayout.Base base = module.getBuilder().saveBaseFromEditor(world);
                player.sendMessage(Component.text("ベース (建物の初期状態) を更新しました (エンティティ " + base.entities.size()
                        + ")。隠れ家は次に入った時に建て直されます。", NamedTextColor.GREEN));
            } catch (IOException e) {
                fail(player, "ベースを保存できませんでした", e);
            }
            return;
        }
        try {
            HideoutBuilder.SaveResult result = module.getBuilder().saveLayer(world, target.facility, target.level);
            player.sendMessage(Component.text(target.facility.label + " Lv." + target.level + " の差分を保存しました (ブロック "
                    + result.blocks() + "、エンティティ +" + result.addedEntities() + " / -" + result.removedEntities() + ")",
                    NamedTextColor.GREEN));
            if (target.level > 1 && !module.getLayout().hasLayer(target.facility, target.level - 1)) {
                player.sendMessage(Component.text("Lv." + (target.level - 1) + " がまだ無いので、このレベルは Lv."
                        + (target.level - 1) + " を保存するまで使われません。", NamedTextColor.GOLD));
            }
        } catch (IOException e) {
            fail(player, "差分を保存できませんでした", e);
        }
    }

    private record Target(HideoutFacility facility, int level) {
    }

    private Target target(Player player, String[] args, String usage) {
        HideoutFacility facility = args.length > 1 ? HideoutFacility.byId(args[1]) : null;
        int level = -1;
        if (args.length > 2) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // 下で使い方を出す
            }
        }
        if (facility == null || level < facility.minEditLevel() || level > facility.editLimit()) {
            player.sendMessage(Component.text("使用法: " + usage + " (設備: " + Arrays.stream(HideoutFacility.values())
                    .map(f -> f.id + " " + f.minEditLevel() + "-" + f.editLimit()).collect(Collectors.joining(", "))
                    + "。base 0 は建物の初期状態)", NamedTextColor.RED));
            return null;
        }
        return new Target(facility, level);
    }

    private void fail(Player player, String what, Exception e) {
        module.getPlugin().getLogger().log(Level.WARNING, what, e);
        player.sendMessage(Component.text(what + ": " + e.getMessage(), NamedTextColor.RED));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Hideout Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/hideout visit - 自分の隠れ家へ移動", NamedTextColor.YELLOW));
        if (player.isOp()) {
            player.sendMessage(Component.text("/hideout base save - WorldEdit で選んだ範囲をベースとして保存", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/hideout edit <設備> <レベル> [reset] - そのレベルの建築を作る区画へ", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/hideout save <設備> <レベル> - 区画の変更を差分として保存", NamedTextColor.YELLOW));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("visit");
            if (sender.isOp()) options.addAll(List.of("base", "edit", "save"));
        } else if (sender.isOp() && args.length == 2 && args[0].equalsIgnoreCase("base")) {
            options.add("save");
        } else if (sender.isOp() && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("save"))) {
            if (args.length == 2) {
                Arrays.stream(HideoutFacility.values()).map(f -> f.id).forEach(options::add);
            } else if (args.length == 3) {
                HideoutFacility facility = HideoutFacility.byId(args[1]);
                if (facility != null) IntStream.rangeClosed(facility.minEditLevel(), facility.editLimit()).mapToObj(String::valueOf).forEach(options::add);
            } else if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
                options.add("reset");
            }
        }
        String typed = args[args.length - 1].toLowerCase();
        return options.stream().filter(s -> s.startsWith(typed)).collect(Collectors.toList());
    }
}
