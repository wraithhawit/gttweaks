package com.wraithhawit.gttweaks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** SchemaView.scrollZoomStep against ModularUI 3.3.1's step, (float) (-scroll / 12.0). */
class ScrollZoomStepTest {
    private static final float EPS = 1e-6F;

    private static float stock(double scroll) {
        return (float) (-scroll / 12.0);
    }

    @Test
    void defaultIsTenTimesModularUiPerNotch() {
        assertEquals(-10.0F / 12.0F, SchemaView.scrollZoomStep(stock(1.0), 10.0), EPS);
        assertEquals(10.0F / 12.0F, SchemaView.scrollZoomStep(stock(-1.0), 10.0), EPS);
    }

    @Test
    void oneMatchesModularUiAndDirectionIsKept() {
        for (double scroll : new double[] {-3.0, -1.0, -0.25, 0.25, 1.0, 3.0}) {
            assertEquals(stock(scroll), SchemaView.scrollZoomStep(stock(scroll), 1.0), EPS);
            assertEquals(Math.signum(stock(scroll)), Math.signum(SchemaView.scrollZoomStep(stock(scroll), 37.5)));
        }
    }

    @Test
    void invalidMultiplierFallsBackToModularUi() {
        for (double bad : new double[] {0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(stock(1.0), SchemaView.scrollZoomStep(stock(1.0), bad), EPS);
        }
    }
}
