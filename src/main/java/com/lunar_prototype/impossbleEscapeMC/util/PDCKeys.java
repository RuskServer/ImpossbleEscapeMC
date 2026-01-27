package com.lunar_prototype.impossbleEscapeMC.util;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;


public class PDCKeys {
    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Integer, Integer> INTEGER = PersistentDataType.INTEGER;
    public static final PersistentDataType<Double, Double> DOUBLE = PersistentDataType.DOUBLE;


    public static final NamespacedKey ITEM_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "item_id");
    public static final NamespacedKey DURABILITY = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "durability");
    public static final NamespacedKey AMMO = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "ammo");;
    public static final NamespacedKey CURRENT_AMMO_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "current_ammo_id");


    public static NamespacedKey affix(String stat) {
        return new NamespacedKey(ImpossbleEscapeMC.getInstance(), "affix_" + stat);
    }
}