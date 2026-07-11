package com.lunar_prototype.impossbleEscapeMC.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.inventory.ItemStack as BukkitItemStack;

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
    public static List<BukkitItemStack> captureItemsFromFunction(World world, String functionNamespacePath) {
        List<BukkitItemStack> capturedItems = new ArrayList<>();

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) world).getHandle();

        // UUIDとプロファイルを作成
        UUID uuid = UUID.randomUUID();
        GameProfile dummyProfile = new GameProfile(uuid, "DummyCollector_" + uuid.toString().substring(0, 8));

        // ダミープレイヤーをメモリ上にのみ生成 (パケット送信やスポーンリストへの追加は行わない)
        ServerPlayer dummyPlayer = new ServerPlayer(server, level, dummyProfile, ClientInformation.createDefault());

        // ダミープレイヤーをベースにしたコマンドソーススタックの作成
        CommandSourceStack sourceStack = dummyPlayer.createCommandSourceStack()
                .withPermission(2) // 一般的なデータパック関数実行用の権限レベル (通常は2)
                .withSuppressedOutput(); // ログ出力（〜にアイテムを1個与えました 等）をミュート

        // 関数の取得と実行
        ResourceLocation functionKey = ResourceLocation.tryParse(functionNamespacePath);
        if (functionKey != null) {
            server.getFunctions().get(functionKey).ifPresent(function -> {
                // 関数を実行する
                server.getFunctions().execute(function, sourceStack);
            });
        }

        // ダミープレイヤーのインベントリからアイテムを回収する
        for (int i = 0; i < dummyPlayer.getInventory().getContainerSize(); i++) {
            ItemStack nmsItem = dummyPlayer.getInventory().getItem(i);
            if (!nmsItem.isEmpty()) {
                // BukkitのItemStackに変換して追加
                capturedItems.add(nmsItem.asBukkitMirror());
            }
        }

        // 回収完了後にインベントリをクリア
        dummyPlayer.getInventory().clearContent();

        return capturedItems;
    }

    /**
     * 指定した銃IDと表示名を持つ銃のItemStackを生成します。
     * コマンドテンプレートを実行してダミープレイヤーに付与し、それを回収することでItemStackを取得します。
     *
     * @param world 実行するワールドコンテキスト
     * @param gunId 銃のID (例: "m4a1")
     * @param displayName 銃の表示名 (例: "M4A1 Carbine")
     * @return 生成されたBukkitItemStack（失敗した場合はnull）
     */
    public static BukkitItemStack generateGunItem(World world, String gunId, String displayName) {
        String rawTemplate = "give @s crossbow[\n" +
                "  piercing_weapon={min_reach:0.0,max_reach:0,hitbox_margin:0,deals_knockback:false,dismounts:false},\n" +
                "  swing_animation={duration:1,type:\"none\"},\n" +
                "  minecraft:item_name={\"text\":\"$(display_name)\",\"color\":\"white\"},\n" +
                "  minecraft:custom_data={\n" +
                "    toisarm:{\n" +
                "      type:gun,\n" +
                "      id:$(id),\n" +
                "      state:{\n" +
                "        chamber:0b,\n" +
                "        ammo_remaining:0,\n" +
                "        modes_index:0\n" +
                "      },\n" +
                "      attachment:[\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  minecraft:charged_projectiles=[\n" +
                "    {\n" +
                "      count:1,\n" +
                "      id:\"minecraft:arrow\"\n" +
                "    }\n" +
                "  ],\n" +
                "  minecraft:enchantments={\n" +
                "    \"toisarm:gun\":1\n" +
                "  },\n" +
                "  minecraft:item_model=\"toisarm:gun/$(id)/idle\",\n" +
                "  minecraft:custom_model_data={floats:[0,0,0,0,0,0,0],strings:[\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\",\"default\"]},\n" +
                "  minecraft:enchantment_glint_override=false,\n" +
                "  minecraft:rarity=epic,\n" +
                "  attribute_modifiers=[\n" +
                "    {\n" +
                "      id:\"block_break_speed\",\n" +
                "      type:\"block_break_speed\",\n" +
                "      amount:-10,\n" +
                "      operation:\"add_value\",\n" +
                "    },\n" +
                "    {\n" +
                "      id:\"block_interaction_range\",\n" +
                "      type:\"block_interaction_range\",\n" +
                "      amount:-10,\n" +
                "      operation:\"add_value\",\n" +
                "    },\n" +
                "    {\n" +
                "      id:\"entity_interaction_range\",\n" +
                "      type:\"entity_interaction_range\",\n" +
                "      amount:-10,\n" +
                "      operation:\"add_value\",\n" +
                "    },\n" +
                "    {\n" +
                "      id:\"sneaking_speed\",\n" +
                "      type:\"sneaking_speed\",\n" +
                "      amount:0.15,\n" +
                "      operation:\"add_value\"\n" +
                "    }\n" +
                "  ],\n" +
                "  tooltip_display={\n" +
                "    hidden_components:[\n" +
                "      \"charged_projectiles\",\n" +
                "      \"attribute_modifiers\",\n" +
                "      \"enchantments\"\n" +
                "    ]\n" +
                "  }\n" +
                "]";

        // プレースホルダーの置き換え
        String command = rawTemplate
                .replace("$(id)", gunId)
                .replace("$(display_name)", displayName);

        // コマンド実行のため改行を削除し、1行にする
        command = command.replaceAll("\\r?\\n", " ").replaceAll("\\s+", " ");

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) world).getHandle();

        UUID uuid = UUID.randomUUID();
        GameProfile dummyProfile = new GameProfile(uuid, "DummyCollector_" + uuid.toString().substring(0, 8));
        ServerPlayer dummyPlayer = new ServerPlayer(server, level, dummyProfile, ClientInformation.createDefault());

        CommandSourceStack sourceStack = dummyPlayer.createCommandSourceStack()
                .withPermission(2)
                .withSuppressedOutput();

        // コマンドを実行
        server.getCommands().performPrefixedCommand(sourceStack, command);

        // ダミープレイヤーのインベントリからアイテムを取得
        BukkitItemStack result = null;
        for (int i = 0; i < dummyPlayer.getInventory().getContainerSize(); i++) {
            ItemStack nmsItem = dummyPlayer.getInventory().getItem(i);
            if (!nmsItem.isEmpty()) {
                result = nmsItem.asBukkitMirror();
                break;
            }
        }

        dummyPlayer.getInventory().clearContent();
        return result;
    }
}
