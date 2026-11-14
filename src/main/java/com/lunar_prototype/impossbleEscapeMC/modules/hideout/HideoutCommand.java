package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.sk89q.worldedit.math.BlockVector3;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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
 * /hideout: 隠れ家の画面。visit で隠れ家へ (パーティーのメンバーはリーダーの隠れ家に集まる。設備の利用は各自のレベル)。
 * 建築を作る (OP):
 * <ul>
 *   <li>/hideout base save: WorldEdit で選んだ範囲をベースとして保存 (原点は範囲の最小の角、立つ位置は今いる所)</li>
 *   <li>/hideout edit &lt;設備&gt; &lt;レベル&gt; [reset]: エディターのその区画へ。初めて (か reset) なら
 *       「ベース + ほかの設備は作ってある最大レベル + その設備の 1 つ前まで」を建てて、その状態を覚える</li>
 *   <li>/hideout save &lt;設備&gt; &lt;レベル&gt;: 区画の今の状態を、覚えた状態との差分 (ブロックとエンティティ) として保存</li>
 *   <li>/hideout reset: 自分の隠れ家の設備と建物のレベルをすべて 0 に戻し、ベースだけの状態に建て直す</li>
 *   <li>/hideout camera &lt;設備&gt;: 今の目の位置と向きを、隠れ家の画面でその設備を映すカメラとして記録する
 *       (エディターの区画か自分の隠れ家で。記録していない設備は自動で決める)</li>
 *   <li>建物の Lv.0 (edit base 0 / save base 0) はベースそのもの: ベースだけの区画で直し、保存するとベースを置き換える</li>
 * </ul>
 */
@Command(name = "hideout", description = "隠れ家システム管理・利用コマンド")
public class HideoutCommand implements BasicCommand {
    private final HideoutModule module;

    public HideoutCommand(HideoutModule module) {
        this.module = module;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.hideout.hideoutcommand.execute.notice_001", MessageText.of("iem.hideout.hideoutcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.execute.notice_001.text_01"))));
            return;
        }

        if (args.length == 0) {
            HideoutDialog.open(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "visit" -> handleVisit(player);
            case "base" -> handleBase(player, args);
            case "edit" -> handleEdit(player, args);
            case "save" -> handleSave(player, args);
            case "reset" -> handleReset(player);
            case "camera" -> handleCamera(player, args);
            case "inspect" -> handleInspect(player);
            default -> sendHelp(player);
        }
        return;
    }

    private void handleVisit(Player player) {
        module.visit(player);
    }

    private void handleBase(Player player, String[] args) {
        if (!player.isOp()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlebase.notice_001", MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("save")) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlebase.notice_002", MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }
        try {
            HideoutLayout.Base base = module.getBuilder().saveBase(player);
            Messages.chat(player, "iem.hideout.hideoutcommand.handlebase.notice_003", MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_003.text_01", base.sizeX, base.sizeY, base.sizeZ, base.entities.size()).color(NamedTextColor.GREEN))));
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlebase.notice_004", MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlebase.notice_004.text_01").color(NamedTextColor.RED))));
        } catch (Exception e) {
            fail(player, "ベースを保存できませんでした", e);
        }
    }

    private void handleEdit(Player player, String[] args) {
        if (!player.isOp()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handleedit.notice_001", MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        Target target = target(player, args, "/hideout edit <設備> <レベル> [reset]");
        if (target == null) return;
        HideoutLayout layout = module.getLayout();
        HideoutLayout.Base base = layout.base();
        World world = module.getWorldManager().getEditorWorld();
        if (base == null || world == null) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handleedit.notice_002", MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_002.text_01").color(NamedTextColor.RED))));
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
                Messages.chat(player, "iem.hideout.hideoutcommand.handleedit.notice_003", MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_003.text_01").color(NamedTextColor.YELLOW))));
            } else {
                Messages.chat(player, "iem.hideout.hideoutcommand.handleedit.notice_004", MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_004.text_01", target.facility.label, target.level, target.facility.id, target.level).color(NamedTextColor.YELLOW))));
            }
        } else if (reference.version != layout.version()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handleedit.notice_005", MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_005", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleedit.notice_005.text_01", target.facility.id, target.level).color(NamedTextColor.GOLD))));
        }
        BlockVector3 origin = HideoutBuilder.editorOrigin(target.facility, target.level);
        player.teleport(new Location(world, origin.x() + base.spawnX, origin.y() + base.spawnY, origin.z() + base.spawnZ,
                base.spawnYaw, base.spawnPitch));
    }

    private void handleReset(Player player) {
        if (!player.isOp()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlereset.notice_001", MessageText.of("iem.hideout.hideoutcommand.handlereset.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlereset.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        boolean inside = module.getWorldManager().isPlayerWorld(player.getWorld());
        try {
            module.reset(player, data);
        } catch (IOException e) {
            fail(player, "隠れ家を建て直せませんでした (レベルは 0 に戻しました。次に入った時に建て直します)", e);
            return;
        }
        // 中にいたら、建て直した建物に埋まらないよう入った時の位置へ
        if (inside && data.getHideoutIndex() >= 0) {
            Location spawn = module.spawn(data.getHideoutIndex());
            if (spawn != null) player.teleport(spawn);
        }
        Messages.chat(player, "iem.hideout.hideoutcommand.handlereset.notice_002", MessageText.of("iem.hideout.hideoutcommand.handlereset.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlereset.notice_002.text_01").color(NamedTextColor.GREEN))));
    }

    /** (確認用・一時的) 見ているブロックが、どの設備の差分で置かれた物と判定されるか */
    private void handleInspect(Player player) {
        if (!player.isOp()) return;
        org.bukkit.block.Block block = player.getTargetBlockExact(8);
        if (block == null) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handleinspect.notice_001", MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        HideoutWorldManager worlds = module.getWorldManager();
        World hideoutWorld = worlds.getPlayerWorld();
        Messages.chat(player, "iem.hideout.hideoutcommand.handleinspect.notice_002", MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_002.text_01", block.getWorld().getName(), (hideoutWorld != null ? hideoutWorld.getName() : "null"), worlds.isPlayerWorld(block.getWorld())).color(NamedTextColor.GOLD))));
        int slot = HideoutModule.slotAt(block.getX());
        BlockVector3 origin = module.origin(slot);
        int x = block.getX() - origin.x(), y = block.getY() - origin.y(), z = block.getZ() - origin.z();
        Messages.chat(player, "iem.hideout.hideoutcommand.handleinspect.notice_003", MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_003.text_01", slot, origin, block.getType(), x, y, z).color(NamedTextColor.GOLD))));
        Messages.chat(player, "iem.hideout.hideoutcommand.handleinspect.notice_004", MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_004.text_01", module.facilityAt(player, block), data.getHideoutBuiltVersion(), module.getLayout().version()).color(NamedTextColor.GOLD))));
        BlockVector3 rel = BlockVector3.at(x, y, z);
        for (HideoutFacility facility : HideoutFacility.values()) {
            int level = data.getHideoutLevel(facility.id);
            int highest = module.getLayout().highestLevel(facility);
            if (level == 0 && highest == 0) continue;
            var built = module.getBuilder().placedBlocks(facility, level);
            var all = module.getBuilder().placedBlocks(facility, highest);
            Messages.chat(player, "iem.hideout.hideoutcommand.handleinspect.notice_005", MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_005", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handleinspect.notice_005.text_01", facility.id, level, highest, built.size(), all.size(), (built.contains(rel) ? MessageText.of("iem.hideout.hideoutcommand.block_current") : all.contains(rel) ? MessageText.of("iem.hideout.hideoutcommand.block_higher") : MessageText.of("iem.hideout.hideoutcommand.block_absent"))).color(NamedTextColor.YELLOW))));
        }
    }

    private void handleCamera(Player player, String[] args) {
        if (!player.isOp()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlecamera.notice_001", MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        HideoutFacility facility = args.length > 1 ? HideoutFacility.byId(args[1]) : null;
        if (facility == null) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlecamera.notice_002", MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_002.text_01", Arrays.stream(HideoutFacility.values())
                    .map(f -> f.id).collect(Collectors.joining(", "))).color(NamedTextColor.RED))));
            return;
        }
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        BlockVector3 origin = module.originAt(player);
        if (origin == null) {
            Messages.chat(player, "iem.hideout.hideoutcommand.handlecamera.notice_003", MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_003.text_01").color(NamedTextColor.RED))));
            return;
        }
        Location eye = player.getEyeLocation();
        HideoutLayout.CameraPose pose = new HideoutLayout.CameraPose();
        pose.x = eye.getX() - origin.x();
        pose.y = eye.getY() - origin.y();
        pose.z = eye.getZ() - origin.z();
        pose.yaw = eye.getYaw();
        pose.pitch = eye.getPitch();
        try {
            module.getLayout().saveCamera(facility, pose);
        } catch (IOException e) {
            fail(player, "カメラを記録できませんでした", e);
            return;
        }
        Messages.chat(player, "iem.hideout.hideoutcommand.handlecamera.notice_004", MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlecamera.notice_004.text_01", facility.label).color(NamedTextColor.GREEN))));
    }

    private void handleSave(Player player, String[] args) {
        if (!player.isOp()) return;
        Target target = target(player, args, "/hideout save <設備> <レベル> [force]");
        if (target == null) return;
        World world = module.getWorldManager().getEditorWorld();
        if (HideoutBuilder.isBaseItself(target.facility, target.level)) {
            // 用意していない区画 (空) をベースにしてしまわないよう、edit base 0 で用意した後だけ
            if (module.getLayout().reference(target.facility, target.level) == null) {
                Messages.chat(player, "iem.hideout.hideoutcommand.handlesave.notice_001", MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_001.text_01").color(NamedTextColor.RED))));
                return;
            }
            try {
                HideoutLayout.Base base = module.getBuilder().saveBaseFromEditor(world);
                Messages.chat(player, "iem.hideout.hideoutcommand.handlesave.notice_002", MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_002.text_01", base.entities.size()).color(NamedTextColor.GREEN))));
            } catch (IOException e) {
                fail(player, "ベースを保存できませんでした", e);
            }
            return;
        }
        try {
            boolean force = args.length > 3 && args[3].equalsIgnoreCase("force");
            HideoutBuilder.SaveResult result = module.getBuilder().saveLayer(world, target.facility, target.level, force);
            Messages.chat(player, "iem.hideout.hideoutcommand.handlesave.notice_003", MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_003.text_01", target.facility.label, target.level, result.blocks(), result.addedEntities(), result.removedEntities()).color(NamedTextColor.GREEN))));
            if (target.level > 1 && !module.getLayout().hasLayer(target.facility, target.level - 1)) {
                Messages.chat(player, "iem.hideout.hideoutcommand.handlesave.notice_004", MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.handlesave.notice_004.text_01", (target.level - 1), (target.level - 1)).color(NamedTextColor.GOLD))));
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
            Messages.chat(player, "iem.hideout.hideoutcommand.target.notice_001", MessageText.of("iem.hideout.hideoutcommand.target.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.target.notice_001.text_01", usage, Arrays.stream(HideoutFacility.values())
                    .map(f -> f.id + " " + f.minEditLevel() + "-" + f.editLimit()).collect(Collectors.joining(", "))).color(NamedTextColor.RED))));
            return null;
        }
        return new Target(facility, level);
    }

    private void fail(Player player, String what, Exception e) {
        module.getPlugin().getLogger().log(Level.WARNING, what, e);
        Messages.chat(player, "iem.hideout.hideoutcommand.fail.notice_001", MessageText.of("iem.hideout.hideoutcommand.fail.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.fail.notice_001.text_01", what, e.getMessage()).color(NamedTextColor.RED))));
    }

    private void sendHelp(Player player) {
        Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_001", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_001", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_001.text_01").color(NamedTextColor.GOLD))));
        Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_002", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_002", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_002.text_01").color(NamedTextColor.YELLOW))));
        if (player.isOp()) {
            Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_003", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_003", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_003.text_01").color(NamedTextColor.YELLOW))));
            Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_004", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_004", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_004.text_01").color(NamedTextColor.YELLOW))));
            Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_005", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_005", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_005.text_01").color(NamedTextColor.YELLOW))));
            Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_006", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_006", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_006.text_01").color(NamedTextColor.YELLOW))));
            Messages.chat(player, "iem.hideout.hideoutcommand.sendhelp.notice_007", MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_007", MessageText.payload(MessageText.of("iem.hideout.hideoutcommand.sendhelp.notice_007.text_01").color(NamedTextColor.YELLOW))));
        }
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("visit");
            if (sender.isOp()) options.addAll(List.of("base", "edit", "save", "reset", "camera", "inspect"));
        } else if (sender.isOp() && args.length == 2 && args[0].equalsIgnoreCase("base")) {
            options.add("save");
        } else if (sender.isOp() && args.length == 2 && args[0].equalsIgnoreCase("camera")) {
            Arrays.stream(HideoutFacility.values()).map(f -> f.id).forEach(options::add);
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
