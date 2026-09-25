package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GhostMobHarnessGameTests {
    private GhostMobHarnessGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mindGhostCommandIsRegisteredOnDedicatedServer(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var root = dispatcher.getRoot();
        var ms14 = root.getChild("ms14");
        require(ms14 instanceof LiteralCommandNode<?>, "dedicated server command dispatcher has literal root /ms14");

        var mindghost = ms14.getChild("mindghost");
        require(mindghost instanceof LiteralCommandNode<?>, "/ms14 has literal child mindghost");
        require(mindghost.getChild("start") instanceof LiteralCommandNode<?>,
                "/ms14 mindghost has literal child start");
        require(mindghost.getChild("stop") instanceof LiteralCommandNode<?>,
                "/ms14 mindghost has literal child stop");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredGhostIsASeparateNonPlayerMobHarness(GameTestHelper helper) {
        GhostMobHarnessEntity ghost = helper.spawn(GhostMobHarnessRegistration.getEntityType(), new BlockPos(1, 1, 1));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));

        helper.runAfterDelay(1, () -> {
            require(ghost.getType() == GhostMobHarnessRegistration.getEntityType(), "ghost uses its registered type");
            require(GhostMobHarnessRegistration.ID.toString().equals("moonstation14:ghost_mob_harness"),
                    "ghost has the intended unique registry id");
            require(ghost instanceof LivingEntity && ghost instanceof Mob, "ghost is a living Mob");
            Entity entity = ghost;
            require(!(entity instanceof ServerPlayer), "ghost is not a player");
            require(ghost.isNoAi() && ghost.isNoGravity() && ghost.noPhysics,
                    "ghost has no AI or gravity and explicitly ignores collision");
            require(villager.getType() == EntityType.VILLAGER && villager != (Object) ghost,
                    "ghost harness remains distinct from an ordinary villager");
            require(ghost.isRemoved() == false, "non-saved ghost remains present in the live world");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ghostRemainsStationaryWithoutInput(GameTestHelper helper) {
        GhostMobHarnessEntity ghost = helper.spawn(GhostMobHarnessRegistration.getEntityType(), new BlockPos(1, 1, 1));
        Vec3 spawnPosition = ghost.position();

        helper.runAfterDelay(6, () -> {
            Vec3 position = ghost.position();
            Vec3 velocity = ghost.getDeltaMovement();
            require(position.distanceToSqr(spawnPosition) <= 1.0e-8,
                    "ghost position changed without input over 6 ticks: spawn=" + spawnPosition + ", current=" + position);
            require(velocity.lengthSqr() <= 1.0e-8,
                    "ghost gained velocity without input over 6 ticks: velocity=" + velocity);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ghostMovesThroughRegularStoneWithNoPhysics(GameTestHelper helper) {
        GhostMobHarnessEntity ghost = helper.spawn(GhostMobHarnessRegistration.getEntityType(), new BlockPos(1, 1, 1));
        BlockPos wall = new BlockPos(2, 1, 1);
        helper.setBlock(wall, Blocks.STONE);
        BlockPos absoluteWall = helper.absolutePos(wall);
        Vec3 start = ghost.position();
        double wallMinX = absoluteWall.getX();
        double wallMaxX = wallMinX + 1.0;
        Vec3 requestedMovement = new Vec3(2.0, 0.0, 0.0);
        Vec3 endpoint = start.add(requestedMovement);

        require(helper.getLevel().getBlockState(absoluteWall).is(Blocks.STONE), "regular stone wall was placed");
        require(start.x < wallMinX && endpoint.x > wallMaxX
                        && start.y < absoluteWall.getY() + 1.0 && start.y + ghost.getBbHeight() > absoluteWall.getY()
                        && start.z + ghost.getBbWidth() / 2.0 > absoluteWall.getZ()
                        && start.z - ghost.getBbWidth() / 2.0 < absoluteWall.getZ() + 1.0,
                "stone wall is between the ghost's start and requested endpoint: start=" + start + ", endpoint=" + endpoint);

        ghost.move(MoverType.SELF, requestedMovement);

        require(ghost.position().distanceToSqr(endpoint) <= 1.0e-8,
                "noPhysics ghost did not reach the requested endpoint through regular stone: start=" + start
                        + ", endpoint=" + endpoint + ", current=" + ghost.position());
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
