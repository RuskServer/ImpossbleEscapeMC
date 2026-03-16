package com.lunar_prototype.impossbleEscapeMC.modules.stash;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
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
        Component title = Component.text("Stash - Page " + page);
        
        try {
            if (serialized != null) {
                inv = SerializationUtil.deserializeInventory(serialized, title);
                // Ensure size matches current unlock level (in case it was upgraded)
                if (inv.getSize() != rows * 9) {
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
            e.printStackTrace();
            inv = org.bukkit.Bukkit.createInventory(null, rows * 9, title);
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

    public int getRows(int stashLevel, int page) {
        if (page < 1 || page > 5) return 0;
        
        int totalHalfPages = stashLevel;
        int startHalfPage = (page - 1) * 2 + 1;
        
        if (totalHalfPages < startHalfPage) return 0; // Page not unlocked
        if (totalHalfPages >= startHalfPage + 1) return 6; // Both halves unlocked
        return 3; // Only first half unlocked
    }
    
    public int getMaxUnlockedPage(int stashLevel) {
        return (stashLevel + 1) / 2;
    }
}
