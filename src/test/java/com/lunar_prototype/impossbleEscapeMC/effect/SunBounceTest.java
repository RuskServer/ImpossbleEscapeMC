package com.lunar_prototype.impossbleEscapeMC.effect;

import java.util.Arrays;

/** 実際の反射レイで入射面の向きと、途中の部分透過を検証する。 */
public final class SunBounceTest {
    private static final int N=12;
    private static SunShadowService.Snapshot wall() {
        byte[] transmit=new byte[N*N*N];Arrays.fill(transmit,(byte)255);
        int span=SunShadowService.SPAN_X*SunShadowService.SPAN_Y*SunShadowService.SPAN_Z;
        byte[] albedo=new byte[span];Arrays.fill(albedo,(byte)128);
        for(int y=0;y<N;y++)for(int z=0;z<N;z++)transmit[6+N*(z+N*y)]=0;
        return new SunShadowService.Snapshot(0,0,0,N,N,N,transmit,0,0,0,new byte[span],new byte[span],albedo,new byte[span],N-1);
    }
    private static double ray(SunShadowService.Snapshot snapshot,double[] sun,float block) {
        float[] direct=new float[N*N*N],lamp=new float[direct.length];Arrays.fill(direct,1);Arrays.fill(lamp,block);
        return SunShadowService.bounceRay(snapshot,4,4,4,new double[]{1,0,0},0,0,0,N,N,N,direct,lamp,sun);
    }
    public static void main(String[] args) {
        var snapshot=wall();double albedo=128.0/255;
        near(ray(snapshot,new double[]{-1,0,0},0),albedo,"sun facing the wall");
        near(ray(snapshot,new double[]{1,0,0},0),0,"sun behind the wall cannot reflect from the visible face");
        near(ray(snapshot,new double[]{0,1,0},0),0,"grazing sunlight contributes no direct bounce");
        near(ray(snapshot,new double[]{-Math.sqrt(.5),Math.sqrt(.5),0},0),albedo*Math.sqrt(.5),"oblique sunlight follows projected area");
        near(ray(snapshot,null,.4f),albedo*.4,"block light remains available at night");
        for(int y=0;y<N;y++)for(int z=0;z<N;z++)snapshot.transmit()[5+N*(z+N*y)]=(byte)128;
        near(ray(snapshot,new double[]{-1,0,0},0),albedo*128/255,"partial obstruction attenuates the return path");
        Arrays.fill(snapshot.albedo(),(byte)0);
        near(ray(snapshot,new double[]{-1,0,0},0),0,"absorbing surfaces do not create light");
        System.out.println("SunBounceTest OK");
    }
    private static void near(double actual,double expected,String reason){if(Math.abs(actual-expected)>1e-6)throw new AssertionError(reason+": "+actual+" != "+expected);}
}
