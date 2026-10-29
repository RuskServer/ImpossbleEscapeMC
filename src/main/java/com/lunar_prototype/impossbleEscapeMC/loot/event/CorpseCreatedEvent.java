package com.lunar_prototype.impossbleEscapeMC.loot.event;

/** 死体の保存済みインベントリと表示を連携するための通知。 */
public final class CorpseCreatedEvent extends org.bukkit.event.Event {
    private static final org.bukkit.event.HandlerList HANDLERS=new org.bukkit.event.HandlerList();
    private final org.bukkit.entity.LivingEntity victim;
    private final org.bukkit.entity.Mannequin corpse;
    private final org.bukkit.inventory.Inventory inventory;
    public CorpseCreatedEvent(org.bukkit.entity.LivingEntity victim,org.bukkit.entity.Mannequin corpse,org.bukkit.inventory.Inventory inventory){this.victim=victim;this.corpse=corpse;this.inventory=inventory;}
    public org.bukkit.entity.LivingEntity getVictim(){return victim;}
    public org.bukkit.entity.Mannequin getCorpse(){return corpse;}
    public org.bukkit.inventory.Inventory getInventory(){return inventory;}
    @Override public org.bukkit.event.@org.jetbrains.annotations.NotNull HandlerList getHandlers(){return HANDLERS;}
    public static org.bukkit.event.HandlerList getHandlerList(){return HANDLERS;}
}
