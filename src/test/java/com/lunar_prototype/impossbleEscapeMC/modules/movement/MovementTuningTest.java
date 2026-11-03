package com.lunar_prototype.impossbleEscapeMC.modules.movement;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import java.lang.reflect.Proxy;
import java.util.*;

/** 既存の重量・疲労・ダッシュ補正への乗算、再適用、解除を実際の共通メソッドで確認する。 */
public final class MovementTuningTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static void near(double a,double b) {check(Math.abs(a-b)<1e-9,a+" != "+b);}
    private static double value(Map<NamespacedKey,AttributeModifier> modifiers) {
        double base=.1,scalar=1,total=1;
        for(var modifier:modifiers.values())switch(modifier.getOperation()) {
            case ADD_NUMBER->base+=modifier.getAmount();
            case ADD_SCALAR->scalar+=modifier.getAmount();
            case MULTIPLY_SCALAR_1->total*=1+modifier.getAmount();
        }
        return base*scalar*total;
    }
    public static void main(String[] args) {
        Map<NamespacedKey,AttributeModifier> modifiers=new HashMap<>();
        var weight=new NamespacedKey("test","weight");var fatigue=new NamespacedKey("test","fatigue");var sprint=new NamespacedKey("test","sprint");
        modifiers.put(weight,new AttributeModifier(weight,-.3,AttributeModifier.Operation.ADD_SCALAR));
        modifiers.put(fatigue,new AttributeModifier(fatigue,-.2,AttributeModifier.Operation.ADD_SCALAR));
        modifiers.put(sprint,new AttributeModifier(sprint,.3,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        int[] writes={0};
        AttributeInstance attribute=(AttributeInstance)Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),new Class<?>[]{AttributeInstance.class},(p,m,a)->switch(m.getName()) {
            case "getModifier"->modifiers.get(a[0]);
            case "removeModifier"->{modifiers.remove(a[0]);writes[0]++;yield null;}
            case "addModifier"->{var modifier=(AttributeModifier)a[0];modifiers.put(modifier.getKey(),modifier);writes[0]++;yield null;}
            default->throw new AssertionError("base value or unrelated attribute access: "+m.getName());
        });
        double before=value(modifiers);
        MovementTuning.applyTo(attribute);
        near(value(modifiers),before*MovementTuning.MULTIPLIER);
        check(modifiers.size()==4,"one global modifier, without changing existing ones");
        int once=writes[0];MovementTuning.applyTo(attribute);
        check(writes[0]==once&&modifiers.size()==4,"join/respawn/reapply cannot stack or rewrite the same modifier");
        MovementTuning.removeFrom(attribute);
        near(value(modifiers),before);check(modifiers.size()==3,"disable/quit only removes own correction");
        modifiers.clear();MovementTuning.applyTo(attribute);
        near(value(modifiers),MovementTuning.scale(.1));
        near(MovementTuning.scale(.15),.15*MovementTuning.MULTIPLIER);
        MovementTuning.applyTo(null);MovementTuning.removeFrom(null);
        System.out.println("MovementTuningTest OK: common ratio, compound weight/fatigue/sprint, idempotence and scoped cleanup");
    }
}
