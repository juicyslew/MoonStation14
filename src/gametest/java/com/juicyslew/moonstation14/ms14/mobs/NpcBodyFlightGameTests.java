package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"npc-flight", "movement"})
public final class NpcBodyFlightGameTests {
    private NpcBodyFlightGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void ordinaryNpcMouseCanLaunchAndAdvanceExternalFlight(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -2; x <= 6; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "ordinary mouse constructor succeeds");
        mouse.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(level.addFreshEntity(mouse), "ordinary resolved NPC mouse joins level");
        require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.canLaunch(mouse),
                "resolved NPC physics is an external-flight consumer without player ownership");
        var id = mouse.getUUID();
        var before = mouse.position();
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(mouse,
                new Vec3(.2, .12, 0), 5);
        require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mouse),
                "flight is tracked on same ordinary body");
        helper.runAfterDelay(2, () -> {
            require(mouse.getUUID().equals(id) && mouse.position().distanceToSqr(before) > .01,
                    "post-level physics advances original NPC body");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void flightContinuesAfterDurationUntilActualLandingSettles(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 8, 8));
        for (int x = -2; x <= 8; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var mouse = createMouse(level, origin);
        var id = mouse.getUUID();
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(mouse,
                new Vec3(.2, .25, 0), 2);
        awaitFlightLanding(helper, mouse, id, 0);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void flightUsesNativeWallCollision(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 8));
        for (int x = -2; x <= 8; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var wall = origin.offset(2, 0, 0);
        for (int y = 0; y < 2; y++) for (int z = -1; z <= 1; z++)
            level.setBlock(wall.offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
        var mouse = createMouse(level, origin);
        double startX = mouse.getX();
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(mouse,
                new Vec3(.2, .05, 0), 8);
        helper.runAfterDelay(6, () -> {
            require(mouse.getX() > startX + .1, "native flight moves toward obstacle");
            require(mouse.getBoundingBox().maxX <= wall.getX() + .001,
                    "native entity sweep clips flight at real solid wall");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void containmentCancelsNpcFlightAndPinsSameBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        var mouse = createMouse(level, origin);
        var id = mouse.getUUID();
        boolean initiallyInvisible = mouse.isInvisible();
        boolean initiallyNoPhysics = mouse.noPhysics;
        boolean initiallyNoGravity = mouse.isNoGravity();
        var service = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var owner = new com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner() {
            private final java.util.UUID identity = java.util.UUID.randomUUID();
            public java.util.UUID identity() { return identity; }
            public net.minecraft.server.level.ServerLevel level() { return level; }
            public Vec3 position() { return new Vec3(origin.getX() + .5, origin.getY() + 2, origin.getZ() + .5); }
            public Stage stage() { return Stage.STATIONARY; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(mouse,
                new Vec3(.2, .2, 0), 8);
        require(service.bind(mouse, owner), "real containment service captures exact NPC during flight");
        Vec3 pinned = mouse.position();
        require(!com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.canLaunch(mouse),
                "contained exact body is not eligible for a new flight until released");
        helper.runAfterDelay(3, () -> {
            require(!com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mouse),
                    "capture cancels flight lease");
            require(level.getEntity(id) == mouse && mouse.position().equals(pinned)
                            && service.ownerOf(mouse).orElse(null) == owner,
                    "containment pins same registered UUID without replacement");
            require(service.unbind(mouse, owner), "capture owner releases original body");
            require(mouse.isInvisible() == initiallyInvisible && mouse.noPhysics == initiallyNoPhysics
                            && mouse.isNoGravity() == initiallyNoGravity,
                    "release restores native collision, gravity, and visibility flags");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void npcFlightDoesNotClaimPlayerMovementOwnership(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(8, 2, 8));
        var mouse = createMouse(level, origin);
        require(!((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) mouse)
                        .moonstation14$isMovementOwned(),
                "ordinary NPC starts without player movement ownership");
        Vec3 start = mouse.position();
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(mouse,
                new Vec3(.2, .15, 0), 5);
        helper.runAfterDelay(2, () -> {
            require(mouse.position().distanceToSqr(start) > .01,
                    "externally forced flight advances ordinary NPC body");
            require(!((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) mouse)
                            .moonstation14$isMovementOwned(),
                    "external physics never enrolls NPC into player ownership");
            helper.succeed();
        });
    }

    private static com.juicyslew.moonstation14.entities.MouseMob createMouse(
            net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "ordinary mouse constructed");
        mouse.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(mouse), "ordinary mouse added to level");
        return mouse;
    }

    private static void awaitFlightLanding(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, java.util.UUID id, int elapsed) {
        if (mouse.onGround() && mouse.getDeltaMovement().lengthSqr() == 0) {
            require(!com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mouse),
                    "lease ends only after native grounding and zero velocity");
            require(helper.getLevel().getEntity(id) == mouse, "landing preserves original body UUID");
            helper.succeed();
            return;
        }
        if (elapsed >= 65) throw new GameTestAssertException("native NPC flight did not settle: "
                + mouse.position() + " velocity=" + mouse.getDeltaMovement() + " tracked="
                + com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mouse));
        if (com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.remainingTicks(mouse) == 0)
            require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mouse),
                    "duration expiry alone does not cancel airborne flight");
        helper.runAfterDelay(1, () -> awaitFlightLanding(helper, mouse, id, elapsed + 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
