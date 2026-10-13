package com.lunar_prototype.impossbleEscapeMC.modules.starter;

import com.lunar_prototype.impossbleEscapeMC.modules.stash.StashModule;

import java.util.Set;
import java.util.stream.Collectors;

/** 初期物資がレベル 1 のスタッシュの 1 ページ目に収まるか (弾は 64 個で 1 スタック、ほかの品物は 1 個で 1 スタック) */
public final class StarterKitTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Set<String> ammo = StarterKit.CONTENTS.stream().filter(StarterKit.Entry::gun).map(StarterKit.Entry::ammoId)
                .collect(Collectors.toSet());
        int slots = StarterKit.slotsNeeded(id -> ammo.contains(id) ? 64 : 1);
        int capacity = new StashModule().getRows(1, StarterKit.PAGE) * 9;
        check(slots == 16, "slots: " + slots);
        check(slots <= capacity, "starter kit (" + slots + ") fits the level 1 stash page (" + capacity + ")");
        // 予備の弾は銃に込める弾と同じ種類
        for (StarterKit.Entry entry : StarterKit.CONTENTS) {
            if (!entry.gun() && (entry.id().startsWith("545") || entry.id().startsWith("45acp"))) {
                check(ammo.contains(entry.id()), "spare ammo is loaded in a gun: " + entry.id());
            }
        }
        check(StarterKit.MONEY == 50_000, "money");
        System.out.println("StarterKitTest OK (" + slots + "/" + capacity + " slots)");
    }
}
