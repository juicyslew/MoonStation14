package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/**
 * Typed routing data supplied when an effect is being run by metabolism.
 *
 * <p>{@code solution} is the metabolism transaction's live working
 * attachment. Effects may mutate it during dispatch; the metabolism owner
 * performs the single change-only persistence step after dispatch completes.
 * It must not escape that transaction as an independently retained alias.</p>
 *
 * <p>{@code ordinaryRemoved} is reserved capacity: metabolism removes this
 * quantity before dispatch in MoonStation, while the upstream effect ordering
 * applies adjustments before that removal.</p>
 */
public record ReagentEffectContext(
        TraitHandler<IReagentTrait> source,
        ReagentAttachment solution,
         ResourceKey<ReagentData> reagent,
         MetabolismStage stage,
         MetabolizerProfile profile,
         MetabolismSystem.SourceSnapshot sourceSnapshot,
         float ordinaryRemoved,
         ReagentEffectTransactionState transactionState,
         Optional<Entity> organ
) {
    /**
     * True only when a live reservoir is observable now. Pre-removal snapshots
     * are deliberately excluded. For body replenishment, stomach contents count
     * only if this reagent has an actual BODY metabolism entry.
     */
    public boolean hasOngoingReservoir() {
        float live = solution.getMap().getOrDefault(reagent, 0f);
        if (live > 0f) return true;
        if (stage == MetabolismStage.DIGESTION || !(source.holder() instanceof net.minecraft.world.entity.LivingEntity living)
                || !living.hasData(ModDataAttachments.STOMACH.get())) return false;
        var prototype = PrototypeRuntime.serverReagents().get(reagent.location());
        if (prototype == null || !prototype.metabolisms().containsKey(MetabolismStage.BLOODSTREAM)
                && !prototype.metabolisms().containsKey(MetabolismStage.RESPIRATION)
                && !prototype.metabolisms().containsKey(MetabolismStage.METABOLITES)) return false;
        ReagentAttachment stomach = MS14Provider.getDetached(living, MS14Bridges.STOMACH);
        return stomach.getMap().getOrDefault(reagent, 0f) > 0f;
    }

    public ReagentEffectContext {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(source.holder(), "source holder");
        Objects.requireNonNull(source.trait(), "source trait");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(reagent, "reagent");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        if (!Float.isFinite(ordinaryRemoved) || ordinaryRemoved < 0f) {
            throw new IllegalArgumentException("ordinaryRemoved must be finite and nonnegative");
        }
        Objects.requireNonNull(transactionState, "transactionState");
        if (Float.compare(ordinaryRemoved, transactionState.ordinaryRemoved()) != 0) {
            throw new IllegalArgumentException("ordinaryRemoved does not match transaction state");
        }
        Objects.requireNonNull(organ, "organ");
    }
}
