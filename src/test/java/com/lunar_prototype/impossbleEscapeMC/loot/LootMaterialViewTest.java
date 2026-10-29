package com.lunar_prototype.impossbleEscapeMC.loot;
import org.bukkit.inventory.ItemStack;
public final class LootMaterialViewTest {
    private static final class Stack extends ItemStack {
        private int amount;Stack(int amount){super();this.amount=amount;}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int n){amount=n;}
        @Override public Stack clone(){return new Stack(amount);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        ItemStack[] visible=new ItemStack[36],shadow=new ItemStack[27];
        visible[0]=new Stack(3);visible[8]=new Stack(99);visible[9]=new Stack(60);shadow[0]=new Stack(4);
        ItemStack toggle=new Stack(1);
        var actual=LootBackpackOverlayListener.materialStorage(visible,shadow,toggle,true);
        check(actual[0].getAmount()==3&&actual[8].getAmount()==1&&actual[9].getAmount()==4,"hidden real rows replace bag display and toggle");
        actual[0].setAmount(0);actual[9].setAmount(0);
        check(visible[0].getAmount()==3&&visible[9].getAmount()==60&&shadow[0].getAmount()==4,"count view cannot change live items");
        actual=LootBackpackOverlayListener.materialStorage(visible,shadow,toggle,false);
        check(actual[9].getAmount()==60,"normal view uses latest real player rows");
        System.out.println("PASS: loot material count view restores real rows without duplicate bag contents or live mutation");
    }
}
