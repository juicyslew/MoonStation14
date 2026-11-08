package com.juicyslew.moonstation14.ms14.singularity.client;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"singularity-generator", "reactor-models"})
@PrefixGameTestTemplate(false)
public final class SingularityGeneratorVisibilityGameTests {
    private SingularityGeneratorVisibilityGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=50)
    @GameTestTags("startup-source-visibility")
    public static void registeredStartupSourceIsVisibleButFullOpaqueCubeIsNot(GameTestHelper h) {
        BlockPos local=new BlockPos(3,3,3), pos=h.absolutePos(local);
        h.setBlock(local,ModBlocks.SINGULARITY_GENERATOR.get());
        ModSingularityVisuals.bootstrapClientBundled();
        var profile=ModSingularityVisuals.get(1);
        if(profile==null||profile.spriteHalfExtent()!=.5f)
            throw new GameTestAssertException("resolved level-1 visual profile uses halfExtent .5: "+profile);
        Vec3 center=Vec3.atCenterOf(pos);
        StringBuilder measured=new StringBuilder();
        int viewIndex=0;
        for(Vec3 forward : new Vec3[]{new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(-1,0,0),
                new Vec3(0,1,1).normalize(),new Vec3(0,-1,-1).normalize()}) {
            Vec3 camera=center.subtract(forward.scale(3));
            var basis=SingularityBillboardBasis.between(center,camera);
            h.setBlock(local,Blocks.STONE);
            float original=SingularitySourceVisibility.fraction(h.getLevel(),h.getLevel()::hasChunkAt,
                    camera,center,profile.spriteHalfExtent(),basis);
            if(original!=0) throw new GameTestAssertException("original opaque full-cube source should block nine probes: "+original);
            h.setBlock(local,ModBlocks.SINGULARITY_GENERATOR.get());
            float fixed=SingularitySourceVisibility.fraction(h.getLevel(),h.getLevel()::hasChunkAt,
                    camera,center,profile.spriteHalfExtent(),basis);
            if(fixed!=1) throw new GameTestAssertException("registered non-occluding partial startup machinery should leave all nine probes clear: "+fixed);
            measured.append(" view").append(viewIndex++).append("=original:").append(Math.round(original*9))
                    .append("/9,registered:").append(Math.round(fixed*9)).append("/9");
        }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("STARTUP_SOURCE_VISIBILITY_TEST level=1 halfExtent={} views=6{}",
                profile.spriteHalfExtent(),measured);
        h.succeed();
    }
}
