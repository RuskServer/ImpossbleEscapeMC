package com.lunar_prototype.impossbleEscapeMC.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.*;

/** 保存済みスキンの優先、未スポーン・NPC番号0・キャッシュ更新と欠落を確認する。 */
public final class TraderPortraitsTest {
    public static final class SavedSkin extends Trait {
        String texture, signature;
        SavedSkin(String texture, String signature) { super("skintrait"); this.texture=texture;this.signature=signature; }
        public String getTexture() { return texture; }
        public String getSignature() { return signature; }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static NPC npc(List<Trait> traits,Player entity,boolean forbidEntityRead) {
        return proxy(NPC.class,(p,m,a)->switch(m.getName()) {
            case "getTraits"->traits;
            case "getEntity"->{check(!forbidEntityRead,"saved texture must not require a spawned entity");yield entity;}
            default->throw new AssertionError("unexpected NPC operation (fetch/spawn/mutation): "+m.getName());
        });
    }
    public static void main(String[] args) {
        SavedSkin trait=new SavedSkin("saved-texture-A","signed-A");
        NPC unspawned=npc(List.of(trait),null,true);
        ProfileProperty cached=TraderPortraits.CitizensSkin.texture(unspawned);
        check(cached.getValue().equals("saved-texture-A")&&cached.getSignature().equals("signed-A"),"unspawned saved skin preserves signature");
        trait.texture="saved-texture-B";trait.signature=null;
        check(TraderPortraits.CitizensSkin.texture(unspawned).getValue().equals("saved-texture-B"),"reopen reads updated NPC skin, not a permanent cache");
        check(!TraderPortraits.CitizensSkin.texture(unspawned).isSigned(),"unsigned saved skin supported");
        var property=new ProfileProperty("textures","entity-texture");
        PlayerProfile profile=proxy(PlayerProfile.class,(p,m,a)->{
            if(m.getName().equals("getProperties"))return Set.of(new ProfileProperty("other","ignored"),property);
            throw new AssertionError("unexpected profile operation (network completion): "+m.getName());
        });
        Player player=proxy(Player.class,(p,m,a)->{
            if(m.getName().equals("getPlayerProfile"))return profile;
            throw new AssertionError(m.getName());
        });
        trait.texture="";
        check(TraderPortraits.CitizensSkin.texture(npc(List.of(trait),player,false)).equals(property),"empty trait falls back to spawned profile");
        check(TraderPortraits.CitizensSkin.texture(npc(List.of(),null,false))==null,"missing cache and entity safely return no portrait");
        trait.texture="saved-texture-C";
        check(TraderPortraits.CitizensSkin.texture(npc(List.of(trait),player,true)).getValue().equals(trait.texture),"saved configured skin wins over old entity skin");
        NPCRegistry registry=proxy(NPCRegistry.class,(p,m,a)->{
            if(m.getName().equals("getById"))return (int)a[0]==0?unspawned:null;
            throw new AssertionError(m.getName());
        });
        CitizensAPI.setImplementation(proxy(CitizensPlugin.class,(p,m,a)->{
            if(m.getName().equals("getNPCRegistry"))return registry;
            throw new AssertionError(m.getName());
        }));
        try {
            check(TraderPortraits.CitizensSkin.byId(0)!=null,"Broker NPC ID0 must not be treated as unconfigured");
            check(TraderPortraits.CitizensSkin.byId(99)==null,"unregistered NPC ID is safe");
        } finally { CitizensAPI.setImplementation(null); }
        var trader=new TraderDefinition("kovacs",null,TraderType.BUY,List.of(),1,false,false);
        trader.displayName="Kovacs (Buy Only)";
        check(TraderSelectionGUI.name(trader).equals("Kovacs"),"legacy operation suffix is not shown as a contact name");
        trader.displayName=null;
        check(TraderSelectionGUI.name(trader).equals("kovacs"),"missing display name uses stable trader ID");
        System.out.println("TraderPortraitsTest passed: saved/live skin priority, signatures, updates, NPC0 and missing NPCs without fetch/spawn");
    }
}
