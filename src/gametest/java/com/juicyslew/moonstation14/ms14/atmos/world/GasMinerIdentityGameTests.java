package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native placement-incarnation persistence coverage for ordinary gas miners. */
@PrefixGameTestTemplate(false)
public final class GasMinerIdentityGameTests {
    private GasMinerIdentityGameTests() { }

    @GameTestTags({"atmosphere", "gas-miner-runtime"})
    @GameTest(template = "atmos_large_empty", batch = "gas_miner_identity", timeoutTicks = 100,
            templateNamespace = "moonstation14")
    public static void placementIdentityTracksReplacementAndPersistsAcrossNbt(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.acquire(helper, new AABB(pos).inflate(2));
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40, () -> true,
                "gas miner identity host entity-ticking", () -> {
        helper.setBlock(local, ModBlocks.GAS_MINER_OXYGEN.get());
        var original = (GasMinerBlockEntity) level.getBlockEntity(pos);
        require(original != null, "placed miner BE exists");
        GasMinerBlockEntity.tick(level, original);
        var identity = original.placementIdentity();
        require(identity != null && identity.equals(original.placementIdentity()), "identity minted and stable on live miner");
        var saved = original.saveWithFullMetadata(level.registryAccess());

        helper.setBlock(local, Blocks.AIR);
        helper.setBlock(local, ModBlocks.GAS_MINER_OXYGEN.get());
        var replacement = (GasMinerBlockEntity) level.getBlockEntity(pos);
        require(replacement != null && replacement != original,
                "replacement is a new live block entity");
        GasMinerBlockEntity.tick(level, replacement);
        require(replacement.placementIdentity() != null && !identity.equals(replacement.placementIdentity()),
                "new live placement receives fresh identity");
        replacement.loadWithComponents(saved, level.registryAccess());
        require(identity.equals(replacement.placementIdentity()), "normal native BE NBT load on live host retains original placement UUID");
        helper.succeed();
        }, pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
