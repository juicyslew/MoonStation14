package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NewRoachAbdomenPoseTest {
    private static final List<String> ROACHES = List.of("moproach", "mothroach");
    private static final double ORIGINAL_DEAD_BOTTOM = 23.975000381469727;

    @Test void preservesOriginalGeometryAndCorpseBoundsWhileArticulatingOnlyDuringGait() {
        for (String animal : ROACHES) {
            var adapter = AnimalModelAdapters.resolve(animal);
            ModelPart root = adapter.definition().bakeRoot();
            var model = adapter.construct(root);
            ModelPart body = root.getChild("mouse").getChild("body");
            ModelPart abdomen = body.getChild("abdomen");

            assertEquals(0, cubes(body).size(), animal + " body should be an empty articulation container");
            assertEquals(30, cubes(abdomen).size(), animal + " abdomen retains all original body cuboids");
            assertEquals(0F, abdomen.xRot, 0F);
            assertEquals(0F, abdomen.yRot, 0F);
            assertEquals(0F, abdomen.zRot, 0F);
            assertEquals(1F, abdomen.xScale, 0F);
            assertEquals(1F, abdomen.yScale, 0F);
            assertEquals(1F, abdomen.zScale, 0F);
            assertEquals(0F, abdomen.x, 0F);
            assertEquals(0F, abdomen.y, 0F);
            assertEquals(0F, abdomen.z, 0F);

            assertTrue(body.getChild("head") != null);
            for (String leg : List.of("leg_0_left", "leg_0_right", "leg_1_left", "leg_1_right", "leg_2_left", "leg_2_right"))
                assertTrue(body.getChild(leg) != null, animal + " original body child " + leg);
            for (String tuft : List.of("ruff_tuft_left0", "ruff_tuft_left1", "ruff_tuft_left2", "ruff_tuft_right0", "ruff_tuft_right1", "ruff_tuft_right2"))
                assertTrue(body.getChild(tuft) != null, animal + " original body child " + tuft);

            for (float phase : new float[]{0F, .5F, 1F}) {
                adapter.applyPose(model, MobStateComponent.State.DEAD, "down_hold", phase, 0F, 0F);
                assertEquals(ORIGINAL_DEAD_BOTTOM, AnimalPoseBounds.maxVisibleY(root).orElseThrow(), .005,
                        animal + " original dead bottom baseline");
                assertEquals(0F, abdomen.yRot, .00001F, animal + " dead abdomen rotation");
                adapter.applyPose(model, MobStateComponent.State.CRITICAL, "down_hold", phase, 0F, 0F);
                assertEquals(0F, abdomen.yRot, .00001F, animal + " critical abdomen rotation");
                adapter.applyPose(model, MobStateComponent.State.ALIVE, "idle_breathe", phase, 0F, 0F);
                assertEquals(0F, abdomen.yRot, .00001F, animal + " idle abdomen rotation");
                adapter.applyPose(model, MobStateComponent.State.ALIVE, "rest", phase, 0F, 0F);
                assertEquals(0F, abdomen.yRot, .00001F, animal + " rest abdomen rotation");
            }

            for (String gait : Set.of("walk", "run")) {
                float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                float duration = (animal.equals("moproach")
                        ? com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMoproachAnimations.byName(gait)
                        : com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMothroachAnimations.byName(gait))
                        .lengthInSeconds();
                for (int i = 0; i < 32; i++) {
                    adapter.applyPose(model, MobStateComponent.State.ALIVE, gait, i * duration / 32F, 0F, 0F);
                    min = Math.min(min, abdomen.yRot);
                    max = Math.max(max, abdomen.yRot);
                }
                double expected = Math.toRadians(gait.equals("walk") ? 4.0 : 6.0);
                System.out.printf("ROACH_ABDOMEN animal=%s gait=%s localYawMin=%.8f localYawMax=%.8f%n",
                        animal, gait, min, max);
                assertEquals(-expected, min, .002, animal + " " + gait + " abdomen local minimum");
                assertEquals(expected, max, .002, animal + " " + gait + " abdomen local maximum");
            }
        }
    }

    private static List<?> cubes(ModelPart part) {
        try {
            var field = ModelPart.class.getDeclaredField("cubes");
            field.setAccessible(true);
            return (List<?>) field.get(part);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect model cuboids", exception);
        }
    }
}
