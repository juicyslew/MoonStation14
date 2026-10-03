package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplosionGameTests {
    private static final BlastPlanner.Proposal PROPOSAL = new BlastPlanner.Proposal(120, 3, 10);

    private ExplosionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void trustedServerBlastBreaksStationMaterialsAndRemovesCableHost(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos sourceFloor = pos(helper, 2, 1, 2);
        BlockPos floor = pos(helper, 3, 1, 2);
        BlockPos sourceWall = pos(helper, 7, 2, 2);
        BlockPos wall = pos(helper, 8, 2, 2);
        BlockPos sourceCeiling = pos(helper, 12, 2, 2);
        BlockPos ceiling = pos(helper, 12, 3, 2);
        level.setBlock(floor, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(ceiling, ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState(), 3);
        require(CableStorage.place(level, floor, Direction.UP, CableTier.HV), "floor cable setup");
        require(CableStorage.has(level, floor, Direction.UP, CableTier.HV), "floor cable exists before blast");

        for (BlockPos source : new BlockPos[]{sourceFloor, sourceWall, sourceCeiling}) {
            var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
            require(result.status() == ServerExplosionService.Status.APPLIED,
                    "default server blast applied at " + source + ": " + result);
        }
        require(level.getBlockState(floor).isAir(), "station floor breached to AIR");
        require(level.getBlockState(wall).isAir(), "steel wall breached to AIR");
        require(level.getBlockState(ceiling).isAir(), "ceiling barrier breached to AIR");
        require(!CableStorage.has(level, floor, Direction.UP, CableTier.HV), "destroyed floor host cable cleaned up");
        // Atmosphere topology invalidation is not asserted; this fixture does not prove an enabled service.
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void entityDamageIsAppliedOnceAcrossAdjacentBlastCells(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = pos(helper, 2, 2, 2);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos(helper, 3, 2, 2));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        // Center at the boundary so its bounding box occupies the source voxel and neighboring blast voxels.
        zombie.setPos(source.getX() + 1.0, source.getY(), source.getZ() + 0.5);
        // Allow the entity section index and its bounding box to catch up with the explicit move.
        helper.runAfterDelay(2, () -> {
            var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
            require(result.status() == ServerExplosionService.Status.APPLIED, "server blast applied: " + result);
            require(result.damaged() == 1, "entity intersecting multiple blast cells is hit once: " + result);
            var damage = zombie.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(damage != null && damage.getMap().values().stream().mapToDouble(Float::doubleValue).sum() > 0,
                    "blast applies typed damage attachment");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blastKnocksLivingTargetsOutwardAndSkipsIneligibleTargets(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = pos(helper, 5, 2, 5);
        Zombie east = helper.spawn(EntityType.ZOMBIE, pos(helper, 6, 2, 5));
        Zombie west = helper.spawn(EntityType.ZOMBIE, pos(helper, 4, 2, 5));
        Zombie immovable = helper.spawn(EntityType.ZOMBIE, pos(helper, 5, 2, 6));
        east.setNoAi(true); west.setNoAi(true); immovable.setNoAi(true);
        east.setNoGravity(true); west.setNoGravity(true); immovable.setNoGravity(true);
        // Keep the east target in voxel X6 while its center is west of that voxel's center.
        // Voxel-origin knockback incorrectly points it back toward the blast at X5.
        east.setPos(source.getX() + 0.75, source.getY(), source.getZ() + 0.5);
        west.setPos(source.getX() - 0.75, source.getY(), source.getZ() + 0.5);
        immovable.noPhysics = true;
        helper.runAfterDelay(2, () -> {
            east.setDeltaMovement(10, 0, 0);
            var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
            require(result.status() == ServerExplosionService.Status.APPLIED, "blast applied: " + result);
            require(east.getDeltaMovement().x > 0 && west.getDeltaMovement().x < 0,
                    "opposite targets move outward: east=" + east.getDeltaMovement() + " west=" + west.getDeltaMovement());
            require(east.getDeltaMovement().length() <= 1.500001 && west.getDeltaMovement().length() <= 1.500001,
                    "blast impulse is bounded");
            require(immovable.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO), "no-physics target untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedBlastDoesNotKnockBackLivingTarget(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = pos(helper, 3, 2, 3);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos(helper, 4, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        helper.runAfterDelay(2, () -> {
            var result = ServerExplosionService.execute(level, source,
                    ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "missing-explosion"), PROPOSAL);
            require(result.status() == ServerExplosionService.Status.UNKNOWN_PROTOTYPE, "unknown blast rejected");
            require(zombie.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO), "rejected blast has no motion effect");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryBlocksBreakAndProtectedBlocksAndInvalidSourcesFailClosed(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = pos(helper, 2, 2, 2);
        BlockPos dirt = source.east();
        BlockPos stone = source.west();
        BlockPos bedrock = source.above();
        BlockPos barrier = source.below();
        BlockPos torch = source.east(2);
        BlockPos chestSource = pos(helper, 9, 2, 9);
        BlockPos chest = chestSource.east();
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
        level.setBlock(barrier, Blocks.BARRIER.defaultBlockState(), 3);
        level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, PROPOSAL);
        require(result.status() == ServerExplosionService.Status.APPLIED, "blast completes");
        require(level.getBlockState(dirt).isAir(), "ordinary dirt is breached");
        require(level.getBlockState(stone).isAir(), "ordinary stone is breached");
        require(level.getBlockState(torch).isAir(), "non-colliding torch is breached");
        require(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock remains unchanged");
        require(level.getBlockState(barrier).is(Blocks.BARRIER), "barrier remains unchanged");
        var chestResult = ServerExplosionService.execute(level, chestSource, ModExplosions.DEFAULT_ID, PROPOSAL);
        require(chestResult.status() == ServerExplosionService.Status.APPLIED, "chest blast applied: " + chestResult);
        require(level.getBlockState(chest).isAir(), "ordinary chest is breached");
        require(ServerExplosionService.execute(level, source,
                        ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "missing-explosion"), PROPOSAL).status()
                        == ServerExplosionService.Status.UNKNOWN_PROTOTYPE,
                "unknown prototype fails closed");
        BlockPos invalidY = new BlockPos(source.getX(), level.getMaxBuildHeight(), source.getZ());
        require(ServerExplosionService.execute(level, invalidY, ModExplosions.DEFAULT_ID, PROPOSAL).status()
                        == ServerExplosionService.Status.INVALID_OR_STALE,
                "out-of-height/unknown source fails closed");
        helper.succeed();
    }

    private static BlockPos pos(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
