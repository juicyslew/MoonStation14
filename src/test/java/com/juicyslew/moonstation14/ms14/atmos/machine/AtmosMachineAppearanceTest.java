package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineAppearanceTest {
    private static AtmosMachineProfile profile(String name) {
        var manager = new PrototypeManager(); manager.register(ModAtmosMachines.TYPE);
        String[] ids = {"atmos_machine_base","atmos_machine_base_powered","constructible_machine_base","gas_pipe_sans_layers","gas_pipe_base","gas_binary_base","gas_trinary_base","gas_thermo_base","teg_center","teg_circulator","gas_pressure_pump","gas_volume_pump","gas_valve","gas_mixer","gas_filter","gas_thermo_heater","gas_thermo_freezer","passive_heat_exchanger"};
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String id : ids) try (var in = AtmosMachineAppearanceTest.class.getResourceAsStream("/data/moonstation14/moonstation14/atmos_machine/"+id+".json")) {
            raw.put(ModAtmosMachines.id(id), JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) { throw new RuntimeException(e); }
        manager.reload(ModAtmosMachines.TYPE, raw);
        return manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id(name));
    }
    @Test void exactNativeAppearanceSelections() {
        var pump = profile("gas_pressure_pump");
        assertEquals("pumpPressureOn", AtmosMachineAppearance.project(pump,true,true,false,TegSupplyPolicy.State.zero()));
        assertEquals("pumpPressure", AtmosMachineAppearance.project(pump,false,true,false,TegSupplyPolicy.State.zero()));
        var mixer = profile("gas_mixer");
        assertEquals("gasMixerOn", AtmosMachineAppearance.project(mixer,false,true,false,TegSupplyPolicy.State.zero()));
        assertEquals("pumpManualValveOn", AtmosMachineAppearance.project(profile("gas_valve"),false,true,false,TegSupplyPolicy.State.zero()));
        assertEquals("heaterOff", AtmosMachineAppearance.project(profile("gas_thermo_heater"),false,true,false,TegSupplyPolicy.State.zero()));
        assertEquals("heaterOn", AtmosMachineAppearance.project(profile("gas_thermo_heater"),true,false,false,TegSupplyPolicy.State.zero()));
        var volume=profile("gas_volume_pump");
        assertEquals("pumpVolumeBlocked", AtmosMachineAppearance.project(volume,true,true,true,TegSupplyPolicy.State.zero()));
        assertEquals("pumpVolume", AtmosMachineAppearance.project(volume,true,false,false,TegSupplyPolicy.State.zero()));
        String offResource = AtmosMachineAppearance.project(volume,false,true,false,TegSupplyPolicy.State.zero());
        assertEquals("pumpVolume", offResource);
        assertEquals(15, AtmosMachineAppearance.nativeDisplay(volume,offResource,false));
    }
    @Test void tegRampBoundariesAndCeilingUseTypedThresholds() {
        var p=profile("teg_center");
        assertEquals(0, AtmosMachineAppearance.tegLevel(true,new TegSupplyPolicy.State(5000,0),p.teg().minimumVisualWatts(),p.teg().maximumVisualWatts()));
        assertEquals(1, AtmosMachineAppearance.tegLevel(true,new TegSupplyPolicy.State(5001,0),p.teg().minimumVisualWatts(),p.teg().maximumVisualWatts()));
        assertEquals(11, AtmosMachineAppearance.tegLevel(true,new TegSupplyPolicy.State(200000,0),p.teg().minimumVisualWatts(),p.teg().maximumVisualWatts()));
        assertEquals(0, AtmosMachineAppearance.tegLevel(false,new TegSupplyPolicy.State(200000,0),p.teg().minimumVisualWatts(),p.teg().maximumVisualWatts()));
    }
    @Test void circulatorUsesResolvedFastThresholdAndStrictSpeedBoundary() {
        var circ=profile("teg_circulator");
        assertEquals(506.625,circ.visual().circulatorFastDeltaKpa());
        assertEquals(26,AtmosMachineAppearance.circulatorDisplay(false,900,2,circ.visual().circulatorFastDeltaKpa()));
        assertEquals(27,AtmosMachineAppearance.circulatorDisplay(true,0,0,circ.visual().circulatorFastDeltaKpa()));
        assertEquals(28,AtmosMachineAppearance.circulatorDisplay(true,506.625,2,circ.visual().circulatorFastDeltaKpa()));
        assertEquals(29,AtmosMachineAppearance.circulatorDisplay(true,506.626,2,circ.visual().circulatorFastDeltaKpa()));
        assertEquals(27,AtmosMachineAppearance.circulatorDisplay(true,900,0,circ.visual().circulatorFastDeltaKpa()));
        var custom = AtmosMachineProfile.Visual.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"sprite\":\"teg\",\"state\":\"circ-0\",\"circulatorFastDeltaKpa\":700}"))
                .getOrThrow();
        assertEquals(700,custom.circulatorFastDeltaKpa());
    }
}
