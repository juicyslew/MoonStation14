package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Transient muzzle-flash prediction queue; presentation only. */
public final class GunMuzzleFlashClient {
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private GunMuzzleFlashClient() { }

    public record Entry(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body, long epoch,
                        ResourceLocation dimension, Vec3 position, Vec3 direction, long startTick) { }
    public record Sample(Entry entry, float alpha) { }
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;

    public static boolean enqueue(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body, long epoch,
                                  ResourceLocation dimension, Vec3 position, Vec3 direction, long startTick) {
        if (effect == null || body == null || epoch <= 0 || dimension == null || !finite(position)
                || !finite(direction) || startTick < 0) return false;
        ENTRIES.add(new Entry(effect, body, epoch, dimension, position, direction, startTick));
        return true;
    }

    /** Observer snapshots are scoped to the current client session, not the local controlled body's identity. */
    public static boolean enqueueObserver(ProjectileProfile.ProjectileSpriteEffectData effect, UUID shooter, long sessionEpoch,
                                          ResourceLocation dimension, Vec3 position, Vec3 direction, long receivedTick) {
        if(effect==null||shooter==null||dimension==null||!finite(position)||!finite(direction)||receivedTick<0)return false;
        ENTRIES.add(new Entry(effect,shooter,0,dimension,position,direction,receivedTick)); return true;
    }
    public static boolean observerEligible(com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload p,UUID player,ResourceLocation dimension){return p!=null&&player!=null&&dimension!=null&&p.dimension().equals(dimension)&&(p.kind()==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT||!p.initiator().equals(player));}
    public static boolean receiveObserver(com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload p){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.level==null||!observerEligible(p,mc.player.getUUID(),mc.level.dimension().location()))return false;return enqueueObserver(p.effect(),p.shooter(),1,p.dimension(),p.position(),p.direction(),com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());}

    public static List<Sample> sample(UUID body, long epoch, ResourceLocation dimension, long nowTick) {
        List<Sample> result = new ArrayList<>();
        Iterator<Entry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.dimension().equals(dimension) || (e.epoch()!=0 && (!e.body().equals(body) || e.epoch() != epoch))) { it.remove(); continue; }
            float duration = e.effect().lifetimeSeconds() * 20f;
            float elapsed = nowTick - e.startTick();
            if (elapsed < 0) continue;
            if (elapsed >= duration) { it.remove(); continue; }
            result.add(new Sample(e, 1f - elapsed / duration));
        }
        return List.copyOf(result);
    }
    public static List<Sample> sampleWorld(ResourceLocation dimension,long nowTick){List<Sample> result=new ArrayList<>();Iterator<Entry> it=ENTRIES.iterator();while(it.hasNext()){Entry e=it.next();if(!e.dimension().equals(dimension)){it.remove();continue;}float elapsed=nowTick-e.startTick(),duration=e.effect().lifetimeSeconds()*20f;if(elapsed<0)continue;if(elapsed>=duration){it.remove();continue;}result.add(new Sample(e,1f-elapsed/duration));}return List.copyOf(result);}
    public static void reconcileWorld(net.minecraft.client.multiplayer.ClientLevel level){if(observedLevel!=level){ENTRIES.clear();observedLevel=level;}}

    public static void clear() { ENTRIES.clear(); }
    public static void reconcile(UUID body, long epoch, ResourceLocation dimension) {
        ENTRIES.removeIf(e -> !e.dimension().equals(dimension) || (e.epoch()!=0&&(body==null||!e.body().equals(body)||e.epoch()!=epoch)));
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
