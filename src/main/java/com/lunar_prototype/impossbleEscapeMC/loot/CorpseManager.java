package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CorpseManager {
    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Location> activeCorpseLocations = new HashMap<>();

    public CorpseManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public void spawnCorpse(LivingEntity victim) {
        Location loc = victim.getLocation();
        Mannequin mannequin = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        
        // --- 永続化を無効化 (サーバー再起動時に残らないようにする) ---
        mannequin.setPersistent(false);
        mannequin.setRemoveWhenFarAway(false);
        
        mannequin.setCustomNameVisible(false);
        mannequin.customName(Component.text("SCAV の死体", NamedTextColor.GRAY));

        mannequin.setProfile(ResolvableProfile.resolvableProfile().skinPatch(skinPatchBuilder -> skinPatchBuilder.model(PlayerTextures.SkinModel.CLASSIC).body(Key.key("minecraft","entity/player/wide/scav"))).build());
        
        // Posing (Simple "lying down" look)
        mannequin.setPose(Pose.SWIMMING);
        
        // Sync Equipment
        if (victim.getEquipment() != null) {
            mannequin.getEquipment().setHelmet(victim.getEquipment().getHelmet());
            mannequin.getEquipment().setChestplate(victim.getEquipment().getChestplate());
            mannequin.getEquipment().setLeggings(victim.getEquipment().getLeggings());
            mannequin.getEquipment().setBoots(victim.getEquipment().getBoots());
            mannequin.getEquipment().setItemInMainHand(victim.getEquipment().getItemInMainHand());
            mannequin.getEquipment().setItemInOffHand(victim.getEquipment().getItemInOffHand());
        }

        // Create virtual inventory
        Inventory virtualInv = Bukkit.createInventory(null, 27, Component.text("死体漁り"));
        if (victim.getEquipment() != null) {
            virtualInv.addItem(victim.getEquipment().getHelmet());
            virtualInv.addItem(victim.getEquipment().getChestplate());
            virtualInv.addItem(victim.getEquipment().getLeggings());
            virtualInv.addItem(victim.getEquipment().getBoots());
            
            ItemStack mainHand = victim.getEquipment().getItemInMainHand();
            virtualInv.addItem(mainHand);
            virtualInv.addItem(victim.getEquipment().getItemInOffHand());

            if (mainHand != null && mainHand.hasItemMeta()) {
                PersistentDataContainer pdc = mainHand.getItemMeta().getPersistentDataContainer();
                String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition def = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.get(itemId);
                
                if (def != null && "GUN".equalsIgnoreCase(def.type) && def.gunStats != null) {
                    String ammoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
                    if (ammoId == null) {
                        com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition defaultAmmo = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry.getWeakestAmmoForCaliber(def.gunStats.caliber);
                        if (defaultAmmo != null) ammoId = defaultAmmo.id;
                    }

                    if (ammoId != null) {
                        int amount = 30 + (int)(Math.random() * 31);
                        ItemStack ammoStack = com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.create(ammoId);
                        if (ammoStack != null) {
                            ammoStack.setAmount(amount);
                            virtualInv.addItem(ammoStack);
                        }
                    }
                }
            }
        }
        
        try {
            String serialized = serializeInventory(virtualInv);
            mannequin.getPersistentDataContainer().set(PDCKeys.CORPSE_INVENTORY, PersistentDataType.STRING, serialized);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to serialize corpse inventory!");
        }

        UUID uuid = mannequin.getUniqueId();
        activeCorpseLocations.put(uuid, loc);

        // Schedule removal (90 seconds = 1800 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeCorpseLocations.remove(uuid);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            } else {
                // チャンクがアンロードされている可能性があるため、強制的にロードして消去
                loc.getChunk().load();
                org.bukkit.entity.Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
            }
        }, 1800L);
    }

    public void cleanup() {
        plugin.getLogger().info("Cleaning up " + activeCorpseLocations.size() + " corpses...");
        for (Map.Entry<UUID, Location> entry : activeCorpseLocations.entrySet()) {
            Location loc = entry.getValue();
            if (loc.getWorld() == null) continue;

            // チャンクを強制ロードして削除を確実にする
            if (!loc.getChunk().isLoaded()) {
                loc.getChunk().load();
            }

            org.bukkit.entity.Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null) {
                entity.remove();
            }
        }
        activeCorpseLocations.clear();
    }

    // Serialization utilities
    public static String serializeInventory(Inventory inventory) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeInt(inventory.getSize());
        for (int i = 0; i < inventory.getSize(); i++) {
            dataOutput.writeObject(inventory.getItem(i));
        }
        dataOutput.close();
        return Base64Coder.encodeLines(outputStream.toByteArray());
    }

    public static Inventory deserializeInventory(String data, Component title) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        Inventory inventory = Bukkit.createInventory(null, dataInput.readInt(), title);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, (ItemStack) dataInput.readObject());
        }
        dataInput.close();
        return inventory;
    }
}
