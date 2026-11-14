package com.lunar_prototype.impossbleEscapeMC.message_test;
import com.lunar_prototype.impossbleEscapeMC.api.message.*;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.concurrent.*;
/** API外の実際の呼び出し位置を取得するためのテスト用コード。 */
public final class MessageCallSiteProbe {
    public static CompletionStage<List<MessageResult>> send(CommandSender target){
        return Messages.chat(target,"iem.admin.messages.usage",MessageText.of("iem.admin.messages.usage"));
    }
}
