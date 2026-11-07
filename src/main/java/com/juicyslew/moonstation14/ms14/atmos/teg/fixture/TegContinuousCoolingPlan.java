package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/** Pure geometry declaration for the continuous fixture's parallel cooling array. */
public final class TegContinuousCoolingPlan {
    private TegContinuousCoolingPlan() { }
    public static final int RADIATOR_COUNT = 5;

    public record Device(BlockPos position, Direction facing) {
        public Device { position=position.immutable(); Objects.requireNonNull(facing); }
    }
    public record Layout(BlockPos core, List<Device> radiators, Device pressurePump,
                         List<List<BlockPos>> pipeComponents, Set<BlockPos> exposedPipeHosts,
                         Set<BlockPos> reservedDevicePositions) {
        public Layout {
            core=core.immutable(); radiators=List.copyOf(radiators); pipeComponents=pipeComponents.stream().map(List::copyOf).toList();
            exposedPipeHosts=Set.copyOf(exposedPipeHosts); reservedDevicePositions=Set.copyOf(reservedDevicePositions);
        }
    }

    public static Layout layout(BlockPos core) {
        Objects.requireNonNull(core);
        List<Device> radiators=new ArrayList<>();
        for(int i=0;i<RADIATOR_COUNT;i++) radiators.add(new Device(core.offset(16,5,1+2*i),Direction.WEST));
        Device pump=new Device(core.offset(19,5,9),Direction.WEST);
        List<BlockPos> c0=line(core,1,0,1,7,0,1);
        List<BlockPos> c1=polyline(core,new int[][]{{9,0,1},{10,0,1},{10,5,1},{15,5,1},{15,5,9}});
        List<BlockPos> c2=polyline(core,new int[][]{{17,5,1},{17,5,9},{18,5,9}});
        List<BlockPos> c3=polyline(core,new int[][]{{20,5,9},{21,5,9},{21,6,9},{21,6,11},{-3,6,11},{-3,6,1},{-3,0,1},{-1,0,1}});
        List<List<BlockPos>> components=List.of(c0,c1,c2,c3);
        Set<BlockPos> pipes=new LinkedHashSet<>(); components.forEach(pipes::addAll);
        Set<BlockPos> reserved=new LinkedHashSet<>(); radiators.forEach(r->reserved.add(r.position())); reserved.add(pump.position());
        if(pipes.size()!=components.stream().mapToInt(List::size).sum()||!Collections.disjoint(pipes,reserved))
            throw new IllegalStateException("cooling pipe geometry aliases");
        for(List<BlockPos> component:components) if(!connected(component)) throw new IllegalStateException("disconnected cooling pipe component");
        for(int i=0;i<components.size();i++) for(int j=i+1;j<components.size();j++) {
            Set<BlockPos> a=Set.copyOf(components.get(i));
            for(BlockPos p:components.get(j)) for(Direction d:Direction.values())
                if(a.contains(p.relative(d))) throw new IllegalStateException("adjacent cooling components alias: "+i+"/"+j);
        }
        for(Device radiator:radiators) if(!c1.contains(radiator.position().relative(Direction.WEST))||!c2.contains(radiator.position().relative(Direction.EAST)))
            throw new IllegalStateException("radiator ports do not meet the declared headers");
        if(!c2.contains(pump.position().west())||!c3.contains(pump.position().east())
                ||!c0.contains(core.offset(7,0,1))||!c1.contains(core.offset(9,0,1)))
            throw new IllegalStateException("device port neighbors missing");
        BlockPos origin=core.offset(-22,-2,-18);
        for(BlockPos p:pipes) if(p.getX()<origin.getX()||p.getX()>=origin.getX()+48||p.getY()<origin.getY()||p.getY()>=origin.getY()+9||p.getZ()<origin.getZ()||p.getZ()>=origin.getZ()+48)
            throw new IllegalStateException("cooling geometry outside fixture bounds");
        return new Layout(core,radiators,pump,components,pipes,reserved);
    }

    private static List<BlockPos> line(BlockPos c,int x1,int y1,int z1,int x2,int y2,int z2) {
        List<BlockPos> out=new ArrayList<>(); int dx=Integer.compare(x2,x1),dy=Integer.compare(y2,y1),dz=Integer.compare(z2,z1);
        for(int x=x1,y=y1,z=z1;;x+=dx,y+=dy,z+=dz){out.add(c.offset(x,y,z));if(x==x2&&y==y2&&z==z2)break;} return List.copyOf(out);
    }
    private static List<BlockPos> polyline(BlockPos c,int[][] points) {
        LinkedHashSet<BlockPos> result=new LinkedHashSet<>(); for(int i=1;i<points.length;i++)result.addAll(line(c,points[i-1][0],points[i-1][1],points[i-1][2],points[i][0],points[i][1],points[i][2])); return List.copyOf(result);
    }
    private static boolean connected(List<BlockPos> positions) {
        Set<BlockPos> allowed=Set.copyOf(positions),seen=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>(); queue.add(positions.getFirst());seen.add(positions.getFirst());
        while(!queue.isEmpty()){BlockPos current=queue.removeFirst();for(Direction d:Direction.values()){BlockPos n=current.relative(d);if(allowed.contains(n)&&seen.add(n))queue.addLast(n);}}
        return seen.size()==positions.size();
    }
}
