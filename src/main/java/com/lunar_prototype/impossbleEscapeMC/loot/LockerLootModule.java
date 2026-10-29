package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionStartEvent;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** ロッカー。登録はマップ、中身・検索済み欄はInteractionのPDCに保存する。 */
public final class LockerLootModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final NamespacedKey ownerKey,slotKey,inventoryKey,generationKey,searchedKey,removerKey;
    private final NamespacedKey eggKey;
    private final Map<String,Cabinet> loaded=new HashMap<>();
    public LockerLootModule(ImpossbleEscapeMC plugin) {
        this.plugin=plugin;eggKey=new NamespacedKey(plugin,"locker_loot_egg_map");
        ownerKey=new NamespacedKey(plugin,"loot_locker_owner");slotKey=new NamespacedKey(plugin,"loot_locker_slot");
        inventoryKey=new NamespacedKey(plugin,"loot_locker_inventory");generationKey=new NamespacedKey(plugin,"loot_locker_generation");
        searchedKey=new NamespacedKey(plugin,"searched_slots");removerKey=new NamespacedKey(plugin,"loot_remover");
    }
    @Override public void onEnable(ServiceContainer services) {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(RaidMap map:plugin.getRaidModule().getMaps().values()) {
            String worldName=map.getWorldName();
            World world=worldName==null?null:Bukkit.getWorld(worldName);if(world==null)continue;
            for(var spec:map.getLootLockers())if(world.isChunkLoaded(spec.x>>4,spec.z>>4))ensure(map,spec,world);
        }
    }
    @Override public void onDisable() {
        for(Cabinet cabinet:List.copyOf(loaded.values())) {close(cabinet);cabinet.animate(false,0);}
        loaded.clear();HandlerList.unregisterAll(this);
    }
    public org.bukkit.inventory.ItemStack createEgg(String mapId) {
        var egg=new org.bukkit.inventory.ItemStack(Material.POLAR_BEAR_SPAWN_EGG);
        var meta=egg.getItemMeta();meta.displayName(Component.text("Locker Loot Placer",NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Map: "+mapId,NamedTextColor.YELLOW),
                Component.text("右クリックでロッカーを設置・登録",NamedTextColor.AQUA),
                Component.text("補充ごとに25%で中身あり・3種類からランダム",NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(eggKey,PersistentDataType.STRING,mapId);egg.setItemMeta(meta);return egg;
    }
    private boolean isEgg(org.bukkit.inventory.ItemStack item) {
        return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(eggKey,PersistentDataType.STRING);
    }
    @EventHandler public void placeEgg(PlayerInteractEvent event) {
        if(!isEgg(event.getItem()))return;
        event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND||event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null)return;
        Player player=event.getPlayer();
        if(!player.isOp()){player.sendMessage(Component.text("ロッカーの設置はOP専用です。",NamedTextColor.RED));return;}
        try {place(player,event.getItem().getItemMeta().getPersistentDataContainer().get(eggKey,PersistentDataType.STRING),
                event.getClickedBlock().getRelative(event.getBlockFace()));}
        catch(IllegalArgumentException ex){player.sendMessage(Component.text(ex.getMessage(),NamedTextColor.RED));}
    }
    @EventHandler public void eggEntity(PlayerInteractEntityEvent event) {
        var inventory=event.getPlayer().getInventory();
        if(isEgg(event.getHand()==EquipmentSlot.HAND?inventory.getItemInMainHand():inventory.getItemInOffHand()))event.setCancelled(true);
    }
    @EventHandler public void eggEntityAt(PlayerInteractAtEntityEvent event){eggEntity(event);}
    private record Placement(RaidMap map,RaidMap.LootLocker spec) {}
    private Placement at(Block block) {
        if(block==null)return null;
        for(RaidMap map:plugin.getRaidModule().getMaps().values())if(map.isWorld(block.getWorld()))
            for(var s:map.getLootLockers())if(s.x==block.getX()&&(s.y==block.getY()||s.y+1==block.getY())&&s.z==block.getZ())return new Placement(map,s);
        return null;
    }
    public boolean isLocker(Block block){return at(block)!=null;}
    public void place(Player player,String mapId,Block target) {
        RaidMap map=plugin.getRaidModule().getMap(mapId);
        if(map==null||!map.isWorld(player.getWorld()))throw new IllegalArgumentException("対象マップのワールドで設置してください。");
        for(String crate:LockerLootRoll.CRATES)if(plugin.getLootManager().getCrate(crate)==null)throw new IllegalArgumentException("必要なクレートがありません: "+crate);
        if(target==null||target.getY()+2>=target.getWorld().getMaxHeight())throw new IllegalArgumentException("設置先の高さを確認してください。");
        for(int dy=0;dy<3;dy++)if(!target.getRelative(0,dy,0).getType().isAir()||at(target.getRelative(0,dy,0))!=null)throw new IllegalArgumentException("設置先は上3ブロック分を空けてください。");
        var spec=new RaidMap.LootLocker(UUID.randomUUID().toString(),target.getX(),target.getY(),target.getZ(),LockerModel.facing(player.getYaw()));
        map.getLootLockers().add(spec);plugin.getRaidModule().saveMap(map);
        target.setType(Material.BARRIER,false);target.getRelative(0,1,0).setType(Material.BARRIER,false);ensure(map,spec,target.getWorld());
        player.sendMessage(Component.text("ロッカーを設置しました ("+mapId+")",NamedTextColor.GREEN));
    }
    public boolean removeAt(Block block) {
        Placement placement=at(block);if(placement==null)return false;
        var spec=placement.spec();Cabinet cabinet=loaded.remove(spec.id);
        if(cabinet!=null)close(cabinet);
        // 当該チャンクの自分の部品だけを消す。持ち物は落とさない。
        for(Entity entity:block.getChunk().getEntities())if(spec.id.equals(entity.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING)))entity.remove();
        placement.map().getLootLockers().remove(spec);plugin.getRaidModule().saveMap(placement.map());
        for(int dy=0;dy<2;dy++){Block cell=block.getWorld().getBlockAt(spec.x,spec.y+dy,spec.z);if(cell.getType()==Material.BARRIER)cell.setType(Material.AIR,false);}
        return true;
    }
    /** 未ロードの家具は世代番号だけ更新し、次のチャンク読込時に補充する。 */
    public void refill(RaidMap map) {
        if(map.getLootLockers().isEmpty())return;
        for(var spec:map.getLootLockers()) {
            spec.generation++;
            Cabinet cabinet=loaded.get(spec.id);
            if(cabinet!=null){close(cabinet);for(Source source:cabinet.sources)source.refill();}
        }
        plugin.getRaidModule().saveMap(map);
    }
    public boolean refillAt(Block block) {
        Placement p=at(block);if(p==null)return false;
        Cabinet cabinet=ensure(p.map(),p.spec(),block.getWorld());
        if(cabinet==null)return false;
        p.spec().generation++;plugin.getRaidModule().saveMap(p.map());
        close(cabinet);for(Source source:cabinet.sources)source.refill();return true;
    }
    private Cabinet ensure(RaidMap map,RaidMap.LootLocker spec,World world) {
        Cabinet existing=loaded.get(spec.id);if(existing!=null)return existing;
        Location anchor=new Location(world,spec.x+.5,spec.y,spec.z+.5);
        Block block=anchor.getBlock();
        for(int dy=0;dy<2;dy++) {
            Material type=block.getRelative(0,dy,0).getType();
            if(type!=Material.BARRIER&&!type.isAir()) {
                plugin.getLogger().warning("ロッカーの設置位置が別ブロックで塞がっています: "+spec.id);return null;
            }
        }
        for(int dy=0;dy<2;dy++)block.getRelative(0,dy,0).setType(Material.BARRIER,false);
        Interaction[] handles=new Interaction[1];
        for(Entity entity:world.getChunkAt(spec.x>>4,spec.z>>4).getEntities()) {
            var data=entity.getPersistentDataContainer();
            if(!spec.id.equals(data.get(ownerKey,PersistentDataType.STRING)))continue;
            Integer slot=data.get(slotKey,PersistentDataType.INTEGER);
            if(entity instanceof Interaction handle&&slot!=null&&slot>=0&&slot<1&&handles[slot]==null)handles[slot]=handle;
            else entity.remove();
        }
        Cabinet cabinet=new Cabinet(map,spec,anchor);
        for(int slot=0;slot<1;slot++) {
            int index=slot;
            if(handles[slot]==null)handles[slot]=world.spawn(anchor.clone().add(0,.02,0),Interaction.class,e->{
                e.setInteractionWidth(1.125f);e.setInteractionHeight(2.25f);e.setResponsive(true);
                e.setPersistent(true);e.setInvulnerable(true);
                e.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,spec.id);
                e.getPersistentDataContainer().set(slotKey,PersistentDataType.INTEGER,index);
            });
            cabinet.sources[slot]=new Source(cabinet,slot,handles[slot]);
        }
        for(boolean door:new boolean[]{false,true}) {
            var item=new org.bukkit.inventory.ItemStack(Material.PAPER);var meta=item.getItemMeta();
            meta.setItemModel(new NamespacedKey("iem","locker/"+(door?"door":"shell")));item.setItemMeta(meta);
            ItemDisplay display=world.spawn(anchor,ItemDisplay.class,e->{
                e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);e.setPersistent(true);e.setInvulnerable(true);
                e.setDisplayWidth(4);e.setDisplayHeight(5);e.setShadowRadius(0);
                e.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,spec.id);
                e.setTransformationMatrix(LockerModel.transform(door,spec.facing,false));
            });
            cabinet.parts.add(display);
        }
        loaded.put(spec.id,cabinet);return cabinet;
    }
    private final class Cabinet {
        final RaidMap map;final RaidMap.LootLocker spec;final Location anchor;
        final Source[] sources=new Source[1];final List<ItemDisplay> parts=new ArrayList<>();
        Cabinet(RaidMap map,RaidMap.LootLocker spec,Location anchor){this.map=map;this.spec=spec;this.anchor=anchor;}
        void animate(boolean open,int duration) {
            for(int i=1;i<parts.size();i++) {
                ItemDisplay display=parts.get(i);if(!display.isValid())continue;
                display.setInterpolationDelay(0);display.setInterpolationDuration(duration);
                display.setTransformationMatrix(LockerModel.transform(true,spec.facing,open));
            }
        }
    }
    private final class Source implements SearchableLootSource {
        final Cabinet cabinet;final int slot;final Interaction handle;final Inventory inventory;
        final Set<UUID> viewers=new HashSet<>();boolean available=true;
        Source(Cabinet cabinet,int slot,Interaction handle) {
            this.cabinet=cabinet;this.slot=slot;this.handle=handle;
            Inventory restored=null;byte[] bytes=data().get(inventoryKey,PersistentDataType.BYTE_ARRAY);
            if(bytes!=null)try {restored=SerializationUtil.deserializeInventoryFromBytes(bytes,Component.text("ロッカー"));}
            catch(Exception ex){available=false;plugin.getLogger().severe("ロッカーの中身を復元できません: "+cabinet.spec.id+" / "+slot);}
            if(restored!=null&&restored.getSize()!=27){available=false;plugin.getLogger().severe("ロッカーの保存サイズが不正です: "+cabinet.spec.id);}
            inventory=restored!=null?restored:Bukkit.createInventory(null,27,Component.text("ロッカー"));
            Long generation=data().get(generationKey,PersistentDataType.LONG);
            if(available&&(bytes==null||generation==null||generation!=cabinet.spec.generation))refill();
        }
        @Override public Inventory inventory(){return available&&handle.isValid()?inventory:null;}
        @Override public PersistentDataContainer data(){return handle.getPersistentDataContainer();}
        @Override public void persist() {
            if(!available)return;
            try {data().set(inventoryKey,PersistentDataType.BYTE_ARRAY,SerializationUtil.serializeInventoryToBytes(inventory));}
            catch(java.io.IOException ex){available=false;plugin.getLogger().severe("ロッカーの保存に失敗しました: "+cabinet.spec.id);}
        }
        void refill() {
            if(!available)return;
            inventory.clear();data().remove(searchedKey);
            String crate=LockerLootRoll.select(cabinet.spec.id,cabinet.spec.generation);
            var items=new ArrayList<org.bukkit.inventory.ItemStack>();
            if(crate!=null)items.addAll(plugin.getLootManager().rollContents(handle.getWorld(),crate));
            Collections.shuffle(items);
            for(int i=0;i<Math.min(items.size(),inventory.getSize());i++)inventory.setItem(i,items.get(i));
            data().set(generationKey,PersistentDataType.LONG,cabinet.spec.generation);persist();
        }
    }
    private void close(Cabinet cabinet) {
        for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder holder
                &&holder.getSource() instanceof Source source&&source.cabinet==cabinet)player.closeInventory();
        for(Source source:cabinet.sources){source.viewers.clear();source.persist();}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event){interactLocker(event);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interactAt(PlayerInteractAtEntityEvent event){interactLocker(event);}
    private void interactLocker(PlayerInteractEntityEvent event) {
        var data=event.getRightClicked().getPersistentDataContainer();String id=data.get(ownerKey,PersistentDataType.STRING);
        if(id==null)return;event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND)return;
        Cabinet cabinet=loaded.get(id);if(cabinet==null)return;
        Player player=event.getPlayer();
        if(player.getLocation().distanceSquared(cabinet.anchor)>36)return;
        var held=player.getInventory().getItemInMainHand();
        if(player.isOp()&&held.hasItemMeta()&&held.getItemMeta().getPersistentDataContainer().has(removerKey,PDCKeys.BOOLEAN)) {
            removeAt(cabinet.anchor.getBlock());player.sendMessage(Component.text("ロッカーを削除しました。",NamedTextColor.GREEN));return;
        }
        Integer slot=data.get(slotKey,PersistentDataType.INTEGER);if(slot==null||slot<0||slot>=1)return;
        Source source=cabinet.sources[slot];
        if(player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder holder&&holder.getSource()==source)return;
        if(!source.available){player.sendMessage(Component.text("このロッカーのデータを確認できません。管理者に連絡してください。",NamedTextColor.RED));return;}
        plugin.getSearchGUI().open(player,source);
    }
    @EventHandler public void opened(LootSessionStartEvent event) {
        if(!(event.getSource() instanceof Source source))return;
        if(!(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder holder)||holder.getSource()!=source)return;
        if(source.viewers.add(event.getPlayer().getUniqueId())&&source.viewers.size()==1) {
            source.cabinet.animate(true,LockerModel.ANIMATION_TICKS);
            source.handle.getWorld().playSound(source.cabinet.anchor,Sound.BLOCK_BARREL_OPEN,SoundCategory.BLOCKS,.6f,.85f);
        }
    }
    @EventHandler public void closed(LootSessionEndEvent event) {
        if(!(event.getSource() instanceof Source source))return;
        if(!source.viewers.remove(event.getPlayer().getUniqueId()))return;
        source.persist();
        if(!plugin.isEnabled())return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(source.viewers.isEmpty()&&loaded.get(source.cabinet.spec.id)==source.cabinet) {
                source.cabinet.animate(false,LockerModel.ANIMATION_TICKS);
                source.handle.getWorld().playSound(source.cabinet.anchor,Sound.BLOCK_BARREL_CLOSE,SoundCategory.BLOCKS,.6f,.85f);
            }
        });
    }
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{
        if(!event.getWorld().isChunkLoaded(event.getChunk().getX(),event.getChunk().getZ()))return;
        for(RaidMap map:plugin.getRaidModule().getMaps().values())if(map.isWorld(event.getWorld()))
            for(var spec:map.getLootLockers())if((spec.x>>4)==event.getChunk().getX()&&(spec.z>>4)==event.getChunk().getZ())ensure(map,spec,event.getWorld());
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chunkUnloaded(ChunkUnloadEvent event) {
        for(Cabinet cabinet:List.copyOf(loaded.values()))if(cabinet.anchor.getWorld()==event.getWorld()
                &&(cabinet.spec.x>>4)==event.getChunk().getX()&&(cabinet.spec.z>>4)==event.getChunk().getZ()) {
            close(cabinet);cabinet.animate(false,0);loaded.remove(cabinet.spec.id);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void worldUnloaded(WorldUnloadEvent event) {
        for(Cabinet cabinet:List.copyOf(loaded.values()))if(cabinet.anchor.getWorld()==event.getWorld()) {
            close(cabinet);cabinet.animate(false,0);loaded.remove(cabinet.spec.id);
        }
    }
    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        if(event.getEntity().getPersistentDataContainer().has(ownerKey,PersistentDataType.STRING))event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent event){if(isLocker(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::isLocker))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::isLocker))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent event){event.blockList().removeIf(this::isLocker);}
    @EventHandler(ignoreCancelled=true) public void explode(BlockExplodeEvent event){event.blockList().removeIf(this::isLocker);}
}
