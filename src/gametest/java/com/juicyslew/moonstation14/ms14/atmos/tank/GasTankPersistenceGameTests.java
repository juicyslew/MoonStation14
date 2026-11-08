package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import java.util.Map;

@GameTestTags({"gas-tank"})
@PrefixGameTestTemplate(false)
public final class GasTankPersistenceGameTests {
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void actualTankStackPersistsFiniteGasAndMetadata(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = ModGasTanks.PLASMA_FILLED_ID;
        ItemStack raw = new ItemStack(ModItems.PLASMA_TANK.get());
        var rawContents = GasTankItem.contents(raw, level).orElseThrow();
        helper.assertTrue(!raw.has(ModDataComponents.GAS_TANK_CONTENTS.get()), "reading an ordinary constructor stack is nonmutating");
        helper.assertTrue(rawContents.prototypeId().equals(ModGasTanks.PLASMA_ID)
                && Math.abs(rawContents.mixture().volumeCubicMeters() - 0.005) < 1e-9
                && Math.abs(rawContents.mixture().temperatureKelvin() - 293.15) < 1e-3
                && rawContents.mixture().totalMoles() == 0, "ordinary constructor stack projects resolved empty profile");
        var overriddenCatalog = overriddenTankCatalog();
        var overriddenRead = GasTankItem.contents(raw, overriddenCatalog).orElseThrow();
        helper.assertTrue(Math.abs(overriddenRead.mixture().volumeCubicMeters() - 0.007) < 1e-9
                && Math.abs(overriddenRead.mixture().temperatureKelvin() - 300.0) < 1e-3,
                "raw-stack live projection follows overridden resolved prototype defaults");
        helper.assertTrue(!raw.has(ModDataComponents.GAS_TANK_CONTENTS.get()), "prototype override read remains nonmutating");
        ItemStack invalidProfile = raw.copy();
        invalidProfile.set(ModDataComponents.GAS_TANK_CONTENTS.get(), new GasTankContents(
                ModGasTanks.id("unregistered_profile"), rawContents.mixture()));
        helper.assertTrue(GasTankItem.contents(invalidProfile, level).isEmpty(), "stored unknown profile fails closed");
        raw.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Unmaterialized"));
        ItemStack materialized = GasTankItem.withMixture(raw, rawContents.mixture().withGasDelta(GasType.PLASMA, 0.25));
        helper.assertTrue(!raw.has(ModDataComponents.GAS_TANK_CONTENTS.get())
                && GasTankItem.contents(raw, level).orElseThrow().mixture().totalMoles() == 0,
                "copy-on-write leaves raw stack logically empty and physically unmodified");
        helper.assertTrue(materialized.has(ModDataComponents.GAS_TANK_CONTENTS.get())
                && materialized.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("Unmaterialized"),
                "gas mutation materializes data on a metadata-preserving copy");
        var rawSaved = raw.save(level.registryAccess());
        ItemStack rawLoaded = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) rawSaved);
        helper.assertTrue(!rawLoaded.has(ModDataComponents.GAS_TANK_CONTENTS.get())
                && GasTankItem.contents(rawLoaded, level).orElseThrow().mixture().totalMoles() == 0,
                "unmaterialized raw stack remains a resolved empty tank after NBT round-trip");
        var emptyDefault = ModItems.PLASMA_TANK.get().getDefaultInstance();
        helper.assertTrue(GasTankItem.contents(emptyDefault).orElseThrow().prototypeId().equals(ModGasTanks.PLASMA_ID)
                && GasTankItem.contents(emptyDefault).orElseThrow().mixture().totalMoles() == 0,
                "registered default instance uses resolved empty profile composition");
        ItemStack stack = GasTankItem.createStack(ModGasTanks.catalog(level), profile);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Test tank"));
        var original = GasTankItem.contents(stack).orElseThrow();
        GasMixture spent = original.mixture().withGasDelta(GasType.PLASMA, -0.5);
        ItemStack depleted = GasTankItem.withMixture(stack, spent);

        helper.assertTrue(stack.getCount() == 1 && stack.getMaxStackSize() == 1, "real registered tank is singleton");
        helper.assertTrue(GasTankItem.contents(stack).orElseThrow().mixture().moles(GasType.PLASMA)
                > GasTankItem.contents(depleted).orElseThrow().mixture().moles(GasType.PLASMA), "copy-on-write leaves original gas unchanged");
        helper.assertTrue(depleted.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("Test tank"), "mutation preserves stack metadata");

        var encoded = depleted.save(level.registryAccess());
        ItemStack restored = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) encoded);
        var restoredGas = GasTankItem.contents(restored).orElseThrow();
        helper.assertTrue(restored.is(ModItems.PLASMA_TANK.get()) && restored.getCount() == 1, "saved stack restores registered item and singleton count");
        helper.assertTrue(restoredGas.prototypeId().equals(profile), "saved profile identity survives");
        helper.assertTrue(Math.abs(restoredGas.mixture().moles(GasType.PLASMA) - spent.moles(GasType.PLASMA)) < 1e-6
                && Math.abs(restoredGas.mixture().volumeCubicMeters() - 0.005) < 1e-9,
                "saved finite gas, depletion and capacity survive vanilla stack serialization");
        helper.assertTrue(restored.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("Test tank"), "saved stack metadata survives");
        helper.assertTrue(new ItemStack(net.minecraft.world.item.Items.STONE).get(ModDataComponents.GAS_TANK_CONTENTS.get()) == null,
                "ordinary non-tank stacks have no gas payload");
        helper.succeed();
    }

    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<GasTankData> overriddenTankCatalog() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasTanks.TYPE);
        var raw = new LinkedHashMap<net.minecraft.resources.ResourceLocation, JsonObject>();
        for (String name : java.util.List.of("gas_tank_base", "plasma_tank", "plasma_tank_filled")) {
            String path = "/data/moonstation14/moonstation14/gas_tank/" + name + ".json";
            try (var stream = GasTankPersistenceGameTests.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing " + path);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (name.equals("gas_tank_base")) {
                    json.addProperty("volumeCubicMeters", 0.007);
                    json.addProperty("temperatureKelvin", 300.0);
                }
                raw.put(ModGasTanks.id(name), json);
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        }
        manager.reload(ModGasTanks.TYPE, raw);
        return manager.snapshot(ModGasTanks.TYPE);
    }
}
