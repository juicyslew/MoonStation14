package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-output-compensation"})
public final class FreshWorldOutputBatchGameTests {
    private FreshWorldOutputBatchGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"botany-harvest"})
    public static void bodyHarvestPublishesStationaryScatteredFreshOutputs(GameTestHelper helper) {
        LivingEntity actor = null;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            actor = EntityType.VILLAGER.create(level);
            require(actor != null, "harvest actor created");
            actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            require(level.addFreshEntity(actor), "harvest actor added");
            LivingEntity harvestActor = actor;
            var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(
                    new ItemStack(ModItems.WHEAT_BUSHEL.get()), false);
            var bodyOrigin = harvestActor.position();
            var before = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            var stacks = java.util.List.of(new ItemStack(ModItems.WHEAT_BUSHEL.get()),
                    new ItemStack(ModItems.WHEAT_BUSHEL.get()), new ItemStack(ModItems.WHEAT_BUSHEL.get()));
            var commits = new java.util.concurrent.atomic.AtomicInteger();
            var result = FreshWorldOutputBatch.publishBodyHarvest(level, harvestActor, stacks, () -> true, commits::incrementAndGet);
            var outputs = entitiesNear(level, harvestActor).stream().filter(e -> !before.contains(e.getUUID())).toList();
            require(result == BodyHandItemTransfer.Result.SUCCESS && commits.get() == 1, "harvest commits source exactly once");
            require(outputs.size() == 3 && outputs.stream().allMatch(e -> e.getItem().getCount() == 1), "all exact outputs published");
            require(outputs.stream().map(e -> e.position().x + "," + e.position().z).distinct().count() >= 2,
                    "actual level RNG scatters outputs to distinct positions");
             require(outputs.stream().allMatch(e -> Math.abs(e.position().x - bodyOrigin.x) <= .25
                     && Math.abs(e.position().z - bodyOrigin.z) <= .25
                     && e.position().y >= bodyOrigin.y
                     && e.getDeltaMovement().lengthSqr() == 0), "scatter stays within the baseline XZ footprint, keeps its floor height, and has no thrown velocity: "
                     + outputs.stream().map(e -> e.position() + "/" + e.getDeltaMovement()).toList());
            helper.runAfterDelay(30, () -> {
                var settled = outputs.stream().filter(e -> !e.isRemoved() && level.getEntity(e.getUUID()) == e).toList();
                require(settled.size() == 3, "fresh harvest outputs remain independently world-owned after physics ticks");
                require(settled.stream().map(e -> Math.round(e.position().x * 1000) + "," + Math.round(e.position().z * 1000))
                        .distinct().count() >= 2, "scatter survives gravity and settling rather than only differing at spawn");
                require(settled.stream().allMatch(LooseItemEntity::settled), "harvest outputs reach settled state within 30 ticks");
                helper.succeed();
            });
        } catch (Throwable failure) { helper.fail("body harvest scatter: " + failure); }
        finally { if (actor != null) actor.discard(); }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void systemHarvestPublishesAtTrayAndRejectsStaleSource(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos tray = helper.absolutePos(new BlockPos(3, 1, 3));
        try {
            level.setBlock(tray, com.juicyslew.moonstation14.block.ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
            var before = allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            var commits = new java.util.concurrent.atomic.AtomicInteger();
            var result = FreshWorldOutputBatch.publishSystemHarvest(level, tray, stacks(), () -> true,
                    commits::incrementAndGet);
            var outputs = allLooseItems(level).stream().filter(e -> !before.contains(e.getUUID())).toList();
            require(result == BodyHandItemTransfer.Result.SUCCESS && commits.get() == 1,
                    "system harvest commits exactly once");
            require(outputs.size() == 1 && outputs.getFirst().position().y >= tray.getY() + 1
                            && Math.abs(outputs.getFirst().position().x - (tray.getX() + .5)) <= .25
                            && Math.abs(outputs.getFirst().position().z - (tray.getZ() + .5)) <= .25,
                    "system output is world-owned within holder-coordinate footprint");

            var beforeStaleAttempts = allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            commits.set(0);
            var staleBefore = FreshWorldOutputBatch.publishSystemHarvest(level, tray, stacks(), () -> false,
                    commits::incrementAndGet);
            require(staleBefore == BodyHandItemTransfer.Result.DENIED && commits.get() == 0,
                    "initially stale source is rejected before staging and commit");
            require(allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(beforeStaleAttempts),
                    "pre-staging stale source publishes no output");

            var checks = new java.util.concurrent.atomic.AtomicInteger();
            var staleBeforeCommit = FreshWorldOutputBatch.publishSystemHarvest(level, tray, stacks(),
                    () -> checks.incrementAndGet() == 1, commits::incrementAndGet);
            require(staleBeforeCommit == BodyHandItemTransfer.Result.DENIED && commits.get() == 0,
                    "source becoming stale after staging denies commit");
            require(allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(beforeStaleAttempts),
                    "stale publication compensates staged outputs");

            var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(
                    new ItemStack(ModItems.WHEAT_BUSHEL.get()), false);
            double candidateY = tray.getY() + 1 + Math.max(.01, profile.physicalHeight() * .5);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(BlockPos.containing(tray.getX() + .5, candidateY, tray.getZ() + .5)
                        .offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            commits.set(0);
            var blocked = FreshWorldOutputBatch.publishSystemHarvest(level, tray, stacks(), () -> true,
                    commits::incrementAndGet);
            require(blocked == BodyHandItemTransfer.Result.DENIED && commits.get() == 0,
                    "fully obstructed system harvest footprint denies without commit");
            require(allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(beforeStaleAttempts),
                    "blocked footprint publishes no output");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("system harvest publication: " + failure); }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void systemHarvestPublishesCompleteMultiOutputBatchAtHolder(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos tray = helper.absolutePos(new BlockPos(3, 1, 3));
        try {
            level.setBlock(tray, com.juicyslew.moonstation14.block.ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
            var items = java.util.List.of(ModItems.WHEAT_BUSHEL.get(), ModItems.TOMATO.get(),
                    ModItems.POTATO.get(), ModItems.POPPY.get(), ModItems.ALOE.get(), ModItems.TOMATO.get());
            var inputs = java.util.stream.IntStream.range(0, items.size()).mapToObj(index -> {
                    ItemStack stack = new ItemStack(items.get(index));
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal("harvest-output-" + index));
                    return stack;
                })
                    .toList();
            var before = allLooseItems(level).stream().map(LooseItemEntity::getUUID)
                    .collect(java.util.stream.Collectors.toSet());
            var commits = new java.util.concurrent.atomic.AtomicInteger();
            var result = FreshWorldOutputBatch.publishSystemHarvest(level, tray, inputs, () -> true,
                    commits::incrementAndGet);
            var outputs = allLooseItems(level).stream().filter(e -> !before.contains(e.getUUID())).toList();
            require(result == BodyHandItemTransfer.Result.SUCCESS && commits.get() == 1,
                    "multi-output system harvest commits once");
            require(outputs.size() == inputs.size(), "every batch output has a world entity");
            for (ItemStack input : inputs) {
                long matching = outputs.stream().filter(entity -> entity.getItem().getCount() == input.getCount()
                        && ItemStack.isSameItemSameComponents(input, entity.getItem())).count();
                require(matching == 1, "exact output item/components/count published once: " + input);
            }
            require(outputs.stream().allMatch(entity -> {
                var position = entity.position();
                var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                        .resolveForStack(entity.getItem(), false);
                return Math.abs(position.x - (tray.getX() + .5)) <= .25
                        && Math.abs(position.z - (tray.getZ() + .5)) <= .25
                        && position.y >= tray.getY() + 1
                        && BodyItemTargetProbe.loadedItemBoxClear(level, position,
                        profile.physicalWidth(), profile.physicalHeight());
            }), "all collocated-permitted outputs remain within holder footprint and clear of blocks");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("system multi-output harvest publication: " + failure); }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"harvest-placement"})
    public static void harvestPlacementFallsBackToClearCenterWhenPreferredIsBlocked(GameTestHelper helper) {
        LivingEntity actor = null;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            actor = EntityType.VILLAGER.create(level);
            require(actor != null, "harvest actor created");
            actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            require(level.addFreshEntity(actor), "harvest actor added");
            ItemStack stack = new ItemStack(ModItems.WHEAT_BUSHEL.get());
            var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false);
            double expectedY = actor.position().y + Math.max(.01, profile.physicalHeight() * .5);
            var preferred = BodyHandItemTransfer.createStagedHarvestDrop(actor, stack, .1, -.1);
            require(preferred != null, "clear preferred placement is preserved");
            require(preferred.position().x == actor.position().x + .1
                            && preferred.position().z == actor.position().z - .1
                            && preferred.position().y == expectedY,
                    "preferred XYZ including resolved-profile feet height is exact");
            preferred.discard();
            // Put a wall immediately west; the actor is close enough that a westward preferred
            // offset clips it, while the centered item AABB remains clear.
            var wall = net.minecraft.core.BlockPos.containing(actor.position().x, actor.position().y, actor.position().z)
                    .west();
            actor.setPos(wall.getX() + 1 + profile.physicalWidth() * .5 + .01, actor.position().y,
                    wall.getZ() + .5);
            expectedY = actor.position().y + Math.max(.01, profile.physicalHeight() * .5);
            level.setBlock(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            stack = new ItemStack(ModItems.WHEAT_BUSHEL.get());
            require(BodyHandItemTransfer.createStagedHarvestDrop(actor, stack, -.25, 0) == null,
                    "wall blocks westward preferred placement");
            require(BodyHandItemTransfer.createStagedHarvestDrop(actor, stack, 0, 0) != null,
                    "center is clear despite blocked preferred placement");
            var fallback = BodyHandItemTransfer.createStagedHarvestDropWithFallback(actor, stack, -.25, 0);
            require(fallback != null && fallback.position().x == actor.position().x
                            && fallback.position().z == actor.position().z,
                    "fallback attempts and selects the clear center");
            require(Math.abs(fallback.position().x - actor.position().x) <= .25
                            && Math.abs(fallback.position().z - actor.position().z) <= .25,
                    "fallback remains within the harvest footprint");
            require(fallback.position().y == expectedY, "fallback preserves resolved-profile feet height");
            require(BodyItemTargetProbe.loadedItemBoxClear(level, fallback.position(),
                            com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false).physicalWidth(),
                            com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false).physicalHeight()),
                    "fallback's full resolved-profile AABB is clear");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("harvest placement fallback: " + failure); }
        finally { if (actor != null) actor.discard(); }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void compensatesOnlyUnmodifiedOutputsWhenSourceCommitFails(GameTestHelper helper) {
        LivingEntity actor = null;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            actor = EntityType.VILLAGER.create(level);
            require(actor != null, "staged living actor created");
            actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            require(level.addFreshEntity(actor), "staged living actor added");
            LivingEntity stagedActor = actor;

            AtomicBoolean unchanged = new AtomicBoolean(true);
            var clean = publish(level, actor, () -> unchanged.get(), () -> { throw new IllegalStateException("injected commit failure"); });
            require(clean.result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED, "throwing commit reports recovery required");
            require(clean.entities.stream().noneMatch(e -> !e.isRemoved()), "unchanged source failure removes our outputs");

            AtomicBoolean changed = new AtomicBoolean(true);
            var preserved = publish(level, actor, () -> changed.get(), () -> {
                changed.set(false);
                throw new IllegalStateException("injected post-source-change failure");
            });
            require(preserved.result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED, "changed source failure reports recovery required");
            require(preserved.entities.stream().allMatch(e -> !e.isRemoved() && level.getEntity(e.getUUID()) == e),
                    "changed source preserves exact published output entities");

            AtomicBoolean stillCurrent = new AtomicBoolean(true);
            var prior = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            var modified = publish(level, actor, stillCurrent::get, () -> {
                var entity = entitiesNear(level, stagedActor).stream().filter(e -> !prior.contains(e.getUUID())).findFirst().orElseThrow();
                entity.getItem().grow(1);
                throw new IllegalStateException("injected modified-output failure");
            });
            require(modified.result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED, "modified-output failure reports recovery required");
            require(modified.entities.stream().allMatch(e -> !e.isRemoved()), "cleanup does not discard modified output stack");
            require(modified.entities.stream().anyMatch(e -> e.getItem().getCount() == 2), "modified stack remains intact");

            int before = entitiesNear(level, actor).size();
            var rejected = FreshWorldOutputBatch.publish(level, actor, stacks(), () -> false, () -> { });
            require(rejected == BodyHandItemTransfer.Result.DENIED, "invalid source is rejected before publication");
            require(entitiesNear(level, actor).size() == before, "invalid source publishes no outputs");

            var logSource = LooseItemEntity.create(level, actor.position().add(0, .1, 1.0),
                    new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
            logSource.setNoGravity(true); require(level.addFreshEntity(logSource), "source-origin Log fixture published");
            var sourceSnapshot = logSource.getItem().copy();
            var sourceOrigin = logSource.position();
            var beforeSourceBatch = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID)
                    .collect(java.util.stream.Collectors.toSet());
            var sourceResult = FreshWorldOutputBatch.publishAtLooseSource(level, sourceOrigin, stacks(),
                    () -> !logSource.isRemoved() && logSource.level() == level
                            && level.getEntity(logSource.getUUID()) == logSource
                            && ItemStack.isSameItemSameComponents(sourceSnapshot, logSource.getItem())
                            && sourceSnapshot.getCount() == logSource.getItem().getCount(),
                    () -> { throw new IllegalStateException("injected source-origin commit failure"); });
            require(sourceResult == BodyHandItemTransfer.Result.RECOVERY_REQUIRED,
                    "source-origin batch uses the existing commit-failure result");
            require(!logSource.isRemoved() && level.getEntity(logSource.getUUID()) == logSource
                            && ItemStack.isSameItemSameComponents(sourceSnapshot, logSource.getItem())
                            && sourceSnapshot.getCount() == logSource.getItem().getCount(),
                    "failed source-origin publication preserves exact source entity and stack");
            require(entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID)
                            .filter(id -> !beforeSourceBatch.contains(id)).allMatch(id -> level.getEntity(id) == null),
                    "failed source-origin batch compensates all newly published outputs");

            AtomicBoolean compensatedSourceCurrent = new AtomicBoolean(true);
            var beforeDenied = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID)
                    .collect(java.util.stream.Collectors.toSet());
            var deniedCommit = FreshWorldOutputBatch.publishAtSourcePosition(level, sourceOrigin, stacks(),
                    compensatedSourceCurrent::get, () -> {
                        compensatedSourceCurrent.set(false); // compensation advanced/replaced source ownership proof
                        return BodyHandItemTransfer.Result.DENIED;
                    }, null);
            require(deniedCommit == BodyHandItemTransfer.Result.DENIED,
                    "explicit denied source transaction propagates DENIED");
            require(entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID)
                            .filter(id -> !beforeDenied.contains(id)).allMatch(id -> level.getEntity(id) == null),
                    "denial cleans exact staged outputs even after old source proof becomes stale");

            var beforeRecovery = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID)
                    .collect(java.util.stream.Collectors.toSet());
            var uncertain = FreshWorldOutputBatch.publishAtSourcePosition(level, sourceOrigin, stacks(),
                    () -> !logSource.isRemoved() && level.getEntity(logSource.getUUID()) == logSource,
                    () -> BodyHandItemTransfer.Result.RECOVERY_REQUIRED, null);
            require(uncertain == BodyHandItemTransfer.Result.RECOVERY_REQUIRED,
                    "uncertain source transaction propagates recovery-required");
            require(entitiesNear(level, actor).stream().filter(entity -> !beforeRecovery.contains(entity.getUUID()))
                            .anyMatch(entity -> !entity.isRemoved() && level.getEntity(entity.getUUID()) == entity),
                    "uncertain source transaction conserves potentially committed output entity");
            require(!logSource.isRemoved() && level.getEntity(logSource.getUUID()) == logSource,
                    "uncertain commit does not destroy the source entity");
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("fresh world output compensation: " + failure);
        } finally {
            if (actor != null) actor.discard();
        }
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"harvest-placement"})
    public static void deniesHarvestWhenEveryFootprintCandidateIsBlocked(GameTestHelper helper) {
        LivingEntity actor = null;
        try {
            ServerLevel level = (ServerLevel) helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            actor = EntityType.VILLAGER.create(level);
            require(actor != null, "harvest actor created");
            actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            require(level.addFreshEntity(actor), "harvest actor added");
            // Cover the full 3x3 candidate grid; the adjacent blocks also cover the item's half-width.
            ItemStack probeStack = new ItemStack(ModItems.WHEAT_BUSHEL.get());
            var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(probeStack, false);
            double candidateY = actor.position().y + Math.max(.01, profile.physicalHeight() * .5);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(net.minecraft.core.BlockPos.containing(actor.position().x, candidateY, actor.position().z)
                        .offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            for (double x : new double[]{0, -.25, .25}) for (double z : new double[]{0, -.25, .25}) {
                require(BodyHandItemTransfer.createStagedHarvestDrop(actor, probeStack, x, z) == null,
                        "each candidate rejects its obstructed placement at resolved feet height: " + x + "," + z);
            }
            var before = allLooseItems(level).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            AtomicBoolean sourceCurrent = new AtomicBoolean(true);
            var commits = new java.util.concurrent.atomic.AtomicInteger();
            var result = FreshWorldOutputBatch.publishBodyHarvest(level, actor, stacks(), sourceCurrent::get,
                    commits::incrementAndGet);
            require(result == BodyHandItemTransfer.Result.DENIED, "exhausted placement candidates deny harvest");
            require(sourceCurrent.get() && commits.get() == 0, "denial leaves source current and never calls commit");
            var after = allLooseItems(level);
            require(after.stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(before),
                    "exhausted placement publishes no new loose-item entities anywhere in the fixture world");
            require(after.stream().allMatch(e -> !e.getItem().isEmpty()), "no empty-stack loose-item entity leaked");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("exhausted harvest placement: " + failure); }
        finally { if (actor != null) actor.discard(); }
    }

    private static Published publish(ServerLevel level, net.minecraft.world.entity.LivingEntity actor,
                                     java.util.function.BooleanSupplier source, Runnable commit) {
        var before = entitiesNear(level, actor).stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        var result = FreshWorldOutputBatch.publish(level, actor, stacks(), source, commit);
        var ours = entitiesNear(level, actor).stream().filter(e -> !before.contains(e.getUUID())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return new Published(result, ours);
    }

    private static ArrayList<LooseItemEntity> entitiesNear(ServerLevel level, net.minecraft.world.entity.LivingEntity actor) {
        return new ArrayList<>(level.getEntitiesOfClass(LooseItemEntity.class, new AABB(actor.blockPosition()).inflate(8), e -> !e.getItem().isEmpty()));
    }

    private static ArrayList<LooseItemEntity> allLooseItems(ServerLevel level) {
        return new ArrayList<>(level.getEntitiesOfClass(LooseItemEntity.class,
                new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000,
                        30_000_000, level.getMaxBuildHeight(), 30_000_000), e -> true));
    }

    private static java.util.List<ItemStack> stacks() { return java.util.List.of(new ItemStack(ModItems.WHEAT_BUSHEL.get())); }
    private record Published(BodyHandItemTransfer.Result result, ArrayList<LooseItemEntity> entities) { }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
