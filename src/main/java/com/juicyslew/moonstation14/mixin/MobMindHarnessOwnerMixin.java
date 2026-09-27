package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMindHarnessOwnerMixin implements MindControlledMob {
    @Unique
    private static final EntityDataAccessor<Boolean> moonstation14$MOVEMENT_OWNED =
            SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"), require = 1)
    private void moonstation14$defineMovementOwner(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(moonstation14$MOVEMENT_OWNED, false);
    }

    @Override
    public boolean moonstation14$isMovementOwned() {
        return ((Mob) (Object) this).getEntityData().get(moonstation14$MOVEMENT_OWNED);
    }

    @Override
    public void moonstation14$setMovementOwned(boolean owned) {
        Mob mob = (Mob) (Object) this;
        if (!mob.level().isClientSide) mob.getEntityData().set(moonstation14$MOVEMENT_OWNED, owned);
    }
}
