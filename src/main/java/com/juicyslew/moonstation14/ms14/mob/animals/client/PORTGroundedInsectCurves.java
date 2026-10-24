package com.juicyslew.moonstation14.ms14.mob.animals.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Small Minecraft landed-motion adaptation for rigs without leg bones. */
final class PORTGroundedInsectCurves {
    private PORTGroundedInsectCurves() { }

    static AnimationDefinition build(boolean run, boolean butterfly) {
        float duration = run ? .38F : .7F;
        float roll = (run ? 2.3F : 1.2F) * (butterfly ? .8F : 1F);
        float closedWing = butterfly ? 62F : 8F;
        return AnimationDefinition.Builder.withLength(duration).looping()
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                key(0, 0, 0, -roll), key(duration * .25F, 0, 0, roll),
                key(duration * .5F, 0, 0, -roll), key(duration * .75F, 0, 0, roll), key(duration, 0, 0, -roll)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                pos(0, 0), pos(duration * .5F, run ? .08F : .025F), pos(duration, 0)))
            .addAnimation("wing_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                key(0, 0, 0, closedWing), key(duration, 0, 0, closedWing)))
            .addAnimation("wing_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                key(0, 0, 0, -closedWing), key(duration, 0, 0, -closedWing))).build();
    }
    private static Keyframe key(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x,y,z), AnimationChannel.Interpolations.LINEAR);
    }
    private static Keyframe pos(float t, float y) {
        return new Keyframe(t, KeyframeAnimations.posVec(0,y,0), AnimationChannel.Interpolations.LINEAR);
    }
}
