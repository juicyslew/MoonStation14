package com.juicyslew.moonstation14.ms14.disposal.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalFixtureContextTest {
    private static final ResourceKey<Level> OVERWORLD=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.withDefaultNamespace("the_nether"));

    @Test void sitesIsolateOperatorAndDimensionAndOnlySuccessfulBuildIsRemembered(){
        var context=new DisposalFixtureSiteContext();var one=UUID.randomUUID();var two=UUID.randomUUID();
        var first=new BlockPos(22,1,24);var second=new BlockPos(30,2,40);
        context.buildResult(one,OVERWORLD,first,false);
        assertNull(context.find(one,OVERWORLD));
        context.buildResult(one,OVERWORLD,first,true);
        assertEquals(first,context.find(one,OVERWORLD));
        assertNull(context.find(two,OVERWORLD));assertNull(context.find(one,NETHER));
        context.buildResult(one,OVERWORLD,second,false);
        assertEquals(first,context.find(one,OVERWORLD),"refused/partial build must not replace prior success");
        context.remember(one,NETHER,second);
        assertEquals(second,context.find(one,NETHER));assertEquals(first,context.find(one,OVERWORLD));
        context.clear();
        assertNull(context.find(one,OVERWORLD),"server stop clears runtime context before next server lifecycle");
    }
}
