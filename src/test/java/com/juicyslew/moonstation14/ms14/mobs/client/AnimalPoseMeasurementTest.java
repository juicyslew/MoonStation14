package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPoseMeasurementTest {
    @Test
    void measuresCorpseAndFlyingPoseBoundsAcrossAdapters() {
        var corpseMeasurements = new ArrayList<String>();
        var corpseCorrections = new ArrayList<Double>();
        var nonStatic = new ArrayList<String>();

        for (var entry : AnimalModelAdapters.bindings().entrySet()) {
            String key = entry.getKey();
            var adapter = entry.getValue();
            var model = adapter.construct(adapter.definition().bakeRoot());
            var bottoms = new ArrayList<Double>();
            var contact = new AnimalModelAdapters.DownedContact();
            for (float phase : new float[]{0F, .5F, 1F}) {
                adapter.applyPose(model, MobStateComponent.State.DEAD, "down_hold", phase, 0F, 0F);
                double measured = AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow(() ->
                        new AssertionError(key + " has no visible posed geometry"));
                adapter.applyPose(model, MobStateComponent.State.CRITICAL, "down_hold", phase, 0F, 0F);
                assertEquals(measured, AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow(), .005,
                        key + " critical/dead down_hold geometry differs");
                bottoms.add(measured);
                contact.apply(model.root(), MobStateComponent.State.DEAD, "down_hold");
                assertEquals(24D, AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow(), .01, key);
                model.root().y -= (float) (24D - measured);
                contact.apply(model.root(), MobStateComponent.State.DEAD, "down_hold");
                assertEquals(24D, AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow(), .01,
                        key + " cached contact accumulates");
                model.root().y -= (float) (24D - measured);
                contact.apply(model.root(), MobStateComponent.State.ALIVE, "idle_breathe");
                assertEquals(measured, AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow(), .01, key);
            }
            assertTrue(bottoms.stream().allMatch(Double::isFinite), key + " corpse bottom must be finite");
            double min = bottoms.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double max = bottoms.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            if (max - min > .005) nonStatic.add(key + " range=" + (max - min));
            double bottom = bottoms.get(0);
            double correction = 24D - bottom;
            corpseMeasurements.add(key + " bottom=" + bottom + " correction=" + correction);
            corpseCorrections.add(correction);
        }

        for (String key : List.of("bat", "bee", "butterfly", "parrot")) {
            var adapter = AnimalModelAdapters.resolve(key);
            var model = adapter.construct(adapter.definition().bakeRoot());
            var bottoms = new ArrayList<Double>();
            for (float phase : new float[]{0F, .1F, .2F, .3F, .4F}) {
                adapter.applyPose(model, MobStateComponent.State.ALIVE, "fly", phase, 0F, 0F);
                bottoms.add(AnimalPoseBounds.maxVisibleY(model.root()).orElseThrow());
            }
            double min = bottoms.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double max = bottoms.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            System.out.println("POSE_MEASUREMENT flyer " + key + " min=" + min + " max=" + max
                    + " relative24Min=" + (min - 24D) + " relative24Max=" + (max - 24D));
        }

        double minCorrection = corpseCorrections.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double maxCorrection = corpseCorrections.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        System.out.println("POSE_MEASUREMENT corpse adapters=" + corpseMeasurements.size()
                + " correctionMin=" + minCorrection + " correctionMax=" + maxCorrection
                + " outliers=" + corpseMeasurements);
        System.out.println("POSE_MEASUREMENT nonStaticDownHold=" + nonStatic);
        assertTrue(nonStatic.isEmpty(), "down_hold phase variation exceeds .005px: " + nonStatic);
    }
}
