package com.lunar_prototype.impossbleEscapeMC.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SerializationUtil {

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
        
        int size = dataInput.readInt();
        Inventory inventory = Bukkit.createInventory(null, size, title);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, (ItemStack) dataInput.readObject());
        }
        dataInput.close();
        return inventory;
    }
}
