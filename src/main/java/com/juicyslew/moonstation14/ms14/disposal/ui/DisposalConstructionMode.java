package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Transient authoritative construction mode, scoped to the actor/body/mind/epoch tuple. */
public final class DisposalConstructionMode {
    public record Plan(UUID bodyUUID,long epoch,String prototype,int rotationOffset,long revision) { }
    record BodySession(UUID actor,UUID body,String mind,long epoch) { }
    record Entry(String prototype,int rotationOffset,long revision,long sequence,boolean active) { }
    record Transition(Entry entry,boolean changedPlan) { }
    private static final Map<BodySession,Entry> MODES=new HashMap<>();
    private static final List<Consumer<DisposalConstructionState>> INVALIDATION=new CopyOnWriteArrayList<>();
    private static final String DEFAULT=ModDisposals.id("disposal_pipe").toString();
    private DisposalConstructionMode(){}

    /** Unit-test seam and parent integration hook: pending placements should cancel on every invalidation callback. */
    public static void setInvalidationListener(Consumer<DisposalConstructionState> listener){INVALIDATION.clear();if(listener!=null)INVALIDATION.add(listener);}

    public static synchronized Optional<DisposalConstructionState> handle(ServerPlayer actor,DisposalConstructionRequest request){
        if(actor==null||request==null)return Optional.empty();
        var authority=HandActorAuthority.resolve(actor).orElse(null);
        if(authority==null||!authority.body().getUUID().equals(request.bodyUUID())||authority.epoch()!=request.epoch())return Optional.empty();
        BodySession session=session(authority); clearStale(session.actor(),session);
        Entry old=MODES.getOrDefault(session,new Entry(DEFAULT,0,0,0,false));
        Transition transition=transition(old,request,DisposalConstructionMode::supported);
        Entry next=transition.entry();
        if(next!=old)MODES.put(session,next);
        var state=snapshot(session,next);
        if(transition.changedPlan())INVALIDATION.forEach(listener->listener.accept(state));
        return Optional.of(state);
    }

    /** The exact immutable state transition used by the authenticated server handler. */
    static Transition transition(Entry old,DisposalConstructionRequest request,Predicate<String> supported){
        Objects.requireNonNull(old);Objects.requireNonNull(request);Objects.requireNonNull(supported);
        if(request.sequence()<=old.sequence())return new Transition(old,false);
        Entry next;
        switch(request.action()){
            case ENTER -> next=new Entry(old.prototype().isBlank()?DEFAULT:old.prototype(),old.rotationOffset(),old.revision()+(old.active()?0:1),request.sequence(),true);
            case EXIT -> next=new Entry("",0,old.revision()+1,request.sequence(),false);
            case ROTATE -> {if(!old.active())return new Transition(old,false);next=new Entry(old.prototype(),old.rotationOffset()+1,old.revision()+1,request.sequence(),true);}
            case SELECT -> {if(!supported.test(request.prototype()))return new Transition(old,false);next=new Entry(request.prototype(),0,old.revision()+1,request.sequence(),old.active());}
            default -> {return new Transition(old,false);}
        }
        boolean changedPlan=old.active()&&(request.action()==DisposalConstructionRequest.Action.EXIT||request.action()==DisposalConstructionRequest.Action.SELECT||request.action()==DisposalConstructionRequest.Action.ROTATE);
        return new Transition(next,changedPlan);
    }

    /** Resolves authority again and removes stale sessions before exposing a live plan. */
    public static synchronized Optional<Plan> activePlan(HandActorAuthority.Snapshot authority){
        if(authority==null)return Optional.empty();
        if(!HandActorAuthority.revalidate(authority.actor(),authority)){clearActor(authority.actor().getUUID());return Optional.empty();}
        BodySession session=session(authority);clearStale(session.actor(),session);Entry state=MODES.get(session);
        return state!=null&&state.active()?Optional.of(new Plan(authority.body().getUUID(),authority.epoch(),state.prototype(),state.rotationOffset(),state.revision())):Optional.empty();
    }
    /** Revalidation/lifecycle query; returns inactive snapshot if no current mode exists. */
    public static synchronized Optional<DisposalConstructionState> query(ServerPlayer actor){
        var authority=HandActorAuthority.resolve(actor).orElse(null);if(authority==null){if(actor!=null)clearActor(actor.getUUID());return Optional.empty();}
        BodySession session=session(authority);clearStale(session.actor(),session);return Optional.of(snapshot(session,MODES.getOrDefault(session,new Entry("",0,0,0,false))));
    }
    private static BodySession session(HandActorAuthority.Snapshot a){return new BodySession(a.actor().getUUID(),a.body().getUUID(),a.mindId().toString(),a.epoch());}
    static void clearStale(Map<BodySession,Entry> modes,BodySession keep){modes.keySet().removeIf(k->k.actor().equals(keep.actor())&&!k.equals(keep));}
    private static void clearStale(UUID actor,BodySession keep){clearStale(MODES,keep);}
    public static synchronized void clearActor(UUID actor){if(actor!=null)MODES.keySet().removeIf(k->k.actor().equals(actor));}
    private static DisposalConstructionState snapshot(BodySession k,Entry e){return new DisposalConstructionState(k.body(),k.epoch(),e.sequence(),e.active(),e.active()?e.prototype():"",e.active()?e.rotationOffset():0,e.revision());}
    private static boolean supported(String text){
        var id=ResourceLocation.tryParse(text);if(id==null||!"moonstation14".equals(id.getNamespace()))return false;
        var proto=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals().get(id);
        if(proto==null||proto.tube()==null||proto.construction()==null)return false;
        return Set.of("disposal_pipe","disposal_bend","disposal_trunk","disposal_junction","disposal_junction_flipped","disposal_y_junction","disposal_x_junction","disposal_tagger","disposal_router","disposal_router_flipped").contains(id.getPath());
    }
}
