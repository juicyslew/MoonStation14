package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/** Pure local-coordinate composition for the bounded two-room ventilation demonstration. */
public final class VentilationFixturePlan {
    public static final int WIDTH = 20, DEPTH = 12, HEIGHT = 5;
    public enum BlockRole { AIR, STEEL, GLASS }
    public enum Lane { MAIN }
    public enum Scenario { SCRUBBER_ENABLED_TEST_GAS, VENT_DISABLED }
    public record Cell(int x,int y,int z,BlockRole role) { public BlockPos pos(){return new BlockPos(x,y,z);} }
    public record PowerDevice(PowerDeviceKind kind, BlockPos position, Direction facing) {
        public PowerDevice { position=new BlockPos(position); }
        @Override public BlockPos position(){return new BlockPos(position);}
        public List<com.juicyslew.moonstation14.ms14.power.topology.DevicePort> ports(){return kind.ports(position,facing);}
    }
    public record PipeNode(BlockPos position, boolean concealed, Direction.Axis axis, Lane lane) {
        public PipeNode { position=new BlockPos(position); }
        @Override public BlockPos position(){return new BlockPos(position);}
    }
    private static final VentilationFixturePlan STANDARD=new VentilationFixturePlan();
    private final List<Cell> blocks;
    private final List<BlockPos> sourceCells, recipientCells;
    private final List<PipeNode> pipes;
    private final List<CableFaceNode> cables;
    private final List<PowerDevice> powerDevices;
    private final BlockPos scrubber, vent, alarm, hvSource, substation, apc;
    private VentilationFixturePlan(){
        Map<BlockPos,BlockRole> b=new LinkedHashMap<>();
        // Steel platform and two sealed rooms; glass replaces each room's complete south wall.
        for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++)put(b,x,0,z,BlockRole.STEEL);
        room(b,1,3); room(b,9,3);
        for(int x=1;x<=3;x++)for(int y=1;y<=2;y++)put(b,x,y,6,BlockRole.GLASS);
        for(int x=9;x<=11;x++)for(int y=1;y<=2;y++)put(b,x,y,6,BlockRole.GLASS);
        scrubber=p(2,1,3); vent=p(10,1,3); alarm=p(6,1,6);
        put(b,6,1,7,BlockRole.STEEL);
        for(int z=3;z<=5;z++)put(b,14,1,z,BlockRole.STEEL);
        for(int z=5;z<=7;z++)put(b,16,1,z,BlockRole.STEEL);
        for(int y=1;y<=2;y++)put(b,14,y,7,BlockRole.STEEL);
        List<PipeNode> ps=new ArrayList<>();
        ps.add(new PipeNode(p(2,1,2),true,Direction.Axis.Z,Lane.MAIN));
        ps.add(new PipeNode(p(10,1,2),true,Direction.Axis.Z,Lane.MAIN));
        for(int x=2;x<=10;x++)ps.add(new PipeNode(p(x,1,1),false,Direction.Axis.X,Lane.MAIN));
        pipes=List.copyOf(ps);
        hvSource=p(15,1,3); substation=p(15,1,5); apc=p(15,1,7);
        powerDevices=List.of(new PowerDevice(PowerDeviceKind.HV_SOURCE,hvSource,Direction.WEST),
                new PowerDevice(PowerDeviceKind.HV_MV_SUBSTATION,substation,Direction.WEST),
                new PowerDevice(PowerDeviceKind.APC,apc,Direction.EAST));
        List<CableFaceNode> cs=new ArrayList<>();
        for(int z=3;z<=5;z++)cs.add(new CableFaceNode(p(14,1,z),Direction.EAST,CableTier.HV));
        for(int z=5;z<=7;z++)cs.add(new CableFaceNode(p(16,1,z),Direction.WEST,CableTier.MV));
        for(int y=1;y<=2;y++)cs.add(new CableFaceNode(p(14,y,7),Direction.EAST,CableTier.APC));
        Set<BlockPos> floor=new LinkedHashSet<>();
        for(int x=6;x<=15;x++)floor.add(p(x,0,7));
        for(int z=1;z<=7;z++)floor.add(p(6,0,z));
        for(int x=2;x<=10;x++)floor.add(p(x,0,1));
        for(BlockPos pos:floor)cs.add(new CableFaceNode(pos,Direction.UP,CableTier.APC));
        cables=List.copyOf(cs);
        sourceCells=roomCells(1,3); recipientCells=roomCells(9,3);
        List<Cell> cellPlan=new ArrayList<>(); b.forEach((pos,role)->cellPlan.add(new Cell(pos.getX(),pos.getY(),pos.getZ(),role)));
        blocks=List.copyOf(cellPlan);
    }
    private static void room(Map<BlockPos,BlockRole>b,int x,int z){
        for(int dx=-1;dx<=3;dx++)for(int dz=-1;dz<=3;dz++){
            put(b,x+dx,0,z+dz,BlockRole.STEEL); put(b,x+dx,3,z+dz,BlockRole.STEEL);
            if(dx==-1||dx==3||dz==-1||dz==3)for(int y=1;y<=2;y++)put(b,x+dx,y,z+dz,BlockRole.STEEL);
        }
        for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)for(int y=1;y<=2;y++)put(b,x+dx,y,z+dz,BlockRole.AIR);
    }
    private static void put(Map<BlockPos,BlockRole>b,int x,int y,int z,BlockRole r){b.put(p(x,y,z),r);}
    private static BlockPos p(int x,int y,int z){return new BlockPos(x,y,z);}
    private static List<BlockPos> roomCells(int x,int z){List<BlockPos>r=new ArrayList<>();for(int y=1;y<=2;y++)for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)r.add(p(x+dx,y,z+dz));return List.copyOf(r);}
    public static VentilationFixturePlan standard(){return STANDARD;}
    public List<Cell> blocks(){return blocks;} public List<PipeNode> pipes(){return pipes;}
    public List<CableFaceNode> cables(){return cables;} public List<PowerDevice> powerDevices(){return powerDevices;}
    public List<BlockPos> sourceCells(){return sourceCells;} public List<BlockPos> recipientCells(){return recipientCells;}
    public BlockPos scrubber(){return new BlockPos(scrubber);} public BlockPos vent(){return new BlockPos(vent);} public BlockPos alarm(){return new BlockPos(alarm);}
    public BlockPos hvSource(){return new BlockPos(hvSource);} public BlockPos substation(){return new BlockPos(substation);} public BlockPos apc(){return new BlockPos(apc);}
    public GasType testGas(){return GasType.PLASMA;} public double testGasMolesPerCell(){return 2.0;}
    public String testGasLabel(){return "Plasma";}
    public Map<String,Scenario> scenarios(){return Map.of("scrubber",Scenario.SCRUBBER_ENABLED_TEST_GAS,"vent",Scenario.VENT_DISABLED);}
    public Direction scrubberFacing(){return Direction.SOUTH;} public Direction ventFacing(){return Direction.SOUTH;} public Direction alarmFacing(){return Direction.NORTH;}
}

