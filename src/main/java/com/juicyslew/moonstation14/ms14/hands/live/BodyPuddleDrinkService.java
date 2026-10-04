package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.character.components.MouthComponent;
import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.*;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.sounds.SoundSource;
import com.juicyslew.moonstation14.sounds.ModSounds;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit body-owned ingestion from the puddle reagent attachment. */
public final class BodyPuddleDrinkService {
    private BodyPuddleDrinkService() { }

    public static BodyLiquidInteraction.Result start(LivingEntity body, String hand, long revision,
                                                       BlockPos target, BooleanSupplier authority) {
        if (!(body != null && body.level() instanceof ServerLevel level) || !eligible(body, hand, revision, authority)
                || target == null || !level.hasChunkAt(target) || !aimedPuddle(level, body, target)) return BodyLiquidInteraction.Result.DENIED;
        if (!(level.getBlockEntity(target) instanceof PuddleBlockEntity puddle)
                || !validContents(puddle, level)) return BodyLiquidInteraction.Result.DENIED;
        return finishTimed(body, target.immutable(), puddle, 100, true, authority);
    }

    public static BodyLiquidInteraction.Result finishTimed(LivingEntity body, BlockPos pos,
            PuddleBlockEntity expectedPuddle, int doseCents, boolean destroyOnEmpty, BooleanSupplier authority) {
        if (body == null || !(body.level() instanceof ServerLevel level) || pos == null || expectedPuddle == null
                || authority == null || doseCents <= 0 || !level.hasChunkAt(pos)
                || !authority.getAsBoolean() || !eligibleBody(body) || level.getBlockEntity(pos) != expectedPuddle
                || !visiblePuddle(level, body, pos) || !validContents(expectedPuddle, level))
            return BodyLiquidInteraction.Result.DENIED;
        PuddleBlockEntity puddle = expectedPuddle;
        ReagentAttachment source = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        ReagentAttachment stomach = MS14Provider.getDetached(body, MS14Bridges.STOMACH);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(source.getMap(), level, "puddle sip source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(stomach.getMap(), level, "puddle sip stomach")) return BodyLiquidInteraction.Result.DENIED;
        var sourceBefore = MS14Provider.snapshot(source); var stomachBefore = MS14Provider.snapshot(stomach);
        var stomachAttachment = body.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
        var plan = LiquidInteractionCapability.plan(ReagentUnits.fromMap(source.getMap()),
                ReagentUnits.fromMap(stomach.getMap()), LiquidInteractionCapability.STOMACH_CAPACITY_CENTS,
                 doseCents);
        if (plan.transferredCents() == 0) return BodyLiquidInteraction.Result.DENIED;
        var stagedSource = new ReagentAttachment(toFloats(plan.sourceAfter()));
        var stagedStomach = new ReagentAttachment(toFloats(plan.destinationAfter()));
        if (!authority.getAsBoolean() || !eligibleBody(body) || !level.hasChunkAt(pos)
                    || level.getBlockEntity(pos) != puddle || !visiblePuddle(level, body, pos)) return BodyLiquidInteraction.Result.DENIED;
        puddle.beginDeferredEmptyCleanup();
        boolean committed = false;
        boolean safeToCleanup = false;
        try {
            MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, sourceBefore, stagedSource);
            // Publishing source is effectful; authority and exact source state must still hold before stomach commit.
            if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != puddle
                    || body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) != stomachAttachment
                    || !ReagentUnits.fromMap(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap()).equals(plan.sourceAfter())
                    || !MS14Provider.snapshot(MS14Provider.getDetached(body, MS14Bridges.STOMACH)).equals(stomachBefore)
                    || !authority.getAsBoolean() || !eligibleBody(body)) throw new IllegalStateException("puddle sip authority/state changed");
            MS14Provider.updateIfChanged(body, MS14Bridges.STOMACH, stomachBefore, stagedStomach);
            committed = ReagentUnits.fromMap(MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap()).equals(plan.destinationAfter())
                    && ReagentUnits.fromMap(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap()).equals(plan.sourceAfter());
            safeToCleanup = committed;
            if (committed && !destroyOnEmpty && plan.sourceAfter().isEmpty())
                puddle.blockDeferredCleanupAfterFailure();
        } catch (RuntimeException failure) {
            // Restore only from the exact known staged source, and only while stomach and authority are unchanged.
            try {
                if (body.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) == stomachAttachment
                        && MS14Provider.snapshot(MS14Provider.getDetached(body, MS14Bridges.STOMACH)).equals(stomachBefore)
                        && level.getBlockEntity(pos) == puddle
                        && ReagentUnits.fromMap(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap()).equals(plan.sourceAfter())) {
                    MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT,
                            MS14Provider.snapshot(new ReagentAttachment(toFloats(plan.sourceAfter()))),
                            new ReagentAttachment(toFloats(plan.sourceBefore())));
                    safeToCleanup = ReagentUnits.fromMap(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap()).equals(plan.sourceBefore());
                }
            } catch (RuntimeException ignored) { safeToCleanup = false; }
        } finally {
            if (!safeToCleanup) puddle.blockDeferredCleanupAfterFailure();
            puddle.endDeferredEmptyCleanup(committed);
        }
        if (!committed) return safeToCleanup ? BodyLiquidInteraction.Result.DENIED : BodyLiquidInteraction.Result.RECOVERY_REQUIRED;
        EntityActivitySystem.update(body, EntityActivity.REAGENT_METABOLISM, true);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), ModSounds.DRINK.get(), SoundSource.PLAYERS,
                0.7943282F, 1f);
        return BodyLiquidInteraction.Result.SUCCESS;
    }

    private static boolean eligible(LivingEntity body, String hand, long revision, BooleanSupplier authority) {
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return authority != null && authority.getAsBoolean() && hand != null && hands != null && hands.compatible(body)
                && hands.revision() == revision && hands.activeHand().equals(hand)
                && hands.stackCopy(hand).isEmpty() && hands.reservation(hand).isEmpty() && eligibleBody(body);
    }
    public static boolean eligibleBody(LivingEntity body) {
        var character = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body).orElse(null);
        return body.isAlive() && !body.isRemoved() && body.isAddedToLevel()
                && character != null && character.component(MouthComponent.class).isPresent()
                && StomachSystem.isEligible(body) && BodySystem.current(body).isPresent()
                && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body);
    }
    private static boolean validContents(PuddleBlockEntity be, ServerLevel level) {
        return ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                MS14Provider.getDetached(be, MS14Bridges.REAGENT).getMap(), level, "puddle sip");
    }
    public static boolean visiblePuddle(ServerLevel level, LivingEntity body, BlockPos nominated) {
        if (!level.hasChunkAt(nominated)) return false;
        var chunk = level.getChunkSource().getChunkNow(nominated.getX() >> 4, nominated.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().get(nominated) == null
                || !(chunk.getBlockEntities().get(nominated) instanceof PuddleBlockEntity)) return false;
        var state = chunk.getBlockState(nominated);
        var shape = state.getShape(level, nominated, CollisionContext.of(body));
        if (shape.isEmpty()) return false;
        AABB bounds = shape.bounds();
        var from = body.getEyePosition();
        var to = new net.minecraft.world.phys.Vec3(nominated.getX() + (bounds.minX + bounds.maxX) / 2,
                nominated.getY() + (bounds.minY + bounds.maxY) / 2,
                nominated.getZ() + (bounds.minZ + bounds.maxZ) / 2);
        if (from.distanceToSqr(to) > 25) return false;
        return clearLine(level, body, nominated, from, to);
    }
    private static boolean aimedPuddle(ServerLevel level, LivingEntity body, BlockPos nominated) {
        return PuddleTargetProbe.selected(level, body, nominated);
    }
    private static boolean clearLine(ServerLevel level, LivingEntity body, BlockPos nominated,
                                     net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        int dx = net.minecraft.core.BlockPos.containing(to).getX() - net.minecraft.core.BlockPos.containing(from).getX();
        int dz = net.minecraft.core.BlockPos.containing(to).getZ() - net.minecraft.core.BlockPos.containing(from).getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz)) + 1;
        for (int i = 0; i <= steps; i++) {
            var pos = net.minecraft.core.BlockPos.containing(from.lerp(to, (double) i / steps));
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(nominated)
                && level.getBlockEntity(nominated) instanceof PuddleBlockEntity;
    }
    private static Map<ResourceKey<ReagentData>, Float> toFloats(Map<ResourceKey<ReagentData>, Long> values) {
        Map<ResourceKey<ReagentData>, Float> result = new LinkedHashMap<>();
        values.forEach((k,v) -> result.put(k, ReagentUnits.toFloat(v))); return result;
    }
}
