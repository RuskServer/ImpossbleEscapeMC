package com.lunar_prototype.impossbleEscapeMC.gui;

import org.bukkit.entity.Player;

/** 既存のコマンド・各画面の戻り先を保った、PDAホームの入口。 */
public final class PDAGUI {
    private final Player player;
    public PDAGUI(Player player) {this.player = player;}
    public void open() {PdaHomeScreen.open(player);}
    public static void shutdown() {PdaHomeScreen.shutdown();}
}
