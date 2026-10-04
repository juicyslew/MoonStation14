package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.chat.server.LocalSpeechOcclusion;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import net.minecraft.resources.ResourceKey;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
@GameTestTags({"explosion", "power", "chemistry"})
public final class ExplosionDownstreamGameTests {
    private static final BlastPlanner.Proposal PROPOSAL = new BlastPlanner.Proposal(120, 3, 10);
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");

    private ExplosionDownstreamGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void blastFloorMovesPuddleToPreparedLandingSupport(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = pos(helper, 1, 2, 2);
        BlockPos floor = pos(helper, 2, 2, 2);
        BlockPos puddlePos = pos(helper, 2, 3, 2);
        BlockPos landing = pos(helper, 2, 1, 2);
        level.setBlock(floor, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        // A protected support keeps this lifecycle fixture focused on floor removal; ordinary
        // stone is intentionally vulnerable to the same blast policy as station materials.
        level.setBlock(landing.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        level.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(WATER, 12f)));
        require(level.getBlockState(landing.below()).is(Blocks.BEDROCK), "landing support is protected below breach");
        var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
        // Removing the floor triggers the puddle's ordinary gravity update before the planner
        // reaches that observed voxel. Revalidation correctly reports PARTIAL rather than
        // overwriting the newly moved block entity with stale blast state.
        require(result.status() == ServerExplosionService.Status.PARTIAL, "station floor blast partially applied after puddle moves: " + result);
        require(level.getBlockState(floor).isAir(), "blast removed station floor beneath puddle");
        require(level.getBlockState(landing).is(ModBlocks.PUDDLE.get()), "neighbor update moved puddle onto landing support");
        require(level.getBlockState(puddlePos).isAir(), "puddle vacated its original position");
        var moved = (PuddleBlockEntity) level.getBlockEntity(landing);
        require(MS14Provider.get(moved, MS14Bridges.REAGENT).getMap().getOrDefault(WATER, 0f) == 12f,
                "gravity movement preserves all reagent units");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void breachedSteelWallOpensPreviouslyOccludedSpeechPath(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos nearWall = pos(helper, 4, 1, 2);
        BlockPos farWall = pos(helper, 5, 1, 3);
        level.setBlock(nearWall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(farWall, Blocks.BEDROCK.defaultBlockState(), 3);
        Vec3 sourceEye = eye(helper, 1, 1.6, 2);
        Vec3 listenerEye = eye(helper, 10, 1.6, 2);
        require(!LocalSpeechOcclusion.audible(level, sourceEye, listenerEye), "steel wall initially blocks speech");
        // The protected control is immune to the blast, but sits outside the speech ray.
        var result = ServerExplosionService.execute(level, pos(helper, 3, 1, 2), ModExplosions.DEFAULT_ID, PROPOSAL);
        require(result.status() == ServerExplosionService.Status.APPLIED, "steel wall blast applied: " + result);
        require(level.getBlockState(nearWall).isAir(), "target steel wall breached");
        require(level.getBlockState(farWall).is(Blocks.BEDROCK), "immune bedrock control remains intact");
        require(LocalSpeechOcclusion.audible(level, sourceEye, listenerEye), "same eye positions are audible after breach");
        helper.succeed();
    }

    private static BlockPos pos(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    private static Vec3 eye(GameTestHelper helper, int x, double y, int z) {
        BlockPos base = helper.absolutePos(new BlockPos(x, 0, z));
        return new Vec3(base.getX() + 0.5, base.getY() + y, base.getZ() + 0.5);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
