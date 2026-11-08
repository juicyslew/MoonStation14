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

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    public static void floorClippedAttachmentPassesSelectedGeometryAdmission(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "FloorSelect");
        var items = new com.juicyslew.moonstation14.entities.LooseItemEntity[2];
        var actors = new net.minecraft.world.entity.LivingEntity[2];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                for (var item : items) if (item != null && !item.isRemoved()) item.discard();
                for (var actor : actors) if (actor != null && !actor.isRemoved()) actor.discard();
                try { fixture.close(); } catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        RegisteredPlayerReadyDriver.awaitCharacterReady(helper, fixture, 40, human -> {
            try {
                var body = human.body(); actors[0] = body;
                ((net.minecraft.world.entity.Mob) body).setNoAi(true);
                var floor = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
                for (int x = -1; x <= 4; x++) for (int z = -1; z <= 4; z++)
                    level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                body.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
                body.setDeltaMovement(Vec3.ZERO); body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                if (villager == null) throw new GameTestAssertException("villager creation failed");
                actors[1] = (net.minecraft.world.entity.LivingEntity) villager;
                ((net.minecraft.world.entity.Mob) villager).setNoAi(true);
                villager.setPos(body.position().add(0, 0, 2));
                if (!level.addFreshEntity(villager)) throw new GameTestAssertException("villager publication failed");
                var contact = new Vec3(villager.getX(), floor.getY() - .1, villager.getZ());
                var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get());
                var dart = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                        contact, stack);
                items[0] = dart;
                if (!level.addFreshEntity(dart) || !dart.attachTo(villager,
                        contact, new Vec3(0, 0, 1),
                        com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED))
                    throw new GameTestAssertException("floor-clipped attachment setup failed");
                var probe = BodyItemTargetProbe.probeSelectedDiagnostic(level, body, dart.getUUID(), 5);
                if (probe.target() == null || !dart.getUUID().equals(probe.target().itemId())
                        || !BodyItemTargetProbe.probeSelectedGeometry(level, body, dart, 5))
                    throw new GameTestAssertException("extended upper geometry did not admit floor-clipped nominated item");
                // The contact is at the villager, two blocks ahead of the observer.
                var wall = floor.offset(0, 0, 1);
                var wallBlocks = new java.util.HashSet<net.minecraft.core.BlockPos>();
                for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) {
                    var block = wall.offset(x, y, 0);
                    wallBlocks.add(block);
                    level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                }
                var wallRay = level.clip(new net.minecraft.world.level.ClipContext(body.getEyePosition(),
                        probe.target().hitLocation(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, body));
                if (wallRay.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                        || !wallBlocks.contains(wallRay.getBlockPos()))
                    throw new GameTestAssertException("wall negative control ray did not hit placed wall; type="
                            + wallRay.getType() + " blockPos=" + wallRay.getBlockPos() + " location="
                            + wallRay.getLocation() + " eye=" + body.getEyePosition() + " end=" + probe.target().hitLocation());
                if (BodyItemTargetProbe.probeSelectedDiagnostic(level, body, dart.getUUID(), 5).target() != null)
                    throw new GameTestAssertException("stone wall did not occlude selected geometry");
                for (var block : wallBlocks) level.setBlock(block, Blocks.AIR.defaultBlockState(), 3);
                var clearRay = level.clip(new net.minecraft.world.level.ClipContext(body.getEyePosition(),
                        probe.target().hitLocation(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, body));
                if (clearRay.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
                    throw new GameTestAssertException("wall removal did not clear endpoint ray: " + clearRay);
                if (!BodyItemTargetProbe.probeSelectedGeometry(level, body, dart, 5)
                        || BodyItemTargetProbe.probeSelectedDiagnostic(level, body, dart.getUUID(), 5).target() == null)
                    throw new GameTestAssertException("removing wall did not restore selected geometry");
                var selfStack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get());
                var self = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position(), selfStack);
                items[1] = self;
                if (!level.addFreshEntity(self) || !self.attachTo(body, body.position(), new Vec3(0, 0, 1),
                        com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED))
                    throw new GameTestAssertException("self attachment setup failed");
                if (BodyItemTargetProbe.probeSelectedDiagnostic(level, body, self.getUUID(), 5).target() != null)
                    throw new GameTestAssertException("world selection admitted item attached to the observing body");
                helper.succeed();
            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 80)
    public static void actualProneTransitionKeepsFloorClippedAttachmentSelectable(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "ProneSelect");
        var itemRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var targetRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                if (itemRef[0] != null && !itemRef[0].isRemoved()) itemRef[0].discard();
                if (targetRef[0] != null && !targetRef[0].isRemoved()) targetRef[0].discard();
                try { fixture.close(); } catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        RegisteredPlayerReadyDriver.awaitCharacterReady(helper, fixture, 40, human -> {
            var observer = human.body();
            ((net.minecraft.world.entity.Mob) observer).setNoAi(true);
            var floor = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
            for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++)
                level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            observer.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
            observer.setDeltaMovement(Vec3.ZERO); observer.setYRot(0); observer.setYHeadRot(0); observer.setXRot(0);
            var target = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (target == null) throw new GameTestAssertException("villager creation failed");
            targetRef[0] = target;
            ((net.minecraft.world.entity.Mob) target).setNoAi(true);
            target.setPos(observer.position().add(0, 0, 2));
            if (!level.addFreshEntity(target) || !com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(
                    target, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID))
                throw new GameTestAssertException("villager publication/enrollment failed");
            var contact = target.position().add(0, .2, -target.getBbWidth() / 2);
            var dart = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, contact,
                    new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get()));
            itemRef[0] = dart;
            if (!level.addFreshEntity(dart) || !dart.attachTo(target, contact, new Vec3(0, 0, -1),
                    com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED))
                throw new GameTestAssertException("standing torso attachment setup failed");
            if (!com.juicyslew.moonstation14.ms14.standing.PostureSystem.voluntaryToggle(target))
                throw new GameTestAssertException("real posture system refused prone transition");
            if (!com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(target))
                throw new GameTestAssertException("target did not become prone");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                var attachment = dart.attachment().orElseThrow(() -> new GameTestAssertException("attachment detached after prone tick"));
                var point = com.juicyslew.moonstation14.entities.AttachmentPoseGeometry.bodyToWorld(target, attachment.targetLocalContact());
                if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                        || point.distanceTo(dart.position()) > .05)
                    throw new GameTestAssertException("prone attachment world point did not track its actual projection");
                var selectionBounds = BodyItemTargetProbe.selectionBounds(dart);
                if (!selectionBounds.contains(point))
                    throw new GameTestAssertException("floor-clipped prone contact escaped admitted selection bounds");
                if (!BodyItemTargetProbe.probeSelectedGeometry(level, observer, dart, 5))
                    throw new GameTestAssertException("actual prone attachment rejected by selected geometry from above floor");
                var selected = BodyItemTargetProbe.probeSelected(level, observer, dart.getUUID(), 5);
                if (selected == null || !dart.getUUID().equals(selected.itemId()))
                    throw new GameTestAssertException("actual prone attachment was not selected by its UUID");
                helper.succeed();
            });
        });
    }

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
