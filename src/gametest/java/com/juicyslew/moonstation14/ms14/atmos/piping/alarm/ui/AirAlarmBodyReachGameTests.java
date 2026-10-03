package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirAlarmBodyReachGameTests {
    private AirAlarmBodyReachGameTests() { }

    // Exercise only the body-side geometric gate; this is deliberately not an authenticated menu test.
    @GameTest(template = "empty", batch = "air_alarm_body_reach_isolated")
    public static void reachesActualAlarmShapeIndependentOfAimAndFailsClosed(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos alarmPos = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState(), 3);
        var state = level.getBlockState(alarmPos);
        var bounds = state.getShape(level, alarmPos).bounds();
        Vec3 point = new Vec3(alarmPos.getX() + (bounds.minX + bounds.maxX) * .5,
                alarmPos.getY() + (bounds.minY + bounds.maxY) * .5,
                alarmPos.getZ() + (bounds.minZ + bounds.maxZ) * .5);
        LivingEntity body = EntityType.ZOMBIE.create(level);
        if (body == null) throw new GameTestAssertException("could not create test body");
        body.moveTo(point.x - 3, point.y - body.getEyeHeight(), point.z, 180, 0); // eye aligned; turned away
        level.addFreshEntity(body);
        require(AirAlarmMenuService.bodyCanReach(level, body, alarmPos, point), "turned-away body within 6 blocks should reach alarm");

        body.moveTo(point.x - 7, point.y - body.getEyeHeight(), point.z, 180, 0);
        require(!AirAlarmMenuService.bodyCanReach(level, body, alarmPos, point), "body beyond 6-block reach must fail");
        body.moveTo(point.x - 3, point.y - body.getEyeHeight(), point.z, 180, 0);
        BlockPos blocker = BlockPos.containing((body.getEyePosition().x + point.x) * .5,
                (body.getEyePosition().y + point.y) * .5, (body.getEyePosition().z + point.z) * .5);
        level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
        require(!AirAlarmMenuService.bodyCanReach(level, body, alarmPos, point), "intervening solid block must block reach");
        level.setBlock(blocker, Blocks.AIR.defaultBlockState(), 3);

        // Keep the supplied point within reach, while target's chunk is just beyond the loaded fixture.
        // Do not assume a fixture's adjacent chunk is unloaded: probe far outside it without force-loading.
        BlockPos remote = new BlockPos((alarmPos.getX() >> 4) * 16 + 1_000_000, alarmPos.getY(), alarmPos.getZ());
        require(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "remote target chunk must start unloaded");
        LivingEntity geometryProbe = EntityType.ZOMBIE.create(level);
        if (geometryProbe == null) throw new GameTestAssertException("could not create unloaded-chunk probe body");
        try {
            geometryProbe.moveTo(remote.getX() - 3, remote.getY(), remote.getZ() + .5, 180, 0);
            Vec3 nearbyPoint = geometryProbe.getEyePosition().add(1, 0, 0);
            require(!AirAlarmMenuService.bodyCanReach(level, geometryProbe, remote, nearbyPoint),
                    "reach into unloaded chunks must fail closed");
            require(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "reach check must not load target chunk");
        } finally {
            geometryProbe.discard();
            body.discard();
        }
        helper.succeed();
    }

    private static void require(boolean result, String message) {
        if (!result) throw new GameTestAssertException(message);
    }
}
