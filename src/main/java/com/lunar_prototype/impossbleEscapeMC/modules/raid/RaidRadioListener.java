package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.loot.event.LootItemRevealEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Player;

final class RaidRadioListener implements Listener {
    private final RaidModule manager;
    RaidRadioListener(RaidModule manager){this.manager=manager;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPickup(EntityPickupItemEvent event) {
        if(!(event.getEntity() instanceof Player player)||event.getRemaining()>=event.getItem().getItemStack().getAmount())return;
        RaidInstance raid=manager.getRaidOf(player);
        if(raid!=null)raid.radio().discovered(player,event.getItem().getItemStack());
    }
    @EventHandler public void onReveal(LootItemRevealEvent event) {
        RaidInstance raid=manager.getRaidOf(event.getPlayer());
        if(raid!=null)raid.radio().discovered(event.getPlayer(),event.getItem());
    }
}
