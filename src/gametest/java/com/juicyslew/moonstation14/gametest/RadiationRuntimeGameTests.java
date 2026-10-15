package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.radiation.RadiationService;
import com.juicyslew.moonstation14.ms14.radiation.RadiationSourceProfile;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"radiation-runtime"})
public final class RadiationRuntimeGameTests {
    private RadiationRuntimeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void loadedWallBlocksDamageAndOpenPathAppliesDose(GameTestHelper helper) {
        var level = helper.getLevel();
        FixtureCleanup cleanup = new FixtureCleanup(helper);
        Zombie actor = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 128, 2));
        cleanup.actor = actor;
        actor.setNoAi(true);
        actor.setNoGravity(true);
        actor.getAttribute(Attributes.ARMOR).setBaseValue(0);
        actor.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) actor.setItemSlot(slot, ItemStack.EMPTY);
        BlockPos source = helper.absolutePos(new BlockPos(1, 128, 2));
        BlockPos wall = helper.absolutePos(new BlockPos(2, 128, 2));
        cleanup.capture(wall);
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        UUID id = UUID.randomUUID();
        cleanup.sources.add(id);
        RadiationService service = RadiationService.INSTANCE;
        require(service.register(level, id, source, new RadiationSourceProfile(10, 1)), "source registered in loaded fixture");
        UUID second = UUID.randomUUID();
        cleanup.sources.add(second);
        require(service.register(level, second, source, new RadiationSourceProfile(10, 1)), "second source registered");
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                () -> "actor did not receive initial wall-shielded radiation dose; source=" + source
                        + " profile=" + new RadiationSourceProfile(10, 1) + " range=10 actorUuid=" + actor.getUUID()
                        + " actorPos=" + actor.position() + " actorY=" + actor.getY() + " alive=" + actor.isAlive()
                        + " health=" + actor.getHealth() + " radiationLedger=" + damage(actor)
                        + " wall=" + level.getBlockState(wall).getBlock() + " loaded=" + level.hasChunkAt(source),
                () -> damage(actor) > 0, () -> {
            float shielded = damage(actor);
            require(shielded > 0 && shielded < 20, "wall-shielded aggregate dose out of range; actual=" + shielded);
            service.remove(level, id); service.remove(level, second);
            level.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            UUID open = UUID.randomUUID();
            cleanup.sources.add(open);
            require(service.register(level, open, source, new RadiationSourceProfile(10, 1)), "open source registered");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                () -> "actor did not receive additional open-path radiation dose; armor="
                        + actor.getAttributeValue(Attributes.ARMOR) + " armorEquipment=" + armorEquipment(actor)
                        + " ledger=" + damage(actor) + " health=" + actor.getHealth()
                        + " source=" + source + " actualSourceCenter=" + Vec3.atLowerCornerWithOffset(source, 0.5, 0.5, 0.5)
                        + " actorPos=" + actor.position(), () -> damage(actor) > shielded, () -> {
                float openDose = damage(actor) - shielded;
                double distance = actor.position().distanceTo(Vec3.atCenterOf(source));
                float expected = (float) Math.max(0, 10 - distance);
                require(openDose > 0 && Math.abs(openDose - expected) < 0.01,
                        "open dose mismatch; actualDelta=" + openDose + ", expected=" + expected + ", distance=" + distance);
                service.remove(level, open); helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void registeredCollectorConsumesTankAndOffersHvPowerWithHoldover(GameTestHelper helper) {
        var level = helper.getLevel();
        FixtureCleanup cleanup = new FixtureCleanup(helper);
        BlockPos basePos = helper.absolutePos(new BlockPos(1, 128, 2));
        cleanup.capture(basePos);
        level.setBlock(basePos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        var baseCollector = (RadiationCollectorBlockEntity) level.getBlockEntity(basePos);
        var baseTank = baseCollector.tankSlot().snapshot();
        require(!baseCollector.enabled() && !baseTank.stack().isEmpty(), "base profile is disabled and initializes its empty tank once");
        require(GasTankItem.contents(baseTank.stack(), level).orElseThrow().mixture().moles(GasType.PLASMA) == 0,
                "base profile tank begins empty");
        BlockPos noTankPos = helper.absolutePos(new BlockPos(3, 128, 2));
        cleanup.capture(noTankPos);
        level.setBlock(noTankPos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        var noTankCollector = (RadiationCollectorBlockEntity) level.getBlockEntity(noTankPos);
        require(noTankCollector.selectProfile(ModRadiationCollectors.NO_TANK_ID), "select no-tank profile before initialization");
        require(noTankCollector.tankSlot().snapshot().stack().isEmpty(), "no-tank profile initializes a saved empty slot");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 128, 2));
        cleanup.capture(pos);
        level.setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector))
            throw new AssertionError("registered collector block entity exists");
        require(collector.selectProfile(ModRadiationCollectors.FULL_TANK_ID), "select full-tank profile before initialization");
        var resolvedProfile = ModRadiationCollectors.catalog(level).get(ModRadiationCollectors.FULL_TANK_ID);
        require(resolvedProfile != null, "resolved full-tank collector profile exists");
        require(collector.toggleEnabled(), "full-tank collector enabled");
        long enableGameTick = level.getGameTime();
        require(collector.powerSupplierOffer() != null && collector.powerSupplierOffer().currentWatts() == 0
                        && collector.powerSupplierOffer().ratedMaximumWatts() == 0,
                "inactive collector remains a valid zero-rated supplier");
        var slot = collector.tankSlot();
        var before = slot.snapshot();
        var contentsBefore = GasTankItem.contents(before.stack(), level).orElseThrow();
        require(contentsBefore.mixture().moles(GasType.PLASMA) > 2, "full profile materialized named filled tank");
        require(slot.mutate(before.revision(), before.token(), stack -> {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("named collector tank")); return true;
        }), "tank name mutation committed");
        long namedRevision = slot.snapshot().revision();

        UUID sourceId = UUID.randomUUID();
        cleanup.sources.add(sourceId);
        require(RadiationService.INSTANCE.register(level, sourceId, pos.offset(1, 0, 0), new RadiationSourceProfile(10, 1)),
                "registered radiation source reaches actual collector receiver");
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                () -> "collector did not consume tank contents after receiving radiation",
                () -> collector.powerSupplierOffer() != null
                        && collector.powerSupplierOffer().currentWatts() > 0
                        && collector.tankSlot().snapshot().revision() > namedRevision, () -> {
            var changed = collector.tankSlot().snapshot();
            var contentsAfter = GasTankItem.contents(changed.stack(), level).orElseThrow();
            require(contentsAfter.mixture().moles(GasType.PLASMA) < contentsBefore.mixture().moles(GasType.PLASMA),
                    "service callback committed finite plasma loss; source=" + sourceId + " sourcePos=" + pos.offset(1, 0, 0)
                            + " profile=" + ModRadiationCollectors.FULL_TANK_ID + " collectorPos=" + pos
                            + " beforeRevision=" + before.revision() + " afterRevision=" + changed.revision()
                            + " beforeMoles=" + contentsBefore.mixture().moles(GasType.PLASMA)
                            + " afterMoles=" + contentsAfter.mixture().moles(GasType.PLASMA)
                            + " doseRange=10 temperature=" + contentsBefore.mixture().temperatureKelvin());
            require(Component.literal("named collector tank").equals(changed.stack().get(DataComponents.CUSTOM_NAME)),
                    "tank metadata survived gas mutation");
            var offer = collector.powerSupplierOffer();
            require(offer != null && offer.currentWatts() > 30_000 && offer.currentWatts() == offer.ratedMaximumWatts(),
                    "actual and rated output preserve computed watts without a 30k clamp");
            require(collector.powerRuntimePorts().getFirst().tier() == com.juicyslew.moonstation14.ms14.power.cable.CableTier.HV,
                    "collector port is HV");

            RadiationService.INSTANCE.remove(level, sourceId);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                    () -> "collector use delay did not elapse; enableTick=" + enableGameTick
                            + " currentTick=" + level.getGameTime() + " profile=" + ModRadiationCollectors.FULL_TANK_ID
                            + " useDelayTicks=" + resolvedProfile.useDelayTicks(),
                    () -> level.getGameTime() >= enableGameTick + resolvedProfile.useDelayTicks(), () -> {
            require(collector.processRadiationDose(10, 1), "direct production consumer accepted finite dose");
            double output = collector.powerSupplierOffer().currentWatts();
            require(collector.toggleEnabled(), "collector disabled after resolved use delay");
            require(!collector.enabled() && collector.powerSupplierOffer().currentWatts() == output,
                    "disable unregisters dose receiver without cutting holdover output");
            for (int i = 0; i < 40; i++) RadiationCollectorBlockEntity.serverTick(level, pos, level.getBlockState(pos), collector);
            require(collector.powerSupplierOffer().currentWatts() == output, "output survives 40 local power updates");
            RadiationCollectorBlockEntity.serverTick(level, pos, level.getBlockState(pos), collector);
            require(collector.powerSupplierOffer().currentWatts() == 0, "output clears on update 41");
            helper.succeed();
            });
        });
    }

    private static float damage(net.minecraft.world.entity.LivingEntity actor) {
        DamageData data = actor.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return data == null ? 0 : data.getMap().getOrDefault(DamageKeys.RADIATION, 0f);
    }
    private static String armorEquipment(Zombie actor) {
        return "head=" + actor.getItemBySlot(EquipmentSlot.HEAD) + ", chest=" + actor.getItemBySlot(EquipmentSlot.CHEST)
                + ", legs=" + actor.getItemBySlot(EquipmentSlot.LEGS) + ", feet=" + actor.getItemBySlot(EquipmentSlot.FEET);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** Owns only this fixture's source registrations, actor, and explicitly captured blocks. */
    private static final class FixtureCleanup {
        private final net.minecraft.server.level.ServerLevel level;
        private final ArrayList<UUID> sources = new ArrayList<>();
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private Zombie actor;
        private FixtureCleanup(GameTestHelper helper) {
            level = helper.getLevel();
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
            });
        }
        private void capture(BlockPos pos) { blocks.putIfAbsent(pos.immutable(), level.getBlockState(pos)); }
        private void cleanup() {
            for (UUID source : sources) RadiationService.INSTANCE.remove(level, source);
            if (actor != null && actor.isAlive()) actor.discard();
            for (var entry : blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }
}
