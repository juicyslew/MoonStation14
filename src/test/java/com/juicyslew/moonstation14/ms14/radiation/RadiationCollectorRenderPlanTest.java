package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.radiation.client.RadiationCollectorRenderPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorRenderPlanTest {
    @Test void everyRegisteredAndPlannedModelIdResolvesToItsNativeResource() throws Exception {
        var ids = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (var model : com.juicyslew.moonstation14.ms14.radiation.client.RadiationCollectorBlockEntityRenderer.additionalModels())
            ids.add(model.id());
        for (int tank = 0; tank < 2; tank++) for (int pressure = 0; pressure < 4; pressure++)
            for (boolean enabled : new boolean[]{false, true}) {
                var snapshot = new RadiationCollectorBlockEntity.CollectorVisualSnapshot(enabled, tank == 1,
                        pressure, ModRadiationCollectors.EMPTY_ID, false, false, 0);
                var plan = RadiationCollectorRenderPlan.from(snapshot, 0);
                for (var id : java.util.List.of(plan.shellModel(), plan.panelModel(), plan.pressureModel())) {
                    ids.add(id);
                    assertTrue(com.juicyslew.moonstation14.ms14.radiation.client.RadiationCollectorBlockEntityRenderer
                            .additionalModels().stream().anyMatch(model -> model.id().equals(id)), "unregistered " + id);
                }
            }
        for (var id : ids) {
            String path = "/assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json";
            try (var resource = getClass().getResourceAsStream(path)) {
                assertNotNull(resource, "Missing native model resource " + path);
            }
        }
    }

    @Test void pressureBandsUseLocalKpaAtExactPaConvertedBoundaries() {
        double first = 3.33 * 101.325;
        double second = 6.66 * 101.325;
        assertEquals(0, RadiationCollectorVisualAppearance.pressureBand(false, 1000));
        assertEquals(0, RadiationCollectorVisualAppearance.pressureBand(true, 0)); // present but empty
        assertEquals(0, RadiationCollectorVisualAppearance.pressureBand(true, .009999));
        assertEquals(1, RadiationCollectorVisualAppearance.pressureBand(true, .01));
        assertEquals(1, RadiationCollectorVisualAppearance.pressureBand(true, first - .001));
        assertEquals(2, RadiationCollectorVisualAppearance.pressureBand(true, first));
        assertEquals(2, RadiationCollectorVisualAppearance.pressureBand(true, second - .001));
        assertEquals(3, RadiationCollectorVisualAppearance.pressureBand(true, second));
    }

    @Test void motionIgnoresMidClipDirectionChangesThenReconcilesLatestTarget() {
        var motion = new RadiationCollectorMotion.State(false, false, 0);
        motion = RadiationCollectorMotion.toggle(motion, true, 100);
        float[] activate = {0, 22.5f, 45, 67.5f, 90, 90};
        long[] ticks = {100, 102, 104, 106, 108, 116};
        for (int i = 0; i < ticks.length; i++) assertEquals(activate[i], RadiationCollectorMotion.angleDegrees(motion, ticks[i]));
        motion = RadiationCollectorMotion.toggle(motion, false, 104); // animation stays on its original clip
        assertTrue(motion.clipTargetEnabled());
        assertEquals(45f, RadiationCollectorMotion.angleDegrees(motion, 104));
        motion = RadiationCollectorMotion.advance(motion, false, 116);
        assertTrue(motion.transitioning());
        assertFalse(motion.clipTargetEnabled());
        assertEquals(90f, RadiationCollectorMotion.angleDegrees(motion, 116));
        assertEquals(67.5f, RadiationCollectorMotion.angleDegrees(motion, 118));
        assertEquals(0f, RadiationCollectorMotion.angleDegrees(motion, 124));
        assertEquals(0f, RadiationCollectorMotion.angleDegrees(motion, 132));
        motion = RadiationCollectorMotion.advance(motion, false, 132);
        assertFalse(motion.transitioning());
        assertEquals(0f, RadiationCollectorMotion.angleDegrees(motion, 132));
    }

    @Test void updateTagIsMinimalAndProductionRenderPlanComposesIndependentLayers() {
        var source = new RadiationCollectorBlockEntity.CollectorVisualSnapshot(true, true, 2,
                ModRadiationCollectors.EMPTY_ID, true, true, 1234);
        var received = RadiationCollectorBlockEntity.decodeVisualSnapshot(RadiationCollectorBlockEntity.encodeVisualSnapshot(source));
        assertEquals(source, received);
        var plan = RadiationCollectorRenderPlan.from(received, 1238);
        assertTrue(plan.shellModel().getPath().endsWith("shell_tank1_pressure2"));
        assertTrue(plan.panelModel().getPath().endsWith("panel_active"));
        assertTrue(plan.pressureModel().getPath().endsWith("pressure_overlay_p2"));
        assertEquals(45f, plan.panelAngleDegrees());
        assertEquals(2, plan.shellPanelDrawCount());
        var shellVariants = new java.util.HashSet<String>();
        for (int present = 0; present <= 1; present++) for (int pressure = 0; pressure <= 3; pressure++) {
            var combination = new RadiationCollectorBlockEntity.CollectorVisualSnapshot(false, present == 1,
                    pressure, ModRadiationCollectors.EMPTY_ID, false, false, 0);
            shellVariants.add(RadiationCollectorRenderPlan.from(combination, 0).shellModel().toString());
        }
        assertEquals(8, shellVariants.size(), "tank and pressure appearance select independent atlas variants");
        var tag = RadiationCollectorBlockEntity.encodeVisualSnapshot(source);
        assertFalse(tag.contains("tankSlot"));
        assertFalse(tag.contains("identity"));
    }
}
