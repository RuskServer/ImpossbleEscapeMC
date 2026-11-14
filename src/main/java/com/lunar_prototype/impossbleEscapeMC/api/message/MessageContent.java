package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.util.Objects;
/** 翻訳前・翻訳後で同じ形を使い、タイトルの時間指定も保持する。 */
public record MessageContent(Component text, Component subtitle, Title.Times times) {
    public MessageContent { Objects.requireNonNull(text);Objects.requireNonNull(subtitle); }
    public static MessageContent text(Component text) {return new MessageContent(text,Component.empty(),null);}
    public static MessageContent title(Title title) {return new MessageContent(title.title(),title.subtitle(),title.times());}
}
