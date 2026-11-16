package com.lunar_prototype.impossbleEscapeMC.modules.boss;

/** 呼出元が渡す区域。Raidのマップ形式には依存しない。 */
public record BossTerritory(String id, double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {
    public BossTerritory {
        if (id == null || id.isBlank() || !Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || !Double.isFinite(maxZ) || minX >= maxX || minY >= maxY || minZ >= maxZ)
            throw new IllegalArgumentException("Invalid boss territory");
    }
    public boolean contains(double[] p) {
        return p[0] >= minX && p[0] < maxX && p[1] >= minY && p[1] < maxY && p[2] >= minZ && p[2] < maxZ;
    }
}
