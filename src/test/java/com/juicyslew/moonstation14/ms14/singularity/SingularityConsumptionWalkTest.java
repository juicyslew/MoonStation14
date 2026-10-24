package com.juicyslew.moonstation14.ms14.singularity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityConsumptionWalkTest {
    @Test void foodUsesFactorThenFlatIncludingNegativeFlat() {
        assertEquals(220, SingularityFoodMath.after(100, 120, 1));
        assertEquals(87, SingularityFoodMath.after(100, -10, .97));
        assertEquals(0, SingularityFoodMath.after(5, -10, 1));
        assertTrue(Double.isNaN(SingularityFoodMath.after(5, Double.NaN, 1)));
        assertEquals(101, SingularityFoodMath.afterOrdinaryEntity(100, 1),
                "ordinary loose item stacks credit once per consumed world entity, not per stack count");
        assertTrue(Double.isNaN(SingularityFoodMath.after(Double.MAX_VALUE, 0, 2)));
        assertEquals(Float.MAX_VALUE, SingularityFoodMath.after(Float.MAX_VALUE, 0, 1));
        assertTrue(Double.isNaN(SingularityFoodMath.afterOrdinaryEntity(Float.MAX_VALUE, Float.MAX_VALUE)));
    }

    @Test void walkPreservesMagnitudeAndAttractionVerticalComponent() {
        Vec3 direction = SingularityWalkMath.direction(.3, .8, -.4, .7, 0, new Vec3(0, 2, 0));
        assertEquals(1, direction.length(), 1e-12);
        assertTrue(direction.y > 0.8, "vertical attraction is added after random-walk flattening");
        assertTrue(Math.abs(SingularityWalkMath.direction(.3, .8, -.4, .7, 0, Vec3.ZERO).y) > 0,
                "random walk retains nonzero vertical movement");
    }

    @Test void escapeBiasIsTransientAndDoesNotChangeChosenSpeed() {
        Vec3 normal = SingularityWalkMath.direction(.2, .3, .1, .7, 0, Vec3.ZERO);
        Vec3 escaping = SingularityWalkMath.direction(.2, .3, .1, .7, 1.8, Vec3.ZERO);
        assertTrue(Math.abs(escaping.y) < Math.abs(normal.y));
        assertTrue(SingularityWalkMath.escapeActive(99, 100));
        assertFalse(SingularityWalkMath.escapeActive(100, 100));
        double chosenSpeed = 2.25;
        assertEquals(chosenSpeed, escaping.scale(chosenSpeed).length(), 1e-12);
    }

    @Test void onlyDropFromAboveLevelOneResetsVelocity() {
        assertTrue(SingularityWalkMath.stageDropResetsVelocity(3, 1));
        assertTrue(SingularityWalkMath.stageDropResetsVelocity(2, 0));
        assertFalse(SingularityWalkMath.stageDropResetsVelocity(1, 1));
        assertFalse(SingularityWalkMath.stageDropResetsVelocity(1, 0));
        assertFalse(SingularityWalkMath.stageDropResetsVelocity(2, 2));
    }
}
