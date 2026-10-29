package com.lunar_prototype.impossbleEscapeMC.loot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** 0〜5は見た目と連動する装備専用。追加の弾・ルート品で空き装備欄を埋めない。 */
public final class CorpseLootLayout {
    public static final int FIRST_EXTRA_SLOT = 6;
    public static List<ItemStack> addExtra(Inventory inventory,List<ItemStack> items) {
        List<ItemStack> overflow=new ArrayList<>();
        for(ItemStack item:items) {
            if(item==null||item.isEmpty()||item.getAmount()<=0)continue;
            ItemStack remaining=item.clone();
            for(int slot=FIRST_EXTRA_SLOT;slot<inventory.getSize()&&remaining.getAmount()>0;slot++) {
                ItemStack existing=inventory.getItem(slot);
                if(existing==null||existing.isEmpty())continue;
                if(!existing.isSimilar(remaining))continue;
                int moved=Math.min(remaining.getAmount(),Math.max(0,Math.min(existing.getMaxStackSize(),inventory.getMaxStackSize())-existing.getAmount()));
                if(moved<=0)continue;
                ItemStack merged=existing.clone();merged.setAmount(existing.getAmount()+moved);inventory.setItem(slot,merged);
                remaining.setAmount(remaining.getAmount()-moved);
            }
            for(int slot=FIRST_EXTRA_SLOT;slot<inventory.getSize()&&remaining.getAmount()>0;slot++) {
                ItemStack existing=inventory.getItem(slot);
                if(existing!=null&&!existing.isEmpty())continue;
                int moved=Math.min(remaining.getAmount(),Math.min(remaining.getMaxStackSize(),inventory.getMaxStackSize()));
                ItemStack placed=remaining.clone();placed.setAmount(moved);inventory.setItem(slot,placed);
                remaining.setAmount(remaining.getAmount()-moved);
            }
            if(remaining.getAmount()>0)overflow.add(remaining);
        }
        return overflow;
    }
    private CorpseLootLayout() {}
}
