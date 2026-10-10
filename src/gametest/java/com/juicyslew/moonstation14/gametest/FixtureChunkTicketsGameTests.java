package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/** Guard the fixture ticket helper against signed world-coordinate chunk-edge regressions. */
@PrefixGameTestTemplate(false)
@GameTestTags({"movement", "full-suite-movement-regression"})
public final class FixtureChunkTicketsGameTests {
    private FixtureChunkTicketsGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void fixtureTicketsKeepBothSidesOfSignedChunkEdgesEntityTicking(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        int negativeXEdge = Math.floorDiv(center.getX(), 16) * 16;
        int positiveZEdge = (Math.floorDiv(center.getZ(), 16) + 1) * 16;
        BlockPos[] positions = {
                new BlockPos(negativeXEdge - 1, center.getY(), positiveZEdge - 1),
                new BlockPos(negativeXEdge, center.getY(), positiveZEdge - 1),
                new BlockPos(negativeXEdge - 1, center.getY(), positiveZEdge),
                new BlockPos(negativeXEdge, center.getY(), positiveZEdge)
        };
        AABB coverage = new AABB(positions[0].getX(), center.getY(), positions[0].getZ(),
                positions[3].getX() + 1, center.getY() + 2, positions[3].getZ() + 1).inflate(1);
        var preexistingForced = Set.copyOf(level.getForcedChunks());
        boolean rootWasEntityTicking = level.isPositionEntityTicking(center);
        var tickets = FixtureChunkTickets.acquire(helper, coverage);
        Villager[] actors = new Villager[positions.length];
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 8, () -> {
            for (int i = 0; i < positions.length; i++) {
                actors[i] = EntityType.VILLAGER.create(level);
                if (actors[i] == null) throw new GameTestAssertException("boundary fixture actor created");
                actors[i].setNoAi(true);
                actors[i].setNoGravity(true);
                actors[i].setPos(positions[i].getX() + .5, positions[i].getY(), positions[i].getZ() + .5);
                if (!level.addFreshEntity(actors[i])) throw new GameTestAssertException("boundary fixture actor joins");
            }
            helper.runAfterDelay(2, () -> {
            for (int i = 0; i < actors.length; i++) {
                if (level.getEntity(actors[i].getUUID()) != actors[i] || !tickets.entityTicking(positions[i]))
                    throw new GameTestAssertException("ticket retains signed-corner actor through natural tick: " + positions[i]);
            }
            tickets.close();
            if (!tickets.isClosed() || !level.getForcedChunks().equals(preexistingForced)
                    || level.isPositionEntityTicking(center) != rootWasEntityTicking)
                throw new GameTestAssertException("fixture ticket removal preserves engine and forced-ticket state");
            helper.succeed();
            });
        }, positions);
    }
}
