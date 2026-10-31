package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import java.util.*;

/** 受取通知と、実完了処理での付与・通知の順序および再操作を確認する。 */
public final class QuestCompletionNoticeTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static String text(Component value){return PlainTextComponentSerializer.plainText().serialize(value);}
    public static void main(String[] args) throws Exception {
        var quest=new QuestBuilder(null,"notice_test").trader("kovacs").name("工具の調達")
                .handInFir("bolts",1).money(8000).exp(950).unlockTrade("kovacs","uzi").build();
        class Probe extends QuestModule {
            int notifications;
            PlayerData data;
            Probe(){super(null);}
            @Override void notifyCompletion(Player player,QuestDefinition completed) {
                check(data.isQuestCompleted(completed.getId()),"completion recorded before notification");
                check(data.getBalance()==8000&&data.getExperience()==950,"receipt only after cash and EXP are applied");
                notifications++;
            }
        }
        var module=new Probe();var data=new PlayerData(UUID.randomUUID());module.data=data;
        module.completeQuest(null,data,quest);check(module.notifications==0,"unaccepted quest is silent");
        var active=new ActiveQuest(quest.getId());data.getActiveQuests().put(quest.getId(),active);
        module.completeQuest(null,data,quest);check(module.notifications==0&&data.getBalance()==0,"incomplete quest neither pays nor notifies");
        active.setProgress(0,1);module.completeQuest(null,data,quest);
        check(module.notifications==1&&data.getActiveQuests().isEmpty(),"one completion and notification");
        module.completeQuest(null,data,quest);
        check(module.notifications==1&&data.getBalance()==8000&&data.getExperience()==950,"repeat report cannot pay or notify twice");

        var lines=QuestCompletionNotice.lines(quest,id->"UZI",id->"Kovacs");
        check(lines.size()==3&&text(lines.get(1)).contains("+8,000 ₽")&&text(lines.get(1)).contains("+950 EXP"),"paid rewards remain visible together");
        check(text(lines.get(2)).contains("UZI"),"short unlock name shown directly");
        var bulk=new QuestDefinition("bulk","kovacs","一括調達","",List.of(),List.of(),List.of(
                new MoneyReward(8000),new MoneyReward(2000),new ExpReward(100),new ExpReward(200),
                new UnlockTradeReward("kovacs","one"),new UnlockTradeReward("kovacs","two"),
                new UnlockTradeReward("pharmakon","three"),new UnlockTradeReward("kovacs","one")));
        lines=QuestCompletionNotice.lines(bulk,id->"商品:"+id,id->"商人:"+id);
        check(lines.size()==3&&text(lines.get(1)).contains("+10,000 ₽")&&text(lines.get(1)).contains("+300 EXP"),"multiple monetary rewards aggregated without dropping value");
        check(text(lines.get(2)).contains("3件"),"large unlock receipt remains compact and deduplicated");
        HoverEvent<?> hover=lines.get(2).hoverEvent();check(hover!=null&&hover.action()==HoverEvent.Action.SHOW_TEXT,"trade detail hover present");
        String details=text((Component)hover.value());
        check(details.contains("商人:kovacs / 商品:one")&&details.contains("商人:pharmakon / 商品:three")
                &&details.contains("必要レベル"),"all distinct trade names, owners and purchase qualifications available");
        var cashOnly=new QuestBuilder(null,"cash").trader("kovacs").name("帰還").handInFir("bolts",1).money(1).exp(1).build();
        check(QuestCompletionNotice.lines(cashOnly,id->id,id->id).size()==2,"ordinary completion uses only two lines");
        var plain=QuestModule.class.getDeclaredMethod("plainName",String.class);plain.setAccessible(true);
        check(plain.invoke(null,"&a§lUZI").equals("UZI"),"legacy formatting cannot leak into receipt text");
        System.out.println("QuestCompletionNoticeTest passed: paid-before-notified, repeat guard, compact receipt, aggregation and full trade hover");
    }
}
