package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

/** 実際のStoreで、保存に失敗した後も本物の持ち物とページが変わっていないことを確認。 */
public final class HandInStoreTest {
    private static final class Stack extends ItemStack {
        private int amount;
        Stack(int amount){super();this.amount=amount;}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int n){amount=n;}
        @Override public Stack clone(){return new Stack(amount);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        ItemStack[] actual={new Stack(5)};
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
            case "getItem"->actual[(int)a[0]];
            case "setItem"->{actual[(int)a[0]]=(ItemStack)a[1];yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
            if(m.getName().equals("getInventory"))return inventory;
            throw new UnsupportedOperationException(m.getName());
        });
        PlayerData data=new PlayerData(UUID.randomUUID());data.setStashPage(0,"original");
        Inventory brokenPage=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(p,m,a)->{
            if(m.getName().equals("setItem"))return null;
            throw new IllegalStateException("injected serialization failure");
        });
        var source=new HandInPlaces.Source(HandInPlaces.Kind.INVENTORY,0,0,-1);
        var store=new HandInPlaces.Store(player,data,null,Map.of(0,brokenPage),true);
        ItemStack copy=store.get(source);copy.setAmount(3);store.set(source,copy);
        store.set(new HandInPlaces.Source(HandInPlaces.Kind.STASH,0,0,-1),null);
        check(actual[0].getAmount()==5,"staging cannot mutate live stack");
        try{store.commit();throw new AssertionError("expected save failure");}catch(IllegalStateException expected){}
        check(actual[0].getAmount()==5 && data.getStashPages().get(0).equals("original"),"save failure leaves all live sources unchanged");
        var good=new HandInPlaces.Store(player,data,null,Map.of(),true);
        ItemStack next=good.get(source);next.setAmount(3);good.set(source,next);good.commit();
        check(actual[0].getAmount()==3,"successful commit applies once");
        System.out.println("PASS: actual hand-in store staging, save-failure atomicity and successful commit");
    }
}
