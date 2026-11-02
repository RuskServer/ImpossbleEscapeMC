package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.definition.*;
import com.lunar_prototype.impossbleEscapeMC.item.factory.HelmetVisors;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentType;
import org.bukkit.Material;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.lang.reflect.*;
import java.util.*;

/** 実際の防護選択・消耗を検証する。Bukkitのアイテム/PDCはメモリ内の代用品。 */
public final class VisorArmorTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static ItemMeta meta(Map<NamespacedKey,Object> values) {
        var data=proxy(PersistentDataContainer.class,(p,m,a)->switch(m.getName()) {
            case "get"->values.get(a[0]);
            case "getOrDefault"->values.getOrDefault(a[0],a[2]);
            case "set"->{values.put((NamespacedKey)a[0],a[2]);yield null;}
            case "remove"->{values.remove(a[0]);yield null;}
            default->throw new AssertionError(m.getName());
        });
        return proxy(ItemMeta.class,(p,m,a)->{
            if(m.getName().equals("getPersistentDataContainer"))return data;
            throw new AssertionError(m.getName());
        });
    }
    private static final class Stack extends ItemStack {
        Map<NamespacedKey,Object> data=new HashMap<>();
        Map<Object,Object> components=new HashMap<>();
        Stack(String id) {data.put(PDCKeys.ITEM_ID,id);}
        @Override public Material getType() {return Material.DIAMOND;}
        @Override public boolean hasItemMeta() {return true;}
        @Override public ItemMeta getItemMeta() {return meta(new HashMap<>(data));}
        @Override public boolean setItemMeta(ItemMeta meta) {
            var pdc=meta.getPersistentDataContainer();
            for(var key:List.of(PDCKeys.ITEM_ID,PDCKeys.HELMET_VISOR)) {
                String v=pdc.get(key,PDCKeys.STRING);if(v!=null)data.put(key,v);else data.remove(key);
            }
            for(var key:List.of(PDCKeys.ARMOR_CLASS,PDCKeys.DURABILITY,PDCKeys.VISOR_DURABILITY)) {
                Integer v=pdc.get(key,PDCKeys.INTEGER);if(v!=null)data.put(key,v);else data.remove(key);
            }
            return true;
        }
        @SuppressWarnings("unchecked") @Override public <T>T getData(DataComponentType.Valued<T> type) {return (T)components.get(type);}
        @Override public <T>void setData(DataComponentType.Valued<T> type,T value) {components.put(type,value);}
        @Override public Stack clone() {var copy=new Stack((String)data.get(PDCKeys.ITEM_ID));copy.data.putAll(data);copy.components.putAll(components);return copy;}
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        // DIAMONDはブロックでないため、そのレジストリ参照だけ空として提供する。
        Field registryField=Class.forName("io.papermc.paper.registry.RegistryAccessHolder").getDeclaredField("INSTANCE");
        Object registryBase=unsafe.staticFieldBase(registryField);long registryOffset=unsafe.staticFieldOffset(registryField);
        Object previousRegistry=unsafe.getObject(registryBase,registryOffset);
        Object[] registry={null};
        RegistryAccess access=proxy(RegistryAccess.class,(p,m,a)->{
            if(!m.getName().equals("getRegistry"))throw new AssertionError(m.getName());
            if(registry[0]==null)registry[0]=proxy(Registry.class,(q,n,b)->{
                if(n.getName().equals("get"))return null;
                if(n.getName().equals("getOrThrow"))return Proxy.newProxyInstance(DataComponentType.class.getClassLoader(),
                        new Class<?>[]{DataComponentType.Valued.class,DataComponentType.NonValued.class},(t,v,c)->switch(v.getName()) {
                            case "hashCode"->System.identityHashCode(t);
                            case "equals"->t==c[0];
                            default->throw new AssertionError(v.getName());
                        });
                throw new AssertionError(n.getName());
            });
            return registry[0];
        });
        unsafe.putObject(registryBase,registryOffset,Optional.of(access));
        Object plugin=unsafe.allocateInstance(ImpossbleEscapeMC.class);
        Field metadata=JavaPlugin.class.getDeclaredField("pluginMeta");metadata.setAccessible(true);
        metadata.set(plugin,proxy(PluginMeta.class,(p,m,a)->{if(m.getName().equals("getName"))return "IEM";if(m.getName().equals("namespace"))return "iem";throw new AssertionError(m.getName());}));
        Field instance=ImpossbleEscapeMC.class.getDeclaredField("instance");instance.setAccessible(true);Object before=instance.get(null);instance.set(null,plugin);
        Field catalog=ItemRegistry.class.getDeclaredField("ITEM_MAP");catalog.setAccessible(true);
        Map<String,ItemDefinition> items=(Map<String,ItemDefinition>)catalog.get(null);
        Map<String,ItemDefinition> saved=new HashMap<>(items);
        try {
            for(var def:ItemCatalog.create())items.put(def.id,def);
            var visor=items.get("FAST-MT_visor");
            check(visor.visorStats.armorClass==2&&visor.visorStats.maxDurability==300,"catalog gives separate class and durability");
            check(items.get("PVS-14").visorStats.armorClass==0,"night vision has no face protection");
            check(items.get("Altyn").armorStats.armorClass==4&&items.get("Altyn").armorStats.coverage.contains(com.lunar_prototype.impossbleEscapeMC.item.stats.ArmorStats.ProtectedZone.FACE),"integral Altyn class four coverage is retained");
            Stack[] helmet={new Stack("FAST-MT")};
            helmet[0].data.put(PDCKeys.ARMOR_CLASS,3);helmet[0].data.put(PDCKeys.DURABILITY,400);
            EntityEquipment equipment=proxy(EntityEquipment.class,(p,m,a)->{
                if(m.getName().equals("getItem"))return a[0]==EquipmentSlot.HEAD?helmet[0]:null;
                if(m.getName().equals("setItem")) {check(a[0]==EquipmentSlot.HEAD,"wear writes the selected slot only");helmet[0]=(Stack)a[1];return null;}
                throw new AssertionError(m.getName());
            });
            LivingEntity victim=proxy(LivingEntity.class,(p,m,a)->{
                if(m.getName().equals("getEquipment"))return equipment;
                throw new AssertionError(m.getName());
            });
            check(BulletDamageModel.armorPiece(victim,"face")==null,"bare FAST-MT does not protect face");
            helmet[0].data.put(PDCKeys.HELMET_VISOR,"PVS-14");
            check(BulletDamageModel.armorPiece(victim,"face")==null,"PVS cannot inherit helmet class on face");
            helmet[0].data.put(PDCKeys.HELMET_VISOR,"FAST-MT_visor");
            var protection=BulletDamageModel.armorPiece(victim,"face");
            check(protection!=null&&protection.visor()&&protection.armorClass()==2,"legacy attached visor defaults to full class two, without adding class three shell");
            check(HelmetVisors.durability(helmet[0],visor)==300,"old attached visor starts full");
            Method wear=DatapackBulletHitBridge.class.getDeclaredMethod("wearArmor",LivingEntity.class,BulletDamageModel.ArmorPiece.class,int.class);
            wear.setAccessible(true);wear.invoke(null,victim,protection,3);
            check(HelmetVisors.durability(helmet[0],visor)==297&&(int)helmet[0].data.get(PDCKeys.DURABILITY)==400,"face hit wears visor only");
            var head=BulletDamageModel.armorPiece(victim,"head");
            check(head!=null&&!head.visor()&&head.armorClass()==3,"head uses the shell independently");
            wear.invoke(null,victim,head,3);
            check((int)helmet[0].data.get(PDCKeys.DURABILITY)==397&&HelmetVisors.durability(helmet[0],visor)==297,"head hit wears shell only");
            check(HelmetVisors.durability(new Stack("FAST-MT_visor"),visor)==300,"old standalone visor starts full despite old zero general durability");
            helmet[0]=(Stack)HelmetVisors.wear(helmet[0],1000,false);
            check(HelmetVisors.durability(helmet[0],visor)==0&&BulletDamageModel.armorPiece(victim,"face")==null,"broken visor gives no protection or legacy full reset");
            check(HelmetVisors.wear(helmet[0],3,false)==null,"broken visor stays broken");
            helmet[0]=new Stack("Altyn");helmet[0].data.put(PDCKeys.ARMOR_CLASS,4);helmet[0].data.put(PDCKeys.DURABILITY,600);
            var altynFace=BulletDamageModel.armorPiece(victim,"face");
            check(altynFace!=null&&!altynFace.visor()&&altynFace.armorClass()==4,"integral face shield uses Altyn shell class four");
            wear.invoke(null,victim,altynFace,3);
            check((int)helmet[0].data.get(PDCKeys.DURABILITY)==597,"Altyn face hit consumes shared integral durability");
            check(BulletDamageModel.damageForHit(10,"face",false,2)==0,"blocked face shot uses normal armor damage");
            check(Math.abs(BulletDamageModel.damageForHit(10,"face",true,2)-9.6)<1e-9,"penetrated face shot uses head multiplier and class reduction");
        } finally {items.clear();items.putAll(saved);instance.set(null,before);unsafe.putObject(registryBase,registryOffset,previousRegistry);}
        System.out.println("VisorArmorTest OK: FAST/PVS/Altyn catalog, real face armor selection, legacy durability, independent wear, broken protection and common damage");
    }
}
