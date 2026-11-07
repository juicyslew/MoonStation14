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
                        ResourceLocation dimension, Vec3 position, Vec3 direction, long startTick, Vec3 endpoint, com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind,
                        com.juicyslew.moonstation14.ms14.combat.ShotVisualSource source, ActualMuzzleFrame resolvedSource) {
        public Entry(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body,long epoch,ResourceLocation dimension,Vec3 position,Vec3 direction,long startTick,Vec3 endpoint,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind){this(effect,body,epoch,dimension,position,direction,startTick,endpoint,kind,null,null);}
        public Entry(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body,long epoch,ResourceLocation dimension,Vec3 position,Vec3 direction,long startTick){this(effect,body,epoch,dimension,position,direction,startTick,null,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE);}
    }
    public record Sample(Entry entry, float alpha, ActualMuzzleFrame sourceFrame) {
        public Sample(Entry entry,float alpha){this(entry,alpha,null);}
        public Vec3 origin(){return sourceFrame==null?entry.position():sourceFrame.muzzle();}
        public Vec3 direction(){return sourceFrame==null?entry.direction():sourceFrame.bore();}
        /** Sprite-art centering only; never changes the native muzzle source or beam origin. */
        public Vec3 pointOrigin(){
            if(entry.kind()==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT)return entry.position();
            if(sourceFrame==null)return entry.position();
            return sourceFrame.muzzle().add(sourceFrame.bore().normalize().scale(entry.effect().forwardOffset()));
        }
        /** A ray from the actual muzzle only travels forward along the authored barrel bore. */
        public boolean hasForwardTravel(double epsilon){
            if(entry.endpoint()==null||!Double.isFinite(epsilon)||epsilon<0)return false;
            Vec3 ray=entry.endpoint().subtract(origin());
            return ray.lengthSqr()>epsilon*epsilon && ray.dot(direction().normalize())>epsilon;
        }
    }
    private static final java.util.Map<Entry,MuzzleSourceResolution> SOURCE_STATES=new java.util.IdentityHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;

    public static boolean enqueue(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body, long epoch,
                                  ResourceLocation dimension, Vec3 position, Vec3 direction, long startTick) {
        return enqueue(effect,body,epoch,dimension,position,direction,startTick,null);
    }
    public static boolean enqueue(ProjectileProfile.ProjectileSpriteEffectData effect, UUID body, long epoch,
                                  ResourceLocation dimension, Vec3 position, Vec3 direction, long startTick,
                                  com.juicyslew.moonstation14.ms14.combat.ShotVisualSource source) {
        if (effect == null || body == null || epoch <= 0 || dimension == null || !finite(position)
                || !finite(direction) || startTick < 0) return false;
        ENTRIES.add(new Entry(effect, body, epoch, dimension, position, direction, startTick,null,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE,source,null));
        return true;
    }

    /** Observer snapshots are scoped to the current client session, not the local controlled body's identity. */
    public static boolean enqueueObserver(ProjectileProfile.ProjectileSpriteEffectData effect, UUID shooter, long sessionEpoch,
                                          ResourceLocation dimension, Vec3 position, Vec3 direction, long receivedTick) {
        if(effect==null||shooter==null||dimension==null||!finite(position)||!finite(direction)||receivedTick<0)return false;
        return enqueueObserver(effect,shooter,dimension,position,direction,receivedTick,null);
    }
    public static boolean enqueueObserver(ProjectileProfile.ProjectileSpriteEffectData effect,UUID shooter,ResourceLocation dimension,Vec3 position,Vec3 direction,long tick,Vec3 endpoint){return enqueueObserver(effect,shooter,dimension,position,direction,tick,endpoint,endpoint==null?com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE:com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM);}
    public static boolean enqueueObserver(ProjectileProfile.ProjectileSpriteEffectData effect,UUID shooter,ResourceLocation dimension,Vec3 position,Vec3 direction,long tick,Vec3 endpoint,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind){return enqueueObserver(effect,shooter,dimension,position,direction,tick,endpoint,kind,null);}
    public static boolean enqueueObserver(ProjectileProfile.ProjectileSpriteEffectData effect,UUID shooter,ResourceLocation dimension,Vec3 position,Vec3 direction,long tick,Vec3 endpoint,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind,com.juicyslew.moonstation14.ms14.combat.ShotVisualSource source){if(effect==null||shooter==null||dimension==null||kind==null||!finite(position)||!finite(direction)||(endpoint!=null&&(!finite(endpoint)||endpoint.distanceTo(position)<1e-5||endpoint.distanceTo(position)>20.01))||tick<0)return false;ENTRIES.add(new Entry(effect,shooter,0,dimension,position,direction,tick,endpoint,kind,source,null));return true;}
    public static boolean observerEligible(com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload p,UUID player,ResourceLocation dimension){return p!=null&&player!=null&&dimension!=null&&p.dimension().equals(dimension)&&(p.kind()!=com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE||!p.initiator().equals(player));}
    public static boolean receiveObserver(com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload p){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.level==null||!observerEligible(p,mc.player.getUUID(),mc.level.dimension().location()))return false;return enqueueObserver(p.effect(),p.shooter(),p.dimension(),p.position(),p.direction(),com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock(),p.endpoint(),p.kind(),p.source());}

    /** Resolve a sourced visual once; absent captures wait only while its render frame is current. */
    public static Sample resolveSample(Sample sample,long frame) {
        Entry entry=sample.entry();
        if(entry.kind()==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT)return new Sample(entry,sample.alpha(),null);
        if(entry.source()==null)return null;
        var state=SOURCE_STATES.computeIfAbsent(entry,k->new MuzzleSourceResolution());
        var mc=net.minecraft.client.Minecraft.getInstance();
        var entity=mc.level==null?null:findBody(mc.level,entry.body());
        var snapshot=entity==null?null:com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking.clientCache().get(entry.dimension(),entry.body(),entity.getId()).orElse(null);
        boolean matching=snapshot!=null&&snapshot.hands().stream().anyMatch(h->h.id().equals(entry.source().handId())&&h.item()!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(h.item().stack().getItem()).equals(entry.source().itemId()));
        if(!matching){removeEntry(entry);return null;}
        ActualMuzzleFrame frozen=state.resolve((int)frame,()->true,()->com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.actualMuzzle(entry.dimension(),entry.body(),entry.source().handId(),entry.source().itemId(),snapshot.revision(),frame).orElse(null));
        if(frozen==null&&state.expired((int)frame)){removeEntry(entry);return null;}
        if(frozen==null)return null;
        return new Sample(entry,sample.alpha(),frozen);
    }
    private static net.minecraft.world.entity.Entity findBody(net.minecraft.client.multiplayer.ClientLevel level,UUID id){for(var e:level.entitiesForRendering())if(e.getUUID().equals(id))return e;return null;}
    private static void removeEntry(Entry entry){ENTRIES.remove(entry);SOURCE_STATES.remove(entry);}
    public static ActualMuzzleFrame resolveSource(Entry entry, long frame) {
        if (entry == null || entry.source() == null) return null;
        var mc=net.minecraft.client.Minecraft.getInstance(); if(mc.level==null)return null;
        var source=entry.source();
        var entity=findBody(mc.level,entry.body());
        if(entity==null)return null;
        long revision=com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking.clientCache().get(entry.dimension(),entry.body(),entity.getId()).map(com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot::revision).orElse(-1L);
        return com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.actualMuzzle(entry.dimension(),entry.body(),source.handId(),source.itemId(),revision,frame).orElse(null);
    }

    public static List<Sample> sample(UUID body, long epoch, ResourceLocation dimension, long nowTick) {
        List<Sample> result = new ArrayList<>();
        Iterator<Entry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.dimension().equals(dimension) || (e.epoch()!=0 && (!e.body().equals(body) || e.epoch() != epoch))) { it.remove(); SOURCE_STATES.remove(e); continue; }
            float duration = e.effect().lifetimeSeconds() * 20f;
            float elapsed = nowTick - e.startTick();
            if (elapsed < 0) continue;
            if (elapsed >= duration) { it.remove(); SOURCE_STATES.remove(e); continue; }
            result.add(new Sample(e, 1f - elapsed / duration));
        }
        return List.copyOf(result);
    }
    public static List<Sample> sampleWorld(ResourceLocation dimension,long nowTick){List<Sample> result=new ArrayList<>();Iterator<Entry> it=ENTRIES.iterator();while(it.hasNext()){Entry e=it.next();if(!e.dimension().equals(dimension)){it.remove();SOURCE_STATES.remove(e);continue;}float elapsed=nowTick-e.startTick(),duration=e.effect().lifetimeSeconds()*20f;if(elapsed<0)continue;if(elapsed>=duration){it.remove();SOURCE_STATES.remove(e);continue;}result.add(new Sample(e,1f-elapsed/duration));}return List.copyOf(result);}
    public static void reconcileWorld(net.minecraft.client.multiplayer.ClientLevel level){if(observedLevel!=level){clear();observedLevel=level;}}

    public static void clear() { ENTRIES.clear(); SOURCE_STATES.clear(); }
    public static void reconcile(UUID body, long epoch, ResourceLocation dimension) {
        var removed=ENTRIES.stream().filter(e->!e.dimension().equals(dimension)||(e.epoch()!=0&&(body==null||!e.body().equals(body)||e.epoch()!=epoch))).toList(); removed.forEach(GunMuzzleFlashClient::removeEntry);
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
