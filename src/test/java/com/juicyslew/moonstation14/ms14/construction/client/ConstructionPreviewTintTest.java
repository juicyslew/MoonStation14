package com.juicyslew.moonstation14.ms14.construction.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionPreviewTintTest {
    @Test void productionBulkQuadPathAppliesAlphaToEveryActualVertex() {
        var delegate=new RecordingVertexConsumer();
        VertexConsumer wrapped=ConstructionPreviewTint.apply(delegate,ConstructionPreviewTint.Kind.READY);
        int[] data=new int[32];
        for(int i=0;i<4;i++) { int base=i*8;data[base]=Float.floatToIntBits(i&1);data[base+1]=Float.floatToIntBits((i>>1)&1);data[base+2]=0;data[base+3]=0xFFFFFFFF; }
        var quad=new BakedQuad(data,-1,Direction.UP,null,true);
        var tint=ConstructionPreviewTint.rgba(ConstructionPreviewTint.Kind.READY);
        wrapped.putBulkData(new PoseStack().last(),quad,tint.red(),tint.green(),tint.blue(),1f,0xF000F0,0);
        assertEquals(4,delegate.colors.size());
        assertTrue(delegate.colors.stream().allMatch(c->c[3]==89&&c[1]>c[0]&&c[1]>c[2]),"bulk emission must retain both tint and alpha through fluent vertex calls");
        wrapped.addVertex(0,0,0).setColor(30,200,60,255);
        assertArrayEquals(new int[]{30,200,60,89},delegate.colors.getLast());
        assertEquals(ConstructionPreviewTint.rgba(ConstructionPreviewTint.Kind.BLOCKED).alpha(),.35f);
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private final ArrayList<int[]> colors=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){colors.add(new int[]{r,g,b,a});return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }

    @Test void canonicalControlledBodyViewDoesNotDependOnCameraEntityIdentity() {
        assertTrue(ConstructionPreviewView.allowed(true,false,true,true,4));
        assertFalse(ConstructionPreviewView.allowed(true,false,true,true,0));
        assertFalse(ConstructionPreviewView.allowed(true,true,true,true,4));
        assertFalse(ConstructionPreviewView.allowed(true,false,true,false,4));
    }
}
