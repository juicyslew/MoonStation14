package com.juicyslew.moonstation14.ms14.construction.inventory;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import java.util.function.Predicate;

/** Immediate, body-owned material transaction for construction; never uses carrier inventory. */
public final class BodyCraftInventoryService {
    private BodyCraftInventoryService() { }
    public enum Result { SUCCESS, MATERIAL_MISSING, CAPACITY, STALE, DENIED, RECOVERY_REQUIRED }

    /** Post-validation synchronous publication seam; callers must perform authorization/revalidation first. */
    interface Publication {
        LiveHands body();
        void body(LiveHands value);
        ItemStack world();
        void world(ItemStack value);
        void discardWorld();
    }
    static Result publishValidated(Publication p,LiveHands before,LiveHands staged,ItemStack original,
                                   ItemStack remainder) {
        ItemStack initialWorld=p.world();
        if(p.body()!=before||initialWorld==null||!ItemStack.matches(original,initialWorld)) return Result.STALE;
        if(!remainder.isEmpty()) try { p.world(remainder); }
        catch(RuntimeException failure) {
            if(p.body()!=before||p.world()==null||!ItemStack.matches(remainder,p.world())) return Result.RECOVERY_REQUIRED;
            try { p.world(original.copy()); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
            return p.body()==before&&p.world()!=null&&ItemStack.matches(original,p.world())?Result.DENIED:Result.RECOVERY_REQUIRED;
        }
        ItemStack expectedWorld=remainder.isEmpty()?initialWorld:p.world();
        if(p.body()!=before||p.world()!=expectedWorld||!ItemStack.matches(remainder.isEmpty()?original:remainder,expectedWorld)) return Result.RECOVERY_REQUIRED;
        try { p.body(staged); }
        catch(RuntimeException failure) {
            if(p.body()==before) {
                if(remainder.isEmpty()) return Result.DENIED;
                if(p.world()!=expectedWorld||!ItemStack.matches(remainder,expectedWorld)) return Result.RECOVERY_REQUIRED;
                try { p.world(original.copy()); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
                return p.world()!=null&&ItemStack.matches(original,p.world())?Result.DENIED:Result.RECOVERY_REQUIRED;
            }
            if(p.body()!=staged||p.world()!=expectedWorld||!ItemStack.matches(remainder.isEmpty()?original:remainder,expectedWorld))
                return Result.RECOVERY_REQUIRED;
            try { p.body(before); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
            if(p.body()!=before||p.world()!=expectedWorld||!ItemStack.matches(remainder.isEmpty()?original:remainder,expectedWorld)) return Result.RECOVERY_REQUIRED;
            if(!remainder.isEmpty()) {
                try { p.world(original.copy()); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
                if(p.world()==null||!ItemStack.matches(original,p.world())) return Result.RECOVERY_REQUIRED;
            }
            return Result.DENIED;
        }
        if(p.body()!=staged) return Result.RECOVERY_REQUIRED;
        if(remainder.isEmpty()) {
            try { p.discardWorld(); }
            catch(RuntimeException failure) {
                if(p.body()!=staged) return Result.RECOVERY_REQUIRED;
                if(p.world()==null) return Result.SUCCESS;
                if(p.world()!=initialWorld||!ItemStack.matches(original,p.world())) return Result.RECOVERY_REQUIRED;
                try { p.body(before); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
                return p.body()==before&&p.world()==initialWorld&&ItemStack.matches(original,p.world())
                        ?Result.DENIED:Result.RECOVERY_REQUIRED;
            }
            if(p.world()!=null) {
                if(p.body()!=staged||p.world()!=initialWorld||!ItemStack.matches(original,p.world())) return Result.RECOVERY_REQUIRED;
                try { p.body(before); } catch(RuntimeException restore) { return Result.RECOVERY_REQUIRED; }
                return p.body()==before&&p.world()==initialWorld?Result.DENIED:Result.RECOVERY_REQUIRED;
            }
        }
        return Result.SUCCESS;
    }

    public static Result craftHeld(ServerPlayer actor, String hand, String sourceToken, long revision,
                                   Predicate<ItemStack> materialMatches, int units, ItemStack output) {
        return LiveHandsCommitGateway.batch(() -> craftHeldCommitted(actor, hand, sourceToken, revision, materialMatches, units, output));
    }

    private static Result craftHeldCommitted(ServerPlayer actor, String hand, String sourceToken, long revision,
                                   Predicate<ItemStack> materialMatches, int units, ItemStack output) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || materialMatches == null || units < 1 || output == null || output.isEmpty()) return Result.DENIED;
        var snapshot = authority.orElseThrow(); var body = snapshot.body();
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body)) return Result.STALE;
        if (before.revision() != revision) return Result.STALE;
        ItemStack source = before.stackCopy(hand).orElse(null);
        if (source == null || !before.token(hand).filter(sourceToken::equals).isPresent()
                || !materialMatches.test(source.copy()) || source.getCount() < units) return Result.MATERIAL_MISSING;
        String outputToken = UUID.randomUUID().toString();
        var candidate = before.consumeAndPlace(revision, hand, sourceToken, source, units, output.copy(), outputToken);
        if (candidate.isEmpty()) return Result.CAPACITY;
        if (!HandActorAuthority.revalidate(actor, snapshot) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || before.revision() != revision || !before.token(hand).filter(sourceToken::equals).isPresent()) return Result.STALE;
        if (!LiveHandsCommitGateway.commitOwned(body, before, candidate.orElseThrow())) return Result.DENIED;
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != candidate.orElseThrow()
                || !BodyHandStateService.representable(candidate.orElseThrow(), body)) return Result.RECOVERY_REQUIRED;
        BodyHandStateService.requireFreshQuery(actor);
        return Result.SUCCESS;
    }

    /** Automatic body inventory lookup: first adequate hand stack, then worn bag/belt grid entries. */
    public static Result craftBody(ServerPlayer actor, long revision, Predicate<ItemStack> materialMatches,
                                   int units, ItemStack output) {
        return LiveHandsCommitGateway.batch(() -> craftBodyCommitted(actor, revision, materialMatches, units, output));
    }

    private static Result craftBodyCommitted(ServerPlayer actor, long revision, Predicate<ItemStack> materialMatches,
                                   int units, ItemStack output) {
        var authority=HandActorAuthority.resolve(actor);
        if(authority.isEmpty()||materialMatches==null||units<1||output==null||output.isEmpty()) return Result.DENIED;
        var auth=authority.orElseThrow(); var body=auth.body();
        if(!(body.level() instanceof ServerLevel level)||level.getServer()==null||!level.getServer().isSameThread()
                ||!CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(before==null||!before.compatible(body)||before.revision()!=revision) return Result.STALE;
        var selected=before.consumeBodyMaterial(revision,materialMatches,units,output.copy(),UUID.randomUUID().toString());
        if(selected.isEmpty()) {
            if (before.hasAdequateBodyMaterial(materialMatches,units)) return Result.CAPACITY;
            return craftNearby(actor,auth,level,before,revision,materialMatches,units,output);
        }
        if(!HandActorAuthority.revalidate(actor,auth)||!CharacterControlSystem.canAct(body)
                ||body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=before||before.revision()!=revision)
            return Result.STALE;
        if (!LiveHandsCommitGateway.commitOwned(body, before, selected.orElseThrow().state())) return Result.DENIED;
        if(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=selected.orElseThrow().state()
                ||!BodyHandStateService.representable(selected.orElseThrow().state(),body)) return Result.RECOVERY_REQUIRED;
        BodyHandStateService.requireFreshQuery(actor);
        return Result.SUCCESS;
    }

    private static Result craftNearby(ServerPlayer actor, HandActorAuthority.Snapshot auth, ServerLevel level,
                                      LiveHands before,long revision,Predicate<ItemStack> matches,int units,ItemStack output) {
        var body=auth.body(); Vec3 center=body.position();
        var candidates=level.getEntities(body,body.getBoundingBox().inflate(2.0),e->e instanceof ItemEntity).stream()
                .filter(ItemEntity.class::isInstance).map(ItemEntity.class::cast)
                .filter(e->e.level()==level&&!e.isRemoved()&&e.isAddedToLevel()&&level.getEntity(e.getUUID())==e
                        &&nearbySourceEligible(level,body,e,center)
                        &&e.getItem()!=null&&!e.getItem().isEmpty()&&e.getItem().getCount()>=units&&matches.test(e.getItem().copy())
                        )
                .sorted(java.util.Comparator.comparingDouble((ItemEntity e)->e.position().distanceToSqr(center))
                        .thenComparing(e->e.getUUID().toString())).toList();
        for(ItemEntity source:candidates) {
            Vec3 pos=source.position(); ItemStack reference=source.getItem(); ItemStack snapshot=reference.copy(); UUID id=source.getUUID();
            String token=UUID.randomUUID().toString();
            var staged=before.placeCraftOutput(revision,output.copy(),token);
            if(staged.isEmpty()) return Result.CAPACITY;
            if(!HandActorAuthority.revalidate(actor,auth)||!CharacterControlSystem.canAct(body)
                    ||body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=before||before.revision()!=revision
                    ||source.level()!=level||source.isRemoved()||!source.isAddedToLevel()||level.getEntity(id)!=source
                    ||!pos.equals(source.position())||source.getItem()!=reference||!ItemStack.matches(snapshot,reference)
                    ||reference.getCount()<units) continue;
            ItemStack remainder=snapshot.copy(); remainder.shrink(units);
            if(!HandActorAuthority.revalidate(actor,auth)||body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=before
                    ||source.isRemoved()||level.getEntity(id)!=source||source.getItem()!=reference||!ItemStack.matches(snapshot,reference)) continue;
            Result committed=publishValidated(new Publication() {
                public LiveHands body(){return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());}
                public void body(LiveHands value){
                    LiveHands expected = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (expected != before && expected != staged.orElseThrow())
                        throw new IllegalStateException("Live hands changed during construction publication");
                    if (!LiveHandsCommitGateway.commitOwned(body, expected, value))
                        throw new IllegalStateException("Live hands publication rejected");
                }
                public ItemStack world(){return source.isRemoved()?null:source.getItem();}
                public void world(ItemStack value){source.setItem(value);}
                public void discardWorld(){source.discard();}
            },before,staged.orElseThrow(),snapshot,remainder);
            if(committed!=Result.SUCCESS) return committed;
            if(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())!=staged.orElseThrow()
                    ||!BodyHandStateService.representable(staged.orElseThrow(),body)) return Result.RECOVERY_REQUIRED;
            BodyHandStateService.requireFreshQuery(actor);
            return Result.SUCCESS;
        }
        return Result.MATERIAL_MISSING;
    }


    /** SS14-style nearby ingredient admission: body inventory remains preferred; this only answers availability. */
    public static boolean hasNearbyMaterial(net.minecraft.world.entity.LivingEntity body, Predicate<ItemStack> matches,
                                            int units) {
        if (body == null || matches == null || units < 1 || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || body.isRemoved()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return false;
        var center = body.position();
        for (var entity : level.getEntities(body, body.getBoundingBox().inflate(2.0), e -> e instanceof ItemEntity)) {
            if (!(entity instanceof ItemEntity item) || item.level() != level || item.isRemoved()
                    || !item.isAddedToLevel() || level.getEntity(item.getUUID()) != item
                    || !nearbySourceEligible(level,body,item,center)) continue;
            ItemStack stack = item.getItem();
            if (stack != null && !stack.isEmpty() && stack.getCount() >= units && matches.test(stack.copy())
                    )
                return true;
        }
        return false;
    }

    private static boolean nearbySourceEligible(ServerLevel level,net.minecraft.world.entity.LivingEntity body,
                                                ItemEntity item,net.minecraft.world.phys.Vec3 center) {
        // Targeted entities remain protected by local world-transfer ownership semantics; pickup delay is
        // deliberately irrelevant to crafting (SS14 nearby ingredient enumeration has no pickup-delay gate).
        return nearbyCandidateEligible(item.getTarget()!=null,item.position().distanceToSqr(center)<=4.0,
                com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.reachableStorageHost(level,body,item,2.0));
    }
    static boolean nearbyCandidateEligible(boolean targeted,boolean inRange,boolean unobstructed) {
        return !targeted&&inRange&&unobstructed;
    }
}
