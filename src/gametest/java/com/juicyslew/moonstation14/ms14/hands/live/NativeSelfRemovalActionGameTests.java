package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises self-removal through the ordinary body-action route, including a rear-facing source. */
@PrefixGameTestTemplate(false)
@GameTestTags({"active_capabilities", "timed-interaction", "thrown-combat"})
public final class NativeSelfRemovalActionGameTests {
    private NativeSelfRemovalActionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 140)
    public static void selfRemovalActionRemovesRearEmbeddedKnife(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "SelfRemoveAction");
        var refs = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                Throwable failure = null;
                try { if (refs[0] != null && !refs[0].isRemoved()) refs[0].discard(); } catch (Throwable t) { failure = t; }
                try { if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                try { fixture.close(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                if (failure != null) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        RegisteredPlayerReadyDriver.awaitCharacterReady(helper, fixture, 40, human -> {
            try {
                var body = human.body(); bodyRef[0] = body;
                if (!(body instanceof net.minecraft.world.entity.Mob mob)) throw new GameTestAssertException("registered character is not a mob");
                mob.setNoAi(true);
                var floor = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
                for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                body.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
                body.setDeltaMovement(Vec3.ZERO); body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var actionId = net.minecraft.resources.ResourceLocation.parse("moonstation14:remove_attached_item");
                var before = BodyActionService.query(fixture.player()).orElseThrow();
                if (before.actions().stream().anyMatch(e -> e.actionId().equals(actionId))) throw new GameTestAssertException("self-removal action appeared without an attachment");
                var baselineActions = before.actions().stream()
                        .map(e -> e.actionId() + "|" + e.providerId()).collect(java.util.stream.Collectors.toSet());
                for (var ordinaryId : java.util.List.of(
                        net.minecraft.resources.ResourceLocation.parse("moonstation14:scream"),
                        net.minecraft.resources.ResourceLocation.parse("moonstation14:combat_mode_toggle"))) {
                    if (before.actions().stream().noneMatch(e -> e.actionId().equals(ordinaryId)))
                        throw new GameTestAssertException("ordinary baseline action missing: " + ordinaryId
                                + "; actual baseline ids=" + before.actions().stream().map(e -> e.actionId().toString()).toList());
                }

                String marker = "self-removal-" + java.util.UUID.randomUUID();
                var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get());
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(marker));
                var knife = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, 1, -.25), stack);
                refs[0] = knife;
                if (!level.addFreshEntity(knife)) throw new GameTestAssertException("knife world publication failed");
                if (!knife.attachTo(body, body.position().add(0, 1, -.25), new Vec3(0, 0, 1), com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED))
                    throw new GameTestAssertException("knife attachment fixture failed");
                var query = BodyActionService.query(fixture.player()).orElseThrow();
                var entry = query.actions().stream().filter(e -> e.actionId().equals(actionId)).findFirst().orElseThrow();
                var accepted = activate(fixture, actionId, entry.providerId(), query, 1);
                if (accepted.status() != BodyActionResult.Status.ACCEPTED)
                    throw new GameTestAssertException("self-removal action denied: " + accepted.status());
                var initial = activeOperation(body, knife);
                if (initial.durationTicks() != 30) throw new GameTestAssertException("knife operation duration=" + initial.durationTicks());
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                    try {
                        var fresh = BodyActionService.query(fixture.player()).orElseThrow();
                        var currentEntry = fresh.actions().stream().filter(e -> e.actionId().equals(actionId)).findFirst().orElseThrow();
                        var repeated = activate(fixture, actionId, currentEntry.providerId(), fresh, 2);
                        if (repeated.status() != BodyActionResult.Status.ACCEPTED)
                            throw new GameTestAssertException("repeat self-removal was not reported in progress: " + repeated.status());
                        var unchanged = activeOperation(body, knife);
                        if (unchanged.id() != initial.id() || unchanged.startTick() != initial.startTick() || unchanged.dueTick() != initial.dueTick())
                            throw new GameTestAssertException("repeat activation restarted timed removal");
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 27, () -> {
                            if (knife.isRemoved() || knife.attachment().isEmpty() || level.getEntity(knife.getUUID()) != knife)
                                throw new GameTestAssertException("knife removed before timed completion");
                            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 3, () -> {
                                if (level.getEntity(knife.getUUID()) != null || !knife.isRemoved()) throw new GameTestAssertException("knife was not removed at timed completion");
                                var hands = fixture.queryHands(human.epoch());
                                if (hands.hands().stream().map(com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Hand::presentation)
                                        .filter(java.util.Objects::nonNull).map(com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation::stack)
                                        .filter(s -> s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                                && marker.equals(s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString())).count() != 1)
                                    throw new GameTestAssertException("removed knife was not conserved in actor hands");
                                var after = BodyActionService.query(fixture.player()).orElseThrow();
                                if (after.actions().stream().anyMatch(e -> e.actionId().equals(actionId))) throw new GameTestAssertException("self-removal action remained without attachments");
                                var afterActions = after.actions().stream()
                                        .map(e -> e.actionId() + "|" + e.providerId()).collect(java.util.stream.Collectors.toSet());
                                if (!afterActions.containsAll(baselineActions))
                                    throw new GameTestAssertException("ordinary baseline action/provider identities disappeared: "
                                            + baselineActions.stream().filter(identity -> !afterActions.contains(identity)).toList());
                                helper.succeed();
                            });
                        });
                    } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
                });
            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
        });
    }

    private static BodyActionResult activate(
            RegisteredServerPlayerFixture fixture, net.minecraft.resources.ResourceLocation id, String provider,
            BodyActionSnapshot query, long sequence) {
        return BodyActionService.activate(fixture.player(),
                new BodyActionRequest(
                        query.bodyId(), query.bodyEpoch(), query.revision(), id, provider, sequence));
    }

    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation activeOperation(
            net.minecraft.world.entity.LivingEntity body, com.juicyslew.moonstation14.entities.LooseItemEntity source) {
        var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        if (state == null) throw new GameTestAssertException("timed operation state missing");
        return state.operations().stream().filter(o -> o.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
                .filter(o -> o.kind().equals("self-attachment-removal") && o.sourceKey().equals(source.getUUID().toString()))
                .findFirst().orElseThrow(() -> new GameTestAssertException("self-removal operation not active"));
    }
}
