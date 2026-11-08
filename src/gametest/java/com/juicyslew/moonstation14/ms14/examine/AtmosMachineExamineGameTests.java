package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.Direction;

@PrefixGameTestTemplate(false)
@GameTestTags("atmos-machine-examine")
public final class AtmosMachineExamineGameTests {
    private AtmosMachineExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void pressurePumpExamineUsesResolvedProfileAndCurrentSettings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.GAS_PRESSURE_PUMP.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine))
            throw new GameTestAssertException("placed pressure pump has its native atmos-machine block entity");

        var profile = machine.resolvedProfile();
        var settings = machine.settingsSnapshot();
        require(profile != null && settings != null, "live pump has resolved profile and default settings");
        ExamineContext context = new ExamineContext(null, level, ExamineTarget.block(pos), null, pos,
                level.getBlockState(pos), machine, false);
        ExamineCollector initial = collect(context);
        require(hasKey(initial, "examine.moonstation14.power.unavailable"), "unallocated pump demand is reported unknown");
        require(initial.description().getString().equals(Component.translatable(
                "block.moonstation14.gas_pressure_pump.description").getString()), "authored pressure-pump description is contributed");
        double defaultPressure = settings.targetPressureKpa().orElseThrow();
        require(defaultPressure == 101.325d && hasPressure(initial, "101.33"), "default pressure target row is examined");

        double changedPressure = defaultPressure == 2000d ? 1000d : 2000d;
        require(machine.applySettings(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                new AtmosMachineSettings.NumericValue(changedPressure)), "existing settings operation accepts pressure override");
        ExamineCollector changed = collect(context);
        require(changedPressure == 2000d && hasPressure(changed, "2000"), "examine reports changed pressure override");
        require(!hasPressure(changed, "101.33"), "examine no longer reports prior default pressure");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void otherAtmosMachinesExamineResolvedDescriptionsAndSettings(GameTestHelper helper) {
        var level = helper.getLevel();
        int index = 0;
        AtmosMachineBlockEntity volume = place(helper, ModBlocks.GAS_VOLUME_PUMP.get(), index++);
        requireDescription(level, volume, "block.moonstation14.gas_volume_pump.description");
        var volumeDefault = volume.settingsSnapshot().transferRateM3PerSecond().orElseThrow() * 1000d;
        require(volumeDefault == 200d && hasRow(collect(context(level, volume)), "examine.moonstation14.atmos.transfer-rate", "200"), "volume pump default L/s row");
        double volumeMaximum = volume.resolvedProfile().pump().maxTransferRate().orElseThrow();
        // The profile rates are expressed in L/s; choose a distinct in-range value even
        // for the highest-rate volume pump rather than using a one-size-fits-all rate.
        double volumeChanged = volumeDefault < volumeMaximum ? volumeMaximum : volumeMaximum / 2d;
        require(volume.applySettings(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,
                new AtmosMachineSettings.NumericValue(volumeChanged / 1000d)), "volume-rate override accepted");
        require(hasRow(collect(context(level, volume)), "examine.moonstation14.atmos.transfer-rate", volumeChanged == volumeMaximum ? "200" : "100"), "volume pump changed L/s row");

        AtmosMachineBlockEntity valve = place(helper, ModBlocks.GAS_VALVE.get(), index++);
        requireDescription(level, valve, "block.moonstation14.gas_valve.description");
        require(hasKey(collect(context(level, valve)), "examine.moonstation14.atmos.valve-open"), "valve default open row");
        require(valve.applySettings(AtmosMachineSettings.Operation.SET_VALVE_OPEN, new AtmosMachineSettings.BooleanValue(false)), "valve close accepted");
        require(hasKey(collect(context(level, valve)), "examine.moonstation14.atmos.valve-closed"), "valve changed closed row");

        var heater = assertThermostat(helper, ModBlocks.GAS_THERMO_HEATER.get(), index++, "block.moonstation14.gas_thermo_heater.description");
        require(heater.apcPowerDemandWatts() == 0d && hasKey(collect(context(level, heater)), "examine.moonstation14.power.standby"), "idle heater is explicitly standby");
        var freezer = assertThermostat(helper, ModBlocks.GAS_THERMO_FREEZER.get(), index++, "block.moonstation14.gas_thermo_freezer.description");
        require(freezer.apcPowerDemandWatts() == 0d && hasKey(collect(context(level, freezer)), "examine.moonstation14.power.standby"), "idle freezer is explicitly standby");
        var mixer = place(helper, ModBlocks.GAS_MIXER.get(), index++);
        requireDescription(level, mixer, "block.moonstation14.gas_mixer.description");
        require(!hasKey(collect(context(level, mixer)), "examine.moonstation14.power.unavailable"), "mixer with no APC component has no power row");
        var filter = place(helper, ModBlocks.GAS_FILTER.get(), index++);
        requireDescription(level, filter, "block.moonstation14.gas_filter.description");
        require(!hasKey(collect(context(level, filter)), "examine.moonstation14.power.unavailable"), "filter with no APC component has no power row");
        var exchanger = place(helper, ModBlocks.PASSIVE_HEAT_EXCHANGER.get(), index);
        requireDescription(level, exchanger, "block.moonstation14.passive_heat_exchanger.description");
        require(!hasKey(collect(context(level, exchanger)), "examine.moonstation14.power.unavailable"), "passive exchanger has no power row");
        // The two circulators are independently examinable; the core is intentionally unassembled here.
        var fixture = com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegFixturePlan.layout(
                helper.absolutePos(new BlockPos(10, 1, 10)));
        var corePos = fixture.core();
        level.setBlock(corePos, ModBlocks.TEG_CENTER.get().defaultBlockState().setValue(
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING, fixture.coreFacing()), 3);
        var core = (AtmosMachineBlockEntity) level.getBlockEntity(corePos);
        require(core != null && core.resolvedProfile() != null, "TEG core has resolved profile");
        var missing = collect(context(level, core));
        requireDescription(level, core, "block.moonstation14.teg_center.description");
        require(hasKey(missing, "examine.moonstation14.atmos.teg-missing"), "unassembled core reports missing circulators");
        require(hasKey(missing, "examine.moonstation14.power.standby"), "unassembled TEG demand safely reports standby");
        placeAt(level, ModBlocks.TEG_CIRCULATOR.get(), fixture.northCirculator(), fixture.northFacing());
        placeAt(level, ModBlocks.TEG_CIRCULATOR.get(), fixture.southCirculator(), fixture.southFacing());
        var north = (AtmosMachineBlockEntity) level.getBlockEntity(fixture.northCirculator());
        var south = (AtmosMachineBlockEntity) level.getBlockEntity(fixture.southCirculator());
        require(north != null && south != null, "placed circulator block entities exist");
        // Same-tick setBlock placement does not run the normal on-load port admission lifecycle.
        // Use the fixture owner's normal reconciliation entry point before resolving assembly.
        core.reconcileLoadedPorts(level);
        north.reconcileLoadedPorts(level);
        south.reconcileLoadedPorts(level);
        requireReadyCirculator(north, "north");
        requireReadyCirculator(south, "south");
        require(com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve(level, core).isPresent(),
                "TEG assembly resolves from live reconciled circulators");
        var assembled = collect(context(level, core));
        require(!hasKey(assembled, "examine.moonstation14.atmos.teg-missing"), "correctly oriented circulators resolve TEG assembly");
        require(hasKey(assembled, "examine.moonstation14.atmos.teg-delivery-unknown"), "assembled TEG without fresh allocation reports unknown delivered output");
        require(hasFormattedRow(assembled, "examine.moonstation14.atmos.teg-capacity", "0.0 W"), "assembled TEG reports capacity separately");
        require(!hasKey(assembled, "examine.moonstation14.atmos.teg-power"), "ambiguous legacy TEG power row is absent");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void placedGasMinersExamineInheritedDescription(GameTestHelper helper) {
        var level = helper.getLevel();
        var blocks = new net.minecraft.world.level.block.Block[]{ModBlocks.GAS_MINER_OXYGEN.get(),
                ModBlocks.GAS_MINER_NITROGEN.get(), ModBlocks.GAS_MINER_PLASMA.get()};
        for (int i = 0; i < blocks.length; i++) {
            var pos = helper.absolutePos(new BlockPos(2 + i, 1, 2));
            level.setBlock(pos, blocks[i].defaultBlockState(), 3);
            if (!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner)
                    || miner.resolvedProfile() == null)
                throw new GameTestAssertException("placed gas miner has resolved profile");
            var context = new ExamineContext(null, level, ExamineTarget.block(pos), null, pos,
                    level.getBlockState(pos), miner, false);
            require(collect(context).description().getString().equals(Component.translatable(
                    "block.moonstation14.gas_miner.description").getString()), "inherited miner description contributed");
        }
        helper.succeed();
    }

    private static AtmosMachineBlockEntity assertThermostat(GameTestHelper h, net.minecraft.world.level.block.Block block, int slot, String description) {
        var level = h.getLevel(); var machine = place(h, block, slot); requireDescription(level, machine, description);
        double initial = machine.settingsSnapshot().thermostatSetpointK().orElseThrow();
        require(initial == 293.15d && hasRow(collect(context(level, machine)), "examine.moonstation14.atmos.temperature-target", "293.15"), "thermostat default K row");
        double changed = initial == 273d ? 300d : 273d;
        require(machine.applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K, new AtmosMachineSettings.NumericValue(changed)), "thermostat override accepted");
        require(hasRow(collect(context(level, machine)), "examine.moonstation14.atmos.temperature-target", "273"), "thermostat changed K row");
        return machine;
    }

    private static AtmosMachineBlockEntity place(GameTestHelper h, net.minecraft.world.level.block.Block block, int slot) {
        BlockPos pos = h.absolutePos(new BlockPos(2 + slot % 8, 1, 2 + slot / 8));
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        if (!(h.getLevel().getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine) || machine.resolvedProfile() == null)
            throw new GameTestAssertException("placed machine has a resolved atmos profile: " + block);
        return machine;
    }

    private static void placeAt(ServerLevel level, net.minecraft.world.level.block.Block block, BlockPos pos, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING, facing), 3);
    }
    private static void requireReadyCirculator(AtmosMachineBlockEntity machine, String label) {
        var ports = machine.gasPorts();
        require(machine.resolvedProfile() != null && ports.size() == 2
                        && ports.stream().allMatch(port -> port.incarnation() != null),
                label + " circulator has resolved profile and reconciled live port incarnations");
    }
    private static ExamineContext context(ServerLevel level, AtmosMachineBlockEntity machine) {
        var pos = machine.getBlockPos(); return new ExamineContext(null, level, ExamineTarget.block(pos), null, pos, level.getBlockState(pos), machine, false);
    }
    private static void requireDescription(ServerLevel level, AtmosMachineBlockEntity machine, String key) {
        var profile = machine.resolvedProfile(); require(profile != null, "resolved machine profile present");
        require(collect(context(level, machine)).description().getString().equals(Component.translatable(key).getString()), "authored description " + key);
    }
    private static boolean hasKey(ExamineCollector c, String key) {
        return c.sortedEntries().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key));
    }
    private static boolean hasRow(ExamineCollector c, String key, String expected) {
        return c.sortedEntries().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(key) && t.getArgs().length > 0
                && t.getArgs()[0].toString().equals(expected));
    }
    private static boolean hasFormattedRow(ExamineCollector c, String key, String value) {
        return c.sortedEntries().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(key) && t.getArgs().length > 0 && value.equals(t.getArgs()[0]));
    }

    private static ExamineCollector collect(ExamineContext context) {
        ExamineCollector collector = new ExamineCollector(false, Component.literal("Pressure pump"));
        BuiltinExamineContributors.metadata(context, collector);
        BuiltinExamineContributors.receiverPower(context, collector);
        return collector;
    }

    private static boolean hasPressure(ExamineCollector collector, String expected) {
        return collector.sortedEntries().stream().anyMatch(entry -> entry.text().getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && translated.getKey().equals("examine.moonstation14.atmos.pressure-target")
                && translated.getArgs().length == 1
                && translated.getArgs()[0].toString().equals(expected));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
