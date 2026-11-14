package com.lunar_prototype.impossbleEscapeMC.api.message;

import com.lunar_prototype.impossbleEscapeMC.message_test.MessageCallSiteProbe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.command.CommandSender;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static com.lunar_prototype.impossbleEscapeMC.api.message.MessageResult.Status.*;

/** 実サーバーを使わず、送信経路・非同期・イベント・停止・再帰を実際に動かす。 */
public final class MessageServiceTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static CommandSender sender(String name){return (CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class[]{CommandSender.class},(proxy,m,a)->switch(m.getName()) {
        case "getName","toString" -> name;case "hashCode" -> System.identityHashCode(proxy);case "equals" -> proxy==a[0];default -> m.getReturnType()==boolean.class?false:null;
    });}
    static final class Platform implements MessagePlatform {
        final Thread main=Thread.currentThread();final Queue<Runnable> queue=new ConcurrentLinkedQueue<>();
        final List<CommandSender> targets=new ArrayList<>();final Set<CommandSender> offline=new HashSet<>();final Map<CommandSender,Locale> locales=new HashMap<>();
        final List<IemMessageSendEvent> before=new ArrayList<>();final List<IemMessageDispatchEvent> after=new ArrayList<>();final List<MessageContent> delivered=new ArrayList<>();
        Consumer<IemMessageSendEvent> handler=e->{};Consumer<IemMessageDispatchEvent> observer=e->{};CommandSender fail;boolean globalCancel;
        public boolean isPrimaryThread(){return Thread.currentThread()==main;}
        public void execute(Runnable r){queue.add(r);}
        public boolean isOnline(CommandSender p){return !offline.contains(p);}
        public Locale locale(CommandSender p,Locale fallback){return locales.getOrDefault(p,fallback);}
        public Collection<? extends CommandSender> broadcastRecipients(){return List.copyOf(targets);}
        public Broadcast prepareBroadcast(MessageContent c){return new Broadcast(broadcastRecipients(),c,globalCancel);}
        public void before(IemMessageSendEvent e){check(isPrimaryThread(),"before on primary thread");before.add(e);handler.accept(e);}
        public void after(IemMessageDispatchEvent e){check(isPrimaryThread(),"after on primary thread");after.add(e);observer.accept(e);}
        public void deliver(CommandSender p,MessageChannel channel,MessageContent content){check(isPrimaryThread(),"deliver on primary thread");if(p==fail)throw new IllegalStateException("test failure");delivered.add(content);}
        void drain(){for(Runnable r;(r=queue.poll())!=null;)r.run();}
    }
    static final String KEY="iem.admin.messages.reload_failed";
    static final MessageContent BODY=MessageContent.text(MessageText.of(KEY,"detail"));
    static final MessageOrigin ORIGIN=new MessageOrigin("caller","method","Caller.java",17);
    public static void main(String[] args)throws Exception {
        MessageCatalog catalog=MessageCatalog.load(null,"ja_jp");Platform p=new Platform();
        CommandSender jp=sender("ja"),en=sender("en"),off=sender("offline");p.locales.put(en,Locale.US);p.offline.add(off);p.targets.addAll(List.of(jp,en,off));
        MessageService service=new MessageService(catalog,p,true);
        check(MessageCatalogTest.plain(GlobalTranslator.render(BODY.text(),Locale.US)).startsWith("Reload failed"),"global registration");
        List<MessageResult> batch=service.broadcast(KEY,BODY,ORIGIN).toCompletableFuture().join();
        check(batch.size()==3&&batch.get(0).sendId().equals(batch.get(1).sendId()),"one ID per broadcast");
        check(batch.get(2).status()==OFFLINE,"offline result");
        check(MessageCatalogTest.plain(p.delivered.get(0).text()).contains("再読み込み"),"recipient Japanese");
        check(MessageCatalogTest.plain(p.delivered.get(1).text()).contains("Reload failed"),"recipient English");
        int count=p.delivered.size();p.handler=e->e.setCancelled(true);
        check(service.send(jp,KEY,BODY,ORIGIN).toCompletableFuture().join().getFirst().status()==CANCELLED&&p.delivered.size()==count,"cancellation prevents delivery");
        p.handler=e->e.setMessage(Component.text("changed"));
        service.send(en,KEY,BODY,ORIGIN).toCompletableFuture().join();var last=p.after.getLast();
        check(last.getDefinition().key().equals(KEY)&&last.getOriginalContent().equals(BODY),"immutable identity and original");
        check(MessageCatalogTest.plain(last.getRenderedContent().text()).equals("changed"),"replacement before rendering");
        p.handler=e->{p.offline.add(jp);};
        check(service.send(jp,KEY,BODY,ORIGIN).toCompletableFuture().join().getFirst().status()==OFFLINE,"disconnect during event rechecked");p.offline.remove(jp);
        p.handler=e->{};p.fail=jp;
        var failures=service.group(List.of(jp,en),KEY,BODY,ORIGIN).toCompletableFuture().join();
        check(failures.getFirst().status()==FAILED&&failures.getLast().status()==DISPATCHED,"one failure does not stop other recipients");p.fail=null;
        p.globalCancel=true;
        check(service.broadcast(KEY,BODY,ORIGIN).toCompletableFuture().join().stream().allMatch(x->x.status()==CANCELLED||x.status()==OFFLINE),"standard broadcast cancellation respected");p.globalCancel=false;
        String titleKey=catalog.definitions().values().stream().filter(x->x.channel()==MessageChannel.TITLE).findFirst().orElseThrow().key();
        var times=Title.Times.times(Duration.ofMillis(150),Duration.ofSeconds(2),Duration.ofMillis(300));
        service.send(jp,titleKey,MessageContent.title(Title.title(Component.text("title"),Component.text("sub"),times)),ORIGIN).toCompletableFuture().join();
        check(p.after.getLast().getRenderedContent().times().equals(times),"title timing preserved");
        Messages.installForTest(service);
        AtomicReference<CompletionStage<List<MessageResult>>> queued=new AtomicReference<>();
        Thread thread=new Thread(()->queued.set(MessageCallSiteProbe.send(en)));thread.start();thread.join();
        check(!queued.get().toCompletableFuture().isDone(),"async call queues instead of firing async events");p.drain();
        check(queued.get().toCompletableFuture().join().getFirst().status()==DISPATCHED,"queued delivery");
        MessageOrigin origin=p.after.getLast().getOrigin();check(origin.className().endsWith("MessageCallSiteProbe")&&origin.methodName().equals("send")&&origin.lineNumber()>0,"source captured before scheduling");
        AtomicReference<MessageResult.Status> recursive=new AtomicReference<>();
        p.handler=e->{var result=service.send(jp,KEY,BODY,ORIGIN).toCompletableFuture().join().getFirst();if(result.status()==RECURSION_LIMIT)recursive.set(result.status());};
        service.send(jp,KEY,BODY,ORIGIN).toCompletableFuture().join();check(recursive.get()==RECURSION_LIMIT,"recursive listeners bounded");p.handler=e->{};
        p.observer=e->{throw new IllegalStateException("observer failure");};
        check(service.group(List.of(jp,en),KEY,BODY,ORIGIN).toCompletableFuture().join().stream().allMatch(x->x.status()==DISPATCHED),"observer failure does not interrupt delivery");p.observer=e->{};
        thread=new Thread(()->queued.set(MessageCallSiteProbe.send(en)));thread.start();thread.join();count=p.delivered.size();service.close();
        check(queued.get().toCompletableFuture().join().getFirst().status()==STOPPED,"shutdown completes pending futures");p.drain();check(p.delivered.size()==count,"stopped queued task cannot deliver later");
        check(GlobalTranslator.render(BODY.text(),Locale.US).equals(BODY.text()),"global source removed on shutdown");Messages.installForTest(null);
        System.out.println("MessageServiceTest OK: events, locale, broadcast, mutation, offline/failure, async origin, recursion, shutdown");
    }
}
