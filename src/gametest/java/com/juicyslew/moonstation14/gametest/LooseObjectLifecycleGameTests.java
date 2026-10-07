package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.LooseChunkBounds;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Registered, live-world checks for the independent loose-object entity lifecycle. */
public final class LooseObjectLifecycleGameTests {
    private static final int WARMUP_BATCHES = 10;
    private static final int SAMPLE_BATCHES = 30;
    private static final int TICKS_PER_BATCH = 1;
    private LooseObjectLifecycleGameTests() { }

    public static void looseItemLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos floor = new BlockPos(2, 0, 2);
        helper.setBlock(floor, Blocks.STONE);
        ItemStack contents = new ItemStack(ModItems.JUG.get());
        contents.set(ModDataComponents.REAGENT.get(), com.juicyslew.moonstation14.ms14.reagent.ReagentComponent
                .fromCents(java.util.Map.of(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), 138L)));
        LooseItemEntity item = LooseItemEntity.create(level, helper.absoluteVec(new Vec3(2.5, 1.2, 2.5)), contents);
        if (!level.addFreshEntity(item)) throw new GameTestAssertException("registered loose object added");
        item.setDeltaMovement(Vec3.ZERO);
        var motionBounds = item.getBoundingBox().inflate(1.0);
        LooseChunkBounds chunks = LooseChunkBounds.from(motionBounds);
        for (int cx = chunks.minX(); cx <= chunks.maxX(); cx++)
            for (int cz = chunks.minZ(); cz <= chunks.maxZ(); cz++)
                level.getChunk(cx, cz);
        assertChunkBounds();
        for (int i = 0; i < 100 && !item.settled(); i++) item.tick();
        if (!item.settled()) throw new GameTestAssertException("object settles on supported floor");
        item.setRotationAngle(1.375f);
        item.tick();
        float orientation = item.getRotationAngle();
        if (orientation != 1.375f) throw new GameTestAssertException("stationary orientation retained");

        int sourceCount = item.getItem().getCount();
        var sourceReagent = item.getItem().get(ModDataComponents.REAGENT.get());
        ItemEntity particleCopy = ((ItemEntity) item).copy();
        if (!(particleCopy instanceof LooseItemEntity looseCopy)
                || looseCopy.getType() != item.getType()
                || looseCopy.getUUID().equals(item.getUUID()) || looseCopy.isAddedToLevel()
                || !ItemStack.matches(item.getItem(), looseCopy.getItem())
                || looseCopy.getItem().getCount() != sourceCount
                || looseCopy.getRotationAngle() != orientation)
            throw new GameTestAssertException("pickup-particle copy retains registered loose type and full stack");
        looseCopy.getItem().setCount(sourceCount + 1);
        if (item.getItem().getCount() != sourceCount
                || !java.util.Objects.equals(item.getItem().get(ModDataComponents.REAGENT.get()), sourceReagent))
            throw new GameTestAssertException("pickup-particle stack mutation does not affect authoritative source");

        ItemStack pickaxeStack = new ItemStack(ModItems.PICKAXE.get(), 1);
        pickaxeStack.set(DataComponents.CUSTOM_NAME, Component.literal("pickaxe-regression"));
        if (pickaxeStack.isDamageableItem()) pickaxeStack.setDamageValue(7);
        LooseItemEntity pickaxe = LooseItemEntity.create(level, item.position(), pickaxeStack);
        pickaxe.setRotationAngle(0.625f);
        ItemEntity pickaxeParticleCopy = ((ItemEntity) pickaxe).copy();
        if (!(pickaxeParticleCopy instanceof LooseItemEntity pickaxeCopy)
                || pickaxeCopy.getType() != ModEntities.LOOSE_ITEM.get()
                || pickaxeCopy.isAddedToLevel() || pickaxeCopy.getUUID().equals(pickaxe.getUUID())
                || !ItemStack.matches(pickaxeStack, pickaxeCopy.getItem()) || pickaxeCopy.getItem().getCount() != 1
                || pickaxeCopy.getRotationAngle() != 0.625f)
            throw new GameTestAssertException("actual pickaxe pickup-particle copy retains registered loose type and stack components");

        CompoundTag saved = item.saveWithoutId(new CompoundTag());
        saved.putShort("Age", (short) 6001);
        var loaded = ModEntities.LOOSE_ITEM.get().create(level);
        if (loaded == null) throw new GameTestAssertException("registered entity type creates load target");
        loaded.load(saved);
        if (!loaded.getUUID().equals(item.getUUID()) || !loaded.settled()
                || loaded.getRotationAngle() != orientation
                || !ItemStack.matches(item.getItem(), loaded.getItem()))
            throw new GameTestAssertException("save/load retains UUID, stack components and stationary orientation");
        loaded.setPos(5.5, 2, 2.5);
        item.discard();
        helper.runAfterDelay(1, () -> {
            if (!level.addFreshEntity(loaded)) throw new GameTestAssertException("round-trip entity added");
            loaded.setDeltaMovement(Vec3.ZERO);
            loaded.tick();
            if (loaded.isRemoved() || loaded.getItem().isEmpty())
                throw new GameTestAssertException("loose item outlives vanilla item age without pickup");

            LooseItemEntity overlap = LooseItemEntity.create(level, loaded.position(), loaded.getItem());
            if (!level.addFreshEntity(overlap)) throw new GameTestAssertException("overlap fixture added");
            loaded.tick(); overlap.tick();
            if (loaded.isRemoved() || overlap.isRemoved()) throw new GameTestAssertException("overlapping loose objects do not merge");
            loaded.discard(); overlap.discard();
            helper.succeed();
        });
    }

    private static void assertChunkBounds() {
        var bounds = LooseChunkBounds.from(new net.minecraft.world.phys.AABB(128, 0, 80, 129, 1, 81));
        if (bounds.minX() != 8 || bounds.maxX() != 8 || bounds.minZ() != 5 || bounds.maxZ() != 5)
            throw new GameTestAssertException("world block coordinates map to actual chunk coordinates");
        var negative = LooseChunkBounds.from(new net.minecraft.world.phys.AABB(-16.01, 0, -0.01, -15.99, 1, .01));
        if (negative.minX() != -2 || negative.maxX() != -1 || negative.minZ() != -1 || negative.maxZ() != 0)
            throw new GameTestAssertException("negative and boundary coordinates map to intersected chunks");
        var exact = LooseChunkBounds.from(new net.minecraft.world.phys.AABB(0, 0, -32, 16, 1, -16));
        if (exact.minX() != 0 || exact.maxX() != 0 || exact.minZ() != -2 || exact.maxZ() != -2)
            throw new GameTestAssertException("exclusive chunk boundaries do not include adjacent chunks");
        var crossing = LooseChunkBounds.from(new net.minecraft.world.phys.AABB(0, 0, 0, 16.000001, 1, 1));
        if (crossing.maxX() != 1) throw new GameTestAssertException("positive-volume boundary crossing includes neighbor");
        var point = LooseChunkBounds.from(new net.minecraft.world.phys.AABB(-16, 0, 0, -16, 1, 0));
        if (point.minX() != -1 || point.maxX() != -1) throw new GameTestAssertException("degenerate bounds use minimum coordinate");
    }

    /** Measures direct LooseItemEntity.tick() work, not server tick rate or network/client performance. */
    public static void looseObjectTickBenchmark(GameTestHelper helper) {
        var level = helper.getLevel();
        var entities = new ArrayList<LooseItemEntity>();
        // Three fixture-local 10x10 floors, with entities deliberately grouped into pile clusters.
        for (int floor = 0; floor < 3; floor++) {
            int y = 1 + floor * 2;
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++)
                helper.setBlock(helper.absolutePos(new BlockPos(x, y - 1, z)), Blocks.STONE);
        }
        // Explicit fixture setup: production motion continues to freeze rather than force-loading sweep chunks.
        BlockPos sceneMin = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos sceneMax = helper.absolutePos(new BlockPos(12, 0, 12));
        for (int cx = net.minecraft.world.level.ChunkPos.getX(sceneMin.getX()); cx <= net.minecraft.world.level.ChunkPos.getX(sceneMax.getX()); cx++)
            for (int cz = net.minecraft.world.level.ChunkPos.getZ(sceneMin.getZ()); cz <= net.minecraft.world.level.ChunkPos.getZ(sceneMax.getZ()); cz++)
                level.getChunk(cx, cz);
        var stack = new ItemStack(Items.STICK);
        for (int i = 0; i < 1024; i++) {
            int floor = i / 342;
            int withinFloor = i % 342;
            // 4 clusters per floor; deterministic overlap simulates accumulated dropped objects.
            int cluster = withinFloor % 4;
            int x = 1 + cluster * 2 + (withinFloor / 4) % 2;
            int z = 1 + cluster / 2 * 4;
            double y = 1.02 + floor * 2 + (withinFloor % 3) * .015;
            Vec3 relative = new Vec3(x + .5, y, z + .5);
            Vec3 absolute = helper.absoluteVec(relative);
            var bounds = helper.getBounds();
            if (!bounds.contains(absolute))
                throw new GameTestAssertException("benchmark seeded coordinate outside fixture bounds: " + relative);
            LooseItemEntity entity = LooseItemEntity.create(level, absolute, stack);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setRotationAngle((i % 100) * .01f);
            if (!level.addFreshEntity(entity)) throw new GameTestAssertException("benchmark fixture entity added");
            entities.add(entity);
        }
        // Let normal entity ticks land every object on its actual fixture floor.
        for (int i = 0; i < 200 && entities.stream().anyMatch(entity -> !entity.settled()); i++)
            entities.stream().filter(entity -> !entity.settled()).forEach(LooseItemEntity::tick);
        assertStates(entities, entities.size(), entities.size(), "warmup settlement");

        Path output = Path.of("build", "loose-object-measurements");
        Path csv = output.resolve("loose-object-tick.csv");
        Path rawCsv = output.resolve("raw-samples.csv");
        StringBuilder rows = new StringBuilder("mode,requested,live,settled,active,ticks_per_batch,warmup_batches,samples,p50_ns,p95_ns,p99_ns\n");
        StringBuilder rawRows = new StringBuilder("workload,live,active,sample_index,nanoseconds\n");
        int[][] workloads = {{0, 0}, {64, 0}, {256, 0}, {1024, 0}, {256, 1}};
        for (int[] workload : workloads) {
            int active = workload[0];
            boolean moving = workload[1] != 0;
            assertStates(entities, entities.size(), entities.size(), "before workload");
            var measured = entities.subList(0, active);
            long[] samples = new long[SAMPLE_BATCHES];
            for (int i = 0; i < WARMUP_BATCHES + SAMPLE_BATCHES; i++) {
                long start = System.nanoTime();
                for (LooseItemEntity entity : measured) for (int tick = 0; tick < TICKS_PER_BATCH; tick++) {
                    if (moving) {
                        double direction = ((i + entity.getId()) & 1) == 0 ? 1 : -1;
                        entity.launch(new Vec3(direction * .06, 0, 0), (float) (direction * .06));
                    }
                    entity.tick();
                }
                long elapsed = System.nanoTime() - start;
                if (i >= WARMUP_BATCHES) samples[i - WARMUP_BATCHES] = elapsed;
            }
            int live = (int) measured.stream().filter(entity -> !entity.isRemoved()).count();
            int settled = (int) measured.stream().filter(entity -> !entity.isRemoved() && entity.settled()).count();
            int actualActive = live - settled;
            if (live != active || settled != (moving ? 0 : active))
                throw new GameTestAssertException("benchmark workload states live=" + live + " settled=" + settled + " active=" + actualActive);
            String mode = moving ? "synthetic-active-jostle" : active == 0 ? "baseline" : "settled";
            for (int sample = 0; sample < samples.length; sample++)
                rawRows.append(String.format(java.util.Locale.ROOT, "%s,%d,%d,%d,%d%n", mode, live, actualActive, sample, samples[sample]));
            java.util.Arrays.sort(samples);
            long p50 = percentile(samples, .50), p95 = percentile(samples, .95), p99 = percentile(samples, .99);
            System.out.printf("[LooseObjectTickBenchmark] mode=%s requested=%d live=%d settled=%d active=%d ticksPerBatch=%d warmup=%d samples=%d nsPerBatch p50=%d p95=%d p99=%d scope=direct-entity-tick-batch; synthetic active jostle includes launch impulse calls and is not player TPS%n",
                    active == 0 ? "baseline" : moving ? "synthetic-active-jostle" : "settled", active,
                    live, settled, actualActive, TICKS_PER_BATCH, WARMUP_BATCHES, SAMPLE_BATCHES, p50, p95, p99);
            rows.append(String.format(java.util.Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    moving ? "synthetic-active-jostle" : active == 0 ? "baseline" : "settled", active, live, settled, actualActive,
                    TICKS_PER_BATCH, WARMUP_BATCHES, SAMPLE_BATCHES, p50, p95, p99));
            if (moving) {
                measured.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO));
                for (int i = 0; i < 200 && measured.stream().anyMatch(entity -> !entity.settled()); i++)
                    measured.stream().filter(entity -> !entity.settled()).forEach(LooseItemEntity::tick);
                if (measured.stream().anyMatch(entity -> !entity.settled()))
                    throw new GameTestAssertException("moving workload did not resettle for next workload");
            }
        }
        try {
            Files.createDirectories(output);
            Files.writeString(csv, rows.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(rawCsv, rawRows.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(output.resolve("environment.txt"), String.format(java.util.Locale.ROOT,
                    "java=%s%nvM=%s%nos=%s/%s%nprocessors=%d%nentities=%d%nfixture=%s%nworkload_seed=deterministic index-derived coordinates and launch direction (entity id, batch index)%nsetup=3 fixture-local 10x10 stone floors; four pile clusters per floor; 1024 seeded entities%nworkloads=0,64,256,1024 settled; 256 synthetic-active-jostle%nwatchers=none; no network/client measurement%nmeasurement=direct entity tick batch only; excludes whole-server TPS, client, network, and player capacity%nprovisional_guardrail=5ms direct-object-work target for 256 active + 1024 idle; report only, not a flaky unit-test assertion%nreference_budget=45ms other system headroom + 5ms object target = 50ms total tick reference; not proof of server TPS%nraw_samples=%s%n",
                    System.getProperty("java.version"), ManagementFactory.getRuntimeMXBean().getVmName(),
                    System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(),
                    entities.size(), helper.getLevel().dimension().location(), rawCsv), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("write benchmark measurements to " + csv + ": " + e.getMessage());
        }
        System.out.printf("[LooseObjectTickBenchmark] java=%s vm=%s os=%s/%s processors=%d seeded=%d counts=0,64,256,1024 csv=%s scope=direct-entity-tick-batch; synthetic jostle includes launch impulse calls; not whole-server TPS/client/network/player capacity%n",
                System.getProperty("java.version"), ManagementFactory.getRuntimeMXBean().getVmName(),
                System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), entities.size(), csv);
        entities.forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    private static void assertStates(java.util.List<LooseItemEntity> entities, int expectedLive, int expectedSettled, String phase) {
        int live = (int) entities.stream().filter(entity -> !entity.isRemoved()).count();
        int settled = (int) entities.stream().filter(entity -> !entity.isRemoved() && entity.settled()).count();
        if (live != expectedLive || settled != expectedSettled)
            throw new GameTestAssertException(phase + ": live=" + live + " settled=" + settled + " expected=" + expectedLive + "/" + expectedSettled);
    }

    private static long percentile(long[] sorted, double percentile) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(percentile * sorted.length) - 1)];
    }
}
