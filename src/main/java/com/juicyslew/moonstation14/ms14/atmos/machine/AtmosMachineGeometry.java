package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Collision geometry sourced from the currently wired native model bindings. */
final class AtmosMachineGeometry {
    private static final String ROOT = "/assets/moonstation14/";
    private static final Map<String, VoxelShape> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> BLOCKS = Map.ofEntries(
            Map.entry("teg_center", "teg_center"), Map.entry("teg_circulator", "teg_circulator"),
            Map.entry("gas_pressure_pump", "gas_pressure_pump"), Map.entry("gas_volume_pump", "gas_volume_pump"),
            Map.entry("gas_valve", "gas_valve"), Map.entry("gas_mixer", "gas_mixer"),
            Map.entry("gas_filter", "gas_filter"), Map.entry("gas_thermo_heater", "gas_thermo_heater"),
            Map.entry("gas_thermo_freezer", "gas_thermo_freezer"), Map.entry("passive_heat_exchanger", "passive_heat_exchanger"),
            Map.entry("gas_pipe_manifold", "gas_pipe_manifold"));

    private AtmosMachineGeometry() { }

    static VoxelShape shape(String profile, BlockState state, BlockGetter level, BlockPos pos) {
        if (!BLOCKS.containsKey(profile)) return Shapes.block();
        int mask = profile.equals("gas_pipe_manifold") && level != null && level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine
                ? machine.manifoldAppearanceMask() : 0;
        return shape(profile,state,mask);
    }

    static VoxelShape shape(String profile, BlockState state, int mask) {
        if (!BLOCKS.containsKey(profile)) return Shapes.block();
        String facing = state.getValue(AtmosMachineBlock.FACING).getName();
        int display = state.getValue(AtmosMachineBlock.DISPLAY), layer = state.getValue(AtmosMachineBlock.PIPE_LAYER);
        String key = profile + '/' + facing + '/' + display + '/' + layer + '/' + mask;
        return CACHE.computeIfAbsent(key, ignored -> load(profile, facing, display, layer, mask));
    }

    private static VoxelShape load(String block, String facing, int display, int layer, int mask) {
        try {
            JsonObject state = json("blockstates/" + block + ".json");
            String id;
            int yRotation;
            if (state.has("variants")) {
                JsonObject variant = state.getAsJsonObject("variants").getAsJsonObject("facing=" + facing + ",display=" + display + ",pipe_layer=" + layer);
                String modelId = variant.get("model").getAsString();
                id = modelId.substring(modelId.indexOf(':') + 1);
                yRotation = quarterTurn(variant.has("y") ? variant.get("y").getAsInt() : 0);
            } else {
                JsonObject apply = state.getAsJsonArray("multipart").asList().stream().map(e -> e.getAsJsonObject())
                        .filter(e -> e.has("when") && e.getAsJsonObject("when").has("facing") && e.getAsJsonObject("when").get("facing").getAsString().equals(facing))
                        .map(e -> e.getAsJsonObject("apply")).findFirst().orElseThrow();
                id = apply.get("model").getAsString().substring("moonstation14:".length());
                yRotation = quarterTurn(apply.has("y") ? apply.get("y").getAsInt() : 0);
            }
            JsonObject model = json("models/" + id + ".json");
            if ("neoforge:obj".equals(model.has("loader") ? model.get("loader").getAsString() : "")) {
                String obj = model.get("model").getAsString().substring("moonstation14:".length());
                return rotateY(parseObj(text(obj)), yRotation);
            }
            VoxelShape result = Shapes.empty();
            if (model.has("elements")) for (var element : model.getAsJsonArray("elements")) {
                JsonObject e = element.getAsJsonObject();
                double[] a = vector(e.getAsJsonArray("from")), b = vector(e.getAsJsonArray("to"));
                // Face-only render overlays have one face and sub-.01 model-unit thickness.
                if (e.has("faces") && e.getAsJsonObject("faces").size() == 1
                        && (Math.abs(a[0]-b[0]) < .01 || Math.abs(a[1]-b[1]) < .01 || Math.abs(a[2]-b[2]) < .01)) continue;
                result = Shapes.or(result, Block.box(a[0], a[1], a[2], b[0], b[1], b[2]));
            }
            if (block.equals("gas_pipe_manifold")) for (String path : AtmosManifoldAppearance.connectedModelPaths(mask, facing))
                result = Shapes.or(result, jsonShape(json("models/" + path + ".json"), path));
            if (result.isEmpty()) throw new IllegalStateException("Known wired model has no physical geometry: models/" + id + ".json");
            return rotateY(result, yRotation);
        } catch (Exception e) { throw new IllegalStateException("Cannot derive atmos geometry for " + block + " facing=" + facing + " display=" + display + " layer=" + layer, e); }
    }

    private static int quarterTurn(int degrees) {
        if (degrees % 90 != 0) throw new IllegalStateException("Unsupported non-quarter-turn blockstate y rotation: " + degrees);
        return Math.floorMod(degrees, 360);
    }

    /** Applies the vanilla blockstate model yaw about block center to already block-local model bounds. */
    private static VoxelShape rotateY(VoxelShape source, int degrees) {
        if (degrees == 0) return source;
        VoxelShape rotated = Shapes.empty();
        for (AABB box : source.toAabbs()) {
            AABB transformed = switch (degrees) {
                case 90 -> new AABB(1-box.maxZ, box.minY, box.minX, 1-box.minZ, box.maxY, box.maxX);
                case 180 -> new AABB(1-box.maxX, box.minY, 1-box.maxZ, 1-box.minX, box.maxY, 1-box.minZ);
                case 270 -> new AABB(box.minZ, box.minY, 1-box.maxX, box.maxZ, box.maxY, 1-box.minX);
                default -> throw new IllegalStateException("Unsupported y rotation " + degrees);
            };
            rotated = Shapes.or(rotated, Shapes.create(transformed));
        }
        return rotated;
    }

    private static VoxelShape parseObj(String source) {
        List<double[]> vertices = new ArrayList<>();
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        List<Integer> active = new ArrayList<>(); groups.put("default", active);
        for (String line : source.split("\\R")) {
            String[] p = line.trim().split("\\s+"); if (p.length == 0) continue;
            if (p[0].equals("g") || p[0].equals("o")) active = groups.computeIfAbsent(p.length > 1 ? p[1] : "default", x -> new ArrayList<>());
            else if (p[0].equals("v") && p.length >= 4) vertices.add(new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            else if (p[0].equals("f")) for (int i = 1; i < p.length; i++) active.add(Integer.parseInt(p[i].split("/")[0]) - 1);
        }
        VoxelShape shape = Shapes.empty();
        for (var group : groups.entrySet()) {
            if (group.getValue().isEmpty() || group.getKey().toLowerCase(Locale.ROOT).contains("overlay")) continue;
            double[] min = {Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for (int index : group.getValue()) { double[] v = vertices.get(index); for (int axis=0; axis<3; axis++) { min[axis]=Math.min(min[axis],v[axis]); max[axis]=Math.max(max[axis],v[axis]); } }
            shape = Shapes.or(shape, Block.box(min[0]*16,min[1]*16,min[2]*16,max[0]*16,max[1]*16,max[2]*16));
        }
        if (shape.isEmpty()) throw new IllegalStateException("OBJ has no face-backed physical groups");
        return shape;
    }
    private static VoxelShape jsonShape(JsonObject model, String path) {
        VoxelShape shape=Shapes.empty();
        if(model.has("elements")) for(var element:model.getAsJsonArray("elements")) {
            JsonObject e=element.getAsJsonObject(); double[] a=vector(e.getAsJsonArray("from")),b=vector(e.getAsJsonArray("to"));
            if(e.has("faces")&&e.getAsJsonObject("faces").size()==1&&(Math.abs(a[0]-b[0])<.01||Math.abs(a[1]-b[1])<.01||Math.abs(a[2]-b[2])<.01))continue;
            shape=Shapes.or(shape,Block.box(a[0],a[1],a[2],b[0],b[1],b[2]));
        }
        if(shape.isEmpty())throw new IllegalStateException("Connected manifold model has no physical geometry: "+path);
        return shape;
    }
    private static double[] vector(com.google.gson.JsonArray a) { return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()}; }
    private static JsonObject json(String path) { try (var reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); } catch (Exception e) { throw new IllegalStateException("Cannot read "+path,e); } }
    private static String text(String path) throws Exception { try (var in=resource(path)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); } }
    private static java.io.InputStream resource(String path) { var in=AtmosMachineGeometry.class.getResourceAsStream(ROOT+path); if(in==null) throw new IllegalStateException("Missing wired model resource "+path); return in; }
}
