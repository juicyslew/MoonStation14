package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Confirms a downward route port is independent of the trunk's upward unit-interface port. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisposalVerticalUnitInletGameTests {
    private DisposalVerticalUnitInletGameTests() { }

    @GameTest(template = "disposal_clear", batch = "disposals", timeoutTicks = 10)
    public static void downwardTrunkRouteIsValidUnitInlet(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos trunkPos = unitPos.below();
        var frame = new DisposalOrientation(Direction.DOWN, Direction.NORTH);
        var record = new DisposalTubeRecord(UUID.randomUUID(), trunkPos, "disposal_trunk", frame, true,
                "", "", 0, DisposalTubeRecord.HostKind.EXPOSED, "", null);
        if (!DisposalTubeWorldData.forMutation(level).installExposed(level, record))
            throw new GameTestAssertException("installed actual anchored trunk block below unit");
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = level.getBlockEntity(unitPos);
        if (!(unit instanceof DisposalUnitBlockEntity) || !DisposalUnitBlockEntity.validUnitTrunk(level, trunkPos))
            throw new GameTestAssertException("DOWN route trunk remains a valid inlet with separate upward unit interface");
        helper.succeed();
    }
}
