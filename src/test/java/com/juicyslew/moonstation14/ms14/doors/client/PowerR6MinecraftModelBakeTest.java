package com.juicyslew.moonstation14.ms14.doors.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Bake accepted R6 source models through Minecraft's real deserializer and FaceBakery. */
class PowerR6MinecraftModelBakeTest {
    private static final List<String> MODELS=List.of(
            "power_collection/generation_pa_control_box/completed",
            "power_collection/generation_pa_control_box/unlitp",
            "power_collection/generation_pa_control_box/unlitp0",
            "power_collection/generation_pa_control_box/unlitp1",
            "power_collection/generation_pa_control_box/unlitp2",
            "power_collection/generation_pa_control_box/unlitp3",
            "power_collection/generation_pa_control_box/default",
            "power_collection/generation_pa_power_box/completed",
            "power_collection/generation_pa_power_box/default",
            "power_collection/generation_pa_power_box/unlitp",
            "power_collection/generation_pa_power_box/unlitp0",
            "power_collection/generation_pa_power_box/unlitp1",
            "power_collection/generation_pa_power_box/unlitp2",
            "power_collection/generation_pa_power_box/unlitp3",
            "power_collection/generation_pa_fuel_chamber/completed",
            "power_collection/generation_pa_fuel_chamber/default",
            "power_collection/generation_pa_fuel_chamber/unlitp",
            "power_collection/generation_pa_fuel_chamber/unlitp0",
            "power_collection/generation_pa_fuel_chamber/unlitp1",
            "power_collection/generation_pa_fuel_chamber/unlitp2",
            "power_collection/generation_pa_fuel_chamber/unlitp3",
            "power_collection/generation_pa_end_cap/completed",
            "power_collection/generation_pa_end_cap/default",
            "power_collection/generation_pa_emitter_port/default",
            "power_collection/generation_pa_emitter_port/unlitp",
            "power_collection/generation_pa_emitter_port/unlitp0",
            "power_collection/generation_pa_emitter_port/unlitp1",
            "power_collection/generation_pa_emitter_port/unlitp2",
            "power_collection/generation_pa_emitter_port/unlitp3",
            "power_collection/generation_pa_emitter_port/completed",
            "power_collection/generation_pa_emitter_fore/default",
            "power_collection/generation_pa_emitter_fore/unlitp",
            "power_collection/generation_pa_emitter_fore/unlitp0",
            "power_collection/generation_pa_emitter_fore/unlitp1",
            "power_collection/generation_pa_emitter_fore/unlitp2",
            "power_collection/generation_pa_emitter_fore/unlitp3",
            "power_collection/generation_pa_emitter_fore/completed",
            "power_collection/generation_pa_emitter_starboard/default",
            "power_collection/generation_pa_emitter_starboard/unlitp",
            "power_collection/generation_pa_emitter_starboard/unlitp0",
            "power_collection/generation_pa_emitter_starboard/unlitp1",
            "power_collection/generation_pa_emitter_starboard/unlitp2",
            "power_collection/generation_pa_emitter_starboard/unlitp3",
            "power_collection/generation_pa_emitter_starboard/completed",
            "power_collection/generation_singularity_emitter/default",
            "power_collection/generation_singularity_emitter/beam",
            "power_collection/generation_singularity_emitter/underpowered",
            "reactor_bindings/field_generator/base",
            "reactor_bindings/field_generator/default",
            "reactor_bindings/field_generator/p1",
            "reactor_bindings/field_generator/p3",
            "reactor_bindings/field_generator/p6",
            "reactor_bindings/field_generator/a1",
            "reactor_bindings/field_generator/a2",
            "reactor_bindings/field_generator/a3",
            "reactor_bindings/field_generator/active",
            "power_collection/generation_singularity_generator/icon");

    @Test void acceptedMachineStatesDeserializeAndBakeFacesForNativeFacings() throws Exception {
        try (var contents=contents()) {
            var sprite=new FixtureSprite(contents);
            var bakery=new FaceBakery();
            var rotations=List.of(BlockModelRotation.X0_Y0,BlockModelRotation.X0_Y90,
                    BlockModelRotation.X0_Y180,BlockModelRotation.X0_Y270,
                    BlockModelRotation.X90_Y0,BlockModelRotation.X270_Y0);
            for(String name:MODELS) {
                var model=read(name);
                assertFalse(model.getElements().isEmpty(),name);
                for(var rotation:rotations) {
                    int quads=0;
                    for(var element:model.getElements()) {
                      if (name.startsWith("power_collection/generation_pa_"))
                          assertNull(element.rotation,name+" accepted PA source element rotation");
                      for(var face:element.faces.entrySet()) {
                        var quad=bakery.bakeQuad(element.from,element.to,face.getValue(),sprite,face.getKey(),rotation,
                                element.rotation,element.shade);
                        assertEquals(32,quad.getVertices().length,name+" "+rotation+" "+face.getKey());
                        for(int vertex=0;vertex<4;vertex++) {
                            int offset=vertex*8;
                            float x=Float.intBitsToFloat(quad.getVertices()[offset]);
                            float y=Float.intBitsToFloat(quad.getVertices()[offset+1]);
                            float z=Float.intBitsToFloat(quad.getVertices()[offset+2]);
                            assertTrue(x>=-1.5f&&x<=2.5f&&y>=-1.5f&&y<=2.5f&&z>=-1.5f&&z<=2.5f,
                                    name+" model coordinate remains bounded after native bake");
                        }
                        // Verify the baked vertex order agrees with Minecraft's packed
                        // face normal. Vertex-count/bounds checks alone miss reversed
                        // winding, which is culled by the native renderer.
                        var vertices=quad.getVertices();
                        float ax=Float.intBitsToFloat(vertices[8])-Float.intBitsToFloat(vertices[0]);
                        float ay=Float.intBitsToFloat(vertices[9])-Float.intBitsToFloat(vertices[1]);
                        float az=Float.intBitsToFloat(vertices[10])-Float.intBitsToFloat(vertices[2]);
                        float bx=Float.intBitsToFloat(vertices[16])-Float.intBitsToFloat(vertices[8]);
                        float by=Float.intBitsToFloat(vertices[17])-Float.intBitsToFloat(vertices[9]);
                        float bz=Float.intBitsToFloat(vertices[18])-Float.intBitsToFloat(vertices[10]);
                        float cx=ay*bz-az*by, cy=az*bx-ax*bz, cz=ax*by-ay*bx;
                        // An independently packed normal can agree with reversed source
                        // geometry. Compare against the source element's transformed center.
                        float[] center = transformedCenter(element, rotation);
                        float vx=Float.intBitsToFloat(vertices[0]), vy=Float.intBitsToFloat(vertices[1]), vz=Float.intBitsToFloat(vertices[2]);
                        float fx=(vx+Float.intBitsToFloat(vertices[8])+Float.intBitsToFloat(vertices[16])+Float.intBitsToFloat(vertices[24]))/4;
                        float fy=(vy+Float.intBitsToFloat(vertices[9])+Float.intBitsToFloat(vertices[17])+Float.intBitsToFloat(vertices[25]))/4;
                        float fz=(vz+Float.intBitsToFloat(vertices[10])+Float.intBitsToFloat(vertices[18])+Float.intBitsToFloat(vertices[26]))/4;
                        float outward=cx*(fx-center[0])+cy*(fy-center[1])+cz*(fz-center[2]);
                        if (cx*cx+cy*cy+cz*cz > 1.0e-8f)
                            assertTrue(outward>0,name+" "+rotation+" "+face.getKey()+" winding points away from transformed element center");
                        quads++;
                      }
                    }
                    assertTrue(quads>0,name+" baked faces");
                }
            }
        }
    }

    @Test void controlBoxUnlitpBakesActualBlockstateUvLockAndReportsPostBakeAlphaCoverage() throws Exception {
        String modelName="power_collection/generation_pa_control_box/unlitp";
        try (var contents=contents();
             var textureStream=getClass().getResourceAsStream("/assets/moonstation14/textures/block/"+modelName+".png");
             var image=NativeImage.read(java.util.Objects.requireNonNull(textureStream,"control box source PNG"))) {
            var model=read(modelName);
            var sprite=new FixtureSprite(contents);
            var bakery=new FaceBakery();
            for(String role:List.of("pa_control_box","pa_power_box","pa_fuel_chamber","pa_end_cap",
                    "pa_emitter_port","pa_emitter_fore","pa_emitter_starboard")) {
                var roleState=readResourceJson("/assets/moonstation14/blockstates/"+role+".json");
                for(var variant:roleState.getAsJsonObject("variants").entrySet())
                    assertFalse(variant.getValue().getAsJsonObject().get("uvlock").getAsBoolean(),
                            role+" production variant must disable the opaque-atlas UV lock workaround: "+variant.getKey());
            }
            var blockstate=readResourceJson("/assets/moonstation14/blockstates/pa_control_box.json");
            var rotations=new java.util.ArrayList<Facing>();
            for(String direction:List.of("north","east","south","west")) {
                var variant=blockstate.getAsJsonObject("variants").getAsJsonObject("facing="+direction+",visual=unlitp");
                assertEquals("moonstation14:block/reactor_bindings/pa_control_box/unlitp",variant.get("model").getAsString());
                assertEquals((90*(List.of("north","east","south","west").indexOf(direction))+180)%360,variant.get("y").getAsInt());
                rotations.add(new Facing(direction,blockRotation(variant.get("y").getAsInt()),variant.get("uvlock").getAsBoolean()));
            }
            int uvLockedMoves=0;
            for(var facing:rotations) {
                int facingFaces=0, moved=0;
                for(int elementIndex=0;elementIndex<model.getElements().size();elementIndex++) {
                  var element=model.getElements().get(elementIndex);
                  for(var entry:element.faces.entrySet()) {
                    var face=entry.getValue();
                    var production=bakery.bakeQuad(element.from,element.to,face,sprite,entry.getKey(),
                            new UvLockedState(facing.rotation,facing.uvLocked),
                            element.rotation,element.shade);
                    var alternate=bakery.bakeQuad(element.from,element.to,face,sprite,entry.getKey(),
                            new UvLockedState(facing.rotation,!facing.uvLocked),element.rotation,element.shade);
                    boolean faceMoved=false;
                    for(int vertex=0;vertex<4;vertex++) {
                        int offset=vertex*8;
                        faceMoved |= Float.floatToIntBits(Float.intBitsToFloat(production.getVertices()[offset+4]))
                                != Float.floatToIntBits(Float.intBitsToFloat(alternate.getVertices()[offset+4]))
                                || Float.floatToIntBits(Float.intBitsToFloat(production.getVertices()[offset+5]))
                                != Float.floatToIntBits(Float.intBitsToFloat(alternate.getVertices()[offset+5]));
                    }
                    if(faceMoved) {
                        moved++;
                        System.out.println("PA uvlock moved facing="+facing.name+" elementIndex="+elementIndex+" face="+entry.getKey()
                                +" productionUvlock="+facing.uvLocked+" production="+quadUv(production)+" alternate="+quadUv(alternate));
                    }
                    var productionAlpha=sampleBakedQuadAlpha(production,image);
                    var alternateAlpha=sampleBakedQuadAlpha(alternate,image);
                    assertEquals(0,productionAlpha.partial+productionAlpha.transparent,
                            "production baked UV should remain opaque facing="+facing.name+" element="+elementIndex+" face="+entry.getKey());
                    System.out.println("PA baked UV alpha facing="+facing.name+" productionUvlock="+facing.uvLocked+
                            " elementIndex="+elementIndex+" face="+entry.getKey()+" opaque/partial/transparent="+productionAlpha);
                    System.out.println("PA baked UV alpha facing="+facing.name+" alternateUvlock="+(!facing.uvLocked)+
                            " elementIndex="+elementIndex+" face="+entry.getKey()+" opaque/partial/transparent="+alternateAlpha);
                    facingFaces++;
                  }
                }
                assertTrue(facingFaces>0,"native control-box faces baked for "+facing.name);
                uvLockedMoves+=moved;
                System.out.println("PA UVLOCK SUMMARY facing="+facing.name+" blockstateUvlock="+facing.uvLocked+" movedFaces="+moved+
                        "/"+facingFaces+" cumulativeMoved="+uvLockedMoves);
            }
        }
    }

    @Test void paControlScreenFacesAwayAndSideAdaptersSelectOppositeAcceptedArtForEveryFacing() throws Exception {
        var facings=List.of("north","east","south","west");
        var control=read("power_collection/generation_pa_control_box/unlitp");
        var sourceControl=readJson("power_collection/generation_pa_control_box/unlitp");
        int screenIndex=-1;
        for(int i=0;i<sourceControl.getAsJsonArray("elements").size();i++)
            if("pa_screen".equals(sourceControl.getAsJsonArray("elements").get(i).getAsJsonObject().get("name").getAsString())) screenIndex=i;
        assertTrue(screenIndex>=0,"accepted source contains pa_screen");
        var screen=control.getElements().get(screenIndex);
        // The accepted pa_screen is a thin named element at source Z 7.84..7.99;
        // its north (-Z) face is its emissive/display face. The adapter's actual
        // blockstate yaw, not a synthetic orientation, is used below.
        assertEquals(7.84f,screen.from.z,0.01f);
        assertEquals(7.99f,screen.to.z,0.01f);
        var controlState=readResourceJson("/assets/moonstation14/blockstates/pa_control_box.json").getAsJsonObject("variants");
        var roleBindings=Map.of("pa_emitter_port","starboard","pa_emitter_starboard","port","pa_emitter_fore","fore");
        for(int i=0;i<facings.size();i++) {
            String facing=facings.get(i);
            int rootYaw=90*i;
            var variant=controlState.getAsJsonObject("facing="+facing+",visual=unlitp");
            assertEquals((rootYaw+180)%360,variant.get("y").getAsInt(),"control art yaw for "+facing);
            var rotation=blockRotation(variant.get("y").getAsInt());
            var face=screen.faces.get(Direction.NORTH);
            try(var contents=contents()) {
            var quad=new FaceBakery().bakeQuad(screen.from,screen.to,face,new FixtureSprite(contents),Direction.NORTH,
                    rotation,screen.rotation,screen.shade);
            var normal=quad.getDirection();
            Direction operator=Direction.valueOf(facing.toUpperCase(java.util.Locale.ROOT));
            assertEquals(operator.getOpposite(),normal,"screen points away from operator-facing normal for "+facing);

            for(var role:roleBindings.entrySet()) {
                var state=readResourceJson("/assets/moonstation14/blockstates/"+role.getKey()+".json").getAsJsonObject("variants");
                for(String visual:List.of("default","completed","unlitp","unlitp0","unlitp1","unlitp2","unlitp3")) {
                    var emitter=state.getAsJsonObject("facing="+facing+",visual="+visual);
                    assertEquals("moonstation14:block/reactor_bindings/"+role.getKey()+"/"+visual,
                            emitter.get("model").getAsString(),role.getKey()+" binding "+visual);
                    assertEquals(rootYaw,emitter.get("y").getAsInt(),"native facing yaw for "+role.getKey()+" "+facing);
                    assertFalse(emitter.get("uvlock").getAsBoolean());
                }
                var adapter=readJson("reactor_bindings/"+role.getKey()+"/default");
                assertEquals("moonstation14:block/power_collection/generation_pa_emitter_"+role.getValue()+"/default",
                        adapter.get("parent").getAsString(),"presentation-only side-art crossing");
                var art=read("power_collection/generation_pa_emitter_"+role.getValue()+"/default");
                Direction root=Direction.valueOf(facing.toUpperCase(java.util.Locale.ROOT));
                Direction fireDirection=root.getClockWise();
                var productionEmitter=state.getAsJsonObject("facing="+fireDirection.getName()+",visual=default");
                int emitterYaw=productionEmitter.get("y").getAsInt();
                var rotationForEmitter=blockRotation(emitterYaw);
                int quarterTurns=Math.floorMod(i-2,4);
                if(!role.getKey().endsWith("fore")) {
                    var connector=elementBySourceName(art,"power_collection/generation_pa_emitter_"+role.getValue(),"offset_connector");
                    assertTrue(role.getKey().endsWith("port")?connector.from.x>=14f:connector.to.x<=2f,
                            "accepted offset_connector lies on the expected outer X bound: "+role.getKey());
                    var connectorCenter=transformedCenter(connector,rotationForEmitter);
                    var towardFore=dominantHorizontalDirection(connectorCenter);
                    Direction inward=role.getKey().endsWith("port")?Direction.NORTH:Direction.SOUTH;
                    for(int turn=0;turn<quarterTurns;turn++) inward=inward.getClockWise();
                    assertEquals(inward,towardFore,"transformed offset_connector center points toward Fore for "+role.getKey()+" "+facing);
                }
                var muzzle=elementBySourceName(art,"power_collection/generation_pa_emitter_"+role.getValue(),"forward_aperture");
                assertEquals(2.16f,muzzle.from.z,0.01f,"accepted thin muzzle bounds");
                assertEquals(2.29f,muzzle.to.z,0.01f,"accepted thin muzzle bounds");
                var muzzleQuad=new FaceBakery().bakeQuad(muzzle.from,muzzle.to,muzzle.faces.get(Direction.NORTH),
                         new FixtureSprite(contents),Direction.NORTH,rotationForEmitter,muzzle.rotation,muzzle.shade);
                assertEquals(fireDirection,muzzleQuad.getDirection(),"accepted forward_aperture muzzle matches firing direction for "+facing);
            }
            }
        }
    }

    private static net.minecraft.client.renderer.block.model.BlockElement elementBySourceName(
            BlockModel model,String path,String name) throws Exception {
        var json=readJson(path+"/default");
        for(int i=0;i<json.getAsJsonArray("elements").size();i++)
            if(name.equals(json.getAsJsonArray("elements").get(i).getAsJsonObject().get("name").getAsString()))
                return model.getElements().get(i);
        throw new AssertionError("Missing source element "+name+" in "+path);
    }

    private static Direction dominantHorizontalDirection(float[] point) {
        float x=point[0]-.5f,z=point[2]-.5f;
        if(Math.abs(x)>Math.abs(z)) return x>0?Direction.EAST:Direction.WEST;
        return z>0?Direction.SOUTH:Direction.NORTH;
    }

    private static String quadUv(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        var values=new java.util.ArrayList<String>();
        for(int vertex=0;vertex<4;vertex++) {
            int offset=vertex*8;
            values.add(Float.intBitsToFloat(quad.getVertices()[offset+4])+","+
                    Float.intBitsToFloat(quad.getVertices()[offset+5]));
        }
        return values.toString();
    }

    private static AlphaCoverage sampleBakedQuadAlpha(net.minecraft.client.renderer.block.model.BakedQuad quad,NativeImage image) {
        int opaque=0,partial=0,transparent=0;
        var vertices=quad.getVertices();
        for(int iy=1;iy<=9;iy++) for(int ix=1;ix<=9;ix++) {
            float s=ix/10f,t=iy/10f;
            // Bilinear interpolation follows the baked quad's four actual vertex UVs;
            // the fixture sprite spans [0,1], so these are normalized source-frame UVs.
            float u=bilinearUv(vertices,4,s,t),v=bilinearUv(vertices,5,s,t);
            int x=Math.max(0,Math.min(image.getWidth()-1,(int)(u*image.getWidth())));
            int y=Math.max(0,Math.min(image.getHeight()-1,(int)(v*image.getHeight())));
            int alpha=(image.getPixelRGBA(x,y)>>>24)&0xff;
            if(alpha==255) opaque++; else if(alpha==0) transparent++; else partial++;
        }
        return new AlphaCoverage(opaque,partial,transparent);
    }

    private static float bilinearUv(int[] vertices,int component,float s,float t) {
        float a=Float.intBitsToFloat(vertices[component]);
        float b=Float.intBitsToFloat(vertices[8+component]);
        float c=Float.intBitsToFloat(vertices[16+component]);
        float d=Float.intBitsToFloat(vertices[24+component]);
        return (1-t)*((1-s)*a+s*b)+t*((1-s)*d+s*c);
    }

    private static BlockModelRotation blockRotation(int y) {
        return switch(y) {
            case 0 -> BlockModelRotation.X0_Y0;
            case 90 -> BlockModelRotation.X0_Y90;
            case 180 -> BlockModelRotation.X0_Y180;
            case 270 -> BlockModelRotation.X0_Y270;
            default -> throw new IllegalArgumentException("Unexpected PA blockstate Y rotation "+y);
        };
    }
    private record Facing(String name,BlockModelRotation rotation,boolean uvLocked) {}
    private record AlphaCoverage(int opaque,int partial,int transparent) {}
    private record UvLockedState(BlockModelRotation delegate,boolean uvLocked) implements ModelState {
        @Override public com.mojang.math.Transformation getRotation() { return delegate.getRotation(); }
        @Override public boolean isUvLocked() { return uvLocked; }
    }

    private static float[] transformedCenter(net.minecraft.client.renderer.block.model.BlockElement element,
                                             BlockModelRotation rotation) {
        var point=new org.joml.Vector3f((element.from.x+element.to.x)/32f,
                (element.from.y+element.to.y)/32f,(element.from.z+element.to.z)/32f);
        // FaceBakery applies BlockModelRotation's pure rotation around the block center.
        point.sub(.5f, .5f, .5f);
        rotation.getRotation().getMatrix().transformPosition(point);
        point.add(.5f, .5f, .5f);
        return new float[]{point.x,point.y,point.z};
    }

    @Test void fieldGeneratorProductionMultipartMapsEveryStateToExactAcceptedNineCuboidComposition() throws Exception {
        var source=readJson("power_collection/generation_singularity_containment/default");
        var sourceByName=new java.util.HashMap<String,com.google.gson.JsonObject>();
        for(var element:source.getAsJsonArray("elements")) {
            var object=element.getAsJsonObject();
            sourceByName.put(object.get("name").getAsString(),object);
        }
        var blockstate=PowerR6PaFrameAlphaTest.json("/assets/moonstation14/blockstates/field_generator.json");
        var powerMaterials=List.of("default","p1","p3","p6");
        var fieldMaterials=List.of("default","a1","a2","a3");
        var expectedNames=Set.of("field_base","power_lens","field_column","column_rear_rib","field_lobe_0",
                "field_lobe_cap_0","field_lobe_1","field_lobe_cap_1","activation_lens");
        for(boolean active:List.of(false,true)) for(int power=0;power<=3;power++) for(int field=0;field<=3;field++) {
            var layers=PowerR6PaFrameAlphaTest.resolvedMultipartModels(blockstate,Map.of(
                    "power",Integer.toString(power),"field",Integer.toString(field),"active",Boolean.toString(active)));
            var composed=new java.util.HashMap<String,com.google.gson.JsonObject>();
            var materials=new java.util.ArrayList<String>();
            for(var model:layers) {
                materials.add(model.getAsJsonObject("textures").get("atlas").getAsString());
                for(var element:model.getAsJsonArray("elements")) {
                    var object=element.getAsJsonObject();
                    assertNull(composed.put(object.get("name").getAsString(),object),"duplicate element "+object.get("name"));
                }
            }
            assertEquals(4,layers.size(),"actual multipart layer count; active="+active+" power="+power+" field="+field);
            assertEquals(9,composed.size(),"actual authored cuboid count; active="+active+" power="+power+" field="+field);
            assertEquals(expectedNames,composed.keySet(),"actual unique element composition");
            assertEquals(List.of("moonstation14:block/reactor_r8/generation_singularity_containment/default",
                            "moonstation14:block/reactor_r8/generation_singularity_containment/"+powerMaterials.get(power),
                            "moonstation14:block/reactor_r8/generation_singularity_containment/"+fieldMaterials.get(field),
                            "moonstation14:block/reactor_r8/generation_singularity_containment/"+(active?"on":"default")),
                    materials,"actual blockstate multipart material selection");
            for(var entry:composed.entrySet()) {
                var accepted=sourceByName.get(entry.getKey());
                assertNotNull(accepted,"unexpected physical element "+entry.getKey());
                var actual=entry.getValue().deepCopy();
                var expected=accepted.deepCopy();
                if(entry.getKey().equals("activation_lens")) {
                    assertEquals(Boolean.TRUE,expected.get("shade").getAsBoolean(),"authored source activation-lens shade");
                    assertEquals(Boolean.FALSE,actual.get("shade").getAsBoolean(),
                            "retained inactive/active activation-lens shading adapter; all other authored elements retain source shade");
                    expected.remove("shade"); actual.remove("shade");
                }
                assertEquals(expected,actual,entry.getKey()+" source geometry and UV/face data");
            }
        }
    }

    @Test void fieldGeneratorAcceptedAtlasPixelDifferencesMatchTheLayerBindings() throws Exception {
        var atlas = new java.util.HashMap<String, java.awt.image.BufferedImage>();
        for (String name : List.of("default", "a1", "a2", "a3", "p1", "p3", "p6", "on")) {
            try (var stream = getClass().getResourceAsStream("/assets/moonstation14/textures/block/power_collection/generation_singularity_containment/" + name + ".png")) {
                assertNotNull(stream, name);
                atlas.put(name, ImageIO.read(stream));
            }
        }
        var baseline = atlas.get("default");
        for (var image : atlas.values()) assertEquals(baseline.getWidth(), image.getWidth());
        // R6 originals remain unchanged; this legacy check records their source provenance only.
        for (String field : List.of("a1", "a2", "a3"))
            assertEquals(0, changedPixels(baseline, atlas.get(field)), field + " immutable R6 source atlas");
        for (String power : List.of("p1", "p3", "p6")) {
            var image = atlas.get(power);
            assertTrue(changedPixels(baseline, image, 123, 0, 125, 14) > 0, power + " power lens must differ");
            assertEquals(changedPixels(baseline, image), changedPixels(baseline, image, 123, 0, 125, 14),
                    power + " pixels outside the power-lens UV region");
        }
        var connected = atlas.get("on");
        assertTrue(changedPixels(baseline, connected, 12, 37, 20, 49) > 0, "connected activation lens must differ");
        assertEquals(changedPixels(baseline, connected), changedPixels(baseline, connected, 12, 37, 20, 49),
                "connected pixels outside the activation-lens UV region");
    }

    @Test void r8FieldGeneratorBindingsUseApprovedAtlasAndVerticalTierPixelProgression() throws Exception {
        var names=List.of("default","a1","a2","a3","p1","p3","p6","on");
        var images=new java.util.HashMap<String,java.awt.image.BufferedImage>();
        for(String name:names) try(var stream=getClass().getResourceAsStream("/assets/moonstation14/textures/block/reactor_r8/generation_singularity_containment/"+name+".png")) {
            assertNotNull(stream,name); images.put(name,ImageIO.read(stream));
        }
        var base=readJson("reactor_bindings/field_generator/base");
        var itemDefault=readJson("reactor_bindings/field_generator/default");
        assertEquals("moonstation14:block/reactor_r8/generation_singularity_containment/default",base.getAsJsonObject("textures").get("atlas").getAsString());
        assertEquals(List.of("field_base"),elementNames(base));
        assertEquals("moonstation14:block/reactor_r8/generation_singularity_containment/default",itemDefault.getAsJsonObject("textures").get("atlas").getAsString());
        assertEquals(9,elementNames(itemDefault).size());
        var sourceDefault=readJson("power_collection/generation_singularity_containment/default");
        var sourceElements=new java.util.HashMap<String,com.google.gson.JsonObject>();
        for(var element:sourceDefault.getAsJsonArray("elements")) sourceElements.put(element.getAsJsonObject().get("name").getAsString(),element.getAsJsonObject());
        for(var element:itemDefault.getAsJsonArray("elements")) assertEquals(sourceElements.get(element.getAsJsonObject().get("name").getAsString()),element.getAsJsonObject(),"R8 item icon preserves source geometry and metadata");
        var fieldNames=List.of("field_column","column_rear_rib","field_lobe_0","field_lobe_cap_0","field_lobe_1","field_lobe_cap_1");
        for(String layer:List.of("field0_lobes","a1","a2","a3")) {
            var model=readJson("reactor_bindings/field_generator/"+layer);
            assertEquals(fieldNames,elementNames(model));
            String expected=layer.equals("field0_lobes")?"default":layer;
            assertEquals("moonstation14:block/reactor_r8/generation_singularity_containment/"+expected,model.getAsJsonObject("textures").get("atlas").getAsString());
        }
        var column=readJson("reactor_bindings/field_generator/field0_lobes").getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals("field_column",column.get("name").getAsString());
        // column + rear rib faces point at the authored vertical cyan bar on all four sides.
        // Pixel coordinates derive from authored 0..16 model UVs at 8 pixels/unit
        // on the approved 128x128 R8 atlas.
        int[][] uv={{45,1,55,38},{64,1,72,36}};
        int[] changed=new int[3];
        for(int i=0;i<3;i++) {
            var image=images.get("a"+(i+1));
            changed[i]=changedPixels(images.get("default"),image,uv[0][0],uv[0][1],uv[0][2],uv[0][3])
                    +changedPixels(images.get("default"),image,uv[1][0],uv[1][1],uv[1][2],uv[1][3]);
            assertEquals(changed[i],changedPixels(images.get("default"),image),"field state edits only column/rear-rib UV surfaces");
            assertTrue(cyanPixels(image,uv[0])+cyanPixels(image,uv[1])>0,"visible cyan fill in field state a"+(i+1));
        }
        assertTrue(changed[0]>0 && changed[1]>changed[0] && changed[2]>changed[1],"R8 vertical bar fill increases by state: "+java.util.Arrays.toString(changed));
        for(String name:List.of("p1","p3","p6")) assertTrue(changedPixels(images.get("default"),images.get(name),123,0,125,14)>0,name+" independent power lens");
        assertTrue(changedPixels(images.get("default"),images.get("on"),12,37,20,49)>0,"independent connected lens");
    }

    private static List<String> elementNames(com.google.gson.JsonObject model) {
        var result=new java.util.ArrayList<String>();
        for(var element:model.getAsJsonArray("elements")) result.add(element.getAsJsonObject().get("name").getAsString());
        return result;
    }

    private static int changedPixels(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b) {
        return changedPixels(a, b, 0, 0, a.getWidth(), a.getHeight());
    }
    private static int changedPixels(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b,
                                     int minX, int minY, int maxX, int maxY) {
        int changed = 0;
        for (int y = minY; y < maxY; y++) for (int x = minX; x < maxX; x++)
            if (a.getRGB(x, y) != b.getRGB(x, y)) changed++;
        return changed;
    }
    private static int cyanPixels(java.awt.image.BufferedImage image,int[] bounds) {
        int count=0;
        for(int y=bounds[1];y<bounds[3];y++) for(int x=bounds[0];x<bounds[2];x++) {
            int rgb=image.getRGB(x,y),r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
            if(g>80&&b>80&&g>r*1.25&&b>r*1.25) count++;
        }
        return count;
    }

    private static BlockModel read(String path) throws Exception {
        String resource="/assets/moonstation14/models/block/"+path+".json";
        try(var stream=PowerR6MinecraftModelBakeTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream,resource);
            return BlockModel.fromStream(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
    }
    private static com.google.gson.JsonObject readJson(String path) throws Exception {
        try(var stream=PowerR6MinecraftModelBakeTest.class.getResourceAsStream("/assets/moonstation14/models/block/"+path+".json")) {
            assertNotNull(stream,path);
            return com.google.gson.JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static com.google.gson.JsonObject readResourceJson(String path) throws Exception {
        try(var stream=PowerR6MinecraftModelBakeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream,path);
            return com.google.gson.JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static SpriteContents contents() { return new SpriteContents(ResourceLocation.parse("test:power"),
            new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY); }
    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(SpriteContents contents) { super(ResourceLocation.parse("test:atlas"),contents,16,16,0,0); }
    }
}
