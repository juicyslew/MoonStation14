package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.component.ModDataComponents;

/** Pure staging policy for revolver world-use transitions. */
public final class GunWorldUsePolicy {
    private GunWorldUsePolicy() { }

    public static ItemStack prepareSpin(GunProfile profile, ItemStack source, int index) {
        if (profile == null || source == null || source.isEmpty() || profile.revolverAmmoProvider().isEmpty()) return ItemStack.EMPTY;
        var provider = profile.revolverAmmoProvider().orElseThrow();
        var state = source.get(ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (provider.capacity() != state.chambers().size() || index < 0 || index >= provider.capacity()) return ItemStack.EMPTY;
        ItemStack staged = source.copy();
        staged.set(ModDataComponents.REVOLVER_STATE.get(), new RevolverState(index, state.chambers()));
        return staged;
    }

    public record InsertPlan(boolean accepted, ItemStack gun, RevolverProviderKernel.InsertFailure failure) {
        public InsertPlan { gun = gun == null ? ItemStack.EMPTY : gun.copy(); }
        @Override public ItemStack gun() { return gun.copy(); }
    }

    /** Stage one registered physical cartridge into a detached revolver copy. */
    public static InsertPlan prepareInsert(GunProfile profile, ItemStack targetGun, ItemStack cartridge) {
        return prepareInsert(profile, targetGun, cartridge, ModCartridgeProfiles.serverCatalog().asMap());
    }

    static InsertPlan prepareInsert(GunProfile profile, ItemStack targetGun, ItemStack cartridge,
                                    java.util.Map<net.minecraft.resources.ResourceLocation, CartridgeProfile> catalog) {
        if (profile == null || targetGun == null || targetGun.isEmpty() || cartridge == null
                || cartridge.isEmpty() || cartridge.getCount() != 1 || profile.revolverAmmoProvider().isEmpty())
            return new InsertPlan(false, targetGun, RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
        var provider = profile.revolverAmmoProvider().orElseThrow();
        var state = targetGun.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (state.chambers().size() != provider.capacity())
            return new InsertPlan(false, targetGun, RevolverProviderKernel.InsertFailure.FULL);
        var result = RevolverProviderKernel.insertOne(state, profile, cartridge.copy(), catalog);
        if (!result.inserted()) return new InsertPlan(false, targetGun, result.failure());
        ItemStack staged = targetGun.copy();
        staged.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), result.state());
        return new InsertPlan(true, staged, null);
    }

    public record EjectPlan(ItemStack gun, java.util.List<ItemStack> outputs) {
        public EjectPlan { outputs = java.util.List.copyOf(outputs); }
    }

    public static EjectPlan prepareEmpty(GunProfile profile, ItemStack source) {
        if (profile == null || source == null || source.isEmpty() || profile.revolverAmmoProvider().isEmpty()) return null;
        var state = source.get(ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (state.chambers().size() != profile.revolverAmmoProvider().orElseThrow().capacity()) return null;
        var ejected = RevolverProviderKernel.ejectAll(state, profile);
        var outputs = new java.util.ArrayList<ItemStack>();
        for (var round : ejected.ejected()) {
            var id = round.physical().map(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()))
                    .orElse(round.deferredPrototype().orElse(null));
            ItemStack output = round.physical().map(ItemStack::copy).orElseGet(() -> id == null ? null : GunFireSystem.materializeCartridge(id, round.spent()));
            if (output == null || output.isEmpty()) return null;
            outputs.add(output);
        }
        ItemStack staged = source.copy();
        staged.set(ModDataComponents.REVOLVER_STATE.get(), ejected.state());
        return new EjectPlan(staged, outputs);
    }
}
