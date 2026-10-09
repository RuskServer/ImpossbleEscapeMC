package com.lunar_prototype.impossbleEscapeMC.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatapackFunctionUtil {

    /**
     * 指定したデータパックのfunctionを実行し、その実行者に与えられたアイテムを取得します。
     * 実行者はダミーのServerPlayerとしてシミュレートされます。
     *
     * @param world 実行するワールドコンテキスト
     * @param functionNamespacePath 実行する関数の名前空間パス (例: "mypack:give_item")
     * @return 関数実行によって得られたBukkitアイテムスタックのリスト
     */
    public static List<org.bukkit.inventory.ItemStack> captureItemsFromFunction(World world, String functionNamespacePath) {
        List<org.bukkit.inventory.ItemStack> capturedItems = new ArrayList<>();

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) world).getHandle();

        // UUIDとプロファイルを作成
        UUID uuid = UUID.randomUUID();
        GameProfile dummyProfile = new GameProfile(uuid, "DummyCollector_" + uuid.toString().substring(0, 8));

        // ダミープレイヤーをメモリ上にのみ生成 (パケット送信やスポーンリストへの追加は行わない)
        ServerPlayer dummyPlayer = new ServerPlayer(server, level, dummyProfile, ClientInformation.createDefault());

        // ダミープレイヤーをベースにしたコマンドソーススタックの作成
        CommandSourceStack sourceStack = dummyPlayer.createCommandSourceStack()
                .withPermission(new net.minecraft.server.permissions.LevelBasedPermissionSet() {
                    @Override
                    public net.minecraft.server.permissions.PermissionLevel level() {
                        return net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS;
                    }
                }) // 一般的なデータパック関数実行用の権限レベル (通常は2)
                .withSuppressedOutput(); // ログ出力（〜にアイテムを1個与えました 等）をミュート

        // 関数の実行 (functionコマンドは実行キューが必要なため runCommand を使う)
        runCommand(server, sourceStack, "function " + functionNamespacePath);

        // ダミープレイヤーのインベントリからアイテムを回収する
        for (int i = 0; i < dummyPlayer.getInventory().getContainerSize(); i++) {
            ItemStack nmsItem = dummyPlayer.getInventory().getItem(i);
            if (!nmsItem.isEmpty()) {
                // BukkitのItemStackに変換して追加
                capturedItems.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsItem));
            }
        }

        // 回収完了後にインベントリをクリア
        dummyPlayer.getInventory().clearContent();

        return capturedItems;
    }

    /**
     * Toi's Armory データパックの銃付与functionを呼び出す (マクロ引数: id, display_name)。
     * アイテム構造をプラグイン側に持たないことで、データパック/MCバージョン更新時の書式ズレを防ぐ。
     */
    private static final String GUN_GIVE_FUNCTION = "toisarm:dialog/get_gun_with_id/with_trigger_count/with_id/with_data/";

    /**
     * 指定した銃IDと表示名を持つ銃のItemStackを生成します。
     * データパックの銃付与functionをダミープレイヤーとして実行し、付与されたアイテムを回収することでItemStackを取得します。
     *
     * @param world 実行するワールドコンテキスト
     * @param gunId 銃のID (例: "m4a1")
     * @param displayName 銃の表示名 (例: "M4A1 Carbine")
     * @return 生成されたItemStack（失敗した場合はnull）
     */
    public static org.bukkit.inventory.ItemStack generateGunItem(World world, String gunId, String displayName) {
        // display_name はデータパック側で "text":"$(display_name)" にそのまま埋め込まれるため、
        // その文字列用とマクロ引数(SNBT)用の2回エスケープする
        String command = "function " + GUN_GIVE_FUNCTION
                + " {id:\"" + escapeSnbtString(gunId) + "\",display_name:\"" + escapeSnbtString(escapeSnbtString(displayName)) + "\"}";

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) world).getHandle();

        UUID uuid = UUID.randomUUID();
        GameProfile dummyProfile = new GameProfile(uuid, "DummyCollector_" + uuid.toString().substring(0, 8));
        ServerPlayer dummyPlayer = new ServerPlayer(server, level, dummyProfile, ClientInformation.createDefault());

        CommandSourceStack sourceStack = dummyPlayer.createCommandSourceStack()
                .withPermission(new net.minecraft.server.permissions.LevelBasedPermissionSet() {
                    @Override
                    public net.minecraft.server.permissions.PermissionLevel level() {
                        return net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS;
                    }
                })
                .withSuppressedOutput();

        runCommand(server, sourceStack, command);

        // ダミープレイヤーのインベントリからアイテムを取得
        org.bukkit.inventory.ItemStack result = null;
        for (int i = 0; i < dummyPlayer.getInventory().getContainerSize(); i++) {
            ItemStack nmsItem = dummyPlayer.getInventory().getItem(i);
            if (!nmsItem.isEmpty()) {
                result = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsItem);
                break;
            }
        }

        dummyPlayer.getInventory().clearContent();

        if (result == null) {
            Bukkit.getLogger().warning("[DatapackFunctionUtil] No item was given by " + command
                    + " (datapack missing, unknown gun id, or the datapack's give command failed)");
        }
        return result;
    }

    /**
     * コマンドをバニラと同じ実行キューでその場で実行する。構文エラー・実行時エラーはログに出す。
     * (dispatcher.execute ではfunctionコマンドが正しく動かないため、バニラの実行キューを使う)
     *
     * Commands#performCommand は、別のコマンドの実行中 (/scavspawn など) に呼ぶと
     * 実行中のキューの末尾に積むだけで、そのコマンドが終わるまで実行されない。
     * 実行直後に結果 (付与されたアイテム) を読むため、常に専用のキューを作って即座に実行する。
     */
    private static void runCommand(MinecraftServer server, CommandSourceStack sourceStack, String command) {
        try {
            net.minecraft.commands.Commands commands = server.getCommands();
            com.mojang.brigadier.ParseResults<CommandSourceStack> parse = commands.getDispatcher().parse(command, sourceStack);
            com.mojang.brigadier.exceptions.CommandSyntaxException parseError = net.minecraft.commands.Commands.getParseException(parse);
            com.mojang.brigadier.context.ContextChain<CommandSourceStack> chain = parseError != null ? null
                    : com.mojang.brigadier.context.ContextChain.tryFlatten(parse.getContext().build(command)).orElse(null);
            if (chain == null) {
                Bukkit.getLogger().severe("[DatapackFunctionUtil] Command syntax error: "
                        + (parseError != null ? parseError.getMessage() : "incomplete command"));
                Bukkit.getLogger().severe("[DatapackFunctionUtil] Failed command: " + command);
                return;
            }

            net.minecraft.world.level.gamerules.GameRules rules = sourceStack.getLevel().getGameRules();
            int maxCommands = Math.max(1, rules.get(net.minecraft.world.level.gamerules.GameRules.MAX_COMMAND_SEQUENCE_LENGTH));
            int maxForks = rules.get(net.minecraft.world.level.gamerules.GameRules.MAX_COMMAND_FORKS);
            try (net.minecraft.commands.execution.ExecutionContext<CommandSourceStack> context =
                         new net.minecraft.commands.execution.ExecutionContext<>(maxCommands, maxForks, net.minecraft.util.profiling.Profiler.get())) {
                net.minecraft.commands.execution.ExecutionContext.queueInitialCommandExecution(
                        context, command, chain, sourceStack, net.minecraft.commands.CommandResultCallback.EMPTY);
                context.runCommandQueue();
            }
        } catch (Throwable e) {
            Bukkit.getLogger().severe("[DatapackFunctionUtil] Error executing command: " + e.getMessage());
            Bukkit.getLogger().severe("[DatapackFunctionUtil] Failed command: " + command);
            e.printStackTrace();
        }
    }

    private static String escapeSnbtString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
