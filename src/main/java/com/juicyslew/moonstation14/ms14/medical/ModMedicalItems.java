package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.item.ModItems;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** Narrow medical item prototype family. */
public final class ModMedicalItems {
    public static final PrototypeType<MedicalItemData> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"medical_item"), "moonstation14/medical_item", MedicalItemData.CODEC, new MedicalItemMergeStrategy());
    private ModMedicalItems() {}
    public static PrototypeCatalog<MedicalItemData> catalog(Level level) { Objects.requireNonNull(level); return level.isClientSide() ? PrototypeRuntime.clientMedicalItems() : PrototypeRuntime.serverMedicalItems(); }
    public static PrototypeCatalog<MedicalItemData> catalogForSide(boolean client) { return client ? PrototypeRuntime.clientMedicalItems() : PrototypeRuntime.serverMedicalItems(); }
    public static MedicalItemData require(PrototypeCatalog<MedicalItemData> catalog, ResourceLocation id) { var d=Objects.requireNonNull(catalog).get(Objects.requireNonNull(id)); if(d==null) throw new IllegalArgumentException("Missing medical item '"+id+"'"); return d; }

    /** Creates the resolved prototype's initial quantity without changing global ItemStack construction semantics. */
    /** Creates a stack using a matching resolved medical and injector-mode catalog pair. */
    public static ItemStack createStack(PrototypeCatalog<MedicalItemData> catalog,
                                        PrototypeCatalog<InjectorModeData> injectorModes,
                                        ResourceLocation prototypeId) {
        MedicalItemData data = require(catalog, prototypeId);
        var item = switch (prototypeId.getPath()) {
            case "ointment", "ointment1" -> ModItems.OINTMENT.get();
            case "brutepack", "brutepack1" -> ModItems.BRUTEPACK.get();
            case "gauze", "gauze1" -> ModItems.GAUZE.get();
            case "handheld_health_analyzer" -> ModItems.HANDHELD_HEALTH_ANALYZER.get();
            case "syringe" -> ModItems.SYRINGE.get();
            default -> throw new IllegalArgumentException("No registered Minecraft item mapping for medical prototype '" + prototypeId + "'");
        };
        ItemStack stack = applyInitialCount(new ItemStack(item), catalog, prototypeId);
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,
                data.stack().map(MedicalItemData.Stack::maxCount).orElse(10));
        if (prototypeId.equals(id("syringe"))) {
            var policy = data.syringePolicy().orElseThrow(() -> new IllegalStateException("Syringe prototype has no policy"));
            var syringeData = initialSyringeData(policy, injectorModes);
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.SYRINGE.get(),
                    syringeData);
        }
        return stack;
    }

    public static SyringeStackData initialSyringeData(MedicalItemData.SyringePolicy policy,
                                                       PrototypeCatalog<InjectorModeData> injectorModes) {
        var mode = Objects.requireNonNull(injectorModes, "injectorModes").get(policy.initialMode());
        if (mode == null) throw new IllegalStateException("Missing syringe mode " + policy.initialMode());
        float dose = mode.transferAmounts().contains(policy.defaultTransferUnits())
                ? policy.defaultTransferUnits() : mode.transferAmounts().get(0);
        return new SyringeStackData(policy.initialMode(), dose);
    }

    public static ItemStack applyInitialCount(ItemStack stack, PrototypeCatalog<MedicalItemData> catalog,
                                               ResourceLocation prototypeId) {
        MedicalItemData data = require(catalog, prototypeId);
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,
                data.stack().map(MedicalItemData.Stack::maxCount).orElse(10));
        stack.setCount(data.stack().map(MedicalItemData.Stack::count).orElse(1));
        return stack;
    }

    /** Creative-tab adapter: only medical items use prototype-defined initial counts. */
    public static ItemStack createCreativeStack(net.minecraft.world.level.ItemLike item) {
        var nativeItem = item.asItem();
        ResourceLocation id = nativeItem == ModItems.OINTMENT.get() ? id("ointment")
                : nativeItem == ModItems.BRUTEPACK.get() ? id("brutepack")
                : nativeItem == ModItems.GAUZE.get() ? id("gauze")
                : nativeItem == ModItems.HANDHELD_HEALTH_ANALYZER.get() ? id("handheld_health_analyzer") : null;
        if (nativeItem == ModItems.SYRINGE.get()) id = id("syringe");
        ItemStack displayStack = id == null ? new ItemStack(item) : createStack(PrototypeRuntime.clientMedicalItems(),
                PrototypeRuntime.clientInjectorModes(), id);
        if (displayStack.isEmpty()) {
            throw new IllegalStateException("Creative tab item adapter produced an empty display stack for " + nativeItem);
        }
        // Minecraft's creative-tab output contract requires one item per displayed entry.
        // Keep prototype quantities in createStack for gameplay; only normalize this UI copy.
        displayStack.setCount(1);
        return displayStack;
    }

    /** Seed the client catalog from the same bundled prototypes until authoritative reload sync arrives. */
    public static void bootstrapClientBundled() {
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : java.util.List.of("base_item", "base_healing_item", "base_syringe", "ointment", "ointment1",
                "brutepack", "brutepack1", "gauze", "gauze1", "handheld_health_analyzer")) {
            String path = "/data/moonstation14/moonstation14/medical_item/" + name + ".json";
            try (var stream = ModMedicalItems.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing bundled medical prototype " + path);
                raw.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read bundled medical prototype " + path, exception);
            }
        }
        String syringePath = "/data/moonstation14/moonstation14/medical_item/syringe.json";
        try (var stream = ModMedicalItems.class.getResourceAsStream(syringePath)) {
            if (stream == null) throw new IllegalStateException("Missing bundled medical prototype " + syringePath);
            raw.put(id("syringe"), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read bundled medical prototype " + syringePath, exception);
        }
        PrototypeRuntime.clientManager().reload(TYPE, raw);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }
}

