package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.ProjectileSpriteFrames;
import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import com.juicyslew.moonstation14.ms14.combat.ModProjectileProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production-mesh, CPU-only source texture preview; not an in-game visual/readiness review. */
class ClosedEffectMeshPreviewTest {
    private static net.minecraft.resources.ResourceLocation id(String path){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",path);}
    private record Source(String id,String name,ProjectileProfile.SpriteData sprite,boolean beam){}
    private record View(String name,double yaw,double pitch){}
    private static final List<View> VIEWS=List.of(new View("head-on",0,0),new View("side",Math.PI/2,0),new View("top",0,Math.PI/2),new View("oblique",.7,.55));
    private static int tint(int pixel,int color){return ((((pixel>>>24)&255)*((color>>>24)&255)/255)<<24)|((((pixel>>>16)&255)*((color>>>16)&255)/255)<<16)|((((pixel>>>8)&255)*((color>>>8)&255)/255)<<8)|((pixel&255)*(color&255)/255);}
    @Test void tintMultipliesTextureChannelsIncludingAlpha(){assertEquals(0x80ff8000,tint(0xffffffff,0x80ff8000));assertEquals(0xffff8040,tint(0xffffffff,0xffff8040));}

    static int tintPixel(int pixel,int color){return tint(pixel,color);}

    @Test void renderResolvedProjectileSourcesAndAnimatedMuzzle() throws Exception {
        PrototypeManager manager=new PrototypeManager();manager.register(ModProjectileProfiles.TYPE);manager.reload(ModProjectileProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("projectile_profile"));var profiles=manager.snapshot(ModProjectileProfiles.TYPE);
        var sources=List.of(new Source("bullet_pistol","bullet-pistol",profiles.get(id("bullet_pistol")).sprite(),false),new Source("bullet_laser","heavy-laser",profiles.get(id("bullet_laser")).sprite(),false),new Source("bullet_disabler_smg","omni-laser",profiles.get(id("bullet_disabler_smg")).sprite(),false),new Source("red_light_laser","muzzle-laser",profiles.get(id("red_light_laser")).muzzleFlash().sprite(),false),new Source("red_light_laser","svalinn-beam",profiles.get(id("red_light_laser")).travelEffect().sprite(),true));
        Path root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();Path out=root.resolve("build/guns-sprint/owner-feedback-round4/effect-preview");Files.createDirectories(out);
        StringBuilder manifest=new StringBuilder("CPU preview generated from real PNG pixels and ClosedEffectMesh; not UI readiness evidence\nOutput: ").append(out).append('\n');
        for(Source s:sources){var spr=s.sprite;String texturePath="/assets/"+spr.texture().getNamespace()+"/textures/"+spr.texture().getPath()+"/"+spr.state()+".png";BufferedImage image;try(InputStream stream=getClass().getResourceAsStream(texturePath)){assertNotNull(stream,"Missing classpath texture: "+texturePath);image=ImageIO.read(stream);}assertNotNull(image,"Unreadable classpath texture: "+texturePath);int fw=spr.frameWidth(),fh=spr.frameHeight(),columns=spr.frameColumns(),frames=spr.frameCount(),rows=(frames+columns-1)/columns;assertTrue(image.getWidth()>=fw*columns);assertTrue(image.getHeight()>=fh*rows);
            int[] frameIndices=s.name.equals("muzzle-laser")?Arrays.stream(new int[]{0,frames/2,frames-1}).distinct().toArray():java.util.stream.IntStream.range(0,frames).toArray();
             int opaqueFrames=0;for(int index:frameIndices){byte[] alpha=new byte[fw*fh];BufferedImage crop=new BufferedImage(fw,fh,BufferedImage.TYPE_INT_ARGB);int col=index%columns,row=index/columns;
                for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){int p=image.getRGB(col*fw+x,row*fh+y);alpha[y*fw+x]=(byte)(p>>>24);crop.setRGB(x,y,p);}
                var frame=ProjectileSpriteFrames.at(spr,(int)Math.floor(index*spr.frameSeconds()/.05),0);float u0=frame.u0(),u1=frame.u1(),v0=frame.v0(),v1=frame.v1();
                  var frameVolume=ClosedEffectMesh.frameVolume(fw,fh,alpha,u0,u1,v0,v1);var mesh=frameVolume.mesh();boolean sourceOpaque=false;for(byte a:alpha)if((a&255)>=ClosedEffectMesh.NATIVE_ALPHA_CUTOFF){sourceOpaque=true;break;}if(!sourceOpaque){assertTrue(mesh.isEmpty(),s.name+" frame "+index+" native-discarded source must have empty mesh");Path blank=out.resolve(s.name+"-frame-"+index+".png");ImageIO.write(crop,"png",blank.toFile());manifest.append(s.name).append(" frame ").append(index).append(" source has no native-visible pixels; 3D coverage skipped; ").append(blank).append('\n');continue;}opaqueFrames++;assertFalse(mesh.isEmpty(),s.name+" native-visible frame "+index+" unexpectedly has empty mesh");
                assertTrue(mesh.stream().allMatch(v->v.u()>=u0&&v.u()<=u1&&v.v()>=v0&&v.v()<=v1));
                for(int y=0;y<fh;y++)for(int x=0;x<fw;x++)if((alpha[y*fw+x]&255)>0)assertTrue((crop.getRGB(x,y)>>>24)!=0);
                Path f=out.resolve(s.name+"-frame-"+index+".png");ImageIO.write(crop,"png",f.toFile());manifest.append(s.name).append(" frame ").append(index).append(" ").append(f).append(" vertices=").append(mesh.size()).append('\n');
                List<ClosedEffectMesh.Vertex> volume=mesh;
                  if(s.beam){volume=ClosedEffectMesh.tube(new Vec3(0,0,-2),new Vec3(0,0,2),frameVolume.opaqueShortSpan()/64.0,frameVolume.opaqueU0(),frameVolume.opaqueU1(),frameVolume.opaqueV0(),frameVolume.opaqueV1(),frameVolume.coreU(),frameVolume.coreV());}
                BufferedImage sheet=new BufferedImage(4*192,192,BufferedImage.TYPE_INT_ARGB);
                 for(int vi=0;vi<VIEWS.size();vi++){View view=VIEWS.get(vi);BufferedImage rendered=CpuMeshRasterizer.rasterize(volume,image,view.yaw(),view.pitch(),spr.color());int count=0;for(int yy=0;yy<192;yy++)for(int xx=0;xx<192;xx++)if((rendered.getRGB(xx,yy)>>>24)!=0){sheet.setRGB(vi*192+xx,yy,rendered.getRGB(xx,yy));count++;}assertTrue(count>0,s.id+" "+s.name+" frame "+index+" zero opaque coverage in "+view.name());}
                Path threeD=out.resolve(s.id+"-"+s.name+"-frame-"+index+"-3d-views.png");ImageIO.write(sheet,"png",threeD.toFile());manifest.append(s.id).append(" ").append(s.name).append(" frame ").append(index).append(" views head-on|side|top|oblique: ").append(threeD).append('\n');
             }
             assertTrue(opaqueFrames>0,s.name+" has no opaque sampled source frames");
        }
        Files.writeString(out.resolve("manifest.txt"),manifest);
    }
}
