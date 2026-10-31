package com.lunar_prototype.impossbleEscapeMC.ai;

/** ボス部隊の持ち場。ワールドの検証はController側で行う。 */
public record ScavBossPost(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                           double x,double y,double z,double radius,boolean leader,java.util.UUID unitId) {
    public ScavBossPost {
        java.util.Objects.requireNonNull(unitId);
        for(double v:new double[]{minX,minY,minZ,maxX,maxY,maxZ,x,y,z,radius})
            if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid boss territory");
        if(minX>=maxX||minY>=maxY||minZ>=maxZ||radius<=0)throw new IllegalArgumentException("Invalid boss territory");
        if(x<minX||x>=maxX||y<minY||y>=maxY||z<minZ||z>=maxZ)throw new IllegalArgumentException("Boss post outside territory");
    }
    public boolean contains(double px,double py,double pz) {
        return Double.isFinite(px)&&Double.isFinite(py)&&Double.isFinite(pz)
                &&px>=minX&&px<maxX&&py>=minY&&py<maxY&&pz>=minZ&&pz<maxZ
                &&Math.pow(px-x,2)+Math.pow(pz-z,2)<=radius*radius&&Math.abs(py-y)<=6;
    }
}
