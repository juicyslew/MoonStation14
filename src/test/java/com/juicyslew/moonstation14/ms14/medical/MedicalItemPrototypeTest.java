package com.juicyslew.moonstation14.ms14.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MedicalItemPrototypeTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void bundledAncestryResolvesTypedHealingStackAndScannerComponents() {
        var resolved = PrototypeResolver.resolve(bundled(), new MedicalItemMergeStrategy());
        assertFalse(resolved.contains(id("base_item")));
        assertFalse(resolved.contains(id("base_healing_item")));
        var ointment = decode(resolved.get(id("ointment")));
        assertEquals("Small", ointment.item().size());
        assertEquals(10, ointment.stack().orElseThrow().count());
        assertEquals(10, ointment.stack().orElseThrow().maxCount());
        assertEquals(3f, ointment.healing().orElseThrow().delay());
        assertEquals(3f, ointment.healing().orElseThrow().selfPenalty());
        assertEquals(java.util.List.of("biological"), ointment.healing().orElseThrow().allowedDamageContainers());
        assertEquals(Map.of("heat", -5f, "cold", -5f, "shock", -5f, "caustic", -1.5f), ointment.healing().orElseThrow().damage());
        for (String child : java.util.List.of("ointment1", "brutepack1", "gauze1")) {
            var single = decode(resolved.get(id(child))).stack().orElseThrow();
            assertEquals(1, single.count());
            assertEquals(10, single.maxCount());
        }
        var gauze = decode(resolved.get(id("gauze"))).healing().orElseThrow();
        assertEquals(-10f, gauze.bleedModifier());
        assertEquals("moonstation14:medical/brutepack_begin", gauze.beginSound().orElseThrow().toString());
        var analyzer = decode(resolved.get(id("handheld_health_analyzer")));
        assertEquals(.8f, analyzer.healthAnalyzer().orElseThrow().scanDelay());
        assertEquals(3.5f, analyzer.healthAnalyzer().orElseThrow().scanRange());
        assertEquals(1f, analyzer.healthAnalyzer().orElseThrow().updateInterval());
        assertEquals("moonstation14:medical/healthscanner", analyzer.healthAnalyzer().orElseThrow().scanSound().orElseThrow().toString());
        assertEquals(3f, analyzer.ui().orElseThrow().range());
        assertFalse(analyzer.toggle().orElseThrow().onUse());
    }

    @Test void inheritedComponentPoliciesSupportExplicitNondefaultOverride() {
        Map<ResourceLocation, JsonObject> source = new LinkedHashMap<>();
        source.put(id("base"), json("{\"item\":{\"size\":\"Small\"},\"stack\":{\"count\":10},\"healing\":{\"damage\":{\"blunt\":-5},\"allowed_damage_containers\":[\"biological\"],\"bleed_modifier\":0,\"delay\":3,\"self_penalty\":3}}"));
        source.put(id("override"), json("{\"parent\":\"base\",\"healing\":{\"damage\":{\"caustic\":-2},\"delay\":4},\"stack\":{\"count\":1}}"));
        var item = decode(PrototypeResolver.resolve(source, new MedicalItemMergeStrategy()).get(id("override")));
        assertEquals(1, item.stack().orElseThrow().count());
        assertEquals(10, item.stack().orElseThrow().maxCount());
        assertEquals(Map.of("blunt", -5f, "caustic", -2f), item.healing().orElseThrow().damage());
        assertEquals(4f, item.healing().orElseThrow().delay());
    }

    @Test void bundledSyringeAncestryAndInjectorModeDefaultsResolve() {
        var medical = PrototypeResolver.resolve(bundled(), new MedicalItemMergeStrategy());
        var syringe = decode(medical.get(id("syringe"))).syringePolicy().orElseThrow();
        assertEquals(15f, syringe.capacityUnits());
        assertEquals("Tiny", decode(medical.get(id("syringe"))).item().size());
        assertEquals(id("syringe_draw_mode"), syringe.initialMode());
        assertEquals(java.util.List.of(id("syringe_draw_mode"), id("syringe_inject_mode")), syringe.allowedModes());
        assertEquals(5f, syringe.defaultTransferUnits());
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : java.util.List.of("base_inject_mode", "base_draw_mode", "base_syringe_mode", "syringe_inject_mode", "syringe_draw_mode")) {
            try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/injector_mode/" + name + ".json")) {
                assertNotNull(stream);
                raw.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) { throw new AssertionError(e); }
        }
        var modes = PrototypeResolver.resolve(raw, new InjectorModeMergeStrategy());
        var draw = InjectorModeData.CODEC.parse(JsonOps.INSTANCE, modes.get(id("syringe_draw_mode"))).getOrThrow(IllegalArgumentException::new);
        assertEquals(InjectorModeData.Kind.DRAW, draw.kind());
        assertEquals(java.util.List.of(5f, 10f, 15f), draw.transferAmounts());
        assertEquals(5f, draw.ignoreDelayForVolume());
        assertEquals(5f, draw.mobTimeSeconds());
        assertEquals(.1f, draw.additionalDelayPerUnitSeconds());
        assertEquals(.5f, draw.selfModifier());
    }

    @Test void syringePolicyRejectsInvalidModesAndCapacity() {
        assertTrue(MedicalItemData.CODEC.parse(JsonOps.INSTANCE, json("{\"item\":{\"size\":\"Small\"},\"syringe_policy\":{\"capacity_units\":0,\"initial_mode\":\"moonstation14:bad\",\"allowed_modes\":[\"moonstation14:other\"],\"default_transfer_units\":5,\"need_hand\":true,\"break_on_hand_change\":true,\"movement_threshold\":0.1}}" )).error().isPresent());
    }

    @Test void syringePolicyRejectsUnsupportedBehaviorFlagVariantsDuringPrototypeDecode() {
        String valid = "{\"item\":{\"size\":\"Tiny\"},\"syringe_policy\":{\"capacity_units\":15,\"initial_mode\":\"moonstation14:draw\",\"allowed_modes\":[\"moonstation14:draw\",\"moonstation14:inject\"],\"default_transfer_units\":5,\"need_hand\":true,\"break_on_hand_change\":true,\"movement_threshold\":0.1,\"ignore_mobs\":false,\"ignore_closed\":true}}";
        assertTrue(MedicalItemData.CODEC.parse(JsonOps.INSTANCE, json(valid)).result().isPresent());
        for (var flag : Map.of("need_hand", false, "break_on_hand_change", false,
                "ignore_mobs", true, "ignore_closed", false).entrySet()) {
            JsonObject variant = json(valid).deepCopy();
            variant.getAsJsonObject("syringe_policy").addProperty(flag.getKey(), flag.getValue());
            assertThrows(IllegalArgumentException.class,
                    () -> MedicalItemData.CODEC.parse(JsonOps.INSTANCE, variant), flag.getKey());
        }
    }

    @Test void creativeInitialStackCountComesFromResolvedPrototype() {
        var resolved = PrototypeResolver.resolve(bundled(), new MedicalItemMergeStrategy());
        var catalog = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(
                resolved.asMap().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> decode(entry.getValue()))));
        var normal = ModMedicalItems.applyInitialCount(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.PAPER), catalog, id("ointment"));
        var single = ModMedicalItems.applyInitialCount(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.PAPER), catalog, id("ointment1"));
        var analyzer = ModMedicalItems.applyInitialCount(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.PAPER), catalog, id("handheld_health_analyzer"));
        assertEquals(10, normal.getCount());
        assertEquals(1, single.getCount());
        assertEquals(1, analyzer.getCount());
        assertEquals(1, analyzer.getMaxStackSize());
    }

    @Test void resolvedMaximumStackSizeIsAppliedToNativeStackAndIndependentOfInitialCount() {
        var source = new LinkedHashMap<ResourceLocation, JsonObject>();
        source.put(id("root"), json("{\"item\":{\"size\":\"Small\"},\"stack\":{\"count\":1,\"max_count\":7}}"));
        source.put(id("child"), json("{\"parent\":\"moonstation14:root\",\"stack\":{\"count\":5}}"));
        var resolved = PrototypeResolver.resolve(source, new MedicalItemMergeStrategy());
        var data = decode(resolved.get(id("child")));
        assertEquals(5, data.stack().orElseThrow().count());
        assertEquals(7, data.stack().orElseThrow().maxCount());
        var catalog = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(Map.of(id("child"), data));
        var stack = ModMedicalItems.applyInitialCount(new net.minecraft.world.item.ItemStack(
                com.juicyslew.moonstation14.item.ModItems.OINTMENT.get()), catalog, id("child"));
        assertEquals(5, stack.getCount());
        assertEquals(7, stack.getMaxStackSize());
    }

    @Test void creativeTabDispatchUsesBootstrappedResolvedCountsForNativeMedicalItems() {
        ModMedicalItems.bootstrapClientBundled();
        ModInjectorModes.bootstrapClientBundled();
        assertEquals(15f, com.juicyslew.moonstation14.item.ModItems.SYRINGE.get().getCapacity());
        assertEquals(1, ModMedicalItems.createCreativeStack(
                com.juicyslew.moonstation14.item.ModItems.OINTMENT.get()).getCount());
        assertEquals(1, ModMedicalItems.createCreativeStack(
                com.juicyslew.moonstation14.item.ModItems.BRUTEPACK.get()).getCount());
        assertEquals(1, ModMedicalItems.createCreativeStack(
                com.juicyslew.moonstation14.item.ModItems.GAUZE.get()).getCount());
        assertEquals(1, ModMedicalItems.createCreativeStack(
                com.juicyslew.moonstation14.item.ModItems.HANDHELD_HEALTH_ANALYZER.get()).getCount());
        var syringe = ModMedicalItems.createCreativeStack(com.juicyslew.moonstation14.item.ModItems.SYRINGE.get());
        assertEquals(1, syringe.getCount());
        assertEquals(id("syringe_draw_mode"), syringe.get(com.juicyslew.moonstation14.component.ModDataComponents.SYRINGE.get()).mode());
        assertEquals(5f, syringe.get(com.juicyslew.moonstation14.component.ModDataComponents.SYRINGE.get()).transferAmountUnits());
        assertEquals(1, ModMedicalItems.createCreativeStack(
                com.juicyslew.moonstation14.item.ModItems.STEEL.get()).getCount());
    }

    @Test void syringeFactoryUsesTheExplicitModeCatalogAndRejectsMissingInitialMode() {
        var medical = decode(PrototypeResolver.resolve(bundled(), new MedicalItemMergeStrategy()).get(id("syringe")));
        var policy = medical.syringePolicy().orElseThrow();
        var client = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(Map.of(
                id("syringe_draw_mode"), mode(InjectorModeData.Kind.DRAW, 5f)));
        var server = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(Map.of(
                id("syringe_draw_mode"), mode(InjectorModeData.Kind.DRAW, 10f)));
        assertEquals(5f, ModMedicalItems.initialSyringeData(policy, client).transferAmountUnits());
        assertEquals(10f, ModMedicalItems.initialSyringeData(policy, server).transferAmountUnits());
        assertThrows(IllegalStateException.class, () -> ModMedicalItems.initialSyringeData(policy,
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(Map.of())));
    }

    private static InjectorModeData mode(InjectorModeData.Kind kind, float amount) {
        return new InjectorModeData(kind, java.util.List.of(amount), amount, 5f, 5f, .1f, .5f, .5f);
    }

    @Test void invalidDamageAndPolicyCompositionsFailClosed() {
        assertTrue(MedicalItemData.CODEC.parse(JsonOps.INSTANCE, json("{\"stack\":{\"count\":1}}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.HealingPolicy(Map.of("slashing", -1f), java.util.List.of("biological"), 0, 3, 3, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.HealthAnalyzerPolicy(.8f, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.HealthAnalyzerPolicy(.8f, 2.5f, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData(new MedicalItemData.Item("Small"), Optional.empty(), Optional.empty(), Optional.of(new MedicalItemData.HealthAnalyzerPolicy(.8f, 2.5f, 1)), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MedicalItemData.HealingPolicy(Map.of("slash", 1f), java.util.List.of("biological"), 0, 3, 3, Optional.empty(), Optional.empty()));
    }

    private static Map<ResourceLocation, JsonObject> bundled() {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (String name : java.util.List.of("base_item", "base_healing_item", "ointment", "ointment1", "brutepack", "brutepack1", "gauze", "gauze1", "handheld_health_analyzer", "base_syringe", "syringe")) {
            String path = "/data/moonstation14/moonstation14/medical_item/" + name + ".json";
            try (var stream = MedicalItemPrototypeTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                result.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) { throw new AssertionError("Could not load " + path, e); }
        }
        return result;
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static MedicalItemData decode(JsonObject value) { return MedicalItemData.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow(IllegalArgumentException::new); }
}
