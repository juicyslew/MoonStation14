package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/** Shared CPU-only test preview rasterizer for production mesh fixtures. */
final class CpuMeshRasterizer {
    private CpuMeshRasterizer() {}
    static BufferedImage rasterize(List<ClosedEffectMesh.Vertex> mesh, BufferedImage texture, double yaw, double pitch, int tint) {
        return rasterize(mesh,texture,yaw,pitch,tint,192);
    }
    static BufferedImage rasterize(List<ClosedEffectMesh.Vertex> mesh, BufferedImage texture, double yaw, double pitch, int tint, int size) {
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        double[] zbuf=new double[size*size]; Arrays.fill(zbuf,Double.NEGATIVE_INFINITY);
        double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        double[][] p=new double[mesh.size()][3]; double minX=1e99,maxX=-1e99,minY=1e99,maxY=-1e99;
        for(int i=0;i<mesh.size();i++){Vec3 v=mesh.get(i).position();double x=cy*v.x-sy*v.z,z=sy*v.x+cy*v.z,y=cp*v.y-sp*z;z=sp*v.y+cp*z;p[i]=new double[]{x,y,z};minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        double scale=(size-12)/Math.max(Math.max(maxX-minX,maxY-minY),1e-6),cx=(minX+maxX)/2,cyy=(minY+maxY)/2;
        for(int i=0;i+2<mesh.size();i+=3){double[][] a={p[i],p[i+1],p[i+2]};ClosedEffectMesh.Vertex[] v={mesh.get(i),mesh.get(i+1),mesh.get(i+2)};double[] sx=new double[3],syy=new double[3];for(int j=0;j<3;j++){sx[j]=size/2+(a[j][0]-cx)*scale;syy[j]=size/2-(a[j][1]-cyy)*scale;}double area=(sx[1]-sx[0])*(syy[2]-syy[0])-(syy[1]-syy[0])*(sx[2]-sx[0]);if(Math.abs(area)<1e-8)continue;
            int x0=Math.max(0,(int)Math.floor(Math.min(sx[0],Math.min(sx[1],sx[2])))),x1=Math.min(size-1,(int)Math.ceil(Math.max(sx[0],Math.max(sx[1],sx[2]))));int y0=Math.max(0,(int)Math.floor(Math.min(syy[0],Math.min(syy[1],syy[2])))),y1=Math.min(size-1,(int)Math.ceil(Math.max(syy[0],Math.max(syy[1],syy[2]))));
             for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){double px=x+.5,py=y+.5,w0=((sx[1]-px)*(syy[2]-py)-(syy[1]-py)*(sx[2]-px))/area,w1=((sx[2]-px)*(syy[0]-py)-(syy[2]-py)*(sx[0]-px))/area,w2=1-w0-w1;if(w0< -1e-7||w1< -1e-7||w2< -1e-7)continue;double z=w0*a[0][2]+w1*a[1][2]+w2*a[2][2];int pos=y*size+x;if(z<=zbuf[pos])continue;double u=w0*v[0].u()+w1*v[1].u()+w2*v[2].u(),vv=w0*v[0].v()+w1*v[1].v()+w2*v[2].v();int tx=Math.max(0,Math.min(texture.getWidth()-1,(int)(u*texture.getWidth()))),ty=Math.max(0,Math.min(texture.getHeight()-1,(int)(vv*texture.getHeight()))),sample=texture.getRGB(tx,ty);if(((sample>>>24)&255)<ClosedEffectMesh.NATIVE_ALPHA_CUTOFF)continue;int color=ClosedEffectMeshPreviewTest.tintPixel(sample,tint);if((color>>>24)==0)continue;zbuf[pos]=z;out.setRGB(x,y,color);}}
        return out;
    }
}
