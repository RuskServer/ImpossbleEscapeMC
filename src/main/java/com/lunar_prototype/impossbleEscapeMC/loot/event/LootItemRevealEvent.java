package com.lunar_prototype.impossbleEscapeMC.loot.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** 漁りのサーチで実際に見えた品・検索済みスロットから取り出した品。未検索スロットの情報は通知しない。 */
public final class LootItemRevealEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player player;
    private final ItemStack item;
    public LootItemRevealEvent(Player player,ItemStack item){this.player=player;this.item=item.clone();}
    public Player getPlayer(){return player;}
    public ItemStack getItem(){return item.clone();}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
