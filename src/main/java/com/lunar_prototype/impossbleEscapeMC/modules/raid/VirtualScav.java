package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.Location;

import java.util.UUID;

public class VirtualScav {
    private final Location spawnLocation;
    private final boolean permanent;
    private UUID entityId;
    private boolean isSpawned;
    private boolean dead;

    public VirtualScav(Location spawnLocation, boolean permanent) {
        this.spawnLocation = spawnLocation;
        this.permanent = permanent;
        this.isSpawned = false;
        this.dead = false;
    }

    public boolean isDead() {
        return dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
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
