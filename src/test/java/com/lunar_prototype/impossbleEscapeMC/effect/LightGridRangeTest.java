package com.lunar_prototype.impossbleEscapeMC.effect;

/** 広い格子の中央へのフォールバックで値・原点・光種別・容量を保つ。 */
public final class LightGridRangeTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        int w = 64, h = 16;
        int[] near = new int[2*((w*h*w+6)/7)], kinds = new int[(32*8*32+6)/7];
        for (int y=0;y<h;y++) for(int z=0;z<w;z++) for(int x=0;x<w;x++) {
            int i=x+w*(z+w*y), value=(x+3*z+5*y)&15, bit=i%7*4;
            near[2*(i/7)] |= (value<<bit)&16383;
            if(bit>=14) near[2*(i/7)+1] |= value<<(bit-14);
            else if(bit+4>14) near[2*(i/7)+1] |= value>>>(14-bit);
        }
        for(int y=0;y<8;y++) for(int z=0;z<32;z++) for(int x=0;x<32;x++) {
            int i=x+32*(z+32*y);kinds[i/7] |= ((x+2*z+3*y)&3)<<(i%7*2);
        }
        var grid=new SunShadowService.Grids(near,kinds,w);
        var fallback=SunShadowService.selectPayload(grid,new int[SunShadowService.DATA_CAPACITY],1936);
        check(!fallback.compressed() && fallback.grids().width()==48 && fallback.shift()==8,"central legacy fallback");
        check(fallback.light().length+fallback.grids().kinds().length+1936+23<=16384,"fallback capacity");
        int[] cropped=fallback.light(), croppedKinds=fallback.grids().kinds();
        for(int y=0;y<h;y++) for(int z=0;z<48;z++) for(int x=0;x<48;x++) {
            int i=x+48*(z+48*y);
            int value=((cropped[2*(i/7)]|cropped[2*(i/7)+1]<<14)>>>(i%7*4))&15;
            check(value==((x+8+3*(z+8)+5*y)&15),"cropped near data");
        }
        for(int y=0;y<8;y++) for(int z=0;z<24;z++) for(int x=0;x<24;x++) {
            int i=x+24*(z+24*y);
            check(((croppedKinds[i/7]>>>(i%7*2))&3)==((x+4+2*(z+4)+3*y)&3),"cropped kind data");
        }
        int budget=16384-23-kinds.length-1936;
        var exact=SunShadowService.selectPayload(grid,new int[budget],1936);
        check(exact.compressed() && exact.shift()==0 && exact.grids().width()==64,"exact capacity accepted");
        check(!SunShadowService.selectPayload(grid,new int[budget+1],1936).compressed(),"one word overflow rejected");
        check(!SunShadowService.selectPayload(grid,null,1936).compressed(),"missing compression falls back");
        var old=SunShadowService.cropGrid(grid,48);
        check(SunShadowService.selectPayload(old,null,1936).shift()==0,"legacy origin unchanged");
        System.out.println("PASS: 64 grid capacity boundary, 48 central fallback, every near/kind cell and origin shift");
    }
}
