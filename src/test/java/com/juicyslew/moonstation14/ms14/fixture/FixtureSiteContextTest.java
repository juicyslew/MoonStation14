package com.juicyslew.moonstation14.ms14.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FixtureSiteContextTest {
    @Test void successfulSitesAreIsolatedAndFailedBuildCannotReplaceThem() {
        var context = new FixtureSiteContext();
        var operator = UUID.randomUUID();
        var other = UUID.randomUUID();
        var dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
        var otherDimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
        var first = new BlockPos(1, 2, 3);
        context.buildResult(operator, dimension, first, false);
        assertNull(context.find(operator, dimension));
        context.buildResult(operator, dimension, first, true);
        context.buildResult(operator, dimension, new BlockPos(9, 9, 9), false);
        assertEquals(first, context.find(operator, dimension));
        assertNull(context.find(other, dimension));
        assertNull(context.find(operator, otherDimension));
        context.clear();
        assertNull(context.find(operator, dimension));
    }
}
