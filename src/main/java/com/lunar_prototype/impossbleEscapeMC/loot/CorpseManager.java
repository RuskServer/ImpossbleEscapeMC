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
import java.util.ArrayList;
import java.util.List;

public class CorpseManager {
    private final ImpossbleEscapeMC plugin;
    private final List<Mannequin> activeCorpses = new ArrayList<>();

    public CorpseManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public void spawnCorpse(LivingEntity victim) {
        Location loc = victim.getLocation();
        Mannequin mannequin = (Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        
        mannequin.setCustomNameVisible(false);
        mannequin.customName(Component.text("SCAV の死体", NamedTextColor.GRAY));

        mannequin.setProfile(ResolvableProfile.resolvableProfile().skinPatch(skinPatchBuilder -> skinPatchBuilder.model(PlayerTextures.SkinModel.CLASSIC).body(Key.key("minecraft","scav"))).build());
        
        // Posing (Simple "lying down" look)
        // Mannequin poses are rotations in EulerAngle
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

        // Create virtual inventory from drops (or equipment + extras)
        Inventory virtualInv = Bukkit.createInventory(null, 27, Component.text("死体漁り"));
        if (victim.getEquipment() != null) {
            // Add equipment to inventory so they can be looted
            virtualInv.addItem(victim.getEquipment().getHelmet());
            virtualInv.addItem(victim.getEquipment().getChestplate());
            virtualInv.addItem(victim.getEquipment().getLeggings());
            virtualInv.addItem(victim.getEquipment().getBoots());
            virtualInv.addItem(victim.getEquipment().getItemInMainHand());
            virtualInv.addItem(victim.getEquipment().getItemInOffHand());
        }
        
        // Save serialized inventory to PDC
        try {
            String serialized = serializeInventory(virtualInv);
            mannequin.getPersistentDataContainer().set(PDCKeys.CORPSE_INVENTORY, PersistentDataType.STRING, serialized);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to serialize corpse inventory!");
        }

        activeCorpses.add(mannequin);

        // Schedule removal (90 seconds = 1800 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mannequin.isValid()) {
                mannequin.remove();
                activeCorpses.remove(mannequin);
            }
        }, 1800L);
    }

    public void cleanup() {
        for (Mannequin m : activeCorpses) {
            if (m.isValid()) m.remove();
        }
        activeCorpses.clear();
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
