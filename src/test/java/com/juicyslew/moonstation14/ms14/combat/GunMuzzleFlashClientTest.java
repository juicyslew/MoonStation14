package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.GunMuzzleFlashClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GunMuzzleFlashClientTest {
    private static final UUID BODY=UUID.randomUUID();
    private static final ResourceLocation DIM=ResourceLocation.withDefaultNamespace("overworld");
    private static final ProjectileProfile.ProjectileSpriteEffectData FLASH=new ProjectileProfile.ProjectileSpriteEffectData(
            new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","flash"),"muzzle_bullet",32,32,1,0,true,false),.4f);
    @AfterEach void clear(){GunMuzzleFlashClient.clear();}
    @Test void productionQueueFadesExpiresAndRejectsMissingFlashAndStaleContext(){
        var direction=new Vec3(0,0,1);
        assertFalse(GunMuzzleFlashClient.enqueue(null,BODY,4,DIM,Vec3.ZERO,direction,100));
        assertTrue(GunMuzzleFlashClient.enqueue(FLASH,BODY,4,DIM,Vec3.ZERO,direction,100));
        assertEquals(1,GunMuzzleFlashClient.sample(BODY,4,DIM,100).size());
        assertEquals(.5f,GunMuzzleFlashClient.sample(BODY,4,DIM,104).get(0).alpha());
        assertTrue(GunMuzzleFlashClient.sample(BODY,4,DIM,108).isEmpty());
        assertTrue(GunMuzzleFlashClient.enqueue(FLASH,BODY,4,DIM,Vec3.ZERO,direction,110));
        assertTrue(GunMuzzleFlashClient.sample(BODY,5,DIM,110).isEmpty());
        assertTrue(GunMuzzleFlashClient.enqueue(FLASH,BODY,4,DIM,Vec3.ZERO,direction,110));
        GunMuzzleFlashClient.reconcile(BODY,5,DIM);
        assertTrue(GunMuzzleFlashClient.sample(BODY,4,DIM,110).isEmpty());
    }
    @Test void observerFlashUsesShooterOnlyAsVisualIdentityAndLivesInCurrentWorld(){
        UUID remote=UUID.randomUUID();
        assertTrue(GunMuzzleFlashClient.enqueueObserver(FLASH,remote,1,DIM,Vec3.ZERO,new Vec3(0,0,1),200));
        assertEquals(remote,GunMuzzleFlashClient.sampleWorld(DIM,204).get(0).entry().body());
        assertEquals(.5f,GunMuzzleFlashClient.sampleWorld(DIM,204).get(0).alpha());
        assertTrue(GunMuzzleFlashClient.sampleWorld(ResourceLocation.withDefaultNamespace("nether"),204).isEmpty());
        assertTrue(GunMuzzleFlashClient.sampleWorld(DIM,208).isEmpty());
    }
}
