package com.juicyslew.moonstation14.ms14.doors;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads source-authored cuboids into canonical local door coordinates. */
public final class AuthoredDoorGeometry {
    private AuthoredDoorGeometry() { }

    public static List<AirlockGeometry.Box> load(String resourcePath) {
        try (var stream = AuthoredDoorGeometry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IllegalStateException("Missing door geometry resource " + resourcePath);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var boxes = new ArrayList<AirlockGeometry.Box>();
            for (var element : root.getAsJsonArray("elements")) {
                var object = element.getAsJsonObject();
                var from = object.getAsJsonArray("from");
                var to = object.getAsJsonArray("to");
                double[] f = {from.get(0).getAsDouble()/16, from.get(1).getAsDouble()/16, from.get(2).getAsDouble()/16};
                double[] t = {to.get(0).getAsDouble()/16, to.get(1).getAsDouble()/16, to.get(2).getAsDouble()/16};
                for (int axis=0; axis<3; axis++)
                    if (!Double.isFinite(f[axis]) || !Double.isFinite(t[axis]) || f[axis] > t[axis])
                        throw new IllegalStateException("Invalid door cuboid in " + resourcePath);
                if (object.has("rotation")) throw new IllegalStateException("Rotated door cuboid unsupported in " + resourcePath);
                boxes.add(new AirlockGeometry.Box(1-t[0], f[1], 1-t[2], 1-f[0], t[1], 1-f[2]));
            }
            return List.copyOf(boxes);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
}
