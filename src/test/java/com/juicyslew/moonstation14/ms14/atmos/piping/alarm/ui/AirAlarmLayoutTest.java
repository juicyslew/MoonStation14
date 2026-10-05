package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmLayoutTest {
    @Test void screenGeometryFitsSupportedViewportsAndSeparatesControls() {
        for (int[] viewport : List.of(new int[]{320,240}, new int[]{427,240}, new int[]{640,360})) {
            var l = AirAlarmLayout.forViewport(viewport[0], viewport[1]);
            assertFalse(l.tooSmall());
            var window = new AirAlarmLayout.Rect(l.x(), l.y(), l.width(), l.height());
            assertTrue(window.inside(0,0,viewport[0],viewport[1]));
            for (var r : List.of(l.detailBack(), l.detailTabs(), l.configure(), l.close(), l.search(), l.deviceRows(), l.refresh(), l.pair(), l.enabled(), l.mode(),
                    l.numericEdit(), l.numericSubmit(), l.inset(), l.footer())) assertTrue(r.inside(l.x(),l.y(),l.width(),l.height()), r.toString());
            assertTrue(l.search().inside(l.x(),l.y(),l.width(),l.height()));
            assertFalse(l.search().overlaps(l.deviceRows())); // MachineSearchList owns rows only; search is 22px above.
            assertEquals(l.width() - 23, l.close().x() - l.x());
            assertEquals(5, l.close().y() - l.y());
            assertEquals(18, l.close().width()); assertEquals(18, l.close().height());
            assertEquals(18, l.search().height()); // MachineSearchList and its viewport use 18px rows/heights.
            assertEquals(72, l.deviceRows().height()); // Four visible 18px rows.
            assertFalse(l.refresh().overlaps(l.pair()));
            assertFalse(l.pair().overlaps(l.configure())); assertFalse(l.refresh().overlaps(l.configure()));
            assertFalse(l.detailBack().overlaps(l.detailTabs()));
            assertFalse(l.numericEdit().overlaps(l.numericSubmit()));
            assertFalse(l.enabled().overlaps(l.mode()));
            assertEquals(9,l.filters().size());
            for (var r : l.filters()) assertTrue(r.inside(l.x(),l.y(),l.width(),l.height()), r.toString());
            for (int i=0;i<9;i++) for(int j=i+1;j<9;j++) assertFalse(l.gas(i).overlaps(l.gas(j)));
            assertTrue(l.inset().inside(l.x(),l.y(),l.width(),l.height()));
        }
    }
    @Test void undersizedViewportContainsOnlyBoundedFallbackAndNoControls() {
        var l = AirAlarmLayout.forViewport(304,220);
        assertTrue(l.tooSmall());
        var window = new AirAlarmLayout.Rect(l.x(),l.y(),l.width(),l.height());
        assertTrue(window.inside(0,0,304,220));
        assertTrue(l.fallback().inside(l.x(),l.y(),l.width(),l.height()));
        // Screen init returns before constructing any interactive widgets for tooSmall layouts.
    }

    @Test void verySmallViewportKeepsFallbackInsideWindow() {
        var l = AirAlarmLayout.forViewport(200,180);
        assertTrue(l.tooSmall());
        assertTrue(l.fallback().inside(l.x(),l.y(),l.width(),l.height()));
    }
}
