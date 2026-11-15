package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;
import java.util.ArrayList;
import java.util.List;

/** 下から上への描画順と、同じ層内の選択規則。状態やBukkitには書き込まない。 */
final class PostEffectStack {
    static final String NAMESPACE = "iem";
    private static Key key(String name) { return Key.key(NAMESPACE, name); }
    private static final Key DEBUG = key("control_debug"), FLASHLIGHT = key("flashlight"), GRADE = key("grade"),
            CONTROL = key("control"), MENU = key("menu_clear"), COVER = key("intro_0");

    record Layers(boolean debug, Key sky, Key flareLight, boolean flashlight, boolean graded, Key visor,
                  Integer dazzle, Key wound, Key playback, Key hit, boolean menuClear, Key probe, boolean cover) { }

    static List<Key> externalOnly(List<Key> current) {
        return current.stream().filter(key -> !NAMESPACE.equals(key.namespace())).toList();
    }
    static List<Key> compose(List<Key> current, Layers layer) {
        List<Key> effects = new ArrayList<>(externalOnly(current));
        if (layer.debug()) effects.add(DEBUG);
        add(effects, layer.sky());
        add(effects, layer.flareLight());
        if (layer.flashlight()) effects.add(FLASHLIGHT);
        // バイザーは画作りへ統合済み。画作りが無い時だけ数値の画素を隠す。
        effects.add(layer.graded() ? layer.visor() == null ? GRADE : key("grade_" + layer.visor().value()) : CONTROL);
        if (layer.graded() && layer.dazzle() != null) effects.add(key("dazzle_" + layer.dazzle()));
        add(effects, layer.wound());
        add(effects, layer.playback());
        add(effects, layer.hit());
        // メニューの印・診断・読み込み用の暗転はこの順で最上層に置く。
        if (layer.menuClear()) effects.add(MENU);
        add(effects, layer.probe());
        if (layer.cover()) effects.add(COVER);
        return List.copyOf(effects);
    }
    private static void add(List<Key> effects, Key effect) { if (effect != null) effects.add(effect); }
}
