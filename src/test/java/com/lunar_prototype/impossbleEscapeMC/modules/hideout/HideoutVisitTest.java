package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.party.Party;

import java.util.UUID;

/** パーティーで隠れ家に入る時の案内先 ({@link HideoutModule#visitHost}) と、座標から区画を求める計算を確かめる */
public final class HideoutVisitTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Party party = new Party(leader);
        party.addMember(member);

        // メンバーはリーダーの隠れ家に案内され、リーダー自身と部外者は自分の隠れ家
        check(leader.equals(HideoutModule.visitHost(party, member)), "member is guided to the leader's hideout");
        check(HideoutModule.visitHost(party, leader) == null, "the leader visits own hideout");
        check(HideoutModule.visitHost(null, member) == null, "a player without a party visits own hideout");
        check(HideoutModule.visitHost(party, outsider) == null, "a non-member visits own hideout");

        // 区画は 1000 ブロック間隔で、x の小さい方から数える。負の座標も左の区画になる
        check(HideoutModule.slotAt(0) == 0 && HideoutModule.slotAt(999) == 0, "slot 0 spans x 0-999");
        check(HideoutModule.slotAt(1000) == 1 && HideoutModule.slotAt(1999) == 1, "slot 1 spans x 1000-1999");
        check(HideoutModule.slotAt(-1) == -1, "negative x belongs to the slot on the left");

        System.out.println("HideoutVisitTest OK: party members are guided to the leader's hideout and slots are derived from x");
    }
}
