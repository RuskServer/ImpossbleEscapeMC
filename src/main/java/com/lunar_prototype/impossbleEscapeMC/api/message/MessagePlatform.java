package com.lunar_prototype.impossbleEscapeMC.api.message;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

/** 実サーバーとテストを分離する。イベントと送信はこの実装の主スレッド上で行う。 */
public interface MessagePlatform {
    boolean isPrimaryThread();
    void execute(Runnable task);
    boolean isOnline(CommandSender recipient);
    Locale locale(CommandSender recipient,Locale fallback);
    Collection<? extends CommandSender> broadcastRecipients();
    record Broadcast(Collection<? extends CommandSender> recipients, MessageContent content, boolean cancelled) {}
    default Broadcast prepareBroadcast(MessageContent content){return new Broadcast(broadcastRecipients(),content,false);}
    default void reportFailure(String key,RuntimeException error) {}
    void before(IemMessageSendEvent event);
    void after(IemMessageDispatchEvent event);
    void deliver(CommandSender recipient,MessageChannel channel,MessageContent content);
}
