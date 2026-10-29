package com.lunar_prototype.impossbleEscapeMC.modules.inventory;
import java.util.Arrays;
public final class CostInsertionTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(Arrays.equals(CostInsertion.plan(8,8,4,10,64,new int[]{0,0},new boolean[]{true,true}),new int[]{0,0}),"empty physical slots cannot bypass full cost");
        check(Arrays.equals(CostInsertion.plan(8,8,4,10,64,new int[]{6,0},new boolean[]{false,true}),new int[]{6,0}),"existing stack accepts partial; rest stays at source");
        check(Arrays.equals(CostInsertion.plan(8,5,4,10,64,new int[]{0},new boolean[]{true}),new int[]{0}),"one costly stack must fit fully");
        check(Arrays.equals(CostInsertion.plan(8,4,4,70,64,new int[]{0,0},new boolean[]{true,true}),new int[]{64,0}),"only one new stack fits");
        check(Arrays.equals(CostInsertion.plan(8,12,4,10,64,new int[]{12,0},new boolean[]{false,true}),new int[]{10,0}),"existing over-capacity can stack but cannot grow cost");
        check(Arrays.equals(CostInsertion.plan(8,2,3,70,64,new int[]{0,0},new boolean[]{true,true}),new int[]{64,6}),"exact cost capacity");
        System.out.println("PASS: cost full/partial/exact/over-capacity insertion without overflow or source loss");
    }
}
