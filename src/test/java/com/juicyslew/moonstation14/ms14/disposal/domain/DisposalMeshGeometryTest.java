package com.juicyslew.moonstation14.ms14.disposal.domain;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DisposalMeshGeometryTest {
    @Test void allAxisBarrelsHaveUnitTransverseNormalsAndValidUv() {
        for(Direction direction:Direction.values()) {
            var mesh=DisposalMeshGeometry.mesh(Set.of(direction,direction.getOpposite()),14.0/16);
            var barrel=mesh.stream().filter(q->!q.dark()&&Math.abs(q.a().nx()*direction.getStepX()+q.a().ny()*direction.getStepY()+q.a().nz()*direction.getStepZ())<1e-9).toList();
            assertEquals(16,barrel.size(),"two ports x eight barrel facets");
            assertEquals(16,mesh.stream().filter(q->q.dark()).count(),"two ports x eight dark disc facets");
            assertEquals(16,mesh.stream().filter(q->!q.dark()&&Math.abs(q.a().nx()*direction.getStepX()+q.a().ny()*direction.getStepY()+q.a().nz()*direction.getStepZ())>=1e-9).count(),"two ports x eight end-ring facets");
            for(var q:mesh) for(var v:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()}) {
                double n=Math.sqrt(v.nx()*v.nx()+v.ny()*v.ny()+v.nz()*v.nz());assertEquals(1,n,1e-9);
                assertTrue(v.x()>=0&&v.x()<=1&&v.y()>=0&&v.y()<=1&&v.z()>=0&&v.z()<=1);
                assertTrue(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1);
            }
            for(var q:barrel)for(var v:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()})assertEquals(0,v.nx()*direction.getStepX()+v.ny()*direction.getStepY()+v.nz()*direction.getStepZ(),1e-9);
            for(var q:mesh){var a=q.a();var b=q.b();var c=q.c();double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();double cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;double area=Math.sqrt(cx*cx+cy*cy+cz*cz);if(area>1e-10)assertTrue(cx*a.nx()+cy*a.ny()+cz*a.nz()>0,"inward face for "+direction);}
            var dark=mesh.stream().filter(DisposalMeshGeometry.Quad::dark).toList();assertEquals(16,dark.size());
            for(var q:dark){var n=q.a();double axisNormal=switch(direction.getAxis()){case X->n.nx();case Y->n.ny();case Z->n.nz();};assertTrue(Math.abs(axisNormal)>.99);
                double expected=(axisNormal>0?1:0)-Math.signum(axisNormal)*.008;
                for(var v:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()})assertEquals(expected,switch(direction.getAxis()){case X->v.x();case Y->v.y();case Z->v.z();},1e-9);
            }
        }
    }
    @Test void hubIsSharedByNonOppositePhysicalPortsButNotStraightPorts(){
        for(Direction a:Direction.values())for(Direction b:Direction.values())if(a!=b){
            var ports=Set.of(a,b);var mesh=DisposalMeshGeometry.mesh(ports,14.0/16);
            boolean expected=a.getOpposite()!=b;assertEquals(expected,DisposalMeshGeometry.needsHub(ports));
            int vertical=(ports.contains(Direction.UP)?1:0)+(ports.contains(Direction.DOWN)?1:0);
            assertEquals(expected?88-16*vertical+8*ports.size():48,mesh.size());
            if(expected){long caps=mesh.stream().filter(q->!q.dark()&&Math.abs(q.a().ny())>.99&&Math.abs(q.b().ny())>.99&&Math.abs(q.c().ny())>.99&&Math.abs(q.d().ny())>.99&&Math.abs(q.a().y()-q.b().y())<1e-9&&Math.abs(q.a().y()-q.c().y())<1e-9&&Math.abs(q.a().y()-q.d().y())<1e-9&& (Math.abs(q.a().y()-(.5-.5*DisposalMeshGeometry.HUB_HALF_HEIGHT_FACTOR*14.0/16))<1e-9||Math.abs(q.a().y()-(.5+.5*DisposalMeshGeometry.HUB_HALF_HEIGHT_FACTOR*14.0/16))<1e-9)).count();assertEquals(16-8*vertical,caps);
                long roots=0;for(Direction port:ports)roots+=mesh.stream().filter(q->isRoot(q,port)).count();assertEquals(8*ports.size(),roots);
            }
        }
    }
    private static boolean isRoot(DisposalMeshGeometry.Quad q,Direction direction){double expected=.5+Math.signum(direction.getStepX()+direction.getStepY()+direction.getStepZ())*.08;
        for(var v:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()}){double coordinate=switch(direction.getAxis()){case X->v.x();case Y->v.y();case Z->v.z();};if(Math.abs(coordinate-expected)>1e-9||Math.abs(v.nx()*direction.getStepX()+v.ny()*direction.getStepY()+v.nz()*direction.getStepZ()+1)>1e-9)return false;}
        return !q.dark();
    }
    @Test void verticalBranchesWeldHubToBarrelWithoutCoplanarArea(){
        var cases=java.util.List.of(Set.of(Direction.UP,Direction.NORTH),Set.of(Direction.UP,Direction.SOUTH),Set.of(Direction.UP,Direction.DOWN,Direction.EAST),Set.of(Direction.NORTH,Direction.SOUTH,Direction.EAST));
        for(var ports:cases){var mesh=DisposalMeshGeometry.mesh(ports,14.0/16);assertNoCoplanarOverlap(mesh);}
        var up=DisposalMeshGeometry.mesh(Set.of(Direction.UP,Direction.NORTH),14.0/16);
        // The hub's vertical full-radius side ends exactly where the UP tube starts.
        double radius=14.0/32,hubHalf=radius*DisposalMeshGeometry.HUB_HALF_HEIGHT_FACTOR;
        for(var q:up)if(!q.dark()&&insideHub(q,radius,.5-hubHalf,.58)&&Math.abs(q.a().ny())<1e-9&&Math.abs(q.b().ny())<1e-9&&Math.abs(q.c().ny())<1e-9&&Math.abs(q.d().ny())<1e-9){
            double min=Math.min(Math.min(q.a().y(),q.b().y()),Math.min(q.c().y(),q.d().y())),max=Math.max(Math.max(q.a().y(),q.b().y()),Math.max(q.c().y(),q.d().y()));
            assertFalse(min<.58-1e-9&&max>.58+1e-9,"hub facet crosses the UP barrel join plane");
        }
    }
    private static boolean insideHub(DisposalMeshGeometry.Quad q,double r,double y0,double y1){for(var v:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()})if(v.x()<.5-r-1e-9||v.x()>.5+r+1e-9||v.z()<.5-r-1e-9||v.z()>.5+r+1e-9||v.y()<y0-1e-9||v.y()>y1+1e-9)return false;return true;}
    private static void assertNoCoplanarOverlap(java.util.List<DisposalMeshGeometry.Quad> mesh){
        for(int i=0;i<mesh.size();i++)for(int j=i+1;j<mesh.size();j++){
            var a=mesh.get(i);var b=mesh.get(j);double[] na=normal(a),nb=normal(b);if(na==null||nb==null)continue;
            double alignment=na[0]*nb[0]+na[1]*nb[1]+na[2]*nb[2];if(Math.abs(Math.abs(alignment)-1)>1e-8)continue;
            var av=vertices(a);var bv=vertices(b);double plane=na[0]*av[0][0]+na[1]*av[0][1]+na[2]*av[0][2];
            boolean coplanar=true;for(var v:bv)if(Math.abs(plane-(na[0]*v[0]+na[1]*v[1]+na[2]*v[2]))>1e-8)coplanar=false;if(!coplanar)continue;
            int drop=Math.abs(na[0])>Math.abs(na[1])?(Math.abs(na[0])>Math.abs(na[2])?0:2):(Math.abs(na[1])>Math.abs(na[2])?1:2);
            var pa=project(av,drop);var pb=project(bv,drop);assertFalse(overlapArea(pa,pb)>1e-9,"coplanar quads overlap with positive area");
        }
    }
    private static double[][] vertices(DisposalMeshGeometry.Quad q){return new double[][]{{q.a().x(),q.a().y(),q.a().z()},{q.b().x(),q.b().y(),q.b().z()},{q.c().x(),q.c().y(),q.c().z()},{q.d().x(),q.d().y(),q.d().z()}};}
    private static double[] normal(DisposalMeshGeometry.Quad q){var p=vertices(q);double[] a={p[1][0]-p[0][0],p[1][1]-p[0][1],p[1][2]-p[0][2]},b={p[2][0]-p[0][0],p[2][1]-p[0][1],p[2][2]-p[0][2]};double[] n={a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};double l=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);if(l<1e-9)return null;for(int i=0;i<3;i++)n[i]/=l;return n;}
    private static double[][] project(double[][] p,int drop){double[][] r=new double[p.length][2];for(int i=0;i<p.length;i++){if(drop==0){r[i][0]=p[i][1];r[i][1]=p[i][2];}else if(drop==1){r[i][0]=p[i][0];r[i][1]=p[i][2];}else{r[i][0]=p[i][0];r[i][1]=p[i][1];}}return r;}
    private static double overlapArea(double[][] subject,double[][] clip){var polygon=new java.util.ArrayList<double[]>();for(var p:subject)polygon.add(p);double orient=area(clip)>0?1:-1;
        for(int i=0;i<clip.length&&!polygon.isEmpty();i++){double[] a=clip[i],b=clip[(i+1)%clip.length];var input=polygon;polygon=new java.util.ArrayList<>();double[] prev=input.get(input.size()-1);boolean pin=inside(prev,a,b,orient);
            for(double[] cur:input){boolean cin=inside(cur,a,b,orient);if(cin!=pin)polygon.add(intersect(prev,cur,a,b));if(cin)polygon.add(cur);prev=cur;pin=cin;}}
        return polygon.size()<3?0:Math.abs(area(polygon.toArray(double[][]::new)));}
    private static boolean inside(double[] p,double[] a,double[] b,double orient){return orient*((b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0]))>=-1e-10;}
    private static double[] intersect(double[] p,double[] q,double[] a,double[] b){double dx=q[0]-p[0],dy=q[1]-p[1],ex=b[0]-a[0],ey=b[1]-a[1];double den=dx*ey-dy*ex;if(Math.abs(den)<1e-12)return q;double t=((a[0]-p[0])*ey-(a[1]-p[1])*ex)/den;return new double[]{p[0]+t*dx,p[1]+t*dy};}
    private static double area(double[][] p){double a=0;for(int i=0;i<p.length;i++)a+=p[i][0]*p[(i+1)%p.length][1]-p[(i+1)%p.length][0]*p[i][1];return a/2;}
}
