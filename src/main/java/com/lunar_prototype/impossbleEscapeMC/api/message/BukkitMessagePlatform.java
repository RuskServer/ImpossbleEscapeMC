package com.lunar_prototype.impossbleEscapeMC.api.message;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.title.Title;
import java.util.*;

/** Bukkitへの実送信はここだけに置く。 */
final class BukkitMessagePlatform implements MessagePlatform {
    private final JavaPlugin plugin;
    BukkitMessagePlatform(JavaPlugin plugin){this.plugin=plugin;}
    public boolean isPrimaryThread(){return Bukkit.isPrimaryThread();}
    public void execute(Runnable task){Bukkit.getScheduler().runTask(plugin,task);}
    public boolean isOnline(CommandSender target){return !(target instanceof Player p)||p.isOnline();}
    public Locale locale(CommandSender target,Locale fallback){return target instanceof Player p?p.locale():fallback;}
    public Collection<? extends CommandSender> broadcastRecipients(){
        List<CommandSender> targets=new ArrayList<>();
        for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("bukkit.broadcast.user"))targets.add(p);
        if(Bukkit.getConsoleSender().hasPermission("bukkit.broadcast.user"))targets.add(Bukkit.getConsoleSender());
        return targets;
    }
    public Broadcast prepareBroadcast(MessageContent content){
        // Bukkit.broadcastを置き換えても既存の標準イベント・受信権限を維持する。
        var event=new org.bukkit.event.server.BroadcastMessageEvent(false,content.text(),new LinkedHashSet<>(broadcastRecipients()));
        Bukkit.getPluginManager().callEvent(event);
        return new Broadcast(List.copyOf(event.getRecipients()),MessageContent.text(event.message()),event.isCancelled());
    }
    public void reportFailure(String key,RuntimeException error){plugin.getLogger().warning("Message dispatch failed ["+key+"]: "+error);}
    public void before(IemMessageSendEvent e){Bukkit.getPluginManager().callEvent(e);}
    public void after(IemMessageDispatchEvent e){Bukkit.getPluginManager().callEvent(e);}
    public void deliver(CommandSender target,MessageChannel channel,MessageContent content){
        switch(channel){
            case CHAT,CONSOLE -> target.sendMessage(content.text());
            case ACTION_BAR -> target.sendActionBar(content.text());
            case TITLE -> target.showTitle(Title.title(content.text(),content.subtitle(),content.times()));
        }
    }
}
