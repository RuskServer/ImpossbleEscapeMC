package com.lunar_prototype.impossbleEscapeMC.loot;

import org.joml.Matrix4f;

/** drawer_command の15部品。元コマンドと同じ1ブロック内の座標。前面は-Z。 */
public final class DrawerModel {
    public static final int ANIMATION_TICKS = 8;
    public static final float TRAVEL = .625f;
    public record Part(float x,float y,float z,float sx,float sy,float sz,int drawer) {}
    public static final java.util.List<Part> PARTS=java.util.List.of(
            new Part(0,0,0,1,.0625f,1,-1),
            new Part(0,.9375f,0,1,.0625f,1,-1),
            new Part(0,.0625f,0,.0625f,.875f,1,-1),
            new Part(.9375f,.0625f,0,.0625f,.875f,1,-1),
            new Part(.0625f,.0625f,.9375f,.875f,.875f,.0625f,-1),
            new Part(.0625f,.0625f,0,.875f,.0625f,1,0),
            new Part(.0625f,.125f,0,.0625f,.375f,1,0),
            new Part(.875f,.125f,0,.0625f,.375f,1,0),
            new Part(.125f,.125f,0,.75f,.3125f,.0625f,0),
            new Part(.2468f,.1875f,-.0514f,.4755f,.1101f,.062f,0),
            new Part(.0625f,.5f,0,.875f,.0625f,1,1),
            new Part(.0625f,.5625f,0,.0625f,.375f,1,1),
            new Part(.875f,.5625f,0,.0625f,.375f,1,1),
            new Part(.125f,.5625f,0,.75f,.3125f,.0625f,1),
            new Part(.2468f,.6271f,-.0514f,.4755f,.1101f,.062f,1));

    public static Matrix4f transform(Part part,int facing,boolean open) {
        return new Matrix4f().rotateY((float)(Math.floorMod(facing,4)*Math.PI/2))
                .translate(part.x()-.5f,part.y(),part.z()-.5f-(open?TRAVEL:0))
                .scale(part.sx(),part.sy(),part.sz());
    }
    /** Minecraftのyawで、設置者の正面に取っ手を向ける。 */
    public static int facing(float yaw){return Math.floorMod(-Math.round(yaw/90),4);}
    private DrawerModel() {}
}
