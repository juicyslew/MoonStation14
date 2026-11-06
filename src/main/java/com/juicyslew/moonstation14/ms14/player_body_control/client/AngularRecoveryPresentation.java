package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared render-only interpolation for the camera and body presentation. */
public final class AngularRecoveryPresentation {
    private static final Map<PlayerCharacterHarnessEntity, Entry> HISTORY = new WeakHashMap<>();

    private AngularRecoveryPresentation() { }

    public static Quaternionf orientation(PlayerCharacterHarnessEntity body, float partialTick) {
        if(body==null)return new Quaternionf();
        if(isExactOwned(body)) {
            HISTORY.remove(body);
            var committed=ownedPair(body);
            return committed==null?new Quaternionf():committedOrientation(committed,partialTick);
        }
        var state = body.angularRecoveryState().orElse(null);
        if (state == null) {
            HISTORY.remove(body);
            return new Quaternionf();
        }
        var q = state.orientation();
        if (!Double.isFinite(q.w()) || !Double.isFinite(q.x()) || !Double.isFinite(q.y()) || !Double.isFinite(q.z()))
            return new Quaternionf();
        var raw = new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
        if(!Float.isFinite(raw.x)||!Float.isFinite(raw.y)||!Float.isFinite(raw.z)||!Float.isFinite(raw.w)
                ||raw.lengthSquared()<1e-12f)return new Quaternionf();
        raw.normalize();
        long epoch=GhostControlClient.presentationCharacterEpoch();
        var level=body.level();
        Entry entry = HISTORY.get(body);
        if (entry == null || entry.epoch != epoch || entry.level != level) {
            entry = new Entry(epoch, level, new AngularRecoveryRenderInterpolation());
            HISTORY.put(body, entry);
        }
        return entry.interpolation.sample(body.tickCount, raw, partialTick);
    }

    public static boolean hasCommittedPresentation(PlayerCharacterHarnessEntity body) {
        return isExactOwned(body);
    }

    private static boolean isExactOwned(PlayerCharacterHarnessEntity body) {
        var level=body.level();long epoch=GhostControlClient.presentationCharacterEpoch();
        return level!=null&&level.isClientSide&&!body.isRemoved()&&epoch>0
                &&GhostControlClient.ownedCharacterForPresentation()==body;
    }

    private static com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair ownedPair(
            PlayerCharacterHarnessEntity body) {
        var level=body.level();
        long epoch=GhostControlClient.presentationCharacterEpoch();
        if(level==null||!level.isClientSide||epoch<=0||GhostControlClient.ownedCharacterForPresentation()!=body)return null;
        return com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore
                .publishedAngular(level,epoch,body.getUUID());
    }

    /** Production owned-body consumer for the publication's exact committed angular endpoints. */
    public static Quaternionf committedOrientation(
            com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair committed,
            float partialTick) {
        if(committed==null)return new Quaternionf();
        return AngularRecoveryRenderInterpolation.sampleCommittedEndpoints(
                committed.from().copy(),committed.to().copy(),partialTick);
    }

    private record Entry(long epoch, net.minecraft.world.level.Level level,
                         AngularRecoveryRenderInterpolation interpolation) { }
}
