package com.juicyslew.moonstation14.ms14.equipment.client;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.*;

/** Validated immutable geometry parsed from the authored wearable interchange format. */
public final class AuthoredWearableMesh {
    public record Vertex(float x, float y, float z, float u, float v) { }
    public record Normal(float x, float y, float z) { }
    public record Quad(List<Vertex> vertices, Normal normal) {
       public Quad { vertices = List.copyOf(vertices); }
    }
    private final ResourceLocation texture;
    private final Map<EquipmentRenderPlan.Anchor, List<Quad>> bones;

    private AuthoredWearableMesh(ResourceLocation texture, Map<EquipmentRenderPlan.Anchor, List<Quad>> bones) {
        this.texture = texture;
        var copy = new LinkedHashMap<EquipmentRenderPlan.Anchor, List<Quad>>();
        bones.forEach((anchor, quads) -> copy.put(anchor, List.copyOf(quads)));
        this.bones = Collections.unmodifiableMap(copy);
    }
    public ResourceLocation texture() { return texture; }
    public Map<EquipmentRenderPlan.Anchor, List<Quad>> bones() { return bones; }

    public static AuthoredWearableMesh parse(Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)".equals(str(root,"schema")))
                throw invalid("unsupported schema");
            ResourceLocation texture = ResourceLocation.parse(str(root,"texture"));
            JsonObject anchors = root.getAsJsonObject("anchors");
            if (anchors == null || anchors.size() == 0) throw invalid("missing anchors");
        var bones = new LinkedHashMap<EquipmentRenderPlan.Anchor,List<Quad>>();
            for (var entry : anchors.entrySet()) {
                EquipmentRenderPlan.Anchor anchor = switch (entry.getKey()) {
                    case "head" -> EquipmentRenderPlan.Anchor.HEAD; case "body", "torso" -> EquipmentRenderPlan.Anchor.TORSO;
                    case "left_arm" -> EquipmentRenderPlan.Anchor.LEFT_ARM; case "right_arm" -> EquipmentRenderPlan.Anchor.RIGHT_ARM;
                    case "left_leg" -> EquipmentRenderPlan.Anchor.LEFT_LEG; case "right_leg" -> EquipmentRenderPlan.Anchor.RIGHT_LEG;
                    default -> throw invalid("unknown bone " + entry.getKey());
                };
                var quads = new ArrayList<Quad>();
                JsonArray cubes = entry.getValue().getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) throw invalid("missing cubes");
                for (JsonElement cubeElement : cubes) {
                    JsonArray faces = cubeElement.getAsJsonObject().getAsJsonArray("quads");
                    if (faces == null) throw invalid("missing quads");
                    for (JsonElement face : faces) quads.add(parseQuad(face.getAsJsonObject()));
                }
                bones.put(anchor, quads);
            }
            return new AuthoredWearableMesh(texture,bones);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("invalid authored wearable mesh",ex); }
    }
    private static Quad parseQuad(JsonObject q) {
        JsonArray points=q.getAsJsonArray("vertices"), uv=q.getAsJsonArray("uv_top_left"), winding=q.getAsJsonArray("winding"), normal=q.getAsJsonArray("normal");
        if(points==null||points.size()!=4||uv==null||uv.size()!=4||winding==null||winding.size()!=4||normal==null||normal.size()!=3) throw invalid("invalid quad dimensions");
        double nx=num(normal,0), ny=num(normal,1), nz=num(normal,2), length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(!Double.isFinite(length)||length==0||Math.abs(length-1)>1e-4) throw invalid("normal must be unit length");
        boolean[] seen=new boolean[4]; var vertices=new Vertex[4];
        for(int i=0;i<4;i++) {
            int index=winding.get(i).getAsInt(); if(index<0||index>3||seen[index]) throw invalid("winding must be a permutation"); seen[index]=true;
            double[] p=triple(points.get(index).getAsJsonArray()), t=pair(uv.get(index).getAsJsonArray());
            if(t[0]<0||t[0]>1||t[1]<0||t[1]>1) throw invalid("UV outside 0..1");
            vertices[i]=new Vertex((float)(p[0]/16),(float)(p[1]/16),(float)(p[2]/16),(float)t[0],(float)t[1]);
        }
        return new Quad(List.of(vertices),new Normal((float)nx,(float)ny,(float)nz));
    }
    private static double[] triple(JsonArray a) { if(a.size()!=3) throw invalid("expected three coordinates"); return new double[]{num(a,0),num(a,1),num(a,2)}; }
    private static double[] pair(JsonArray a) { if(a.size()!=2) throw invalid("expected two UV coordinates"); return new double[]{num(a,0),num(a,1)}; }
    private static double num(JsonArray a,int i) { double d=a.get(i).getAsDouble(); if(!Double.isFinite(d)) throw invalid("non-finite number"); return d; }
    private static String str(JsonObject o,String key) { return o.get(key).getAsString(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

