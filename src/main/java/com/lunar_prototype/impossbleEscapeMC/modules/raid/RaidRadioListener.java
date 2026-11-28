package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.loot.event.LootItemRevealEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class RaidRadioListener implements Listener {
    private final RaidModule manager;
    RaidRadioListener(RaidModule manager){this.manager=manager;}
    @EventHandler public void onReveal(LootItemRevealEvent event) {
        if(!event.isSearchCompletion())return;
        RaidInstance raid=manager.getRaidOf(event.getPlayer());
        if(raid!=null)raid.radio().discovered(event.getPlayer(),event.getItem());
    }
}
