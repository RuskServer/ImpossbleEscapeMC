package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import com.lunar_prototype.impossbleEscapeMC.loot.CorpseLootLayout;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Proxy;
import java.util.*;

/** サーバーを起動せず、実際のスロット割当とラグドールの適用判定をつないで検証する。 */
public final class CorpseLootLayoutTest {
    private static final class Stack extends ItemStack {
        private final String kind;private int amount;
        Stack(String kind,int amount){super();this.kind=kind;this.amount=amount;}
        @Override public Material getType(){return Material.IRON_INGOT;}
        @Override public int getAmount(){return amount;}
        @Override public boolean isEmpty(){return amount<=0;}
        @Override public void setAmount(int n){amount=n;}
        @Override public int getMaxStackSize(){return 64;}
        @Override public boolean hasItemMeta(){return false;}
        @Override public boolean isSimilar(ItemStack other){return other instanceof Stack s&&kind.equals(s.kind);}
        @Override public Stack clone(){return new Stack(kind,amount);}
    }
    private static Inventory inventory(ItemStack[] slots) {
        return (Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(proxy,method,args)->switch(method.getName()) {
            case "getSize"->slots.length;
            case "getMaxStackSize"->64;
            case "getItem"->slots[(int)args[0]];
            case "setItem"->{slots[(int)args[0]]=(ItemStack)args[1];yield null;}
            default->throw new UnsupportedOperationException(method.getName());
        });
    }
    private static boolean supported(Inventory inventory) throws Exception {
        var method=ScavRagdollModule.class.getDeclaredMethod("supported",Inventory.class);method.setAccessible(true);
        return (boolean)method.invoke(null,inventory);
    }
    public static void main(String[] args) throws Exception {
        // 旧addItemが空き装備欄へ予備弾を詰めた結果。装備ではない弾で対象判定が失敗する。
        ItemStack[] old=new ItemStack[27];old[4]=new Stack("gun",1);
        old[0]=new Stack("ammo",64);old[1]=new Stack("ammo",64);old[2]=new Stack("ammo",2);
        check(!supported(inventory(old)),"reproduce ammo mistaken for leg armor");
        ItemStack[] slots=new ItemStack[27];slots[4]=new Stack("gun",1);
        Stack ammunition=new Stack("ammo",130);
        check(CorpseLootLayout.addExtra(inventory(slots),List.of(ammunition)).isEmpty(),"ordinary reserve ammo fits");
        check(supported(inventory(slots)),"same unarmored SCAV now qualifies for ragdoll");
        check(slots[0]==null&&slots[1]==null&&slots[2]==null&&slots[3]==null&&slots[5]==null,"empty equipment slots remain empty");
        check(slots[6].getAmount()==64&&slots[7].getAmount()==64&&slots[8].getAmount()==2,"split ammo into loot slots");
        check(ammunition.getAmount()==130,"do not mutate caller stacks");
        ItemStack helmet=new Stack("helmet",1),chest=new Stack("chest",1);slots[0]=helmet;slots[1]=chest;
        CorpseLootLayout.addExtra(inventory(slots),List.of(new Stack("ammo",20)));
        check(slots[0]==helmet&&slots[1]==chest,"preserve real equipment for appearance sync");
        check(slots[8].getAmount()==22,"merge partial stacks only in loot slots");
        slots=new ItemStack[9];slots[0]=helmet;
        var overflow=CorpseLootLayout.addExtra(inventory(slots),List.of(new Stack("ammo",260)));
        check(slots[0]==helmet&&slots[1]==null&&slots[2]==null&&slots[3]==null&&slots[4]==null&&slots[5]==null,"full loot area never borrows equipment slots");
        check(overflow.size()==1&&overflow.getFirst().getAmount()==68,"return exact overflow for world drop without loss");
        check(slots[6].getAmount()+slots[7].getAmount()+slots[8].getAmount()+overflow.getFirst().getAmount()==260,"conserve ammo quantity");
        System.out.println("CorpseLootLayoutTest OK: old ragdoll rejection reproduced, reserved slots and ammo conservation verified");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
