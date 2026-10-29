package com.lunar_prototype.impossbleEscapeMC.loot;

import org.joml.Matrix4f;

/** パックの固定部・扉はそれぞれ中心と蝶番を原点にしている。前面は-Z。 */
public final class LockerModel {
    public static final int ANIMATION_TICKS=10;
    public static Matrix4f transform(boolean door,int facing,boolean open) {
        Matrix4f matrix=new Matrix4f().rotateY((float)(Math.floorMod(facing,4)*Math.PI/2));
        if(door)matrix.translate(-.4375f,1.125f,-.46875f).rotateY(open?(float)Math.PI/2:0);
        else matrix.translate(0,1.125f,0);
        return matrix;
    }
    public static int facing(float yaw){return DrawerModel.facing(yaw);}
    private LockerModel() {}
}
