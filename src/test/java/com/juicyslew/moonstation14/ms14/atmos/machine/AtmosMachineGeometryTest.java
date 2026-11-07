package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineGeometryTest {
    private static final Map<String, AtmosMachineBlock> BLOCKS = Map.ofEntries(
            Map.entry("teg_center", ModBlocks.TEG_CENTER.get()), Map.entry("teg_circulator", ModBlocks.TEG_CIRCULATOR.get()),
            Map.entry("gas_pressure_pump", ModBlocks.GAS_PRESSURE_PUMP.get()), Map.entry("gas_volume_pump", ModBlocks.GAS_VOLUME_PUMP.get()),
            Map.entry("gas_valve", ModBlocks.GAS_VALVE.get()), Map.entry("gas_mixer", ModBlocks.GAS_MIXER.get()),
            Map.entry("gas_filter", ModBlocks.GAS_FILTER.get()), Map.entry("gas_thermo_heater", ModBlocks.GAS_THERMO_HEATER.get()),
            Map.entry("gas_thermo_freezer", ModBlocks.GAS_THERMO_FREEZER.get()), Map.entry("passive_heat_exchanger", ModBlocks.PASSIVE_HEAT_EXCHANGER.get()),
            Map.entry("gas_pipe_manifold", ModBlocks.GAS_PIPE_MANIFOLD.get()));

    @Test void everyRegisteredBlockstateBindingResolvesThroughBothLiveShapeHooks() {
        assertEquals(11, BLOCKS.size());
        for (var entry : BLOCKS.entrySet()) {
            var source=resource("blockstates/"+entry.getKey()+".json");
            var variants=com.google.gson.JsonParser.parseString(source).getAsJsonObject();
            java.util.Set<String> modelBindings=new java.util.HashSet<>();
            if(variants.has("variants")) for(var binding:variants.getAsJsonObject("variants").entrySet()) {
                String[] properties=binding.getKey().split(",");
                var values=new java.util.HashMap<String,String>();
                for(String property:properties){String[] pair=property.split("=");values.put(pair[0],pair[1]);}
                Direction facing=Direction.byName(values.get("facing")); int display=Integer.parseInt(values.get("display")),layer=Integer.parseInt(values.get("pipe_layer"));
                modelBindings.add(binding.getValue().getAsJsonObject().get("model").getAsString());
                BlockState s=entry.getValue().defaultBlockState().setValue(AtmosMachineBlock.FACING,facing)
                        .setValue(AtmosMachineBlock.DISPLAY,display).setValue(AtmosMachineBlock.PIPE_LAYER,layer);
                var outline=entry.getValue().getShape(s,null,net.minecraft.core.BlockPos.ZERO,CollisionContext.empty());
                var collision=entry.getValue().getCollisionShape(s,null,net.minecraft.core.BlockPos.ZERO,CollisionContext.empty());
                assertFalse(outline.isEmpty(),entry.getKey()+" "+binding.getKey());
                assertEquals(outline.bounds(),collision.bounds(),entry.getKey()+" outline/collision "+binding.getKey());
            }
            else for(var part:variants.getAsJsonArray("multipart")) {
                var object=part.getAsJsonObject(); if(!object.has("when"))continue;
                String facingName=object.getAsJsonObject("when").get("facing").getAsString();
                modelBindings.add(object.getAsJsonObject("apply").get("model").getAsString());
                BlockState s=entry.getValue().defaultBlockState().setValue(AtmosMachineBlock.FACING,Direction.byName(facingName));
                assertFalse(entry.getValue().getShape(s,null,net.minecraft.core.BlockPos.ZERO,CollisionContext.empty()).isEmpty(),entry.getKey()+" "+facingName);
            }
            assertFalse(modelBindings.isEmpty(),entry.getKey()+" model bindings discovered");
        }
    }

    @Test void volumePumpBoundsRemainNarrowAndModelFacingCanBeAsymmetric() {
        BlockState north=BLOCKS.get("gas_volume_pump").defaultBlockState();
        var shape=AtmosMachineGeometry.shape("gas_volume_pump",north,0);
        assertTrue(shape.max(Direction.Axis.X)-shape.min(Direction.Axis.X) < 1.0);
        assertTrue(shape.max(Direction.Axis.Y) < .75);
        BlockState east=north.setValue(AtmosMachineBlock.FACING,Direction.EAST);
        assertNotEquals(AtmosMachineGeometry.shape("gas_volume_pump",north,0).bounds().toString(),
                AtmosMachineGeometry.shape("gas_volume_pump",east,0).bounds().toString());
    }

    @Test void manifoldGeometryUsesExactlyRendererConnectedPathsAndMask() {
        var block=BLOCKS.get("gas_pipe_manifold");
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var state=block.defaultBlockState().setValue(AtmosMachineBlock.FACING,facing);
            var base=AtmosMachineGeometry.shape("gas_pipe_manifold",state,0);
            var all=AtmosMachineGeometry.shape("gas_pipe_manifold",state,63);
            assertTrue(all.max(Direction.Axis.X)>=base.max(Direction.Axis.X));
            assertEquals(6,AtmosManifoldAppearance.connectedModelPaths(63,facing.getName()).size());
            for(String path:AtmosManifoldAppearance.connectedModelPaths(63,facing.getName()))
                assertNotNull(getClass().getResource("/assets/moonstation14/models/"+path+".json"),path);
        }
    }

    @Test void r6TegSnapshotsProvideFiniteFaceBackedGeometryForSemanticStatesAndAllFacings() {
        var semantic=Map.of("teg_center",java.util.Set.of(0,1,2,3,4,5,6,7,8,9,10,11,12),
                "teg_circulator",java.util.Set.of(26,27,28,29));
        for(var entry:semantic.entrySet()) for(int display=0;display<30;display++) for(Direction facing:Direction.Plane.HORIZONTAL) {
            var state=BLOCKS.get(entry.getKey()).defaultBlockState().setValue(AtmosMachineBlock.FACING,facing)
                    .setValue(AtmosMachineBlock.DISPLAY,display);
            var outline=AtmosMachineGeometry.shape(entry.getKey(),state,0);
            var collision=BLOCKS.get(entry.getKey()).getCollisionShape(state,null,net.minecraft.core.BlockPos.ZERO,CollisionContext.empty());
            String context=entry.getKey()+" display="+display+" facing="+facing+" semantic="+semantic.get(entry.getKey()).contains(display);
            assertFalse(outline.isEmpty(),context); assertEquals(outline.bounds(),collision.bounds(),context);
            var b=outline.bounds();
            for(double value:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ}) assertTrue(Double.isFinite(value),context+" bounds="+b);
            Bounds source=sourceFaceBackedBounds(entry.getKey(),display);
            assertTrue(b.minX>=0 && b.minY>=0 && b.minZ>=0 && b.maxX<=1 && b.maxY<=source.maxY()+1e-4 && b.maxZ<=1,
                    context+" bounds="+b);
            assertTrue(b.maxY<1.0,context+" must use the face-backed R6 bounds, not a full-cube placeholder");
            Bounds expected=rotate(sourceFaceBackedBounds(entry.getKey(),display),yaw(facing));
            assertBounds(expected,b,context+" rotated source OBJ bounds");
        }
        assertEquals("moonstation14:block/power_collection/generation_teg/unpowered", selector("teg_center",29));
        assertEquals("moonstation14:block/power_collection/generation_teg__tegcirculator/still_unlit", selector("teg_circulator",0));
        for(String block:java.util.List.of("teg_center","teg_circulator")) {
            var owner=BLOCKS.get(block);
            var north=owner.defaultBlockState().setValue(AtmosMachineBlock.FACING,Direction.NORTH);
            var east=north.setValue(AtmosMachineBlock.FACING,Direction.EAST);
            var northBounds=AtmosMachineGeometry.shape(block,north,0).bounds();
            var eastBounds=AtmosMachineGeometry.shape(block,east,0).bounds();
            var south=owner.defaultBlockState().setValue(AtmosMachineBlock.FACING,Direction.SOUTH);
            var west=owner.defaultBlockState().setValue(AtmosMachineBlock.FACING,Direction.WEST);
            var southBounds=AtmosMachineGeometry.shape(block,south,0).bounds();
            var westBounds=AtmosMachineGeometry.shape(block,west,0).bounds();
            assertNotEquals(northBounds,eastBounds,block+" variant yaw rotates model-derived bounds");
            assertNotEquals(southBounds,westBounds,block+" asymmetric R6 boxes distinguish 180 from 270 degrees");
            assertEquals(northBounds,AtmosMachineGeometry.shape(block,north,0).bounds(),block+" cached north base remains unchanged after east transform");
        }
    }
    @Test void r6PrimarySocketContractFitsMeasuredSquarePipeWithDocumentedClearance() {
        assertR6PrimarySocketFitFromMeasuredContractAndObjBounds();
    }
    private void assertR6PrimarySocketFitFromMeasuredContractAndObjBounds() {
        // Frozen R6 export/measured_ports.json defines PRIMARY as 4x4 units at Y=8.
        // Verify the runtime OBJ's actual face-backed bore groups, not just constants.
        var primaryModel=com.google.gson.JsonParser.parseString(resource("models/block/gas_pipe_visual_north.json")).getAsJsonObject();
        var primaryBounds=primaryModel.getAsJsonArray("elements").get(0).getAsJsonObject();
        double primaryMinX=primaryBounds.getAsJsonArray("from").get(0).getAsDouble()/16;
        double primaryMaxX=primaryBounds.getAsJsonArray("to").get(0).getAsDouble()/16;
        double primaryMinY=primaryBounds.getAsJsonArray("from").get(1).getAsDouble()/16;
        double primaryMaxY=primaryBounds.getAsJsonArray("to").get(1).getAsDouble()/16;
        double primaryCenterY=(primaryMinY+primaryMaxY)/2, primaryWidth=primaryMaxX-primaryMinX;
        for(int display=0;display<30;display++) for(String end:java.util.List.of("inlet","outlet")) {
            Bounds bore=sourceFaceBackedBounds("teg_circulator",display,end+"_bore");
            assertEquals(.5,(bore.minX()+bore.maxX())/2,1e-6,end+" bore X center display="+display);
            assertEquals(primaryCenterY,(bore.minY()+bore.maxY())/2,1e-6,end+" bore Y center display="+display);
            assertEquals(4.5/16,bore.maxX()-bore.minX(),1e-6,end+" bore width display="+display);
            assertEquals(4.5/16,bore.maxY()-bore.minY(),1e-6,end+" bore height display="+display);
            assertEquals(.25/16,(bore.maxX()-bore.minX()-primaryWidth)/2,1e-6,end+" per-side PRIMARY clearance display="+display);
            assertEquals(primaryCenterY,(primaryMinY+primaryMaxY)/2,1e-9,"PRIMARY JSON centerline");
        }
        var inletCollar=sourceFaceBackedBounds("teg_circulator",26,"inlet_collar");
        var outletCollar=sourceFaceBackedBounds("teg_circulator",26,"outlet_collar");
        assertEquals(0,inletCollar.minZ(),1e-6,"inlet collar meets block boundary");
        assertEquals(1,outletCollar.maxZ(),1e-6,"outlet collar meets block boundary");
        // The face-backed mesh bounds reach above Y=8; no separate hitbox is added.
        var core=sourceFaceBackedBounds("teg_center",0);
        var circ=sourceFaceBackedBounds("teg_circulator",26);
        assertEquals(13.16/16,core.maxY(),1e-4,"R6 core face-backed OBJ top");
        assertEquals(13.16/16,circ.maxY(),1e-4,"R6 circulator face-backed OBJ top");
        assertTrue(core.maxY()>primaryCenterY+primaryWidth/2,"core mesh rises above socket opening");
        assertTrue(circ.maxY()>primaryCenterY+primaryWidth/2,"circulator mesh rises above socket opening");
    }
    private int yaw(Direction facing) { return switch(facing) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> throw new AssertionError(facing); }; }
    private Bounds sourceFaceBackedBounds(String block,int display) {
        return sourceFaceBackedBounds(block,display,null);
    }
    private Bounds sourceFaceBackedBounds(String block,int display,String onlyGroup) {
        var variant=com.google.gson.JsonParser.parseString(resource("blockstates/"+block+".json")).getAsJsonObject()
                .getAsJsonObject("variants").getAsJsonObject("facing=north,display="+display+",pipe_layer=0");
        var model=com.google.gson.JsonParser.parseString(resource("models/"+variant.get("model").getAsString().substring("moonstation14:".length())+".json")).getAsJsonObject();
        String objPath=model.get("model").getAsString().substring("moonstation14:".length());
        String[] lines=resource(objPath).split("\\R");
        var vertices=new java.util.ArrayList<double[]>();
        var indices=new java.util.LinkedHashMap<String,java.util.List<Integer>>();
        String group="default"; indices.put(group,new java.util.ArrayList<>());
        for(String line:lines) {
            String[] token=line.trim().split("\\s+"); if(token.length==0) continue;
            if(token[0].equals("g") || token[0].equals("o")) { group=token.length>1?token[1]:"default"; indices.computeIfAbsent(group,k->new java.util.ArrayList<>()); }
            else if(token[0].equals("v") && token.length>=4) vertices.add(new double[]{Double.parseDouble(token[1]),Double.parseDouble(token[2]),Double.parseDouble(token[3])});
            else if(token[0].equals("f")) for(int i=1;i<token.length;i++) indices.get(group).add(Integer.parseInt(token[i].split("/")[0])-1);
        }
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY}, max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(var groupIndices:indices.entrySet()) if(!groupIndices.getValue().isEmpty()
                && (onlyGroup==null?!groupIndices.getKey().toLowerCase(java.util.Locale.ROOT).contains("overlay"):onlyGroup.equals(groupIndices.getKey())))
            for(int index:groupIndices.getValue()) for(int axis=0;axis<3;axis++) { min[axis]=Math.min(min[axis],vertices.get(index)[axis]); max[axis]=Math.max(max[axis],vertices.get(index)[axis]); }
        assertTrue(Double.isFinite(min[0]),"OBJ has face-backed "+(onlyGroup==null?"geometry":onlyGroup)+" group: "+objPath);
        return new Bounds(min[0],min[1],min[2],max[0],max[1],max[2]);
    }
    private Bounds rotate(Bounds b,int degrees) {
        return switch(degrees) {
            case 0 -> b;
            case 90 -> new Bounds(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
            case 180 -> new Bounds(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
            case 270 -> new Bounds(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
            default -> throw new AssertionError(degrees);
        };
    }
    private void assertBounds(Bounds expected,net.minecraft.world.phys.AABB actual,String context) {
        assertEquals(expected.minX(),actual.minX,1e-6,context+" minX"); assertEquals(expected.minY(),actual.minY,1e-6,context+" minY");
        assertEquals(expected.minZ(),actual.minZ,1e-6,context+" minZ"); assertEquals(expected.maxX(),actual.maxX,1e-6,context+" maxX");
        assertEquals(expected.maxY(),actual.maxY,1e-6,context+" maxY"); assertEquals(expected.maxZ(),actual.maxZ,1e-6,context+" maxZ");
    }
    private record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) { }
    private String selector(String block,int display) {
        var states=com.google.gson.JsonParser.parseString(resource("blockstates/"+block+".json")).getAsJsonObject().getAsJsonObject("variants");
        return states.getAsJsonObject("facing=north,display="+display+",pipe_layer=0").get("model").getAsString();
    }
    private String resource(String path) {
        try(var in=getClass().getResourceAsStream("/assets/moonstation14/"+path)) {
            assertNotNull(in,path); return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e) { throw new AssertionError(e); }
    }
}
