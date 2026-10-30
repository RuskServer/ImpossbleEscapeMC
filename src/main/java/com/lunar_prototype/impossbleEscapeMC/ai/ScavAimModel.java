package com.lunar_prototype.impossbleEscapeMC.ai;

import org.joml.Vector3d;
import java.util.UUID;

/** 視認した位置だけを記憶し、個体固有の狙い違い・追い遅れを狙点として出す。 */
public final class ScavAimModel {
    private final ScavIndividualTraits traits;
    private UUID target;
    private Vector3d observed,tracked,velocity=new Vector3d();
    private double eyeHeight;
    private int observedTick,acquiredTick,lastUpdate;

    public ScavAimModel(ScavIndividualTraits traits) {this.traits=traits;}
    public void observe(UUID target,Vector3d feet,double height,int tick) {
        if(target==null||!feet.isFinite()||!Double.isFinite(height)||height<=0)return;
        int elapsed=tick-observedTick;
        boolean fresh=!target.equals(this.target)||observed==null||elapsed>12||elapsed<0||observed.distanceSquared(feet)>36;
        if(fresh) {
            this.target=target;acquiredTick=tick;tracked=new Vector3d(feet);velocity.zero();lastUpdate=tick;
        } else if(elapsed>0) {
            Vector3d measured=new Vector3d(feet).sub(observed).div(elapsed);
            if(measured.length()>.8)measured.normalize(.8);
            velocity.lerp(measured,.5);
        }
        observed=new Vector3d(feet);eyeHeight=height;observedTick=tick;
    }
    public Vector3d aimPoint(Vector3d shooter,int tick,double difficulty) {
        if(observed==null||!shooter.isFinite()||tick-observedTick>6||tick<observedTick)return null;
        double pressure=Math.clamp(Double.isFinite(difficulty)?difficulty:1,0,1);
        int dt=Math.clamp(tick-lastUpdate,0,6);
        Vector3d anticipated=new Vector3d(observed).fma(Math.min(3,tick-observedTick)+traits.predictionTicks(),velocity);
        tracked.lerp(anticipated,1-Math.exp(-dt/(traits.trackingTicks()*(1+pressure))));lastUpdate=tick;
        double dx=tracked.x-shooter.x,dz=tracked.z-shooter.z,flat=Math.hypot(dx,dz);
        Vector3d side=flat<1e-6?new Vector3d(1,0,0):new Vector3d(-dz/flat,0,dx/flat);
        double acquisition=Math.exp(-Math.max(0,tick-acquiredTick)/traits.correctionTicks());
        double range=1+Math.min(1.3,flat/35)*pressure;
        double horizontal=(traits.sideBias()+traits.acquisitionSide()*acquisition)*range
                +Math.sin(tick*.045+traits.phase())*(.025+.22*pressure);
        double vertical=traits.acquisitionHeight()*acquisition+Math.sin(tick*.031+traits.phase()*1.7)*(.01+.06*pressure);
        double height=Math.clamp(eyeHeight*traits.heightFraction()+vertical,eyeHeight*.2,eyeHeight*.95);
        return new Vector3d(tracked).fma(horizontal,side).add(0,height,0);
    }
}
