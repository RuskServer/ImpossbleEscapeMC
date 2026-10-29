package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import java.util.List;
import java.util.Map;

public final class RaidRadioHudTest {
    private static final Key HUD=Key.key("iem","hud"),ALERT=Key.key("iem","radio_alert");
    private static final Map<Character,Integer> WIDTH=Map.ofEntries(
            Map.entry('',181),Map.entry('',1),Map.entry('',5),Map.entry('',5),Map.entry('',5),Map.entry('',5),
            Map.entry('',8),Map.entry('',7),Map.entry('',9),Map.entry('',10),Map.entry('',3),
            Map.entry(OperationHintHud.MAP.glyph(),OperationHintHud.MAP.width()));
    public static void main(String[] args) {
        for(int frame=0;frame<12;frame++) {
            Component base=RaidHud.compose(20,List.of(),List.of(),345,OperationHintHud.MAP);
            Component title=RaidHud.compose(20,List.of(),List.of(),345,OperationHintHud.MAP,frame);
            for(int i=0;i<base.children().size()-1;i++)check(base.children().get(i).equals(title.children().get(i)),"compass, timer, and operation hint stay unchanged");
            int cursor=0,alerts=0;
            for(Component part:title.children()) {
                String text=((TextComponent)part).content();
                for(char c:text.toCharArray()) {
                    if(ALERT.equals(part.font())) {
                        check(cursor==(alerts==0?-20:90),"alert halves meet at compass center");
                        check(c==(alerts==0?0xE000:0xE020)+frame,"correct animation glyph");
                        cursor+=111;alerts++;
                    } else if(HUD.equals(part.font())&&c>=0xE500&&c<=0xE508)cursor+=1<<(c-0xE500);
                    else if(HUD.equals(part.font())&&c>=0xE510&&c<=0xE518)cursor-=1<<(c-0xE510);
                    else if(HUD.equals(part.font()))cursor+=WIDTH.get(c)+1;
                    else cursor+=c==':'?2:6;
                }
            }
            check(alerts==2&&cursor==RaidHud.STRIP,"overlay does not change the bossbar title's centering width");
        }
        check(RaidHud.compose(0,List.of(),List.of(),10,null,-1).equals(RaidHud.compose(0,List.of(),List.of(),10)),"expired alert leaves no glyph");
        System.out.println("RaidRadioHudTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
