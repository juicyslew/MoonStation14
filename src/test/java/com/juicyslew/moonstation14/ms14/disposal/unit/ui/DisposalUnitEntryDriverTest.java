package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DisposalUnitEntryDriverTest {
    @Test
    void fixedLifetimeEntryDriverUsesServerTickPost() throws NoSuchMethodException {
        // The carrier player tick can be absent (for example, while no player is ticking),
        // so pending-world entry work must be driven by the server tick handler.
        // This signature check does not establish anything about network authority.
        var handler = DisposalUnitMenuService.class.getDeclaredMethod("serverTick", ServerTickEvent.Post.class);
        assertTrue(Modifier.isPublic(handler.getModifiers()));
        assertTrue(Modifier.isStatic(handler.getModifiers()));
        assertFalse(Arrays.stream(DisposalUnitMenuService.class.getDeclaredMethods())
                .anyMatch(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(PlayerTickEvent.class::isAssignableFrom)),
                "Disposal unit entry driver must not depend on a player tick handler");
    }
}
