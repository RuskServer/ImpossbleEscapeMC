package com.lunar_prototype.impossbleEscapeMC.api.message;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** 各モジュール共通の入口。送信元はここで取得し、主スレッドへ渡す前に保存する。 */
public final class Messages {
    private static volatile MessageService service;
    private Messages() {}
    public static void init(ImpossbleEscapeMC plugin) throws IOException {
        if(service!=null)throw new IllegalStateException("Messages already initialized");
        service=new MessageService(MessageCatalog.load(plugin.getDataFolder().toPath().resolve("lang"),
                plugin.getConfig().getString("messages.default-language","ja_jp")),new BukkitMessagePlatform(plugin),true);
        plugin.getServiceContainer().register(MessageService.class,service);
    }
    public static MessageService service(){MessageService s=service;if(s==null)throw new IllegalStateException("Messages not initialized");return s;}
    public static void shutdown(){MessageService s=service;service=null;if(s!=null)s.close();}
    static void installForTest(MessageService replacement){service=replacement;}
    public static CompletionStage<List<MessageResult>> chat(CommandSender recipient,String key,Component text){
        return service().send(recipient,key,MessageContent.text(text),MessageOrigin.capture());
    }
    public static CompletionStage<List<MessageResult>> chat(CommandSender recipient,String key,String text){return service().send(recipient,key,MessageContent.text(MessageText.legacy(text)),MessageOrigin.capture());}
    public static CompletionStage<List<MessageResult>> actionBar(CommandSender recipient,String key,Component text){return service().send(recipient,key,MessageContent.text(text),MessageOrigin.capture());}
    public static CompletionStage<List<MessageResult>> title(CommandSender recipient,String key,Title title){return service().send(recipient,key,MessageContent.title(title),MessageOrigin.capture());}
    public static CompletionStage<List<MessageResult>> group(Collection<? extends CommandSender> recipients,String key,Component text){return service().group(recipients,key,MessageContent.text(text),MessageOrigin.capture());}
    public static CompletionStage<List<MessageResult>> broadcast(String key,Component text){return service().broadcast(key,MessageContent.text(text),MessageOrigin.capture());}
}
