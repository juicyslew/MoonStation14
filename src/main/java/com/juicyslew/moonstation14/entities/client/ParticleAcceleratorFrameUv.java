package com.juicyslew.moonstation14.entities.client;

/** UV selection for the imported 3x3-packed SS14 four-direction, two-frame RSI state. */
final class ParticleAcceleratorFrameUv {
    private static final int COLUMNS=3;
    private static final int DIRECTIONS=4;
    private static final int FRAMES_PER_DIRECTION=2;
    private static final float TILE=1f/3f;
    private static final float PAD=.5f/96f;
    private ParticleAcceleratorFrameUv() { }

    static int frame(int tick,int direction) {
        int safeDirection=Math.max(0,Math.min(DIRECTIONS-1,direction));
        int animationFrame=Math.floorMod(tick,FRAMES_PER_DIRECTION*2)/2;
        return safeDirection*FRAMES_PER_DIRECTION+animationFrame;
    }

    /** RsiDirection enum/storage order is S,N,E,W; Minecraft's positive Z points south. */
    static int direction(double motionX,double motionZ) {
        if(Math.abs(motionX)+Math.abs(motionZ)<1e-6) return 0;
        double angle=Math.atan2(motionX,motionZ);
        int cardinal=Math.floorMod((int)Math.floor(angle/(Math.PI/2)+.5),4);
        return switch(cardinal) {
            case 0 -> 0; // South (+Z)
            case 1 -> 2; // East (+X)
            case 2 -> 1; // North (-Z)
            default -> 3; // West (-X)
        };
    }

    static float[] bounds(int tick,int direction) {
        int index=frame(tick,direction);
        float left=(index%COLUMNS)*TILE+PAD;
        float top=(index/COLUMNS)*TILE+PAD;
        return new float[]{left,top,left+TILE-2*PAD,top+TILE-2*PAD};
    }
}
