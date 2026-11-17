package com.lunar_prototype.impossbleEscapeMC.effect;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 太陽の遮蔽を、伝播計算とは独立した光線と箱の交差で確かめる。 */
class SunVisibilityTest {
    private record Block(int x, int y, int z, int transmission) {}

    private static SunShadowService.Snapshot scene(List<Block> blocks) {
        int nx = 128, ny = 64, nz = 7;
        byte[] transmit = new byte[nx * ny * nz];
        Arrays.fill(transmit, (byte) 255);
        int top = -1;
        for (Block b : blocks) {
            transmit[(b.y() * nz + b.z() + 3) * nx + b.x()] = (byte) b.transmission();
            top = Math.max(top, b.y());
        }
        return new SunShadowService.Snapshot(0, 0, -3, nx, ny, nz, transmit,
                0, 0, 0, new byte[0], new byte[0], new byte[0], new byte[0], top);
    }

    private static double[] direction(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }

    /** 48ブロックの線分と各箱の正の長さの交差。辺や角に触れるだけなら遮蔽しない。 */
    private static double reference(List<Block> blocks, int x, int y, int z, double[] sun) {
        double visibility = 1;
        for (Block b : blocks) {
            // 元のセルは従来のsunTransmit同様に除外。固体の受信セルは光を持たない。
            if (b.x() == x && b.y() == y && b.z() == z) {
                if (b.transmission() == 0) return 0;
                continue;
            }
            double enter = 0, exit = 48;
            double[] origin = {x + .5, y + .5, z + .5};
            int[] lo = {b.x(), b.y(), b.z()};
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(sun[axis]) < 1e-12) {
                    if (origin[axis] < lo[axis] || origin[axis] > lo[axis] + 1) exit = -1;
                } else {
                    double a = (lo[axis] - origin[axis]) / sun[axis];
                    double c = (lo[axis] + 1 - origin[axis]) / sun[axis];
                    enter = Math.max(enter, Math.min(a, c));
                    exit = Math.min(exit, Math.max(a, c));
                }
            }
            if (exit - enter > 1e-8) visibility *= b.transmission() / 255.0;
        }
        return visibility < .02 ? 0 : visibility;
    }

    @Test
    void singleBlockShadowSurvivesTwentyFourLayers() {
        var snapshot = scene(List.of(new Block(64, 24, 0, 0)));
        float[] visibility = SunShadowService.sunVisibility(snapshot, 32, 0, 0, 64, 1, 1, direction(.5, 1, 0));
        assertEquals(0, visibility[52 - 32], 1e-7, "従来の伝播では0.83882になり日なた扱いだった地点");
        assertEquals(1, visibility[51 - 32], 1e-7);
        assertEquals(1, visibility[53 - 32], 1e-7);
    }

    @Test
    void matchesIndependentIntersectionsAcrossSunDirections() {
        Random random = new Random(5193);
        List<Block> blocks = List.of(new Block(64, 24, 0, 0), new Block(48, 9, 1, 0),
                new Block(32, 10, 0, 89), new Block(32, 11, 0, 89), new Block(65, 23, -1, 0));
        var snapshot = scene(blocks);
        double[][] directions = {direction(0, 1, 0), direction(1, 1, 0), direction(-1, 1, 0),
                direction(.5, 1, 0), direction(-.5, 1, 0), direction(1, .1, 0), direction(-1, .1, 0),
                direction(.3, 1, .2)};
        for (double[] sun : directions) {
            float[] actual = SunShadowService.sunVisibility(snapshot, 0, 0, -2, 128, 20, 5, sun);
            for (int i = 0; i < 2000; i++) {
                int x = random.nextInt(128), y = random.nextInt(20), z = random.nextInt(5) - 2;
                double expected = reference(blocks, x, y, z, sun);
                assertEquals(expected, actual[x + 128 * (z + 2 + 5 * y)], 1e-6,
                        () -> "sun=" + Arrays.toString(sun) + " cell=" + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void touchingACornerDoesNotCountAsPassingThroughABlock() {
        double[] sun = direction(1, 1, 0);
        var tangent = scene(List.of(new Block(10, 10, 0, 0)));
        assertEquals(1, SunShadowService.sunVisibility(tangent, 10, 9, 0, 1, 1, 1, sun)[0], 1e-7);
        var crossed = scene(List.of(new Block(11, 10, 0, 0)));
        assertEquals(0, SunShadowService.sunVisibility(crossed, 10, 9, 0, 1, 1, 1, sun)[0], 1e-7);
    }

    @Test
    void leavesAttenuateAndBlockersBeyondReachDoNotCastShadows() {
        var leaves = scene(List.of(new Block(10, 8, 0, 89), new Block(10, 9, 0, 89)));
        assertEquals(Math.pow(89 / 255.0, 2), SunShadowService.sunVisibility(leaves,
                10, 0, 0, 1, 1, 1, direction(0, 1, 0))[0], 1e-7);
        var distant = scene(List.of(new Block(10, 50, 0, 0)));
        assertEquals(1, SunShadowService.sunVisibility(distant, 10, 0, 0, 1, 1, 1,
                direction(0, 1, 0))[0], 1e-7);
    }

    @Test
    void packedSunBitPreservesTheThinShadow() {
        var snapshot = SunShadowServiceTest.wall(0, 15);
        int index = ((24 - snapshot.y0()) * snapshot.nz() + (10 - snapshot.z0())) * snapshot.nx() + (32 - snapshot.x0());
        snapshot.transmit()[index] = 0;
        // wall()のtopは10なので、上に追加した遮蔽物を含める。
        snapshot = new SunShadowService.Snapshot(snapshot.x0(), snapshot.y0(), snapshot.z0(), snapshot.nx(),
                snapshot.ny(), snapshot.nz(), snapshot.transmit(), snapshot.sx0(), snapshot.sy0(), snapshot.sz0(),
                snapshot.skyLight(), snapshot.blockLight(), snapshot.albedo(), snapshot.cool(), 24);
        var grids = SunShadowService.computeLight(snapshot, 0, 0, 0,
                new SunShadowService.Light(UUID.randomUUID(), direction(.5, 1, 0), 1, 1));
        assertFalse(sunlit(grids.light(), 20, 1, 10));
        assertTrue(sunlit(grids.light(), 24, 1, 10));
    }

    private static boolean sunlit(int[] values, int x, int y, int z) {
        int cell = x + SunShadowService.W * (z + SunShadowService.D * y);
        int packed = values[2 * (cell / 7)] | values[2 * (cell / 7) + 1] << 14;
        return ((packed >>> ((cell % 7) * 4)) & 8) != 0;
    }
}
