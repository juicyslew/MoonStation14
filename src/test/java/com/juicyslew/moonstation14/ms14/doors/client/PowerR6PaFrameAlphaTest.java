package com.juicyslew.moonstation14.ms14.doors.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures every authored PA geometry face has visible texels in every animation frame. */
class PowerR6PaFrameAlphaTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test void everyConsumedPaFaceHasAlphaInEveryUsedAnimationFrame() throws Exception {
        var roles = new HashSet<String>();
        var failures = new ArrayList<String>();
        int coverage = 0;
        var images = new HashMap<String, NativeImage>();
        var imageFrames = new HashMap<String, ImageFrames>();
        try {
        for (String state : List.of("pa_control_box", "pa_power_box", "pa_fuel_chamber", "pa_end_cap",
                "pa_emitter_port", "pa_emitter_fore", "pa_emitter_starboard")) {
            JsonObject blockstate = json(ROOT + "blockstates/" + state + ".json");
            for (JsonElement modelRef : modelReferences(blockstate)) {
                String model = modelRef.getAsString().replace("moonstation14:block/", "");
                roles.add(state);
                var resolved = resolve(model, new HashMap<>());
                JsonArray elements = resolved.getAsJsonArray("elements");
                for (int ei = 0; ei < elements.size(); ei++) {
                    JsonObject element = elements.get(ei).getAsJsonObject();
                    JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
                    if (!nondegenerate(from, to)) continue;
                    String name = element.has("name") ? element.get("name").getAsString() : "element[" + ei + "]";
                    for (var entry : element.getAsJsonObject("faces").entrySet()) {
                        JsonObject face = entry.getValue().getAsJsonObject();
                        JsonArray uv = face.getAsJsonArray("uv");
                        if (uv == null || !validUv(uv)) continue;
                        String textureKey = face.get("texture").getAsString();
                        while (textureKey.startsWith("#")) textureKey = resolved.getAsJsonObject("textures").get(textureKey.substring(1)).getAsString();
                        String texture = textureKey.replace("moonstation14:", "");
                        String source = ROOT + "textures/" + texture + ".png";
                        ImageFrames frameData = imageFrames.computeIfAbsent(source, ignored -> {
                            try { return loadFrames(source); } catch (Exception e) { throw new RuntimeException(e); }
                        });
                        NativeImage image = images.computeIfAbsent(source, ignored -> {
                            try { return readImage(source); } catch (Exception e) { throw new RuntimeException(e); }
                        });
                            int x0 = clamp((int)Math.floor(Math.min(uv.get(0).getAsDouble(),uv.get(2).getAsDouble()) * frameData.width / 16.0),frameData.width);
                            int y0 = clamp((int)Math.floor(Math.min(uv.get(1).getAsDouble(),uv.get(3).getAsDouble()) * frameData.height / 16.0),frameData.height);
                            int x1 = clamp((int)Math.ceil(Math.max(uv.get(0).getAsDouble(),uv.get(2).getAsDouble()) * frameData.width / 16.0),frameData.width);
                            int y1 = clamp((int)Math.ceil(Math.max(uv.get(1).getAsDouble(),uv.get(3).getAsDouble()) * frameData.height / 16.0),frameData.height);
                            boolean faceCovered = false;
                            for (int frame : frameData.frames) {
                                boolean visible = false;
                                for (int y = y0; y < y1 && !visible; y++) for (int x = x0; x < x1; x++) {
                                    int px = x + (frame % frameData.columns) * frameData.width;
                                    int py = y + (frame / frameData.columns) * frameData.height;
                                    if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()
                                            && ((image.getPixelRGBA(px, py) >>> 24) & 0xff) != 0) { visible = true; break; }
                                }
                                faceCovered |= visible;
                                if (!visible) failures.add("source=" + source + " model=" + model + " element=" + name
                                        + " direction=" + entry.getKey() + " UV=" + uv + " frame=" + frame);
                            }
                            if (faceCovered) coverage++;
                    }
                }
            }
        }
        assertEquals(7, roles.size(), "all seven PA block roles are consumed");
        assertTrue(coverage > 0, "at least one geometric face has alpha coverage");
        assertTrue(failures.isEmpty(), "transparent authored faces (review exact evidence before changing assets):\n" + String.join("\n", failures));
        } finally { images.values().forEach(NativeImage::close); }
    }

    private static List<JsonElement> modelReferences(JsonObject state) {
        var result = new ArrayList<JsonElement>();
        if (state.has("variants")) for (JsonElement e : state.getAsJsonObject("variants").entrySet().stream().map(Map.Entry::getValue).toList()) collectModel(e, result);
        if (state.has("multipart")) for (JsonElement e : state.getAsJsonArray("multipart")) collectModel(e.getAsJsonObject().get("apply"), result);
        return result;
    }
    private static void collectModel(JsonElement value, List<JsonElement> out) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> collectModel(e, out));
        else if (value.isJsonObject()) { if (value.getAsJsonObject().has("model")) out.add(value.getAsJsonObject().get("model")); }
    }
    static List<JsonObject> resolvedMultipartModels(JsonObject blockstate,Map<String,String> properties) throws Exception {
        var result=new ArrayList<JsonObject>(); var cache=new HashMap<String,JsonObject>();
        for(JsonElement partElement:blockstate.getAsJsonArray("multipart")) {
            JsonObject part=partElement.getAsJsonObject();
            if(part.has("when")) {
                boolean matches=true;
                for(var condition:part.getAsJsonObject("when").entrySet())
                    if(!properties.getOrDefault(condition.getKey(),"\u0000").equals(condition.getValue().getAsString())) matches=false;
                if(!matches) continue;
            }
            collectResolvedModels(part.get("apply"),result,cache);
        }
        return result;
    }
    private static void collectResolvedModels(JsonElement apply,List<JsonObject> out,Map<String,JsonObject> cache) throws Exception {
        if(apply.isJsonArray()) for(JsonElement option:apply.getAsJsonArray()) collectResolvedModels(option,out,cache);
        else if(apply.isJsonObject()) {
            JsonObject object=apply.getAsJsonObject();
            if(object.has("model")) out.add(resolve(object.get("model").getAsString().replace("moonstation14:block/",""),cache));
        }
    }
    private static JsonObject resolve(String name, Map<String, JsonObject> cache) throws Exception {
        if (cache.containsKey(name)) return cache.get(name);
        JsonObject own = json(ROOT + "models/block/" + name + ".json"), result = new JsonObject();
        if (own.has("parent")) result = resolve(own.get("parent").getAsString().replace("moonstation14:block/", ""), cache).deepCopy();
        if (own.has("textures")) {
            if (!result.has("textures")) result.add("textures", new JsonObject());
            for (var entry : own.getAsJsonObject("textures").entrySet())
                result.getAsJsonObject("textures").add(entry.getKey(), entry.getValue());
        }
        if (own.has("elements")) result.add("elements", own.get("elements"));
        cache.put(name, result); return result;
    }
    static JsonObject json(String path) throws Exception {
        try (InputStream stream = PowerR6PaFrameAlphaTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static NativeImage readImage(String path) throws Exception {
        try (InputStream stream = PowerR6PaFrameAlphaTest.class.getResourceAsStream(path)) { assertNotNull(stream, path); return NativeImage.read(stream); }
    }
    private static ImageFrames loadFrames(String source) throws Exception {
        try (NativeImage image = readImage(source)) {
            String metadata = source + ".mcmeta";
            var stream = PowerR6PaFrameAlphaTest.class.getResourceAsStream(metadata);
            if (stream == null) return new ImageFrames(image.getWidth(), image.getHeight(), 1, List.of(0));
            JsonObject animation;
            try (stream) { animation = json(metadata).getAsJsonObject("animation"); }
            int width = animation.has("width") ? animation.get("width").getAsInt() : animation.has("height") ? image.getWidth() : Math.min(image.getWidth(),image.getHeight());
            int height = animation.has("height") ? animation.get("height").getAsInt() : animation.has("width") ? width : Math.min(image.getWidth(),image.getHeight());
            int columns = image.getWidth() / width, rows = image.getHeight() / height;
            List<Integer> frames = new ArrayList<>();
            if (animation.has("frames")) for (JsonElement frame : animation.getAsJsonArray("frames")) frames.add(frame.isJsonObject() ? frame.getAsJsonObject().get("index").getAsInt() : frame.getAsInt());
            else for (int i = 0; i < columns * rows; i++) frames.add(i);
            return new ImageFrames(width, height, columns, frames);
        }
    }
    private static int clamp(int value,int frameSize) { return Math.max(0,Math.min(frameSize,value)); }
    private static boolean nondegenerate(JsonArray from, JsonArray to) { for (int i=0;i<3;i++) if (from.get(i).getAsDouble() == to.get(i).getAsDouble()) return false; return true; }
    private static boolean validUv(JsonArray uv) { return uv.size() == 4 && uv.get(0).getAsDouble() != uv.get(2).getAsDouble() && uv.get(1).getAsDouble() != uv.get(3).getAsDouble(); }
    private record ImageFrames(int width, int height, int columns, List<Integer> frames) {}
}
