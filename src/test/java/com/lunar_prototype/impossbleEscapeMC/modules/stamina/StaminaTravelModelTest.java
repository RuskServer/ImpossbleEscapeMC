package com.lunar_prototype.impossbleEscapeMC.modules.stamina;

public final class StaminaTravelModelTest {
    public static void main(String[] args) {
        var normal=StaminaModule.diagnosticTravel(20000,20,5,5.6,4.3);
        near(normal.seconds(0),0);
        near(normal.seconds(80),80/5.6);
        check(normal.seconds(250)>250/5.6,"long sprint must include walking recovery");
        check(StaminaModule.diagnosticTravel(30000,20,5,5.6,4.3).seconds(500)>normal.seconds(500),"heavier cost and delay");
        check(StaminaModule.diagnosticTravel(20000,5,5,5.6,4.3).seconds(1000)>normal.seconds(1000),"food recovery penalty");
        var critical=StaminaModule.diagnosticTravel(35000,20,5,5.6,4.3);
        check(!critical.canSprint(),"sprinting blocked at 35kg");near(critical.seconds(250),250/4.3);
        var overloaded=StaminaModule.diagnosticTravel(60000,20,5,5.6,4.3);
        near(overloaded.seconds(100),100/(4.3*.25));
        double previous=0;
        for(double d=.5;d<2000;d+=.5) {
            double t=normal.seconds(d);
            check(Double.isFinite(t)&&t>=previous&&t>=d/5.6-1e-8,"monotonic pacing across cycle boundaries");previous=t;
        }
        boolean invalid=false;
        try {new StaminaTravelModel(100,4,12,1,100,5.6,4.3,true);}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"invalid reserve rejected");
        System.out.println("StaminaTravelModelTest OK");
    }
    private static void near(double actual,double expected){check(Math.abs(actual-expected)<1e-6,actual+" != "+expected);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
