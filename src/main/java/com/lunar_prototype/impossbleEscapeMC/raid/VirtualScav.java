package com.lunar_prototype.impossbleEscapeMC.raid;

import org.bukkit.Location;

import java.util.UUID;

public class VirtualScav {
    private final Location spawnLocation;
    private final boolean permanent;
    private UUID entityId;
    private boolean isSpawned;

    public VirtualScav(Location spawnLocation, boolean permanent) {
        this.spawnLocation = spawnLocation;
        this.permanent = permanent;
        this.isSpawned = false;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public boolean isSpawned() {
        return isSpawned;
    }

    public void setSpawned(boolean spawned) {
        isSpawned = spawned;
    }
}
