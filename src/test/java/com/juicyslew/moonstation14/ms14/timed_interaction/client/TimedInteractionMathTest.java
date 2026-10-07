package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TimedInteractionMathTest {
    @Test
    void predictionAcknowledgementRequiresMatchingBodyAndSequence() {
        var body = new java.util.UUID(0, 1);
        var remoteBody = new java.util.UUID(0, 2);
        assertTrue(TimedInteractionPredictionIdentity.matches(body, 17, body, 17));
        assertFalse(TimedInteractionPredictionIdentity.matches(body, 17, remoteBody, 17));
        assertFalse(TimedInteractionPredictionIdentity.matches(body, 17, body, 18));
    }

    @Test
    void fractionalProgressAndFillAreClamped() {
        assertEquals(.25f, TimedInteractionMath.progress(10, 20, 15.0));
        assertEquals(0, TimedInteractionMath.progress(10, 20, 0));
        assertEquals(1, TimedInteractionMath.progress(10, 20, 40));
        assertEquals(0, TimedInteractionMath.fillPixels(-1));
        assertEquals(0, TimedInteractionMath.fillPixels(0));
        assertEquals(10, TimedInteractionMath.fillPixels(.5f));
        assertEquals(20, TimedInteractionMath.fillPixels(1));
        assertEquals(20, TimedInteractionMath.fillPixels(2));
    }

    @Test
    void stackingCompletionColorAndCancelledProgressStayStable() {
        assertEquals(0, TimedInteractionMath.stackOffset(0));
        assertEquals(7, TimedInteractionMath.stackOffset(1));
        assertEquals(28, TimedInteractionMath.stackOffset(4));
        assertEquals(7.0/32.0, TimedInteractionMath.stackOffset(1)/32.0);
        assertEquals(0xff00ff00, TimedInteractionMath.color(.2f, TimedOperation.Phase.COMPLETED));
        assertEquals(0xff00ff00, TimedInteractionMath.color(1f, TimedOperation.Phase.ACTIVE));
        assertEquals(0xffbf0000, TimedInteractionMath.color(.5f, TimedOperation.Phase.CANCELLED));
        float frozen=TimedInteractionMath.progress(10,20,17.5);
        assertEquals(frozen, TimedInteractionMath.progress(10,20,17.5));
    }

    @Test
    void cancelledFillFlashesStartingVisible() {
        assertEquals(1f, TimedInteractionMath.cancellationFillAlpha(10, 9));
        assertEquals(1f, TimedInteractionMath.cancellationFillAlpha(10, 10));
        assertEquals(0f, TimedInteractionMath.cancellationFillAlpha(10, 12.5));
        assertEquals(1f, TimedInteractionMath.cancellationFillAlpha(10, 15));
    }

    @Test
    void firstPersonAnchorProjectsForwardAndStacksAtRenderedScale() {
        var camera=new net.minecraft.world.phys.Vec3(1,2,3);
        double projectionYScale=1.0/Math.tan(Math.toRadians(35));
        var anchor=TimedInteractionMath.firstPersonAnchor(camera,new net.minecraft.world.phys.Vec3(0,0,-1),
                new net.minecraft.world.phys.Vec3(0,1,0),0,projectionYScale);
        assertEquals(TimedInteractionMath.firstPersonVerticalOffset(projectionYScale), anchor.y-camera.y,1e-9);
        assertEquals(-.25, anchor.z-camera.z,1e-9);
        double width=TimedInteractionMath.WIDTH*TimedInteractionMath.FIRST_PERSON_SCALE;
        double height=TimedInteractionMath.HEIGHT*TimedInteractionMath.FIRST_PERSON_SCALE;
        assertEquals(24.0/384.0,width,1e-7);
        assertEquals(7.0/384.0,height,1e-7);
        assertTrue(2*Math.atan(width/2/TimedInteractionMath.FIRST_PERSON_DISTANCE)<Math.toRadians(90));
        assertTrue(TimedInteractionMath.ACTOR_BAR_CLEARANCE - 3*TimedInteractionMath.ACTOR_SCALE >= .15);
        var stacked=TimedInteractionMath.firstPersonAnchor(camera,new net.minecraft.world.phys.Vec3(0,0,-1),
                new net.minecraft.world.phys.Vec3(0,1,0),1,projectionYScale);
        assertEquals(7.0/384.0,stacked.y-anchor.y,1e-7);
        for(double fov:new double[]{30,70,110}) {
            double scale=1.0/Math.tan(Math.toRadians(fov/2));
            double half=TimedInteractionMath.firstPersonVerticalHalfExtent(scale);
            double normalized=TimedInteractionMath.firstPersonVerticalOffset(scale)/half;
            assertTrue(normalized>=-.75&&normalized<=-.60);
            double barBottom=normalized+TimedInteractionMath.HEIGHT*TimedInteractionMath.FIRST_PERSON_SCALE/2/half;
            assertTrue(barBottom>=-.85&&barBottom<0);
            assertTrue(TimedInteractionMath.WIDTH*TimedInteractionMath.FIRST_PERSON_SCALE/2
                    <TimedInteractionMath.FIRST_PERSON_DISTANCE*Math.tan(Math.toRadians(fov/2)));
        }
        assertEquals(TimedInteractionMath.firstPersonVerticalOffset(Double.NaN),TimedInteractionMath.firstPersonVerticalOffset(0),1e-12);
    }

    @Test
    void progressAssetAndFillRectangleFitExpectedBounds() throws IOException {
        Path asset = projectRoot().resolve("src/main/resources/assets/moonstation14/textures/gui/timed_interaction_progress.png");
        var image=ImageIO.read(asset.toFile());
        assertEquals(24, image.getWidth());
        assertEquals(7, image.getHeight());
        assertEquals(20, TimedInteractionMath.fillPixels(1));
        assertEquals(20, (TimedInteractionMath.FILL_LEFT+TimedInteractionMath.fillPixels(1))-TimedInteractionMath.FILL_LEFT);
        assertEquals(1, TimedInteractionMath.FILL_HEIGHT);
        assertEquals(22, TimedInteractionMath.FILL_LEFT + TimedInteractionMath.fillPixels(1) - (-12));
        assertEquals(3, TimedInteractionMath.FILL_TOP - (-3));
        assertEquals(24, 12-(-12));
        assertEquals(7, 4-(-3));
    }

    private static Path projectRoot() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (java.nio.file.Files.isDirectory(path.resolve("src/main/resources/assets/moonstation14"))) return path;
        }
        throw new IllegalStateException("Could not locate project root");
    }
}
