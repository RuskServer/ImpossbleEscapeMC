package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidBossAlertHudTest {
    @Test void everyFrameUsesSixCorrectTilesWithoutChangingNormalHud() {
        var base=RaidHud.compose(20,List.of(),List.of(),345,OperationHintHud.MAP);
        for(int f=0;f<212;f++) {
            var title=RaidHud.compose(20,List.of(),List.of(),345,OperationHintHud.MAP,-1,null,f,"ヴォロノフ部隊");
            for(int i=0;i<base.children().size()-1;i++)assertEquals(base.children().get(i),title.children().get(i));
            List<Component> tiles=title.children().stream().filter(c->Key.key("iem","boss_alert").equals(c.font())).toList();
            assertEquals(6,tiles.size());
            for(int i=0;i<6;i++) {
                assertEquals(String.valueOf(RaidBossAlertFrames.glyph(f,i)),((TextComponent)tiles.get(i)).content());
                assertEquals(i/3,(tiles.get(i).color().value()>>8)&255);
            }
            long names=title.children().stream().filter(c->c.color()!=null&&(c.color().value()&0xff)==239).count();
            assertEquals(f>=30?1:0,names);
        }
    }
    @Test void outOfRangeFramesDoNotLeaveOverlayGlyphs() {
        var base=RaidHud.compose(0,List.of(),List.of(),0);
        assertEquals(base,RaidHud.compose(0,List.of(),List.of(),0,null,-1,null,-1,"Boss"));
        assertEquals(base,RaidHud.compose(0,List.of(),List.of(),0,null,-1,null,212,"Boss"));
    }
}
