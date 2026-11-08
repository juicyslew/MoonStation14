package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.combat.ModProjectileProfiles;
import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileEffectAtlasResourceTest {
    @Test void everyResolvedProjectileEffectMatchesItsPngAndRsiFrameMetadata() throws Exception {
        var manager=new PrototypeManager(); manager.register(ModProjectileProfiles.TYPE);
        var profiles=new LinkedHashMap<ResourceLocation,ProjectileProfile>();
        manager.stage(Map.of(ModProjectileProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("projectile_profile")), catalogs ->
                catalogs.get(ModProjectileProfiles.TYPE.typeId()).forEach((key,raw)->profiles.put(key,
                        ProjectileProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,raw).getOrThrow())));
        assertFalse(profiles.isEmpty());
        assertEquals(90f, profiles.get(ResourceLocation.fromNamespaceAndPath("moonstation14","red_medium_laser")).muzzleFlash().sprite().rotationOffset());
        assertEquals(90f, profiles.get(ResourceLocation.fromNamespaceAndPath("moonstation14","red_medium_laser")).impactEffect().sprite().rotationOffset());
        assertTrue(profiles.values().stream().filter(p->p.sprite()!=null).anyMatch(p->p.sprite().rotationOffset()==0f));
        int checked=0;
        for(var entry:profiles.entrySet()) {
            var p=entry.getValue(); var sprites=new ArrayList<ProjectileProfile.SpriteData>();
            if(p.sprite()!=null)sprites.add(p.sprite());
            for(var effect:new ProjectileProfile.ProjectileSpriteEffectData[]{p.muzzleFlash(),p.travelEffect(),p.impactEffect()}) if(effect!=null)sprites.add(effect.sprite());
            for(var sprite:sprites) { check(sprite,entry.getKey().toString()); checked++; }
        }
        assertTrue(checked>0);
    }
    private static void check(ProjectileProfile.SpriteData s,String profile) throws Exception {
        String base="/assets/"+s.texture().getNamespace()+"/textures/"+s.texture().getPath()+"/";
        var png=ProjectileEffectAtlasResourceTest.class.getResourceAsStream(base+s.state()+".png");
        assertNotNull(png,profile+" missing "+s.state()+" PNG");
        var image=ImageIO.read(png); assertNotNull(image);
        int rows=(s.frameCount()+s.frameColumns()-1)/s.frameColumns();
        assertEquals(s.frameWidth()*s.frameColumns(),image.getWidth(),profile+" PNG width");
        assertEquals(s.frameHeight()*rows,image.getHeight(),profile+" PNG height");
        var metaStream=ProjectileEffectAtlasResourceTest.class.getResourceAsStream(base+"meta.json"); assertNotNull(metaStream);
        var meta=JsonParser.parseString(new String(metaStream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        var state=meta.getAsJsonArray("states").asList().stream().map(e->e.getAsJsonObject()).filter(e->e.get("name").getAsString().equals(s.state())).findFirst().orElseThrow();
        assertEquals(s.frameWidth(),meta.getAsJsonObject("size").get("x").getAsInt(),profile+" RSI frame width");
        assertEquals(s.frameHeight(),meta.getAsJsonObject("size").get("y").getAsInt(),profile+" RSI frame height");
        int declared=state.has("delays")?state.getAsJsonArray("delays").asList().stream().mapToInt(a->a.getAsJsonArray().size()).sum():1;
        assertEquals(declared,s.frameCount(),profile+" RSI frame count for "+s.state());
    }
}
