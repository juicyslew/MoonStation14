package com.juicyslew.moonstation14.ms14.ui.animation;

import com.juicyslew.moonstation14.ms14.ui.client.animation.ApcSpriteAnimations;
import com.juicyslew.moonstation14.ms14.ui.model.animation.LayeredSpriteAnimation;
import com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpriteAnimationTest {
    @Test void fullSheetUsesRowMajorCoordinatesAcrossDirections() {
        SpriteSheet full = ApcSpriteAnimations.FULL;
        assertEquals(0, full.u(0, 0));
        assertEquals(32, full.u(0, 1));
        assertEquals(0, full.u(1, 0));
        assertEquals(32, full.v(1, 0));
        assertEquals(32, full.u(3, 1));
        assertEquals(96, full.v(3, 1));
        assertEquals(32, ApcSpriteAnimations.BASE.v(ApcSpriteAnimations.EAST, 0));
    }

    @Test void timingUsesExactBoundariesAndWrapsHugeElapsed() {
        SpriteSheet lack = ApcSpriteAnimations.LACK;
        assertEquals(0, lack.frameAt(0, 0));
        assertEquals(0, lack.frameAt(0, 249));
        assertEquals(1, lack.frameAt(0, 250));
        assertEquals(2, lack.frameAt(0, 500));
        assertEquals(3, lack.frameAt(0, 1500));
        assertEquals(0, lack.frameAt(0, 4500));
        assertEquals(3, lack.frameAt(0, -1));
        assertEquals(lack.frameAt(2, Long.MAX_VALUE % 4500), lack.frameAt(2, Long.MAX_VALUE));
        SpriteSheet charging = ApcSpriteAnimations.CHARGING;
        assertEquals(7, charging.frameAt(3, 700));
        assertEquals(0, charging.frameAt(3, 900));
        assertEquals(1, ApcSpriteAnimations.FULL.frameAt(1, 1000));
        assertEquals(0, ApcSpriteAnimations.FULL.frameAt(1, 2000));
    }

    @Test void orderedLayersRetainVisibilityAndSnapshotInput() {
        LayeredSpriteAnimation apc = ApcSpriteAnimations.withDisplay(ApcSpriteAnimations.CHARGING, false);
        assertSame(ApcSpriteAnimations.BASE, apc.layer(0).sheet());
        assertSame(ApcSpriteAnimations.CHARGING, apc.layer(1).sheet());
        assertTrue(apc.layer(0).visible());
        assertFalse(apc.layer(1).visible());
        assertThrows(IllegalArgumentException.class, () -> ApcSpriteAnimations.withDisplay(ApcSpriteAnimations.BASE, true));
        assertThrows(NullPointerException.class, () -> new LayeredSpriteAnimation(List.of(new LayeredSpriteAnimation.Layer(null, true))));
    }

    @Test void rejectsMalformedAndExtremeSheetsWithoutMutatingDurations() {
        int[][] delays = {{100, 200}, {100, 200}, {100, 200}, {100, 200}};
        SpriteSheet sheet = new SpriteSheet("sample", 64, 128, 32, 32, delays);
        delays[0][0] = 1;
        assertEquals(0, sheet.frameAt(0, 99));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("", 32, 32, 32, 32, new int[][] {{1}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 4097, 32, 32, 32, new int[][] {{1}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 33, 32, 32, 32, new int[][] {{1}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 32, 32, 32, 32, new int[][] {{0}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 32, 32, 32, 32, new int[][] {{Integer.MAX_VALUE}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 32, 32, 32, 32, new int[][] {}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 32, 32, 32, 32,
                new int[][] {{1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}}));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet("x", 32, 32, 32, 32, new int[][] {{1, 1}}));
        assertThrows(IllegalArgumentException.class, () -> sheet.frameAt(4, 0));
        assertThrows(IllegalArgumentException.class, () -> sheet.u(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new LayeredSpriteAnimation(List.of()));
    }

    @Test void singleDirectionAndRaggedDirectionsRetainRowMajorOffsets() {
        SpriteSheet single = new SpriteSheet("icon", 32, 32, 32, 32, new int[][] {{200}});
        assertEquals(0, single.frameAt(0, Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> single.frameAt(1, 0));
        SpriteSheet ragged = new SpriteSheet("ragged", 96, 64, 32, 32,
                new int[][] {{10, 20}, {30}, {40, 50}, {60}});
        assertEquals(64, ragged.u(1, 0));
        assertEquals(0, ragged.v(1, 0));
        assertEquals(0, ragged.u(2, 0));
        assertEquals(32, ragged.v(2, 0));
        assertEquals(64, ragged.u(3, 0));
        assertEquals(32, ragged.v(3, 0));
        assertEquals(1, ragged.frameAt(0, 10));
        assertEquals(0, ragged.frameAt(0, 30));
    }

    @Test void genericTwoAndEightDirectionSheetsUseTheSameBoundsAndAddressing() {
        SpriteSheet two = new SpriteSheet("two", 64, 32, 32, 32, new int[][] {{10}, {20}});
        assertEquals(2, two.directions());
        assertEquals(32, two.u(1, 0));
        assertEquals(0, two.v(1, 0));
        assertEquals(0, two.frameAt(1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> two.frameAt(2, 0));

        SpriteSheet eight = new SpriteSheet("eight", 128, 64, 32, 32,
                new int[][] {{1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}});
        assertEquals(8, eight.directions());
        assertEquals(96, eight.u(7, 0));
        assertEquals(32, eight.v(7, 0));
        assertThrows(IllegalArgumentException.class, () -> eight.u(8, 0));
    }
}
