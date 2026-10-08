package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.medical.SyringeStackData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** SS14 injector post-transfer mode changes, applied to the staged stack before its owning commit. */
public final class SyringeTransferModeTransition {
    private SyringeTransferModeTransition() { }

    public static boolean apply(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SyringeItem syringe)) return true;
        var policy = ModMedicalItems.require(ModMedicalItems.catalog(level), SyringeItem.PROTOTYPE_ID)
                .syringePolicy().orElse(null);
        if (policy == null) return false;
        var modes = level.isClientSide() ? PrototypeRuntime.clientInjectorModes() : PrototypeRuntime.serverInjectorModes();
        var data = syringe.dataOrInitial(stack, level);
        var current = modes.get(data.mode());
        if (current == null || !policy.allowedModes().contains(data.mode())) return false;
        var bridge = ReagentSystem.bridgeForHolder(stack);
        if (bridge == null) return false;
        var contents = ReagentUnits.fromMap(com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(stack, bridge).getMap());
        long total = ReagentUnits.total(contents.values());
        long capacity = ReagentUnits.fromFloat(policy.capacityUnits());
        SyringeStackData next = nextAfterTransfer(policy, modes.asMap(), data, total, capacity);
        if (next != data) stack.set(ModDataComponents.SYRINGE.get(), next);
        return true;
    }

    /** Pure resolved-prototype transition, used by both the owned transfer commit and unit coverage. */
    public static SyringeStackData nextAfterTransfer(com.juicyslew.moonstation14.ms14.medical.MedicalItemData.SyringePolicy policy,
            java.util.Map<net.minecraft.resources.ResourceLocation, InjectorModeData> modes,
            SyringeStackData data, long totalCents, long capacityCents) {
        if (policy == null || modes == null || data == null || totalCents < 0 || capacityCents <= 0)
            throw new IllegalArgumentException("invalid syringe transfer state");
        InjectorModeData current = modes.get(data.mode());
        if (current == null || !policy.allowedModes().contains(data.mode()))
            throw new IllegalArgumentException("unresolved syringe mode");
        boolean transition = current.kind() == InjectorModeData.Kind.DRAW && totalCents >= capacityCents
                || current.kind() == InjectorModeData.Kind.INJECT && totalCents == 0;
        if (!transition) return data;
        InjectorModeData.Kind targetKind = current.kind() == InjectorModeData.Kind.DRAW
                ? InjectorModeData.Kind.INJECT : InjectorModeData.Kind.DRAW;
        var targetId = policy.allowedModes().stream().filter(id -> {
            var mode = modes.get(id);
            return mode != null && mode.kind() == targetKind;
        }).findFirst().orElse(null);
        if (targetId == null) return data;
        var target = modes.get(targetId);
        float dose = target.transferAmounts().contains(data.transferAmountUnits()) ? data.transferAmountUnits()
                : target.transferAmounts().contains(policy.defaultTransferUnits()) ? policy.defaultTransferUnits()
                : target.transferAmounts().get(0);
        return new SyringeStackData(targetId, dose);
    }
}
