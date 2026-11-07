package com.juicyslew.moonstation14.ms14.combat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable artist-authored weapon frame/anchor data. Does not own item scale or pose policy. */
public final class GunPresentationBindings {
    public record Binding(GunArmAimMath.Vec bore, GunArmAimMath.Vec up, GunArmAimMath.Vec grip,
                          GunArmAimMath.Vec support, GunArmAimMath.Vec muzzle) {
        public Binding {
            finite(bore); finite(up); finite(grip); finite(muzzle);
            if (support != null) finite(support);
            var b=bore.normalized(); var u=up.normalized();
            if (Math.abs(b.dot(u)) > 1e-6) throw new IllegalArgumentException("non-orthogonal gun frame");
        }
        private static void finite(GunArmAimMath.Vec v) {
            if (v==null || !Double.isFinite(v.x()) || !Double.isFinite(v.y()) || !Double.isFinite(v.z()))
                throw new IllegalArgumentException("invalid gun presentation coordinate");
        }
    }
    private static Map<ResourceLocation, Binding> entries=Map.of();
    private GunPresentationBindings() { }
    public static Binding find(ResourceLocation itemId) { return entries.get(itemId); }
    static Map<ResourceLocation, Binding> snapshot() { return entries; }

    public static void load() {
        ResourceLocation file=ResourceLocation.fromNamespaceAndPath("moonstation14", "gun_presentation_bindings.json");
        var resource=Minecraft.getInstance().getResourceManager().getResource(file).orElseThrow(() -> new IllegalStateException("Missing " + file));
        try (var reader=new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            Map<ResourceLocation,Binding> next=new LinkedHashMap<>();
            for (var item:root.getAsJsonObject("items").entrySet()) {
                JsonObject o=item.getValue().getAsJsonObject();
                var binding=new Binding(vec(o,"bore"),vec(o,"up"),vec(o,"grip"),o.has("support")?vec(o,"support"):null,vec(o,"muzzle"));
                ResourceLocation id=ResourceLocation.fromNamespaceAndPath("moonstation14",item.getKey());
                if(next.put(id,binding)!=null) throw new IllegalArgumentException("duplicate binding " + id);
            }
            if(next.size()!=8) throw new IllegalArgumentException("Expected 8 gun presentation bindings, got " + next.size());
            entries=Collections.unmodifiableMap(next);
        } catch (Exception e) { throw new IllegalStateException("Invalid gun presentation bindings",e); }
    }
    private static GunArmAimMath.Vec vec(JsonObject o,String key) {
        var a=o.getAsJsonArray(key); if(a==null||a.size()!=3) throw new IllegalArgumentException("Expected 3-vector: "+key);
        return new GunArmAimMath.Vec(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
}
