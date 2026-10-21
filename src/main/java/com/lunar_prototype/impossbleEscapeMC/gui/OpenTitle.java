package com.lunar_prototype.impossbleEscapeMC.gui;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * 開いている画面のタイトルを替える (画面を開き直さずに)。Paper の InventoryView#setTitle と同じく、同じ番号で開く知らせを
 * 送り直して中身も送り直す。setTitle は文字列しか受け取らず、フォント (背景の絵の字) を指定できないため
 */
public final class OpenTitle {

    private OpenTitle() {
    }

    public static void set(Player player, Component title) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        AbstractContainerMenu menu = handle.containerMenu;
        if (menu == handle.inventoryMenu) return;
        handle.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), PaperAdventure.asVanilla(title)));
        menu.sendAllDataToRemote();
    }
}
