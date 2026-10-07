package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"ventilation", "atmosphere", "power"})
@PrefixGameTestTemplate(false)
public final class PaidVentilationFlowGameTests {
    private static long syntheticGameTime;
    private PaidVentilationFlowGameTests() { }

    @GameTestTags("simulation-defaults")
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void onlyCompletedPaidSolveMovesConservedGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = fixture(helper);
        GasMixture pipeBefore = pipe(f);
        GasMixture roomBefore = f.atmosphere.sample(level, f.room).orElseThrow();
        awaitPaidInterval(helper, level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(level, f.vent).known(), "real power chain provides paid interval");
            GasMixture pipeAfter = pipe(f);
            GasMixture roomAfter = f.atmosphere.sample(level, f.room).orElseThrow();
            require(roomAfter.totalMoles() > roomBefore.totalMoles(), "paid vent increases room gas");
            require(pipeAfter.totalMoles() < pipeBefore.totalMoles(), "paid vent decreases pipe gas; pipe before=" + pipeBefore
                    + ", pipe after=" + pipeAfter + "; room before=" + roomBefore + ", room after=" + roomAfter
                    + diagnostic(level, f));
            conserve(pipeBefore, roomBefore, pipeAfter, roomAfter);

            PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(f.apc);
            require(apc != null && apc.toggleBreaker(player(level, f.apc), 0), "open APC breaker");
            GasMixture stoppedPipe = pipe(f);
            GasMixture stoppedRoom = f.atmosphere.sample(level, f.room).orElseThrow();
            helper.runAfterDelay(40, () -> {
                require(same(stoppedPipe, pipe(f)), "breaker prevents additional pipe transfer");
                require(same(stoppedRoom, f.atmosphere.sample(level, f.room).orElseThrow()),
                        "breaker prevents additional room transfer");
                helper.succeed();
            });
        });
    }

    @GameTestTags("simulation-defaults")
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void disabledDeviceDoesNotMoveGas(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var settings = f.vent.config().settings();
        require(f.vent.config().compareAndSet(settings.revision(), new VentilationDeviceConfig.Settings(
                settings.revision(), false, settings.externalPressureBoundKPa(), settings.targetPressureKPa(),
                settings.targetPressureKPa(), settings.volumeRateM3PerSecond(), settings.scrubberMode(),
                settings.gasesToScrub(), settings.selectedLayer())), "disable vent config");
        GasMixture pipeBefore = pipe(f), roomBefore = f.atmosphere.sample(f.level, f.room).orElseThrow();
        awaitPaidInterval(helper, f.level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(f.level, f.vent).known(), "paid solve exists");
            require(same(pipeBefore, pipe(f)), "disabled configuration leaves pipe unchanged");
            require(same(roomBefore, f.atmosphere.sample(f.level, f.room).orElseThrow()), "disabled configuration leaves room unchanged");
            helper.succeed();
        });
    }

    @GameTestTags("simulation-defaults")
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void poweredScrubberCapturesOnlySelectedGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = fixture(helper, true);
        GasMixture pipeBefore = pipe(f);
        GasMixture roomBefore = f.atmosphere.sample(level, f.room).orElseThrow();
        awaitPaidInterval(helper, level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(level, f.vent).known(), "real power chain provides scrubber paid interval");
            GasMixture pipeAfter = pipe(f);
            GasMixture roomAfter = f.atmosphere.sample(level, f.room).orElseThrow();
            require(pipeAfter.moles(GasType.CARBON_DIOXIDE) > pipeBefore.moles(GasType.CARBON_DIOXIDE),
                    "scrubber captures room CO2 into vacuum waste pipe; pipe before=" + pipeBefore + ", pipe after=" + pipeAfter
                            + "; room before=" + roomBefore + ", room after=" + roomAfter + diagnostic(level, f));
            require(close(pipeAfter.moles(GasType.OXYGEN), 0) && close(pipeAfter.moles(GasType.NITROGEN), 0),
                    "scrubber does not transfer unselected gases");
            require(close(roomAfter.moles(GasType.OXYGEN), roomBefore.moles(GasType.OXYGEN))
                            && close(roomAfter.moles(GasType.NITROGEN), roomBefore.moles(GasType.NITROGEN)),
                    "scrubber leaves room oxygen and nitrogen unchanged");
            require(roomAfter.moles(GasType.CARBON_DIOXIDE) < roomBefore.moles(GasType.CARBON_DIOXIDE),
                    "scrubber reduces room CO2");
            conserve(pipeBefore, roomBefore, pipeAfter, roomAfter);

            PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(f.apc);
            require(apc != null && apc.toggleBreaker(player(level, f.apc), 0), "open APC breaker");
            GasMixture stoppedPipe = pipe(f);
            GasMixture stoppedRoom = f.atmosphere.sample(level, f.room).orElseThrow();
            helper.runAfterDelay(40, () -> {
                require(same(stoppedPipe, pipe(f)), "breaker prevents further scrubber transfer");
                require(same(stoppedRoom, f.atmosphere.sample(level, f.room).orElseThrow()),
                        "breaker prevents further room transfer");
                helper.succeed();
            });
        });
    }

    private static void awaitPaidInterval(GameTestHelper helper, ServerLevel level,
                                          VentilationDeviceBlockEntity vent, Runnable onPaidInterval) {
        AtomicBoolean done = new AtomicBoolean();
        for (int tick = 1; tick <= 260; tick++) {
            int checkTick = tick;
            helper.runAtTickTime(checkTick, () -> {
                if (done.get()) return;
                var meter = PowerRuntime.ventilationMeter(level, vent);
                boolean available = meter.known() && meter.deliveredWatts() >= 5;
                if (available) {
                    done.set(true);
                    onPaidInterval.run();
                    return;
                }
                if (checkTick == 260) {
                    done.set(true);
                    throw new GameTestAssertException("paid ventilation meter did not become known at >=5W before deadline; meter="
                            + meter
                            + ", solve=" + PowerRuntime.lastSolveDiagnostic(level));
                }
            });
        }
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, false);
    }

    private static Fixture fixture(GameTestHelper helper, boolean scrubber) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.INSTANCE;
        // These tests intentionally keep the fixture's full power/pipe/room neighborhood loaded:
        // production graph discovery must not depend on unrelated, randomly unloaded test chunks.
        BlockPos footprintMin = helper.absolutePos(new BlockPos(2, 0, 1));
        BlockPos footprintMax = helper.absolutePos(new BlockPos(7, 3, 6));
        for (int cx = (footprintMin.getX() >> 4) - 1; cx <= (footprintMax.getX() >> 4) + 1; cx++)
            for (int cz = (footprintMin.getZ() >> 4) - 1; cz <= (footprintMax.getZ() >> 4) + 1; cz++)
                level.getChunk(cx, cz);
        // One-cell sealed room, with the vent occupying the south wall and the room facing north.
        for (int x = 5; x <= 7; x++) for (int z = 3; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) {
                boolean opening = x == 6 && z == 4 && y == 1;
                helper.setBlock(new BlockPos(x, y, z), opening ? Blocks.AIR : Blocks.STONE);
            }
        }
        BlockPos ventPos = helper.absolutePos(new BlockPos(6, 1, 5));
        helper.setBlock(new BlockPos(6, 1, 5), (scrubber ? ModBlocks.GAS_SCRUBBER.get() : ModBlocks.GAS_VENT.get()).defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.NORTH));
        BlockPos pipePos = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.setBlock(new BlockPos(6, 1, 6), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedSealedWallSegment.prepareOne(level, pipePos, Direction.NORTH,
                PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(), "admit straight sealed wall lane");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pipePos,
                PipeTopology.Layer.PRIMARY), "create sealed wall gas owner");
        // Live source -> substation -> APC power path.
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos sub = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 5));
        place(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        place(level, sub, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        place(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(4, 1, z), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "HV cable");
        }
        for (int z = 3; z <= 5; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "MV cable");
        }
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.STONE);
        require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, 5)), Direction.WEST, CableTier.APC), "APC cable");
        for (int x = 3; x <= 6; x++) {
            // Cable records require a full-collision sturdy host; the template's floor is not
            // guaranteed at these cells, and the room floor only covers x=5..7.
            helper.setBlock(new BlockPos(x, 0, 5), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(x, 0, 5)),
                    Direction.UP, CableTier.APC), "floor APC cable at x=" + x);
        }
        BlockPos room = helper.absolutePos(new BlockPos(6, 1, 4));
        for (int i = 0; i < 12 && atmosphere.sample(level, room).isEmpty(); i++)
            atmosphere.tick(level, syntheticGameTime += AtmosphereService.TICK_CADENCE);
        require(atmosphere.sample(level, room).isPresent(), "room classified finite" + diagnostic(level,
                new Fixture(level, atmosphere, (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos), room, pipePos, BlockPos.ZERO)));
        double ambientMoles = atmosphere.sample(level, room).orElseThrow().totalMoles();
        require(atmosphere.removeGasUpTo(level, room, ambientMoles) > 0, "purge authored ambient for controlled fixture gas");
        if (scrubber) {
            require(atmosphere.addGas(level, room, GasType.OXYGEN, 100, 300), "seed finite room oxygen");
            require(atmosphere.addGas(level, room, GasType.NITROGEN, 300, 300), "seed finite room nitrogen");
            require(atmosphere.addGas(level, room, GasType.CARBON_DIOXIDE, 20, 300), "seed finite room CO2");
        }
        LevelChunk chunk = (LevelChunk) level.getChunk(room);
        require(chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                .isFiniteClaimed(room.getX() & 15, room.getY(), room.getZ() & 15), "finite room ownership");
        var owner = LoadedSealedWallGasOwner.activeSnapshot(level, pipePos, PipeTopology.Layer.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("sealed wall gas owner"));
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        var entry = before.get(key).orElseThrow();
        var after = before.withMixture(key, owner.incarnation(), entry.version(), scrubber ? GasMixture.vacuum() : new GasMixture(
                Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310));
        require(saved.replace(before.revision(), before, after), "seed pipe gas");
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(vent != null, "vent block entity");
        return new Fixture(level, atmosphere, vent, room, pipePos, apc);
    }

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("unsupported power device");
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }
    private static GasMixture pipe(Fixture f) {
        return LoadedSealedWallGasOwner.activeSnapshot(f.level, f.pipe, PipeTopology.Layer.PRIMARY).orElseThrow().mixture();
    }
    private static String diagnostic(ServerLevel level, Fixture f) {
        StringBuilder result = new StringBuilder("; fixture topology:");
        for (Direction direction : Direction.values()) {
            BlockPos pos = f.vent.getBlockPos().relative(direction);
            var state = level.getBlockState(pos);
            result.append(" [").append(direction).append('=').append(pos).append(',').append(state)
                    .append(",passable=").append(AtmosphereTopology.isPassable(level, pos));
            var gas = f.atmosphere.sample(level, pos);
            result.append(",finiteSample=").append(gas.map(Object::toString).orElse("none")).append(']');
        }
        var roomChunk = level.getChunk(f.room);
        var data = roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        result.append("; room=").append(f.room).append(",claim=")
                .append(data != null && data.isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15));
        result.append("; ventFacing=").append(level.getBlockState(f.vent.getBlockPos()).getValue(VentilationDeviceBlock.FACING));
        result.append("; pipeOwner=").append(LoadedSealedWallGasOwner.activeSnapshot(level, f.pipe, PipeTopology.Layer.PRIMARY));
        return result.toString();
    }
    private static Player player(ServerLevel level, BlockPos pos) {
        Player result = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "paid-vent-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        result.getAbilities().instabuild = true;
        return result;
    }
    private static void conserve(GasMixture p0, GasMixture r0, GasMixture p1, GasMixture r1) {
        for (GasType gas : GasType.values()) require(close(p0.moles(gas) + r0.moles(gas), p1.moles(gas) + r1.moles(gas)), "conserve " + gas);
        require(close(p0.thermalEnergy() + r0.thermalEnergy(), p1.thermalEnergy() + r1.thermalEnergy()), "conserve thermal energy");
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (!close(a.thermalEnergy(), b.thermalEnergy())) return false;
        for (GasType gas : GasType.values()) if (!close(a.moles(gas), b.moles(gas))) return false;
        return true;
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
    private record Fixture(ServerLevel level, AtmosphereService atmosphere, VentilationDeviceBlockEntity vent,
                           BlockPos room, BlockPos pipe, BlockPos apc) { }
}
