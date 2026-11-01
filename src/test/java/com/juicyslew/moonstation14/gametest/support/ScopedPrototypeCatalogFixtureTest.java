package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScopedPrototypeCatalogFixtureTest {
    @Test void restoresNestedSameCatalogActionsInLifoOrder() {
        var info = mock(GameTestInfo.class);
        var order = new ArrayList<String>();
        var outer = new ScopedPrototypeCatalogFixture.Handle(() -> order.add("normal"));
        var inner = new ScopedPrototypeCatalogFixture.Handle(() -> order.add("fakeMelee"));
        ScopedPrototypeCatalogFixture.register(info, outer);
        ScopedPrototypeCatalogFixture.register(info, inner);

        listener(info).testPassed(info, null);

        assertEquals(List.of("fakeMelee", "normal"), order);
    }

    @Test void restoresThreeDifferentCatalogActionsInReverseOrder() {
        var info = mock(GameTestInfo.class);
        var order = new ArrayList<Integer>();
        for (int i = 1; i <= 3; i++) {
            int action = i;
            ScopedPrototypeCatalogFixture.register(info, new ScopedPrototypeCatalogFixture.Handle(() -> order.add(action)));
        }
        listener(info).testPassed(info, null);
        assertEquals(List.of(3, 2, 1), order);
    }

    @Test void manualRestoreAndRepeatedCallbacksDoNotReapplyActions() {
        var info = mock(GameTestInfo.class);
        var order = new ArrayList<String>();
        var outer = new ScopedPrototypeCatalogFixture.Handle(() -> order.add("outer"));
        var inner = new ScopedPrototypeCatalogFixture.Handle(() -> order.add("inner"));
        ScopedPrototypeCatalogFixture.register(info, outer);
        ScopedPrototypeCatalogFixture.register(info, inner);
        inner.restore();
        var callback = listener(info);
        callback.testPassed(info, null);
        callback.testFailed(info, null);
        callback.testAddedForRerun(info, mock(GameTestInfo.class), null);
        assertEquals(List.of("inner", "outer"), order);
    }

    @Test void failureAndRerunRemoveGroupWithoutOldCallbackPoppingNewScope() {
        for (boolean rerun : List.of(false, true)) {
            var info = mock(GameTestInfo.class);
            var oldCount = new int[1];
            ScopedPrototypeCatalogFixture.register(info, new ScopedPrototypeCatalogFixture.Handle(() -> oldCount[0]++));
            GameTestListener old = capture(info).getValue();
            if (rerun) old.testAddedForRerun(info, mock(GameTestInfo.class), null);
            else old.testFailed(info, null);
            assertEquals(1, oldCount[0]);

            clearInvocations(info);
            var newCount = new int[1];
            ScopedPrototypeCatalogFixture.register(info, new ScopedPrototypeCatalogFixture.Handle(() -> newCount[0]++));
            verify(info, times(1)).addListener(any(GameTestListener.class));
            old.testPassed(info, null);
            assertEquals(0, newCount[0]);
            capture(info).getValue().testPassed(info, null);
            assertEquals(1, newCount[0]);
        }
    }

    private static GameTestListener listener(GameTestInfo info) { return capture(info).getValue(); }
    private static org.mockito.ArgumentCaptor<GameTestListener> capture(GameTestInfo info) {
        var captor = org.mockito.ArgumentCaptor.forClass(GameTestListener.class);
        verify(info, atLeastOnce()).addListener(captor.capture());
        return captor;
    }
}
