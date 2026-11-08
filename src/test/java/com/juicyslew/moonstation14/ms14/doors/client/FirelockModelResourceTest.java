package com.juicyslew.moonstation14.ms14.doors.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FirelockModelResourceTest {
    private static final String ROOT="/assets/moonstation14/models/block/firelock/standard/";
    private static final List<String> COMPONENTS=List.of("upper_leaf","lower_leaf","indicator_dark",
            "indicator_pressure_lit","indicator_pressure_phase0","indicator_pressure_phase1",
            "indicator_temperature_lit","indicator_temperature_phase0","indicator_temperature_phase1",
            "indicator_both_lit","indicator_both_p0_t0","indicator_both_p0_t1","indicator_both_p1_t0","indicator_both_p1_t1");

    @Test void all52ImportedDestinationsMatchFrozenHashesAndExist() throws Exception {
        try(var stream=getClass().getResourceAsStream(ROOT+"provenance/manifest.json")) {
            assertNotNull(stream);
            var files=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("files");
            assertEquals(52,files.size());
            for(var item:files) {
                var entry=item.getAsJsonObject();
                String destination=entry.get("destination").getAsString();
                String resource="/"+destination.substring("src/main/resources/".length());
                try(var asset=getClass().getResourceAsStream(resource)) {
                    assertNotNull(asset,resource);
                    String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.readAllBytes()));
                    assertEquals(entry.get("sha256").getAsString(),actual,resource);
                }
            }
        }
    }

    @Test void approvedRigHasFourteenBodyCuboidsAndOneFourHousingIndicatorChoice() throws Exception {
        try(var sourceContents=contents("test:firelock_source");var targetContents=contents("test:firelock_target")) {
            TextureAtlasSprite source=new FixtureSprite(id("source"),sourceContents,64,64,0,0);
            TextureAtlasSprite target=new FixtureSprite(id("target"),targetContents,64,64,32,16);
            FaceBakery bakery=new FaceBakery();
            int bodyCuboids=0;
            for(String component:COMPONENTS) {
                BlockModel model=read("components/"+component+".json");
                assertFalse(model.getElements().isEmpty(),component);
                if(component.endsWith("leaf")) bodyCuboids+=model.getElements().size();
                for(var element:model.getElements()) {
                    float x0=element.from.x/16f,x1=element.to.x/16f,y0=element.from.y/16f,y1=element.to.y/16f;
                    assertTrue(x0>=-.25/16-1e-6&&x1<=16.25/16+1e-6,component+" X bounds");
                    assertTrue(y0>=-1e-6&&y1<=2+1e-6,component+" Y bounds");
                    for(var face:element.faces.entrySet()) {
                        var baked=bakery.bakeQuad(element.from,element.to,face.getValue(),source,face.getKey(),
                                BlockModelRotation.X0_Y0,element.rotation,element.shade);
                        int[] raw=baked.getVertices();
                        var vertices=new java.util.ArrayList<AirlockQuadClipper.Vertex>(4);
                        for(int i=0;i<4;i++) {
                            int off=i*8; float u=Float.intBitsToFloat(raw[off+4]),v=Float.intBitsToFloat(raw[off+5]);
                            vertices.add(new AirlockQuadClipper.Vertex(Float.intBitsToFloat(raw[off]),Float.intBitsToFloat(raw[off+1]),
                                    Float.intBitsToFloat(raw[off+2]),source.getUOffset(u),source.getVOffset(v)));
                        }
                        for(var clipped:AirlockQuadClipper.clip(vertices,-.25f/16,16.25f/16,0,2,0,1)) {
                            assertTrue(clipped.x()>=-.25/16-1e-6&&clipped.x()<=16.25/16+1e-6);
                            assertTrue(clipped.y()>=-1e-6&&clipped.y()<=2+1e-6);
                            assertTrue(clipped.z()>=-1e-6&&clipped.z()<=1+1e-6);
                            assertEquals(target.getU(clipped.u()),AirlockBlockEntityRenderer.remapAtlasCoordinate(clipped.u(),target.getU0(),target.getU1()),1e-6);
                            assertEquals(target.getV(clipped.v()),AirlockBlockEntityRenderer.remapAtlasCoordinate(clipped.v(),target.getV0(),target.getV1()),1e-6);
                        }
                    }
                }
            }
            assertEquals(14,bodyCuboids,"R4 body has fourteen cuboids, with four separate indicator housings");
            assertEquals(4,read("components/indicator_dark.json").getElements().size(),"each selector is a single four-housing component");
            for(String choice:List.of("indicator_dark","indicator_pressure_lit","indicator_temperature_lit","indicator_both_lit",
                    "indicator_pressure_phase0","indicator_pressure_phase1","indicator_temperature_phase0","indicator_temperature_phase1",
                    "indicator_both_p0_t0","indicator_both_p0_t1","indicator_both_p1_t0","indicator_both_p1_t1"))
                assertEquals(4,read("components/"+choice+".json").getElements().size(),choice);
        }
        var registered=FirelockRenderer.additionalModels().stream().map(Object::toString).toList();
        assertTrue(registered.stream().anyMatch(id->id.contains("states/closed_w0_f0")));
        assertTrue(registered.stream().anyMatch(id->id.contains("states/open_w3_f1")));
        assertTrue(registered.stream().anyMatch(id->id.contains("components/indicator_both_p1_t1")));
    }

    private static BlockModel read(String path) throws Exception {
        try(var stream=FirelockModelResourceTest.class.getResourceAsStream(ROOT+path)) {
            assertNotNull(stream,path); return BlockModel.fromStream(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
    }
    private static SpriteContents contents(String path) { return new SpriteContents(id(path),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY); }
    private static ResourceLocation id(String path) { return ResourceLocation.parse(path); }
    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(ResourceLocation atlas,SpriteContents contents,int width,int height,int x,int y) { super(atlas,contents,width,height,x,y); }
    }
}
