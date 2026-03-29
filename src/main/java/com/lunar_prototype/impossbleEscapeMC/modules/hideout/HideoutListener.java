package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class HideoutListener implements Listener {
    private final HideoutModule module;

    public HideoutListener(HideoutModule module) {
        this.module = module;
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
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
