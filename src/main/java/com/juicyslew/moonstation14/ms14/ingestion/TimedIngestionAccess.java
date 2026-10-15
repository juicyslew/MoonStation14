package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.MouthComponent;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.*;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.*;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Server-only admission and revalidation for timed ingestion; admissions are never network state. */
public final class TimedIngestionAccess {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation PUDDLE_PROFILE = ResourceLocation.fromNamespaceAndPath("moonstation14", "puddle");
    private TimedIngestionAccess() { }

    public enum Completion { COMMITTED_REPEAT, COMMITTED_STOP, CANNOT_INGEST, DENIED, RECOVERY_REQUIRED }
    public enum WorldAdmissionFailure { NONE, SERVER_CONTEXT, NULL_TARGET, HAND_UNAVAILABLE, NOT_VISIBLE,
        WRONG_SOURCE_KIND, CLAIMED, PICKUP_DELAY, NOT_REAGENT, EMPTY_SOURCE, NO_STOMACH_ROOM, HYDRATION_BLOCKED,
        OUT_OF_REACH_OR_OCCLUDED, AUTHORITY_REJECTED }
    public record WorldAdmissionResult(Optional<Admission> admission, WorldAdmissionFailure failure,
                                       BodyItemTargetProbe.ProbeDiagnostic probe) { }
    private enum Kind { HELD, WORLD, PUDDLE }

    public static final class Admission {
        private final String sourceKey;
        private final ResourceLocation profileId;
        private final Kind kind;
        private final ServerLevel level;
        private final UUID bodyId, worldId;
        private final LivingEntity body;
        private final String token, initialHand;
        private final long initialRevision;
        private final ResourceLocation itemType;
        private final ItemEntity world;
        private final BlockPos pos;
        private final PuddleBlockEntity puddle;
        private WorldAdmissionFailure lastWorldValidationFailure;
        private Admission(String key, ResourceLocation profile, Kind kind, LivingEntity body, ServerLevel level,
                          String token, String hand, long revision, ResourceLocation itemType,
                          ItemEntity world, BlockPos pos, PuddleBlockEntity puddle) {
            sourceKey=key; profileId=profile; this.kind=kind; this.body=body; this.level=level;
            bodyId=body.getUUID(); this.token=token; initialHand=hand; initialRevision=revision;
            this.itemType=itemType; this.world=world; worldId=world == null ? null : world.getUUID();
            this.pos=pos; this.puddle=puddle;
        }
        public String sourceKey() { return sourceKey; }
        public ResourceLocation profileId() { return profileId; }
    }

    public static Optional<Admission> admitHeld(LivingEntity body, String hand, String token, long revision, BooleanSupplier authority) {
        ServerLevel level = server(body, authority);
        if (level == null || hand == null || token == null) return Optional.empty();
        LiveHands hands = liveHands(body);
        if (hands == null || hands.revision()!=revision || !hands.activeHand().equals(hand)
                || !hands.token(hand).filter(token::equals).isPresent()) return Optional.empty();
        ItemStack stack=hands.stackCopy(hand).orElse(null);
        if (!reagent(stack) || !positive(stack, level) || !stomachRoom(body, level)) return Optional.empty();
        try { new ItemToken(token); } catch (RuntimeException ex) { return Optional.empty(); }
        ResourceLocation type=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (blockedByHydration(body, type)) return Optional.empty();
        return Optional.of(new Admission("held:"+token,type,Kind.HELD,body,level,token,hand,revision,type,null,null,null));
    }

    public static Optional<Admission> admitWorld(LivingEntity body, UUID target, String hand, long revision, BooleanSupplier authority) {
        return diagnoseWorldAdmission(body, target, hand, revision, authority).admission();
    }

    /** Read-only explanation generated by the exact authoritative admission branches. */
    public static WorldAdmissionResult diagnoseWorldAdmission(LivingEntity body, UUID target, String hand,
                                                               long revision, BooleanSupplier authority) {
        ServerLevel level=server(body,authority);
        if(level==null)return worldFailure(WorldAdmissionFailure.SERVER_CONTEXT, null);
        if(target==null)return worldFailure(WorldAdmissionFailure.NULL_TARGET, null);
        LiveHands hands=level==null?null:liveHands(body);
        boolean handless=level!=null&&OralAccess.handless(body);
        if(!handless&&(hand==null||hands==null||hands.revision()!=revision
                ||!hands.activeHand().equals(hand)||hands.stackCopy(hand).isPresent()||hands.reservation(hand).isPresent()))
            return worldFailure(WorldAdmissionFailure.HAND_UNAVAILABLE, null);
        var probe=BodyItemTargetProbe.probeSelectedDiagnostic(level,body,target,BodyItemTargetProbe.MAX_REACH);
        if(probe.target()==null)return worldFailure(WorldAdmissionFailure.NOT_VISIBLE, probe.diagnostic());
        Entity entity=level.getEntity(target);
        if(!(entity instanceof ItemEntity item)||!(item instanceof LooseItemEntity))
            return worldFailure(WorldAdmissionFailure.WRONG_SOURCE_KIND, probe.diagnostic());
        if(item.getTarget()!=null)return worldFailure(WorldAdmissionFailure.CLAIMED, probe.diagnostic());
        if(item.hasPickUpDelay())return worldFailure(WorldAdmissionFailure.PICKUP_DELAY, probe.diagnostic());
        if(!reagent(item.getItem()))return worldFailure(WorldAdmissionFailure.NOT_REAGENT, probe.diagnostic());
        if(!positive(item.getItem(),level))return worldFailure(WorldAdmissionFailure.EMPTY_SOURCE, probe.diagnostic());
        if(!stomachRoom(body,level))return worldFailure(WorldAdmissionFailure.NO_STOMACH_ROOM, probe.diagnostic());
        ResourceLocation type=BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
        if (blockedByHydration(body, type))return worldFailure(WorldAdmissionFailure.HYDRATION_BLOCKED, probe.diagnostic());
        return new WorldAdmissionResult(Optional.of(new Admission("world:"+target,type,Kind.WORLD,body,level,null,
                hand,revision,type,item,null,null)),WorldAdmissionFailure.NONE,probe.diagnostic());
    }

    private static WorldAdmissionResult worldFailure(WorldAdmissionFailure failure,
            BodyItemTargetProbe.ProbeDiagnostic probe) {
        return new WorldAdmissionResult(Optional.empty(),failure,probe);
    }

    public static Optional<Admission> admitPuddle(LivingEntity body, BlockPos pos, String hand, long revision, BooleanSupplier authority) {
        ServerLevel level=server(body,authority);
        LiveHands hands=level==null?null:liveHands(body);
        boolean handless=level!=null&&OralAccess.handless(body);
        if(level==null||pos==null||(!handless&&(hand==null||hands==null||hands.revision()!=revision||!hands.activeHand().equals(hand)
                ||hands.stackCopy(hand).isPresent()||hands.reservation(hand).isPresent()))||!level.hasChunkAt(pos)
                ||!PuddleTargetProbe.selected(level,body,pos)||!stomachRoom(body,level)) return Optional.empty();
        if(!(level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle)||!positive(puddle,level)) return Optional.empty();
        BlockPos fixed=pos.immutable();
        if (blockedByHydration(body, PUDDLE_PROFILE)) return Optional.empty();
        return Optional.of(new Admission("puddle:"+level.dimension().location()+":"+fixed.getX()+","+fixed.getY()+","+fixed.getZ(),
                PUDDLE_PROFILE,Kind.PUDDLE,body,level,null,hand,revision,null,null,fixed,puddle));
    }

    public static boolean valid(LivingEntity body, Admission admission, BooleanSupplier authority) {
        if(admission==null||body==null) return false;
        if (admission.kind == Kind.WORLD) {
            WorldAdmissionFailure failure = worldValidationFailure(body, admission, authority);
            if (failure != admission.lastWorldValidationFailure && failure != WorldAdmissionFailure.NONE) {
                Entity source = admission.level.getEntity(admission.worldId);
                LOGGER.info("Timed world-ingestion revalidation failed: actor={} source={} reason={} eye={} look={} sourceBounds={}",
                        body.getUUID(), admission.worldId, failure, body.getEyePosition(), body.getLookAngle(),
                        source == null ? null : source.getBoundingBox());
            }
            admission.lastWorldValidationFailure = failure;
            return failure == WorldAdmissionFailure.NONE;
        }
        if(admission.body!=body||server(body,authority)!=admission.level
                ||!body.getUUID().equals(admission.bodyId)||!stomachRoom(body,admission.level)
                ||blockedByHydration(body, admission.profileId)) return false;
        LiveHands hands=liveHands(body);
        if(hands==null&&(admission.kind==Kind.HELD||!OralAccess.handless(body))) return false;
        return switch(admission.kind) {
            case HELD -> findToken(hands,admission.token,admission.itemType,admission.level);
            case WORLD -> throw new IllegalStateException("world admissions are handled above");
            case PUDDLE -> admission.level.hasChunkAt(admission.pos)&&admission.level.getBlockEntity(admission.pos)==admission.puddle
                    &&BodyPuddleDrinkService.visiblePuddle(admission.level,body,admission.pos)&&positive(admission.puddle,admission.level);
        };
    }

    private static WorldAdmissionFailure worldValidationFailure(LivingEntity body, Admission admission,
                                                                 BooleanSupplier authority) {
        if (admission.body != body || server(body, () -> true) != admission.level
                || !body.getUUID().equals(admission.bodyId)) return WorldAdmissionFailure.SERVER_CONTEXT;
        if (authority == null || !authority.getAsBoolean()) return WorldAdmissionFailure.AUTHORITY_REJECTED;
        if (!stomachRoom(body, admission.level)) return WorldAdmissionFailure.NO_STOMACH_ROOM;
        if (blockedByHydration(body, admission.profileId)) return WorldAdmissionFailure.HYDRATION_BLOCKED;
        LiveHands hands = liveHands(body);
        if (hands == null && !OralAccess.handless(body)) return WorldAdmissionFailure.HAND_UNAVAILABLE;
        ItemEntity item = admission.world;
        if (item == null || !item.getUUID().equals(admission.worldId)
                || admission.level.getEntity(admission.worldId) != item || item.level() != admission.level
                || item.isRemoved() || !(item instanceof LooseItemEntity)) return WorldAdmissionFailure.WRONG_SOURCE_KIND;
        if (item.getTarget() != null) return WorldAdmissionFailure.CLAIMED;
        if (item.hasPickUpDelay()) return WorldAdmissionFailure.PICKUP_DELAY;
        if (!reagent(item.getItem()) || !sameType(item.getItem(), admission.itemType))
            return WorldAdmissionFailure.NOT_REAGENT;
        if (!positive(item.getItem(), admission.level)) return WorldAdmissionFailure.EMPTY_SOURCE;
        // Continuation validates the same loaded-only source/reach/collider predicate as before.
        // Current gaze is an admission-time selection requirement, not a lease on an active bite.
        if (!BodyItemTargetProbe.loadedReachableItem(admission.level, body, admission.worldId,
                BodyItemTargetProbe.MAX_REACH)) return WorldAdmissionFailure.OUT_OF_REACH_OR_OCCLUDED;
        return WorldAdmissionFailure.NONE;
    }

    public static Completion complete(LivingEntity body, Admission admission, int doseCents, boolean destroyOnEmpty,
                                      boolean food, BooleanSupplier authority) {
        return complete(body, admission, doseCents, destroyOnEmpty, food, authority, ignored -> { });
    }

    public static Completion complete(LivingEntity body, Admission admission, int doseCents, boolean destroyOnEmpty,
                                      boolean food, BooleanSupplier authority, java.util.function.LongConsumer receipt) {
        if (receipt == null) return Completion.DENIED;
        if(doseCents<=0||!valid(body,admission,authority)) return Completion.DENIED;
        IngestionProfile resolvedProfile = ModIngestionProfiles.serverLookup(admission.profileId);
        LiveHands hands=liveHands(body);
        String hand=admission.kind==Kind.HELD?findTokenHand(hands,admission.token):hands==null?null:hands.activeHand();
        if(admission.kind==Kind.HELD&&hand==null || hands==null&&!OralAccess.handless(body))return Completion.DENIED;
        long revision=hands==null?0:hands.revision(); long available=availableStomach(body,admission.level);
        if (resolvedProfile != null && resolvedProfile.consumeSource()) {
            // Whole-source ingestion is only supported for an authenticated held source.
            if (admission.kind != Kind.HELD || hands == null || hand == null) return Completion.DENIED;
            ItemStack pill = hands.stackCopy(hand).orElse(null);
            long fullDose = sourceCents(pill, admission.level);
            if (fullDose <= 0 || fullDose > Integer.MAX_VALUE) return Completion.DENIED;
            BodyLiquidInteraction.Result consumed = BodyLiquidInteraction.consumeTimedEdibleOwned(body, hand,
                    admission.token, revision, (int) fullDose, authority, resolvedProfile.completionSound(),
                    resolvedProfile.completionVolumeDb(), resolvedProfile.completionPitchVariation());
            if (consumed == BodyLiquidInteraction.Result.SUCCESS) receipt.accept(fullDose);
            return consumed == BodyLiquidInteraction.Result.SUCCESS ? Completion.COMMITTED_STOP
                    : consumed == BodyLiquidInteraction.Result.CANNOT_INGEST ? Completion.CANNOT_INGEST
                    : consumed == BodyLiquidInteraction.Result.RECOVERY_REQUIRED ? Completion.RECOVERY_REQUIRED : Completion.DENIED;
        }
        if(available<=0)return Completion.DENIED;
        BodyLiquidInteraction.Result result;
        if(admission.kind==Kind.PUDDLE) result=BodyPuddleDrinkService.finishTimed(body,admission.pos,admission.puddle,doseCents,destroyOnEmpty,authority,receipt);
        else result=BodyLiquidInteraction.sipTimedOwned(body,admission.kind==Kind.WORLD?admission.worldId:null,
                hand,admission.kind==Kind.HELD?admission.token:null,revision,doseCents,destroyOnEmpty,food,authority,
                resolvedProfile == null ? null : resolvedProfile.completionSound(),
                resolvedProfile == null ? -2f : resolvedProfile.completionVolumeDb(),
                resolvedProfile == null ? .25f : resolvedProfile.completionPitchVariation(), receipt);
        return switch(result) {
            case DENIED -> Completion.DENIED;
            case CANNOT_INGEST -> Completion.CANNOT_INGEST;
            case RECOVERY_REQUIRED -> Completion.RECOVERY_REQUIRED;
            // Decide repetition from the state produced by this committed dose, not just
            // the pre-transaction room. A drink that reaches max hydration is a terminal
            // voluntary operation even if metabolism later opens room again.
            case SUCCESS -> validSourceAfter(body,admission,authority)
                    && acceptedStomachCents(body, admission.level, available) == doseCents
                    && stomachRoom(body, admission.level)
                    && !blockedByHydration(body, admission.profileId)
                    ?Completion.COMMITTED_REPEAT:Completion.COMMITTED_STOP;
        };
    }

    private static long sourceCents(ItemStack stack, ServerLevel level) {
        try {
            if (!reagent(stack)) return 0;
            var bridge = ReagentSystem.bridgeForHolder(stack);
            if (bridge == null) return 0;
            var component = MS14Provider.getDetached(stack, bridge);
            if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(component.getMap(), level, "whole-dose pill")) return 0;
            return ReagentUnits.fromMap(component.getMap()).values().stream().mapToLong(Long::longValue).sum();
        } catch (RuntimeException invalid) { return 0; }
    }

    /** Identifies an active ingestion journal entry as a voluntary drink from its live source. */
    public static boolean isDrinkOperation(LivingEntity body, String sourceKey) {
        if (body == null || sourceKey == null) return false;
        ResourceLocation profileId = null;
        if (sourceKey.startsWith("puddle:")) profileId = PUDDLE_PROFILE;
        else if (sourceKey.startsWith("held:")) {
            LiveHands hands = liveHands(body);
            String token = sourceKey.substring("held:".length());
            if (hands != null) for (String hand : hands.handIds())
                if (hands.token(hand).filter(token::equals).isPresent()) {
                    var stack = hands.stackCopy(hand).orElse(null);
                    if (stack != null && !stack.isEmpty()) profileId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    break;
                }
        } else if (sourceKey.startsWith("world:")) {
            try {
                if (body.level() instanceof ServerLevel level) {
                    Entity source = level.getEntity(UUID.fromString(sourceKey.substring("world:".length())));
                    if (source instanceof ItemEntity item && !item.getItem().isEmpty())
                        profileId = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
                }
            } catch (IllegalArgumentException ignored) { return false; }
        }
        IngestionProfile profile = profileId == null ? null : ModIngestionProfiles.serverLookup(profileId);
        return profile != null && profile.edible().kind() == IngestionProfile.Kind.DRINK;
    }

    private static boolean validSourceAfter(LivingEntity body,Admission a,BooleanSupplier auth) {
        if(a.kind==Kind.PUDDLE) return a.level.hasChunkAt(a.pos)&&a.level.getBlockEntity(a.pos)==a.puddle&&positive(a.puddle,a.level);
        if(a.kind==Kind.WORLD) return a.level.getEntity(a.worldId)==a.world&&positive(a.world.getItem(),a.level);
        return findToken(liveHands(body),a.token,a.itemType,a.level);
    }
    private static boolean findToken(LiveHands h,String token,ResourceLocation type,ServerLevel l) {
        String hand=findTokenHand(h,token); if(hand==null)return false;
        ItemStack stack=h.stackCopy(hand).orElse(null);
        return reagent(stack)&&sameType(stack,type)&&positive(stack,l);
    }
    private static String findTokenHand(LiveHands h,String token) {
        if(h==null)return null;
        for(String hand:h.handIds())if(h.token(hand).filter(token::equals).isPresent())return hand;
        return null;
    }
    private static ServerLevel server(LivingEntity body,BooleanSupplier auth) {
        if(body==null||auth==null||!(body.level() instanceof ServerLevel l)||l.getServer()==null||!l.getServer().isSameThread()
                ||body.isRemoved()||!body.isAlive()||!body.isAddedToLevel()||l.getEntity(body.getUUID())!=body
                ||!auth.getAsBoolean()||!CharacterControlSystem.canAct(body))return null;
        var actor=com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body).orElse(null);
        return actor!=null&&actor.component(MouthComponent.class).isPresent()&&StomachSystem.isEligible(body)&&BodySystem.current(body).isPresent()?l:null;
    }
    private static LiveHands liveHands(LivingEntity body) {
        LiveHands h=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return h!=null&&h.compatible(body)?h:null;
    }
    private static boolean reagent(ItemStack s){return s!=null&&!s.isEmpty()&&s.getItem() instanceof IReagentTrait;}
    private static boolean sameType(ItemStack s,ResourceLocation id){return id.equals(BuiltInRegistries.ITEM.getKey(s.getItem()));}
    private static boolean positive(Object holder,ServerLevel l){
        try {
            if (holder instanceof ItemStack stack) com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem.initializeSource(stack, l);
            else if (holder instanceof ItemEntity item) com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem.initializeSource(item.getItem(), l);
            var bridge=ReagentSystem.bridgeForHolder(holder);
            if(bridge==null)return false;
            var map=MS14Provider.getDetached(holder,bridge).getMap();
            if(!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(map,l,"timed ingestion source"))return false;
            return ReagentUnits.fromMap(map).values().stream().mapToLong(Long::longValue).sum()>0;
        }
        catch(RuntimeException ex){return false;}
    }
    private static boolean stomachRoom(LivingEntity body,ServerLevel l){
        try { var m=MS14Provider.getDetached(body,MS14Bridges.STOMACH).getMap();
            if(!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(m,l,"timed ingestion stomach"))return false;
            return availableStomach(body,l)>0;
        } catch(RuntimeException ex){return false;}
    }
    private static boolean blockedByHydration(LivingEntity body, ResourceLocation profileId) {
        IngestionProfile profile = ModIngestionProfiles.serverLookup(profileId);
        boolean drink = PUDDLE_PROFILE.equals(profileId) || profile != null && profile.edible().kind() == IngestionProfile.Kind.DRINK;
        return drink && com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(body);
    }
    private static long availableStomach(LivingEntity body,ServerLevel l){
        var map=MS14Provider.getDetached(body,MS14Bridges.STOMACH).getMap();
        long used=ReagentUnits.fromMap(map).values().stream().mapToLong(Long::longValue).sum();
        return Math.max(0,(long)com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability.STOMACH_CAPACITY_CENTS-used);
    }
    /** Returns the committed increase in stomach cents from the completion just performed. */
    private static long acceptedStomachCents(LivingEntity body, ServerLevel level, long roomBefore) {
        long roomAfter = availableStomach(body, level);
        return Math.max(0, roomBefore - roomAfter);
    }
}
