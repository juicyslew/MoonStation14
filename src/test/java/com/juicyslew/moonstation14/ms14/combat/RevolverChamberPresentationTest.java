package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RevolverChamberPresentationTest {
    private static GunProfile gun(int capacity) {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(capacity,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick"), Set.of(), Set.of())));
    }

    @Test void projectsOrderedLoadedSpentMissingAndCurrentWithoutMutatingStack() {
        var profile = gun(4);
        var input = new ItemStack(Items.STICK);
        var spent = new ItemStack(Items.STICK);
        spent.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var state = new RevolverState(2, List.of(new RevolverState.Chamber(true, Optional.empty()),
                new RevolverState.Chamber(false, Optional.of(spent)), RevolverState.Chamber.empty(),
                RevolverState.Chamber.spentDeferred(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick"))));
        var view = RevolverChamberPresentation.project(profile, input, state).orElseThrow();
        assertEquals(2, view.currentIndex());
        assertEquals(List.of(0, 1, 2, 3), view.chambers().stream().map(RevolverChamberPresentation.ChamberIcon::index).toList());
        assertEquals(List.of(RevolverChamberPresentation.Status.LOADED, RevolverChamberPresentation.Status.SPENT,
                RevolverChamberPresentation.Status.MISSING, RevolverChamberPresentation.Status.MISSING),
                view.chambers().stream().map(RevolverChamberPresentation.ChamberIcon::status).toList());
        assertTrue(view.chambers().get(2).current());
        assertEquals(0xffd7df60, view.chambers().get(0).tint());
        assertEquals(0xffb50e25, view.chambers().get(1).tint());
        assertEquals(0xff222222, view.chambers().get(2).tint());
        assertFalse(view.chambers().get(1).current());
        assertTrue(spent.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
    }

    @Test void previewInitializesFromProfileButPresentEmptyDoesNotRefillAndMismatchesAreRejected() {
        var profile = gun(6);
        var stack = new ItemStack(Items.STICK);
        var preview = RevolverChamberPresentation.project(profile, stack, null).orElseThrow();
        assertEquals(6, preview.chambers().size());
        assertTrue(preview.chambers().stream().allMatch(c -> c.status() == RevolverChamberPresentation.Status.LOADED));
        var empty = RevolverChamberPresentation.project(profile, stack, RevolverState.empty(6)).orElseThrow();
        assertTrue(empty.chambers().stream().allMatch(c -> c.status() == RevolverChamberPresentation.Status.MISSING));
        assertTrue(RevolverChamberPresentation.project(profile, stack, RevolverState.empty(5)).isEmpty());
        assertTrue(RevolverChamberPresentation.project(gun(1), stack, null).isPresent());
        assertThrows(UnsupportedOperationException.class, () -> preview.chambers().add(preview.chambers().get(0)));
    }

    @Test void hudDrawPlanUsesNativeIconResourcesAndLeavesCurrentGreenOutlineVisible() throws Exception {
        var profile = gun(2);
        var stack = new ItemStack(Items.STICK);
        var spentStack = new ItemStack(Items.STICK);
        spentStack.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var state = new RevolverState(0, List.of(new RevolverState.Chamber(true, Optional.empty()),
                new RevolverState.Chamber(false, Optional.of(spentStack))));
        var projection = RevolverChamberPresentation.project(profile, stack, state).orElseThrow();
        var draws = RevolverChamberPresentation.drawPlan(projection, 10, 20);
        assertEquals(3, draws.size());
        var outline = draws.get(0);
        assertEquals(0xff32cd32, outline.tint());
        assertEquals(7, outline.width());
        assertEquals(16, outline.height());
        assertEquals(5, outline.sourceWidth()); assertEquals(12, outline.sourceHeight());
        var inner = draws.get(1);
        assertEquals(11, inner.x()); assertEquals(22, inner.y());
        assertEquals(5, inner.width()); assertEquals(12, inner.height());
        var spent = draws.get(2);
        assertTrue(spent.texture().endsWith("/empty"));
        assertEquals(0xffb50e25, spent.tint());
        for (var draw : draws) {
            var id = RevolverChamberPresentation.textureId(draw.texture());
            assertEquals("moonstation14:textures/" + draw.texture() + ".png", id.toString());
            try (var in = getClass().getClassLoader().getResourceAsStream("assets/" + id.getNamespace() + "/" + id.getPath())) {
                assertNotNull(in, id.toString());
            }
        }
        var empty = RevolverChamberPresentation.project(profile, stack, RevolverState.empty(2)).orElseThrow();
        assertEquals(0xff222222, RevolverChamberPresentation.drawPlan(empty, 0, 0).get(1).tint());
    }
}
