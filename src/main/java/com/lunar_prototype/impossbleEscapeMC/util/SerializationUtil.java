package com.lunar_prototype.impossbleEscapeMC.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SerializationUtil {

    /**
     * インベントリを圧縮されたバイト配列にシリアライズします。
     * PDC (PersistentDataContainer) などで使用するのに最適です。
     */
    public static byte[] serializeInventoryToBytes(Inventory inventory) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(gzipStream)) {
            
            dataOutput.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                // コスト占有スロットやリグ制限スロットは保存しない
                if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(item) ||
                    com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule.isLockedSlotPlaceholder(item)) {
                    dataOutput.writeObject(null);
                } else {
                    dataOutput.writeObject(item);
                }
            }
        }
        return outputStream.toByteArray();
    }

    /**
     * 圧縮されたバイト配列からインベントリをデシリアライズします。
     */
    public static Inventory deserializeInventoryFromBytes(byte[] data, Component title) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             GZIPInputStream gzipStream = new GZIPInputStream(inputStream);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(gzipStream)) {
            
            int size = dataInput.readInt();
            Inventory inventory = Bukkit.createInventory(null, size, title);
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }
            return inventory;
        }
    }

    /**
     * インベントリをBase64形式の文字列にシリアライズします（圧縮あり）。
     * JSON (GSON) やデータベースで使用するのに適しています。
     */
    public static String serializeInventory(Inventory inventory) throws IOException {
        byte[] bytes = serializeInventoryToBytes(inventory);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64形式の文字列からインベントリをデシリアライズします。
     * 圧縮されたデータと、古い非圧縮データの両方をサポートします。
     */
    public static Inventory deserializeInventory(String data, Component title) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) {
            return Bukkit.createInventory(null, 9, title);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            // 旧来の Base64Coder 形式（改行あり）を試みる
            bytes = org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder.decodeLines(data);
        }

        if (isGZipped(bytes)) {
            return deserializeInventoryFromBytes(bytes, title);
        } else {
            // 旧来の非圧縮形式
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                
                int size = dataInput.readInt();
                Inventory inventory = Bukkit.createInventory(null, size, title);
                for (int i = 0; i < size; i++) {
                    inventory.setItem(i, (ItemStack) dataInput.readObject());
                }
                return inventory;
            }
        }
    }

    /**
     * ItemStackをBase64形式の文字列にシリアライズします（圧縮あり）。
     */
    public static String serializeItemStack(ItemStack item) throws IOException {
        if (item == null) return null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(gzipStream)) {
            dataOutput.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64形式の文字列からItemStackをデシリアライズします。
     */
    public static ItemStack deserializeItemStack(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return null;
        byte[] bytes = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             GZIPInputStream gzipStream = new GZIPInputStream(inputStream);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(gzipStream)) {
            return (ItemStack) dataInput.readObject();
        }
    }

    private static boolean isGZipped(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b;
    }
}
