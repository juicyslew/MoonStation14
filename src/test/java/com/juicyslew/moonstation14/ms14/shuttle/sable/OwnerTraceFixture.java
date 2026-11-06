package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class OwnerTraceFixture {
    private OwnerTraceFixture() {}
    static JsonObject load(String name) {
        String path="/fixtures/shuttle/"+name;
        try(var in=OwnerTraceFixture.class.getResourceAsStream(path)) {
            if(in==null) throw new AssertionError("missing "+path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception e) { throw new AssertionError(e); }
    }
    static MovementVector vector(JsonArray a) { return new MovementVector(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()); }
    static List<CurrentFrameCollisionScope.NativeCollisionTrace> rows(JsonArray source, UUID frame) {
        var result=new ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for(var item:source) {
            JsonObject r=item.getAsJsonObject(),g=r;
            JsonArray pose=g.getAsJsonArray("pose"),bounds=g.getAsJsonArray("bounds");
            double[] matrix=new double[pose.size()]; for(int i=0;i<matrix.length;i++) matrix[i]=pose.get(i).getAsDouble();
            var geometry=new NativeCollisionGeometry(vector(g.getAsJsonArray("center")),vector(g.getAsJsonArray("dimensions")),
                    new NativeCollisionGeometry.QuaternionValue(g.getAsJsonArray("orientation").get(0).getAsDouble(),g.getAsJsonArray("orientation").get(1).getAsDouble(),g.getAsJsonArray("orientation").get(2).getAsDouble(),g.getAsJsonArray("orientation").get(3).getAsDouble()),
                    new NativeFrameInterval.PoseValue(matrix),new AABB(bounds.get(0).getAsJsonArray().get(0).getAsDouble(),bounds.get(0).getAsJsonArray().get(1).getAsDouble(),bounds.get(0).getAsJsonArray().get(2).getAsDouble(),bounds.get(1).getAsJsonArray().get(0).getAsDouble(),bounds.get(1).getAsJsonArray().get(1).getAsDouble(),bounds.get(1).getAsJsonArray().get(2).getAsDouble()),vector(g.getAsJsonArray("mtv")),vector(g.getAsJsonArray("motion")),r.get("iteration").getAsInt(),r.get("substeps").getAsInt());
            MovementVector selected=vector(r.getAsJsonArray("selected")),before=vector(r.getAsJsonArray("before")),applied=vector(r.getAsJsonArray("applied")),response=vector(r.getAsJsonArray("response"));
            result.add(new CurrentFrameCollisionScope.NativeCollisionTrace(frame,r.get("substep").getAsInt(),r.get("substeps").getAsInt(),r.get("iteration").getAsInt(),new BlockPos(r.getAsJsonArray("selectedPos").get(0).getAsInt(),r.getAsJsonArray("selectedPos").get(1).getAsInt(),r.getAsJsonArray("selectedPos").get(2).getAsInt()),Blocks.STONE.defaultBlockState(),"stone","stone",selected.x(),selected.y(),selected.z(),before.x(),before.y(),before.z(),applied.x(),applied.y(),applied.z(),response.x(),response.y(),response.z(),geometry,r.get("vertical").getAsBoolean()));
        }
        return List.copyOf(result);
    }
    static CurrentFrameCollisionScope.WorldClipObservation worldClip(JsonObject root) {
        return worldClip(root, new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER,null));
    }
    static CurrentFrameCollisionScope.WorldClipObservation worldClip(JsonObject root, net.minecraft.world.entity.Entity entity) {
        JsonObject clip=root.getAsJsonObject("worldClip");
        Vec3 in=vec(clip.getAsJsonArray("input")),out=vec(clip.getAsJsonArray("output")); JsonArray p=clip.getAsJsonArray("positionBefore"),b=clip.getAsJsonArray("boundsBefore");
        Vec3 pos=vec(p); AABB box=new AABB(vec(b.get(0).getAsJsonArray()),vec(b.get(1).getAsJsonArray()));
        return CurrentFrameCollisionScope.withTrackedCharacterPose(entity,false,()->{CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(entity,in,pos,box);CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(entity,out);return null;}).worldClipObservation();
    }
    private static Vec3 vec(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
