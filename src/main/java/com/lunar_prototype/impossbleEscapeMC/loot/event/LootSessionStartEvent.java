package com.lunar_prototype.impossbleEscapeMC.loot.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public class LootSessionStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LootSessionSourceType sourceType;
    private final Object source;
    private final Inventory sourceInventory;

    public LootSessionStartEvent(Player player, LootSessionSourceType sourceType, Object source, Inventory sourceInventory) {
        this.player = player;
        this.sourceType = sourceType;
        this.source = source;
        this.sourceInventory = sourceInventory;
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

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
