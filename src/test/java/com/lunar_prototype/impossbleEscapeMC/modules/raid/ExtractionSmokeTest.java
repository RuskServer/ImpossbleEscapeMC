package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionSmokeTest {
    @Test
    void climbsEightBlocksAndFadesAtTheTop() {
        for (int seed = 0; seed < 24; seed++) {
            var first = ExtractionSmoke.sample(0, Double.POSITIVE_INFINITY, seed, 0);
            var full = ExtractionSmoke.sample(0.4, Double.POSITIVE_INFINITY, seed, 0);
            var end = ExtractionSmoke.sample(0.99, Double.POSITIVE_INFINITY, seed, 0);
            assertEquals(0, first.y());
            assertEquals(7.92, end.y(), 0.001);
            assertTrue(end.x() > full.x());
            assertTrue(end.size() > first.size());
            assertEquals(first.sprite(), end.sprite());
            assertEquals(first.sprite() + 4, full.sprite());
            assertTrue(full.sprite() < 15);
        }
    }

    @Test
    void keepsTheWholeSpriteBelowLowCeilings() {
        for (double ceiling : new double[]{0.61, 1.2, 2.3, 7.0, 9.5}) {
            for (int step = 0; step <= 32; step++) {
                var puff = ExtractionSmoke.sample(step / 32.0, ceiling, step, 0.8);
                assertTrue(puff.y() >= 0);
                assertTrue(puff.y() + puff.size() / Math.sqrt(2) <= ceiling);
            }
        }
    }
}
