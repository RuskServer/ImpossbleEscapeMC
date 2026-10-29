package com.lunar_prototype.impossbleEscapeMC.effect;
public final class AdrenalineHeartbeatRecoveryTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int delay=1;delay<=10;delay++){
            var recovery=new AdrenalineHeartbeatRecovery(delay);
            int last=0,interval=0,count=0;
            float volume=1,pitch=1;
            for(int tick=1;tick<=80;tick++){
                var beat=recovery.tick();
                if(beat==null)continue;
                if(count==0)check(tick==delay,"initial beat preserves preceding cadence");
                else{
                    check(tick-last>=10&&tick-last<=20,"heart rate interval bounds");
                    check(tick-last>=interval,"heart rate slows monotonically");
                    interval=tick-last;
                }
                check(beat.volume()>0&&beat.volume()<volume,"volume fades monotonically");
                check(beat.pitch()<=pitch&&beat.pitch()>=0.85f,"pitch remains bounded");
                last=tick;volume=beat.volume();pitch=beat.pitch();count++;
            }
            check(count>=4&&count<=8&&volume<0.2f,"audible recovery has finite fading beats");
            check(recovery.finished(),"recovery ends at four seconds");
            for(int i=0;i<100;i++)check(recovery.tick()==null,"no beats after completion");
        }
        System.out.println("PASS: every onset phase, slowing cadence, fading volume and bounded recovery lifetime");
    }
}
