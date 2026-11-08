package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Menu authority and interrupted self-entry do-after lifecycle. */
public final class DisposalUnitMenuService {
    private record Pending(ServerPlayer actor, DisposalUnitMenu menu, DisposalUnitBlockEntity unit,
                           LivingEntity body, HandActorAuthority.Snapshot authority, BlockPos start, net.minecraft.world.phys.Vec3 startPosition,
                           float health, long handRevision, long started, long requiredTicks) { }
    private static final Map<DisposalUnitMenu,Pending> PENDING=new WeakHashMap<>();
    private record Direct(ServerPlayer actor, DisposalUnitSelfEntryAttempt attempt,
                          HandActorAuthority.Snapshot authority) {
        DisposalUnitBlockEntity unit() { return attempt.unit(); }
        LivingEntity body() { return attempt.body(); }
    }
    private static final Map<ServerPlayer,Direct> DIRECT=new WeakHashMap<>();
    private DisposalUnitMenuService(){}
    public static void playerLoggedOut(ServerPlayer actor) {
        if (actor == null) return;
        DIRECT.remove(actor);
        PENDING.values().removeIf(p -> p.actor() == actor);
    }
    public static void onServerStopped() {
        DIRECT.clear();
        PENDING.clear();
    }
    public static void typedDamageApplied(com.juicyslew.moonstation14.ms14.damage.TypedDamageAppliedEvent event) {
        double amount = event.delta().values().stream().filter(v -> v > 0f).mapToDouble(Float::doubleValue).sum();
        if (!DisposalUnitEntryPolicy.damageCancels((float) amount)) return;
        LivingEntity body = event.entity();
        for (var entry : new ArrayList<>(DIRECT.entrySet())) if (entry.getValue().body() == body) {
            Direct pending = entry.getValue(); DIRECT.remove(entry.getKey());
            entry.getKey().displayClientMessage(Component.literal("Disposal entry cancelled: damage."), true);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Disposal self-entry cancelled body={} unit={} cause=DAMAGE", body.getUUID(), pending.unit().getBlockPos());
        }
        for (Pending pending : new ArrayList<>(PENDING.values())) if (pending.body() == body) {
            PENDING.remove(pending.menu()); pending.menu().update(pending.menu().state(),3,pending.unit().bodyIds().size());
        }
    }
    public static boolean isBodyContained(ServerPlayer actor) {
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        return authority != null && authority.body().level() instanceof ServerLevel level
                && BodyContainmentService.forLevel(level).ownerOf(authority.body()).isPresent();
    }
    /** Fresh identity proof for using this exact owner menu as the controlled body's HUD context. */
    public static boolean isExactContainedBodyMenu(ServerPlayer actor, LivingEntity body,
                                                    net.minecraft.world.inventory.AbstractContainerMenu candidate) {
        if (!(candidate instanceof DisposalUnitMenu menu) || actor == null || body == null
                || actor.containerMenu != menu || !(body.level() instanceof ServerLevel level)
                || actor.level() != level || !level.getServer().isSameThread()) return false;
        var owner = BodyContainmentService.forLevel(level).ownerOf(body).orElse(null);
        var unit = unit(menu);
        return owner != null && owner == unit && unit != null && unit.isValid()
                && menu.authority() != null && menu.authority().actor() == actor
                && menu.authority().body() == body && unit.bodyIds().contains(body.getUUID())
                && HandActorAuthority.revalidateInMenu(actor, menu.authority(), menu);
    }
    /** Pre-admission exception predicate only; callers must still run BodyHandRequestService admission. */
    public static boolean isExactContainingUnitTarget(ServerPlayer actor, BodyBlockUseRequest request) {
        if (actor == null || request == null || !(request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                || request.intent() == BodyBlockUseRequest.Intent.CLIMB_INSIDE
                || request.intent() == BodyBlockUseRequest.Intent.USE)) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || !(authority.body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !level.getWorldBorder().isWithinBounds(request.target())) return false;
        var chunk = level.getChunkSource().getChunkNow(request.target().getX() >> 4, request.target().getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || !(chunk.getBlockEntity(request.target()) instanceof DisposalUnitBlockEntity unit)) return false;
        boolean exactOwner = BodyContainmentService.forLevel(level).ownerOf(authority.body()).filter(owner -> owner == unit).isPresent()
                && unit.bodyIds().contains(authority.body().getUUID());
        return containedBlockUseAllowed(exactOwner, unit.isValid(), request.intent());
    }

    static boolean containedBlockUseAllowed(boolean exactOwnerUnit, boolean validTargetUnit,
                                            BodyBlockUseRequest.Intent intent) {
        return exactOwnerUnit && validTargetUnit
                && (intent == BodyBlockUseRequest.Intent.ACTIVATE_WORLD || intent == BodyBlockUseRequest.Intent.CLIMB_INSIDE
                || intent == BodyBlockUseRequest.Intent.USE);
    }
    static boolean directEntryAdmissionAllowed(boolean validUnit, boolean freshAuthority, boolean reachable,
                                               boolean ableToAct, boolean alreadyContained) {
        return validUnit && freshAuthority && reachable && ableToAct && !alreadyContained;
    }
    public static boolean open(ServerPlayer actor, DisposalUnitBlockEntity unit, HandActorAuthority.Snapshot authority) {
        if(actor==null||unit==null||authority==null||authority.actor()!=actor||!unit.isValid()
                ||!HandActorAuthority.revalidate(actor,authority)||!(actor.level() instanceof ServerLevel level)
                ||unit.getLevel()!=level||authority.body().level()!=level) return false;
        var currentOwner = BodyContainmentService.forLevel(level).ownerOf(authority.body());
        if (currentOwner.isPresent() && currentOwner.orElseThrow() != unit) return false;
        UUID session=UUID.randomUUID(); actor.openMenu(new SimpleMenuProvider((id,inv,p)->new DisposalUnitMenu(id,unit.getBlockPos(),unit.identity(),session,level,authority,new net.minecraft.world.inventory.SimpleContainerData(3)), Component.literal("Disposal Unit")),buf->{buf.writeBlockPos(unit.getBlockPos());buf.writeUUID(unit.identity());buf.writeUUID(session);});
        if(actor.containerMenu instanceof DisposalUnitMenu menu&&menu.session().equals(session)){refresh(menu);return true;} return false;
    }
    public static void apply(ServerPlayer actor, DisposalUnitAction request) {
        if(!(actor.containerMenu instanceof DisposalUnitMenu menu)||request.containerId()!=menu.containerId||!request.session().equals(menu.session())||!menu.stillValid(actor))return;
        DisposalUnitBlockEntity unit=unit(menu); LivingEntity body=menu.authority().body(); if(unit==null||body==null)return;
        switch(request.operation()) {
            case CLIMB_INSIDE -> begin(actor,menu,unit,body);
            case ENGAGE -> {if(allowedOperator(menu,unit,body))unit.engage();}
            case POWER -> {if(allowedOperator(menu,unit,body))unit.setPowerSwitch(!unit.powerSwitch());}
            case EJECT -> {if(allowedOperator(menu,unit,body))unit.ejectAll();}
        }
        refresh(menu);
    }
    private static void begin(ServerPlayer actor,DisposalUnitMenu menu,DisposalUnitBlockEntity unit,LivingEntity body) {
        LiveHands hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(unit.bodyIds().contains(body.getUUID())||!CharacterControlSystem.canAct(body)||hands==null||!hands.compatible(body))return;
        var prototype=unit.prototype();if(prototype==null)return;
        long requiredTicks=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.ticks(prototype.entrySeconds());
        PENDING.put(menu,new Pending(actor,menu,unit,body,menu.authority(),body.blockPosition(),body.position(),body.getHealth(),hands.revision(),((ServerLevel)actor.level()).getGameTime(),requiredTicks)); menu.update(menu.state(),1,unit.bodyIds().size());
    }
    /** Starts authoritative self-entry from a fresh body-block admission; no menu or power dependency. */
    public static boolean beginDirect(ServerPlayer actor, DisposalUnitBlockEntity unit, HandActorAuthority.Snapshot authority) {
        if (actor == null) return false;
        String rejection = "authority changed";
        if (unit != null && unit.isValid() && authority != null && authority.actor() == actor
                && actor.level() instanceof ServerLevel level && authority.body().level() == level
                && HandActorAuthority.revalidate(actor, authority)) {
            var body = authority.body();
            if (unit.getLevel() != level) rejection = "unit changed";
            else if (unit.bodyIds().contains(body.getUUID()) || BodyContainmentService.forLevel(level).ownerOf(body).isPresent()) rejection = "already contained";
            else if (!CharacterControlSystem.canAct(body)) rejection = "cannot act";
            else if (body.distanceToSqr(unit.getBlockPos().getX()+.5, unit.getBlockPos().getY()+.5, unit.getBlockPos().getZ()+.5) > 36) rejection = "too far";
            else if (unit.prototype() == null) rejection = "unit changed";
            else {
                DIRECT.put(actor, new Direct(actor, new DisposalUnitSelfEntryAttempt(unit, body, level.getGameTime()), authority));
                var direct = DIRECT.get(actor);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Disposal self-entry started body={} unit={} requiredTicks={}", body.getUUID(), unit.getBlockPos(), direct.attempt().requiredTicks());
                actor.displayClientMessage(Component.literal("Climbing into disposal unit…"), true);
                return true;
            }
        }
        actor.displayClientMessage(Component.literal("Cannot enter disposal unit: " + rejection + "."), true);
        return false;
    }
    /** One authoritative clock driver for all actors, independent of player/body physical ticks. */
    public static void serverTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        Set<ServerPlayer> actors = Collections.newSetFromMap(new IdentityHashMap<>());
        actors.addAll(server.getPlayerList().getPlayers());
        for (ServerPlayer actor : DIRECT.keySet()) if (actor.getServer() == server) actors.add(actor);
        for (Pending pending : PENDING.values()) {
            ServerPlayer actor = pending.actor();
            if (actor.getServer() == server) actors.add(actor);
        }
        for (ServerPlayer actor : actors) if (actor.getServer() == server) advanceActor(actor);
    }
    private static void advanceActor(ServerPlayer actor) {
        Direct direct = DIRECT.get(actor);
        if (direct != null) {
            var body = direct.body(); var unit = direct.unit();
            String cancel = null;
            if (!HandActorAuthority.revalidate(actor, direct.authority())) cancel = "AUTHORITY_CHANGED";
            else if (actor.level() != body.level() || !unit.isValid() || !unit.identity().equals(direct.attempt().unit().identity()) || unit.getLevel() != body.level() || body.level().getBlockEntity(unit.getBlockPos()) != unit) cancel = "UNIT_CHANGED";
            else if (!CharacterControlSystem.canAct(body)) cancel = "CANNOT_ACT";
            else if (body.distanceToSqr(unit.getBlockPos().getX()+.5, unit.getBlockPos().getY()+.5, unit.getBlockPos().getZ()+.5) > 36) cancel = "TOO_FAR";
            if (cancel != null) {
                DIRECT.remove(actor);
                actor.displayClientMessage(Component.literal("Disposal entry cancelled: " + cancel.toLowerCase(Locale.ROOT).replace('_',' ') + "."), true);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Disposal self-entry cancelled body={} unit={} cause={}", body.getUUID(), unit.getBlockPos(), cancel);
            } else {
                var outcome = direct.attempt().advance(((ServerLevel) actor.level()).getGameTime(), true);
                if (outcome.result() != DisposalUnitSelfEntryAttempt.Result.WAIT) {
                    DIRECT.remove(actor);
                    boolean entered = outcome.result() == DisposalUnitSelfEntryAttempt.Result.ENTERED;
                    actor.displayClientMessage(Component.literal(entered ? "Entered disposal unit." : outcome.result() == DisposalUnitSelfEntryAttempt.Result.FAILED ? "Disposal entry failed." : "Disposal entry cancelled: " + outcome.reason().name().toLowerCase(Locale.ROOT).replace('_',' ') + "."), true);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Disposal self-entry completed body={} unit={} elapsedTicks={} requiredTicks={} result={} reason={}", body.getUUID(), unit.getBlockPos(), ((ServerLevel)actor.level()).getGameTime()-direct.attempt().startedWorldTick(), direct.attempt().requiredTicks(), outcome.result(), outcome.reason());
                }
            }
        }
        for(Pending p:new ArrayList<>(PENDING.values())) if(p.actor()==actor) {
            var menu=p.menu(); var unit=unit(menu); LiveHands hands=p.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            boolean valid=actor.containerMenu==menu&&menu.stillValid(actor)&&unit==p.unit()&&p.unit().isValid()
                    &&HandActorAuthority.revalidateInMenu(actor,p.authority(),menu)&&CharacterControlSystem.canAct(p.body())
                    &&DisposalUnitEntryPolicy.withinMovementThreshold(p.startPosition(),p.body().position())
                    &&hands!=null&&hands.compatible(p.body())&&hands.revision()==p.handRevision()&&!p.unit().bodyIds().contains(p.body().getUUID());
            if(!valid){PENDING.remove(menu);menu.update(menu.state(),3,p.unit().bodyIds().size());continue;}
            long elapsed=((ServerLevel)actor.level()).getGameTime()-p.started();
            var decision=DisposalUnitEntryPolicy.tick(elapsed,p.requiredTicks(),actor.containerMenu==menu,unit==p.unit(),p.body().getUUID().equals(p.authority().body().getUUID()),HandActorAuthority.revalidateInMenu(actor,p.authority(),menu),CharacterControlSystem.canAct(p.body()),DisposalUnitEntryPolicy.withinMovementThreshold(p.startPosition(),p.body().position()));
            if(decision==DisposalUnitEntryPolicy.TickResult.CANCEL){PENDING.remove(menu);menu.update(menu.state(),3,p.unit().bodyIds().size());continue;}
            if(decision==DisposalUnitEntryPolicy.TickResult.WAIT)continue;
            PENDING.remove(menu); boolean entered=p.unit().insertBody(p.body()); menu.update(menu.state(),entered?2:4,p.unit().bodyIds().size());
        }
        if(actor.containerMenu instanceof DisposalUnitMenu menu)refresh(menu);
    }
    private static boolean allowedOperator(DisposalUnitMenu menu,DisposalUnitBlockEntity unit,LivingEntity body){
        if(!menu.stillValid(menu.authority().actor())||!unit.isValid())return false;
        var owner=BodyContainmentService.forLevel(unit.level()).ownerOf(body).orElse(null);
        boolean contained=owner!=null;boolean exactOwner=owner==unit&&unit.bodyIds().contains(body.getUUID());
        boolean reachable=exactOwner||body.distanceToSqr(unit.getBlockPos().getX()+.5,unit.getBlockPos().getY()+.5,unit.getBlockPos().getZ()+.5)<=64;
        return DisposalUnitEntryPolicy.mayOperate(true,reachable,contained,exactOwner,contained&&owner!=unit);
    }
    private static DisposalUnitBlockEntity unit(DisposalUnitMenu menu){return menu.authority()==null||menu.authority().actor().level()!=menu.authority().body().level()?null:menu.authority().actor().level().getBlockEntity(menu.pos()) instanceof DisposalUnitBlockEntity u&&u.identity().equals(menu.unitId())?u:null;}
    private static void refresh(DisposalUnitMenu menu){var u=unit(menu);if(u!=null)menu.update(u.getBlockState().getValue(com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock.POWERED)?1:0,menu.result(),u.bodyIds().size());}
}

