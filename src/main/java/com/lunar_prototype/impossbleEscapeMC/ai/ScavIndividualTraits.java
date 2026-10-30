package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.Random;
import java.util.UUID;

/** 生存中は変わらない癖。ランク内にも連続した差を持たせ、射撃ごとに抽選しない。 */
public record ScavIndividualTraits(double heightFraction,double sideBias,double acquisitionSide,double acquisitionHeight,
        double correctionTicks,double trackingTicks,double predictionTicks,double phase,
        double strafeDuration,double orbit,double pauseChance,double peekRest) {
    public static ScavIndividualTraits create(UUID id,int rank) {
        Random r=new Random(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),23));
        int level=Math.clamp(rank,0,2);
        double height=(level==0?.48:level==1?.55:.66)+r.nextDouble()*(level==2?.24:.30);
        double error=level==0?.55:level==1?.38:.22;
        return new ScavIndividualTraits(height,signed(r)*(level==0?.16:level==1?.12:.08),signed(r)*error,signed(r)*error*.3,
                (level==0?18:level==1?12:8)+r.nextDouble()*16,
                (level==0?5:level==1?3:1.8)+r.nextDouble()*(level==0?4:level==1?4:2.4),
                r.nextDouble()*3,r.nextDouble()*Math.PI*2,
                .8+r.nextDouble()*.65,.7+r.nextDouble()*.5,.04+r.nextDouble()*.14,.9+r.nextDouble()*.5);
    }
    private static double signed(Random r) {return r.nextDouble()*2-1;}
}
