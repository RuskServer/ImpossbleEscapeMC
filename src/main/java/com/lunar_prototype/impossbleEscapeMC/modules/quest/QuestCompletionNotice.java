package com.lunar_prototype.impossbleEscapeMC.modules.quest;

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
        lines.add(Component.text("依頼完了",AMBER).append(Component.text("  "+quest.getDisplayName(),NAME)));
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
            Component receipt=Component.text("  受取  ",NOTE);
            if(money!=0)receipt=receipt.append(Component.text((money>0?"+":"")+format.format(money)+" ₽",AMBER));
            if(money!=0&&exp!=0)receipt=receipt.append(Component.text("  /  ",DIM));
            if(exp!=0)receipt=receipt.append(Component.text((exp>0?"+":"")+format.format(exp)+" EXP",OK));
            lines.add(receipt);
        }
        if(!unlocks.isEmpty()) {
            List<String> names=new ArrayList<>();
            Component details=Component.text("取引解放",AMBER);
            for(Unlock unlock:unlocks) {
                String item=name(unlock.item,itemName);names.add(item);
                details=details.append(Component.newline()).append(Component.text(name(unlock.trader,traderName)+" / ",NOTE))
                        .append(Component.text(item,NAME));
            }
            details=details.append(Component.newline()).append(Component.text("必要レベル・購入上限は各取引条件に従います。",DIM));
            String summary=String.join("、",names);
            if(names.size()>2||summary.codePointCount(0,summary.length())>28)
                summary=names.size()+"件（ホバーで詳細）";
            lines.add(Component.text("  取引解放  ",NOTE).append(Component.text(summary,INK))
                    .hoverEvent(HoverEvent.showText(details.decoration(TextDecoration.ITALIC,false))));
        }
        for(String description:other)lines.add(Component.text("  "+description,INK));
        return lines.stream().map(line->line.decoration(TextDecoration.ITALIC,false)).toList();
    }

    private static String name(String id,Function<String,String> resolver) {
        String value=resolver.apply(id);
        return value==null||value.isBlank()?id:value;
    }
}
