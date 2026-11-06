package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/** Stationary real-owner cabin baseline; deliberately does not load or reference Sable. */
@GameTestTags({"shuttle", "shuttle-stationary"})
@PrefixGameTestTemplate(false)
public final class ShuttleCabinFixtureGameTests {
    private ShuttleCabinFixtureGameTests() { }

    @GameTestTags({"power", "atmosphere", "loose-objects", "chemistry", "shuttle-cabin-ventilation"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", batch = "shuttle_stationary_cabin", timeoutTicks = 650)
    public static void stationaryCabinKeepsFiniteRoomPipeMachineAndCargoOwners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        assertFootprintInsideTemplate(helper, origin);
        BlockPos far = origin.offset(ShuttleCabinFixture.WIDTH - 1, ShuttleCabinFixture.HEIGHT - 1,
                ShuttleCabinFixture.DEPTH - 1);
        FixtureChunkTickets.Lease tickets = FixtureChunkTickets.around(helper, origin, far, 2);
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 12, () -> {
            ShuttleCabinFixture.Built cabin = ShuttleCabinFixture.build(level, origin);
            var machine = (AirAlarmBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.MACHINE));
            require(machine != null && machine.prototype() != null,
                    "real alarm machine is authored at the cabin machine position");
            require(level.getBlockState(cabin.at(ShuttleCabinFixture.WINDOW)).is(Blocks.GLASS), "authored real window in hull");
            AirlockBlockEntity door = (AirlockBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.AIRLOCK));
            require(door != null && door.identity().equals(cabin.airlockIdentity()) && door.prototype() != null,
                    "two-high authored airlock has stable live root identity and resolved prototype");
            for (int z = 1; z <= 7; z++)
                require(CableStorage.has(level, origin.offset(5, 3, z), net.minecraft.core.Direction.WEST, CableTier.APC),
                        "real APC extension cable is connected over the cabin ceiling, z=" + z);
            require(cabin.roomCells().size() > 20, "bounded cabin finite room was composed from passable authored cells");
            var roomCell = cabin.roomCells().getFirst();
            var gas = AtmosphereService.INSTANCE.readAtmosphere(level, roomCell).orElseThrow();
            require(gas.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 0d) == 7.0
                            && gas.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, 0d) == 26.0
                            && gas.mixture().temperatureKelvin() == 289.0,
                    "finite room receives the fixture's non-default species and temperature");

            var pipe = (VisualPipeBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.PIPE));
            var pipeBefore = LoadedPipeGasOwner.activeSnapshot(level, pipe.getBlockPos(), VisualPipeBlockEntity.PRIMARY).orElseThrow();
            require(pipeBefore.incarnation().equals(cabin.pipeIdentity())
                            && pipeBefore.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 0d) == .75
                            && pipeBefore.mixture().temperatureKelvin() == 307.0,
                    "finite pipe gas retains species, thermal state and existing lane incarnation");
            CabinGasInventorySnapshot cabinGasBefore = CabinGasInventorySnapshot.capture(level, cabin);
            long cabinGasStartTick = level.getGameTime();

            ItemStack cargoStack = new ItemStack(ModItems.BEAKER_LARGE.get());
            var water = ModReagents.createKey("water");
            long beakerFillCents = ReagentUnits.fromFloat(12.5f);
            long beakerCapacityCents = ReagentUnits.fromFloat(IReagentTrait.resolveCapacity(
                    (IReagentTrait) cargoStack.getItem(), cargoStack, level));
            require(beakerFillCents > 0 && beakerFillCents <= beakerCapacityCents,
                    "authored 12.5-unit beaker fill fits the resolved item-prototype capacity: fill="
                            + beakerFillCents + " cents, capacity=" + beakerCapacityCents + " cents");
            var mixture = ReagentComponent.fromCents(Map.of(water, beakerFillCents));
            cargoStack.set(ModDataComponents.REAGENT.get(), mixture);
            LooseItemEntity cargo = LooseItemEntity.create(level,
                    new net.minecraft.world.phys.Vec3(origin.getX() + 2.5, origin.getY() + 1.2, origin.getZ() + 5.5), cargoStack);
            require(level.addFreshEntity(cargo), "registered real loose-item entity with filled mod beaker admitted");
            UUID cargoId = cargo.getUUID();
            ItemToken cargoToken = new ItemToken("shuttle-cabin-" + UUID.randomUUID());
            UUID cargoWorld = com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage.worldIdentity(level);
            var ledger = LevelItemLocationLedgers.forLevel(level);
            require(ledger.register(cargoToken, new ItemLocation.WorldEntity(cargoWorld, cargoId)).accepted(),
                    "same physical loose root is indexed by existing world-item ownership ledger");
            PowerFixtureReadiness.await(helper, level, 500,
                    () -> PowerRuntime.airAlarmMeter(level, machine).known()
                            && PowerRuntime.airAlarmMeter(level, machine).deliveredWatts() == 100
                            && PowerRuntime.airlockPower(level, door).known()
                            && PowerRuntime.airlockPower(level, door).deliveredWatts() == 20
                            && cargo.settled() && cargo.onGround(),
                    () -> "alarm=" + PowerRuntime.airAlarmMeter(level, machine) + ", airlock="
                            + PowerRuntime.airlockPower(level, door) + ", cargoSettled=" + cargo.settled(),
                    () -> {
                        var settledPosition = cargo.position();
                        long settledMoveSequence = cargo.nativeMoveCapture() == null ? -1
                                : cargo.nativeMoveCapture().sequence();
                        ItemStack settledStack = cargo.getItem().copy();
                        BoundedGameTestWait.safeDelay(helper, 40, () -> {
                        require(level.getEntity(cargoId) == cargo && cargo.settled() && cargo.onGround(),
                                "native settled-optimization state persists on the same physical loose root");
                        require(cargo.position().equals(settledPosition), "settled root remains stationary after reaching its settled coordinate");
                        require(settledMoveSequence > 0 && cargo.nativeMoveCapture() != null
                                        && cargo.nativeMoveCapture().sequence() == settledMoveSequence,
                                "ordinary-world settled cargo preserves the existing no-move 10-tick support cadence");
                        require(ledger.snapshot().locationOf(cargoToken)
                                        .filter(new ItemLocation.WorldEntity(cargoWorld, cargoId)::equals).isPresent(),
                                "exact item token remains owned by the same world/entity UUID");
                        require(ItemStack.matches(settledStack, cargo.getItem())
                                         && Objects.equals(cargo.getItem().get(ModDataComponents.REAGENT.get()), mixture)
                                         && cargo.getItem().get(ModDataComponents.REAGENT.get()).centContents()
                                         .getOrDefault(water, 0L) == beakerFillCents,
                                 "physical loose beaker retains its valid 12.5-unit mixture and exact hundredth-unit contents");
                        require(PowerRuntime.airlockPower(level, door).deliveredWatts() == 20,
                                "authored hatch stays on the real 20 W paid extension path while cabin is static");
                        cabinGasBefore.assertConserved(CabinGasInventorySnapshot.capture(level, cabin),
                                CabinGasInventorySnapshot.precisionOperations(cabin, level.getGameTime() - cabinGasStartTick));
                        require(LoadedPipeGasOwner.activeSnapshot(level, pipe.getBlockPos(), VisualPipeBlockEntity.PRIMARY)
                                        .map(now -> now.incarnation().equals(pipeBefore.incarnation()))
                                        .orElse(false),
                                "stationary waiting preserves real pipe UUID and finite ownership; gas may redistribute");
                        helper.succeed();
                        });
                    });
        }, origin, far);
    }

    @GameTestTags({"character", "hands", "death"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", batch = "shuttle_stationary_cabin", timeoutTicks = 80)
    public static void stationaryCabinRetainsBoundHumanCorpseOwners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        assertFootprintInsideTemplate(helper, origin);
        BlockPos far = origin.offset(ShuttleCabinFixture.WIDTH - 1, ShuttleCabinFixture.HEIGHT - 1,
                ShuttleCabinFixture.DEPTH - 1);
        FixtureChunkTickets.Lease tickets = FixtureChunkTickets.around(helper, origin, far, 2);
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 12, () -> {
            ShuttleCabinFixture.build(level, origin);
            PlayerCharacterHarnessEntity human = BoundHumanFixture.create(level,
                    new net.minecraft.world.phys.Vec3(origin.getX() + 2.5, origin.getY() + 1, origin.getZ() + 4.5), "shuttle-human");
            PlayerCharacterHarnessEntity corpse = BoundHumanFixture.create(level,
                    new net.minecraft.world.phys.Vec3(origin.getX() + 4.5, origin.getY() + 1, origin.getZ() + 4.5), "shuttle-corpse");
            require(DamageSystem.applyHealthChange(corpse, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                            == DamageSystem.Result.APPLIED && MobStateSystem.isDead(corpse) && corpse.isDeadOrDying(),
                    "corpse is produced through real typed damage instead of fixture status edits");
            UUID humanId = human.getUUID(), corpseId = corpse.getUUID();
            Object humanBinding = human.playerCharacterBinding(), corpseBinding = corpse.playerCharacterBinding();
            Object humanHands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            Object corpseHands = corpse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(humanHands != null && corpseHands != null, "real bootstrap installed both human hand owners");
            BoundedGameTestWait.safeDelay(helper, 12, () -> {
                require(level.getEntity(humanId) == human && level.getEntity(corpseId) == corpse
                                && human.playerCharacterBinding().equals(humanBinding)
                                && corpse.playerCharacterBinding().equals(corpseBinding)
                                && MobStateSystem.isDead(corpse),
                        "existing human and damage-produced corpse retain the same bound body/Mind records");
                require(human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == humanHands
                                && corpse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == corpseHands,
                        "hands owner objects remain attached to their exact bodies");
                helper.succeed();
            });
        }, origin, far);
    }

    private static void assertFootprintInsideTemplate(GameTestHelper helper, BlockPos origin) {
        BlockPos far = origin.offset(ShuttleCabinFixture.WIDTH - 1, ShuttleCabinFixture.HEIGHT - 1,
                ShuttleCabinFixture.DEPTH - 1);
        for (BlockPos corner : new BlockPos[]{origin, origin.offset(ShuttleCabinFixture.WIDTH - 1, 0, 0),
                origin.offset(0, ShuttleCabinFixture.HEIGHT - 1, 0), origin.offset(0, 0, ShuttleCabinFixture.DEPTH - 1),
                far})
            require(helper.getBounds().contains(corner.getCenter()),
                    "authored cabin footprint corner remains inside GameTest template bounds: " + corner);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
