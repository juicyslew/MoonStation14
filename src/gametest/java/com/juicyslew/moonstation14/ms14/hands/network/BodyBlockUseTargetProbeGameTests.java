package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** No-server-start regression proof for authorized outline admission through real dynamic tube geometry. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyBlockUseTargetProbeGameTests {
    private BodyBlockUseTargetProbeGameTests() { }

    @GameTest(template = "disposal_clear", batch = "disposals", timeoutTicks = 20)
    public static void liveTubeShapeIsUsedForRayAndWrongFaceAndOcclusionStillReject(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos tube = helper.absolutePos(new BlockPos(5, 2, 5));
        var record = new DisposalTubeRecord(UUID.randomUUID(), tube, "moonstation14:disposal_pipe",
                new DisposalOrientation(Direction.EAST, Direction.UP), true, "", "", 0,
                DisposalTubeRecord.HostKind.EXPOSED, "", null);
        require(DisposalTubeWorldData.forMutation(level).installExposed(level, record), "resolved tube record installed");

        var body = EntityType.ZOMBIE.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setPos(tube.getX() - 1.5, tube.getY() + .88, tube.getZ() + .5);
        body.setYRot(-90); // East, through the tube's live centerline.
        require(level.addFreshEntity(body), "body added");

        var raw = level.clip(new ClipContext(body.getEyePosition(), body.getEyePosition().add(body.getLookAngle().scale(6)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
        require(raw.getType() == HitResult.Type.BLOCK && raw.getBlockPos().equals(tube)
                && raw.getDirection() == Direction.WEST, "vanilla level ray sees the resolved live tube outline");
        var authorized = BodyBlockUseTargetProbe.probe(level, body, tube, Direction.WEST);
        require(authorized != null && authorized.pos().equals(tube) && authorized.face() == Direction.WEST,
                "bounded admission preserves level context for dynamic outline shape");
        require(BodyBlockUseTargetProbe.probe(level, body, tube, Direction.EAST) == null,
                "face nomination remains exact");

        level.setBlock(tube.west(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        require(BodyBlockUseTargetProbe.probe(level, body, tube, Direction.WEST) == null,
                "intervening wall still occludes the nominated tube");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
