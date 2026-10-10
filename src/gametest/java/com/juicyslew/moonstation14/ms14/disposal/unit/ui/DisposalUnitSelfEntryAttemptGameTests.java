package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "interaction", "movement", "containment"})
@PrefixGameTestTemplate(false)
public final class DisposalUnitSelfEntryAttemptGameTests {
    private DisposalUnitSelfEntryAttemptGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_entry_clock", timeoutTicks = 80)
    public static void liveHumanSelfEntryUsesWorldClockAndBindsOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 3; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        require(unit != null && !unit.getBlockState().getValue(com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock.POWERED),
                "real unit has no available power and no power-test override");

        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        CompoundTag saved = new CompoundTag(), binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID()); binding.putString("Profile", "main"); binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding); body.readAdditionalSaveData(saved);
        body.setNoAi(true);
        require(body.playerCharacterBinding() != null && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "normal human enrollment");
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(body)
                        && com.juicyslew.moonstation14.ms14.lung.LungSystem.reconcile(body)
                        && BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED,
                "normal body, lung and hand bootstrap");
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        UUID bodyId = body.getUUID();
        long started = level.getGameTime();
        var attempt = new DisposalUnitSelfEntryAttempt(unit, body, started);
         require(attempt.requiredTicks() == 10, "resolved half-second entry policy is ten native ticks");
        require(attempt.advance(level.getGameTime(), true).result() == DisposalUnitSelfEntryAttempt.Result.WAIT, "initial advance waits");
        helper.runAfterDelay(9, () -> {
            require(level.getGameTime() - started < attempt.requiredTicks(), "nine real world ticks remain before deadline");
            require(unit.bodyIds().isEmpty() && BodyContainmentService.forLevel(level).ownerOf(body).isEmpty(), "no early insertion");
            require(attempt.advance(level.getGameTime(), true).result() == DisposalUnitSelfEntryAttempt.Result.WAIT, "waits at nine ticks");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                var entered = attempt.advance(level.getGameTime(), true);
                require(level.getGameTime() - started >= attempt.requiredTicks(), "real world clock crossed deadline");
                require(entered.result() == DisposalUnitSelfEntryAttempt.Result.ENTERED, "deadline enters via real unit insertion");
                require(unit.bodyIds().contains(bodyId) && BodyContainmentService.forLevel(level).ownerOf(body).orElse(null) == unit, "unit owns same body");
                var view = BodyContainmentService.forLevel(level).ownerOf(body).orElseThrow().view();
                require(body.noPhysics && body.isInvisible() && view.cameraCollision(), "contained human flags and unit camera view");
                require(body.getUUID().equals(bodyId) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands, "body identity and hand inventory preserved");
                require(attempt.advance(level.getGameTime() + 100, true) == entered && unit.bodyIds().size() == 1, "terminal result is stable without duplicate insertion");
                 GroundedHarnessWorldStep worldStep = new GroundedHarnessWorldStep();
                 runIdleWorldTicks(helper, body, unit, attempt, entered, hands, bodyId, view, worldStep, 8, 0);
            });
        });
    }

    private static void runIdleWorldTicks(GameTestHelper helper, PlayerCharacterHarnessEntity body,
            DisposalUnitBlockEntity unit, DisposalUnitSelfEntryAttempt attempt,
            DisposalUnitSelfEntryAttempt.Outcome entered, Object hands, UUID bodyId,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyView view,
            GroundedHarnessWorldStep worldStep, int remaining, int tick) {
        helper.runAfterDelay(1, () -> {
            var accepted = worldStep.step(body, 0, 0, false, true, tick % 2 == 0 ? 0f : 90f)
                    .orElseThrow(() -> new GameTestAssertException("owned idle world step is eligible"));
            var owner = BodyContainmentService.forLevel(helper.getLevel()).ownerOf(body).orElse(null);
            require(owner == unit && unit.bodyIds().contains(bodyId), "idle input retains actual unit ownership");
            require(accepted.velocity().equals(com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO)
                            && body.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO)
                            && body.noPhysics && body.isInvisible() && owner.view().equals(view)
                            && owner.view().cameraCollision(),
                    "idle world step keeps the body pinned, stationary and contained");
            require(attempt.advance(body.level().getGameTime() + 100, true) == entered
                            && body.getUUID().equals(bodyId)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "idle input preserves terminal binding, body and hands");
            if (remaining > 1) runIdleWorldTicks(helper, body, unit, attempt, entered, hands,
                    bodyId, view, worldStep, remaining - 1, tick + 1);
            else helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_entry_clock", timeoutTicks = 40)
    public static void liveAttemptCancelsOnStaleContextAndMovement(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3)); body.setNoAi(true);
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human enrollment");
        var stale = new DisposalUnitSelfEntryAttempt(unit, body, level.getGameTime());
        require(stale.advance(level.getGameTime(), false).reason() == DisposalUnitSelfEntryAttempt.Reason.AUTHORITY_CHANGED, "stale actor context cancels");
        var moved = new DisposalUnitSelfEntryAttempt(unit, body, level.getGameTime());
        body.setPos(body.getX() + 1, body.getY(), body.getZ());
        require(moved.advance(level.getGameTime() + 20, true).reason() == DisposalUnitSelfEntryAttempt.Reason.MOVED, "movement cancels even after deadline");
        require(unit.bodyIds().isEmpty(), "cancelled attempts never insert");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
