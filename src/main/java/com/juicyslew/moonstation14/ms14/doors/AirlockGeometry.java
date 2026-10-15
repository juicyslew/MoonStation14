package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Pure local-space geometry/motion shared by rendering and focused unit tests. */
public final class AirlockGeometry {
    public static final double UPPER_TRAVEL = 21.5 / 16.0;
    public static final double LOWER_TRAVEL = 9.5 / 16.0;
    private static final double CONTACT_EPSILON=1.0E-5;
    private AirlockGeometry() { }

    public record Point(double x,double y,double z) { }
    public record Face(Point a,Point b,Point c,Point d, float nx,float ny,float nz) { }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public List<Face> faces() { return List.of(
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y1,z1),new Point(x0,y1,z1),0,0,1),
                face(new Point(x1,y0,z0),new Point(x0,y0,z0),new Point(x0,y1,z0),new Point(x1,y1,z0),0,0,-1),
                face(new Point(x0,y1,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),new Point(x0,y1,z1),0,1,0),
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x0,y0,z0),0,-1,0),
                face(new Point(x0,y0,z0),new Point(x0,y0,z1),new Point(x0,y1,z1),new Point(x0,y1,z0),-1,0,0),
                face(new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),1,0,0));
        }
    }
    private static Face face(Point a,Point b,Point c,Point d,float x,float y,float z) { return new Face(a,b,c,d,x,y,z); }
    public static double openness(AirlockState.DoorPhase phase,double elapsed,double openOne,double openTwo,double closeOne,double closeTwo) {
        return switch(phase) {
            case OPEN -> 1; case CLOSED,DENYING -> 0;
            case OPENING -> progress(elapsed,openOne);
            case OPENING_PASSABLE -> 1;
            case CLOSING -> 1-progress(elapsed,closeOne);
            case CLOSING_PASSABLE -> 0;
        };
    }
    private static double progress(double elapsed,double duration) { return duration<=0?1:Math.max(0,Math.min(1,elapsed/duration)); }
    public enum Part { FIXED, HEADER_LAMPS, UPPER_LEAF, LOWER_LEAF, UPPER_INFILL, READER_COVER, READER_HOUSING, MAINTENANCE_WIRES }
    private static final Map<Part,List<Box>> MODELS = loadModels();
    /** Source-authored cuboids after the canonical 180-degree clip pose; coordinates are Minecraft block units. */
    public static List<Box> model(Part part) { return MODELS.get(part); }
    /** Canonical authored movement, shared by visible bounds and the baked renderer. */
    public static double movementY(Part part,double openness) {
        double q=Math.max(0,Math.min(1,openness));
        return switch(part) {
            case UPPER_LEAF,UPPER_INFILL,READER_COVER,READER_HOUSING,MAINTENANCE_WIRES -> UPPER_TRAVEL*q;
            case LOWER_LEAF -> -LOWER_TRAVEL*q;
            default -> 0;
        };
    }
    public static List<Box> leaves(double openness) {
        return concat(clip(translate(model(Part.UPPER_LEAF),0,movementY(Part.UPPER_LEAF,openness),0)),clip(translate(model(Part.LOWER_LEAF),0,movementY(Part.LOWER_LEAF,openness),0)));
    }
    private static List<Box> concat(List<Box> a,List<Box> b) { java.util.ArrayList<Box> out=new java.util.ArrayList<>(a);out.addAll(b);return List.copyOf(out); }
    private static List<Box> translate(List<Box> boxes,double x,double y,double z) { return boxes.stream().map(b->new Box(b.x0()+x,b.y0()+y,b.z0()+z,b.x1()+x,b.y1()+y,b.z1()+z)).toList(); }
    private static Map<Part,List<Box>> loadModels() {
        EnumMap<Part,List<Box>> result=new EnumMap<>(Part.class);
        for(Part part:Part.values()) {
            String filename=part.name().toLowerCase(java.util.Locale.ROOT);
            String path="/assets/moonstation14/models/block/airlock/vertical_all/standard_basic/components/"+filename+".json";
            try(var stream=AirlockGeometry.class.getResourceAsStream(path)) {
                if(stream==null) throw new IllegalStateException("Missing airlock geometry resource "+path);
                var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.ArrayList<Box> boxes=new java.util.ArrayList<>();
                for(var element:root.getAsJsonArray("elements")) {
                    var object=element.getAsJsonObject(); var from=object.getAsJsonArray("from");var to=object.getAsJsonArray("to");
                    double[] f={from.get(0).getAsDouble()/16,from.get(1).getAsDouble()/16,from.get(2).getAsDouble()/16};
                    double[] t={to.get(0).getAsDouble()/16,to.get(1).getAsDouble()/16,to.get(2).getAsDouble()/16};
                    for(int axis=0;axis<3;axis++) if(!Double.isFinite(f[axis])||!Double.isFinite(t[axis])||f[axis]>t[axis]) throw new IllegalStateException("Invalid airlock cuboid in "+path);
                    if(object.has("rotation")) throw new IllegalStateException("Rotated airlock cuboid unsupported in "+path);
                    double x0=f[0],x1=t[0],y0=f[1],y1=t[1],z0=f[2],z1=t[2];
                    // Canonical 180-degree Y clip pose: x'=1-x, z'=1-z.
                    boxes.add(new Box(1-x1,y0,1-z1,1-x0,y1,1-z0));
                }
                result.put(part,List.copyOf(boxes));
            } catch(Exception e) { throw new ExceptionInInitializerError(e); }
        }
        return Map.copyOf(result);
    }
    private static List<Box> clip(List<Box> boxes) { return boxes.stream().map(b->new Box(Math.max(0,b.x0()),Math.max(0,b.y0()),Math.max(0,b.z0()),Math.min(1,b.x1()),Math.min(2,b.y1()),Math.min(1,b.z1()))).filter(b->b.x0()<b.x1()&&b.y0()<b.y1()&&b.z0()<b.z1()).toList(); }
    /** Fixed cuboids emitted independently of door motion. */
    public static List<Box> frame() { return concat(clip(model(Part.FIXED)),clip(model(Part.HEADER_LAMPS))); }
    /** World-space bounds of visible authored solids; upper-mounted parts follow the upper leaf. */
    public static List<AABB> worldBounds(BlockPos root, Direction facing, double openness) {
        return worldBounds(root,facing,openness,false);
    }
    public static List<AABB> worldBounds(BlockPos root, Direction facing, double openness, boolean panelOpen) {
        java.util.ArrayList<AABB> bounds=new java.util.ArrayList<>();
        java.util.ArrayList<Box> parts=new java.util.ArrayList<>(frame()); parts.addAll(leaves(openness));
        parts.addAll(clip(translate(model(Part.READER_HOUSING),0,movementY(Part.READER_HOUSING,openness),0)));
        parts.addAll(clip(translate(model(Part.UPPER_INFILL),0,movementY(Part.UPPER_INFILL,openness),0)));
        Part panelPart=panelOpen?Part.MAINTENANCE_WIRES:Part.READER_COVER;
        parts.addAll(clip(translate(model(panelPart),0,movementY(panelPart,openness),0)));
        int rx=facing.getCounterClockWise().getStepX(), rz=facing.getCounterClockWise().getStepZ();
        int fx=facing.getStepX(), fz=facing.getStepZ();
        for(Box b:parts) {
            double[] xs={b.x0(),b.x1()}, ys={b.y0(),b.y1()}, zs={b.z0(),b.z1()};
            double minX=Double.POSITIVE_INFINITY,minY=root.getY()+b.y0(),minZ=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxY=root.getY()+b.y1(),maxZ=Double.NEGATIVE_INFINITY;
            for(double x:xs)for(double z:zs) {
                double wx=root.getX()+.5+rx*(x-.5)+fx*(z-.5), wz=root.getZ()+.5+rz*(x-.5)+fz*(z-.5);
                minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
            }
            bounds.add(new AABB(minX,minY,minZ,maxX,maxY,maxZ));
        }
        return List.copyOf(bounds);
    }
    public static AABB renderBounds(BlockPos root) {
        return new AABB(root.getX(),root.getY(),root.getZ(),root.getX()+1,root.getY()+2,root.getZ()+1);
    }
    /** Actual body/leaf contact, including a face-tangent edge but excluding corners, vertical support, and real gaps. */
    public static boolean touchesDoorway(AABB body,AABB doorway){
        if(body==null||doorway==null||Math.min(body.maxY,doorway.maxY)-Math.max(body.minY,doorway.minY)<=0)return false;
        double x=Math.min(body.maxX,doorway.maxX)-Math.max(body.minX,doorway.minX);
        double z=Math.min(body.maxZ,doorway.maxZ)-Math.max(body.minZ,doorway.minZ);
        return (x>=-CONTACT_EPSILON&&z>0)||(z>=-CONTACT_EPSILON&&x>0);
    }
}

