package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.concurrent.atomic.AtomicBoolean;

/** Narrow providers backed by resolved local profiles/components; never reads contained inventory. */
public final class BuiltinExamineContributors {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private BuiltinExamineContributors() { }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ExamineContributorRegistry.register("domain-metadata", BuiltinExamineContributors::metadata);
        ExamineContributorRegistry.register("structural-damage", BuiltinExamineContributors::structuralDamage);
        ExamineContributorRegistry.register("receiver-power", BuiltinExamineContributors::receiverPower);
        ExamineContributorRegistry.register("apc-battery", BuiltinExamineContributors::apcBattery);
        ExamineContributorRegistry.register("puddle-solution", BuiltinExamineContributors::puddleSolution);
        ExamineContributorRegistry.register("jug-solution", BuiltinExamineContributors::jugSolution);
        ExamineContributorRegistry.register("beaker-solution", BuiltinExamineContributors::beakerSolution);
        ExamineContributorRegistry.register("syringe-solution", BuiltinExamineContributors::syringeSolution);
        ExamineContributorRegistry.register("botany-tray", BotanyExamineContributor::contribute);
        ExamineContributorRegistry.register("botany-seed-packet", BotanySeedPacketExamineContributor::contribute);
        ExamineContributorRegistry.register("gas-tank", BuiltinExamineContributors::gasTank);
    }

    /** Supported SS14 tank examination subset: detail-range pressure; connection and sealed valve state. */
    static void gasTank(ExamineContext context, ExamineCollector collector) {
        var stack = context.itemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem)) return;
        var catalog = com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.catalog(context.level());
        var contents = com.juicyslew.moonstation14.item.custom.GasTankItem.contents(stack, catalog).orElse(null);
        var profile = contents == null ? null : catalog.get(contents.prototypeId());
        if (profile == null) return;
        if (context.detailsAllowed()) {
            double pressure = contents.mixture().pressureKpa(profile.volumeCubicMeters());
            collector.detailEntry(Component.translatable("examine.moonstation14.gas-tank.pressure",
                    GasTankExaminePresentation.examinedPressure(pressure)).withStyle(net.minecraft.ChatFormatting.GOLD), "gas-tank", 40);
        }
        boolean connected = context.ownedItem() && context.target() != null
                && context.target().kind() == ExamineTarget.Kind.ITEM && context.actor() instanceof LivingEntity body
                && com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body)
                        .map(source -> source.token().equals(context.target().ownedItem().token())).orElse(false);
        collector.publicEntry(Component.translatable(connected ? "examine.moonstation14.gas-tank.connected"
                : "examine.moonstation14.gas-tank.disconnected"), "gas-tank", 30);
        collector.publicEntry(Component.translatable("examine.moonstation14.gas-tank.valve-closed"), "gas-tank", 20);
    }

    /** SS14 ExaminableDamage WindowMessages, backed by resolved machine composition and persisted damage. */
    static void structuralDamage(ExamineContext context, ExamineCollector collector) {
        if (!(context.blockEntity() instanceof com.juicyslew.moonstation14.ms14.tesla.TeslaMachineBlockEntity machine)) return;
        var profile = machine.profile();
        if (profile == null || profile.examineDamageMessages().size() != 6) return;
        int band = StructuralDamageExamineFormatter.band(machine.structuralDamage(), profile.destructionThreshold());
        collector.publicEntry(Component.translatable(profile.examineDamageMessages().get(band)), "damage", 60);
    }

    static void metadata(ExamineContext context, ExamineCollector collector) {
        if (context.blockEntity() instanceof AirlockBlockEntity airlock) {
            var data = airlock.prototype();
            if (data != null) collector.metadata(data.metadata());
        } else if (context.blockEntity() instanceof DisposalUnitBlockEntity disposal) {
            collector.metadata(disposal.examineMetadata());
        } else if (context.blockEntity() instanceof PuddleBlockEntity puddle) {
            var data = puddle.getResolvedPuddleData();
            if (data != null) collector.metadata(data.metadata());
        }
        if (!context.itemStack().isEmpty()) {
            var stack = context.itemStack();
            if (stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.JugItem jug) {
                var data = jug.getResolvedComposition(context.level());
                if (data != null) collector.metadata(data.metadata());
            } else {
                var profile = ToolQualities.profile(stack);
                if (profile != null) collector.metadata(profile.metadata());
            }
        } else if (context.targetEntity() instanceof LivingEntity living) {
            if (living.hasCustomName()) collector.preserveName();
            CharacterIdentitySystem.resolveForActor(living).ifPresent(data -> collector.metadata(data.metadata()));
        } else if (context.targetEntity() instanceof ItemEntity item) {
            if (item.getItem().getItem() instanceof com.juicyslew.moonstation14.item.custom.JugItem jug) {
                var data = jug.getResolvedComposition(context.level());
                if (data != null) collector.metadata(data.metadata());
            } else {
                var profile = ToolQualities.profile(item.getItem());
                if (profile != null) collector.metadata(profile.metadata());
            }
        }
    }

    static void receiverPower(ExamineContext context, ExamineCollector collector) {
        var level = context.level();
        if (context.blockEntity() instanceof AirlockBlockEntity airlock) {
            var data = airlock.prototype();
            if (data == null) return;
            var observation = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level, airlock);
            if (airlock.state().receiverPowerDisabled())
                collector.publicEntry(Component.translatable("examine.moonstation14.power.off"), "power", 100);
            else if (!observation.known())
                collector.publicEntry(Component.translatable("examine.moonstation14.power.unavailable"), "power", 100);
            else collector.publicEntry(Component.translatable(observation.deliveredWatts() == data.apcPowerReceiver().requestedWatts()
                    ? "examine.moonstation14.power.on" : "examine.moonstation14.power.off"), "power", 100);
        } else if (context.blockEntity() instanceof DisposalUnitBlockEntity disposal) {
            var config = disposal.prototype();
            if (config == null) return;
            if (!disposal.powerSwitch()) {
                collector.publicEntry(Component.translatable("examine.moonstation14.power.off"), "power", 100);
                return;
            }
            var observation = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitMeter(level, disposal);
            if (!observation.known()) collector.publicEntry(Component.translatable("examine.moonstation14.power.unavailable"), "power", 100);
            else collector.publicEntry(Component.translatable(observation.deliveredWatts() == config.powerWatts()
                    ? "examine.moonstation14.power.on" : "examine.moonstation14.power.off"), "power", 100);
        }
    }

    static void apcBattery(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed() || !(context.blockEntity() instanceof PowerDeviceBlockEntity apc)
                || !(apc.getBlockState().getBlock() instanceof PowerDeviceBlock block)
                || block.kind() != PowerDeviceKind.APC) return;
        double fraction = apc.energyJoules() / PowerDeviceRules.APC_CAPACITY_JOULES;
        int percentage = (int) Math.round(Math.clamp(fraction, 0.0, 1.0) * 100.0);
        collector.detailEntry(Component.translatable("examine.moonstation14.apc.battery", percentage), "battery", 50);
    }

    /** Supported subset of SS14 SharedSolutionContainerSystem.OnExamineSolution, read-only. */
    static void puddleSolution(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed() || !(context.blockEntity() instanceof PuddleBlockEntity puddle)) return;
        var data = puddle.getResolvedPuddleData();
        if (data == null || !data.examinableSolution().solutionName().equals("puddle")) return;
        ReagentAttachment solution = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        float capacity = puddle.getCapacity();
        boolean exact = data.examinableSolution().exactVolume();
        var output = SolutionExamineFormatter.format(solution, context.level(), capacity, exact,
                new SolutionExamineFormatter.Templates("examine.moonstation14.puddle.volume",
                        "examine.moonstation14.puddle.volume.exact", "examine.moonstation14.puddle.volume.",
                        false,
                        "examine.moonstation14.puddle.physical.singular",
                        "examine.moonstation14.puddle.physical.plural",
                        "examine.moonstation14.puddle.recognized", "examine.moonstation14.puddle.list",
                        "examine.moonstation14.puddle.generic-physical"), id -> lookup(context, id));
        collector.detailEntry(output.volume(), "puddle-solution", 40);
        for (int i = 0; i < output.details().size(); i++)
            collector.detailEntry(output.details().get(i), "puddle-solution", 39 - i);
    }

    /** Read-only supported Jug composition + current stack reagent snapshot. */
    static void jugSolution(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed()) return;
        var stack = itemStack(context);
        if (stack.isEmpty() || !(stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.JugItem jug)) return;
        var data = jug.getResolvedComposition(context.level());
        if (data == null || !data.examinableSolution().solutionName().equals(data.solutionContainer().solutionName())) return;
        // The existing trait handle keeps ItemStack as the sole authoritative owner;
        // MS14Provider returns a detached snapshot and does not initialize stored data.
        ReagentAttachment solution = MS14Provider.getDetached(jug.toHandle(stack), MS14Bridges.REAGENT);
        var output = SolutionExamineFormatter.format(solution, context.level(),
                data.solutionContainer().maxVolume(), data.examinableSolution().exactVolume(),
                SolutionExamineFormatter.Templates.GENERIC, id -> lookup(context, id));
        collector.detailEntry(output.volume(), "jug-solution", 40);
        int count = Math.min(data.examinableSolution().maxDetails(), output.details().size());
        for (int i = 0; i < count; i++) collector.detailEntry(output.details().get(i), "jug-solution", 39 - i);
    }

    /** Fresh, resolved stack view shared by floor, held and stored-item examination. */
    static void beakerSolution(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed()) return;
        var stack = itemStack(context);
        if (stack.isEmpty() || !(stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.BeakerItem beaker)) return;
        var data = com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.resolved(context.level(), beaker.prototypeId());
        if (data == null || !beakerExaminable(data.examinableSolution(), context)
                || !data.examinableSolution().solutionName().equals(data.solutionContainer().solutionName())) return;
        ReagentAttachment solution = MS14Provider.getDetached(beaker.toHandle(stack), MS14Bridges.REAGENT);
        var output = SolutionExamineFormatter.format(solution, context.level(), beaker.getCapacity(stack, context.level()),
                data.examinableSolution().exactVolume(), SolutionExamineFormatter.Templates.GENERIC,
                id -> lookup(context, id));
        collector.detailEntry(output.volume(), "beaker-solution", 40);
        for (int i = 0; i < output.details().size(); i++)
            collector.detailEntry(output.details().get(i), "beaker-solution", 39 - i);
    }

    static boolean beakerExaminable(com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerData.ExaminableSolution policy,
                                    ExamineContext context) {
        if (policy == null || !policy.enabled()) return false;
        if (!policy.heldOnly()) return true;
        return context.ownedItem() && context.target() != null && context.target().kind() == ExamineTarget.Kind.ITEM
                && context.target().ownedItem().location() == com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess.Location.HELD;
    }

    private static net.minecraft.world.item.ItemStack itemStack(ExamineContext context) {
        var stack = context.itemStack();
        if (!stack.isEmpty()) return stack;
        return context.targetEntity() instanceof ItemEntity item ? item.getItem().copy() : net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Syringe solution inspection from a detached authoritative stack snapshot. */
    static void syringeSolution(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed()) return;
        var stack = itemStack(context);
        if (stack.isEmpty() || !(stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.SyringeItem syringe)) return;
        var data = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(context.level()),
                com.juicyslew.moonstation14.item.custom.SyringeItem.PROTOTYPE_ID);
        var policy = data.syringePolicy().orElse(null);
        if (policy == null) return;
        ReagentAttachment solution = MS14Provider.getDetached(syringe.toHandle(stack), MS14Bridges.REAGENT);
        var output = SolutionExamineFormatter.format(solution, context.level(), policy.capacityUnits(), true,
                SolutionExamineFormatter.Templates.GENERIC, id -> lookup(context, id));
        collector.detailEntry(output.volume(), "syringe-solution", 40);
        for (int i = 0; i < output.details().size(); i++)
            collector.detailEntry(output.details().get(i), "syringe-solution", 39 - i);
    }

    private static ReagentData lookup(ExamineContext context, ResourceLocation id) {
        try { return ModReagents.require(context.level(), id); }
        catch (IllegalArgumentException missing) { return null; }
    }
    static String examinedVolume(long units, float capacity, boolean exact) {
        if (exact) return com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(units)
                + " / " + com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatUnits(capacity);
        return SolutionExamineFormatter.volumeCategory(units, capacity); // deterministic adaptation for SS14 examiner-dependent middle band.
    }
}
