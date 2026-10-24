package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamPolicy;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReactionAudio;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/** Narrow conditional access to an already initialized, policy-supported patient bloodstream. */
public final class BloodstreamSolutionAccess {
    private BloodstreamSolutionAccess() { }

    public record Snapshot(LivingEntity body, Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> units,
                           long capacityCents) {
        public Snapshot { units = Map.copyOf(units); }
    }

    /** Reads without initializing, migrating, or otherwise materializing blood state. */
    public static Optional<Snapshot> inspect(LivingEntity body) {
        if (!onOwnerThread(body) || body.isRemoved()
                || ((ServerLevel) body.level()).getEntity(body.getUUID()) != body
                || !body.hasData(ModDataAttachments.BLOOD.get())
                || !body.hasData(ModDataAttachments.BLOODSTREAM.get())
                || body.hasData(ModDataAttachments.REAGENT.get())) return Optional.empty();
        Optional<BloodstreamPolicy> policy = BloodSystem.resolvePolicy(body);
        if (policy.isEmpty() || BloodSystem.state(body).filter(BloodComponent::initialized).isEmpty())
            return Optional.empty();
        try {
            long cap = BloodReducer.capacity(policy.get());
            ReagentAttachment mixture = MS14Provider.getDetached(body, MS14Bridges.BLOODSTREAM);
            if (mixture.totalUnits() > cap
                    || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(mixture.getMap(), body.level(), "patient bloodstream"))
                return Optional.empty();
            return Optional.of(new Snapshot(body, mixture.snapshotUnits(), cap));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    /** Validates and stages a complete proposed solution; no patient mutation occurs. */
    public static Optional<ReagentAttachment> previewStoreChange(Snapshot expected, Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> after) {
        if (expected == null || after == null) return Optional.empty();
        try {
            long total = ReagentUnits.total(after.values());
            if (total > expected.capacityCents()) return Optional.empty();
            ReagentAttachment staged = new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(after));
            if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(staged.getMap(), expected.body().level(), "patient bloodstream"))
                return Optional.empty();
            return Optional.of(staged);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    /** Stages a whole dose against the pre-reaction capacity, never accepting a partial fit. */
    public static Optional<ReagentAttachment> previewWholeAddition(Snapshot expected,
            Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> doseCents) {
        if (expected == null || doseCents == null || doseCents.isEmpty()) return Optional.empty();
        try {
            if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                    new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(doseCents)).getMap(),
                    expected.body().level(), "melee injector dose")) return Optional.empty();
            Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> candidate =
                    wholeDoseCandidate(expected.units(), doseCents, expected.capacityCents());
            if (candidate == null) return Optional.empty();
            return previewStoreChange(expected, candidate);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    /** Adds and normally reacts a complete dose on a detached mixture, then conditionally commits it. */
    public static boolean tryAddWholeReacting(Snapshot expected,
            Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> doseCents) {
        ReagentAttachment staged = previewWholeAddition(expected, doseCents).orElse(null);
        if (staged == null || !(expected.body().level() instanceof ServerLevel level)) return false;
        try {
            staged.recursiveReaction(level, ReagentUnits.toFloat(expected.capacityCents()));
            if (!commit(expected, staged)) return false;
            ReactionAudio.playCommitted(level, expected.body().position(), staged);
            return true;
        } catch (RuntimeException invalid) { return false; }
    }

    static Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>
    wholeDoseCandidate(Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> existing,
                       Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> dose,
                       long capacity) {
        try {
            Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> merged = new HashMap<>(existing);
            dose.forEach((key, value) -> {
                ReagentUnits.validateCents(value, "dose");
                if (value == 0) throw new IllegalArgumentException("dose entries must be positive");
                merged.merge(key, value, Math::addExact);
            });
            return ReagentUnits.total(merged.values()) <= capacity ? Map.copyOf(merged) : null;
        } catch (RuntimeException invalid) { return null; }
    }

    /** Commits only if the same body is still eligible and its store exactly matches the snapshot. */
    public static boolean commit(Snapshot expected, ReagentAttachment after) {
        if (expected == null || after == null) return false;
        LivingEntity body = expected.body();
        Snapshot current = inspect(body).orElse(null);
        if (current == null || current.capacityCents() != expected.capacityCents()
                || !current.units().equals(expected.units()) || after.totalUnits() > current.capacityCents()
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(after.getMap(), body.level(), "patient bloodstream")) return false;
        try {
            var before = MS14Provider.getDetached(body, MS14Bridges.BLOODSTREAM).toComponent();
            return MS14Provider.updateIfChanged(body, MS14Bridges.BLOODSTREAM, before, after);
        } catch (RuntimeException invalid) { return false; }
    }

    private static boolean onOwnerThread(LivingEntity body) {
        return body != null && body.level() instanceof ServerLevel level && level.getServer() != null
                && level.getServer().isSameThread();
    }
}
