package com.lunar_prototype.impossbleEscapeMC.ai;

import org.joml.Vector3d;
import java.util.UUID;

public final class ScavIndividualAimTest {
    private static final UUID TARGET=new UUID(1,2);
    private static final Vector3d EYE=new Vector3d(0,1.62,0),FEET=new Vector3d(10,0,0);
    private static ScavIndividualTraits traits(double tracking,double acquisition) {
        return new ScavIndividualTraits(.65,.1,acquisition,.12,20,tracking,0,0,1,1,.1,1.1);
    }
    public static void main(String[] args) {
        for(int i=0;i<1000;i++) {
            UUID id=new UUID(i,i*71L);
            var low=ScavIndividualTraits.create(id,0);var high=ScavIndividualTraits.create(id,2);
            check(low.equals(ScavIndividualTraits.create(id,0)),"traits must not reroll");
            check(low.trackingTicks()>high.trackingTicks(),"rank bounds preserve tracking difference");
            check(low.heightFraction()>=.48&&low.heightFraction()<=.78,"body aim height");
            check(low.pauseChance()>=.04&&low.pauseChance()<=.18&&low.strafeDuration()>=.8&&low.strafeDuration()<=1.45,"movement bounds");
            check(!low.equals(ScavIndividualTraits.create(new UUID(i+1,i*71L),0)),"different individuals");
        }
        var model=new ScavAimModel(traits(5,.6));
        model.observe(TARGET,FEET,1.62,0);
        Vector3d first=model.aimPoint(EYE,0,0),previous=first;
        check(first.y<1.62&&first.y>0,"aim body rather than exact eyes");
        check(first.equals(model.aimPoint(EYE,0,0)),"same tick must not resample aim");
        Vector3d last=first;
        for(int tick=1;tick<=120;tick++) {
            if(tick%3==0)model.observe(TARGET,FEET,1.62,tick);
            last=model.aimPoint(EYE,tick,0);
            check(last.isFinite()&&last.distance(previous)<.05,"continuous correction without shot jitter");
            previous=last;
        }
        check(Math.abs(last.z-.1)<.04&&Math.abs(first.z-.1)>.5,"acquisition error corrects instead of missing forever");
        check(model.aimPoint(EYE,127,0)==null,"unseen target cannot be tracked indefinitely");
        model.observe(new UUID(9,9),new Vector3d(-10,0,0),1.62,128);
        check(model.aimPoint(EYE,128,0).x<-9,"target switch clears old tracked position and velocity");
        model.observe(new UUID(9,9),new Vector3d(40,0,0),1.62,131);
        check(model.aimPoint(EYE,131,0).x>39,"teleport cannot become an extreme velocity lead");
        var slow=new ScavAimModel(traits(8,0));var fast=new ScavAimModel(traits(2,0));
        Vector3d slowPoint=null,fastPoint=null;
        for(int tick=0;tick<=81;tick++) {
            Vector3d observed=new Vector3d(10,0,tick*.15);
            if(tick%3==0){slow.observe(TARGET,observed,1.62,tick);fast.observe(TARGET,observed,1.62,tick);}
            slowPoint=slow.aimPoint(EYE,tick,0);fastPoint=fast.aimPoint(EYE,tick,0);
        }
        check(fastPoint.z-slowPoint.z>.5,"tracking personality produces persistent movement lag");
        var stressed=new ScavAimModel(ScavIndividualTraits.create(new UUID(32,73),0));
        for(int tick=0;tick<100;tick++) {
            stressed.observe(TARGET,FEET,1.62,tick);
            Vector3d point=stressed.aimPoint(EYE,tick,1);
            check(point.isFinite()&&point.y>.3&&point.y<1.7&&Math.abs(point.z)<1.5,"stress stays near the body rather than sky/ground");
        }
        stressed.observe(TARGET,FEET,.4,100);
        Vector3d crawling=stressed.aimPoint(EYE,100,1);
        check(crawling.y>=.08&&crawling.y<=.38,"short poses retain body-height bounds");
        System.out.println("ScavIndividualAimTest OK: stable traits, body aim, correction, tracking lag, observation expiry and bounded stress");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
