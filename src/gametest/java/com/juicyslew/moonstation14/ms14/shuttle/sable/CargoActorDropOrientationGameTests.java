package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises an ordinary native yaw and the real actor-authorized hand-drop consumer. */
@PrefixGameTestTemplate(false)
public final class CargoActorDropOrientationGameTests {
    private CargoActorDropOrientationGameTests() { }

    @GameTestTags({"shuttle-cargo-drop", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 500)
    public static void actorReplacementCapturesFrameWorldBasis(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2000, -47, 1500), "cargo-drop-basis",
                2.5, 1, 2.5, (fixture, body) -> {
                    MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () ->
                            verifyAndDrop(helper, fixture, body));
                });
    }

    private static void verifyAndDrop(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                     net.minecraft.world.entity.LivingEntity body) {
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(fixture.level(), body);
        var expected = point == null ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        fixture.level(), point.frameId(), point.storagePoint(), false, 1f);
        if (expected == null || expected.equals(new org.joml.Quaterniond(), 1.0e-4)) {
            helper.fail("native yaw did not provide nonidentity admitted frame basis: point=" + point + " q=" + expected);
            return;
        }
        ItemStack stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("orientation cargo"));
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var contents = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment();
        contents.specificAdd(water, 5f, ((com.juicyslew.moonstation14.item.custom.JugItem)
                com.juicyslew.moonstation14.item.ModItems.JUG.get()).getCapacity());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), contents.toComponent());
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.activeHand().isBlank()) {
            helper.fail("moving cabin body has no active live hand");
            return;
        }
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                .forLevel(fixture.level());
        var ledgerBefore = ledger.snapshot();
        var issued = com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool.issue(
                fixture.owner().actor(), hands.activeHand(), stack);
        var held = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (held == null || !held.token(issued.hand()).filter(issued.token()::equals).isPresent()
                || !held.stackCopy(issued.hand()).filter(value -> ItemStack.matches(stack, value)).isPresent()) {
            helper.fail("fixture issue did not publish exact source token and complete stack");
            return;
        }
        AtomicReference<UUID> committed = new AtomicReference<>();
        var result = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.drop(
                fixture.owner().actor(), issued.hand(), issued.token(), issued.revision(), committed::set);
        UUID id = committed.get();
        var loose = id == null ? null : fixture.level().getEntity(id);
        var after = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        LooseItemEntity cargo = loose instanceof LooseItemEntity item ? item : null;
        if (result != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS
                || cargo == null || after == null || after.stackCopy(issued.hand()).isPresent()
                || after.token(issued.hand()).isPresent() || !ItemStack.matches(stack, cargo.getItem())) {
            if (cargo != null) cargo.discard();
            helper.fail("normal authorized hand drop failed conservation: result=" + result + " id=" + id
                    + " entity=" + loose + " hands=" + after);
            return;
        }
        var snapshot = cargo.orientationSnapshot();
        var seeded = cargo.authoritativeQueryOrientation(fixture.level());
        var preMovePoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(fixture.level(), cargo);
        var ledgerAfter = ledger.snapshot();
        boolean valid = snapshot != null && snapshot.frameId() == null && seeded != null
                && expected.equals(seeded, 1.0e-6) && preMovePoint != null && preMovePoint.frameId() == null
                && ledgerBefore.revision() == ledgerAfter.revision()
                && ledgerBefore.locations().equals(ledgerAfter.locations());
        String failure = "replacement drop basis, pre-move membership, or optional-ledger behavior wrong: q=" + snapshot
                + " seeded=" + seeded + " native=" + preMovePoint + " ledgerBefore=" + ledgerBefore.locations()
                + " ledgerAfter=" + ledgerAfter.locations() + " expectedBasis=" + expected;
        cargo.discard();
        if (!valid) helper.fail(failure);
        else helper.succeed();
    }
}
