package com.lunar_prototype.impossbleEscapeMC.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 銃弾がエンティティに着弾した際に呼び出されるカスタムイベント
 */
public class BulletHitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity victim;
    private final LivingEntity shooter;
    private final double damage;
    private final String hitLocation; // head, arms, legs, body
    private final boolean penetrated;
    private final int ammoClass;

    public BulletHitEvent(LivingEntity victim, LivingEntity shooter, double damage, String hitLocation, boolean penetrated, int ammoClass) {
        this.victim = victim;
        this.shooter = shooter;
        this.damage = damage;
        this.hitLocation = hitLocation;
        this.penetrated = penetrated;
        this.ammoClass = ammoClass;
    }

    public LivingEntity getVictim() {
        return victim;
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public double getDamage() {
        return damage;
    }

    public String getHitLocation() {
        return hitLocation;
    }

    public boolean isPenetrated() {
        return penetrated;
    }

    public int getAmmoClass() {
        return ammoClass;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
