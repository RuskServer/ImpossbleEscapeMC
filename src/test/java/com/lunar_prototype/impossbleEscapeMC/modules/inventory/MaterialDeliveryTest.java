package com.lunar_prototype.impossbleEscapeMC.modules.inventory;
import java.util.List;
import java.util.Map;
public final class MaterialDeliveryTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var stock=List.of(new MaterialDelivery.Stock<>("hand","ram",2),new MaterialDelivery.Stock<>("bag","ram",3),new MaterialDelivery.Stock<>("stashBag","ram",4),new MaterialDelivery.Stock<>("stash","drill",1));
        var plan=MaterialDelivery.plan(stock,Map.of("ram",7,"drill",1));
        check(plan.equals(Map.of("hand",2,"bag",3,"stashBag",2,"stash",1)),"cross-source exact consumption");
        check(MaterialDelivery.plan(stock,Map.of("ram",10))==null,"shortage refuses entire delivery");
        check(MaterialDelivery.plan(stock,Map.of("ram",2)).equals(Map.of("hand",2)),"earlier sources first");
        check(MaterialDelivery.plan(stock,Map.of()).isEmpty(),"no requirements no consumption");
        var duplicates=List.of(new MaterialDelivery.Stock<>("same","ram",2),new MaterialDelivery.Stock<>("same","ram",2));
        check(MaterialDelivery.plan(duplicates,Map.of("ram",3))==null,"duplicate source cannot supply twice");
        check(stock.get(0).amount()==2 && stock.get(2).amount()==4,"plan never mutates sources");
        System.out.println("PASS: shared material allocation, backpack/stash sources, shortage atomicity and duplicate protection");
    }
}
