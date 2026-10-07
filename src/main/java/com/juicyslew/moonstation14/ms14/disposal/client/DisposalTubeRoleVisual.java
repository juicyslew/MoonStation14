package com.juicyslew.moonstation14.ms14.disposal.client;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import net.minecraft.core.Direction;
import java.util.*;

/** Immutable, bounded semantic overlays located just outside the octagonal tube barrel. */
public final class DisposalTubeRoleVisual {
    private DisposalTubeRoleVisual() { }
    private static final int CACHE_LIMIT=256;
    private record Key(DisposalEntityData.Kind kind, DisposalDirection preferred, DisposalDirection branch, long diameterBits) { }
    private static final Map<Key,List<Marking>> CACHE=new LinkedHashMap<>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,List<Marking>> eldest) { return size()>CACHE_LIMIT; }
    };
    public static DisposalDirection preferred(DisposalEntityData.Tube tube, DisposalOrientation frame) {
        return DisposalDirection.fromMC(frame.world(tube.topology().directions().get(0).rotate(tube.rotationQuarters())));
    }
    public record Marking(List<DisposalMeshGeometry.Quad> mesh,int red,int green,int blue) {
        public Marking { mesh=List.copyOf(mesh); }
    }
    public static List<Marking> markings(DisposalEntityData.Tube tube,DisposalOrientation frame,double diameter) {
        DisposalDirection preferred=preferred(tube,frame);
        DisposalDirection branch=tube.kind()==DisposalEntityData.Kind.ROUTER
                ? DisposalDirection.fromMC(frame.world(tube.topology().directions().get(1).rotate(tube.rotationQuarters()))) : null;
        Key key=new Key(tube.kind(),preferred,branch,Double.doubleToLongBits(diameter));
        synchronized(CACHE) { return CACHE.computeIfAbsent(key,ignored->build(tube.kind(),preferred,branch,diameter)); }
    }
    private static List<Marking> build(DisposalEntityData.Kind kind,DisposalDirection preferred,DisposalDirection branch,double diameter) {
        var result=new ArrayList<Marking>();
        if(switch(kind){case THREE_WAY,THREE_WAY_FLIPPED,Y_JUNCTION,X_JUNCTION,ROUTER->true;default->false;}) result.add(new Marking(arrow(preferred,diameter),255,190,25));
        if(kind==DisposalEntityData.Kind.ROUTER) result.add(new Marking(arrow(branch,diameter),48,155,255));
        if(kind==DisposalEntityData.Kind.TAGGER) result.add(new Marking(collar(preferred.toMC(),diameter,true),48,155,255));
        if(kind==DisposalEntityData.Kind.TRUNK) result.add(new Marking(collar(Direction.UP,diameter,false),255,190,25));
        return List.copyOf(result);
    }

    /** Arrows are duplicated onto four orthogonal radial faces, beyond the circumscribed barrel radius. */
    private static List<DisposalMeshGeometry.Quad> arrow(DisposalDirection output,double diameter) {
        Direction axis=output.toMC();
        double radius=diameter*.5+.005; var quads=new ArrayList<DisposalMeshGeometry.Quad>();
        for(Direction radial:radials(axis)) {
            Direction tangent=Direction.fromDelta(
                    axis.getStepY()*radial.getStepZ()-axis.getStepZ()*radial.getStepY(),
                    axis.getStepZ()*radial.getStepX()-axis.getStepX()*radial.getStepZ(),
                    axis.getStepX()*radial.getStepY()-axis.getStepY()*radial.getStepX());
            for(double offset:new double[]{0,.075}) {
                double base=.30+offset, tip=.46;
                var a=point(axis,radial,tangent,base,-.035,radius);var b=point(axis,radial,tangent,tip,0,radius);var c=point(axis,radial,tangent,base,.035,radius);
                quads.add(oriented(a,b,c,radial));
            }
        }
        return List.copyOf(quads);
    }
    private static List<Direction> radials(Direction axis) {
        var list=new ArrayList<Direction>(4);for(Direction d:Direction.values())if(d.getAxis()!=axis.getAxis())list.add(d);return list;
    }
    private static DisposalMeshGeometry.Vertex point(Direction axis,Direction radial,Direction tangent,double along,double across,double radius) {
        double x=.5+axis.getStepX()*along+radial.getStepX()*radius+tangent.getStepX()*across;
        double y=.5+axis.getStepY()*along+radial.getStepY()*radius+tangent.getStepY()*across;
        double z=.5+axis.getStepZ()*along+radial.getStepZ()*radius+tangent.getStepZ()*across;
        return vertex(x,y,z,radial);
    }
    private static DisposalMeshGeometry.Quad oriented(DisposalMeshGeometry.Vertex a,DisposalMeshGeometry.Vertex b,DisposalMeshGeometry.Vertex c,Direction n) {
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double dot=(uy*vz-uz*vy)*n.getStepX()+(uz*vx-ux*vz)*n.getStepY()+(ux*vy-uy*vx)*n.getStepZ();
        return dot>=0?new DisposalMeshGeometry.Quad(a,b,c,c,false):new DisposalMeshGeometry.Quad(a,c,b,b,false);
    }

    /** Circumferential surface band around a tube axis, for tagger identity or trunk unit coupling. */
    private static List<DisposalMeshGeometry.Quad> collar(Direction axis,double diameter,boolean glyph) {
        Direction u=radials(axis).get(0);
        Direction v=Direction.fromDelta(axis.getStepY()*u.getStepZ()-axis.getStepZ()*u.getStepY(),axis.getStepZ()*u.getStepX()-axis.getStepX()*u.getStepZ(),axis.getStepX()*u.getStepY()-axis.getStepY()*u.getStepX());
        double radius=diameter*.5/Math.cos(Math.PI/8)+.005;
        double along=glyph?.30:.31, width=.025;var out=new ArrayList<DisposalMeshGeometry.Quad>();
        for(int i=0;i<8;i++) {double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            var p=axisPoint(axis,u,v,along-width,radius,a);var q=axisPoint(axis,u,v,along-width,radius,b);
            var s=axisPoint(axis,u,v,along+width,radius,b);var t=axisPoint(axis,u,v,along+width,radius,a);
            out.add(orientedQuad(p,q,s,t));
        }
        if(glyph) { // Three raised axial bands are a small TAG glyph visible around the pipe circumference.
            for(double alongBar:new double[]{along-.035,along,along+.035}) for(int i=0;i<8;i++) {
                double a=i*Math.PI/4,b=(i+1)*Math.PI/4;out.add(orientedQuad(axisPoint(axis,u,v,alongBar-.008,radius+.003,a),axisPoint(axis,u,v,alongBar-.008,radius+.003,b),axisPoint(axis,u,v,alongBar+.008,radius+.003,b),axisPoint(axis,u,v,alongBar+.008,radius+.003,a)));
            }
        }
        return List.copyOf(out);
    }
    private static DisposalMeshGeometry.Vertex axisPoint(Direction axis,Direction u,Direction v,double along,double radius,double angle) {
        double nx=u.getStepX()*Math.cos(angle)+v.getStepX()*Math.sin(angle),ny=u.getStepY()*Math.cos(angle)+v.getStepY()*Math.sin(angle),nz=u.getStepZ()*Math.cos(angle)+v.getStepZ()*Math.sin(angle);
        return new DisposalMeshGeometry.Vertex(.5+axis.getStepX()*along+radius*nx,.5+axis.getStepY()*along+radius*ny,.5+axis.getStepZ()*along+radius*nz,0,0,nx,ny,nz);
    }
    private static DisposalMeshGeometry.Vertex vertex(double x,double y,double z,Direction normal) { return new DisposalMeshGeometry.Vertex(x,y,z,0,0,normal.getStepX(),normal.getStepY(),normal.getStepZ()); }
    private static DisposalMeshGeometry.Quad orientedQuad(DisposalMeshGeometry.Vertex a,DisposalMeshGeometry.Vertex b,DisposalMeshGeometry.Vertex c,DisposalMeshGeometry.Vertex d) {
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double dot=(uy*vz-uz*vy)*a.nx()+(uz*vx-ux*vz)*a.ny()+(ux*vy-uy*vx)*a.nz();
        return dot>=0?new DisposalMeshGeometry.Quad(a,b,c,d,false):new DisposalMeshGeometry.Quad(a,d,c,b,false);
    }
}
