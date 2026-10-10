package com.juicyslew.moonstation14.ms14.power.device;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PowerAppearancePrototypeTest {
    private static JsonObject json(String name) {
        String path="/data/moonstation14/moonstation14/power_appearance/"+name+".json";
        try(var reader=new InputStreamReader(java.util.Objects.requireNonNull(getClassResource(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch(java.io.IOException e) { throw new RuntimeException(e); }
    }
    private static java.io.InputStream getClassResource(String path) { return PowerAppearancePrototypeTest.class.getResourceAsStream(path); }
    private static Map<ResourceLocation,JsonObject> raw() {
        Map<ResourceLocation,JsonObject> r=new LinkedHashMap<>();
        r.put(ResourceLocation.fromNamespaceAndPath("moonstation14","base_power_appearance"),json("base_power_appearance"));
        r.put(ModPowerAppearances.SOURCE,json("hv_source")); r.put(ModPowerAppearances.SUBSTATION,json("hv_mv_substation"));
        r.put(ModPowerAppearances.APC,json("apc")); return r;
    }
    @Test void resolvesInheritedDisplayDefaultsAndApcExportShape() {
        PrototypeManager manager=new PrototypeManager(); manager.register(ModPowerAppearances.TYPE);
        manager.reload(ModPowerAppearances.TYPE,raw()); var c=manager.snapshot(ModPowerAppearances.TYPE);
        assertEquals(3,c.asMap().size());
        var source=c.get(ModPowerAppearances.SOURCE); assertEquals("fixed_on",source.active()); assertEquals("charging",source.charging());
        assertEquals("full",c.get(ModPowerAppearances.SUBSTATION).full());
        assertEquals(0, source.lightLevel());
        var apc=c.get(ModPowerAppearances.APC); assertEquals("lack",apc.lack());
        assertEquals(5, apc.lightLevel());
        assertEquals(new PowerAppearanceData.Shape(4,4.5,12.7,12,11.5,16),apc.shape());
    }
}
