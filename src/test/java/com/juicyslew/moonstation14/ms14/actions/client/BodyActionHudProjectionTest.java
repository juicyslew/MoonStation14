package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionHudProjectionTest {
    private static BodyActionSnapshot.Entry entry(String n) {
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", n);
        return new BodyActionSnapshot.Entry(id, "provider", n, id, Optional.empty(), ActionPrototype.Kind.INSTANT,
                true, false, 0, 0, "", 0);
    }
    @Test void paintedCellPinsOldEntryAcrossHoleReuse() {
        var a = entry("a"); var b = entry("b"); var slots = new BodyActionSlots();
        slots.update(new BodyActionSnapshot(java.util.UUID.randomUUID(), 1, 1, 0, List.of(a)));
        var old = BodyActionHudProjection.build(slots.resolve(), 60, 320);
        slots.update(new BodyActionSnapshot(java.util.UUID.randomUUID(), 1, 1, 0, List.of(b)));
        var hit = old.hit(1, 1);
        assertNotNull(hit); assertSame(a, hit.entry()); assertNotSame(b, hit.entry());
        var fresh = BodyActionHudProjection.build(slots.resolve(), 60, 320);
        assertSame(b, fresh.hit(1, 1).entry());
    }
    @Test void holesKeepTheirNumberedGeometryAndTallNarrowViewportFitsTwenty() {
        var one = entry("one"); var three = entry("three");
        var p = BodyActionHudProjection.build(List.of(new BodyActionSlots.Slot(1, one), new BodyActionSlots.Slot(3, three)), 90, 320);
        assertSame(one, p.hit(1, 1).entry()); assertNull(p.hit(1, 40));
        assertSame(three, p.hit(1, 85).entry());
        var many = new java.util.ArrayList<BodyActionSlots.Slot>();
        for (int i=1;i<=20;i++) many.add(new BodyActionSlots.Slot(i, one));
        assertEquals(20, BodyActionHudProjection.build(many, 28, 840).cells().size());
        assertTrue(BodyActionHudProjection.build(many, 28, 640).cells().size() < 20);
    }
}
