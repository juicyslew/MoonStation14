package com.juicyslew.moonstation14.ms14.mobs.client;

/** Per-entity clip clock. Contains no entity/model references. */
final class AnimalAnimationTransition {
    static final float BLEND_SECONDS = .1f;
    private static final float CRAB_WALK_RUN_AUTHORED_RATE = 1.5F;
    static final class State {
        private String appearance, clip, source;
        private double changedAt, lastObservedAt = Double.NaN, lastPhaseAt = Double.NaN, lastX, lastZ;
        private float phase, sourcePhase;
        private boolean observed;

        boolean observe(String appearance, double now, double x, double z, double walkSpeed) {
            if (!appearance.equals(this.appearance)) reset(appearance);
            if (!isWalking(clip) && clip!=null && !Double.isNaN(lastPhaseAt) && now>lastPhaseAt)
                phase+=(float)(now-lastPhaseAt);
            lastPhaseAt=now;
            boolean discontinuity=observed && (now<=lastObservedAt || now-lastObservedAt>.25 || Math.hypot(x-lastX,z-lastZ)>2);
            if (discontinuity) { clip=null; source=null; phase=0; }
            if (!observed || discontinuity) {
                lastX=x; lastZ=z; lastObservedAt=now; observed=true;
                return false;
            }
            double elapsed=now-lastObservedAt, dx=x-lastX, dz=z-lastZ;
            lastX=x; lastZ=z; lastObservedAt=now;
            boolean moving=Math.hypot(dx,dz)>.001;
            if (moving && isWalking(clip)) {
                float cadence=com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation
                        .cadenceScale(dx/(elapsed*20),dz/(elapsed*20),walkSpeed);
                phase+=(float)elapsed*cadence*authoredPlaybackRate(appearance, clip);
            }
            return moving;
        }

        static float authoredPlaybackRate(String appearance, String clip) {
            return "crab".equals(appearance) && isWalking(clip)
                    ? CRAB_WALK_RUN_AUTHORED_RATE : 1F;
        }

        Selection select(String appearance, String requested, double now, boolean immediate) {
            if (!appearance.equals(this.appearance)) reset(appearance);
            if (immediate) {
                clip=requested; source=null; changedAt=now; phase=0;
                return new Selection(requested,requested,1,0,0);
            }
            if (clip==null) { clip=requested; changedAt=now; }
            else if (!clip.equals(requested)) {
                source=clip; sourcePhase=phase; clip=requested; phase=0; changedAt=now;
            }
            float t=Math.max(0,Math.min(1,(float)((now-changedAt)/BLEND_SECONDS)));
            float weight=t*t*(3-2*t);
            if(weight>=1) source=null;
            float from=source==null?phase:sourcePhase+(isWalking(source)?0:(float)Math.max(0,now-changedAt));
            return new Selection(source==null?clip:source,clip,weight,from,phase);
        }
        private void reset(String key) {
            appearance=key; clip=null; source=null; phase=0; sourcePhase=0;
            observed=false; lastObservedAt=Double.NaN; lastPhaseAt=Double.NaN;
        }
        private static boolean isWalking(String c) { return "walk".equals(c)||"run".equals(c); }
    }
    record Selection(String source,String destination,float destinationWeight,float sourcePhase,float destinationPhase) { }
}
