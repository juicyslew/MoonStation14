package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

@GameTestTags({"loose-objects", "item-damage", "destruction-outputs"})
@PrefixGameTestTemplate(false)
public final class DestructionOutputGameTests {
    private DestructionOutputGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20, batch = "loose-food-initialization")
    public static void freshLooseTomatoPreservesExactPublisherStack(GameTestHelper helper) {
        var level = helper.getLevel();
        var input = new ItemStack(ModItems.TOMATO.get());
        var originalComponent = input.get(ModDataComponents.REAGENT.get());
        var loose = LooseItemEntity.create(level, Vec3.ZERO, input);
        require(loose.getItem().get(ModDataComponents.REAGENT.get()) == originalComponent, "creation preserves exact component identity");
        require(input.get(ModDataComponents.REAGENT.get()) == originalComponent, "creation does not mutate source stack");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "destruction-output-impact-catalog")
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining", "merge-main-root-causes"})
    public static void impactPublishesOneFreshOutputAndConsumesSource(GameTestHelper helper) {
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var original = new java.util.HashMap<>(PrototypeRuntime.serverManager().snapshot(type).asMap());
        var previous = original.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"));
        var damage = new LooseObjectProfile.ItemDamageComposition(java.util.Set.of("brute"), Map.of("blunt", 1f),
                .1f, 1f, 0, Map.of(), 1f, null, true,
                List.of(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"), 1)));
        var overridden = previous.withItemDamage(damage);
        ScopedPrototypeCatalogFixture.replace(helper, type, candidate -> { candidate.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), overridden); return candidate; });
        var level = helper.getLevel();
        var wall = helper.absolutePos(new BlockPos(8, 2, 5));
        for (int y = 1; y < 6; y++) level.setBlock(wall.offset(0, y - 2, 0), Blocks.STONE.defaultBlockState(), 3);
        var source = new ItemStack(ModItems.TOMATO.get());
        var sourceGenetics = source.get(ModDataComponents.GENETIC_VALUE.get());
        var sourceReagents = source.get(ModDataComponents.REAGENT.get());
        source.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), new com.juicyslew.moonstation14.ms14.damage.ItemDamageState(Map.of("blunt", .5f), 0));
        var loose = LooseItemEntity.create(level, new Vec3(wall.getX() - 2, wall.getY() + .5, wall.getZ() + .5), source);
        loose.setNoGravity(true);
        loose.setDeltaMovement(2, 0, 0);
        if (!level.addFreshEntity(loose)) throw new GameTestAssertException("damage source enters world");
        helper.runAfterDelay(8, () -> {
            try {
                require(loose.isRemoved(), "threshold impact consumes source");
                require(source.get(ModDataComponents.REAGENT.get()) == null, "spill initialization leaves original owned source chemistry unchanged");
                var spilled = new java.util.HashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
                for (int x = wall.getX() - 2; x <= wall.getX() + 2; x++)
                    for (int y = wall.getY() - 2; y <= wall.getY() + 2; y++)
                        for (int z = wall.getZ() - 2; z <= wall.getZ() + 2; z++) {
                            var blockEntity = level.getBlockEntity(new BlockPos(x, y, z));
                            if (blockEntity instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle) {
                                var contents = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(puddle,
                                        com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits();
                                contents.forEach((key, amount) -> spilled.merge(key, amount, Math::addExact));
                            }
                        }
                require(spilled.getOrDefault(ModReagents.createKey("nutriment"), 0L) == 700L
                                && spilled.getOrDefault(ModReagents.createKey("vitamin"), 0L) == 300L
                                && spilled.getOrDefault(ModReagents.createKey("water"), 0L) == 400L,
                        "fresh tomato destruction spills resolved authored 7u nutriment, 3u vitamin, 4u water");
                var outputs = level.getEntitiesOfClass(LooseItemEntity.class,
                        new net.minecraft.world.phys.AABB(wall).inflate(8), e -> e.getItem().is(ModItems.FOOD_COCOA_BEANS.get()));
                require(outputs.size() == 1 && outputs.get(0).getItem().getCount() == 1, "exactly one configured output published");
                var product = outputs.get(0).getItem();
                require(product.get(ModDataComponents.GENETIC_VALUE.get()) == null, "fresh generic output carries no source genetics");
                require(!product.get(ModDataComponents.REAGENT.get()).equals(sourceReagents), "output does not inherit live source chemistry");
                var freshChemistry = PrototypeRuntime.serverItemFoodSolutionProfiles().get(
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"));
                var outputReagents = product.get(ModDataComponents.REAGENT.get());
                require(freshChemistry != null && outputReagents != null, "fresh output chemistry and its profile are present");
                var outputContents = outputReagents.centContents().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().location(), Map.Entry::getValue));
                require(outputContents.equals(freshChemistry.contentsCents()),
                        "output chemistry is initialized from its own food solution prototype");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("fixed destruction output: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "destruction-output-canceled-catalog")
    public static void canceledOutputPublicationStillConsumesDestroyedSource(GameTestHelper helper) {
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var original = new java.util.HashMap<>(PrototypeRuntime.serverManager().snapshot(type).asMap());
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var previous = original.get(tomatoId);
        var damage = new LooseObjectProfile.ItemDamageComposition(java.util.Set.of("brute"), Map.of("blunt", 1f),
                .1f, 1f, 0, Map.of(), 1f, null, true,
                List.of(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"), 1)));
        ScopedPrototypeCatalogFixture.Handle catalog = ScopedPrototypeCatalogFixture.replace(helper, type, candidate -> { candidate.put(tomatoId, previous.withItemDamage(damage)); return candidate; });
        var listenerRef = new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent>>();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void restore() {
                var listener = listenerRef.getAndSet(null);
                if (listener != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
        });
        var level = helper.getLevel();
        var wall = helper.absolutePos(new BlockPos(8, 2, 5));
        for (int y = 1; y < 6; y++) level.setBlock(wall.offset(0, y - 2, 0), Blocks.STONE.defaultBlockState(), 3);
        var source = ProduceFactory.createHarvest(tomatoId, 10);
        source.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(Map.of(
                ModReagents.createKey("nutriment"), 150L, ModReagents.createKey("vitamin"), 120L)));
        var sourceReagents = source.get(ModDataComponents.REAGENT.get());
        source.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), new com.juicyslew.moonstation14.ms14.damage.ItemDamageState(Map.of("blunt", .5f), 0));
        var loose = LooseItemEntity.create(level, new Vec3(wall.getX() - 2, wall.getY() + .5, wall.getZ() + .5), source);
        loose.setNoGravity(true);
        loose.setDeltaMovement(2, 0, 0);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancelOutput = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getItem().is(ModItems.FOOD_COCOA_BEANS.get())) {
                attempts.incrementAndGet();
                event.setCanceled(true);
            }
        };
        listenerRef.set(cancelOutput);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancelOutput);
        if (!level.addFreshEntity(loose)) {
            var listener = listenerRef.getAndSet(null);
            if (listener != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            catalog.restore();
            throw new GameTestAssertException("damage source enters world");
        }
        helper.runAfterDelay(12, () -> {
            try {
                var outputs = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(wall).inflate(8), e -> e.getItem().is(ModItems.FOOD_COCOA_BEANS.get()));
                require(attempts.get() == 1, "exactly one staged output join was attempted");
                require(loose.isRemoved(), "source is terminally consumed despite denied output publication");
                require(outputs.isEmpty(), "denied staged output leaves no published owner");
                require(source.get(ModDataComponents.REAGENT.get()).equals(sourceReagents), "source stack chemistry remains independent of staged output");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("denied destruction output: " + failure); }
            finally {
                var listener = listenerRef.getAndSet(null);
                if (listener != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            }
        });
    }

    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
