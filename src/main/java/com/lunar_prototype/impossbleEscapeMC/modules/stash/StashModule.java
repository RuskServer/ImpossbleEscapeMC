package com.lunar_prototype.impossbleEscapeMC.modules.stash;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.gui.GuiBackground;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.IOException;

public class StashModule implements IModule {
    private PlayerDataModule dataModule;

    @Override
    public void onEnable(ServiceContainer container) {
        this.dataModule = container.get(PlayerDataModule.class);
    }

    @Override
    public void onDisable() {
        //PlayerDataModule handles saving via onQuit and auto-save
    }

    public void openStash(Player player, int page) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        int rows = getRows(data.getStashLevel(), page);
        
        if (rows == 0) {
            player.sendMessage(Component.text("§cこのページはまだアンロックされていません。"));
            return;
        }

        String serialized = data.getStashPages().get(page);
        Inventory inv;
        // 背景は段数ごとに別の画像。帯の液晶にページ番号を出す
        GuiBackground background = rows == 6 ? GuiBackground.STASH_6 : GuiBackground.STASH_3;
        Component title = background.title(page + "/" + getMaxUnlockedPage(data.getStashLevel()));
        
        try {
            if (serialized != null) {
                inv = SerializationUtil.deserializeInventory(serialized, title);
                if (inv.getSize() > rows * 9) {
                    // 前の版は 1 ページ 6 行だった。縮めると下の段の物が消えるので、その大きさのまま開く
                    rows = inv.getSize() / 9;
                    background = rows == 6 ? GuiBackground.STASH_6 : GuiBackground.STASH_3;
                    title = background.title(page + "/" + getMaxUnlockedPage(data.getStashLevel()));
                    Inventory same = org.bukkit.Bukkit.createInventory(null, inv.getSize(), title);
                    for (int i = 0; i < inv.getSize(); i++) same.setItem(i, inv.getItem(i));
                    inv = same;
                } else if (inv.getSize() != rows * 9) {
                    // Ensure size matches current unlock level (in case it was upgraded)
                    Inventory newInv = org.bukkit.Bukkit.createInventory(null, rows * 9, title);
                    for (int i = 0; i < Math.min(inv.getSize(), newInv.getSize()); i++) {
                        newInv.setItem(i, inv.getItem(i));
                    }
                    inv = newInv;
                }
            } else {
                inv = org.bukkit.Bukkit.createInventory(null, rows * 9, title);
            }
        } catch (Exception e) {
            // 読めないページは開かない (空のページを開くと、閉じた時に空で上書きして中身が消える)。データはそのまま残す
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                    "スタッシュのページを読めませんでした: " + player.getName() + " ページ " + page, e);
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "スタッシュのページ " + page + " を読めませんでした (中身は消していません。運営に連絡してください)",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        new StashGUI(player, inv, page, this).open();
    }

    public void saveStash(Player player, int page, Inventory inventory) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        try {
            String serialized = SerializationUtil.serializeInventory(inventory);
            data.setStashPage(page, serialized);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 開放するページ (添字 = スタッシュのレベル)。Lv1=1, Lv2=2, Lv3=3,4, Lv4=5 */
    private static final int[] MAX_PAGE = {0, 1, 2, 4, 5};
    /** 1 ページの行数 (27 枠) */
    private static final int PAGE_ROWS = 3;

    public int getRows(int stashLevel, int page) {
        if (page < 1 || page > getMaxUnlockedPage(stashLevel)) return 0;
        return PAGE_ROWS;
    }
    
    public int getMaxUnlockedPage(int stashLevel) {
        int level = Math.max(0, Math.min(stashLevel, MAX_PAGE.length - 1));
        return MAX_PAGE[level];
    }
}
