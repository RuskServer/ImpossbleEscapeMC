package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import java.io.File;
import java.nio.file.Files;

/** 隠れ家の建築データ: 版の上がり方、続いているレベルの数え方、ブロックの比べ方 (座標を除く) */
public final class HideoutLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("hideout-layout").toFile();
        HideoutLayout layout = new HideoutLayout(dir);
        check(layout.version() == 0 && layout.base() == null, "empty");

        HideoutLayout.Base base = new HideoutLayout.Base();
        base.sizeX = 20;
        base.sizeY = 8;
        base.sizeZ = 16;
        base.spawnX = 3.5;
        base.entities.add(new HideoutLayout.EntityEntry("base_0", "{id:\"minecraft:armor_stand\"}", 1, 0, 2, 90f, 0f));
        layout.saveBase(base);
        check(layout.version() == 1, "base save bumps version");
        HideoutLayout.Base read = layout.base();
        check(read.sizeX == 20 && read.spawnX == 3.5 && read.entities.size() == 1 && "base_0".equals(read.entities.getFirst().key()), "base round trip");

        // 続いているレベルだけ数える (Lv.3 だけあっても Lv.2 が無ければ 1)
        layout.saveLayer(HideoutFacility.WORKBENCH, 1, new HideoutLayout.Layer());
        layout.saveLayer(HideoutFacility.WORKBENCH, 3, new HideoutLayout.Layer());
        check(layout.highestLevel(HideoutFacility.WORKBENCH) == 1, "gap stops the count");
        layout.saveLayer(HideoutFacility.WORKBENCH, 2, new HideoutLayout.Layer());
        check(layout.highestLevel(HideoutFacility.WORKBENCH) == 3, "contiguous levels");
        check(layout.version() == 4, "each layer save bumps version: " + layout.version());
        // 最大レベルより上は数えない
        layout.saveLayer(HideoutFacility.AMMO, 1, new HideoutLayout.Layer());
        layout.saveLayer(HideoutFacility.AMMO, 2, new HideoutLayout.Layer());
        check(layout.highestLevel(HideoutFacility.AMMO) == 1, "capped at the facility max");
        // 建物 (ベースの建築改良) の最大レベルは作った差分の数
        check(HideoutFacility.BASE.maxLevel(layout) == 0, "no base upgrades yet");
        layout.saveLayer(HideoutFacility.BASE, 1, new HideoutLayout.Layer());
        check(HideoutFacility.BASE.maxLevel(layout) == 1 && HideoutFacility.WORKBENCH.maxLevel(layout) == 4, "max levels");

        // ブロックの比べ方: いちばん外側の x, y, z だけを除く
        String chest = "minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:stone\",count:1,components:{x:1}}],x:10,y:64,z:-3,id:\"minecraft:chest\",CustomName:'{\"text\":\"a,x:1\"}'}";
        String moved = "minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:stone\",count:1,components:{x:1}}],x:522,y:70,z:9,id:\"minecraft:chest\",CustomName:'{\"text\":\"a,x:1\"}'}";
        check(SnbtPosition.strip(chest).equals(SnbtPosition.strip(moved)), "position removed: " + SnbtPosition.strip(chest));
        check(SnbtPosition.strip(chest).contains("components:{x:1}") && SnbtPosition.strip(chest).contains("a,x:1"), "nested and quoted kept");
        check(!SnbtPosition.strip(chest).equals(SnbtPosition.strip(chest.replace("count:1", "count:2"))), "content still compared");
        check(SnbtPosition.strip("minecraft:stone").equals("minecraft:stone"), "no nbt");
        System.out.println("HideoutLayoutTest OK");
    }
}
