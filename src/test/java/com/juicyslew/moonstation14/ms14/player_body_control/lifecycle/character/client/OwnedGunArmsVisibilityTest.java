package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OwnedGunArmsVisibilityTest {
    @Test void armMaskAndFinallyRestorePreserveArbitraryVisibilityAndRigChildren() {
        for (boolean slim : new boolean[]{false, true}) {
            var model = new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(
                    LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            for (int i = 0; i < 12; i++) part(model, i).visible = (i % 3) != 1;
            model.forearm(net.minecraft.world.entity.HumanoidArm.LEFT).visible = false;
            model.forearm(net.minecraft.world.entity.HumanoidArm.RIGHT).visible = true;
            model.leftArm.getChild("neutral_full").visible = false;
            boolean[] before = PlayerCharacterHarnessRenderer.captureVisibility(model);
            try {
                PlayerCharacterHarnessRenderer.setItemsOnlyVisibility(model);
                assertFalse(model.head.visible); assertFalse(model.hat.visible); assertFalse(model.body.visible);
                assertFalse(model.jacket.visible); assertFalse(model.leftLeg.visible); assertFalse(model.rightLeg.visible);
                assertFalse(model.leftPants.visible); assertFalse(model.rightPants.visible);
                assertFalse(model.leftArm.visible); assertFalse(model.rightArm.visible);
                assertFalse(model.leftSleeve.visible); assertFalse(model.rightSleeve.visible);
                assertFalse(model.forearm(net.minecraft.world.entity.HumanoidArm.LEFT).visible);
                assertTrue(model.forearm(net.minecraft.world.entity.HumanoidArm.RIGHT).visible);
                assertFalse(model.leftArm.getChild("neutral_full").visible);
                throw new IllegalStateException("simulate render failure");
            } catch (IllegalStateException expected) {
                assertEquals("simulate render failure", expected.getMessage());
            } finally {
                PlayerCharacterHarnessRenderer.restoreVisibility(model, before);
            }
            assertArrayEquals(before, PlayerCharacterHarnessRenderer.captureVisibility(model));
        }
    }

    private static ModelPart part(PlayerModel<?> model, int i) {
        return switch (i) {
            case 0 -> model.head; case 1 -> model.hat; case 2 -> model.body; case 3 -> model.jacket;
            case 4 -> model.leftLeg; case 5 -> model.rightLeg; case 6 -> model.leftPants; case 7 -> model.rightPants;
            case 8 -> model.leftArm; case 9 -> model.rightArm; case 10 -> model.leftSleeve; default -> model.rightSleeve;
        };
    }
}
