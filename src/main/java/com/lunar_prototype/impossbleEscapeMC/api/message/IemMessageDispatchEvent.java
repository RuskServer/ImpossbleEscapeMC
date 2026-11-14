package com.lunar_prototype.impossbleEscapeMC.api.message;

import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import java.util.Locale;
import java.util.UUID;
/** 送信APIを実行した結果。クライアントによる受信・表示の保証ではない。 */
public final class IemMessageDispatchEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final IemMessageSendEvent request;
    private final MessageContent rendered;
    private final Locale locale;
    private final MessageResult.Status status;
    private final String error;
    public IemMessageDispatchEvent(IemMessageSendEvent request,MessageContent rendered,Locale locale,MessageResult.Status status,String error) {
        this.request=request;this.rendered=rendered;this.locale=locale;this.status=status;this.error=error;
    }
    public UUID getSendId(){return request.getSendId();}
    public CommandSender getRecipient(){return request.getRecipient();}
    public MessageDefinition getDefinition(){return request.getDefinition();}
    public MessageOrigin getOrigin(){return request.getOrigin();}
    public MessageContent getOriginalContent(){return request.getOriginalContent();}
    public MessageContent getContent(){return request.getContent();}
    public MessageContent getRenderedContent(){return rendered;}
    public Locale getLocale(){return locale;}
    public MessageResult.Status getStatus(){return status;}
    public String getError(){return error;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
