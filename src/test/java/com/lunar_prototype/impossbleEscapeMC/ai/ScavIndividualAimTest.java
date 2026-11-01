package com.lunar_prototype.impossbleEscapeMC.ai;

import org.joml.Vector3d;
import java.util.UUID;
import java.util.Random;

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
        // 最小値付近へクランプして山を作らず、左右両側の範囲を連続して使う。
        Random population=new Random(0x5CAFL);
        int[] positive=new int[3],atFloor=new int[3];double[] sums=new double[3];
        double[] minimum={.06,.03,0},maximum={.16,.12,.08};
        for(int i=0;i<10000;i++) {
            UUID id=new UUID(population.nextLong(),population.nextLong());
            for(int rank=0;rank<3;rank++) {
                var individual=ScavIndividualTraits.create(id,rank);double side=individual.sideBias(),magnitude=Math.abs(side);
                check(magnitude>=minimum[rank]&&magnitude<=maximum[rank],"persistent side-bias bounds by rank");
                check(individual.equals(ScavIndividualTraits.create(id,rank)),"bias and other traits remain deterministic");
                if(side>0)positive[rank]++;if(magnitude==minimum[rank])atFloor[rank]++;sums[rank]+=magnitude;
            }
        }
        for(int rank=0;rank<3;rank++) {
            check(positive[rank]>4500&&positive[rank]<5500,"left/right preference stays balanced");
            check(atFloor[rank]<10,"minimum is not a clamp that piles up individuals at one value");
            double mean=sums[rank]/10000,range=maximum[rank]-minimum[rank];
            check(mean>minimum[rank]+range*.45&&mean<minimum[rank]+range*.55,"magnitudes use the whole intended interval");
            // 修正前に記録した同一UUIDの値。追加抽選で他の性格をずらさない。
            var reference=ScavIndividualTraits.create(new UUID(123,456),rank);
            check(reference.phase()==2.8815302322284206&&reference.predictionTicks()==1.9904194932016381
                    &&reference.strafeDuration()==1.3411072619333118&&reference.orbit()==.9914874291076246
                    &&reference.pauseChance()==.1457665697958506&&reference.peekRest()==1.3181752237185074,
                    "non-bias personality draws must remain unchanged");
        }
        check(ScavIndividualTraits.create(new UUID(123,456),2).sideBias()==-.06521582632976608,"HIGH bias distribution remains unchanged");
        // 下限は固定の寄りだけ。揺れとの相殺を禁止するような毎発の最低誤差は作らない。
        double pressure=.2,range=1+10./35*pressure;
        double phase=Math.asin(-.06*range/(.025+.22*pressure));
        var cancellable=new ScavAimModel(new ScavIndividualTraits(.65,.06,0,0,20,5,0,phase,1,1,.1,1));
        cancellable.observe(TARGET,FEET,1.62,0);
        check(Math.abs(cancellable.aimPoint(EYE,0,pressure).z)<1e-12,"continuous sway can still cross the intended point");
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
        System.out.println("ScavIndividualAimTest OK: 30000 bias bounds/distributions, stable personality, sway cancellation, body aim, correction, tracking and expiry");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
