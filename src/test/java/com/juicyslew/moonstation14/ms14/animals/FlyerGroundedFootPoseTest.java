package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBatModel;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBeeModel;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalButterflyModel;
import com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalParrotModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.mobs.client.AnimalPoseBounds;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Measures animated ground-contact geometry from baked rigs without starting a client. */
class FlyerGroundedFootPoseTest {
    private static float feetBottom(ModelPart root) {
        return (float) AnimalPoseBounds.maxVisibleY(root, path -> {
            String part = path.toLowerCase();
            boolean foot = part.contains("foot") || part.contains("leg");
            boolean bodyContact = part.contains("mouse/body") && !part.contains("wing") && !part.contains("gore")
                    && !part.contains("tail") && !part.contains("antenna");
            return foot || bodyContact;
        }).orElse(Float.NEGATIVE_INFINITY);
    }

    private static void measure(String id, HierarchicalModel<?> model, ModelPart root) {
        float maximumResidual = 0F;
        float maximumRawGap = 0F;
        var adapter = AnimalModelAdapters.resolve(id);
        for (float time : new float[]{0F, .25F, .5F, .75F, 1F}) {
            if (model instanceof AnimalBatModel<?> bat) bat.applyPresentation("idle_breathe", false, time, 0, 0, -1);
            else if (model instanceof AnimalBeeModel<?> bee) bee.applyPresentation("idle_breathe", false, time, 0, 0, -1);
            else if (model instanceof AnimalButterflyModel<?> butterfly) butterfly.applyPresentation("idle_breathe", false, time, 0, 0, -1);
            else if (model instanceof AnimalParrotModel<?> parrot) parrot.applyPresentation("idle_breathe", false, time, 0, 0, -1);
            float bottom = feetBottom(root);
            if (!Float.isFinite(bottom)) throw new AssertionError(id + " no ground contact geometry");
            float rawGap = 24F - bottom;
            maximumRawGap = Math.max(maximumRawGap, rawGap);
            float originalRootY = root.y;
            AnimalModelAdapters.applyGroundedContact(adapter, root, true, true, "idle_breathe", 1F);
            float correctedBottom = feetBottom(root);
            maximumResidual = Math.max(maximumResidual, Math.abs(24F - correctedBottom));
            root.y = originalRootY;
            AnimalModelAdapters.applyGroundedContact(adapter, root, true, false, "idle_breathe", 1F);
            assertTrue(Math.abs(feetBottom(root) - bottom) < .001F, id + " airborne pose shifted");
            root.y = originalRootY;
            AnimalModelAdapters.applyGroundedContact(adapter, root, false, true, "idle_breathe", 1F);
            assertTrue(Math.abs(feetBottom(root) - bottom) < .001F, id + " non-alive pose shifted");
            root.y = originalRootY;
            AnimalModelAdapters.applyGroundedContact(adapter, root, true, true, "fly", 1F);
            assertTrue(Math.abs(feetBottom(root) - bottom) < .001F, id + " flight clip shifted");
            root.y = originalRootY;
        }
        System.out.printf("GROUNDED_FOOT %s maxRawGapPixels=%.3f correctedResidualPixels=%.4f%n",
                id, maximumRawGap, maximumResidual);
        assertTrue(maximumResidual <= .06F, id + " corrected contact residual=" + maximumResidual);
    }

    @Test void reportsAnimatedGroundedFootClearanceForEveryFlyer() {
        var batRoot = AnimalBatModel.createBodyLayer().bakeRoot();
        measure("bat", new AnimalBatModel<LivingEntity>(batRoot), batRoot);
        var beeRoot = AnimalBeeModel.createBodyLayer().bakeRoot();
        measure("bee", new AnimalBeeModel<LivingEntity>(beeRoot), beeRoot);
        var butterflyRoot = AnimalButterflyModel.createBodyLayer().bakeRoot();
        measure("butterfly", new AnimalButterflyModel<LivingEntity>(butterflyRoot), butterflyRoot);
        var parrotRoot = AnimalParrotModel.createBodyLayer().bakeRoot();
        measure("parrot", new AnimalParrotModel<LivingEntity>(parrotRoot), parrotRoot);
    }
}
