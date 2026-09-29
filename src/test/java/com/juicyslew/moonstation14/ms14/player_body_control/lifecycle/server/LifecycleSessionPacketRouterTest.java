package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleSessionPacketRouterTest {
    @Test
    void routesCharacterOnlyAndGhostOnlyToTheirUniqueOwner() {
        var reached = new AtomicReference<LifecycleSessionPacketRouter.Route>();
        assertEquals(LifecycleSessionPacketRouter.Route.CHARACTER,
                LifecycleSessionPacketRouter.route(true, false, reached::set));
        assertEquals(LifecycleSessionPacketRouter.Route.CHARACTER, reached.get());
        assertEquals(LifecycleSessionPacketRouter.Route.GHOST,
                LifecycleSessionPacketRouter.route(false, true, reached::set));
        assertEquals(LifecycleSessionPacketRouter.Route.GHOST, reached.get());
    }

    @Test
    void dropsOverlapAndUnknownWithoutDispatch() {
        var reached = new AtomicReference<LifecycleSessionPacketRouter.Route>();
        assertEquals(LifecycleSessionPacketRouter.Route.AMBIGUOUS,
                LifecycleSessionPacketRouter.route(true, true, reached::set));
        assertEquals(LifecycleSessionPacketRouter.Route.UNKNOWN,
                LifecycleSessionPacketRouter.route(false, false, reached::set));
        assertEquals(null, reached.get());
    }

    @Test
    void retainsNetworkingSlotWhenGhostRemovedAndCharacterRemains() {
        assertTrue(LifecycleSessionPacketRouter.networkingSlotRequired(true, true, false, false));
    }

    @Test
    void retainsNetworkingSlotWhenCharacterRemovedAndGhostRemains() {
        assertTrue(LifecycleSessionPacketRouter.networkingSlotRequired(false, false, true, true));
    }

    @Test
    void clearsNetworkingSlotWhenBothRemovedOrOnlyIncompletePairsRemain() {
        assertFalse(LifecycleSessionPacketRouter.networkingSlotRequired(false, false, false, false));
        assertFalse(LifecycleSessionPacketRouter.networkingSlotRequired(true, false, false, true));
        assertFalse(LifecycleSessionPacketRouter.networkingSlotRequired(false, true, true, false));
    }

    @Test
    void retainsNetworkingSlotWhenBothHandlersInstalledDespiteAmbiguousOwner() {
        assertTrue(LifecycleSessionPacketRouter.networkingSlotRequired(true, true, true, true));
        var reached = new AtomicReference<LifecycleSessionPacketRouter.Route>();
        assertEquals(LifecycleSessionPacketRouter.Route.AMBIGUOUS,
                LifecycleSessionPacketRouter.route(true, true, reached::set));
        assertEquals(null, reached.get());
    }

    @Test
    void acceptsOneInjectedGhostOwnerAndRejectsNullOrOverlappingInstallation() {
        var router = new LifecycleSessionPacketRouter();
        BiConsumer<CustomPacketPayload, IPayloadContext> handler = (payload, context) -> { };
        Predicate<net.minecraft.server.level.ServerPlayer> owner = player -> false;
        assertThrows(IllegalArgumentException.class, () -> router.installGhostHandler(null, owner));
        assertThrows(IllegalArgumentException.class, () -> router.installGhostHandler(handler, null));
        router.installGhostHandler(handler, owner);
        try {
            assertNotNull(owner); // The injected owner, rather than packet data, determines ghost ownership.
            assertThrows(IllegalStateException.class, () -> router.installGhostHandler(handler, owner));
            router.clearCharacterHandler();
            assertTrue(router.ghostHandlerInstalled());
            assertFalse(router.characterHandlerInstalled());
        } finally {
            router.clearGhostHandler();
        }
    }
}
