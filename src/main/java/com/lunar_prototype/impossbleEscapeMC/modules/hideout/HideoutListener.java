package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class HideoutListener implements Listener {
    private final HideoutModule module;

    public HideoutListener(HideoutModule module) {
        this.module = module;
    }

    /** 隠れ家のワールドに入った時 (隠れ家の中で参加し直した時も) はアドベンチャーにする (ブロックを壊させない) */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        enforceAdventure(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        enforceAdventure(event.getPlayer());
    }

    private void enforceAdventure(Player player) {
        if (!player.getWorld().getName().equals(HideoutWorldManager.PLAYER_WORLD_NAME)) return;
        if (player.getGameMode() != GameMode.ADVENTURE) player.setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 右クリックは手ごとに来ることがあるので、メインハンドの時だけ (画面を2回開かない)
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        // 看板の1行目をチェックして種類を判別 (エディターで書いてNBT保存する想定)
        String line1 = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase();
        
        if (line1.contains("generator")) {
            event.getPlayer().sendMessage("発電機の燃料メニュー（未実装）を開きます。");
            // new GeneratorGUI(module, event.getPlayer()).open();
        } else if (line1.contains("workbench")) {
            event.getPlayer().sendMessage("ワークベンチのクラフトメニュー（未実装）を開きます。");
            // new WorkbenchGUI(module, event.getPlayer()).open();
        } else if (line1.contains("hideout")) {
            new HideoutGUI(module, event.getPlayer()).open();
        }
    }
}
