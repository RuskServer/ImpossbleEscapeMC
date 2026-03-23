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
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.profile.PlayerProfile;
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
        spawnCorpse(victim, victim.getKiller());
    }

    public void spawnCorpse(LivingEntity victim, LivingEntity killer) {
        if (victim instanceof Player player) {
            spawnPlayerCorpse(player, killer);
            return;
        }

        Location loc = victim.getLocation();
        Mannequin mannequin = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        
        // --- 永続化を無効化 (サーバー再起動時に残らないようにする) ---
        mannequin.setPersistent(false);
        mannequin.setRemoveWhenFarAway(false);
        
        mannequin.setCustomNameVisible(false);

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
            virtualInv.setItem(0, victim.getEquipment().getHelmet());
            virtualInv.setItem(1, victim.getEquipment().getChestplate());
            virtualInv.setItem(2, victim.getEquipment().getLeggings());
            virtualInv.setItem(3, victim.getEquipment().getBoots());
            
            ItemStack mainHand = victim.getEquipment().getItemInMainHand();
            virtualInv.setItem(4, mainHand);
            virtualInv.setItem(5, victim.getEquipment().getItemInOffHand());

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
        
        finishSpawning(mannequin, virtualInv, loc, 1800L);
    }

    public void spawnPlayerCorpse(Player player, LivingEntity killer) {
        Location loc = player.getLocation();
        Mannequin mannequin = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        
        mannequin.setPersistent(false);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setCustomNameVisible(true);
        mannequin.customName(Component.text(player.getName() + " の死体", NamedTextColor.GRAY));

        // Set player's skin
        mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
        
        mannequin.setPose(Pose.SWIMMING);
        
        PlayerInventory playerInv = player.getInventory();
        
        // Sync Equipment to Mannequin (Visible)
        if (mannequin.getEquipment() != null) {
            mannequin.getEquipment().setHelmet(playerInv.getHelmet());
            mannequin.getEquipment().setChestplate(playerInv.getChestplate());
            mannequin.getEquipment().setLeggings(playerInv.getLeggings());
            mannequin.getEquipment().setBoots(playerInv.getBoots());
            mannequin.getEquipment().setItemInMainHand(playerInv.getItemInMainHand());
            mannequin.getEquipment().setItemInOffHand(playerInv.getItemInOffHand());
        }

        // Create virtual inventory (54 slots for full player inventory)
        Inventory virtualInv = Bukkit.createInventory(null, 54, Component.text(player.getName() + " の死体"));
        
        // Map Player Inventory to Virtual Inventory
        // 0-3: Armor
        virtualInv.setItem(0, playerInv.getHelmet());
        virtualInv.setItem(1, playerInv.getChestplate());
        virtualInv.setItem(2, playerInv.getLeggings());
        virtualInv.setItem(3, playerInv.getBoots());
        // 4: Main Hand, 5: Off Hand
        virtualInv.setItem(4, playerInv.getItemInMainHand());
        virtualInv.setItem(5, playerInv.getItemInOffHand());
        
        // Dog Tag in slot 8
        virtualInv.setItem(8, createDogTag(player, killer));
        
        // 9-44: Player's 36 inventory slots
        for (int i = 0; i < 36; i++) {
            virtualInv.setItem(9 + i, playerInv.getItem(i));
        }

        // Finish spawning with 10 minutes (12000 ticks) duration for players
        finishSpawning(mannequin, virtualInv, loc, 12000L);
    }

    private ItemStack createDogTag(Player victim, LivingEntity killer) {
        ItemStack dogTag = new ItemStack(Material.NAME_TAG);
        var meta = dogTag.getItemMeta();
        
        meta.displayName(Component.text("ドッグタグ: " + victim.getName(), NamedTextColor.GOLD));
        
        java.util.List<Component> lore = new java.util.ArrayList<>();
        String killerName = "不明";
        String weaponName = "素手 / 不明";
        
        if (killer != null) {
            if (killer instanceof Player p) {
                killerName = p.getName();
            } else if (killer instanceof Mob mob) {
                if (com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner.getController(mob.getUniqueId()) != null) {
                    killerName = "SCAV";
                } else {
                    killerName = mob.getName();
                }
            }
            
            if (killer.getEquipment() != null) {
                ItemStack weapon = killer.getEquipment().getItemInMainHand();
                if (weapon != null && weapon.getType() != Material.AIR) {
                    if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
                        weaponName = Component.text().append(weapon.getItemMeta().displayName()).toString(); // Simple fallback
                        // More robust way to get display name as string if using Adventure
                        weaponName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(weapon.getItemMeta().displayName());
                    } else {
                        weaponName = weapon.getType().name();
                    }
                }
            }
        }
        
        lore.add(Component.text("殺害されたプレイヤー: ", NamedTextColor.GRAY).append(Component.text(victim.getName(), NamedTextColor.WHITE)));
        lore.add(Component.text("殺害した者: ", NamedTextColor.GRAY).append(Component.text(killerName, NamedTextColor.RED)));
        lore.add(Component.text("使用武器: ", NamedTextColor.GRAY).append(Component.text(weaponName, NamedTextColor.YELLOW)));
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        lore.add(Component.text("日時: ", NamedTextColor.GRAY).append(Component.text(now.format(formatter), NamedTextColor.DARK_GRAY)));
        
        meta.lore(lore);
        dogTag.setItemMeta(meta);
        return dogTag;
    }

    private void finishSpawning(Mannequin mannequin, Inventory virtualInv, Location loc, long durationTicks) {
        try {
            String serialized = serializeInventory(virtualInv);
            mannequin.getPersistentDataContainer().set(PDCKeys.CORPSE_INVENTORY, PersistentDataType.STRING, serialized);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to serialize corpse inventory!");
        }

        UUID uuid = mannequin.getUniqueId();
        activeCorpseLocations.put(uuid, loc);

        // Schedule removal
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeCorpseLocations.remove(uuid);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            } else {
                if (loc.getWorld() != null) {
                    loc.getChunk().load();
                    org.bukkit.entity.Entity e = Bukkit.getEntity(uuid);
                    if (e != null) e.remove();
                }
            }
        }, durationTicks);
    }

    public static void updateMannequinAppearance(Mannequin mannequin, Inventory inventory) {
        if (mannequin.getEquipment() == null) return;
        mannequin.getEquipment().setHelmet(inventory.getItem(0));
        mannequin.getEquipment().setChestplate(inventory.getItem(1));
        mannequin.getEquipment().setLeggings(inventory.getItem(2));
        mannequin.getEquipment().setBoots(inventory.getItem(3));
        mannequin.getEquipment().setItemInMainHand(inventory.getItem(4));
        mannequin.getEquipment().setItemInOffHand(inventory.getItem(5));
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
        return com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil.serializeInventory(inventory);
    }

    public static Inventory deserializeInventory(String data, Component title) throws IOException, ClassNotFoundException {
        return com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil.deserializeInventory(data, title);
    }
}
