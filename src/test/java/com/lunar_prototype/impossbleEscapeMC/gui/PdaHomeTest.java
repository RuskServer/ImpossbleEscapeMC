package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.gui.PdaHomeModel.Action;

/** 表示モデルと実際の操作コントローラーを検証する。Minecraftクライアントは不要。 */
public final class PdaHomeTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static PdaHomeModel.View view(int level,boolean raid,boolean hideout,Set<Action> available) {
        return new PdaHomeModel.View("Operator12345678",level,350,12000,123450,12,raid,hideout,
                "3人 / リーダー","Inviter1234567890",available);
    }
    private static boolean hasHover(Component c) {
        return c.hoverEvent()!=null||c.children().stream().anyMatch(PdaHomeTest::hasHover);
    }
    private static final class Port implements PdaHomeController.Port {
        PdaHomeModel.View view;
        String token;
        PdaHomeController.Page page;
        int opened,closed,denied,unavailable;
        final List<Action> routes=new ArrayList<>();
        PdaHomeModel.View dispatched;
        Port(PdaHomeModel.View view) {this.view=view;}
        public PdaHomeModel.View snapshot(Player p) {return view;}
        public void show(Player p,String token,PdaHomeModel.View view,PdaHomeController.Page page) {this.token=token;this.page=page;opened++;}
        public void unavailable(Player p) {unavailable++;}
        public void denied(Player p,String reason) {check(reason!=null&&!reason.isBlank(),"blocked action explains why");denied++;}
        public void close(Player p) {closed++;}
        public void dispatch(Player p,Action action,PdaHomeModel.View view) {routes.add(action);dispatched=view;}
    }
    public static void main(String[] args) {
        var all=EnumSet.allOf(Action.class);
        var plain=PlainTextComponentSerializer.plainText();
        for(boolean raid:new boolean[]{false,true})for(boolean hideout:new boolean[]{false,true})for(int level:new int[]{1,9,10,40}) {
            var v=view(level,raid,hideout,all);var entries=PdaHomeModel.entries(v);
            check(entries.size()==9&&entries.stream().map(PdaHomeModel.Entry::action).distinct().count()==9,"nine home actions");
            for(var entry:entries) {
                check(!entry.title().isBlank()&&!entry.description().isBlank(),"name and purpose always present");
                var component=PdaHomeScreen.buttonLabel(entry);
                check(!hasHover(component)&&plain.serialize(component).equals(entry.label()),"item name includes purpose or disabled reason");
                check(entry.enabled()==(entry.reason()==null),"availability and visible reason agree");
            }
            check(entries.stream().anyMatch(e -> e.action()==Action.HIDEOUT_MENU), "home opens hideout selection");
            check(entries.stream().noneMatch(e -> e.action()==Action.HIDEOUT || e.action()==Action.FACILITIES), "home has no direct travel or facility buttons");
            var hideoutEntries=PdaHomeModel.hideoutEntries(v);
            check(hideoutEntries.size()==2, "hideout page has exactly two choices");
            check(hideoutEntries.getFirst().title().equals(hideout?"ロビーへ戻る":"隠れ家へ移動"), "travel direction is explicit");
            check(hideoutEntries.getLast().action()==Action.FACILITIES, "facility management is separate");
            for(var line:PdaHomeScreen.header(v)) {
                check(!hasHover(line),"status lore uses plain text");
            }
            for(Action action:List.of(Action.QUESTS,Action.PARTY,Action.SETTINGS))check(PdaHomeModel.blocked(v,action)==null,"raid-safe actions remain available");
            if(raid)for(Action action:List.of(Action.RAID,Action.STASH,Action.TRADERS,Action.HIDEOUT_MENU,Action.HIDEOUT,Action.FACILITIES,Action.MARKET))
                check("レイド中".equals(PdaHomeModel.blocked(v,action)),"raid restriction is visible");
            if(!raid)check((PdaHomeModel.blocked(v,Action.MARKET)==null)==(level>=10),"market level gate");
        }
        for (var page : PdaHomeController.Page.values()) {
            var slots = new HashSet<Integer>();
            for (Action action : Action.values()) {
                int slot=PdaHomeScreen.slot(page,action);
                if (slot < 0) continue;
                check(slot < 27 && slot != 4 && slot != 13 && slots.add(slot), "unique buttons preserve status slots");
                check(PdaHomeScreen.actionAt(page,slot)==action,"each chest slot routes to its own action");
            }
            check(PdaHomeScreen.actionAt(page,-1)==null && PdaHomeScreen.actionAt(page,27)==null,"outside clicks cannot route");
        }
        check(PdaHomeScreen.slot(PdaHomeController.Page.HOME,Action.HIDEOUT_MENU)==9,"original hideout button position");
        check(PdaHomeScreen.slot(PdaHomeController.Page.HIDEOUT,Action.HIDEOUT)==11 && PdaHomeScreen.slot(PdaHomeController.Page.HIDEOUT,Action.FACILITIES)==15,"two separate submenu buttons");
        var mutable=EnumSet.allOf(Action.class);var copied=view(10,false,false,mutable);mutable.clear();
        check(PdaHomeModel.blocked(copied,Action.RAID)==null,"view copies module availability");
        check("利用不可".equals(PdaHomeModel.blocked(view(40,false,false,Set.of()),Action.STASH)),"missing destination cannot be selected");
        check(Action.parse("invented")==null&&Action.parse("close/extra")==null,"unknown action path rejected");
        UUID id=UUID.randomUUID();boolean[] online={true},dead={false};
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->id;case "isOnline"->online[0];case "isDead"->dead[0];
            default->throw new AssertionError("controller must not mutate inventory or world directly: "+m.getName());
        });
        var port=new Port(view(12,false,false,all));var controller=new PdaHomeController(port);
        controller.open(player);String stale=port.token;controller.open(player);String current=port.token;
        controller.handle(player,stale,Action.HIDEOUT_MENU);
        check(port.page==PdaHomeController.Page.HOME&&port.routes.isEmpty(),"stale menu cannot open");
        port.view=view(12,true,false,all);controller.handle(player,current,Action.HIDEOUT_MENU);
        check(port.page==PdaHomeController.Page.HOME&&port.denied==1,"raid entry blocks submenu");
        port.view=view(12,false,false,all);controller.open(player);
        current=port.token;controller.handle(player,current,Action.HIDEOUT);
        check(port.routes.isEmpty(),"home cannot bypass submenu with direct travel");
        controller.handle(player,current,Action.HIDEOUT_MENU);
        check(port.page==PdaHomeController.Page.HIDEOUT&&port.routes.isEmpty(),"hideout click opens two choices without teleport");
        String submenu=port.token;controller.handle(player,submenu,Action.RAID);
        check(port.routes.isEmpty(),"submenu cannot invoke a home-only action");
        port.view=view(12,false,true,all);controller.handle(player,submenu,Action.HIDEOUT);
        controller.handle(player,submenu,Action.FACILITIES);
        check(port.routes.equals(List.of(Action.HIDEOUT))&&port.dispatched.inHideout(),"travel rechecks location and executes once");
        controller.open(player);controller.handle(player,port.token,Action.HIDEOUT_MENU);
        controller.handle(player,port.token,Action.BACK);
        check(port.page==PdaHomeController.Page.HOME&&port.routes.size()==1,"back returns home without travel");
        controller.handle(player,port.token,Action.HIDEOUT_MENU);
        port.view=view(12,true,false,all);int routes=port.routes.size();
        controller.handle(player,port.token,Action.FACILITIES);
        check(port.routes.size()==routes&&port.page==PdaHomeController.Page.HIDEOUT&&port.denied==2,"raid entry while submenu is open blocks facilities and refreshes reasons");
        controller.handle(player,port.token,Action.BACK);
        port.view=view(12,false,false,all);controller.handle(player,port.token,Action.HIDEOUT_MENU);
        controller.handle(player,port.token,Action.FACILITIES);
        check(port.routes.getLast()==Action.FACILITIES,"facility choice opens management");
        port.view=view(9,false,false,all);controller.open(player);controller.handle(player,port.token,Action.MARKET);
        check(port.denied==3,"market level gate remains");
        port.view=view(12,false,false,all);
        for(Action action:List.of(Action.RAID,Action.QUESTS,Action.STASH,Action.TRADERS,Action.MARKET,Action.PARTY,Action.SETTINGS)) {
            controller.open(player);controller.handle(player,port.token,action);check(port.routes.getLast()==action,"all destinations retained");
        }
        controller.open(player);int before=port.opened;controller.handle(player,port.token,Action.REFRESH);
        check(port.opened==before+1&&port.page==PdaHomeController.Page.HOME,"refresh reloads home");
        controller.forget(id);before=port.routes.size();controller.handle(player,port.token,Action.RAID);
        check(port.routes.size()==before,"close or quit invalidates session");
        controller.open(player);controller.clear();controller.handle(player,port.token,Action.RAID);
        check(port.routes.size()==before,"shutdown invalidates session");
        controller.open(player);port.view=null;controller.handle(player,port.token,Action.STASH);
        check(port.unavailable==1&&port.routes.size()==before,"missing player state cannot dispatch");
        port.view=view(12,false,false,all);controller.open(player);int closes=port.closed;
        controller.handle(player,port.token,Action.CLOSE);check(port.closed==closes+1,"close exits home");
        controller.open(player);online[0]=false;controller.handle(player,port.token,Action.RAID);online[0]=true;
        dead[0]=true;controller.handle(player,port.token,Action.RAID);check(port.routes.size()==before,"offline/dead players cannot dispatch");
        System.out.println("PdaHomeTest OK: chest slots, labels/status, hideout submenu/back, permissions, current-state recheck, one-use sessions, routing and cleanup");
    }
}
