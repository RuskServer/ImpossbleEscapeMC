package com.lunar_prototype.impossbleEscapeMC.api.message;

import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.util.*;

/** サーバーなしの既存機能テストにも、本番と同じ辞書・送信経路を入れる。 */
public final class MessageTestSupport {
    private static final MessageCatalog CATALOG=load();
    private static MessageCatalog load(){try{return MessageCatalog.load(null,"ja_jp");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
    public static Component japanese(Component input){return MessageService.render(input,Locale.JAPAN,CATALOG);}
    public static AutoCloseable forwarding(){
        MessageService service=new MessageService(CATALOG,new MessagePlatform(){
            public boolean isPrimaryThread(){return true;}
            public void execute(Runnable r){r.run();}
            public boolean isOnline(CommandSender p){return true;}
            public Locale locale(CommandSender p,Locale fallback){return fallback;}
            public Collection<? extends CommandSender> broadcastRecipients(){return List.of();}
            public void before(IemMessageSendEvent e){}
            public void after(IemMessageDispatchEvent e){}
            public void deliver(CommandSender p,MessageChannel c,MessageContent content){
                switch(c){case CHAT,CONSOLE->p.sendMessage(content.text());case ACTION_BAR->p.sendActionBar(content.text());case TITLE->p.showTitle(Title.title(content.text(),content.subtitle(),content.times()));}
            }
        },false);
        Messages.installForTest(service);return ()->{service.close();Messages.installForTest(null);};
    }
}
