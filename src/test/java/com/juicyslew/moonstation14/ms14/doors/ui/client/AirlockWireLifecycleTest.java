package com.juicyslew.moonstation14.ms14.doors.ui.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockWireLifecycleTest {
    @Test void explicitRemovalClearsOnceAndOldRemovalCannotClearReplacement() {
        var lifecycle = new AirlockWireLifecycle();
        Object first = new Object(), second = new Object();
        lifecycle.register(first);
        assertTrue(lifecycle.unregister(first)); // explicit X/ESC or foreign screen replacement
        assertFalse(lifecycle.unregister(first)); // removed after onClose is idempotent
        lifecycle.register(second);
        assertFalse(lifecycle.unregister(first)); // stale callback is identity guarded
        assertSame(second, lifecycle.active());
    }

    @Test void serverCloseAndAuthorityLossClearCurrentWindowOnly() {
        var lifecycle = new AirlockWireLifecycle();
        Object first = new Object(), second = new Object();
        lifecycle.register(first);
        assertTrue(lifecycle.unregister(first)); // server-close callback
        assertFalse(lifecycle.unregister(first));
        lifecycle.register(second);
        assertFalse(lifecycle.unregister(first)); // stale server-close for old session
        assertTrue(lifecycle.unregister(second)); // authority loss
        assertNull(lifecycle.active());
    }

    @Test void captureTransitionDoesNotUnregisterRetainedScreen() {
        var lifecycle = new AirlockWireLifecycle();
        Object screen = new Object();
        lifecycle.register(screen);
        // Screen removal during the try/finally capture transition deliberately does not
        // call unregister; restoration uses the same retained active instance.
        assertSame(screen, lifecycle.active());
        assertTrue(lifecycle.isActive(screen));
        assertFalse(lifecycle.unregister(new Object()));
        assertSame(screen, lifecycle.active());
    }

}
