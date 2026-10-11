package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 数値の画素 (空のシェーダーが画面に書くカメラ・太陽の向きなど) の確認用エフェクトを、自分の画面に付け外しする。
 * 正しく読めていれば、地平線・方角・太陽の輪が絵とずれずに重なる。左下の四角が緑なら数値がそろっている
 */
public class ShaderDebugCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーだけが使えます");
            return true;
        }
        boolean enabled = ScreenEffectService.toggleControlDebug(player);
        player.sendMessage(Component.text(enabled
                ? "数値の画素の確認を表示 (左下: 緑 = 空と太陽の数値あり / 黄 = 空だけ / 赤 = 無し)"
                : "数値の画素の確認を消しました", NamedTextColor.AQUA));
        return true;
    }
}
