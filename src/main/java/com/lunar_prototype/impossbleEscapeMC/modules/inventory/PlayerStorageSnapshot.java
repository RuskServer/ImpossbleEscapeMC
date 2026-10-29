package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;

/** 同一メインスレッド処理内の交換・建築失敗時に、素材を元の場所と状態へ戻す。 */
public final class PlayerStorageSnapshot {
    private final ItemStack[] contents;
    private final Map<Integer,String> stash;
    private final Inventory openBag;
    private final ItemStack[] openContents;
    private PlayerStorageSnapshot(Player player, PlayerData data) {
        contents = cloneItems(player.getInventory().getContents());
        stash = new HashMap<>(data.getStashPages());
        Inventory top=player.getOpenInventory().getTopInventory();
        openBag=top.getHolder() instanceof BackpackModule.BackpackInventoryHolder ? top : null;
        openContents=openBag==null?null:cloneItems(openBag.getContents());
    }
    public static PlayerStorageSnapshot capture(Player player, PlayerData data) { return new PlayerStorageSnapshot(player,data); }
    public void restore(Player player, PlayerData data) {
        player.getInventory().setContents(cloneItems(contents));
        data.getStashPages().clear();data.getStashPages().putAll(stash);
        if(openBag!=null && player.getOpenInventory().getTopInventory()==openBag) openBag.setContents(cloneItems(openContents));
        data.setDirty(true);
    }
    public static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy=items.clone();
        for(int i=0;i<copy.length;i++) if(copy[i]!=null)copy[i]=copy[i].clone();
        return copy;
    }
}
