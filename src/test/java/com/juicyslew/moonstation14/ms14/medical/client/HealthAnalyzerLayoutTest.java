package com.juicyslew.moonstation14.ms14.medical.client;

import com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HealthAnalyzerLayoutTest {
    private static HealthAnalyzerSnapshot reading(int damageRows) {
        var groups = new ArrayList<HealthAnalyzerSnapshot.GroupReading>();
        for (int i = 0; i < damageRows; i++)
            groups.add(new HealthAnalyzerSnapshot.GroupReading("group" + i, 1, Map.of("blunt", 1f)));
        return new HealthAnalyzerSnapshot(UUID.randomUUID(), 1, UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "patient", "Human", "Alive", true, true, Optional.empty(), Optional.empty(), false, false,
                damageRows, groups, Map.of());
    }

    @Test void emptyAndFewReadingsUseTheMinimumHeight() {
        assertEquals(HealthAnalyzerLayout.MIN_HEIGHT, HealthAnalyzerLayout.desiredHeight(null, 144, 640, 360));
        assertTrue(HealthAnalyzerLayout.desiredHeight(reading(1), 144, 640, 360) < 80);
        assertEquals(0, HealthAnalyzerLayout.maxScroll(reading(0), HealthAnalyzerLayout.desiredHeight(reading(0), 144, 640, 360)));
    }

    @Test void manyDamageRowsGrowThenCapHeightAndScrollExtentIsStable() {
        var many = reading(40);
        assertTrue(HealthAnalyzerLayout.desiredHeight(many, 144, 640, 360) <= HealthAnalyzerLayout.MAX_HEIGHT);
        assertEquals(HealthAnalyzerLayout.MAX_HEIGHT, HealthAnalyzerLayout.desiredHeight(many, 144, 640, 360));
        int max = HealthAnalyzerLayout.maxScroll(many, HealthAnalyzerLayout.MAX_HEIGHT);
        assertEquals(max, HealthAnalyzerLayout.maxScroll(many, HealthAnalyzerLayout.MAX_HEIGHT));
        assertTrue(max > 0);
    }

    @Test void shortScreenBoundsHeightAndScrollCapTracksTheActualViewport() {
        var many = reading(40);
        assertEquals(104, HealthAnalyzerLayout.desiredHeight(many, 120, 120, 120));
        assertEquals(52, HealthAnalyzerLayout.desiredHeight(null, 90, 90, 90));
        assertTrue(HealthAnalyzerLayout.maxScroll(many, 60) > HealthAnalyzerLayout.maxScroll(many, 120));
    }

    @Test void compactGeometryAllocatesAlertsOnlyWhenPresentAndPreservesDamageOrderExtent() {
        var clean = reading(0);
        var bleeding = new HealthAnalyzerSnapshot(clean.scannerId(), clean.epoch(), clean.actorId(), clean.targetEntityId(),
                clean.targetId(), clean.frame(), clean.name(), clean.species(), clean.state(),
                clean.active(), clean.inRange(), clean.temperatureKelvin(), clean.usableBloodFraction(), true,
                false, clean.totalDamage(), clean.groups(), clean.damage());
        assertEquals(0, HealthAnalyzerLayout.footerHeight(clean));
        assertEquals(HealthAnalyzerLayout.ALERT_ROW, HealthAnalyzerLayout.footerHeight(bleeding));
        assertEquals(144, HealthAnalyzerLayout.WIDTH);
        assertEquals(52, HealthAnalyzerLayout.desiredHeight(clean, 144, 640, 360));
        assertTrue(HealthAnalyzerLayout.maxScroll(reading(14), 150) > 0);
        int viewport = HealthAnalyzerLayout.damageViewportHeight(150, HealthAnalyzerLayout.footerHeight(reading(14)));
        int max = HealthAnalyzerLayout.maxScroll(reading(14), 150);
        assertEquals(viewport, HealthAnalyzerLayout.contentEnd(reading(14)) - max);
        assertTrue(HealthAnalyzerLayout.contentEnd(clean) <= 52 - HealthAnalyzerLayout.HEADER);
    }

    @Test void areaCapMatchesApproximateViewportFractionsAtCommonGuiSizes() {
        int compact = HealthAnalyzerLayout.desiredHeight(reading(40), 144, 480, 270);
        int large = HealthAnalyzerLayout.desiredHeight(reading(40), 144, 640, 360);
        assertEquals(200, compact);
        assertEquals(200, large);
        assertTrue(144L * HealthAnalyzerLayout.desiredHeight(reading(0), 144, 480, 270)
                < 480L * 270 / 16);
    }

    @Test void fixedSummaryDamageViewportAndProportionalThumbStayIndependent() {
        int summary=HealthAnalyzerLayout.summaryBottom();
        assertEquals(50,summary);
        assertEquals(summary,HealthAnalyzerLayout.summaryBottom());
        assertEquals(0,HealthAnalyzerLayout.damageViewportHeight(50,0));
        assertEquals(new HealthAnalyzerLayout.ClipBounds(101,74,243,150),HealthAnalyzerLayout.bodyClipBounds(100,60,144,90,0));
        int track=200, viewport=100, content=200;
        assertEquals(100,HealthAnalyzerLayout.scrollbarThumbHeight(track,viewport,content));
        assertEquals(6,HealthAnalyzerLayout.scrollbarThumbHeight(track,1,10000));
        assertEquals(100,HealthAnalyzerLayout.scrollbarThumbY(0,track,100,999,10));
    }

    @Test void measuredWidthHonorsWhicheverColumnIsWidest() {
        assertTrue(HealthAnalyzerLayout.measuredWidth(90,38,40,30,30)>HealthAnalyzerLayout.measuredWidth(30,20,100,30,30));
        assertTrue(HealthAnalyzerLayout.measuredWidth(30,20,100,30,30)>100);
    }

    @Test void damageGroupsUseSharedNestedRowsAndOnlyInterGroupSeparators() {
        var two = new HealthAnalyzerSnapshot.GroupReading("brute", 2,
                Map.of("blunt", 1f, "slash", 1f));
        var one = new HealthAnalyzerSnapshot.GroupReading("burn", 1, Map.of("heat", 1f));
        var base = reading(0);
        var grouped = new HealthAnalyzerSnapshot(base.scannerId(), base.epoch(), base.actorId(), base.targetEntityId(),
                base.targetId(), base.frame(), base.name(), base.species(), base.state(), base.active(), base.inRange(),
                base.temperatureKelvin(), base.usableBloodFraction(), false, false, 3, List.of(two, one), Map.of());

        assertEquals(HealthAnalyzerLayout.GROUP_ROW + 2 * HealthAnalyzerLayout.DAMAGE_ROW
                        + HealthAnalyzerLayout.GROUP_SEPARATOR + HealthAnalyzerLayout.GROUP_ROW
                        + HealthAnalyzerLayout.DAMAGE_ROW,
                HealthAnalyzerLayout.contentExtent(grouped));
        assertEquals(8, HealthAnalyzerLayout.GROUP_ROW); // Category to first damage row; shared with renderer.
        assertEquals(3, HealthAnalyzerLayout.GROUP_SEPARATOR); // Between groups only.
        assertEquals(0, HealthAnalyzerLayout.contentExtent(reading(0)));
        assertEquals(0, HealthAnalyzerLayout.maxScroll(reading(0), 52));
    }

    @Test void measuredSummaryWidthIncludesPortraitAndBothColumnGapsAtRendererScale() {
        int logicalWidth = HealthAnalyzerLayout.measuredWidth(40, 30, 0, 0, 0);
        int requiredLogical = 8 + 40 + 6 + 30 + 6 + HealthAnalyzerLayout.PORTRAIT_WIDTH + 8;
        assertEquals(requiredLogical, logicalWidth);
        assertEquals(32, HealthAnalyzerLayout.PORTRAIT_WIDTH / 2);
        assertEquals(requiredLogical / 2, logicalWidth / 2);
    }

    @Test void titleIndicatorFitsReservedGapAndLongTranslationsDegradeWithoutRenderFailure() {
        var close = com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(0, 0, 288, .5f);
        var led = HealthAnalyzerLayout.ledBounds(144, 50, close.left());
        assertEquals(4, led.size());
        assertTrue(led.x() + led.size() <= close.left());
        assertTrue(led.y() >= 0 && led.y() + led.size() <= HealthAnalyzerLayout.HEADER);
        assertEquals(0, HealthAnalyzerLayout.labelWidth(3, 12, 9));
        assertEquals(55, HealthAnalyzerLayout.labelWidth(90, 55, 25));
    }

    @Test void bodyClipUsesGlobalGuiCoordinatesForTranslatedAnalyzerWindow() {
        assertEquals(new HealthAnalyzerLayout.ClipBounds(101, 74, 243, 150),
                HealthAnalyzerLayout.bodyClipBounds(100, 60, 144, 90, 0));
    }
}
