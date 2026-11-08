package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BatGaitVerificationTest {
    @Test void gaitSelectionPoseAndGroundContactAreVerifiedAcrossCycles() {
        for (String animal : Set.of("bat", "parrot")) verify(animal);
    }

    private static void verify(String animal) {
        var adapter = AnimalModelAdapters.resolve(animal);
        var clips = adapter.supportedClips();
        assertEquals("walk", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, true, false, false));
        assertEquals("run", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, true, true, false));
        assertEquals("fly", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, true, true, false, false));
        ModelPart root = adapter.definition().bakeRoot();
        var model = adapter.construct(root);
        var foot = root.getChild("mouse").getChild("body").getChild("foot_left");
        var wing = root.getChild("mouse").getChild("body").getChild("wing_left");
        double maxResidual = 0, maxVisibleBottom = Double.NEGATIVE_INFINITY;
        for (String clip : Set.of("walk", "run")) {
            float firstFoot = Float.NaN;
            float[] firstWing = new float[3];
            boolean footVaries = false;
            double minWing = Double.POSITIVE_INFINITY, maxWing = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 32; i++) {
                float phase = i / 32F;
                adapter.applyPose(model, MobStateComponent.State.ALIVE, clip, phase, 0, 0);
                if (i == 0) { firstFoot = foot.xRot; firstWing = new float[]{wing.xRot, wing.yRot, wing.zRot}; }
                else {
                    footVaries |= Math.abs(foot.xRot - firstFoot) > .01F;
                    assertEquals(firstWing[0], wing.xRot, .001F);
                    assertEquals(firstWing[1], wing.yRot, .001F);
                    assertEquals(firstWing[2], wing.zRot, .001F);
                }
                minWing = Math.min(minWing, wing.zRot); maxWing = Math.max(maxWing, wing.zRot);
                AnimalModelAdapters.applyGroundedContact(adapter, root, true, true, clip, 1F);
                var contact = AnimalPoseBounds.maxVisibleY(root, p -> { String s = p.toLowerCase(); return s.contains("foot") || (s.contains("mouse/body") && !s.contains("wing") && !s.contains("gore") && !s.contains("tail") && !s.contains("antenna")); }).orElseThrow();
                var bottom = AnimalPoseBounds.maxVisibleY(root).orElseThrow();
                maxVisibleBottom = Math.max(maxVisibleBottom, bottom);
                maxResidual = Math.max(maxResidual, Math.abs(24 - contact));
            }
            assertTrue(footVaries, clip + " foot should move");
            assertEquals(minWing, maxWing, .001, clip + " wing resting pose varies");
        }
        double minFly = Double.POSITIVE_INFINITY, maxFly = Double.NEGATIVE_INFINITY;
        for (int i=0;i<32;i++) { adapter.applyPose(model, MobStateComponent.State.ALIVE, "fly", i/32F, 0, 0); minFly = Math.min(minFly, wing.zRot); maxFly = Math.max(maxFly, wing.zRot); }
        assertTrue(maxFly - minFly > .01, "fly wing angle should vary");
        System.out.printf("%s_GAIT maxContactResidualPixels=%.4f maxVisibleBottomPixels=%.4f%n", animal.toUpperCase(), maxResidual, maxVisibleBottom);
        assertTrue(maxResidual <= .5, "contact residual=" + maxResidual);
        assertTrue(maxVisibleBottom <= 24.5, "visible bottom=" + maxVisibleBottom);
    }
}
