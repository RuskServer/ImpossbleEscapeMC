package com.lunar_prototype.impossbleEscapeMC.api.message;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.translation.Translator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.*;

/** 不変の辞書スナップショット。再読み込みは検証完了後に丸ごと差し替える。 */
public final class MessageCatalog implements Translator {
    private static final String RES="messages/";
    private static final Pattern ARG=Pattern.compile("<arg:(\\d+)>");
    private static final Pattern ARG_TAG=Pattern.compile("(?<!\\\\)<arg(?=[:>\\s])[^>]*>", Pattern.CASE_INSENSITIVE);
    private final Map<String,MessageDefinition> definitions;
    private final Map<String,Integer> arguments;
    private final Map<String,Map<String,String>> languages;
    private final String defaultLanguage;
    private final MiniMessage mini=MiniMessage.miniMessage();

    private MessageCatalog(Map<String,MessageDefinition> definitions,Map<String,Integer> arguments,
                           Map<String,Map<String,String>> languages,String fallback) {
        this.definitions=Map.copyOf(definitions);this.arguments=Map.copyOf(arguments);this.languages=Map.copyOf(languages);
        this.defaultLanguage=normalize(fallback);
        if(!languages.containsKey(defaultLanguage))throw new IllegalArgumentException("Unknown default message language: "+fallback);
        for(var language:languages.entrySet())for(var entry:language.getValue().entrySet()) {
            Integer count=arguments.get(entry.getKey());
            if(count==null)throw new IllegalArgumentException("Unknown translation key: "+entry.getKey());
            Set<Integer> found=new HashSet<>();Matcher matcher=ARG.matcher(entry.getValue());
            Matcher tags=ARG_TAG.matcher(entry.getValue());
            while(tags.find())if(!ARG.matcher(tags.group()).matches())
                throw new IllegalArgumentException("Invalid argument tag in "+entry.getKey()+": "+tags.group());
            while(matcher.find())found.add(Integer.parseInt(matcher.group(1)));
            for(int i=0;i<count;i++)if(!found.contains(i))throw new IllegalArgumentException("Missing argument "+i+" in "+language.getKey()+":"+entry.getKey());
            if(found.stream().anyMatch(i->i>=count))throw new IllegalArgumentException("Unexpected argument in "+entry.getKey());
            // MiniMessageは既存の開きタグ/リセットを許容。引数の構造は上で厳密に検証する。
            Component[] empty=new Component[count];Arrays.fill(empty,Component.empty());
            deserialize(entry.getValue(),List.of(empty));
        }
        for(String key:arguments.keySet())if(!languages.get("ja_jp").containsKey(key))throw new IllegalArgumentException("Missing Japanese translation: "+key);
        for(String key:definitions.keySet())if(!arguments.containsKey(key))throw new IllegalArgumentException("Missing message template: "+key);
    }
    public static MessageCatalog load(Path directory,String fallback) throws IOException {
        Map<String,Map<String,String>> languages=new HashMap<>();
        for(String language:List.of("ja_jp","en_us")) {
            Map<String,String> built=readStrings(resource("lang/"+language+".json"));
            if(directory!=null) {
                Files.createDirectories(directory);
                Path path=directory.resolve(language+".json");
                if(!Files.exists(path))try(InputStream in=resource("lang/"+language+".json")){Files.copy(in,path);}
                try(InputStream in=Files.newInputStream(path)){built.putAll(readStrings(in));}
            }
            languages.put(language,Map.copyOf(built));
        }
        if(directory!=null)try(var files=Files.list(directory)) {
            for(Path path:files.filter(p->p.getFileName().toString().matches("[a-z]{2,3}(_[a-z0-9]{2,8})?\\.json")).toList()) {
                String language=path.getFileName().toString().replace(".json","");
                if(languages.containsKey(language))continue;
                try(InputStream in=Files.newInputStream(path)){languages.put(language,Map.copyOf(readStrings(in)));}
            }
        }
        Map<String,Integer> counts=new HashMap<>();
        try(InputStream in=resource(RES+"arguments.json")) {
            for(var e:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().entrySet())counts.put(e.getKey(),e.getValue().getAsInt());
        }
        Map<String,MessageDefinition> defs=new HashMap<>();
        try(InputStream in=resource(RES+"definitions.json")) {
            for(var e:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                JsonObject o=e.getValue().getAsJsonObject();
                defs.put(e.getKey(),new MessageDefinition(e.getKey(),MessageCategory.valueOf(o.get("category").getAsString()),MessageLevel.valueOf(o.get("level").getAsString()),MessageChannel.valueOf(o.get("channel").getAsString())));
            }
        }
        return new MessageCatalog(defs,counts,languages,fallback);
    }
    private static InputStream resource(String name) throws IOException {
        InputStream in=MessageCatalog.class.getClassLoader().getResourceAsStream(name);
        if(in==null)throw new IOException("Missing message resource: "+name);return in;
    }
    private static Map<String,String> readStrings(InputStream input) throws IOException {
        try(input;JsonReader reader=new JsonReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
            Map<String,String> out=new LinkedHashMap<>();reader.beginObject();
            while(reader.hasNext()) {
                String key=reader.nextName();if(out.containsKey(key))throw new IllegalArgumentException("Duplicate translation: "+key);
                if(reader.peek()!=com.google.gson.stream.JsonToken.STRING)throw new IllegalArgumentException("Translation must be a string: "+key);
                out.put(key,reader.nextString());
            }
            reader.endObject();if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing translation data");return out;
        }
    }
    private static String normalize(String language){return language.toLowerCase(Locale.ROOT).replace('-','_');}
    public Locale defaultLocale(){return Locale.forLanguageTag(defaultLanguage.replace('_','-'));}
    public MessageDefinition definition(String key){MessageDefinition d=definitions.get(key);if(d==null)throw new IllegalArgumentException("Unknown message: "+key);return d;}
    public Map<String,MessageDefinition> definitions(){return definitions;}
    public Set<String> keys(){return arguments.keySet();}
    private String template(String key,Locale locale) {
        List<String> choices=new ArrayList<>();String full=normalize(locale.toLanguageTag());choices.add(full);choices.add(locale.getLanguage());
        if(locale.getLanguage().equals("en"))choices.add("en_us");if(locale.getLanguage().equals("ja"))choices.add("ja_jp");
        choices.add(defaultLanguage);choices.add("ja_jp");
        for(String choice:choices){Map<String,String> map=languages.get(choice);if(map!=null&&map.containsKey(key))return map.get(key);}return null;
    }
    private Component deserialize(String template,List<Component> args) {
        return mini.deserialize(template,TagResolver.resolver("arg",(queue,context)->{
            String value=queue.popOr("Argument index is required").value();
            int index;try{index=Integer.parseInt(value);}catch(NumberFormatException e){throw context.newException("Invalid argument index",queue);}
            if(index<0||index>=args.size())throw context.newException("Argument index out of range",queue);
            return Tag.inserting(args.get(index));
        }));
    }
    @Override public Key name(){return Key.key("iem","messages");}
    @Override public MessageFormat translate(String key,Locale locale){return null;}
    @Override public boolean canTranslate(String key,Locale locale){return arguments.containsKey(key);}
    @Override public Component translate(TranslatableComponent input,Locale locale) {
        Integer count=arguments.get(input.key());if(count==null)return null;
        if(input.arguments().size()!=count)throw new IllegalArgumentException("Argument count mismatch: "+input.key());
        List<Component> args=input.arguments().stream().map(a->a.value() instanceof Component c?c:Component.text(String.valueOf(a.value()))).toList();
        Component result=deserialize(Objects.requireNonNull(template(input.key(),locale)),args);
        // 元のStyleは親に置く。翻訳文の明示色や引数のクリック・ホバーを上書きしない。
        return Component.empty().style(input.style()).append(result).append(input.children());
    }
}
