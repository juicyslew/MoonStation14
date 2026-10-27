package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Timed, authenticated capture/cross use for items with resolved BotanySwab capability. */
final class BotanySwabHandUseService {
    private static final String KIND = "botany-swab";
    private BotanySwabHandUseService() { }

    enum PlanKind { CAPTURE, CROSS, NOOP }
    record CompletionPlan(PlanKind kind, PlantSeedReference swabAfter, PlantSeedReference trayAfter) { }

    /** Reads references only when called at completion; no start-time genome is retained. */
    static CompletionPlan prepareCurrent(PlantSeedReference currentSwab, PlantSeedReference currentTray,
                                         java.util.function.DoubleSupplier random) {
        if (currentSwab == null)
            return new CompletionPlan(PlanKind.CAPTURE, currentTray, null);
        if (currentTray == null) return new CompletionPlan(PlanKind.NOOP, currentSwab, null);
        GeneticValue crossed = PlantPollenCross.cross(currentSwab.current(), currentTray.current(), random);
        return new CompletionPlan(PlanKind.CROSS, currentTray, new PlantSeedReference(crossed, true));
    }

    static int durationTicks(double seconds) {
        return Double.isFinite(seconds) && seconds > 0 ? Math.max(1, (int) Math.ceil(seconds * 20.0)) : 0;
    }

    /** Existing hand publication batch is held until the prevalidated domain replacement has run. */
    static BodyHeldItemMutation.Result executeCross(java.util.function.BooleanSupplier preflight,
            java.util.function.Supplier<BodyHeldItemMutation.Result> heldCommit, Runnable trayCommit) {
        final BodyHeldItemMutation.Result[] result = {BodyHeldItemMutation.Result.DENIED};
        try {
            LiveHandsCommitGateway.batch(() -> {
                if (!preflight.getAsBoolean()) return null;
                result[0] = heldCommit.get();
                if (result[0] != BodyHeldItemMutation.Result.SUCCESS) return null;
                trayCommit.run();
                return null;
            });
            return result[0];
        } catch (RuntimeException postCommitFailure) {
            return BodyHeldItemMutation.Result.RECOVERY_REQUIRED;
        }
    }

    static BodyBlockUseResult.Outcome start(ServerPlayer actor, BodyBlockUseRequest request,
                                             BodyHandRequestService.BlockUseAdmission admission,
                                             HydroponicsTrayBlockEntity tray, ItemStack held) {
        if (actor == null || request == null || admission == null || tray == null || held == null || held.isEmpty()
                || request.intent() != BodyBlockUseRequest.Intent.USE) return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            return BodyBlockUseResult.Outcome.DENIED;
        LiveHands hands = admission.hands();
        var profile = ToolQualities.profile(held);
        if (profile == null || profile.botanySwab() == null) return BodyBlockUseResult.Outcome.DENIED;
        var capability = profile.botanySwab();
        BlockPos target = admission.hit().pos().immutable();
        if (level.getBlockEntity(target) != tray || !tray.isLoadedValid()
                || !level.mayInteract(actor, target)) return BodyBlockUseResult.Outcome.DENIED;
        var initialState = tray.plantState();
        if (initialState == null) return BodyBlockUseResult.Outcome.DENIED;
        var placement = tray.placementIdentity();
        String hand = request.handId(), token = request.expectedToken();
        long revision = request.expectedRevision();
        ItemStack initial = held.copy();
        int ticks = durationTicks(profile.botanySwab().delaySeconds());
        java.util.function.BooleanSupplier valid = () -> valid(actor, authority, hands, hand, token, revision,
                initial, capability, level, tray, target, placement);
        var started = TimedInteractionSystem.start(body, actor, request.sequence(), KIND, placement.toString(),
                ticks, false, true, valid, () -> {
                    if (!valid.getAsBoolean()) return TimedInteractionSystem.Completion.STOP;
                    var currentHeld = hands.stackCopy(hand).orElse(null);
                    if (currentHeld == null) return TimedInteractionSystem.Completion.STOP;
                    PlantSeedReference pollen = currentHeld.get(ModDataComponents.PLANT_SEED_REFERENCE.get());
                    PlantInstance currentPlant = tray.plantState();
                    PlantSeedReference recipient = currentPlant == null ? null : currentPlant.seedReference();
                    if (pollen == null) {
                        CompletionPlan plan = prepareCurrent(null, recipient, level.getRandom()::nextDouble);
                        ItemStack after = currentHeld.copy();
                        if (plan.swabAfter() == null) after.remove(ModDataComponents.PLANT_SEED_REFERENCE.get());
                        else after.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), plan.swabAfter());
                        if (recipient == null && currentHeld.get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == null) {
                            popup(actor, "botany-swab-from", "You collect pollen from the plant.");
                            return TimedInteractionSystem.Completion.STOP;
                        }
                        var preview = BodyHeldItemMutation.preview(actor, authority, hands, hand, token,
                                revision, currentHeld, after);
                        if (preview.isEmpty()) return TimedInteractionSystem.Completion.STOP;
                        var outcome = BodyHeldItemMutation.commit(actor, authority, hands, hand, token,
                                revision, currentHeld, after);
                        if (outcome != BodyHeldItemMutation.Result.SUCCESS) return TimedInteractionSystem.Completion.STOP;
                        popup(actor, "botany-swab-from", "You collect pollen from the plant.");
                        return TimedInteractionSystem.Completion.STOP;
                    }
                    if (recipient == null || currentPlant.seed() == null) return TimedInteractionSystem.Completion.STOP;
                    CompletionPlan plan;
                    try { plan = prepareCurrent(pollen, recipient, level.getRandom()::nextDouble); }
                    catch (RuntimeException invalidGenetics) { return TimedInteractionSystem.Completion.STOP; }
                    if (plan.kind() != PlanKind.CROSS) return TimedInteractionSystem.Completion.STOP;
                    ItemStack after = currentHeld.copy();
                    after.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), plan.swabAfter());
                    if (BodyHeldItemMutation.preview(actor, authority, hands, hand, token, revision,
                            currentHeld, after).isEmpty()) return TimedInteractionSystem.Completion.STOP;
                    final BodyHeldItemMutation.Result outcome = executeCross(
                            () -> valid.getAsBoolean() && tray.placementIdentity().equals(placement)
                                    && tray.plantState() == currentPlant && currentPlant.seedReference() == recipient,
                            () -> BodyHeldItemMutation.commit(actor, authority, hands, hand, token,
                                    revision, currentHeld, after), () -> tray.commitPollen(placement, plan.trayAfter()));
                    if (outcome != BodyHeldItemMutation.Result.SUCCESS) {
                        if (outcome == BodyHeldItemMutation.Result.RECOVERY_REQUIRED)
                            popup(actor, "botany-swab-recovery", "Pollen transfer requires recovery; its final state may be incomplete.");
                        return outcome == BodyHeldItemMutation.Result.RECOVERY_REQUIRED
                                ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.DENIED;
                    }
                    popup(actor, "botany-swab-to", "You transfer pollen to the plant.");
                    return TimedInteractionSystem.Completion.STOP;
                });
        return started == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED
                ? BodyBlockUseResult.Outcome.PLACED : BodyBlockUseResult.Outcome.DENIED;
    }

    private static boolean valid(net.minecraft.server.level.ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                 LiveHands hands, String hand, String token, long revision, ItemStack initial,
                                 com.juicyslew.moonstation14.ms14.tools.ToolProfile.BotanySwab capability,
                                 ServerLevel level, HydroponicsTrayBlockEntity tray, BlockPos target,
                                 java.util.UUID placement) {
        var body = authority.body();
        return HandActorAuthority.revalidate(actor, authority) && body.level() == level
                && CharacterControlSystem.canAct(body)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && hands.compatible(body) && hands.revision() == revision && hand.equals(hands.activeHand())
                && hands.token(hand).filter(token::equals).isPresent()
                && hands.stackCopy(hand).filter(stack -> ItemStack.isSameItemSameComponents(stack, initial)
                        && stack.getCount() == initial.getCount()).isPresent()
                && ToolQualities.profile(hands.stackCopy(hand).orElse(ItemStack.EMPTY)) != null
                && capability.equals(ToolQualities.profile(hands.stackCopy(hand).orElse(ItemStack.EMPTY)).botanySwab())
                && tray.isLoadedValid() && tray.placementIdentity().equals(placement)
                && level.getBlockEntity(target) == tray && level.hasChunkAt(target)
                && level.mayInteract(actor, target)
                && com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.lineOfSight(level, body, target);
    }

    private static void popup(ServerPlayer actor, String key, String english) {
        actor.sendSystemMessage(Component.translatableWithFallback(key, english));
    }
}
