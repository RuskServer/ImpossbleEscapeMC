package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.file.*;
import java.util.*;

/** 実リソースの完全性、言語選択、装飾付き引数、失敗する再読み込みを確認する。 */
public final class MessageCatalogTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    public static void main(String[] args)throws Exception {
        MessageCatalog built=MessageCatalog.load(null,"ja_jp");
        check(built.definitions().size()>480,"all notifications registered");
        // 英語辞書を日本語と同じ範囲に保ち、引数を含め全テンプレートを実際に展開する。
        var loader = MessageCatalogTest.class.getClassLoader();
        com.google.gson.JsonObject english;
        com.google.gson.JsonObject japanese;
        com.google.gson.JsonObject argumentCounts;
        try (var enReader = new java.io.InputStreamReader(loader.getResourceAsStream("lang/en_us.json"), java.nio.charset.StandardCharsets.UTF_8);
             var jaReader = new java.io.InputStreamReader(loader.getResourceAsStream("lang/ja_jp.json"), java.nio.charset.StandardCharsets.UTF_8);
             var countsReader = new java.io.InputStreamReader(loader.getResourceAsStream("messages/arguments.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            english = com.google.gson.JsonParser.parseReader(enReader).getAsJsonObject();
            japanese = com.google.gson.JsonParser.parseReader(jaReader).getAsJsonObject();
            argumentCounts = com.google.gson.JsonParser.parseReader(countsReader).getAsJsonObject();
        }
        check(english.keySet().equals(japanese.keySet()), "English covers every Japanese key");
        var japaneseCharacters = java.util.regex.Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]");
        for (var entry : english.entrySet()) {
            check(!japaneseCharacters.matcher(entry.getValue().getAsString()).find(), "Untranslated English template: " + entry.getKey());
            Object[] values = new Object[argumentCounts.get(entry.getKey()).getAsInt()];
            Arrays.fill(values, Component.text("value"));
            Component rendered = MessageService.render(MessageText.of(entry.getKey(), values), Locale.US, built);
            check(!japaneseCharacters.matcher(plain(rendered)).find(), "Japanese fallback in English template: " + entry.getKey());
            check(!plain(rendered).contains("<arg:"), "Unexpanded argument: " + entry.getKey());
        }
        String key="iem.admin.messages.reload_failed";
        Component argument=Component.text("<red>Player 'x'",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/example"))
                .hoverEvent(HoverEvent.showText(Component.text("detail")));
        Component input=MessageText.of(key,argument).decoration(TextDecoration.BOLD,true);
        Component jp=MessageService.render(input,Locale.JAPAN,built),en=MessageService.render(input,Locale.US,built);
        check(plain(jp).contains("再読み込みに失敗"),"Japanese");check(plain(en).contains("Reload failed"),"English");
        check(plain(jp).contains("<red>Player 'x'"),"argument not parsed as markup");
        check(jp.toString().contains("/example")&&jp.toString().contains("detail"),"interactive argument preserved");
        check(input.equals(MessageText.of(key,argument).decoration(TextDecoration.BOLD,true)),"input immutable");
        check(plain(MessageService.render(input,Locale.FRANCE,built)).equals(plain(jp)),"unsupported locale falls back");
        Component vanilla=Component.translatable("block.minecraft.stone");
        check(MessageService.render(vanilla,Locale.JAPAN,built).equals(vanilla),"client vanilla translation preserved");
        Path dir=Path.of("local/message-api/test-lang-"+UUID.randomUUID());Files.createDirectories(dir);
        MessageCatalog.load(dir,"ja_jp");
        Files.writeString(dir.resolve("en_us.json"),"{\""+key+"\":\"Custom <arg:0>\"}");
        MessageCatalog edited=MessageCatalog.load(dir,"ja_jp");check(plain(MessageService.render(input,Locale.UK,edited)).startsWith("Custom"),"language family fallback");
        Files.writeString(dir.resolve("en_us.json"),"{\""+key+"\":\"Missing argument\"}");
        boolean rejected=false;try{MessageCatalog.load(dir,"ja_jp");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"missing argument rejected");check(plain(MessageService.render(input,Locale.US,edited)).startsWith("Custom"),"old dictionary remains usable");
        Files.writeString(dir.resolve("en_us.json"),"{\""+key+"\":\"<arg:0>\",\""+key+"\":\"<arg:0>\"}");
        rejected=false;try{MessageCatalog.load(dir,"ja_jp");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"duplicate JSON keys rejected");
        for(String invalid:List.of("<arg:0><arg:abc>","<arg:0><arg:-1>","<arg:0><arg>")) {
            Files.writeString(dir.resolve("en_us.json"),"{\""+key+"\":\""+invalid+"\"}");
            rejected=false;try{MessageCatalog.load(dir,"ja_jp");}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"invalid argument tag rejected: "+invalid);
        }
        System.out.println("MessageCatalogTest OK: real catalog, locale/fallback, rich arguments, reload validation");
    }
}
