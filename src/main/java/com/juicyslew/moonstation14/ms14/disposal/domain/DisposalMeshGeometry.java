package com.juicyslew.moonstation14.ms14.disposal.domain;

import net.minecraft.core.Direction;
import java.util.*;

/** Procedural octagonal tube mesh shared by world rendering and construction previews. */
public final class DisposalMeshGeometry {
    private DisposalMeshGeometry() { }
    public static final double HUB_HALF_HEIGHT_FACTOR=.70;
    public static final double HUB_BEVEL_INSET_FACTOR=.72;
    public static final double HUB_END_RADIUS_FACTOR=.68;
    public static final double PORT_START=.08;
    public record Vertex(double x,double y,double z,double u,double v,double nx,double ny,double nz) { }
    public record Quad(Vertex a,Vertex b,Vertex c,Vertex d,boolean dark) { }
    public static boolean needsHub(Set<Direction> ports){
        if(ports.size()!=2)return true;
        var it=ports.iterator();Direction a=it.next(),b=it.next();return a.getOpposite()!=b;
    }
    public static List<Quad> mesh(Set<Direction> ports,double diameter) {
        double r=diameter/2;boolean hub=needsHub(ports);var out=new ArrayList<Quad>();if(hub)beveledHub(out,r,ports);
        for(Direction d:ports)cylinder(out,d,r,hub?PORT_START:0,hub);
        return List.copyOf(out);
    }
    /** Closed beveled octagonal barrel-like junction body. Flat ends are hidden by connected arms. */
    private static void beveledHub(List<Quad> out,double r,Set<Direction> ports){
        double h=r*HUB_HALF_HEIGHT_FACTOR;double[] ys={.5-h,.5-h*HUB_BEVEL_INSET_FACTOR,.5+h*HUB_BEVEL_INSET_FACTOR,.5+h};double[] rs={r*HUB_END_RADIUS_FACTOR,r,r,r*HUB_END_RADIUS_FACTOR};
        boolean down=ports.contains(Direction.DOWN),up=ports.contains(Direction.UP);
        if(down){ys[0]=.5-.08;rs[0]=r;ys[1]=ys[0];rs[1]=r;}
        if(up){ys[3]=.5+.08;rs[3]=r;ys[2]=ys[3];rs[2]=r;}
        for(int band=0;band<ys.length-1;band++)if(ys[band+1]-ys[band]>1e-9)for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            Vertex p=radial(a,ys[band],rs[band],0),q=radial(b,ys[band],rs[band],1),s=radial(b,ys[band+1],rs[band+1],1),t=radial(a,ys[band+1],rs[band+1],0);
            out.add(outward(p,q,s,t,false));
        }
        // Polygonal end caps. These are concealed by port barrels at junctions; the cap closes any unused axis.
        for(int end=0;end<2;end++){if(end==0&&down||end==1&&up)continue;double y=ys[end==0?0:3], rr=rs[end==0?0:3], ny=end==0?-1:1;
            for(int i=0;i<8;i++){double a=i*Math.PI/4,b=(i+1)*Math.PI/4;Vertex center=v(.5,y,.5,0,0,0,ny,0),p=capVertex(a,y,rr,ny,0),q=capVertex(b,y,rr,ny,1);out.add(outward(center,p,q,q,false));}
        }
    }
    private static Vertex capVertex(double a,double y,double r,double ny,double u){return v(.5+r*Math.cos(a),y,.5+r*Math.sin(a),u,0,0,ny,0);}
    private static Vertex radial(double a,double y,double r,double uv){double nx=Math.cos(a),nz=Math.sin(a);return v(.5+r*nx,y,.5+r*nz,uv,0,nx,0,nz);}
    private static void cylinder(List<Quad> out,Direction d,double r,double start,boolean closeRoot){
        Direction.Axis axis=d.getAxis();double sign=d.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:-1;double end=sign>0?1:0,begin=.5+sign*start;
        double[] normals=new double[3];
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;double[] p=point(axis,begin,r,a),q=point(axis,begin,r,b),s=point(axis,end,r,b),t=point(axis,end,r,a);
            double ca=Math.cos((a+b)/2),sa=Math.sin((a+b)/2);double[] n=switch(axis){case X->new double[]{0,sa,ca};case Y->new double[]{ca,0,sa};case Z->new double[]{ca,sa,0};};
            out.add(outward(v(p[0],p[1],p[2],0,0,n[0],n[1],n[2]),v(q[0],q[1],q[2],0,1,n[0],n[1],n[2]),v(s[0],s[1],s[2],.5,1,n[0],n[1],n[2]),v(t[0],t[1],t[2],.5,0,n[0],n[1],n[2]),false));
        }
        if(closeRoot){double[] normal={-d.getStepX(),-d.getStepY(),-d.getStepZ()},center=point(axis,begin,0,0);
            for(int i=0;i<8;i++){double a=i*Math.PI/4,b=(i+1)*Math.PI/4;double[] pa=point(axis,begin,r,a),pb=point(axis,begin,r,b);Vertex c=v(center[0],center[1],center[2],.25,.5,normal[0],normal[1],normal[2]);out.add(outward(c,vertex(pa,a,normal),vertex(pb,b,normal),vertex(pb,b,normal),false));}
        }
        // A small annular bevel and a flat dark disc recessed 1/128 block; all vertices lie on mouth plane.
        double recess=.008,inner=r*.72,plane=end-sign*recess;double[] center=point(axis,plane,0,0);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;double[] outerA=point(axis,end,r,a),outerB=point(axis,end,r,b),innerA=point(axis,plane,inner,a),innerB=point(axis,plane,inner,b);
            double[] norm={d.getStepX(),d.getStepY(),d.getStepZ()};
            out.add(outward(vertex(outerA,a,norm),vertex(outerB,b,norm),vertex(innerB,b,norm),vertex(innerA,a,norm),false));
            Vertex c=v(center[0],center[1],center[2],0,.5,norm[0],norm[1],norm[2]);
            out.add(outward(c,vertex(innerA,a,norm),vertex(innerB,b,norm),vertex(innerB,b,norm),true));
        }
    }
    private static Vertex vertex(double[] p,double angle,double[] n){return v(p[0],p[1],p[2],(angle/(2*Math.PI)),0,n[0],n[1],n[2]);}
    private static double[] point(Direction.Axis axis,double along,double r,double a){double u=r*Math.cos(a),v=r*Math.sin(a);return switch(axis){case X->new double[]{along,.5+v,.5+u};case Y->new double[]{.5+u,along,.5+v};case Z->new double[]{.5+u,.5+v,along};};}
    private static Quad outward(Vertex a,Vertex b,Vertex c,Vertex d,boolean dark){double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;double cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;double dot=cx*a.nx+cy*a.ny+cz*a.nz;if(dot<0)return new Quad(a,d,c,b,dark);return new Quad(a,b,c,d,dark);}
    private static Vertex v(double x,double y,double z,double u,double vv,double nx,double ny,double nz){double length=Math.sqrt(nx*nx+ny*ny+nz*nz);return new Vertex(x,y,z,u,vv,length==0?0:nx/length,length==0?0:ny/length,length==0?0:nz/length);}
}

