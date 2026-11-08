package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.construction.tool.ModTools;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Timed repair of damaged Tesla coils and grounding rods using an admitted body-held welder. */
public final class TeslaMachineRepairUse {
    private TeslaMachineRepairUse() { }

    public static boolean begin(ServerPlayer actor, BodyBlockUseRequest request,
                                 BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null || request.intent() != BodyBlockUseRequest.Intent.USE)
            return false;
        var authority = admission.authority();
        LiveHands hands = admission.hands();
        LivingEntity body = authority.body();
        BlockPos pos = admission.hit().pos();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !request.target().equals(pos) || request.face() != admission.hit().face()
                || request.expectedRevision() != hands.revision() || !request.handId().equals(hands.activeHand())
                || !authority.handIds().contains(request.handId())
                || !hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || !HandActorAuthority.revalidate(actor, authority)
                || !CharacterControlSystem.canAct(body)) return false;
        if (!(level.getBlockEntity(pos) instanceof TeslaMachineBlockEntity machine)) return false;
        var profile = machine.profile();
        var repair = profile == null ? null : profile.repair();
        if (repair == null || machine.structuralDamage() <= 0) return false;
        int ticks = durationTicks(repair);
        if (ticks < 1) return false;
        var catalog = PrototypeRuntime.serverTools();
        var toolId = ModTools.WELDER;
        var tool = catalog.get(toolId);
        ToolPrototypeData.Quality quality;
        try { quality = ToolPrototypeData.Quality.valueOf(repair.quality()); }
        catch (RuntimeException invalidQuality) { return false; }
        if (tool == null || quality != ToolPrototypeData.Quality.Welding) return false;
        ItemStack before = hands.stackCopy(request.handId()).orElse(null);
        if (before == null || !before.is(ModItems.WELDER)
                || !ModTools.hasQuality(catalog, toolId, quality,
                    before.get(com.juicyslew.moonstation14.component.ModDataComponents.WELDER_FUEL.get()))
                || !WelderFuelService.canSpendFuel(before, tool, repair.fuelCost(), level.getServer().overworld().getGameTime())
                || BodyBlockUseTargetProbe.probe(level, body, pos, request.face()) == null) return false;
        var identity = machine.placementIdentity();
        var block = level.getBlockState(pos).getBlock();
        java.util.function.BooleanSupplier valid = () -> valid(actor, request, authority, level, pos,
                machine, identity, block, before, tool, repair.fuelCost());
        var result = TimedInteractionSystem.start(body, actor, request.sequence(), "tesla_repair",
                pos.asLong() + ":" + identity, (int) ticks, true, true, valid,
                () -> complete(actor, request, authority, level, pos, machine, identity, block,
                        tool, repair.fuelCost(), valid), java.util.Optional.of(pos), () -> { });
        return result != TimedInteractionEngine.StartResult.DENIED;
    }

    static int durationTicks(TeslaMachineProfile.Repair repair) {
        if (repair == null || !Double.isFinite(repair.durationSeconds()) || repair.durationSeconds() <= 0)
            return -1;
        double ticks = Math.ceil(repair.durationSeconds() * 20.0);
        return !Double.isFinite(ticks) || ticks < 1 || ticks > Integer.MAX_VALUE ? -1 : (int) ticks;
    }

    private static TimedInteractionSystem.Completion complete(ServerPlayer actor, BodyBlockUseRequest request,
            HandActorAuthority.Snapshot authority, ServerLevel level, BlockPos pos,
            TeslaMachineBlockEntity machine, java.util.UUID identity, net.minecraft.world.level.block.Block block,
            ToolPrototypeData tool, int fuelCost, java.util.function.BooleanSupplier valid) {
        if (!valid.getAsBoolean()) return TimedInteractionSystem.Completion.DENIED;
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body())) return TimedInteractionSystem.Completion.DENIED;
        ItemStack current = hands.stackCopy(request.handId()).orElse(null);
        if (current == null) return TimedInteractionSystem.Completion.DENIED;
        long revision = hands.revision();
        ItemStack after = WelderFuelService.spendFuel(current, tool, fuelCost,
                level.getServer().overworld().getGameTime()).orElse(null);
        if (after == null || BodyHeldItemMutation.preview(actor, authority, hands, request.handId(),
                request.expectedToken(), revision, current, after).isEmpty())
            return TimedInteractionSystem.Completion.DENIED;
        if (BodyHeldItemMutation.commit(actor, authority, hands, request.handId(), request.expectedToken(),
                revision, current, after) != BodyHeldItemMutation.Result.SUCCESS)
            return TimedInteractionSystem.Completion.DENIED;
        if (!validMachine(level, pos, machine, identity, block) || !machine.repairStructuralDamage())
            return TimedInteractionSystem.Completion.DENIED;
        return TimedInteractionSystem.Completion.STOP;
    }

    private static boolean valid(ServerPlayer actor, BodyBlockUseRequest request, HandActorAuthority.Snapshot authority,
            ServerLevel level, BlockPos pos, TeslaMachineBlockEntity machine, java.util.UUID identity,
            net.minecraft.world.level.block.Block block, ItemStack initialStack, ToolPrototypeData tool, int fuelCost) {
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack current = hands == null ? null : hands.stackCopy(request.handId()).orElse(null);
        long revision = hands == null ? -1 : hands.revision();
        long now = level.getServer().overworld().getGameTime();
        var catalog = PrototypeRuntime.serverTools();
        boolean quality = ModTools.hasQuality(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding,
                current == null ? null : current.get(com.juicyslew.moonstation14.component.ModDataComponents.WELDER_FUEL.get()));
        return actor.getServer() != null && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor
                && HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body())
                && authority.body().level() == level
                && hands != null && hands.handIds().equals(authority.handIds())
                && hands.compatible(authority.body())
                && WelderFuelService.revisionContinuity(request.expectedRevision(), revision,
                        WelderFuelService.passiveFuelChanged(initialStack, current))
                && hands.activeHand().equals(request.handId())
                && hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                && WelderFuelService.sameStackOrPassiveFuel(initialStack, current)
                && current != null && current.is(ModItems.WELDER) && quality
                && WelderFuelService.canSpendFuel(current, tool, fuelCost, now)
                // Aim/face are admission-only. Once the weld action begins SS14 DoAfter
                // continuation requires range/visibility, not continued looking at the target.
                && BodyBlockUseTargetProbe.lineOfSight(level, authority.body(), pos)
                && validMachine(level, pos, machine, identity, block) && machine.structuralDamage() > 0;
    }

    private static boolean validMachine(ServerLevel level, BlockPos pos, TeslaMachineBlockEntity expected,
            java.util.UUID identity, net.minecraft.world.level.block.Block block) {
        return level.getServer().isSameThread() && level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() == block
                && level.getBlockEntity(pos) == expected && expected.placementIdentity().equals(identity);
    }
}
