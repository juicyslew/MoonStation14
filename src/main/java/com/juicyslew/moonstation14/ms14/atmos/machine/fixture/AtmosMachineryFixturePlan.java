package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePortDirections;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Function;

/** Pure bounded geometry for isolated atmos-machine stations. Origin is the floor plane; machines rest at local Y=1. */
public final class AtmosMachineryFixturePlan {
    public static final int WIDTH = 64, DEPTH = 64, HEIGHT = 6;
    public static final BlockPos VENTILATION_ORIGIN = new BlockPos(2,0,52);
    public static final BlockPos CROSSING_CENTER = new BlockPos(53,1,23);
    public static final List<Integer> MINER_X = List.of(27,40,53);
    public enum Kind { PRESSURE_PUMP, VOLUME_PUMP, MIXER, FILTER, VALVE, HEATER, FREEZER, PASSIVE_EXCHANGER, MANIFOLD }
    public record Placement(Kind kind, BlockPos position, Direction facing, String blockId,
                            List<PipeSeed> inletSeeds, List<PipeSeed> outletSeeds) {
        public Placement {
            position = position.immutable();
            inletSeeds = List.copyOf(inletSeeds);
            outletSeeds = List.copyOf(outletSeeds);
        }
    }
    public record PipeSeed(BlockPos position, Direction.Axis axis, AtmosMachineProfile.PortName port, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer layer) {
        public PipeSeed { position = position.immutable(); }
    }
    public record Label(Kind kind, BlockPos targetPosition, BlockPos position, Direction facing, String text) {
        public Label { targetPosition=targetPosition.immutable(); position=position.immutable(); }
    }

    private final List<Placement> placements;
    private final List<Label> labels;
    private AtmosMachineryFixturePlan(Function<ResourceLocation, AtmosMachineProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles");
        var all = new ArrayList<Placement>();
        Kind[] kinds = Kind.values();
        String[] ids = {"gas_pressure_pump", "gas_volume_pump", "gas_mixer", "gas_filter", "gas_valve", "gas_thermo_heater", "gas_thermo_freezer", "passive_heat_exchanger", "gas_pipe_manifold"};
        AtmosMachineProfile.Kind[] profileKinds = {AtmosMachineProfile.Kind.PRESSURE_PUMP,
                AtmosMachineProfile.Kind.VOLUME_PUMP, AtmosMachineProfile.Kind.MIXER,
                AtmosMachineProfile.Kind.FILTER, AtmosMachineProfile.Kind.MANUAL_VALVE, AtmosMachineProfile.Kind.HEATER, AtmosMachineProfile.Kind.FREEZER,
                AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER, AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD};
        for (int i = 0; i < kinds.length; i++) {
            // Three-by-three independently plumbed bays; the intervening two-cell aisles
            // remain clear for inspection and the finite-room glass does not crowd ports.
            BlockPos machine = stationPosition(kinds[i]);
            Direction facing = i == 0 || i == 1 || i == 5 || i == 6 ? Direction.SOUTH : Direction.NORTH;
            var resolved = Objects.requireNonNull(profiles.apply(ModAtmosMachines.id(ids[i])), "missing profile " + ids[i]);
            if (resolved.kind() != profileKinds[i]) throw new IllegalArgumentException("wrong profile kind for " + ids[i]);
            List<PipeSeed> in = new ArrayList<>(), out = new ArrayList<>();
            for (var port : resolved.ports()) {
                boolean inlet = port.name() == AtmosMachineProfile.PortName.INLET
                        || (port.name() == AtmosMachineProfile.PortName.FILTER && kinds[i] == Kind.MIXER);
                boolean outlet = port.name() == AtmosMachineProfile.PortName.OUTLET
                        || (port.name() == AtmosMachineProfile.PortName.FILTER && kinds[i] == Kind.FILTER);
                if (kinds[i] == Kind.MANIFOLD) { inlet = port.name().name().startsWith("NORTH"); outlet = port.name().name().startsWith("SOUTH"); }
                if (kinds[i] == Kind.FREEZER || kinds[i] == Kind.HEATER || kinds[i] == Kind.PASSIVE_EXCHANGER) inlet = port.name() == AtmosMachineProfile.PortName.PIPE || port.name() == AtmosMachineProfile.PortName.INLET;
                if (!inlet && !outlet) continue;
                Direction d = AtmosMachinePortDirections.worldDirection(port.direction(), facing);
                var seed = new PipeSeed(machine.relative(d, 1), axis(d), port.name(), port.fixedLayer().orElse(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
                (inlet ? in : out).add(seed);
            }
            all.add(new Placement(kinds[i], machine, facing, ids[i], in, out));
        }
        placements = List.copyOf(all);
        String[] text = {"Pressure pump\nPlasma + N2", "Volume pump\nPlasma + N2", "Mixer\nPl + H2O",
                "Gas filter\nPl + H2O", "Manual valve\nPlasma + N2", "Gas heater\nMeasured ΔT", "Gas freezer\nMeasured ΔT", "Heat exchanger\nRoom/pipe ΔE", "Manifold\n1 mix/6 ports"};
        var plannedLabels = new ArrayList<Label>();
        for (int i=0;i<all.size();i++) {
            var placement=all.get(i);
            // Keep the operator sign beside the appliance, away from its axial port
            // and the room/ventilation corridor extending from that port.
            var inlet=placement.inletSeeds().stream().findFirst();
            BlockPos label;
            if (placement.kind()==Kind.PASSIVE_EXCHANGER) label=placement.position().relative(Direction.EAST,3).relative(Direction.SOUTH);
            else if (inlet.isPresent()) {
                Direction gasDirection=Direction.getNearest(inlet.get().position().getX()-placement.position().getX(),0,
                        inlet.get().position().getZ()-placement.position().getZ());
                label=placement.position().relative(Direction.EAST).relative(gasDirection.getOpposite());
            } else label=placement.position().relative(Direction.EAST).relative(Direction.SOUTH);
            plannedLabels.add(new Label(placement.kind(),placement.position(),label,Direction.SOUTH,text[i]));
        }
        labels=List.copyOf(plannedLabels);
    }
    private static Direction.Axis axis(Direction d) { return d.getAxis(); }
    public static BlockPos stationPosition(Kind kind) {
        Objects.requireNonNull(kind, "kind");
        int[] grid={8,23,38};
        return new BlockPos(grid[kind.ordinal()%3],1,grid[kind.ordinal()/3]);
    }
    public static AtmosMachineryFixturePlan standard(Function<ResourceLocation, AtmosMachineProfile> profiles) {
        return new AtmosMachineryFixturePlan(profiles);
    }
    public List<Placement> placements() { return placements; }
    public List<Label> labels() { return labels; }
    public BlockPos station(Kind kind) { return placements.stream().filter(p -> p.kind() == kind).findFirst().orElseThrow().position(); }
}
