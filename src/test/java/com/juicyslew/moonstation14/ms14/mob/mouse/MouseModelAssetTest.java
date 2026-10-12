package com.juicyslew.moonstation14.ms14.mob.mouse;

import com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Focused non-graphical check of the registered R2 layer, rig, clips and material bundle. */
class MouseModelAssetTest {
    @Test
    void authoredLayerBakesRigAndRunAnimationMovesLegs() {
        ModelPart root = MouseModel.createBodyLayer().bakeRoot();
        MouseModel<net.minecraft.world.entity.LivingEntity> model = new MouseModel<>(root);
        ModelPart mouse = root.getChild("mouse");
        ModelPart body = mouse.getChild("body");
        assertNotNull(body.getChild("head").getChild("ear_left"));
        assertNotNull(body.getChild("tail_base").getChild("tail_middle"));
        assertNotNull(body.getChild("hind_left"));
        assertFalse(mouse.getChild("gore").visible);

        model.applyPresentation("run", false, 0.025F, 0, 0, -1, 1);
        assertNotEquals(0.0F, body.getChild("front_left").xRot, 1.0e-5F);
        model.applyPresentation("down_hold", true, 0.0F, 0, 0, -1, 1);
        assertTrue(mouse.getChild("gore").visible);
    }

    @Test
    void allThreeAliveAndDeadMaterialsArePresentAtLogicalTextureSize() throws Exception {
        for (String color : new String[]{"white", "brown", "gray"}) {
            for (String state : new String[]{"", "_dead"}) {
                String path = "/assets/moonstation14/textures/entity/mouse/mouse_" + color + state + ".png";
                try (InputStream in = getClass().getResourceAsStream(path)) {
                    assertNotNull(in, path);
                    var image = ImageIO.read(in);
                    assertEquals(128, image.getWidth(), path);
                    assertEquals(128, image.getHeight(), path);
                }
            }
        }
    }
}
