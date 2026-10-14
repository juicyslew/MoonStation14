package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** Deterministic power recipe for the singularity showcase layout. */
public final class SingularityFixturePower {
    private SingularityFixturePower() { }

    public record Circuit(List<BlockPos> devices, List<CableFaceNode> nodes, List<DevicePort> ports,
                          boolean topologyVerified) {
        public Circuit { devices=List.copyOf(devices); nodes=List.copyOf(nodes); ports=List.copyOf(ports); }
    }

    public static Circuit bootstrap(ServerLevel level, SingularityFixtureLayout.Placed placed) {
        BlockPos o=placed.origin();
        LinkedHashSet<CableFaceNode> planned=new LinkedHashSet<>();
        List<CableFaceNode> nodes;
        List<DevicePort> ports=new ArrayList<>();
        List<BlockPos> devices=new ArrayList<>();
        add(planned,o,19,1,2,Direction.WEST,CableTier.HV); add(planned,o,19,1,3,Direction.WEST,CableTier.HV); add(planned,o,19,1,4,Direction.WEST,CableTier.HV);
        add(planned,o,17,1,4,Direction.EAST,CableTier.MV);
        for(int z=4;z<=9;z++) add(planned,o,18,0,z,Direction.UP,CableTier.MV);
        for(int x=1;x<=18;x++) add(planned,o,x,0,4,Direction.UP,CableTier.MV);
        for(int x=18;x<=19;x++) add(planned,o,x,0,6,Direction.UP,CableTier.MV);
        for(int z=4;z<=13;z++) { add(planned,o,0,1,z,Direction.EAST,CableTier.MV); add(planned,o,14,1,z,Direction.WEST,CableTier.MV); }
        for(int y=2;y<=9;y++) for(int z=4;z<=13;z++) { add(planned,o,0,y,z,Direction.EAST,CableTier.MV); add(planned,o,14,y,z,Direction.WEST,CableTier.MV); }
        for(int y=1;y<=5;y++) add(planned,o,18,y,8,Direction.SOUTH,CableTier.MV);
        add(planned,o,18,1,6,Direction.EAST,CableTier.MV);
        add(planned,o,20,1,6,Direction.WEST,CableTier.APC);
        for(int y=2;y<=5;y++) add(planned,o,20,y,6,Direction.WEST,CableTier.APC);
        add(planned,o,20,5,7,Direction.WEST,CableTier.APC); add(planned,o,20,5,8,Direction.WEST,CableTier.APC); add(planned,o,19,5,7,Direction.SOUTH,CableTier.APC);
        nodes=new ArrayList<>(planned);

        Set<BlockPos> hosts=preflight(level,placed,nodes);
        BlockPos src=o.offset(18,1,2), sub=o.offset(18,1,4), apc=o.offset(19,1,6);
        ports.addAll(PowerDeviceKind.HV_SOURCE.ports(src,Direction.EAST));
        ports.addAll(PowerDeviceKind.HV_MV_SUBSTATION.ports(sub,Direction.EAST));
        ports.addAll(PowerDeviceKind.APC.ports(apc,Direction.WEST));
        for(BlockPos emitter:placed.emitters()) {
            if(!(level.getBlockEntity(emitter) instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity be))
                throw new IllegalStateException("expected emitter power consumer missing at "+emitter);
            ports.addAll(be.powerRuntimePorts());
        }
        for(BlockPos part:placed.acceleratorParts()) {
            if(!(level.getBlockEntity(part) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity be))
                throw new IllegalStateException("expected accelerator part power consumer missing at "+part);
            ports.addAll(be.powerRuntimePorts());
        }
        BlockPos control=placed.acceleratorControl();
        if(!(level.getBlockEntity(control) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity controlBe)
                || !controlBe.role().equals("control"))
            throw new IllegalStateException("expected accelerator control power consumer missing at "+control);
        ports.addAll(controlBe.powerRuntimePorts());
        boolean foundPower=false;
        for(BlockPos part:placed.acceleratorParts()) if(level.getBlockEntity(part) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity be && be.role().equals("power")) {
            ports.addAll(be.powerRuntimePorts()); foundPower=true; break;
        }
        if(!foundPower) throw new IllegalStateException("expected accelerator power-box consumer missing");
        List<DevicePort> hvGroup=List.of(new DevicePort(src,Direction.EAST,CableTier.HV),new DevicePort(sub,Direction.EAST,CableTier.HV));
        List<DevicePort> mvGroup=new ArrayList<>(); mvGroup.add(new DevicePort(sub,Direction.WEST,CableTier.MV)); mvGroup.add(new DevicePort(apc,Direction.WEST,CableTier.MV));
        List<DevicePort> apcGroup=new ArrayList<>(); apcGroup.add(new DevicePort(apc,Direction.EAST,CableTier.APC));
        for(DevicePort p:ports) { if(p.tier()==CableTier.MV&&!p.device().equals(sub)) mvGroup.add(p); if(p.tier()==CableTier.APC&&!p.device().equals(apc)) apcGroup.add(p); }
        if(!validatePlannedComponents(nodes,ports,hvGroup,mvGroup,apcGroup)) throw new IllegalStateException("planned fixture power topology is disconnected");
        for(BlockPos p:hosts) if(level.getBlockState(p).isAir()) level.setBlock(p,isRearWall(o,p)?Blocks.GLASS.defaultBlockState():Blocks.STONE.defaultBlockState(),3);
        level.setBlock(src,ModBlocks.HV_SOURCE.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        level.setBlock(sub,ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        if(!PowerDeviceFixturePlacement.placeApc(level,apc,Direction.WEST)) throw new IllegalStateException("APC support/placement failed at "+apc);
        devices.addAll(List.of(src,sub,apc));
        for(CableFaceNode n:nodes) if(!CableStorage.place(level,n.host(),n.face(),n.tier())) throw new IllegalStateException("cable placement failed at "+n);
        for(CableFaceNode n:nodes) if(!CableStorage.has(level,n.host(),n.face(),n.tier())) throw new IllegalStateException("cable record missing at "+n);
        for(DevicePort p:ports) if(nodes.stream().noneMatch(n->PowerTopology.deviceAdjacent(p,n))) throw new IllegalStateException("device port has no cable node: "+p);
        return new Circuit(devices,nodes,ports,true);
    }

    /** Adds the isolated collector export plane without touching the bootstrap recipe. */
    public static Circuit export(ServerLevel level, SingularityFixtureLayout.Placed placed) {
        BlockPos o=placed.origin();
        LinkedHashSet<CableFaceNode> planned=new LinkedHashSet<>();
        for(int x:new int[]{2,12}) for(int z:new int[]{7,9}) for(int y=1;y<=2;y++) add(planned,o,x,y,z,Direction.SOUTH,CableTier.HV);
        for(int x:new int[]{2,12}) for(int z=8;z<=10;z++) add(planned,o,x,0,z,Direction.UP,CableTier.HV);
        for(int x=2;x<=24;x++) add(planned,o,x,0,10,Direction.UP,CableTier.HV);
        for(int z=10;z<=12;z++) add(planned,o,24,0,z,Direction.UP,CableTier.HV);
        add(planned,o,24,1,11,Direction.SOUTH,CableTier.HV);
        BlockPos sub=o.offset(24,1,12), apc=o.offset(24,3,12), lamp=o.offset(24,3,14);
        for(int y=1;y<=3;y++) add(planned,o,24,y,13,Direction.NORTH,CableTier.MV);
        add(planned,o,23,3,12,Direction.EAST,CableTier.MV);
        add(planned,o,25,3,12,Direction.WEST,CableTier.APC);
        add(planned,o,25,3,13,Direction.WEST,CableTier.APC); add(planned,o,25,3,14,Direction.WEST,CableTier.APC);
        add(planned,o,24,3,13,Direction.SOUTH,CableTier.APC);
        List<CableFaceNode> nodes=new ArrayList<>(planned); List<BlockPos> devices=List.of(sub,apc,lamp);
        List<DevicePort> ports=new ArrayList<>();
        Direction substationFacing=Direction.NORTH;
        ports.addAll(PowerDeviceKind.HV_MV_SUBSTATION.ports(sub,substationFacing));
        ports.addAll(PowerDeviceKind.APC.ports(apc,Direction.WEST));
        ports.addAll(PowerDeviceKind.DEBUG_LOAD_LAMP.ports(lamp,Direction.NORTH));
        for(BlockPos collector:placed.collectors()) {
            if(!(level.getBlockEntity(collector) instanceof com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity be)) throw new IllegalStateException("expected radiation collector missing at "+collector);
            ports.addAll(be.powerRuntimePorts());
        }
        Set<BlockPos> hosts=preflight(level,placed,nodes);
        List<DevicePort> hvGroup=new ArrayList<>(); hvGroup.add(new DevicePort(sub,substationFacing,CableTier.HV));
        for(DevicePort p:ports) if(p.tier()==CableTier.HV&&!p.device().equals(sub)) hvGroup.add(p);
        if(!validatePlannedComponents(nodes,ports,hvGroup,
                List.of(new DevicePort(sub,substationFacing.getOpposite(),CableTier.MV),new DevicePort(apc,Direction.WEST,CableTier.MV)),
                List.of(new DevicePort(apc,Direction.EAST,CableTier.APC),new DevicePort(lamp,Direction.NORTH,CableTier.APC)))) throw new IllegalStateException("planned export topology is disconnected");
        for(BlockPos p:hosts) if(level.getBlockState(p).isAir()) level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(sub,ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,substationFacing),3);
        if(!PowerDeviceFixturePlacement.placeApc(level,apc,Direction.WEST)) throw new IllegalStateException("export APC support/placement failed at "+apc);
        level.setBlock(lamp,ModBlocks.HIGH_LOAD_TEST_LAMP.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.NORTH),3);
        for(CableFaceNode n:nodes) if(!CableStorage.place(level,n.host(),n.face(),n.tier())) throw new IllegalStateException("export cable placement failed at "+n);
        for(CableFaceNode n:nodes) if(!CableStorage.has(level,n.host(),n.face(),n.tier())) throw new IllegalStateException("export cable record missing at "+n);
        return new Circuit(devices,nodes,ports,true);
    }

    private static Set<BlockPos> preflight(ServerLevel level,SingularityFixtureLayout.Placed placed,List<CableFaceNode> nodes) {
        Map<String,CableTier> tiers=new HashMap<>(); Set<BlockPos> hosts=new LinkedHashSet<>();
        for(CableFaceNode n:nodes) { String key=n.host()+":"+n.face(); CableTier prior=tiers.putIfAbsent(key,n.tier()); if(prior!=null&&prior!=n.tier()) throw new IllegalStateException("conflicting cable tiers at "+key); hosts.add(n.host()); }
        for(BlockPos p:hosts) if(placed.occupiedPositions().contains(p)) throw new IllegalStateException("planned cable host overlaps fixture machinery: "+p);
        for(CableFaceNode n:nodes) if(!CableStorage.eligible(level,n.host(),n.face())&&!level.getBlockState(n.host()).isAir()) throw new IllegalStateException("ineligible cable host "+n.host()+" face "+n.face());
        return hosts;
    }

    /** Validates every consumer/supplier port and the explicitly required isolated tier groups. */
    private static boolean validatePlannedComponents(List<CableFaceNode> nodes,List<DevicePort> ports,List<DevicePort>... requiredGroups) {
        Map<CableFaceNode,Set<CableFaceNode>> graph=new HashMap<>(); for(CableFaceNode n:nodes) graph.put(n,new HashSet<>());
        for(int i=0;i<nodes.size();i++) for(int j=i+1;j<nodes.size();j++) if(PowerTopology.cableEdge(nodes.get(i),nodes.get(j)).isPresent()){graph.get(nodes.get(i)).add(nodes.get(j));graph.get(nodes.get(j)).add(nodes.get(i));}
        Map<CableFaceNode,Integer> component=new HashMap<>(); int id=0;
        for(CableFaceNode start:nodes) if(!component.containsKey(start)){ArrayDeque<CableFaceNode> q=new ArrayDeque<>();q.add(start);component.put(start,id);while(!q.isEmpty()){var n=q.remove();for(var next:graph.get(n))if(!component.containsKey(next)){component.put(next,id);q.add(next);}}id++;}
        Map<DevicePort,Integer> roots=new HashMap<>(); for(DevicePort p:ports){CableFaceNode n=nodes.stream().filter(c->PowerTopology.deviceAdjacent(p,c)).findFirst().orElse(null);if(n==null)return false;roots.put(p,component.get(n));}
        for(List<DevicePort> group:requiredGroups) { Integer root=null; for(DevicePort p:group){Integer c=roots.get(p);if(c==null)return false;if(root==null)root=c;else if(!root.equals(c))return false;} }
        return true;
    }
    private static void add(Set<CableFaceNode> out,BlockPos o,int x,int y,int z,Direction face,CableTier tier) { out.add(new CableFaceNode(o.offset(x,y,z),face,tier)); }
    private static boolean isRearWall(BlockPos origin,BlockPos host) {
        int x=host.getX()-origin.getX(), y=host.getY()-origin.getY(), z=host.getZ()-origin.getZ();
        return (x==0||x==14)&&y>=1&&y<=9&&z>=4&&z<=13;
    }
}
