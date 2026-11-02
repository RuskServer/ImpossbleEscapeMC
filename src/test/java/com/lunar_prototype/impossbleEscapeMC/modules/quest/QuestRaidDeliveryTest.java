package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.command.QuestCommand;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.MoneyReward;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;

/** 実際の公開入口と実行処理を呼び、レイド中は消費・進捗・報酬まで到達しないことを確認する。 */
public final class QuestRaidDeliveryTest {
    private static final ServiceContainer services = new ServiceContainer();
    private static boolean participating;
    private static int messages, closed;
    public static final class RaidProbe extends RaidModule {
        RaidProbe() { super(null); }
        @Override public boolean isInRaid(Player player) { return participating; }
    }
    private static final class Quests extends QuestModule {
        int notified;
        Quests() { super(null); }
        @Override void notifyCompletion(Player player, QuestDefinition quest) { notified++; }
    }
    private static void check(boolean b,String message) { if(!b)throw new AssertionError(message); }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type, InvocationHandler h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);
    }
    /** JavaPluginやRaidModuleの起動・ファイル生成をせず、実サービス参照だけを検証する。 */
    private static Object allocate(Class<?> type) throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type);
    }
    private static Object session(Class<?> owner,Class<?>[] types,Object...args)throws Exception {
        Class<?> type=Class.forName(owner.getName()+"$Session");
        Constructor<?> constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    @SuppressWarnings("unchecked") private static Map<UUID,Object> sessions(Class<?> owner)throws Exception {
        Field field=owner.getDeclaredField("SESSIONS");field.setAccessible(true);return (Map<UUID,Object>)field.get(null);
    }
    private static void invoke(Class<?> owner,String method,Class<?>[] types,Object receiver,Object...args)throws Exception {
        Method target=owner.getDeclaredMethod(method,types);target.setAccessible(true);target.invoke(receiver,args);
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        UUID id=UUID.randomUUID();
        Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->id;
            case "isOnline"->true;
            case "sendMessage"->{messages++;yield null;}
            case "closeDialog"->{closed++;yield null;}
            default->throw new AssertionError("Unexpected player access (inventory read/consume/dialog build): "+m.getName());
        });
        Field singleton=ImpossbleEscapeMC.class.getDeclaredField("instance");singleton.setAccessible(true);
        Object before=singleton.get(null);
        Object plugin=allocate(ImpossbleEscapeMC.class);
        Field container=ImpossbleEscapeMC.class.getDeclaredField("serviceContainer");container.setAccessible(true);
        container.set(plugin,services);singleton.set(null,plugin);
        services.register(RaidModule.class,(RaidModule)allocate(RaidProbe.class));
        var quests=new Quests();var data=new PlayerData(id);
        var hand=new HandInObjective("bolts",null,1,true);
        var kill=new KillEntityObjective("SCAV",1);
        var reach=new ReachLocationObjective("raid",0,0,0,2,"warehouse");
        var quest=new QuestDefinition("delivery","kovacs","test","",List.of(),List.of(hand,kill,reach),List.of(new MoneyReward(100)));
        var active=new ActiveQuest(quest.getId());data.getActiveQuests().put(quest.getId(),active);
        Field catalog=QuestModule.class.getDeclaredField("quests");catalog.setAccessible(true);
        ((Map<String,QuestDefinition>)catalog.get(quests)).put(quest.getId(),quest);
        invoke(QuestModule.class,"setupEventBus",new Class<?>[]{},quests);
        Map<String,Object> item=Map.of("itemId","bolts","itemType","LOOT","isFIR",true,"amount",1);
        try {
            participating=true;
            check(QuestDeliveryAccess.blocked(player),"actual RaidModule membership is used");
            check(!hand.updateProgress(player,data,active,0,QuestTrigger.HAND_IN,item),"direct objective cannot accept raid delivery");
            quests.getEventBus().fire(player,data,QuestTrigger.HAND_IN,item);
            check(active.getProgress(0)==0,"generic event path cannot bypass raid restriction");
            quests.getEventBus().fire(player,data,QuestTrigger.KILL_ENTITY,Map.of("entityType","SCAV"));
            quests.getEventBus().fire(player,data,QuestTrigger.LOCATION_REACHED,Map.of("locationName","warehouse"));
            check(active.getProgress(1)==1&&active.getProgress(2)==1,"kill and exploration still progress in a raid");
            CommandSourceStack stack=proxy(CommandSourceStack.class,(p,m,a)->{
                if(m.getName().equals("getSender"))return player;throw new AssertionError(m.getName());
            });
            new QuestCommand(quests).execute(stack,new String[]{"handin"});
            check(active.getProgress(0)==0,"command is rejected before looking at or consuming the held item");
            QuestHandInDialog.open(player,null,null);
            check(!QuestHandInDialog.openForTrader(player,null,quests),"individual and bulk dialogs cannot open in raid");
            Class<?> handSession=Class.forName(QuestHandInDialog.class.getName()+"$Session");
            Method handIn=QuestHandInDialog.class.getDeclaredMethod("handIn",Player.class,handSession);handIn.setAccessible(true);
            check(((Map<?,?>)handIn.invoke(null,player,null)).isEmpty(),"delivery execution rechecks before reading inventory/data");
            check(new QuestAutoHandIn(player,data,quests).apply(List.of())==0,"old result-screen delivery cannot read/consume inventory");
            // ロビーで開いた画面のボタンが、参加後に遅れて届く場合。
            Object stale=session(QuestHandInDialog.class,new Class<?>[]{TraderDefinition.class,List.class,boolean.class},null,List.of(quest.getId()),false);
            sessions(QuestHandInDialog.class).put(id,stale);
            Constructor<?> handler=QuestHandInDialog.class.getDeclaredConstructor();handler.setAccessible(true);
            invoke(QuestHandInDialog.class,"handle",new Class<?>[]{Player.class,String.class},handler.newInstance(),player,"go");
            check(!sessions(QuestHandInDialog.class).containsKey(id)&&closed==1,"stale hand-in session is cleared without consumption");
            sessions(QuestDialog.class).put(id,session(QuestDialog.class,new Class<?>[]{String.class},"kovacs"));
            invoke(QuestDialog.class,"handle",new Class<?>[]{Player.class,String.class},null,player,"report/"+quest.getId());
            active.setProgress(0,1);
            quests.completeQuest(player,data,quest);
            check(data.getBalance()==0&&!data.isQuestCompleted(quest.getId())&&quests.notified==0,"raid reporting cannot grant rewards or complete a quest");
            check(messages>0,"blocked user actions receive an explanation");
            participating=false;active.setProgress(0,0);
            check(hand.updateProgress(player,data,active,0,QuestTrigger.HAND_IN,item),"delivery resumes after leaving raid membership");
            quests.completeQuest(player,data,quest);
            check(data.isQuestCompleted(quest.getId())&&data.getBalance()==100&&quests.notified==1,"report and rewards work normally after raid");
        } finally {
            sessions(QuestHandInDialog.class).remove(id);sessions(QuestDialog.class).remove(id);
            singleton.set(null,before);
        }
        System.out.println("QuestRaidDeliveryTest passed: command/dialog/objective/event/result guards, stale sessions, no raid reward and normal kill/exploration/post-raid delivery");
    }
}
