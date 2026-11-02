package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 素材の4秒/0.5秒フェードに対する、実際の再生状態とイベント停止範囲を検証する。 */
public final class AmbiencePlaybackTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private record Segment(String bed,int start) { }
    private static double envelope(int age) {
        if(age<0||age>=80)return 0;
        if(age<10)return Math.pow(Math.sin(age/10.0*Math.PI/2),2);
        if(age>70)return Math.pow(Math.sin((80-age)/10.0*Math.PI/2),2);
        return 1;
    }
    public static void main(String[] args) {
        var state=new AmbienceService.State();
        check("outdoor_day".equals(state.next("outdoor_day",0)),"first bed starts immediately");
        check(state.next("indoor_day",10)==null&&state.next("indoor_day",20)==null,"confirmed zone change does not stack a new 4-second segment midway");
        check(state.next("indoor_day",60)==null,"wait for outgoing fade");
        check("indoor_day".equals(state.next("indoor_day",70)),"switch at outgoing tail fade");
        check("outdoor_day".equals(state.previousBed),"old fading bed remains tracked for cleanup");
        var boundary=new AmbienceService.State();boundary.next("hall",0);
        check("hall".equals(boundary.next("underground",70)),"one boundary sample cannot bypass confirmation");
        check(boundary.next("hall",80)==null,"brief doorway oscillation does not trigger another segment");
        var lagged=new AmbienceService.State();lagged.next("hall",0);
        check("hall".equals(lagged.next("hall",200))&&lagged.next("hall",200)==null,"late update starts once, without catch-up stacking");
        var stress=new AmbienceService.State();var segments=new ArrayList<Segment>();int last=-70;
        String[] beds={"outdoor_day","outdoor_night","indoor_day","indoor_night","hall","underground"};
        for(int now=0;now<=2800;now++) {
            if(now%10==0) {
                String wanted=beds[(now/30)%beds.length];String next=stress.next(wanted,now);
                if(next!=null) {check(now-last>=70,"no early segments under repeated changes");segments.add(new Segment(next,now));last=now;}
            }
            int active=0;double gain=0;
            for(var segment:segments) {
                int age=now-segment.start();
                if(age>=0&&age<80)active++;
                gain+=envelope(age)*AmbienceService.volume(segment.bed());
            }
            check(active<=2,"at most one fading tail and one new bed");
            check(gain<=.500001,"crossfade envelope does not boost normalized background gain");
        }
        List<SoundStop> stops=new ArrayList<>();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
            if(m.getName().equals("stopSound")&&a[0] instanceof SoundStop sound){stops.add(sound);return null;}
            throw new AssertionError("unrelated player operation: "+m.getName());
        });
        state.stop(player);
        check(stops.size()==2,"exit cleans both current and previous fade");
        for(var stop:stops)check(stop.source()==Sound.Source.AMBIENT&&stop.sound().asString().startsWith("iem:ambient."),"stop only owned ambience events, preserving weather/end-sequence/SFX");
        stops.clear();state.next("indoor_day",140);state.stop(player);
        check(stops.size()==1,"same-bed overlap uses one exact stop event");
        for(String bed:beds)check(AmbienceService.volume(bed)>0&&AmbienceService.volume(bed)<=.5,"all beds quieter and retained");
        check(AmbienceService.volume("underground")<AmbienceService.volume("hall")&&AmbienceService.volume("outdoor_day")<AmbienceService.volume("indoor_day"),"prominent beds receive additional reduction");
        System.out.println("AmbiencePlaybackTest OK: volume balance, confirmed boundary switching, overlap envelope, lag, rapid changes and scoped exit cleanup");
    }
}
