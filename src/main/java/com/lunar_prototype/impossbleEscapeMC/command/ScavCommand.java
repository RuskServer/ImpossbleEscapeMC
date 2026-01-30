package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ScavCommand implements CommandExecutor {

    public ScavCommand(ImpossbleEscapeMC plugin) {

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        // 権限チェック (必要に応じて)
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        // SCAVをスポーンさせる
        try {
            ImpossbleEscapeMC.getInstance().getScavSpawner().spawnScav(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "SCAVをスポーンさせました。");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "スポーン中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}