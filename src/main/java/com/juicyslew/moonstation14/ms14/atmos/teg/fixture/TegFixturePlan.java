package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/** Canonical fixture geometry and isolated power topology; finite gas/power quantities are seeded separately. */
public final class TegFixturePlan {
    public static final int WIDTH = 25, HEIGHT = 4, DEPTH = 27;
    private TegFixturePlan() { }

    /** Core faces north: its left APC/LV side is west and its right HV output side is east. */
    public static Layout layout(BlockPos core) {
        Objects.requireNonNull(core, "core");
        Direction facing = Direction.NORTH;
        TegAssembly.Layout assembly = TegAssembly.layout(core, facing);
        if (!assembly.circulatorA().equals(core.north())
                || assembly.circulatorAFacing() != Direction.EAST
                || !assembly.circulatorB().equals(core.south())
                || assembly.circulatorBFacing() != Direction.WEST)
            throw new IllegalStateException("TEG assembly layout contract changed");
        var devices = new Devices(core.immutable(), assembly.circulatorA(), assembly.circulatorB(),
                core.offset(-5, 0, 0), core.offset(0, 0, 7), core.offset(5, 0, 10), core.offset(10, 0, 16));
        return new Layout(core.immutable(), facing, core.west(), core.east(), assembly.circulatorA(),
                assembly.circulatorAFacing(), assembly.circulatorB(), assembly.circulatorBFacing(), devices,
                cables(core), cableHosts(core));
    }

    public record Layout(BlockPos core, Direction coreFacing, BlockPos lvReceiver, BlockPos hvOutput,
                         BlockPos northCirculator, Direction northFacing,
                         BlockPos southCirculator, Direction southFacing, Devices devices,
                         List<CableFaceNode> cables, Set<BlockPos> cableHosts) {
        public Layout {
            Objects.requireNonNull(core); Objects.requireNonNull(coreFacing);
            Objects.requireNonNull(lvReceiver); Objects.requireNonNull(hvOutput);
            Objects.requireNonNull(northCirculator); Objects.requireNonNull(northFacing);
            Objects.requireNonNull(southCirculator); Objects.requireNonNull(southFacing);
            Objects.requireNonNull(devices); cables=List.copyOf(cables); cableHosts=Set.copyOf(cableHosts);
            if (coreFacing != Direction.NORTH || !lvReceiver.equals(core.west()) || !hvOutput.equals(core.east())
                    || !northCirculator.equals(core.north()) || northFacing != Direction.EAST
                    || !southCirculator.equals(core.south()) || southFacing != Direction.WEST)
                throw new IllegalArgumentException("invalid TEG fixture orientation");
        }
    }

    public record Devices(BlockPos core, BlockPos northCirc, BlockPos southCirc, BlockPos startupApc,
                          BlockPos substation, BlockPos outputApc, BlockPos load) { }

    /** Pure circuit assertion used before placement; the two APC-tier islands must remain disjoint. */
    public static boolean powerTopologyValid(Layout layout) {
        var d=layout.devices();
        List<DevicePort> ports=List.of(
                new DevicePort(d.startupApc(),Direction.WEST,CableTier.APC),
                new DevicePort(d.core(),Direction.WEST,CableTier.APC),
                new DevicePort(d.core(),Direction.EAST,CableTier.HV),
                new DevicePort(d.substation(),Direction.EAST,CableTier.HV),
                new DevicePort(d.substation(),Direction.WEST,CableTier.MV),
                new DevicePort(d.outputApc(),Direction.WEST,CableTier.MV),
                new DevicePort(d.outputApc(),Direction.EAST,CableTier.APC),
                new DevicePort(d.load(),Direction.NORTH,CableTier.APC));
        if(ports.stream().anyMatch(p->layout.cables().stream().noneMatch(c->PowerTopology.deviceAdjacent(p,c))))return false;
        return connected(layout.cables(),ports.get(0),ports.get(1))
                && connected(layout.cables(),ports.get(2),ports.get(3))
                && connected(layout.cables(),ports.get(4),ports.get(5))
                && connected(layout.cables(),ports.get(6),ports.get(7))
                && !connected(layout.cables(),ports.get(0),ports.get(6));
    }
    public static boolean connected(List<CableFaceNode> nodes,DevicePort first,DevicePort last) {
        Set<CableFaceNode> seen=new HashSet<>();ArrayDeque<CableFaceNode> todo=new ArrayDeque<>();
        nodes.stream().filter(n->PowerTopology.deviceAdjacent(first,n)).forEach(n->{seen.add(n);todo.add(n);});
        Set<CableFaceNode> targets=new HashSet<>();nodes.stream().filter(n->PowerTopology.deviceAdjacent(last,n)).forEach(targets::add);
        while(!todo.isEmpty()){var current=todo.remove();if(targets.contains(current))return true;for(var candidate:nodes)
            if(!seen.contains(candidate)&&PowerTopology.cableEdge(current,candidate).isPresent()){seen.add(candidate);todo.add(candidate);}}
        return false;
    }

    private static List<CableFaceNode> cables(BlockPos c) {
        var n = new LinkedHashSet<CableFaceNode>();
        add(n,c.offset(-6,0,0),Direction.EAST,CableTier.APC);
        for(int x=-5;x<=0;x++) add(n,c.offset(x,-1,0),Direction.UP,CableTier.APC);
        add(n,c.offset(-1,0,0),Direction.EAST,CableTier.APC);
        add(n,c.offset(1,0,0),Direction.WEST,CableTier.HV);
        add(n,c.offset(1,0,1),Direction.WEST,CableTier.HV);
        add(n,c.offset(1,0,2),Direction.WEST,CableTier.HV);
        add(n,c.offset(0,-1,2),Direction.UP,CableTier.HV);
        for(int x=0;x<=2;x++) add(n,c.offset(x,-1,2),Direction.UP,CableTier.HV);
        for(int z=3;z<=7;z++) add(n,c.offset(0,-1,z),Direction.UP,CableTier.HV);
        add(n,c.offset(1,0,7),Direction.WEST,CableTier.HV);
        // Isolated substation MV branch: route on a separate wall plane before turning down.
        add(n,c.offset(-1,0,7),Direction.EAST,CableTier.MV);
        for(int z=8;z<=10;z++) add(n,c.offset(-1,0,z),Direction.EAST,CableTier.MV);
        add(n,c.offset(0,-1,10),Direction.UP,CableTier.MV);
        for(int x=0;x<=5;x++) add(n,c.offset(x,-1,10),Direction.UP,CableTier.MV);
        add(n,c.offset(4,0,10),Direction.EAST,CableTier.MV);
        // APC output serves only the isolated 100 W lamp; no source is present on this graph.
        add(n,c.offset(6,0,10),Direction.WEST,CableTier.APC);
        for(int z=11;z<=13;z++) add(n,c.offset(6,0,z),Direction.WEST,CableTier.APC);
        add(n,c.offset(5,-1,13),Direction.UP,CableTier.APC);
        for(int x=5;x<=10;x++) add(n,c.offset(x,-1,13),Direction.UP,CableTier.APC);
        for(int z=14;z<=16;z++) add(n,c.offset(10,-1,z),Direction.UP,CableTier.APC);
        add(n,c.offset(10,0,15),Direction.SOUTH,CableTier.APC);
        return List.copyOf(n);
    }
    private static Set<BlockPos> cableHosts(BlockPos c) {
        Set<BlockPos> out=new LinkedHashSet<>(); for(var n:cables(c)) out.add(n.host()); return Set.copyOf(out);
    }
    private static void add(Set<CableFaceNode> out,BlockPos host,Direction face,CableTier tier) { out.add(new CableFaceNode(host,face,tier)); }
}
