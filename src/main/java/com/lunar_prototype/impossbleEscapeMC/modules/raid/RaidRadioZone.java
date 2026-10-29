package com.lunar_prototype.impossbleEscapeMC.modules.raid;

/** 管理者が設定する危険区域。敵の現在位置・人数とは連動しない。 */
record RaidRadioZone(String id,String map,String name,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
    RaidRadioZone {
        if(id==null||id.isBlank()||map==null||map.isBlank()||name==null||name.isBlank()
                ||!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)
                ||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)
                ||minX>=maxX||minY>=maxY||minZ>=maxZ)throw new IllegalArgumentException("invalid radio zone");
    }
    boolean contains(double x,double y,double z,double margin) {
        double vertical=Math.min(margin,0.5);
        return x>=minX-margin&&x<maxX+margin&&y>=minY-vertical&&y<maxY+vertical&&z>=minZ-margin&&z<maxZ+margin;
    }
}
