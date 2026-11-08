package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterPrototypeTest {
    private static JsonObject load(String path) throws Exception {
        try (var in = new InputStreamReader(ChemMasterPrototypeTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    @Test void inheritedReceiverDefaultsAndChildLoadResolve() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ModChemMasters.BASE_ID, load("/data/moonstation14/moonstation14/chemmaster/base.json"));
        raw.put(ModChemMasters.CHEM_MASTER_ID, load("/data/moonstation14/moonstation14/chemmaster/chemmaster.json"));
        var defaultsId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_chemmaster_defaults");
        var defaults = new JsonObject(); defaults.addProperty("id", "fixture_chemmaster_defaults"); defaults.addProperty("parent", "base");
        raw.put(defaultsId, defaults);
        var uiOverrideId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_chemmaster_ui_override");
        var uiOverride = new JsonObject(); uiOverride.addProperty("id", "fixture_chemmaster_ui_override");
        uiOverride.addProperty("parent", "chemmaster"); uiOverride.addProperty("uiRange", 3.25f); raw.put(uiOverrideId, uiOverride);
        var fixtureId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_chemmaster");
        var child = new JsonObject(); child.addProperty("id", "fixture_chemmaster"); child.addProperty("parent", "chemmaster");
        child.add("inputSlot", JsonParser.parseString("{\"fitsInDispenser\":false}")); raw.put(fixtureId, child);
        var noDumpId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_chemmaster_no_dump");
        var noDump = new JsonObject(); noDump.addProperty("id", "fixture_chemmaster_no_dump");
        noDump.addProperty("parent", "chemmaster");
        noDump.add("dumpableSolution", JsonParser.parseString("{\"enabled\":false}")); raw.put(noDumpId, noDump);
        var finiteId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_chemmaster_finite_dump");
        var finite = new JsonObject(); finite.addProperty("id", "fixture_chemmaster_finite_dump");
        finite.addProperty("parent", "chemmaster");
        finite.add("dumpableSolution", JsonParser.parseString("{\"unlimited\":false}")); raw.put(finiteId, finite);
        var manager = new PrototypeManager(); manager.register(ModChemMasters.TYPE); manager.reload(ModChemMasters.TYPE, raw);
        var base = manager.snapshot(ModChemMasters.TYPE).get(defaultsId);
        var machine = manager.snapshot(ModChemMasters.TYPE).get(ModChemMasters.CHEM_MASTER_ID);
        var derived = manager.snapshot(ModChemMasters.TYPE).get(fixtureId);
        assertEquals(1000, base.powerLoadWatts()); assertEquals(3, base.cableReach());
        assertEquals(2f, base.uiRange());
        assertTrue(base.inputSlot().fitsInDispenser());
        assertEquals(0, base.pillDosageLimit(), "generic/default machines do not inherit ChemMaster's required component value");
        assertTrue(base.outputSlot().acceptedProfiles().isEmpty(), "output policy defaults disabled");
        assertEquals(250, machine.powerLoadWatts()); assertEquals(3, machine.cableReach());
        assertEquals(2f, machine.uiRange());
        assertTrue(machine.inputSlot().fitsInDispenser());
        assertEquals(20, machine.pillDosageLimit());
        var bottleProfile = ResourceLocation.fromNamespaceAndPath("moonstation14", "bottle");
        var canisterProfile = ResourceLocation.fromNamespaceAndPath("moonstation14", "pill_canister");
        assertTrue(machine.outputSlot().accepts(bottleProfile));
        assertTrue(machine.outputSlot().accepts(canisterProfile));
        assertFalse(machine.outputSlot().accepts(ResourceLocation.fromNamespaceAndPath("moonstation14", "pill")));
        assertTrue(derived.outputSlot().accepts(bottleProfile), "child inherits resolved output allowlist");
        assertTrue(machine.dumpableSolution().enabled()); assertEquals("buffer", machine.dumpableSolution().solutionName());
        assertTrue(machine.dumpableSolution().unlimited());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "chemistry/chemmaster/click"), machine.clickSound());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "chemistry/chemmaster/insert"), machine.slotInsertSound());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "chemistry/chemmaster/eject"), machine.slotEjectSound());
        assertEquals(250, ChemMasterBlockEntity.powerLoadWattsFromResolved(machine));
        assertEquals(3, ChemMasterBlockEntity.cableReachFromResolved(machine));
        assertTrue(ChemMasterBlockEntity.acceptsResolvedSlotPolicy(machine, true));
        assertFalse(ChemMasterBlockEntity.acceptsResolvedSlotPolicy(machine, false));
        assertEquals(250, derived.powerLoadWatts()); assertFalse(derived.inputSlot().fitsInDispenser());
        assertFalse(ChemMasterBlockEntity.acceptsResolvedSlotPolicy(derived, true));
        assertEquals(2f, derived.uiRange());
        assertTrue(derived.dumpableSolution().enabled());
        assertFalse(manager.snapshot(ModChemMasters.TYPE).get(noDumpId).dumpableSolution().enabled());
        assertTrue(manager.snapshot(ModChemMasters.TYPE).get(noDumpId).dumpableSolution().unlimited(),
                "disabled field-aware child retains inherited unlimited; disablement does not erase sibling fields");
        var disabledPolicy = manager.snapshot(ModChemMasters.TYPE).get(noDumpId).dumpableSolution();
        var finitePolicy = manager.snapshot(ModChemMasters.TYPE).get(finiteId).dumpableSolution();
        assertTrue(finitePolicy.enabled());
        assertFalse(finitePolicy.unlimited(), "explicit child override can disable inherited unlimited behavior");
        assertEquals(machine.clickSound(), derived.clickSound());
        assertEquals(3.25f, manager.snapshot(ModChemMasters.TYPE).get(uiOverrideId).uiRange());
    }
}
