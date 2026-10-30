package com.lunar_prototype.impossbleEscapeMC.ai;

/** データパックが引き金を処理する間の照準。見た目と撃ち手が同じ値を読む。 */
public final class ScavShotAim {
    public record Rotation(float yaw,float pitch) {}
    private Rotation rotation;
    private int remaining;
    public void capture(float yaw,float pitch,int ticks) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)){clear();return;}
        rotation=new Rotation(yaw,Math.clamp(pitch,-90,90));remaining=Math.max(0,ticks);
    }
    public Rotation held() {return remaining>0?rotation:null;}
    public void tick() {if(remaining>0)remaining--;}
    public void clear() {remaining=0;rotation=null;}
}
