package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 分類・イベント・言語展開・実送信を一つの経路へ集約する。 */
public final class MessageService implements AutoCloseable {
    private volatile MessageCatalog catalog;
    private final MessagePlatform platform;
    private final Set<Pending> pending=ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
    private volatile boolean stopped;
    private final boolean globalRegistration;
    private static final int MAX_DEPTH=8;
    public MessageService(MessageCatalog catalog,MessagePlatform platform,boolean registerGlobal){
        this.catalog=Objects.requireNonNull(catalog);this.platform=Objects.requireNonNull(platform);this.globalRegistration=registerGlobal;
        if(registerGlobal)GlobalTranslator.translator().addSource(catalog);
    }
    public synchronized void reload(MessageCatalog replacement){
        Objects.requireNonNull(replacement);if(stopped)throw new IllegalStateException("Messages stopped");
        if(globalRegistration){GlobalTranslator.translator().removeSource(catalog);GlobalTranslator.translator().addSource(replacement);}
        catalog=replacement;
    }
    public MessageCatalog catalog(){return catalog;}
    public CompletionStage<List<MessageResult>> send(CommandSender target,String key,MessageContent content,MessageOrigin origin){
        return enqueue(UUID.randomUUID(),List.of(target),key,content,origin,false);
    }
    public CompletionStage<List<MessageResult>> broadcast(String key,MessageContent content,MessageOrigin origin){
        return enqueue(UUID.randomUUID(),List.of(),key,content,origin,true);
    }
    public CompletionStage<List<MessageResult>> group(Collection<? extends CommandSender> targets,String key,MessageContent content,MessageOrigin origin){
        return enqueue(UUID.randomUUID(),List.copyOf(new LinkedHashSet<>(targets)),key,content,origin,false);
    }
    private static final class Pending {
        final UUID id;final CompletableFuture<List<MessageResult>> future=new CompletableFuture<>();final AtomicBoolean claimed=new AtomicBoolean();
        Pending(UUID id){this.id=id;}
    }
    private CompletionStage<List<MessageResult>> enqueue(UUID id,List<? extends CommandSender> requested,String key,MessageContent content,MessageOrigin origin,boolean broadcast){
        Objects.requireNonNull(content);Objects.requireNonNull(origin);catalog.definition(key);
        Objects.requireNonNull(requested);
        Pending request=new Pending(id);pending.add(request);
        if(stopped){finishStopped(request);return request.future;}
        Runnable task=()->{
            if(!request.claimed.compareAndSet(false,true))return;
            int nesting=depth.get();
            if(nesting>=MAX_DEPTH){pending.remove(request);request.future.complete(List.of(new MessageResult(id,MessageResult.Status.RECURSION_LIMIT)));return;}
            depth.set(nesting+1);
            try {
                if(stopped){request.future.complete(List.of(new MessageResult(id,MessageResult.Status.STOPPED)));return;}
                MessageCatalog dictionary=catalog;
                MessagePlatform.Broadcast batch=broadcast?platform.prepareBroadcast(content):new MessagePlatform.Broadcast(requested,content,false);
                List<MessageResult> results=new ArrayList<>();
                for(CommandSender recipient:List.copyOf(batch.recipients()))
                    results.add(dispatch(id,recipient,dictionary.definition(key),content,batch.content(),batch.cancelled(),origin,dictionary));
                request.future.complete(List.copyOf(results));
            }catch(RuntimeException e){platform.reportFailure(key,e);request.future.completeExceptionally(e);}
            finally{pending.remove(request);depth.set(nesting);if(nesting==0)depth.remove();}
        };
        if(platform.isPrimaryThread())task.run();
        else try{platform.execute(task);}catch(RuntimeException e){finishStopped(request);}
        return request.future;
    }
    private MessageResult dispatch(UUID id,CommandSender recipient,MessageDefinition definition,MessageContent original,MessageContent content,boolean blocked,MessageOrigin origin,MessageCatalog dictionary){
        if(definition.channel()==MessageChannel.CHAT && (recipient instanceof org.bukkit.command.ConsoleCommandSender
                || recipient instanceof org.bukkit.command.RemoteConsoleCommandSender))
            definition=new MessageDefinition(definition.key(),definition.category(),definition.level(),MessageChannel.CONSOLE);
        int nesting=depth.get();
        // ここでイベントを再発火すると再帰するため、上限時は結果だけ返す。
        if(nesting>=MAX_DEPTH)return new MessageResult(id,MessageResult.Status.RECURSION_LIMIT);
        depth.set(nesting+1);
        IemMessageSendEvent event=new IemMessageSendEvent(id,recipient,definition,origin,original);
        event.setContent(content);
        Locale locale=dictionary.defaultLocale();MessageContent rendered=null;MessageResult.Status status;String error=null;
        try {
            if(!platform.isOnline(recipient))status=MessageResult.Status.OFFLINE;
            else {
                locale=platform.locale(recipient,dictionary.defaultLocale());
                platform.before(event);
                if(blocked||event.isCancelled())status=MessageResult.Status.CANCELLED;
                else if(stopped)status=MessageResult.Status.STOPPED;
                else if(!platform.isOnline(recipient))status=MessageResult.Status.OFFLINE;
                else {
                    MessageContent modified=event.getContent();
                    // 明示的な翻訳はGUIなどの自動翻訳の有無に依存しない。
                    Component text=render(modified.text(),locale,dictionary);
                    Component subtitle=render(modified.subtitle(),locale,dictionary);
                    rendered=new MessageContent(text,subtitle,modified.times());
                    platform.deliver(recipient,definition.channel(),rendered);status=MessageResult.Status.DISPATCHED;
                }
            }
        }catch(RuntimeException e){status=MessageResult.Status.FAILED;error=e.getClass().getName()+": "+e.getMessage();platform.reportFailure(definition.key(),e);}
        try{platform.after(new IemMessageDispatchEvent(event,rendered,locale,status,error));}
        catch(RuntimeException e){platform.reportFailure(definition.key(),e);}
        finally{depth.set(nesting);if(nesting==0)depth.remove();}
        return new MessageResult(id,status);
    }
    public static Component render(Component component,Locale locale,MessageCatalog dictionary){
        return net.kyori.adventure.text.renderer.TranslatableComponentRenderer.usingTranslationSource(dictionary).render(component,locale);
    }
    private void finishStopped(Pending p){if(p.claimed.compareAndSet(false,true)){pending.remove(p);p.future.complete(List.of(new MessageResult(p.id,MessageResult.Status.STOPPED)));}}
    @Override public synchronized void close(){
        stopped=true;for(Pending p:pending)finishStopped(p);
        if(globalRegistration)GlobalTranslator.translator().removeSource(catalog);
    }
}
