package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
/** 引数は本文として挿入する。プレイヤー入力をMiniMessageとして再解釈しない。 */
public final class MessageText {
    private MessageText() {}
    public static Component of(String key,Object... arguments) {
        Component[] parts=new Component[arguments.length];
        for(int i=0;i<parts.length;i++) parts[i]=arguments[i] instanceof ComponentLike c ? c.asComponent() : Component.text(String.valueOf(arguments[i]));
        return Component.translatable(key,parts);
    }
    public static Component payload(ComponentLike component){return component.asComponent();}
    public static Component payload(String text){return legacy(text);}
    public static Component legacy(String text) {return LegacyComponentSerializer.legacySection().deserialize(text);}
}
