package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.MedicalItemData;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.medical.SyringeStackData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Authenticated selection controls for a held syringe; never performs a reagent transfer. */
public final class SyringeModeService {
    private SyringeModeService() { }

    public static BodyHeldItemMutation.Result activate(ServerPlayer actor, String handId, String token,
                                                        long revision, long sequence) {
        return mutate(actor, handId, token, revision, sequence, false);
    }

    public static BodyHeldItemMutation.Result cycleDose(ServerPlayer actor, String handId, String token,
                                                         long revision, long sequence) {
        return mutate(actor, handId, token, revision, sequence, true);
    }

    /** Pure selection helper shared with focused tests; choices are resolved prototype choices. */
    public static ResourceLocation nextMode(List<ResourceLocation> allowed, ResourceLocation current) {
        if (allowed == null || allowed.isEmpty() || current == null || !allowed.contains(current)) return null;
        return allowed.get((allowed.indexOf(current) + 1) % allowed.size());
    }

    public static Float nextDose(List<Float> choices, float current) {
        if (choices == null || choices.isEmpty() || !Float.isFinite(current)) return null;
        int index = choices.indexOf(current);
        return choices.get((index + 1) % choices.size());
    }

    private static BodyHeldItemMutation.Result mutate(ServerPlayer actor, String handId, String token,
            long revision, long sequence, boolean doseOnly) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || handId == null || token == null) return BodyHeldItemMutation.Result.DENIED;
        var body = authority.body();
        if (!CharacterControlSystem.canAct(body)) return BodyHeldItemMutation.Result.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != revision
                || !handId.equals(hands.activeHand()) || hands.token(handId).filter(token::equals).isEmpty())
            return BodyHeldItemMutation.Result.DENIED;
        ItemStack before = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        if (before.isEmpty() || before.getCount() != 1 || !(before.getItem() instanceof SyringeItem))
            return BodyHeldItemMutation.Result.DENIED;
        var policy = ModMedicalItems.require(ModMedicalItems.catalog(body.level()), SyringeItem.PROTOTYPE_ID)
                .syringePolicy().orElse(null);
        if (policy == null) return BodyHeldItemMutation.Result.DENIED;
        var modes = PrototypeRuntime.serverInjectorModes();
        SyringeStackData data = before.get(ModDataComponents.SYRINGE.get());
        if (data == null) {
            var initial = modes.get(policy.initialMode());
            if (initial == null || !policy.allowedModes().contains(policy.initialMode())) return BodyHeldItemMutation.Result.DENIED;
            float initialDose = initial.transferAmounts().contains(policy.defaultTransferUnits())
                    ? policy.defaultTransferUnits() : initial.transferAmounts().get(0);
            data = new SyringeStackData(policy.initialMode(), initialDose);
        }
        InjectorModeData current = modes.get(data.mode());
        if (current == null || !policy.allowedModes().contains(data.mode())
                || !current.transferAmounts().contains(data.transferAmountUnits())) return BodyHeldItemMutation.Result.DENIED;
        ResourceLocation selectedMode = data.mode();
        float selectedDose;
        if (doseOnly) {
            Float next = nextDose(current.transferAmounts(), data.transferAmountUnits());
            if (next == null) return BodyHeldItemMutation.Result.DENIED;
            selectedDose = next;
        } else {
            selectedMode = nextMode(policy.allowedModes(), data.mode());
            if (selectedMode == null) return BodyHeldItemMutation.Result.DENIED;
            InjectorModeData selected = modes.get(selectedMode);
            if (selected == null || selected.transferAmounts().isEmpty()) return BodyHeldItemMutation.Result.DENIED;
            selectedDose = selected.transferAmounts().contains(data.transferAmountUnits()) ? data.transferAmountUnits()
                    : selected.transferAmounts().contains(policy.defaultTransferUnits()) ? policy.defaultTransferUnits()
                    : selected.transferAmounts().get(0);
        }
        ItemStack after = before.copy();
        after.set(ModDataComponents.SYRINGE.get(), new SyringeStackData(selectedMode, selectedDose));
        BodyHeldItemMutation.Result result = BodyHeldItemMutation.commit(actor, authority, hands, handId, token, revision, before, after);
        if (result == BodyHeldItemMutation.Result.SUCCESS) {
            InjectorModeData selected = modes.get(selectedMode);
            String modeKey = selected.kind() == InjectorModeData.Kind.DRAW
                    ? "message.moonstation14.syringe.mode.draw" : "message.moonstation14.syringe.mode.inject";
            actor.displayClientMessage(Component.translatable("message.moonstation14.syringe.selection",
                    Component.translatable(modeKey), selectedDose), true);
        }
        return result;
    }
}
