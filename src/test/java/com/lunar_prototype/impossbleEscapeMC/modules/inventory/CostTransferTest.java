package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Proxy;
import java.util.List;

/** サーバーなしで実際の転送関数を実行し、拒否時の元スタックと未使用スロットを確認。 */
public final class CostTransferTest {
    private static final class Stack extends ItemStack {
        private final String kind; private int amount;
        Stack(String kind,int amount){super();this.kind=kind;this.amount=amount;}
        @Override public Material getType(){return Material.IRON_INGOT;}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int n){amount=n;}
        @Override public int getMaxStackSize(){return 64;}
        @Override public boolean hasItemMeta(){return false;}
        @Override public boolean isSimilar(ItemStack item){return item instanceof Stack s&&kind.equals(s.kind);}
        @Override public Stack clone(){return new Stack(kind,amount);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        // Paper 26.3のMaterial.isAirはサーバーのレジストリを使う。非ブロック素材だけを試験用に固定する。
        var blockType=Material.class.getDeclaredField("blockType");blockType.setAccessible(true);
        Object previous=blockType.get(Material.IRON_INGOT);
        blockType.set(Material.IRON_INGOT,(java.util.function.Supplier<org.bukkit.block.BlockType>)()->null);
        try {
        ItemStack[] slots={new Stack("ram",60),null,null};
        Inventory inv=(Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(p,m,a)->switch(m.getName()) {
            case "getMaxStackSize"->64;
            case "getItem"->slots[(int)a[0]];
            case "setItem"->{slots[(int)a[0]]=(ItemStack)a[1];yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
        Stack source=new Stack("ram",10);
        ItemStack rest=CostSlotManager.insert(inv,List.of(0,1),1,source);
        check(slots[0].getAmount()==64&&slots[1]==null&&rest.getAmount()==6,"partial stack only at cost limit");
        check(source.getAmount()==10,"caller source unchanged until transfer succeeds");
        rest=CostSlotManager.insert(inv,List.of(0,1),2,rest);
        check(rest==null&&slots[1].getAmount()==6,"new stack fits exact capacity");
        check(slots[2]==null,"unlisted map/locked slot never receives items");
        ItemStack before=slots[0];
        rest=CostSlotManager.insert(inv,List.of(0,1),2,new Stack("drill",1));
        check(rest.getAmount()==1&&slots[0]==before,"full cost refusal does not evict any item");
        System.out.println("PASS: actual cost-aware inventory transfer, partial/source retention and locked-slot exclusion");
        } finally {blockType.set(Material.IRON_INGOT,previous);}
    }
}
