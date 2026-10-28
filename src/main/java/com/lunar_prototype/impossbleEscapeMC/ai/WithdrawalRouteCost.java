package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.List;

/** 撤退は「遠い遮蔽」より「早く射線を切れる経路」を優先する。距離はm。 */
final class WithdrawalRouteCost {
    record Sample(double distance, boolean exposed, double danger, double threatDistance) {}
    static double score(List<Sample> samples, double startRange, int exits) {
        double prefix=0,exposure=0,danger=0,closing=0,reexposure=0;
        boolean reachedCover=false;
        double previous=0;
        for(Sample s:samples) {
            double segment=Math.max(0,s.distance-previous);previous=s.distance;
            if(s.exposed) {
                exposure+=segment;
                if(!reachedCover)prefix=s.distance;
                else reexposure+=segment;
            } else reachedCover=true;
            danger+=Math.min(5,Math.max(0,s.danger))*segment;
            closing=Math.max(closing,startRange-s.threatDistance);
        }
        return -(prefix*1.8+exposure*.8+reexposure*1.2+previous*.12+danger*.3+Math.max(0,closing-1)*.8
                +(exits<=1?2.5:0));
    }
    /** 経路の残距離で進捗を見る。壁を回って敵から離れる動きも停止扱いにしない。 */
    static final class Progress {
        private final int started;
        private int lastProgress;
        private double best=Double.POSITIVE_INFINITY;
        Progress(int tick){started=lastProgress=tick;}
        boolean stuck(int tick,double remaining) {
            if(remaining<best-.3){best=remaining;lastProgress=tick;}
            return tick-lastProgress>=24 || tick-started>=160;
        }
    }
}
