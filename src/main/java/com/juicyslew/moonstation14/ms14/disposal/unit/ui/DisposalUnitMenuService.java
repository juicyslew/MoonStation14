package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.containment.DisposalContainmentService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Menu authority and interrupted self-entry do-after lifecycle. */
public final class DisposalUnitMenuService {
    private record Pending(ServerPlayer actor, DisposalUnitMenu menu, DisposalUnitBlockEntity unit,
                           LivingEntity body, HandActorAuthority.Snapshot authority, BlockPos start, net.minecraft.world.phys.Vec3 startPosition,
                           float health, long handRevision, long started, long requiredTicks) { }
    private static final Map<DisposalUnitMenu,Pending> PENDING=new WeakHashMap<>();
    private DisposalUnitMenuService(){}
    public static boolean isBodyContained(ServerPlayer actor) {
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        return authority != null && authority.body().level() instanceof ServerLevel level
                && DisposalContainmentService.forLevel(level).ownerOf(authority.body()).isPresent();
    }
    /** Pre-admission exception predicate only; callers must still run BodyHandRequestService admission. */
    public static boolean isExactContainingUnitTarget(ServerPlayer actor, BodyBlockUseRequest request) {
        if (actor == null || request == null || !(request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                || request.intent() == BodyBlockUseRequest.Intent.USE)) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || !(authority.body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !level.getWorldBorder().isWithinBounds(request.target())) return false;
        var chunk = level.getChunkSource().getChunkNow(request.target().getX() >> 4, request.target().getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || !(chunk.getBlockEntity(request.target()) instanceof DisposalUnitBlockEntity unit)) return false;
        boolean exactOwner = DisposalContainmentService.forLevel(level).ownerOf(authority.body()).filter(owner -> owner == unit).isPresent()
                && unit.bodyIds().contains(authority.body().getUUID());
        return containedBlockUseAllowed(exactOwner, unit.isValid(), request.intent());
    }

    static boolean containedBlockUseAllowed(boolean exactOwnerUnit, boolean validTargetUnit,
                                            BodyBlockUseRequest.Intent intent) {
        return exactOwnerUnit && validTargetUnit
                && (intent == BodyBlockUseRequest.Intent.ACTIVATE_WORLD || intent == BodyBlockUseRequest.Intent.USE);
    }
    public static boolean open(ServerPlayer actor, DisposalUnitBlockEntity unit, HandActorAuthority.Snapshot authority) {
        if(actor==null||unit==null||authority==null||authority.actor()!=actor||!unit.isValid()
                ||!HandActorAuthority.revalidate(actor,authority)||!(actor.level() instanceof ServerLevel level)
                ||unit.getLevel()!=level||authority.body().level()!=level) return false;
        var currentOwner = DisposalContainmentService.forLevel(level).ownerOf(authority.body());
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
        if(unit.bodyIds().contains(body.getUUID())||!CharacterControlSystem.canAct(body)||hands==null||!hands.compatible(body)||hands.handIds().isEmpty())return;
        var prototype=unit.prototype();if(prototype==null)return;
        long requiredTicks=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.ticks(prototype.entrySeconds());
        PENDING.put(menu,new Pending(actor,menu,unit,body,menu.authority(),body.blockPosition(),body.position(),body.getHealth(),hands.revision(),((ServerLevel)actor.level()).getGameTime(),requiredTicks)); menu.update(menu.state(),1,unit.bodyIds().size());
    }
    public static void playerTick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;
        for(Pending p:new ArrayList<>(PENDING.values())) if(p.actor()==actor) {
            var menu=p.menu(); var unit=unit(menu); LiveHands hands=p.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            boolean valid=actor.containerMenu==menu&&menu.stillValid(actor)&&unit==p.unit()&&p.unit().isValid()
                    &&HandActorAuthority.revalidateInMenu(actor,p.authority(),menu)&&CharacterControlSystem.canAct(p.body())
                    &&p.body().blockPosition().equals(p.start())&&p.body().position().distanceToSqr(p.startPosition())<1.0e-6&&p.body().getHealth()>=p.health()
                    &&hands!=null&&hands.compatible(p.body())&&hands.revision()==p.handRevision()&&!p.unit().bodyIds().contains(p.body().getUUID());
            if(!valid){PENDING.remove(menu);menu.update(menu.state(),3,p.unit().bodyIds().size());continue;}
            long elapsed=((ServerLevel)actor.level()).getGameTime()-p.started();
            var decision=DisposalUnitEntryPolicy.tick(elapsed,p.requiredTicks(),actor.containerMenu==menu,unit==p.unit(),p.body().getUUID().equals(p.authority().body().getUUID()),HandActorAuthority.revalidateInMenu(actor,p.authority(),menu),CharacterControlSystem.canAct(p.body()),p.body().blockPosition().equals(p.start())&&p.body().position().distanceToSqr(p.startPosition())<1.0e-6,p.body().getHealth()>=p.health(),hands!=null&&hands.revision()==p.handRevision());
            if(decision==DisposalUnitEntryPolicy.TickResult.CANCEL){PENDING.remove(menu);menu.update(menu.state(),3,p.unit().bodyIds().size());continue;}
            if(decision==DisposalUnitEntryPolicy.TickResult.WAIT)continue;
            PENDING.remove(menu); boolean entered=p.unit().insertBody(p.body()); menu.update(menu.state(),entered?2:4,p.unit().bodyIds().size());
        }
        if(actor.containerMenu instanceof DisposalUnitMenu menu)refresh(menu);
    }
    private static boolean allowedOperator(DisposalUnitMenu menu,DisposalUnitBlockEntity unit,LivingEntity body){
        if(!menu.stillValid(menu.authority().actor())||!unit.isValid())return false;
        var owner=DisposalContainmentService.forLevel(unit.level()).ownerOf(body).orElse(null);
        boolean contained=owner!=null;boolean exactOwner=owner==unit&&unit.bodyIds().contains(body.getUUID());
        boolean reachable=exactOwner||body.distanceToSqr(unit.getBlockPos().getX()+.5,unit.getBlockPos().getY()+.5,unit.getBlockPos().getZ()+.5)<=64;
        return DisposalUnitEntryPolicy.mayOperate(true,reachable,contained,exactOwner,contained&&owner!=unit);
    }
    private static DisposalUnitBlockEntity unit(DisposalUnitMenu menu){return menu.authority()==null||menu.authority().actor().level()!=menu.authority().body().level()?null:menu.authority().actor().level().getBlockEntity(menu.pos()) instanceof DisposalUnitBlockEntity u&&u.identity().equals(menu.unitId())?u:null;}
    private static void refresh(DisposalUnitMenu menu){var u=unit(menu);if(u!=null)menu.update(u.getBlockState().getValue(com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock.POWERED)?1:0,menu.result(),u.bodyIds().size());}
}
