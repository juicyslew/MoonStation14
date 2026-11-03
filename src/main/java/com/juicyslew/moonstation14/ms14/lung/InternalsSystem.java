package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankDrawHelper;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent;
import com.juicyslew.moonstation14.ms14.character.components.InternalsComponent;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment;
import com.juicyslew.moonstation14.ms14.equipment.BreathToolItemState;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Resolves a connected internals source from current, prototype-backed body equipment. */
public final class InternalsSystem {
    private InternalsSystem() { }

    /** Exact carried identity, not native item ID. Stack is returned defensively. */
    public record SourceHandle(String slot, boolean held, String token, ItemStack stack,
                               GasTankContents contents, GasTankData profile, LiveHands currentLiveHands) {
        public SourceHandle { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    /** Small semantic observation for action controls; contains no raw item/prototype identifiers. */
    public record Status(boolean connected, boolean working, boolean lowPressure, double internalPressureKpa) { }

    public static boolean toggle(LivingEntity body, String tankToken) {
        if (body == null || body.level().isClientSide || body.level().getServer() == null
                || !body.level().getServer().isSameThread()) return false;
        if (!eligibleBody(body)) { disconnect(body); return false; }
        InternalsState state = state(body);
        if (state.connected() && (tankToken == null || state.tankToken().orElseThrow().equals(tankToken))) {
            disconnect(body);
            return true;
        }
        Optional<SourceHandle> source = tankToken == null ? fallback(body) : findToken(body, tankToken);
        if (source.isEmpty()) {
            if (state.connected() && selectedTank(body).isPresent()) return false;
            disconnect(body);
            return false;
        }
        body.setData(ModDataAttachments.INTERNALS.get(), InternalsState.connected(source.orElseThrow().token()));
        return true;
    }

    public static void disconnect(LivingEntity body) {
        if (body != null) body.setData(ModDataAttachments.INTERNALS.get(), InternalsState.DISCONNECTED);
    }

    public static boolean isWorking(LivingEntity body) { return selectedTank(body).isPresent(); }
    public static boolean isConnected(LivingEntity body) { return body != null && state(body).connected(); }
    /** Provider-aware action state predicate: a different carried tank is not active. */
    public static boolean providerSelected(String connectedToken, String requestedToken) {
        return connectedToken != null && requestedToken != null && connectedToken.equals(requestedToken);
    }
    /** Resolved worn-tool capability seam; future lowered-mask state is checked by the owning item-state policy. */
    public static boolean isBreathToolFunctional(LivingEntity body) { return body != null && eligibleBody(body); }
    static boolean useTankForPhase(LungComponent.Phase phase, boolean connectedAndFunctional) {
        return phase == LungComponent.Phase.INHALING && connectedAndFunctional;
    }
    public static Status status(LivingEntity body) {
        Optional<SourceHandle> source = selectedTank(body);
        if (source.isEmpty()) return new Status(isConnected(body), false, false, 0d);
        SourceHandle value = source.orElseThrow();
        double pressure = value.contents().mixture().pressureKpa(value.profile().volumeCubicMeters());
        return new Status(true, true, pressure <= value.profile().lowPressureThresholdKpa(), pressure);
    }

    /** Shared staged draw primitive used by production transaction and pure conservation tests. */
    static Optional<GasTankDrawHelper.Draw> validatedDraw(GasTankContents contents, GasTankData profile,
            double breathVolumeLiters, double requestedMoles, Predicate<GasMixture> validator) {
        if (validator == null) return Optional.empty();
        try {
            GasTankDrawHelper.Draw draw = GasTankDrawHelper.drawOutput(contents, profile, breathVolumeLiters, requestedMoles);
            return validator.test(draw.inhaled()) ? Optional.of(draw) : Optional.empty();
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    /** Ownership/reconciliation guard shared by canonical publication and source-level tests. */
    static boolean stillSelected(SourceHandle source, LiveHands current, String selectedToken) {
        return source != null && current == source.currentLiveHands() && selectedToken != null
                && source.token().equals(selectedToken);
    }

    /** Regulated output preview; never reports tank-internal pressure as inhaled pressure. */
    public static Optional<com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource.Sample>
    sampleInhale(LivingEntity body, double breathVolumeLiters) {
        if (!Double.isFinite(breathVolumeLiters) || breathVolumeLiters <= 0) return Optional.empty();
        return selectedTank(body).map(source -> {
            GasMixture preview = GasTankDrawHelper.drawOutput(source.contents(), source.profile(), breathVolumeLiters).inhaled();
            return new com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource.Sample(preview, breathVolumeLiters / 1000d);
        });
    }

    /** Stages a bounded output draw, validates the lung candidate, then compare-and-commits exact tank ownership. */
    public static Optional<com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange> exchangeInhale(
            LivingEntity body, double requestedMoles, double breathVolumeLiters, Predicate<GasMixture> validator) {
        if (body == null || validator == null || !Double.isFinite(requestedMoles) || requestedMoles < 0
                || !Double.isFinite(breathVolumeLiters) || breathVolumeLiters <= 0 || body.level().isClientSide
                || body.level().getServer() == null || !body.level().getServer().isSameThread()) return Optional.empty();
        SourceHandle source = selectedTank(body).orElse(null);
        if (source == null) return Optional.empty();
        GasTankDrawHelper.Draw preview = validatedDraw(source.contents(), source.profile(), breathVolumeLiters,
                requestedMoles, validator).orElse(null);
        if (preview == null) return Optional.empty();
        GasMixture packet = preview.inhaled();
        if (packet.totalMoles() == 0) return Optional.of(new com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange(packet, GasMixture.vacuum()));
        LiveHands expected = source.currentLiveHands();
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (!stillSelected(source, current, state(body).tankToken().orElse(null))) return Optional.empty();
        var changed = GasTankItem.withMixture(source.stack(), preview.remaining().mixture());
        var candidate = source.held()
                ? expected.debitHeldTank(expected.revision(), source.slot(), new ItemToken(source.token()), changed)
                : expected.debitEquippedTank(expected.revision(), source.slot(), new ItemToken(source.token()), changed);
        if (candidate.isEmpty() || !LiveHandsCommitGateway.commitTrusted(body, expected, candidate.orElseThrow())) return Optional.empty();
        double beforePressure = source.contents().mixture().pressureKpa(source.profile().volumeCubicMeters());
        double afterPressure = preview.remaining().mixture().pressureKpa(source.profile().volumeCubicMeters());
        if (beforePressure > source.profile().lowPressureThresholdKpa()
                && afterPressure <= source.profile().lowPressureThresholdKpa())
            com.juicyslew.moonstation14.ms14.actions.InternalsActionFeedback.notifyController(body,
                    "moonstation14.action.internals.low_pressure");
        if (source.contents().mixture().totalMoles() > 0 && preview.remaining().mixture().totalMoles() <= 0)
            com.juicyslew.moonstation14.ms14.actions.InternalsActionFeedback.notifyController(body,
                    "moonstation14.action.internals.depleted");
        return Optional.of(new com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange(packet, GasMixture.vacuum()));
    }

    /** Drops stale selections when eligibility is lost or the exact carried source leaves the body. */
    public static Optional<SourceHandle> selectedTank(LivingEntity body) {
        if (body == null) return Optional.empty();
        InternalsState state = state(body);
        if (!state.connected() || !eligibleBody(body)) {
            if (state.connected()) disconnect(body);
            return Optional.empty();
        }
        Optional<SourceHandle> source = findToken(body, state.tankToken().orElseThrow());
        if (source.isEmpty()) disconnect(body);
        return source;
    }

    private static InternalsState state(LivingEntity body) {
        InternalsState value = body.getExistingDataOrNull(ModDataAttachments.INTERNALS.get());
        return value == null ? InternalsState.DISCONNECTED : value;
    }

    private static boolean eligibleBody(LivingEntity body) {
        var character = HandCapability.resolveCharacter(body).orElse(null);
        if (character == null || character.component(InternalsComponent.class).isEmpty()
                || character.component(RespiratorComponent.class).isEmpty()
                || HandCapability.resolveEquipmentTemplate(body).isEmpty()) return false;
        LiveHands live = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (live == null || !live.compatible(body) || LiveHands.equipmentSnapshot(body).isEmpty()) return false;
        return ResolvedWornEquipment.entries(body).stream().anyMatch(entry -> entry.profile().breathMask().isPresent()
                && !BreathToolItemState.isLowered(entry.stack())
                && entry.profile().breathMask().orElseThrow().allowedSlots().stream().anyMatch(entry.slotFlags()::contains));
    }

    private static Optional<SourceHandle> findToken(LivingEntity body, String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return Optional.empty();
        for (String id : hands.handIds()) {
            if (hands.token(id).filter(token::equals).isPresent()) {
                ItemStack stack = hands.stackCopy(id).orElse(ItemStack.EMPTY);
                Optional<SourceHandle> handle = source(body, hands, id, true, token, stack);
                if (handle.isPresent()) return handle;
            }
        }
        var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
        if (snapshot == null) return Optional.empty();
        for (var slot : snapshot.slots()) if (slot.token().filter(token::equals).isPresent()) {
            Optional<SourceHandle> handle = source(body, hands, slot.id(), false, token,
                    slot.stack().orElse(ItemStack.EMPTY));
            if (handle.isPresent()) return handle;
        }
        return Optional.empty();
    }

    private static Optional<SourceHandle> fallback(LivingEntity body) {
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return Optional.empty();
        var layout = HandCapability.resolveEquipmentTemplate(body).orElse(null);
        var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
        if (snapshot == null || layout == null) return Optional.empty();
        var slots = snapshot.slots().stream().filter(s -> s.token().isPresent() && s.stack().isPresent()).toList();
        for (int priority = 0; priority <= 1; priority++) for (var slot : slots) {
            if (equipmentPriority(layout.slot(slot.id()).map(InventoryTemplateData.Slot::flags).orElse(List.of()), slot.id()) != priority) continue;
            Optional<SourceHandle> found = source(body, hands, slot.id(), false, slot.token().orElseThrow(), slot.stack().orElseThrow());
            if (found.isPresent()) return found;
        }
        for (String hand : hands.handIds()) {
            String token = hands.token(hand).orElse(null);
            if (token == null) continue;
            Optional<SourceHandle> found = source(body, hands, hand, true, token, hands.stackCopy(hand).orElse(ItemStack.EMPTY));
            if (found.isPresent()) return found;
        }
        for (var slot : slots) {
            if (equipmentPriority(layout.slot(slot.id()).map(InventoryTemplateData.Slot::flags).orElse(List.of()), slot.id()) <= 1) continue;
            Optional<SourceHandle> found = source(body, hands, slot.id(), false, slot.token().orElseThrow(), slot.stack().orElseThrow());
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static int equipmentPriority(List<InventoryTemplateData.SlotFlag> flags, String id) {
        if ("back".equals(id) || flags.contains(InventoryTemplateData.SlotFlag.BACK)) return 0;
        if (flags.contains(InventoryTemplateData.SlotFlag.SUITSTORAGE)) return 1;
        return 2;
    }

    private static Optional<SourceHandle> source(LivingEntity body, LiveHands hands, String slot, boolean held,
                                                   String token, ItemStack stack) {
        var catalog = ModGasTanks.catalog(body.level());
        Optional<GasTankContents> contents = GasTankItem.contents(stack, catalog);
        if (contents.isEmpty()) return Optional.empty();
        GasTankData profile = catalog.get(contents.orElseThrow().prototypeId());
        if (profile == null) return Optional.empty();
        return Optional.of(new SourceHandle(slot, held, token, stack, contents.orElseThrow(), profile, hands));
    }
}
