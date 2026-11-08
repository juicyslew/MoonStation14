package com.juicyslew.moonstation14.ms14.ui.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Test-only implementation of the supported bitmap/space providers in Minecraft's default font. */
final class MinecraftBitmapFont {
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Integer, Float> spaces = new HashMap<>();
    private final Set<String> providerManifest = new LinkedHashSet<>();

    static MinecraftBitmapFont load(Path resourcesJar) throws Exception {
        var font = new MinecraftBitmapFont();
        try (var zip = new ZipFile(resourcesJar.toFile())) {
            var root = json(zip, "assets/minecraft/font/default.json");
            List<JsonObject> ordered = new ArrayList<>();
            for (var item : root.getAsJsonArray("providers")) {
                var reference = item.getAsJsonObject();
                require("reference".equals(reference.get("type").getAsString()), "default font graph has unsupported root provider");
                String id = reference.get("id").getAsString();
                if (reference.has("filter")) {
                    JsonObject filter = reference.getAsJsonObject("filter");
                    require(filter.size() == 1 && filter.has("uniform") && !filter.get("uniform").getAsBoolean(),
                            "default-font reference filter unsupported; this backend models non-uniform default options only");
                }
                font.providerManifest.add("reference:" + id);
                String[] idParts = id.split(":", 2);
                require(idParts.length == 2, "invalid font reference " + id);
                String path = "assets/" + idParts[0] + "/font/" + idParts[1] + ".json";
                JsonObject referenced = json(zip, path);
                for (var providerElement : referenced.getAsJsonArray("providers")) {
                    var provider = providerElement.getAsJsonObject();
                    if ("space".equals(provider.get("type").getAsString())) {
                        font.providerManifest.add(id + "#space");
                        for (var entry : provider.getAsJsonObject("advances").entrySet())
                            font.spaces.putIfAbsent(entry.getKey().codePointAt(0), entry.getValue().getAsFloat());
                    } else if ("bitmap".equals(provider.get("type").getAsString())) {
                        String file = provider.get("file").getAsString();
                        font.providerManifest.add(id + "#bitmap:" + file);
                        font.loadBitmap(zip, provider);
                    } else if ("reference".equals(provider.get("type").getAsString())) {
                        throw new UnsupportedOperationException("nested default-font reference unsupported: " + provider);
                    } else {
                        throw new UnsupportedOperationException("default-font provider unsupported: " + provider.get("type"));
                    }
                }
            }
        }
        return font;
    }

    private void loadBitmap(ZipFile zip, JsonObject definition) throws Exception {
        String file = definition.get("file").getAsString();
        String[] fileParts = file.split(":", 2);
        require(fileParts.length == 2, "invalid bitmap resource id " + file);
        // ResourceLocation already contains the extension from BitmapProviderDefinition.file;
        // BitmapProvider only prefixes textures/ when opening it.
        String path = "assets/" + fileParts[0] + "/textures/" + fileParts[1];
        var entry = zip.getEntry(path);
        require(entry != null, "missing Minecraft bitmap resource " + path);
        try (InputStream input = zip.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(input);
            require(image != null, "invalid Minecraft bitmap resource " + path);
            int[][] chars = new int[definition.getAsJsonArray("chars").size()][];
            int row = 0;
            for (var charsRow : definition.getAsJsonArray("chars")) chars[row++] = charsRow.getAsString().codePoints().toArray();
            require(chars.length > 0 && chars[0].length > 0, "empty bitmap character grid " + path);
            int cellWidth = image.getWidth() / chars[0].length;
            int cellHeight = image.getHeight() / chars.length;
            require(cellWidth > 0 && cellHeight > 0 && image.getWidth() % chars[0].length == 0 && image.getHeight() % chars.length == 0,
                    "bitmap dimensions do not divide character grid " + path);
            int lineHeight = definition.has("height") ? definition.get("height").getAsInt() : 8;
            int ascent = definition.get("ascent").getAsInt();
            require(ascent <= lineHeight, "Minecraft bitmap ascent exceeds height " + path);
            float scale = (float) lineHeight / cellHeight;
            for (int gy = 0; gy < chars.length; gy++) {
                require(chars[gy].length == chars[0].length, "ragged bitmap character grid " + path);
                for (int gx = 0; gx < chars[gy].length; gx++) {
                    int cp = chars[gy][gx];
                    if (cp == 0 || cp == 32) continue;
                    int trimWidth = actualGlyphWidth(image, gx * cellWidth, gy * cellHeight, cellWidth, cellHeight);
                    int advance = (int)(0.5 + (double)((float)trimWidth * scale)) + 1;
                    Glyph glyph = new Glyph(image, gx * cellWidth, gy * cellHeight, cellWidth, cellHeight, scale, ascent, advance, path);
                    glyphs.putIfAbsent(cp, glyph); // FontSet selects the first active provider with the glyph.
                }
            }
        }
    }

    private static int actualGlyphWidth(BufferedImage image, int x, int y, int width, int height) {
        int i;
        for (i = width - 1; i >= 0; i--) {
            for (int row = 0; row < height; row++) {
                int pixel = image.getRGB(x + i, y + row);
                if ((pixel >>> 24) != 0) return i + 1;
            }
        }
        return i + 1;
    }

    float advance(int codePoint) {
        if (spaces.containsKey(codePoint)) return spaces.get(codePoint);
        Glyph glyph = glyphs.get(codePoint);
        if (glyph == null) throw unsupported(codePoint);
        return glyph.advance;
    }

    int width(String text) {
        return text.codePoints().map(cp -> (int) advance(cp)).sum();
    }

    String manifest() { return String.join("\n", providerManifest); }

    void draw(BufferedImage target, int x, int baseline, int codePoint, int argb) {
        Glyph glyph = glyphs.get(codePoint);
        if (glyph == null) {
            if (spaces.containsKey(codePoint)) return;
            throw unsupported(codePoint);
        }
        // BitmapProvider supplies the complete grid cell; oversample is 1/scale. Baked glyphs are
        // rasterized at the scaled dimensions, with the source cell sampled nearest-neighbour.
        int outputWidth = Math.max(1, Math.round(glyph.width * glyph.scale));
        int outputHeight = Math.max(1, Math.round(glyph.height * glyph.scale));
        int top = baseline - glyph.ascent;
        for (int py = 0; py < outputHeight; py++) for (int px = 0; px < outputWidth; px++) {
            int targetX = x + px, targetY = top + py;
            if (targetX < 0 || targetY < 0 || targetX >= target.getWidth() || targetY >= target.getHeight()) continue;
            int sx = glyph.x + Math.min(glyph.width - 1, (int)(px / glyph.scale));
            int sy = glyph.y + Math.min(glyph.height - 1, (int)(py / glyph.scale));
            int alpha = glyph.image.getRGB(sx, sy) >>> 24;
            if (alpha != 0) target.setRGB(targetX, targetY, (alpha << 24) | (argb & 0x00ffffff));
        }
    }

    /** Bitmap-provider cell ink, addressed by its top-left; logical cell bounds may exceed advance. */
    void drawCell(BufferedImage target, int x, int top, int codePoint, int argb, float scaleX, float scaleY) {
        Glyph glyph = glyphs.get(codePoint);
        if (glyph == null) { if (spaces.containsKey(codePoint)) return; throw unsupported(codePoint); }
        int width=Math.max(1,Math.round(glyph.width*glyph.scale*scaleX));
        int height=Math.max(1,Math.round(glyph.height*glyph.scale*scaleY));
        for(int py=0;py<height;py++) for(int px=0;px<width;px++) {
            int targetX=x+px,targetY=top+py;
            if(targetX<0||targetY<0||targetX>=target.getWidth()||targetY>=target.getHeight())continue;
            int sx=glyph.x+Math.min(glyph.width-1,(int)(px/(glyph.scale*scaleX)));
            int sy=glyph.y+Math.min(glyph.height-1,(int)(py/(glyph.scale*scaleY)));
            int alpha=glyph.image.getRGB(sx,sy)>>>24;
            if(alpha!=0)target.setRGB(targetX,targetY,(alpha<<24)|(argb&0x00ffffff));
        }
    }
    int cellWidth(int codePoint){Glyph glyph=glyphs.get(codePoint);return glyph==null?0:Math.max(1,Math.round(glyph.width*glyph.scale));}
    int cellHeight(int codePoint){Glyph glyph=glyphs.get(codePoint);return glyph==null?0:Math.max(1,Math.round(glyph.height*glyph.scale));}
    int ascent(int codePoint){Glyph glyph=glyphs.get(codePoint);return glyph==null?0:glyph.ascent;}

    private static UnsupportedOperationException unsupported(int cp) {
        return new UnsupportedOperationException("Minecraft default-font codepoint/provider unsupported: U+" + Integer.toHexString(cp));
    }
    private static JsonObject json(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path); require(entry != null, "missing Minecraft resource " + path);
        try (var reader = new java.io.InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record Glyph(BufferedImage image, int x, int y, int width, int height, float scale, int ascent, int advance, String resource) { }
}
