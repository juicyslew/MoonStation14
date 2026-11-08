package com.juicyslew.moonstation14.entities.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ParticleAcceleratorFrameUvTest {
    @Test void mapsFourDirectionsAndTwoFramesWithoutPaddingTile() {
        assertEquals(0,ParticleAcceleratorFrameUv.frame(0,0));
        assertEquals(0,ParticleAcceleratorFrameUv.frame(1,0));
        assertEquals(1,ParticleAcceleratorFrameUv.frame(2,0));
        assertEquals(1,ParticleAcceleratorFrameUv.frame(3,0));
        assertEquals(2,ParticleAcceleratorFrameUv.frame(0,1));
        assertEquals(7,ParticleAcceleratorFrameUv.frame(2,3));
        assertEquals(0,ParticleAcceleratorFrameUv.frame(4,0));
        float[] last=ParticleAcceleratorFrameUv.bounds(2,3);
        assertTrue(last[0]>=1f/3 && last[1]>=2f/3);
        assertTrue(last[2]<=1 && last[3]<=1);
    }

    @Test void mapsMinecraftMotionToRsiEnumStorageOrder() {
        assertEquals(0,ParticleAcceleratorFrameUv.direction(0,1)); // South
        assertEquals(1,ParticleAcceleratorFrameUv.direction(0,-1)); // North
        assertEquals(2,ParticleAcceleratorFrameUv.direction(1,0)); // East
        assertEquals(3,ParticleAcceleratorFrameUv.direction(-1,0)); // West
        assertEquals(2,ParticleAcceleratorFrameUv.direction(1,1)); // southeast tie -> east
        assertEquals(1,ParticleAcceleratorFrameUv.direction(1,-1)); // northeast -> north
        assertEquals(0,ParticleAcceleratorFrameUv.direction(0,0)); // stationary fallback
        assertEquals(0,ParticleAcceleratorFrameUv.direction(0,2)); // vertical motion fallback
    }

    @Test void strengthStatesAreRealThreeByThreeSheets() throws Exception {
        for(int strength=0;strength<=3;strength++) {
            try (var input=getClass().getResourceAsStream("/assets/moonstation14/textures/entity/pa_particle/particle"+strength+".png")) {
            assertNotNull(input);
            var image=ImageIO.read(input);
            assertNotNull(image);
            assertEquals(96,image.getWidth());
            assertEquals(96,image.getHeight());
            }
        }
    }
}
