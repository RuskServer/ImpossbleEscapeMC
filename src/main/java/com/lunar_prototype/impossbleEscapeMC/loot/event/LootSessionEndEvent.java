package com.lunar_prototype.impossbleEscapeMC.loot.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public class LootSessionEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LootSessionSourceType sourceType;
    private final Object source;
    private final Inventory sourceInventory;
    private final LootSessionEndReason reason;

    public LootSessionEndEvent(Player player,
                               LootSessionSourceType sourceType,
                               Object source,
                               Inventory sourceInventory,
                               LootSessionEndReason reason) {
        this.player = player;
        this.sourceType = sourceType;
        this.source = source;
        this.sourceInventory = sourceInventory;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public LootSessionSourceType getSourceType() {
        return sourceType;
    }

    public Object getSource() {
        return source;
    }

    public Inventory getSourceInventory() {
        return sourceInventory;
    }

    public LootSessionEndReason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
