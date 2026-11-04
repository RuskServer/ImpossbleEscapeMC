package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 出撃先・同行者の同意を、古い画面や状態変更後に流用できないことを検証する。 */
public final class RaidDeploymentApprovalTest {
    private static void check(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static final UUID LEADER = UUID.randomUUID(), MEMBER = UUID.randomUUID();
    private static final RaidConditions DAY = new RaidConditions(RaidConditions.Period.DAY, 6000, RaidConditions.Weather.CLEAR);
    private static final RaidConditions NIGHT = new RaidConditions(RaidConditions.Period.NIGHT, 18000, RaidConditions.Weather.RAIN);
    private static RaidDeploymentApproval.Snapshot snapshot(String map, RaidDeploymentApproval.Mode mode, long cycle,
            RaidConditions conditions, UUID leader, List<UUID> members, List<UUID> online, String queued) {
        return new RaidDeploymentApproval.Snapshot(map,mode,cycle,conditions,leader,members,online,queued);
    }
    private static boolean hover(Component component) {
        return component.hoverEvent()!=null || component.children().stream().anyMatch(RaidDeploymentApprovalTest::hover);
    }
    public static void main(String[] args) {
        var mode=RaidDeploymentApproval.Mode.IMMEDIATE;
        var group=List.of(LEADER,MEMBER);
        var original=snapshot("薬品工場",mode,100,DAY,LEADER,group,group,null);
        var approval=new RaidDeploymentApproval(original,500);
        check(approval.matches(approval.id()),"displayed confirmation starts unconsumed");
        check(approval.accept("stale",original,501)==RaidDeploymentApproval.Result.INVALID,"old or forged dialog cannot approve");
        check(approval.matches(approval.id()),"wrong screen does not consume current approval");
        check(approval.accept(approval.id(),original,501)==RaidDeploymentApproval.Result.ACCEPTED,"unchanged shown plan can proceed");
        check(approval.accept(approval.id(),original,502)==RaidDeploymentApproval.Result.INVALID,"double click cannot deploy twice");
        var replacement=new RaidDeploymentApproval(original,501);
        check(replacement.accept(approval.id(),original,502)==RaidDeploymentApproval.Result.INVALID,"new dialog rejects prior approval");
        var changed=List.of(
                snapshot("沿岸工業地区",mode,100,DAY,LEADER,group,group,null),
                snapshot("薬品工場",RaidDeploymentApproval.Mode.LATE_JOIN,100,DAY,LEADER,group,group,null),
                snapshot("薬品工場",RaidDeploymentApproval.Mode.QUEUE,100,DAY,LEADER,group,group,null),
                snapshot("薬品工場",mode,101,DAY,LEADER,group,group,null),
                snapshot("薬品工場",mode,100,NIGHT,LEADER,group,group,null),
                snapshot("薬品工場",mode,100,DAY,MEMBER,group,group,null),
                snapshot("薬品工場",mode,100,DAY,LEADER,List.of(LEADER),List.of(LEADER),null),
                snapshot("薬品工場",mode,100,DAY,LEADER,group,List.of(LEADER),null),
                snapshot("薬品工場",mode,100,DAY,null,List.of(LEADER),List.of(LEADER),null),
                snapshot("薬品工場",mode,100,DAY,LEADER,group,group,"沿岸工業地区"));
        for(var current:changed) {
            var request=new RaidDeploymentApproval(original,500);
            check(request.accept(request.id(),current,501)==RaidDeploymentApproval.Result.CHANGED,"changed destination, mode, cycle, weather, leader, membership, online group or queue requires fresh agreement");
            check(request.accept(request.id(),original,502)==RaidDeploymentApproval.Result.INVALID,"changed screen's old agreement cannot be revived");
        }
        var missing=new RaidDeploymentApproval(original,500);
        check(missing.accept(missing.id(),null,501)==RaidDeploymentApproval.Result.CHANGED,"deleted map or lost eligibility cannot approve");
        var expired=new RaidDeploymentApproval(original,500);
        check(expired.expired(500+RaidDeploymentApproval.LIFETIME_TICKS),"timeout includes exact boundary");
        check(expired.accept(expired.id(),original,500+RaidDeploymentApproval.LIFETIME_TICKS)==RaidDeploymentApproval.Result.INVALID,"expired dialog cannot deploy");
        var justInTime=new RaidDeploymentApproval(original,Integer.MAX_VALUE-100);
        check(justInTime.accept(justInTime.id(),original,Integer.MIN_VALUE+100)==RaidDeploymentApproval.Result.ACCEPTED,"short lifetime survives tick counter wrap");
        var mutable=new ArrayList<>(group);
        var copied=snapshot("薬品工場",mode,100,DAY,LEADER,mutable,mutable,null);mutable.clear();
        check(copied.partyMembers().equals(group)&&copied.onlineMembers().equals(group),"displayed membership is immutable");
        var plain=PlainTextComponentSerializer.plainText();
        for(var m:RaidDeploymentApproval.Mode.values()) {
            var view=snapshot("ノヴォザリエ薬品工場",m,100,DAY,LEADER,group,group,null);
            var lines=RaidSelectionGUI.confirmationLines(view,List.of("Leader","Member"),90);
            String text=String.join("\n",lines.stream().map(plain::serialize).toList());
            check(text.contains(view.mapId())&&text.contains(DAY.describe())&&text.contains("Leader")&&text.contains("Member")&&text.contains("2名"),"destination, conditions and participants are readable without hover");
            check(lines.stream().noneMatch(RaidDeploymentApprovalTest::hover),"essential confirmation information has no hover requirement");
            check(text.contains(switch(m){case IMMEDIATE->"確認後すぐ";case LATE_JOIN->"途中参加";case QUEUE->"01:30";}),"immediate, late and queued participation are distinguished");
        }
        System.out.println("RaidDeploymentApprovalTest OK: one-use agreement, stale/expired screens, changed destination/party/conditions, immutable membership and visible summary");
    }
}
