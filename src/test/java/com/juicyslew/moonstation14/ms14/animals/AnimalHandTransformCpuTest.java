package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKoboldModel;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMonkeyModel;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalScurretModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real baked rigs and records converted model grip locations without starting a client. */
class AnimalHandTransformCpuTest {
    private record P(float x, float y, float z) { }

    private static P origin(PoseStack pose) {
        Vector3f p = pose.last().pose().transformPosition(new Vector3f());
        return new P(p.x, p.y, p.z);
    }

    private static P expected(ModelPart root, String path, float x, float y, float z) {
        PoseStack pose = new PoseStack();
        ModelPart part = root;
        for (String name : path.split("/")) {
            part = part.getChild(name);
            part.translateAndRotate(pose);
        }
        pose.translate(x / 16F, y / 16F, z / 16F);
        return origin(pose);
    }

    private static P convertedGrip(P modelPoint, boolean left, BodyAppearanceItemRenderer.GripConvention convention) {
        PoseStack pose = new PoseStack();
        pose.translate(modelPoint.x, modelPoint.y, modelPoint.z);
        BodyAppearanceItemRenderer.applyItemGripTransform(pose, left, convention);
        return origin(pose);
    }

    private static void verify(String id, HierarchicalModel<? extends LivingEntity> model,
                               ModelPart root, String leftPath, String rightPath,
                               float x, float y, float z) {
        var adapter = AnimalModelAdapters.resolve(id);
        for (String clip : new String[]{"idle_breathe", "walk"}) {
            adapter.applyPose(model, MobStateComponent.State.ALIVE, clip, .7F, 0, 0);
            for (boolean left : new boolean[]{true, false}) {
                String path = left ? leftPath : rightPath;
                PoseStack actual = new PoseStack();
                assertTrue(adapter.translateToHand(model, left, actual), id);
                P measured = origin(actual);
                P composed = expected(root, path, x, y, z);
                assertEquals(composed.x, measured.x, 1e-6F, id + " " + clip + " x");
                assertEquals(composed.y, measured.y, 1e-6F, id + " " + clip + " y");
                assertEquals(composed.z, measured.z, 1e-6F, id + " " + clip + " z");
                // The renderer uses this same transform for both ordinary items and portable-body rendering.
                P converted = convertedGrip(measured, left, BodyAppearanceItemRenderer.GripConvention.PALM);
                System.out.printf("HAND %s %s %s model=(%.6f,%.6f,%.6f) wearable=(%.6f,%.6f,%.6f)%n",
                        id, clip, left ? "left" : "right", measured.x, measured.y, measured.z,
                        converted.x, converted.y, converted.z);
                assertTrue(Float.isFinite(converted.x) && Float.isFinite(converted.y)
                        && Float.isFinite(converted.z));
                assertEquals(measured.y, converted.y, 1e-6F, id + " " + clip + " portable/ordinary palm origin");
            }
        }
    }

    @Test void humanoidGripConventionRetainsArmPivotTranslation() {
        P pivot = new P(0, 0, 0);
        for (boolean left : new boolean[]{true, false}) {
            P converted = convertedGrip(pivot, left, BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            assertEquals(.625F, converted.y, 1e-6F);
        }
    }

    @Test void bakedAnimalHandAnchorsFollowAnimatedBones() {
        ModelPart koboldRoot = AnimalKoboldModel.createBodyLayer().bakeRoot();
        verify("kobold", new AnimalKoboldModel<>(koboldRoot), koboldRoot,
                "mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right", .1F, 3.65F, -.1F);
        ModelPart monkeyRoot = AnimalMonkeyModel.createBodyLayer().bakeRoot();
        verify("monkey", new AnimalMonkeyModel<>(monkeyRoot), monkeyRoot,
                "mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right", -.13F, 2.75F, -.1F);
        ModelPart scurretRoot = AnimalScurretModel.createBodyLayer().bakeRoot();
        verify("scurret", new AnimalScurretModel<>(scurretRoot), scurretRoot,
                "mouse/body/arm_left", "mouse/body/arm_right", -.12F, 3.75F, -1F);
    }
}
