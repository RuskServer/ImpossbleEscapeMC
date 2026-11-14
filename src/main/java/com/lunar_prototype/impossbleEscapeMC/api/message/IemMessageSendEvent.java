package com.lunar_prototype.impossbleEscapeMC.api.message;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import java.util.Objects;
import java.util.UUID;
/** 宛先ごとの同期イベント。通知の中止はゲーム本体の処理を中止しない。 */
public final class IemMessageSendEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final UUID sendId;
    private final CommandSender recipient;
    private final MessageDefinition definition;
    private final MessageOrigin origin;
    private final MessageContent original;
    private MessageContent content;
    private boolean cancelled;
    public IemMessageSendEvent(UUID id,CommandSender recipient,MessageDefinition definition,MessageOrigin origin,MessageContent content) {
        this.sendId=id;this.recipient=recipient;this.definition=definition;this.origin=origin;this.original=content;this.content=content;
    }
    public UUID getSendId(){return sendId;}
    public CommandSender getRecipient(){return recipient;}
    public MessageDefinition getDefinition(){return definition;}
    public String getMessageKey(){return definition.key();}
    public MessageCategory getCategory(){return definition.category();}
    public MessageLevel getLevel(){return definition.level();}
    public MessageChannel getChannel(){return definition.channel();}
    public MessageOrigin getOrigin(){return origin;}
    public MessageContent getOriginalContent(){return original;}
    public MessageContent getContent(){return content;}
    public void setContent(MessageContent content){this.content=Objects.requireNonNull(content);}
    public void setMessage(Component text){content=new MessageContent(Objects.requireNonNull(text),content.subtitle(),content.times());}
    @Override public boolean isCancelled(){return cancelled;}
    @Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
