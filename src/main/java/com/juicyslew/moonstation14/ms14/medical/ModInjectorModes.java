package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public final class ModInjectorModes {
    public static final PrototypeType<InjectorModeData> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "injector_mode"),
            "moonstation14/injector_mode", InjectorModeData.CODEC, new InjectorModeMergeStrategy());
    private ModInjectorModes() { }
    public static void bootstrapClientBundled() {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : java.util.List.of("base_inject_mode", "base_draw_mode", "base_syringe_mode", "syringe_inject_mode", "syringe_draw_mode")) {
            String path = "/data/moonstation14/moonstation14/injector_mode/" + name + ".json";
            try (var stream = ModInjectorModes.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing bundled injector mode " + path);
                raw.put(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read bundled injector mode " + path, exception);
            }
        }
        PrototypeRuntime.clientManager().reload(TYPE, raw);
    }
}

