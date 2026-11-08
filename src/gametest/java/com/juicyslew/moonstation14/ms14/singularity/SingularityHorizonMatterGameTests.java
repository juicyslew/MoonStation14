package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused live-consumer contract for loaded item entities and terrain event-horizon waves. */
@GameTestTags({"singularity-horizon", "singularity-prototype"})
@PrefixGameTestTemplate(false)
public final class SingularityHorizonMatterGameTests {
    private SingularityHorizonMatterGameTests() { }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(template="empty", timeoutTicks=20, templateNamespace="moonstation14")
    public static void resolvedHorizonConsumesTerrainAndLooseEntityOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        require(singularity != null, "registered singularity created");
        singularity.setPos(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
        require(level.addFreshEntity(singularity), "singularity admitted");
        singularity.feed(21);

        var dirt = origin.east();
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
        var loose = LooseItemEntity.create(level, new Vec3(origin.getX() + 1.1, origin.getY() + .5, origin.getZ() + .5),
                new ItemStack(Items.STONE, 64));
        require(level.addFreshEntity(loose), "loose item entity admitted");
        double before = singularity.energy();
        long startTick = level.getGameTime();
        helper.runAfterDelay(2, () -> {
            require(level.getBlockState(dirt).isAir(), "resolved consumeTiles wave removes intersecting terrain without drops");
            require(loose.isRemoved(), "resolved consumeEntities wave consumes loose item entity");
            double drained = singularity.gravityProfile().energyAfterSeconds(before, 2,
                    (level.getGameTime() - startTick) * .05);
            double expected = drained + singularity.gravityProfile().eventHorizon().entityEnergy()
                    + singularity.gravityProfile().eventHorizon().lowYieldTerrainEnergy();
            require(Math.abs(singularity.energy() - expected) < .02,
                    "runtime consumer credits once per item entity and applies tagged low-yield terrain energy");
            loose.discard(); singularity.discard();
            helper.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=30, templateNamespace="moonstation14")
    public static void horizonWaveDeadlineSurvivesNativeEntitySaveReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(3, 3, 3));
        var source = ModEntities.SINGULARITY.get().create(level);
        require(source != null, "registered singularity created");
        source.setPos(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
         // Keep the entity at level 2 for the complete persisted-deadline window;
         // otherwise natural drain crosses the stage boundary and shrinks its horizon.
         source.feed(80);
        require(level.addFreshEntity(source), "singularity admitted");
        helper.runAtTickTime(2, () -> {
            // A fixed terrain cell avoids loose-item gravity and source random-walk making
            // the target leave the horizon before the persisted deadline is reached.
            var target = origin.east();
            level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            var saved = source.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            long deadline = saved.getLong("NextHorizonWave");
            require(deadline > level.getGameTime(), "saved source has a future wave deadline");
            source.discard();
            BoundedGameTestWait.safeDelay(helper, 1, () -> {
                require(source.isRemoved(), "original source removed before restoring its identity");
                var restored = ModEntities.SINGULARITY.get().create(level);
                require(restored != null, "registered reload instance created");
                restored.load(saved);
                restored.setPos(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
                require(level.addFreshEntity(restored), "restored source admitted after original removal");
                require(!level.getBlockState(target).isAir(), "reload does not replay the first horizon wave immediately");
                long beforeDeadline = deadline - level.getGameTime() - 1;
                require(beforeDeadline > 0, "fixture reaches a tick before the persisted deadline");
                helper.runAfterDelay((int) beforeDeadline, () -> {
                    require(!level.getBlockState(target).isAir(), "target survives through the tick before saved deadline");
                    // GameTest callbacks can execute before entity ticking in the same
                    // world tick. Observe one completed tick past the deadline instead.
                    BoundedGameTestWait.safeDelay(helper, 1, () -> {
                        var afterWave = restored.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                        long actualNext = afterWave.getLong("NextHorizonWave");
                        String state = "gametime=" + level.getGameTime() + ", savedDeadline=" + deadline
                                + ", actualNext=" + actualNext + ", energy=" + restored.energy()
                                + ", level=" + restored.singularityLevel() + ", restoredTicks=" + restored.tickCount
                                + ", position=" + restored.position() + ", target=" + level.getBlockState(target)
                                + ", chunkLoaded=" + level.hasChunkAt(target) + ", entityRegistered="
                                + (level.getEntity(restored.getUUID()) == restored) + ", removed=" + restored.isRemoved()
                                + ", removalReason=" + restored.getRemovalReason() + ", savedEnergy=" + saved.getDouble("Energy");
                        require(actualNext == deadline + 10, "deadline cadence should advance exactly ten ticks; " + state);
                        require(level.getBlockState(target).isAir(), "saved deadline wave consumes terrain; " + state);
                        restored.discard();
                        source.discard();
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template="empty", timeoutTicks=20, templateNamespace="moonstation14")
    public static void placedAndPortableBeaconUseFoodFormulaWhileOrdinaryStackCreditsOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        require(singularity != null, "registered singularity created");
        singularity.setPos(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
        require(level.addFreshEntity(singularity), "singularity admitted");
        singularity.feed(21); // Level 2 remains above threshold after natural drain.

        var beaconPos = origin.east();
        level.setBlock(beaconPos, ModBlocks.SINGULARITY_BEACON.get().defaultBlockState(), 3);
        var beaconStack = new ItemStack(ModBlocks.SINGULARITY_BEACON.get().asItem());
        var portableBeacon = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 1.1, origin.getY() + .5, origin.getZ() + .5), beaconStack);
        var ordinaryStack = LooseItemEntity.create(level,
                new Vec3(origin.getX() + 1.2, origin.getY() + .5, origin.getZ() + .5), new ItemStack(Items.STONE, 64));
        require(level.addFreshEntity(portableBeacon) && level.addFreshEntity(ordinaryStack), "loose item entities admitted");
        double before = singularity.energy();
        long startTick = level.getGameTime();
        helper.runAfterDelay(2, () -> {
            require(level.getBlockState(beaconPos).isAir(), "placed beacon consumed through its block-entity food interface");
            require(portableBeacon.isRemoved(), "portable beacon item consumed");
            require(ordinaryStack.isRemoved(), "ordinary 64-stack item entity consumed");
            double drained = singularity.gravityProfile().energyAfterSeconds(before, 2,
                    (level.getGameTime() - startTick) * .05);
            double expected = drained + 120 + 120 + 1;
            require(Math.abs(singularity.energy() - expected) < .02,
                    "placed and portable beacons each apply +120; ordinary stack credits +1 per entity");
            portableBeacon.discard(); ordinaryStack.discard(); singularity.discard();
            helper.succeed();
        });
    }

    @GameTest(template="power_receiver_tall_clear", timeoutTicks=600, templateNamespace="moonstation14")
    public static void naturalHorizonRemovalClearsCableHostAndInvalidatesBeaconPower(GameTestHelper helper) {
        var fixture = AirAlarmPowerFixture.place(helper);
        var level = fixture.level();
        var cableHost = fixture.cable().offset(0, 0, 2);
        var cableTurnHost = cableHost.west().north();
        var beaconPos = cableTurnHost.south();
        level.setBlock(fixture.cable().south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cableHost, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cableTurnHost, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, fixture.cable().south(), net.minecraft.core.Direction.WEST, CableTier.APC),
                "extend APC cable across the host wall");
        require(CableStorage.place(level, cableHost, net.minecraft.core.Direction.WEST, CableTier.APC),
                "place the cable host that the horizon will consume");
        require(CableStorage.place(level, cableTurnHost, net.minecraft.core.Direction.SOUTH, CableTier.APC),
                "turn APC cable onto the beacon north port");
        level.setBlock(beaconPos, ModBlocks.SINGULARITY_BEACON.get().defaultBlockState(), 3);
        var beacon = (com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity) level.getBlockEntity(beaconPos);
        require(beacon != null, "beacon block entity installed on the APC circuit");
        require(CableStorage.has(level, cableHost, net.minecraft.core.Direction.WEST, CableTier.APC),
                "cable host initially owns the APC face record");

        BoundedGameTestWait.await(helper, 300, () -> "beacon meter=" + PowerRuntime.apcPowerMeter(level, beacon),
                beacon::fullyPowered, () -> {
                    var singularity = ModEntities.SINGULARITY.get().create(level);
                    require(singularity != null, "registered singularity created");
                    singularity.setPos(cableHost.getX() + 1.6, cableHost.getY() + .5, cableHost.getZ() + .5);
                    require(level.addFreshEntity(singularity), "singularity admitted beside cable host");
                    singularity.feed(21);
                    BoundedGameTestWait.safeDelay(helper, 2, () -> {
                        require(level.getBlockState(cableHost).isAir(), "natural singularity tick consumed cable host block");
                        var chunk = level.getChunkAt(cableHost);
                        var cableData = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
                        require(cableData == null || !cableData.contains(cableHost.getX() & 15,
                                        cableHost.getY(), cableHost.getZ() & 15,
                                        net.minecraft.core.Direction.WEST, CableTier.APC),
                                "CableStorage.removeHost retired APC cable face data, not merely host eligibility");
                        require(!beacon.fullyPowered(), "fresh beacon authorization is lost after its required APC path disappears");
                        singularity.discard();
                        helper.succeed();
                    });
                });
    }
}
