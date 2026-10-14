package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"movement", "character"})
@PrefixGameTestTemplate(false)
public final class ExternalBodyFlightGameTests {
    private ExternalBodyFlightGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 40)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal"})
    public static void disposalEjectionPassivelyMovesBoundBodyAndExpiresFlight(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        bindAndPrepare(body, level);
        UUID id = body.getUUID();
        Object hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        Vec3 before = body.position();
        var routing = new LoadedWorldDisposalRouting(level);
        require(routing.ejectBody(body, helper.absolutePos(new BlockPos(4, 1, 2)), Direction.WEST, 10), "real disposal routing ejects body");
        require(ExternalBodyFlight.active(body) && body.getDeltaMovement().x < 0, "ejection starts bounded westward flight");
        helper.runAfterDelay(1, () -> {
            require(body.getX() < before.x - .2, "world post hook passively advances native physics without input");
            require(body.getUUID().equals(id) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "flight preserves body identity and hands");
            require(!body.isInvisible() && !body.noPhysics, "flight body remains an ordinary collidable visible entity");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 6, () -> {
                require(!ExternalBodyFlight.active(body) && ExternalBodyFlight.remainingTicks(body) == 0,
                        "flight lease expires on native world clock");
                require(body.getX() < before.x - 2.3, "body traveled through passive flight ticks");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 40)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"containment"})
    public static void worldStepDeduplicatesFlightAndContainmentCancelsIt(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        bindAndPrepare(body, level);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        ExternalBodyFlight.launch(body, new Vec3(.5, 0, 0), 6);
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        step.step(body, 0, 0, false, false, 0).orElseThrow();
        Vec3 advanced = body.position();
        step.step(body, 0, 0, false, true, 180).orElseThrow();
        require(body.position().equals(advanced), "second same-tick input step cannot advance flight twice");
        ExternalBodyFlight.tick(level);
        require(body.position().equals(advanced), "world flight hook deduplicates an already advanced body");
        ExternalBodyFlight.cancel(body);
        require(!ExternalBodyFlight.active(body), "explicit ownership release cancels external flight");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 60)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal-regression"})
    public static void airborneFlightExpiryContinuesPassiveGravityToLanding(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 6, 4));
        bindAndPrepare(body, level);
        UUID id = body.getUUID();
        Object binding = body.playerCharacterBinding();
        Object hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        require(!body.isNoGravity() && !body.onGround(), "human starts airborne with ordinary gravity");
        ExternalBodyFlight.launch(body, new Vec3(.5, 0, 0), 6);
        helper.runAfterDelay(6, () -> {
            require(ExternalBodyFlight.remainingTicks(body) == 0, "bounded flight expires after six world ticks");
            double yAtExpiry = body.getY();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                require(body.getY() < yAtExpiry - .05 && body.getDeltaMovement().y < 0,
                        "ordinary passive gravity continues after the flight lease expires");
                pollForLanding(helper, body, id, binding, hands, 0);
            });
        });
    }

    private static void pollForLanding(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            UUID id, Object binding, Object hands, int elapsed) {
        if (body.onGround()) {
            long firstLandingTime = helper.getLevel().getGameTime();
            double landingHorizontalSpeed = body.getDeltaMovement().horizontalDistance();
            if (body.getDeltaMovement().horizontalDistanceSqr() >= .001)
                require(ExternalBodyFlight.tracked(body),
                        "flight remains tracked at first landing until grounded friction runs");
            helper.runAfterDelay(1, () -> {
                require(helper.getLevel().getGameTime() > firstLandingTime,
                        "a subsequent world post-hook had an opportunity to run");
                require(body.getDeltaMovement().horizontalDistance() < landingHorizontalSpeed * .31,
                        "three resolved 60 Hz friction substeps strongly damp horizontal flight velocity");
                awaitSettled(helper, body, id, binding, hands, 20);
            });
            return;
        }
        if (elapsed >= 25) throw new GameTestAssertException("ordinary gravity did not land the body within the bounded wait");
        helper.runAfterDelay(1, () -> pollForLanding(helper, body, id, binding, hands, elapsed + 1));
    }

    private static void awaitSettled(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            UUID id, Object binding, Object hands, int remaining) {
        if (!ExternalBodyFlight.tracked(body)) {
            require(body.onGround() && body.getDeltaMovement().horizontalDistanceSqr() < .001,
                    "grounded substep friction eventually settles horizontal flight velocity");
            require(body.getUUID().equals(id) && body.playerCharacterBinding().equals(binding)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "settling preserves original body identity, binding, and hands");
            require(((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                    "passive flight settling preserves movement ownership");
            helper.succeed();
            return;
        }
        if (remaining <= 0) throw new GameTestAssertException("ground friction did not settle flight before bounded deadline");
        helper.runAfterDelay(1, () -> awaitSettled(helper, body, id, binding, hands, remaining - 1));
    }

    private static void bindAndPrepare(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                      ServerLevel level) {
        CompoundTag saved = new CompoundTag(), binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID()); binding.putString("Profile", "main"); binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding); body.readAdditionalSaveData(saved);
        body.setNoAi(true);
        require(body.playerCharacterBinding() != null && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "normal human enrollment");
        require(BodySystem.reconcile(body) && LungSystem.reconcile(body)
                && BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "normal body and hands initialized");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
