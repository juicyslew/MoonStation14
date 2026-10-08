package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.medical.SyringeStackData;
import com.juicyslew.moonstation14.ms14.medical.ModInjectorModes;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Empty, single-use-stack syringe vessel. Transfer behavior is supplied by medical prototypes. */
public final class SyringeItem extends Item implements IReagentTrait {
    public static final ResourceLocation PROTOTYPE_ID = id("syringe");
    public SyringeItem(Properties properties) { super(properties); }

    /** Compatibility entry point backed by the client resolved catalog; live consumers should pass their level. */
    @Override public float getCapacity() {
        return ModMedicalItems.require(PrototypeRuntime.clientMedicalItems(), PROTOTYPE_ID)
                .syringePolicy().orElseThrow(() -> new IllegalStateException("Syringe prototype has no syringe policy"))
                .capacityUnits();
    }

    @Override public float getCapacity(ItemStack stack, Level level) {
        return ModMedicalItems.require(ModMedicalItems.catalog(level), PROTOTYPE_ID)
                .syringePolicy().orElseThrow(() -> new IllegalStateException("Syringe prototype has no syringe policy"))
                .capacityUnits();
    }

    public static SyringeStackData initialData(Level level) {
        var policy = ModMedicalItems.require(ModMedicalItems.catalog(level), PROTOTYPE_ID)
                .syringePolicy().orElseThrow(() -> new IllegalStateException("Syringe prototype has no syringe policy"));
        var modes = level.isClientSide() ? PrototypeRuntime.clientInjectorModes() : PrototypeRuntime.serverInjectorModes();
        var mode = modes.get(policy.initialMode());
        if (mode == null) throw new IllegalStateException("Syringe initial mode is missing: " + policy.initialMode());
        float dose = mode.transferAmounts().contains(policy.defaultTransferUnits())
                ? policy.defaultTransferUnits() : mode.transferAmounts().get(0);
        return new SyringeStackData(policy.initialMode(), dose);
    }

    public SyringeStackData dataOrInitial(ItemStack stack, Level level) {
        var data = stack.get(ModDataComponents.SYRINGE.get());
        return data == null ? initialData(level) : data;
    }

    public static boolean isInjectMode(ResourceLocation modeId, Level level) {
        var catalog = level.isClientSide() ? PrototypeRuntime.clientInjectorModes() : PrototypeRuntime.serverInjectorModes();
        var mode = catalog.get(modeId);
        return mode != null && mode.kind() == com.juicyslew.moonstation14.ms14.medical.InjectorModeData.Kind.INJECT;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
}
