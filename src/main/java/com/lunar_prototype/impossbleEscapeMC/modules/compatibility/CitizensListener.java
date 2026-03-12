package com.lunar_prototype.impossbleEscapeMC.modules.compatibility;

import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderGUI;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CitizensListener implements Listener {
    private final TraderModule traderModule;

    public CitizensListener(TraderModule traderModule) {
        this.traderModule = traderModule;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        int npcId = event.getNPC().getId();
        TraderDefinition trader = traderModule.getTraderByNpcId(npcId);
        
        if (trader != null) {
            new TraderGUI(traderModule, trader, event.getClicker()).open();
        } else {
            // デバッグ用: クリックされたが紐付けがない場合にログを出力
            traderModule.getPlugin().getLogger().info("[Trader] NPC Clicked: ID=" + npcId + " (No trader assigned in traders.yml)");
        }
    }
}
