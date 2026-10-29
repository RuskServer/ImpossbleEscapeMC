package com.lunar_prototype.impossbleEscapeMC.loot;

import org.joml.Matrix4f;
import java.util.List;

/** locker_commandの14部品をそのまま使う。ドロワーと同じ床原点、前面は-Z。 */
public final class LockerModel {
    public static final int ANIMATION_TICKS=10;
    public record Part(boolean door,boolean slab,Matrix4f local) {}
    public static final List<Part> PARTS=List.of(
            part(false,false, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0625f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(false,false, 0.0625f, 0.0f, 0.0f, 0.0f, 0.0f, 1.9375f, 0.0f, 0.0625f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(false,false, 0.0625f, 0.0f, 0.0f, 0.9375f, 0.0f, 1.9375f, 0.0f, 0.0625f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(false,false, -0.0f, 0.0f, -0.875f, 0.9375f, 0.0f, 1.9375f, 0.0f, 0.0625f, 0.0625f, 0.0f, -0.0f, 0.9375f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.125f, 0.8849f, 0.0f, 0.4375f, 0.0f, 0.5625f, 0.0625f, 0.0f, 0.0f, -0.0397f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.125f, 0.9375f, 0.0f, 0.9375f, 0.0f, 1.0625f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.125f, 0.1875f, 0.0f, 0.9375f, 0.0f, 1.0625f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.625f, 0.8125f, 0.0f, 0.125f, 0.0f, 1.125f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.625f, 0.8125f, 0.0f, 0.125f, 0.0f, 1.3125f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.625f, 0.8125f, 0.0f, 0.125f, 0.0f, 1.5f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.625f, 0.8125f, 0.0f, 0.3125f, 0.0f, 1.6875f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(true,false, 0.0f, 0.0f, -0.875f, 0.9375f, 0.0f, 1.0f, 0.0f, 0.0625f, 0.0625f, 0.0f, 0.0f, -0.0f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(false,false, 0.875f, 0.0f, 0.0f, 0.0625f, 0.0f, 0.125f, 0.0f, 0.5625f, 0.0f, 0.0f, 0.8125f, 0.125f, 0.0f, 0.0f, 0.0f, 1.0f),
            part(false,true, 0.875f, 0.0f, 0.0f, 0.0625f, 0.0f, 0.5f, 0.0f, 2.0f, 0.0f, 0.0f, 0.875f, 0.0668f, 0.0f, 0.0f, 0.0f, 1.0f));
    private static Part part(boolean door,boolean slab,float... rowMajor) {
        return new Part(door,slab,new Matrix4f().set(rowMajor).transpose());
    }
    public static Matrix4f transform(Part part,int facing,boolean open) {
        Matrix4f matrix=new Matrix4f().rotateY((float)(Math.floorMod(facing,4)*Math.PI/2)).translate(-.5f,0,-.5f);
        // 前面左端の縦軸を蝶番とし、全ての扉部品を同じ軸で外へ開く。
        if(part.door()&&open)matrix.translate(.0625f,0,0).rotateY((float)Math.PI/2).translate(-.0625f,0,0);
        return matrix.mul(part.local());
    }
    public static int facing(float yaw){return DrawerModel.facing(yaw);}
    private LockerModel() {}
}
