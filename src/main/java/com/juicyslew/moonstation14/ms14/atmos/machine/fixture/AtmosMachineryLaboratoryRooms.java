package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;

import java.util.*;

/** Exact finite-room geometry for the exchanger/miner laboratory stations. Caller owns preflight. */
public final class AtmosMachineryLaboratoryRooms {
    public enum Station { EXCHANGER, MINER, DEVICE_ENDPOINT }
    public record Geometry(Station station, BlockPos machine, List<BlockPos> roomCells,
                           List<BlockPos> occupiedPositions, List<BlockPos> glassPositions, List<BlockPos> steelWallCrossings,
                           BlockPos labelPosition) {
        public Geometry {
            machine=machine.immutable(); labelPosition=labelPosition.immutable();
            roomCells=immutable(roomCells); occupiedPositions=immutable(occupiedPositions); glassPositions=immutable(glassPositions); steelWallCrossings=immutable(steelWallCrossings);
        }
        private static List<BlockPos> immutable(List<BlockPos> ps) { return ps.stream().map(BlockPos::immutable).toList(); }
    }
    private AtmosMachineryLaboratoryRooms() { }

    public static Geometry exchanger(BlockPos origin) {
        BlockPos host=origin.offset(AtmosMachineryFixturePlan.stationPosition(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
        var occupied=List.of(host, host.north(), host.south());
        // The exchanger host is intentionally part of the finite chamber candidate;
        // ordinary passability filtering excludes its machine/pipe occupied cells.
        BlockPos local=AtmosMachineryFixturePlan.stationPosition(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
        return geometry(origin, Station.EXCHANGER, host, local.getX()-1,local.getX()+1, local.getZ()-1,local.getZ()+1, occupied, host.relative(net.minecraft.core.Direction.EAST,3));
    }
    public static List<Geometry> miners(BlockPos origin) {
        var result=new ArrayList<Geometry>();
        for (int x : AtmosMachineryFixturePlan.MINER_X) {
            BlockPos host=origin.offset(x,1,55);
            result.add(geometry(origin, Station.MINER, host, x-1, x+1, 54,56, List.of(host), host.offset(3,0,2)));
        }
        return List.copyOf(result);
    }

    /** One passable 3x3x2 finite room around an ordinary vent/scrubber host. */
    public static Geometry endpoint(BlockPos origin, BlockPos ventHost, net.minecraft.core.Direction outward, BlockPos label) {
        BlockPos center=ventHost.relative(outward);
        return centered(origin,Station.DEVICE_ENDPOINT,ventHost,center,label);
    }

    public static Geometry deviceRoom(BlockPos origin, BlockPos machine, List<BlockPos> hosts, BlockPos label) {
        return centered(origin,Station.DEVICE_ENDPOINT,machine,machine,label,hosts);
    }

    public static Geometry deviceRoom(BlockPos origin, BlockPos machine, List<BlockPos> hosts, BlockPos label, BlockPos center) {
        return centered(origin,Station.DEVICE_ENDPOINT,machine,center,label,hosts);
    }

    private static Geometry centered(BlockPos origin,Station station,BlockPos host,BlockPos center,BlockPos label) {
        return centered(origin,station,host,center,label,List.of(host));
    }
    private static Geometry centered(BlockPos origin,Station station,BlockPos host,BlockPos center,BlockPos label,List<BlockPos> hosts) {
        int minX=center.getX()-origin.getX()-1, maxX=minX+2;
        int minZ=center.getZ()-origin.getZ()-1, maxZ=minZ+2;
        return geometry(origin, station, host, minX,maxX,minZ,maxZ,hosts,label);
    }

    /** Layer-independent room boundary crossing two blocks outward from a station port. */
    public static BlockPos sealedCrossing(BlockPos machine, net.minecraft.core.Direction portDirection) {
        return machine.relative(portDirection, 2);
    }
    private static Geometry geometry(BlockPos origin, Station station, BlockPos machine, int minX,int maxX,int minZ,int maxZ,List<BlockPos> occupied,BlockPos label) {
        var cells=new ArrayList<BlockPos>();
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) for(int y=1;y<=2;y++) {
            BlockPos p=origin.offset(x,y,z);
            cells.add(p);
        }
        var glass=new LinkedHashSet<BlockPos>();
        for(int x=minX-1;x<=maxX+1;x++) for(int z=minZ-1;z<=maxZ+1;z++) {
            if(x==minX-1||x==maxX+1||z==minZ-1||z==maxZ+1) for(int y=1;y<=2;y++) glass.add(origin.offset(x,y,z));
        }
        for(int x=minX-1;x<=maxX+1;x++) for(int z=minZ-1;z<=maxZ+1;z++) glass.add(origin.offset(x,3,z));
        return new Geometry(station,machine,cells,occupied,List.copyOf(glass),List.of(),label);
    }

    /** Geometry candidates filtered against the actual runtime atmosphere passability rule. */
    public static List<BlockPos> passableCells(ServerLevel level, Geometry geometry) {
        return geometry.roomCells().stream().filter(p -> AtmosphereTopology.isPassable(level,p)).toList();
    }

    /** Circuit volumes must expose every planned cell; legacy miner chambers admit their actual passable cells only. */
    public static boolean finiteRoomShapeReady(Geometry geometry,List<BlockPos> passableCells,boolean requireEveryPlannedCell) {
        if(geometry==null||passableCells==null||passableCells.isEmpty())return false;
        if(!requireEveryPlannedCell)return true;
        return passableCells.size()==geometry.roomCells().size()
                &&new HashSet<>(passableCells).equals(new HashSet<>(geometry.roomCells()));
    }

    /** Writes only chamber boundary glass; does not clear or replace machines/pipe hosts/interior. */
    public static void build(ServerLevel level, BlockPos origin, Geometry geometry) {
        for(BlockPos p:geometry.glassPositions()) level.setBlock(p, Blocks.GLASS.defaultBlockState(),3);
    }

    /** Builds the distinct endpoint chambers in a planned room-circuit layout. */
    public static void buildCircuitRooms(ServerLevel level, AtmosMachineryRoomCircuitPlan circuits) {
        var seen = new HashSet<Geometry>();
        for (var endpoint : circuits.endpoints()) {
            var room = endpoint.room();
            if (seen.add(room)) {
                build(level, BlockPos.ZERO, room);
                requestDiscovery(level, room);
            }
        }
    }

    /** Normal discovery request; this is an explicit startup mutation and never asserts trusted ownership. */
    public static void requestDiscovery(ServerLevel level, Geometry geometry) {
        for(BlockPos p:geometry.roomCells()) if(AtmosphereTopology.isPassable(level,p))
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,p);
    }
}
