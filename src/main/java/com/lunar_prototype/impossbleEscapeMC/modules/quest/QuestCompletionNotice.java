package com.lunar_prototype.impossbleEscapeMC.modules.quest;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Function;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.*;

/** ダイアログと同じ色で、完了時の受取内容を短いチャットへまとめる。 */
final class QuestCompletionNotice {
    private record Unlock(String trader,String item) {}
    private QuestCompletionNotice() {}

    static List<Component> lines(QuestDefinition quest,Function<String,String> itemName,Function<String,String> traderName) {
        List<Component> lines=new ArrayList<>();
        lines.add(MessageText.of("iem.quest.questcompletionnotice.lines.support_001").color(AMBER).append(MessageText.of("iem.quest.questcompletionnotice.lines.support_002", quest.getDisplayName()).color(NAME)));
        double money=0;long exp=0;
        Set<Unlock> unlocks=new LinkedHashSet<>();
        List<String> other=new ArrayList<>();
        for(QuestReward reward:quest.getRewards()) {
            if(reward instanceof MoneyReward m)money+=m.getAmount();
            else if(reward instanceof ExpReward e)exp+=e.getAmount();
            else if(reward instanceof UnlockTradeReward u)unlocks.add(new Unlock(u.getTraderId(),u.getItemId()));
            else other.add(reward.getDescription());
        }
        var format=new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.ROOT));
        if(money!=0||exp!=0) {
            Component receipt=MessageText.of("iem.quest.questcompletionnotice.lines.support_003").color(NOTE);
            if(money!=0)receipt=receipt.append(MessageText.of("iem.quest.questcompletionnotice.lines.support_004", (money>0?"+":""), format.format(money)).color(AMBER));
            if(money!=0&&exp!=0)receipt=receipt.append(MessageText.of("iem.quest.questcompletionnotice.lines.support_005").color(DIM));
            if(exp!=0)receipt=receipt.append(MessageText.of("iem.quest.questcompletionnotice.lines.support_006", (exp>0?"+":""), format.format(exp)).color(OK));
            lines.add(receipt);
        }
        if(!unlocks.isEmpty()) {
            List<String> names=new ArrayList<>();
            Component details=MessageText.of("iem.quest.questcompletionnotice.lines.support_007").color(AMBER);
            for(Unlock unlock:unlocks) {
                String item=name(unlock.item,itemName);names.add(item);
                details=details.append(Component.newline()).append(MessageText.of("iem.quest.questcompletionnotice.lines.support_008", name(unlock.trader,traderName)).color(NOTE))
                        .append(Component.text(item,NAME));
            }
            details=details.append(Component.newline()).append(MessageText.of("iem.quest.questcompletionnotice.lines.support_009").color(DIM));
            String joined=String.join("、",names);
            Component summary=Component.text(joined,INK);
            if(names.size()>2||joined.codePointCount(0,joined.length())>28)
                summary=MessageText.of("iem.quest.questcompletionnotice.unlock_count", names.size());
            lines.add(MessageText.of("iem.quest.questcompletionnotice.lines.support_010").color(NOTE).append(summary.color(INK))
                    .hoverEvent(HoverEvent.showText(details.decoration(TextDecoration.ITALIC,false))));
        }
        for(String description:other)lines.add(MessageText.of("iem.quest.questcompletionnotice.lines.support_011", description).color(INK));
        return lines.stream().map(line->line.decoration(TextDecoration.ITALIC,false)).toList();
    }

    private static String name(String id,Function<String,String> resolver) {
        String value=resolver.apply(id);
        return value==null||value.isBlank()?id:value;
    }
}
