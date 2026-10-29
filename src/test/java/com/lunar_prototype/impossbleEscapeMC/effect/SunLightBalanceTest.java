package com.lunar_prototype.impossbleEscapeMC.effect;

public final class SunLightBalanceTest {
    public static void main(String[] args) {
        near(SunShadowService.mapReflectance(128,128,128),.21586,.00001,"gray map color is linearized before bounce");
        check(SunShadowService.mapReflectance(128,128,128)<128.0/255*.5,"gray no longer reflects more than twice its approximate energy");
        near(SunShadowService.mapReflectance(0,0,0),.02,.00001,"dark surface reflectance floor");
        near(SunShadowService.mapReflectance(255,255,255),.85,.00001,"white reflectance cap");
        check(SunShadowService.mapReflectance(0,255,0)>SunShadowService.mapReflectance(255,0,0),"linear luminance weights");
        for(int level=0;level<=12;level++)near(SunShadowService.skyFill(level,1),SunShadowService.SKY,1e-9,"indoor skylight retained");
        for(int level=0;level<=15;level++)near(SunShadowService.skyFill(level,0),SunShadowService.SKY,1e-9,"night sky coefficient retained");
        near(SunShadowService.skyFill(15,1),SunShadowService.OUTDOOR_SKY,1e-9,"clear outdoor sky fill");
        check(SunShadowService.skyFill(15,.25)>SunShadowService.skyFill(15,1),"overcast retains diffuse sky fill");
        double previous=SunShadowService.SKY;
        for(int level=12;level<=15;level++) {
            double fill=SunShadowService.skyFill(level,1);check(fill<=previous&&fill>=SunShadowService.OUTDOOR_SKY,"bounded gradual exposure transition");previous=fill;
        }
        check(SunShadowService.SUN/SunShadowService.OUTDOOR_SKY>=2,"direct sunlight is distinct from outdoor fill");
        double diagonal=Math.sqrt(.5);
        var daylight=new SunShadowService.Light(java.util.UUID.randomUUID(),new double[]{diagonal,diagonal,0},1,1);
        var outdoor=SunShadowService.computeLight(SunShadowServiceTest.wall(0,15),0,0,0,daylight);
        int lit=SunShadowServiceTest.level(outdoor,35,1,10),shade=SunShadowServiceTest.level(outdoor,25,1,10);
        check(lit>=6,"outdoor sunlight stays bright");
        check(shade>0&&shade<SunShadowService.LEVEL_ONE,"shadow retains headroom below it instead of clipping to minimum code");
        check(SunShadowService.levelFactor(lit)/SunShadowService.levelFactor(shade)>3,"outdoor contrast survives packed grid quantization");
        var indoor=SunShadowService.computeLight(SunShadowServiceTest.wall(0,5),0,0,0,daylight);
        check(SunShadowServiceTest.level(indoor,25,1,10)>=3,"indoor shadow remains readable");
        System.out.println("SunLightBalanceTest OK");
    }
    private static void near(double actual,double expected,double tolerance,String message){check(Math.abs(actual-expected)<tolerance,message+": "+actual);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
