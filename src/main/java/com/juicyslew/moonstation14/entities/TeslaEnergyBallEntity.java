package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.singularity.GeneratorSpawnTarget;
import com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles;
import com.juicyslew.moonstation14.ms14.tesla.TeslaMovementPolicy;
import com.juicyslew.moonstation14.ms14.tesla.TeslaBallProfile;
import com.juicyslew.moonstation14.ms14.tesla.TeslaEnergyState;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorEnergyReceiver;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrengthProfile;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative Tesla source; behavior is resolved from inherited typed ball profiles. */
public final class TeslaEnergyBallEntity extends Entity implements GeneratorSpawnTarget, ParticleAcceleratorEnergyReceiver {
    public enum EnergyInput { NONE, PA, LIVING, LOOSE_ITEM, BLOCK }
    private static final EntityDataAccessor<String> SYNC_PROFILE = SynchedEntityData.defineId(TeslaEnergyBallEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(TeslaEnergyBallEntity.class, EntityDataSerializers.FLOAT);
    private static final String PROFILE_TAG = "TeslaProfile", ENERGY_TAG = "TeslaEnergy";
    private ResourceLocation profileId = ModTeslaProfiles.MAIN_ID;
    private long ageTicks, nextWalkTick, nextTargetTick, nextJumpTick;
    private long nextShotTick;
    private long lightningShotAttempts, lightningBoltsEmitted, lastLightningShotTick = -1;
    private Vec3 rememberedTarget;
    private java.util.UUID rememberedTargetEntity;
    private net.minecraft.core.BlockPos rememberedTargetBlock;
    private final java.util.Set<java.util.UUID> touching = new java.util.HashSet<>();
    private boolean initialized;
    private boolean spawnSoundPlayed;
    private int miniContactAttempts;
    private String miniLastShockResult = "NONE";
    private java.util.UUID miniLastContact;
    private EnergyInput lastEnergyInputKind=EnergyInput.NONE;
    private double lastEnergyInputAmount;
    private long lastEnergyInputTick=-1;
    private long paHitsReceived;
    private double paEnergyReceived;

    public TeslaEnergyBallEntity(EntityType<? extends TeslaEnergyBallEntity> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SYNC_PROFILE, ModTeslaProfiles.MAIN_ID.toString());
        builder.define(ENERGY, 0f);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if(SYNC_PROFILE.equals(accessor)) refreshDimensions();
    }

    public ResourceLocation profileId() {
        if (!level().isClientSide) return profileId;
        try { return ResourceLocation.parse(entityData.get(SYNC_PROFILE)); }
        catch (RuntimeException ignored) { return profileId; }
    }
    public double energy() { return entityData.get(ENERGY); }
    public boolean isMini() { return ModTeslaProfiles.MINI_ID.equals(profileId()); }
    public int miniContactAttempts() { return miniContactAttempts; }
    public String miniLastShockResult() { return miniLastShockResult; }
    public java.util.UUID miniLastContact() { return miniLastContact; }
    public EnergyInput lastEnergyInputKind() { return lastEnergyInputKind; }
    public double lastEnergyInputAmount() { return lastEnergyInputAmount; }
    public long lastEnergyInputTick() { return lastEnergyInputTick; }
    public long paHitsReceived() { return paHitsReceived; }
    public double paEnergyReceived() { return paEnergyReceived; }
    public long lastPaInputTick() { return lastPaInputTick; }
    private long lastPaInputTick=-1;
    public long lightningShotAttempts() { return lightningShotAttempts; }
    public long lightningBoltsEmitted() { return lightningBoltsEmitted; }
    public long lastLightningShotTick() { return lastLightningShotTick; }
    public boolean hasChaseTarget() { return rememberedTarget != null; }
    public Vec3 chaseTargetPosition() { return rememberedTarget; }
    public String chaseTargetKind() { return rememberedTargetEntity != null ? "ENTITY" : rememberedTargetBlock != null ? "BLOCK" : "NONE"; }
    private TeslaBallProfile profile() { return ModTeslaProfiles.balls(level()).get(profileId()); }
    public double visualRadius() { try { Double r=profile().visualRadius(); return r==null?getBbWidth()*.5:r; } catch(RuntimeException unavailable) { return getBbWidth()*.5; } }
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        double radius;
        try { radius=profile().colliderRadius()==null?getBbWidth()*.5:profile().colliderRadius(); }
        catch(RuntimeException unavailable) { radius=getBbWidth()*.5; }
        return net.minecraft.world.entity.EntityDimensions.scalable((float)(radius*2),(float)(radius*2));
    }
    public TeslaBallProfile.Ambient ambientProfile() { try { return profile().ambient(); } catch (RuntimeException unavailable) { return null; } }

    @Override public boolean initializeSourceProfile(ResourceLocation requested) {
        if (level().isClientSide || requested == null) return false;
        try {
            TeslaBallProfile resolved = ModTeslaProfiles.balls(level()).get(requested);
            resolved.validate();
            profileId = requested;
            entityData.set(SYNC_PROFILE, requested.toString());
            refreshDimensions();
            entityData.set(ENERGY, (float) (resolved.energy() == null ? 0 : resolved.energy().initial()));
            initialized = true;
            scheduleInitialCadences();
            return true;
        } catch (RuntimeException invalidProfile) { return false; }
    }

    private void scheduleInitialCadences() {
        TeslaBallProfile p = profile();
        // ChasingWalk.OnChasingMapInit sets both clocks to CurTime. Its first
        // update acquires a target and applies the first impulse immediately;
        // authored intervals govern only subsequent updates/retargets.
        nextWalkTick = level().getGameTime();
        nextTargetTick = level().getGameTime();
        nextJumpTick = p.jump() == null ? Long.MAX_VALUE : level().getGameTime() + randomTicks(p.jump().minimumIntervalSeconds(), p.jump().maximumIntervalSeconds());
        nextShotTick = level().getGameTime() + seconds(p.shooter().maximumIntervalSeconds());
    }

    @Override public double particleContactRadius() { return isMini() ? 0 : value(profile().contactRadius(), 0); }
    @Override public double particleEnergy() { return isMini() ? 0 : energy(); }
    @Override public void receiveParticleEnergy(ParticleAcceleratorStrengthProfile strength) {
        if (level() instanceof ServerLevel && !isMini() && strength != null) adjustEnergy(strength.singularityFood(),EnergyInput.PA);
    }

    /** Accepted entity consumption feeds its authored profile yield; a failed admission never reaches here. */
    public void recordConsumedEntity() {
        if (level().isClientSide || isMini() || profile().energy() == null) return;
        adjustEnergy(profile().energy().gainPerConsumedEntity(),EnergyInput.LIVING);
    }

    private void adjustEnergy(double delta) {
        adjustEnergy(delta,EnergyInput.NONE);
    }
    private void adjustEnergy(double delta, EnergyInput source) {
        var rules = profile().energy();
        if (rules == null) return;
        TeslaEnergyState.Adjustment result = TeslaEnergyState.adjust(energy(), delta, rules);
        if (result.transition() == TeslaEnergyState.Transition.NONE && result.energy() == energy()) return;
        entityData.set(ENERGY, (float) result.energy());
        if (source!=EnergyInput.NONE && Double.isFinite(delta) && delta>0) {
            lastEnergyInputKind=source; lastEnergyInputAmount=delta; lastEnergyInputTick=level().getGameTime();
            if(source==EnergyInput.PA){paHitsReceived++;paEnergyReceived+=delta;lastPaInputTick=level().getGameTime();}
        }
        if (result.transition() == TeslaEnergyState.Transition.COLLAPSE) {
            playTeslaSound(com.juicyslew.moonstation14.sounds.ModSounds.TESLA_SPAWN.get(), .7f + random.nextFloat()*.6f);
            discard(); return;
        }
        if (result.transition() == TeslaEnergyState.Transition.SPAWN_MINI && level() instanceof ServerLevel server) {
            TeslaEnergyBallEntity mini = ModEntities.TESLA_ENERGY_BALL.get().create(server);
            if (mini != null && mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID)) {
                mini.moveTo(position());
                server.addFreshEntity(mini);
            }
        }
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        if (!initialized && !initializeSourceProfile(profileId)) return;
        if (!isMini() && !spawnSoundPlayed && isAddedToLevel()) {
            playTeslaSound(com.juicyslew.moonstation14.sounds.ModSounds.TESLA_SPAWN.get(), .7f + random.nextFloat()*.6f);
            spawnSoundPlayed=true;
        }
        TeslaBallProfile p = profile();
        ageTicks++;
        if (p.timedDespawnSeconds() != null && ageTicks >= seconds(p.timedDespawnSeconds())) { discard(); return; }
        long now = server.getGameTime();
        if (now >= nextTargetTick) {
            selectTarget(server, p);
            nextTargetTick = now + randomTicks(p.chasingWalk().minimumRetargetSeconds(), p.chasingWalk().maximumRetargetSeconds());
        }
        refreshRememberedTarget(server);
        boolean walkDue = now >= nextWalkTick;
        if (walkDue && rememberedTarget == null) selectTarget(server, p);
        Entity orbitMain=null;
        if(isMini()&&p.orbit()!=null&&rememberedTargetEntity!=null) {
            Entity tracked=server.getEntity(rememberedTargetEntity);
            if(tracked instanceof TeslaEnergyBallEntity main&&!main.isMini()&&main.isAlive())orbitMain=main;
        }
        if (walkDue) {
            if (rememberedTarget != null && orbitMain==null) {
                Vec3 direction = rememberedTarget.subtract(position());
                if (direction.lengthSqr() > 1e-8) setDeltaMovement(direction.normalize().scale(random(p.chasingWalk().minimumSpeed(), p.chasingWalk().maximumSpeed())));
            }
            nextWalkTick = now + seconds(p.chasingWalk().updateSeconds());
        }
        if(orbitMain instanceof TeslaEnergyBallEntity main) {
                Vec3 targetPoint=TeslaMovementPolicy.orbitPoint(main.position(),getUUID(),ageTicks/20.0,p.orbit());
                Vec3 rayStart=targetPoint;
                Vec3 rayEnd=targetPoint.add(0,-p.orbit().groundProbeDistance(),0);
                double miniRadius=value(p.colliderRadius(),.65);
                if(ProjectileLoadedSweep.isLoadedSpan(server,rayStart,rayEnd,miniRadius)) {
                    var floor=ProjectileLoadedSweep.clip(server,this,rayStart,rayEnd,miniRadius);
                    if(floor!=null&&floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)
                        targetPoint=new Vec3(targetPoint.x,Math.max(targetPoint.y,floor.getLocation().y+miniRadius),targetPoint.z);
                }
                // The orbit point is the moving target. Do not feed the owner's
                // instantaneous velocity into pursuit: that cancels the intended
                // positional response and welds minis to containment bounces.
                setDeltaMovement(TeslaMovementPolicy.orbitVelocity(getDeltaMovement(),position(),targetPoint,Vec3.ZERO,p.orbit(),p.chasingWalk().maximumSpeed(),.05));
        }
        if (rememberedTarget == null) {
            setDeltaMovement(com.juicyslew.moonstation14.ms14.tesla.TeslaMovementPolicy.dampTargetlessVertical(
                    getDeltaMovement(), 1.0 / 20.0, p.chasingWalk().targetlessVerticalHalfLifeSeconds()));
        }
        if (p.jump() != null && now >= nextJumpTick) {
            jump(server, p.jump());
            nextJumpTick = now + randomTicks(p.jump().minimumIntervalSeconds(), p.jump().maximumIntervalSeconds());
        }
        if (now >= nextShotTick) {
            if (p.lightning() != null) {
                lightningShotAttempts++;
                lastLightningShotTick=now;
                lightningBoltsEmitted+=com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.shoot(server, position(), p.shooter(), p.lightning(), new java.util.Random(random.nextLong()));
            }
            nextShotTick = now + randomTicks(p.shooter().minimumIntervalSeconds(), p.shooter().maximumIntervalSeconds());
        }
        updateContact(server,p);
        moveContained(server, p);
        consumeMainContacts(server,p);
    }

    /** Main-ball entity-only contact consumption; terrain and periodic horizon waves are intentionally absent. */
    private void consumeMainContacts(ServerLevel server, TeslaBallProfile p) {
        if (isMini() || p.energy() == null || p.horizonRadius() == null || p.horizonRadius() <= 0 || isRemoved()) return;
        double radius = value(p.contactRadius(),0.65);
        Vec3 center = position();
        consumeOwnedStartupGenerator(server,center,radius,p);
        if(isRemoved())return;
        var bounds = getBoundingBox().inflate(radius);
        for (var body : server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, bounds,
                com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption::eligible)) {
            if (!com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption.touches(center,radius,body)) continue;
            if (com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption.kill(server,body)) recordConsumedEntity();
        }
        for (var entity : server.getEntitiesOfClass(Entity.class,bounds,e -> e != this && e.isAlive() && e.isAddedToLevel()
                && e instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable)) {
            if (!com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry.touches(center,radius,entity.getBoundingBox())) continue;
            if (entity instanceof LooseItemEntity item && com.juicyslew.moonstation14.ms14.singularity.SingularityConsumptionSafety.hasOwnedPayload(item.getItem())) continue;
            double gain = p.energy().gainPerConsumedEntity();
            if (entity instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food) {
                double flat=food.singularityFoodEnergy();
                if (!Double.isFinite(flat)) continue;
                gain=flat;
            } else if (entity instanceof LooseItemEntity item
                    && item.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food) {
                double flat=food.singularityFoodEnergy();
                if (!Double.isFinite(flat)) continue;
                gain=flat;
            }
            if (!Double.isFinite(gain) || gain <= 0) continue;
            entity.discard();
            if (entity.isRemoved()) {
                playTeslaSound(com.juicyslew.moonstation14.sounds.ModSounds.TESLA_CONSUME.get(), 1f);
                adjustEnergy(gain,entity instanceof LooseItemEntity ? EnergyInput.LOOSE_ITEM : EnergyInput.LIVING);
            }
        }
    }

    private void consumeContactBlocks(ServerLevel server, TeslaBallProfile p, Vec3 start, Vec3 end) {
        double radius=p.resolvedBlockConsumerRadius();
        int reach=(int)Math.ceil(radius+1);
        var min=new net.minecraft.core.BlockPos((int)Math.floor(Math.min(start.x,end.x)-reach),(int)Math.floor(Math.min(start.y,end.y)-reach),(int)Math.floor(Math.min(start.z,end.z)-reach));
        var max=new net.minecraft.core.BlockPos((int)Math.floor(Math.max(start.x,end.x)+reach),(int)Math.floor(Math.max(start.y,end.y)+reach),(int)Math.floor(Math.max(start.z,end.z)+reach));
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(min,max)) {
            if (!server.hasChunkAt(pos)
                    || server.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity
                    || !com.juicyslew.moonstation14.ms14.singularity.ReactorBlockConsumption.touchesSegment(pos,start,end,radius)) continue;
            var blockEntity=server.getBlockEntity(pos);
            double gain=p.energy().gainPerConsumedEntity();
            if(blockEntity instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food) {
                gain=food.singularityFoodEnergy();
                if(!Double.isFinite(gain))continue;
            }
            if(com.juicyslew.moonstation14.ms14.singularity.ReactorBlockConsumption.remove(server,pos,false)) {
                playTeslaSound(com.juicyslew.moonstation14.sounds.ModSounds.TESLA_CONSUME.get(),1f);
                adjustEnergy(gain,EnergyInput.BLOCK);
            }
        }
    }

    /** Removes only the inert generator whose own successful spawn is this exact source entity. */
    private void consumeOwnedStartupGenerator(ServerLevel server,Vec3 center,double radius,TeslaBallProfile p) {
        var pos=blockPosition();
        if(!server.hasChunkAt(pos)||!com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry
                .touches(center,radius,new net.minecraft.world.phys.AABB(pos)))return;
        var generator=server.getBlockEntity(pos);
        if(!(generator instanceof com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity source)
                ||!source.startupInert()||!getUUID().equals(source.spawnedSourceIdentity()))return;
        if(server.removeBlock(pos,false)) {
            com.juicyslew.moonstation14.ms14.power.cable.CableStorage.removeHost(server,pos);
            adjustEnergy(p.energy().gainPerConsumedEntity(),EnergyInput.BLOCK);
        }
    }

    private void updateContact(ServerLevel server, TeslaBallProfile p) {
        if (!isMini() || p.contact() == null) { touching.clear(); return; }
        double radius = value(p.colliderRadius(), 0);
        if (radius <= 0) return;
        var box = getBoundingBox().inflate(radius);
        var nowTouching = new java.util.HashSet<java.util.UUID>();
        for (var target : server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> e.isAlive() && e.isAddedToLevel() && !e.isSpectator())) {
            if (target instanceof net.minecraft.server.level.ServerPlayer player
                    && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player)) continue;
            if (!com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry.touches(position(), radius, target.getBoundingBox())) continue;
            var id = target.getUUID(); nowTouching.add(id);
            if (!touching.contains(id)) {
                miniContactAttempts++;
                miniLastContact = id;
                miniLastShockResult = com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.applySourceShock(
                        target,(float)p.contact().shockDamage(),(float)p.contact().siemens(),(float)p.contact().stunSeconds()).name();
            }
        }
        touching.clear(); touching.addAll(nowTouching);
    }

    private void refreshRememberedTarget(ServerLevel server) {
        double range=profile().chasingWalk().range();
        // A valid owner remains authoritative across the main ball's 5–10 block jump;
        // reacquisition itself is still the bounded local scan in selectTarget.
        if(isMini()&&profile().orbit()!=null)range=Math.max(range,20);
        if (rememberedTargetEntity != null) {
            Entity target=server.getEntity(rememberedTargetEntity);
            if(isMini()&&target instanceof TeslaEnergyBallEntity main) {
                if(main!=this&&!main.isMini()&&main.isAlive()&&main.level()==server
                        &&position().distanceToSqr(main.position())<=range*range) rememberedTarget=main.position();
                else { rememberedTargetEntity=null; rememberedTarget=null; }
                return;
            }
            var resolved=target instanceof net.minecraft.world.entity.LivingEntity living ? com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.resolveLivingTarget(server,living) : null;
            if(resolved!=null && position().distanceToSqr(resolved.position())<=range*range) rememberedTarget=resolved.position();
            else { rememberedTargetEntity=null; rememberedTarget=null; }
        } else if(rememberedTargetBlock!=null) {
            var pos=rememberedTargetBlock;
            var be=server.getBlockEntity(pos);
            var resolved=com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.resolveBlockTarget(server,be);
            if(resolved!=null && position().distanceToSqr(resolved.position())<=profile().chasingWalk().range()*profile().chasingWalk().range()) rememberedTarget=resolved.position();
            else { rememberedTargetBlock=null; rememberedTarget=null; }
        }
    }

    private Vec3 selectTarget(ServerLevel server, TeslaBallProfile p) {
        double r = p.chasingWalk().range();
        if (isMini()) {
            if(rememberedTargetEntity!=null)return rememberedTarget;
            var candidates=server.getEntitiesOfClass(TeslaEnergyBallEntity.class,getBoundingBox().inflate(r),e->e!=this&&!e.isMini()&&e.isAlive()&&e.distanceToSqr(this)<=r*r);
            if(candidates.isEmpty())return rememberedTarget;
            var target=candidates.get(random.nextInt(candidates.size())); rememberedTargetEntity=target.getUUID();rememberedTargetBlock=null;return rememberedTarget=target.position();
        }
        var candidates=com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.targets(server,position(),r).stream()
                .filter(t->t.position().distanceToSqr(position())<=r*r).toList();
        if(candidates.isEmpty())return rememberedTarget;
        var target=candidates.get(random.nextInt(candidates.size()));
        if(target.living()!=null){rememberedTargetEntity=target.living().getUUID();rememberedTargetBlock=null;}
        else {rememberedTargetEntity=null;rememberedTargetBlock=target.blockEntity()==null?null:target.blockEntity().getBlockPos();}
        return rememberedTarget=target.position();
    }

    private void jump(ServerLevel server, TeslaBallProfile.Jump jump) {
        Vec3 dir = new Vec3(random(-1,1), random(-1,1), random(-1,1));
        if (dir.lengthSqr() < 1e-8) return;
        Vec3 start = position(), end = start.add(dir.normalize().scale(random(jump.minimumDistance(), jump.maximumDistance())));
        Vec3 destination=jumpDestination(server,this,start,end,value(profile().colliderRadius(),getBbWidth()*.5));
        if (destination!=null) {
            com.juicyslew.moonstation14.ms14.tesla.TeslaArcNetworking.sendJump(server,start);
            setPos(destination.x,destination.y,destination.z);
        }
    }

    /** Finds the nearest loaded block or field-face collision and clamps the jump before it. */
    public static Vec3 jumpDestination(ServerLevel server,Entity collisionContext,Vec3 from,Vec3 proposed,double radius) {
        if(server==null||collisionContext==null||from==null||proposed==null||!Double.isFinite(radius)||radius<0)return null;
        Vec3 collision=null;
        var field=FieldContainmentRuntime.firstFaceHit(server,from,proposed,radius);
        if(field.isPresent()) collision=from.add(proposed.subtract(from).scale(Math.max(0,field.get().segmentFraction())));
        var block=ProjectileLoadedSweep.clip(server,collisionContext,from,proposed,radius);
        if(block!=null&&block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS
                &&(collision==null||from.distanceToSqr(block.getLocation())<from.distanceToSqr(collision))) collision=block.getLocation();
        Vec3 destination=proposed;
        if(collision!=null){Vec3 toward=collision.subtract(from);double length=toward.length();destination=length<=1?from:from.add(toward.scale((length-1)/length));}
        return ProjectileLoadedSweep.isLoadedSpan(server,from,destination,radius)?destination:null;
    }

    private void moveContained(ServerLevel server, TeslaBallProfile p) {
        Vec3 start=position(), displacement=getDeltaMovement().scale(.05), end=start.add(displacement);
        double radius=value(p.colliderRadius(), .35);
        // Collision is a resolved prototype behavior (mini's upstream fixture has
        // no mask/layer); unloaded chunks remain an independent travel guard.
        if (p.collision() == TeslaBallProfile.Collision.NONE) {
            if (ProjectileLoadedSweep.isLoadedSpan(server,start,end,radius)) setPos(end.x,end.y,end.z);
            return;
        }
        var face = FieldContainmentRuntime.firstFaceHit(server,start,end,radius);
        if (face.isPresent()) {
            var hit=face.get(); Vec3 n=new Vec3(hit.normal().getStepX(),hit.normal().getStepY(),hit.normal().getStepZ());
            double t=Math.max(0,hit.segmentFraction()-1e-5); end=start.add(displacement.scale(t));
            setDeltaMovement(getDeltaMovement().subtract(n.scale(2*getDeltaMovement().dot(n))).scale(.8));
        }
        // Consume only physical main-ball contact along the field-clipped travel segment,
        // before ordinary block collision can bounce the ball away from a machine.
        if (!isMini()) consumeContactBlocks(server,p,start,end);
        if (isRemoved()) return;
        var blockHit=ProjectileLoadedSweep.clip(server,this,start,end,radius);
        if(blockHit!=null && blockHit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS) {
            Vec3 normal=blockHit instanceof net.minecraft.world.phys.BlockHitResult blockFace
                    ? new Vec3(blockFace.getDirection().getStepX(), blockFace.getDirection().getStepY(), blockFace.getDirection().getStepZ())
                    : end.subtract(start).normalize().scale(-1);
            setDeltaMovement(getDeltaMovement().subtract(normal.scale(2*getDeltaMovement().dot(normal))).scale(.8));
            end=blockHit.getLocation().subtract(normal.scale(1e-3));
        }
        if(ProjectileLoadedSweep.isLoadedSpan(server,start,end,radius)) setPos(end.x,end.y,end.z);
    }

    @Override public net.minecraft.network.chat.Component getName() {
        if (hasCustomName()) return getCustomName();
        return net.minecraft.network.chat.Component.translatable(isMini()
                ? "entity.moonstation14.tesla_mini_energy_ball"
                : "entity.moonstation14.tesla_energy_ball");
    }

    private static long seconds(double seconds) { return Math.max(1, (long)Math.ceil(seconds*20)); }
    private long randomTicks(double a,double b) { return seconds(random(a,b)); }
    private double random(double a,double b) { return a + random.nextDouble()*(b-a); }
    private static double value(Double d,double fallback) { return d==null?fallback:d; }
    private void playTeslaSound(net.minecraft.sounds.SoundEvent sound,float pitch) {
        if(level() instanceof ServerLevel server) server.playSound(null,getX(),getY(),getZ(),sound,net.minecraft.sounds.SoundSource.AMBIENT,1f,pitch);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(PROFILE_TAG,profileId.toString()); tag.putDouble(ENERGY_TAG,energy()); tag.putLong("AgeTicks",ageTicks);
        tag.putBoolean("TeslaSpawnSoundPlayed",spawnSoundPlayed);
        tag.putLong("NextWalk",nextWalkTick); tag.putLong("NextTarget",nextTargetTick); tag.putLong("NextJump",nextJumpTick); tag.putLong("NextShot",nextShotTick);
        Vec3 v=getDeltaMovement(); tag.putDouble("VelocityX",v.x);tag.putDouble("VelocityY",v.y);tag.putDouble("VelocityZ",v.z);
        if(rememberedTarget!=null){tag.putDouble("TargetX",rememberedTarget.x);tag.putDouble("TargetY",rememberedTarget.y);tag.putDouble("TargetZ",rememberedTarget.z);}
        if(rememberedTargetEntity!=null)tag.putUUID("TargetEntity",rememberedTargetEntity);
        if(rememberedTargetBlock!=null)tag.putLong("TargetBlock",rememberedTargetBlock.asLong());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if(tag.contains(PROFILE_TAG)) profileId=ResourceLocation.parse(tag.getString(PROFILE_TAG));
        entityData.set(SYNC_PROFILE,profileId.toString()); entityData.set(ENERGY,tag.getFloat(ENERGY_TAG)); ageTicks=Math.max(0,tag.getLong("AgeTicks"));
        spawnSoundPlayed=tag.getBoolean("TeslaSpawnSoundPlayed");
        nextWalkTick=tag.getLong("NextWalk");nextTargetTick=tag.getLong("NextTarget");nextJumpTick=tag.getLong("NextJump");nextShotTick=tag.getLong("NextShot");
        setDeltaMovement(tag.getDouble("VelocityX"),tag.getDouble("VelocityY"),tag.getDouble("VelocityZ"));
        if(tag.contains("TargetX")) rememberedTarget=new Vec3(tag.getDouble("TargetX"),tag.getDouble("TargetY"),tag.getDouble("TargetZ"));
        if(tag.hasUUID("TargetEntity"))rememberedTargetEntity=tag.getUUID("TargetEntity");
        if(tag.contains("TargetBlock"))rememberedTargetBlock=net.minecraft.core.BlockPos.of(tag.getLong("TargetBlock"));
        initialized=true;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) { return new ClientboundAddEntityPacket(this, serverEntity); }
}
