package com.juicyslew.moonstation14.ms14.timed_interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stateless transitions for actor-owned timed operations. Terminal bars linger 10 ticks. */
public final class TimedInteractionEngine {
    public static final int TERMINAL_LINGER_TICKS = 10;
    private TimedInteractionEngine() { }
    public enum StartResult { STARTED, DUPLICATE_CANCELLED, DENIED }
    public record Transition(TimedOperationState state, List<TimedOperation> completed) {
        public Transition { completed = List.copyOf(completed); }
    }
    public record Start(TimedOperationState state, StartResult result, TimedOperation operation) { }

    /** Duplicate kind+source cancels the existing active operation and rejects this start. */
    public static Start start(TimedOperationState state, String kind, String source, long now,
                              int duration, boolean breakOnDamage, boolean breakOnMove) {
        Objects.requireNonNull(state); Objects.requireNonNull(kind); Objects.requireNonNull(source);
        if (now < 0 || duration < 1) return new Start(state, StartResult.DENIED, null);
        Math.addExact(now, duration);
        for (int i = 0; i < state.operations().size(); i++) {
            TimedOperation old = state.operations().get(i);
            if (old.phase() == TimedOperation.Phase.ACTIVE && old.kind().equals(kind) && old.sourceKey().equals(source)) {
                if (now < old.startTick()) throw new IllegalArgumentException("tick precedes operation start");
                ArrayList<TimedOperation> ops = new ArrayList<>(state.operations());
                ops.set(i, terminal(old, TimedOperation.Phase.CANCELLED, now));
                return new Start(next(state, ops, state.nextId()), StartResult.DUPLICATE_CANCELLED, null);
            }
        }
        long nextId = Math.addExact(state.nextId(), 1);
        TimedOperation op = new TimedOperation(state.nextId(), kind, source, now, duration,
                TimedOperation.Phase.ACTIVE, -1, breakOnDamage, breakOnMove);
        ArrayList<TimedOperation> ops = new ArrayList<>(state.operations()); ops.add(op);
        return new Start(next(state, ops, nextId), StartResult.STARTED, op);
    }

    /** Advances due active operations exactly once; completion consumers run from returned list. */
    public static Transition advance(TimedOperationState state, long now) {
        if (now < 0) throw new IllegalArgumentException("negative tick");
        ArrayList<TimedOperation> ops = new ArrayList<>(); ArrayList<TimedOperation> completed = new ArrayList<>();
        for (TimedOperation op : state.operations()) {
            if (op.phase() == TimedOperation.Phase.ACTIVE && now >= op.dueTick()) {
                TimedOperation done = terminal(op, TimedOperation.Phase.COMPLETED, op.dueTick()); ops.add(done); completed.add(done);
            } else ops.add(op);
        }
        return new Transition(ops.equals(state.operations()) ? state : next(state, ops, state.nextId()), completed);
    }

    /** Successful repeat keeps operation identity and starts a full duration at now. */
    public static TimedOperationState repeat(TimedOperationState state, long id, long now, int duration) {
        if (now < 0 || duration < 1) throw new IllegalArgumentException("invalid repeat timing");
        Math.addExact(now, duration);
        ArrayList<TimedOperation> ops = new ArrayList<>(state.operations());
        for (int i=0;i<ops.size();i++) { TimedOperation o=ops.get(i); if(o.id()==id && o.phase()==TimedOperation.Phase.COMPLETED) {
            ops.set(i,new TimedOperation(o.id(),o.kind(),o.sourceKey(),now,duration,TimedOperation.Phase.ACTIVE,-1,o.breakOnDamage(),o.breakOnMove()));
            return next(state,ops,state.nextId());
        }}
        return state;
    }
    public static TimedOperationState cancel(TimedOperationState state, long id, long now) {
        return change(state,id,now,TimedOperation.Phase.CANCELLED);
    }
    public static TimedOperationState cancelForDamage(TimedOperationState s,long now) { return cancelMatching(s,now,true,false); }
    public static TimedOperationState cancelForMovement(TimedOperationState s,long now) { return cancelMatching(s,now,false,true); }
    private static TimedOperationState cancelMatching(TimedOperationState s,long now,boolean damage,boolean movement) {
        if(now<0)throw new IllegalArgumentException("negative tick");
        ArrayList<TimedOperation> ops=new ArrayList<>(s.operations()); boolean changed=false;
        for(int i=0;i<ops.size();i++){TimedOperation o=ops.get(i); if(o.phase()==TimedOperation.Phase.ACTIVE && ((!damage||o.breakOnDamage())&&(!movement||o.breakOnMove()))){if(now<o.startTick())throw new IllegalArgumentException("tick precedes operation start");ops.set(i,terminal(o,TimedOperation.Phase.CANCELLED,now));changed=true;}}
        return changed?next(s,ops,s.nextId()):s;
    }
    private static TimedOperationState change(TimedOperationState s,long id,long now,TimedOperation.Phase phase){
        if(now<0)throw new IllegalArgumentException("negative tick"); ArrayList<TimedOperation> ops=new ArrayList<>(s.operations());
        for(int i=0;i<ops.size();i++){TimedOperation o=ops.get(i);if(o.id()==id&&o.phase()==TimedOperation.Phase.ACTIVE){if(now<o.startTick())throw new IllegalArgumentException("tick precedes operation start");ops.set(i,terminal(o,phase,now));return next(s,ops,s.nextId());}} return s;
    }
    public static TimedOperationState prune(TimedOperationState s,long now){
        if(now<0)throw new IllegalArgumentException("negative tick");
        List<TimedOperation> ops=s.operations().stream().filter(o->o.phase()==TimedOperation.Phase.ACTIVE||now-o.terminalTick()<TERMINAL_LINGER_TICKS).toList();
        return ops.size()==s.operations().size()?s:next(s,ops,s.nextId());
    }
    private static TimedOperation terminal(TimedOperation o,TimedOperation.Phase p,long tick){return new TimedOperation(o.id(),o.kind(),o.sourceKey(),o.startTick(),o.durationTicks(),p,Math.max(o.startTick(),tick),o.breakOnDamage(),o.breakOnMove());}
    private static TimedOperationState next(TimedOperationState s,List<TimedOperation> ops,long id){return new TimedOperationState(ops,Math.addExact(s.revision(),1),id);}
}
