package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import com.juicyslew.moonstation14.ms14.character.components.AbsorbentCleaningComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPuddleDrinkService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Shared controlled and autonomous cleaner capability; all chemistry is staged in detached reagent copies. */
public final class AbsorbentCleaningService {
    public enum Result { SUCCESS, DENIED }
    private AbsorbentCleaningService() { }

    public static boolean capable(CharacterData character) {
        return character != null && character.component(AbsorbentCleaningComponent.class).isPresent();
    }

    /** Authenticated controlled-body action; only the canonical current gaze target is accepted. */
    public static Result cleanLookingAt(Mob controlledBody, long now, java.util.function.BooleanSupplier authority) {
        if (controlledBody == null || authority == null || !authority.getAsBoolean()
                || !controlledBody.isAlive() || controlledBody.isRemoved()
                || !(controlledBody.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || level.getEntity(controlledBody.getUUID()) != controlledBody
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(controlledBody).isPresent()
                || controlledBody.getExistingDataOrNull(ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get()) != null)
            return Result.DENIED;
        BlockPos nominated = com.juicyslew.moonstation14.ms14.hands.live.PuddleTargetProbe.find(
                controlledBody.level(), controlledBody);
        return nominated == null ? Result.DENIED : cleanAt(controlledBody, nominated, now, false);
    }

    /** NPC planner uses this bounded query; no world mutation and only loaded, visible canonical puddles. */
    public static BlockPos nearestVisiblePuddle(Mob actor, float range) {
        if (actor == null || !(actor.level() instanceof ServerLevel level) || !Float.isFinite(range) || range <= 0) return null;
        range = Math.min(range, 10f); // Bound the loaded-cell search even for permissive prototype profiles.
        BlockPos origin = actor.blockPosition();
        int radius = (int) Math.ceil(range);
        var character = CharacterIdentitySystem.resolveForActor(actor).orElse(null);
        var profile = character == null ? null : character.component(AbsorbentCleaningComponent.class).orElse(null);
        if (profile == null) return null;
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water =
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var cleaner = MS14Provider.getDetached(actor, MS14Bridges.REAGENT);
        long waterAvailable = cleaner.snapshotUnits().getOrDefault(water, 0L);
        long usable = Math.min(Math.min(profile.pickupCents(), profile.capacityCents() - cleaner.totalUnits() + waterAvailable),
                waterAvailable);
        if (usable <= 0) return null;
        BlockPos best = null; double bestDistance = range * range;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius,-radius,-radius), origin.offset(radius,radius,radius))) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle)) continue;
            var contents = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            if (contents.totalUnits() == contents.snapshotUnits().getOrDefault(water, 0L)) continue;
            double distance = actor.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            if (distance < bestDistance && BodyPuddleDrinkService.visiblePuddle(level, actor, pos)) { best = pos.immutable(); bestDistance = distance; }
        }
        return best;
    }

    /** Public action contract shared by player-controlled activation and autonomous Moproach behavior. */
    public static Result cleanAt(Mob actor, BlockPos pos, long now, boolean autonomousAuthority) {
        if (actor == null || pos == null || !(actor.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !level.hasChunkAt(pos)) return Result.DENIED;
        CharacterData character = CharacterIdentitySystem.resolveForActor(actor).orElse(null);
        AbsorbentCleaningComponent profile = character == null ? null : character.component(AbsorbentCleaningComponent.class).orElse(null);
        if (profile == null || !actor.isAlive() || actor.isRemoved() || actor.isPassenger() || !actor.isAddedToLevel()
                || level.getEntity(actor.getUUID()) != actor || !BodyPuddleDrinkService.visiblePuddle(level, actor, pos)) return Result.DENIED;
        if (autonomousAuthority && (!character.component(NpcBehaviorComponent.class).isPresent()
                || !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(actor, level, character))) return Result.DENIED;
        if (autonomousAuthority && (com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(actor).isPresent() || actor.getExistingDataOrNull(ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get()) != null))
            return Result.DENIED;
        if (!(level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle)) return Result.DENIED;
        var state = actor.getExistingDataOrNull(ModDataAttachments.ABSORBENT_CLEANING.get());
        if (state == null) state = AbsorbentCleaningAttachment.EMPTY;
        if (now < state.nextUseTick()) return Result.DENIED;
        ReagentAttachment source = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        ReagentAttachment dest = MS14Provider.getDetached(actor, MS14Bridges.REAGENT);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(source.getMap(), level, "absorbent source")
                || !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dest.getMap(), level, "absorbent solution")) return Result.DENIED;
        var beforeSource = MS14Provider.snapshot(source); var beforeDest = MS14Provider.snapshot(dest);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water =
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var plan = AbsorbentCleaningPolicy.prepare(source, dest, profile, water,
                ReagentUnits.fromFloat(puddle.getCapacity()));
        if (plan == null) return Result.DENIED;
        var changedSource = plan.puddleAfter();
        var changedDest = plan.cleanerAfter();
        changedSource.recursiveReaction(level, puddle.getCapacity());
        changedDest.recursiveReaction(level, ReagentUnits.toFloat(profile.capacityCents()));
        if (level.getBlockEntity(pos) != puddle || !level.hasChunkAt(pos)
                || !BodyPuddleDrinkService.visiblePuddle(level, actor, pos)
                || autonomousAuthority && !com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(
                actor, level, CharacterIdentitySystem.resolveForActor(actor).orElse(null))) return Result.DENIED;
        puddle.beginDeferredEmptyCleanup(); boolean committed = false, restored = false;
        try {
            MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, beforeSource, changedSource);
            if (level.getBlockEntity(pos) != puddle || !MS14Provider.snapshot(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT))
                    .equals(MS14Provider.snapshot(changedSource))) throw new IllegalStateException("clean source changed");
            MS14Provider.updateIfChanged(actor, MS14Bridges.REAGENT, beforeDest, changedDest);
            committed = MS14Provider.snapshot(MS14Provider.getDetached(actor, MS14Bridges.REAGENT)).equals(MS14Provider.snapshot(changedDest))
                    && MS14Provider.snapshot(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT)).equals(MS14Provider.snapshot(changedSource));
        } catch (RuntimeException failure) {
            try { if (MS14Provider.snapshot(MS14Provider.getDetached(actor, MS14Bridges.REAGENT)).equals(beforeDest)
                    && level.getBlockEntity(pos) == puddle
                    && MS14Provider.snapshot(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT)).equals(MS14Provider.snapshot(changedSource))) {
                MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, MS14Provider.snapshot(changedSource), new ReagentAttachment(beforeSource));
                restored = true;
            }} catch (RuntimeException ignored) { }
        } finally { if (!committed && !restored) puddle.blockDeferredCleanupAfterFailure(); puddle.endDeferredEmptyCleanup(committed); }
        if (!committed) return Result.DENIED;
        actor.setData(ModDataAttachments.ABSORBENT_CLEANING.get(), new AbsorbentCleaningAttachment(now + profile.cooldownTicks(), state.nextCycleTick()));
        return Result.SUCCESS;
    }

    /** Upstream regeneration is a fixed interval and capacity-clamped; purge removes contaminants only, preserving water. */
    public static void tick(Mob mob, long now) {
        CharacterData character = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        AbsorbentCleaningComponent profile = character == null ? null : character.component(AbsorbentCleaningComponent.class).orElse(null);
        if (profile == null) return;
        var state = mob.getExistingDataOrNull(ModDataAttachments.ABSORBENT_CLEANING.get());
        if (state == null) state = AbsorbentCleaningAttachment.EMPTY;
        if (state.nextCycleTick() == 0) {
            mob.setData(ModDataAttachments.ABSORBENT_CLEANING.get(),
                    new AbsorbentCleaningAttachment(state.nextUseTick(), now + profile.cycleTicks()));
            return;
        }
        if (now < state.nextCycleTick()) return;
        var stored = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        long regen = Math.min(profile.regenerationCents(), Math.max(0, profile.capacityCents() - stored.totalUnits()));
        var cycled = AbsorbentCleaningPolicy.cycle(stored, profile, water);
        if (!cycled.equals(stored)) MS14Provider.updateIfChanged(mob, MS14Bridges.REAGENT,
                MS14Provider.snapshot(MS14Provider.getDetached(mob, MS14Bridges.REAGENT)), cycled);
        mob.setData(ModDataAttachments.ABSORBENT_CLEANING.get(), new AbsorbentCleaningAttachment(state.nextUseTick(), now + profile.cycleTicks()));
    }
}
