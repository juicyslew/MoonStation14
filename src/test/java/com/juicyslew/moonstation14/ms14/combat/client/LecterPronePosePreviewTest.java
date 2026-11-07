package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.junit.jupiter.api.Assertions.*;

/** Production-solver geometry admission check for the forthcoming owner-facing pose preview. */
class LecterPronePosePreviewTest {
    @Test void produceActualBakedSkinGeometryPosePreviewForSolverContacts() throws Exception {
        var item=GunMountTransformTest.resourceJson("assets/moonstation14/gun_presentation_bindings.json")
                .getAsJsonObject("items").getAsJsonObject("weapon_rifle_lecter");
        var binding=new GunPresentationBindings.Binding(GunMountTransformTest.vec(item,"bore"),
                GunMountTransformTest.vec(item,"up"),GunMountTransformTest.vec(item,"grip"),
                GunMountTransformTest.vec(item,"support"),GunMountTransformTest.vec(item,"muzzle"));
        Path root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();
        Path out=root.resolve("build/guns-sprint/owner-feedback-round4/prone-preview"); Files.createDirectories(out);
        StringBuilder manifest=new StringBuilder("Actual baked skin model + frozen Lecter OBJ + production hand mount. CPU geometry/texture preview only, not GPU renderer proof. OBJ frozen positions/UVs, flip_v=false. Lecter material uses native AnimationMetadataSection frame 0, cropped to 256x256 from weapon_rifle_lecter.png via weapon_rifle_lecter.png.mcmeta.\nSources: assets/moonstation14/models/item/guns/weapon_rifle_lecter.obj; assets/moonstation14/models/item/guns/weapon_rifle_lecter.json textures.atlas; assets/moonstation14/textures/item/guns/weapon_rifle_lecter.png.mcmeta.\nViews yaw/pitch: front 0/0; side pi/2/0; top 0/pi/2; oblique .7/.55.\n");
        for(boolean slim:new boolean[]{false,true}) for(HumanoidArm active:HumanoidArm.values()) {
            HumanoidArm support=active==HumanoidArm.LEFT?HumanoidArm.RIGHT:HumanoidArm.LEFT;
            var baked=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
            var model=new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(baked,slim);
            ProneGunTwoBonePose.applyTorso(model,(float)com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry.TORSO_RADIANS,0);
            var nativeFrame=new com.mojang.blaze3d.vertex.PoseStack();
            com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,
                    active==HumanoidArm.LEFT,com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            var display=GunMountTransformTest.resolveModel("moonstation14:item/weapon_rifle_lecter").getAsJsonObject("display");
            GunMountTransformTest.decodeTransform(display.getAsJsonObject(active==HumanoidArm.LEFT?"thirdperson_lefthand":"thirdperson_righthand"))
                    .apply(active==HumanoidArm.LEFT,nativeFrame); nativeFrame.translate(-.5,-.5,-.5);
            var result=ProneGunTwoBonePose.solveContacts(model,active,support,binding,
                    new org.joml.Matrix4f(nativeFrame.last().pose()),1,
                    new GunMountTransform.AimTargets(new GunArmAimMath.Vec(0,-1,0),new GunArmAimMath.Vec(0,0,1)));
             String label="lecter prone slim="+slim+" active="+active;
            assertTrue(result.primary().floorContact()&&result.support().floorContact(),label);
            assertTrue(result.primary().reached()&&result.support().reached(),label);
             assertTrue(result.supportFraction()>=.5,label);
            model.young=false; model.syncArmMeshVisibility();
            var captured=new SkinCapture();
            var pose=new com.mojang.blaze3d.vertex.PoseStack();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applySharedRootPose(pose,.6f,1,false);
            pose.scale(-1,-1,1); pose.translate(0,-1.501,0);
            model.renderToBuffer(pose,captured,0x00F000F0,0,0xffffffff);
            assertTrue(captured.vertices.size()>0,label+" no rendered body vertices");
            BufferedImage skin=skin(slim); var triangles=new ArrayList<ClosedEffectMesh.Vertex>();
            for(int i=0;i+3<captured.vertices.size();i+=4) for(int j:new int[]{0,1,2,0,2,3}) {
                var v=captured.vertices.get(i+j); triangles.add(new ClosedEffectMesh.Vertex(v.pos,v.u,v.v));
            }
            BufferedImage gunTexture=lecterFrameZero();
            BufferedImage atlas=new BufferedImage(Math.max(skin.getWidth(),gunTexture.getWidth()),skin.getHeight()+gunTexture.getHeight()+1,BufferedImage.TYPE_INT_ARGB);
            var ag=atlas.createGraphics(); ag.drawImage(skin,0,0,null); ag.setColor(new Color(105,105,105,255)); ag.fillRect(0,skin.getHeight(),atlas.getWidth(),1); ag.drawImage(gunTexture,0,skin.getHeight()+1,null); ag.dispose();
            var combined=new ArrayList<ClosedEffectMesh.Vertex>();
            for(var v:triangles) combined.add(new ClosedEffectMesh.Vertex(v.position(),v.u()*skin.getWidth()/atlas.getWidth(),v.v()*skin.getHeight()/atlas.getHeight()));
            Matrix4f rootPose=new Matrix4f(pose.last().pose());
             PoseStack handPose=new PoseStack(); model.translateToHand(active,handPose);
            var armTargets=GunMountTransform.articulatedArmSpaceTargets(model,active,new GunArmAimMath.Vec(0,-1,0),new GunArmAimMath.Vec(0,0,1));
            var palm=GunMountTransform.handContactPoint(model,active); PoseStack correction=new PoseStack();
            GunMountTransform.applyCorrection(correction,nativeFrame.last().pose(),binding,1,armTargets.bore(),armTargets.up(),palm);
            Matrix4f gunWorld=new Matrix4f(rootPose).mul(handPose.last().pose()).mul(correction.last().pose()).mul(nativeFrame.last().pose());
            List<ClosedEffectMesh.Vertex> gun=readLecterObj(gunWorld,gunTexture.getWidth(),skin.getHeight()+1,gunTexture.getHeight(),atlas.getWidth(),atlas.getHeight());
            assertFalse(gun.isEmpty(),"Lecter OBJ has no triangulated geometry"); combined.addAll(gun);
            float floorU=atlas.getWidth()*.5f/atlas.getWidth(), floorV=(skin.getHeight()+.5f)/atlas.getHeight();
            net.minecraft.world.phys.Vec3[] floor={new net.minecraft.world.phys.Vec3(-.8,0,-1.2),new net.minecraft.world.phys.Vec3(.8,0,-1.2),new net.minecraft.world.phys.Vec3(.8,0,1.8),new net.minecraft.world.phys.Vec3(-.8,0,1.8)};
            for(int[] corner:new int[][]{{0,1,2},{0,2,3}})for(int index:corner)combined.add(new ClosedEffectMesh.Vertex(floor[index],floorU,floorV));
            BufferedImage sheet=new BufferedImage(4*512,540,BufferedImage.TYPE_INT_ARGB);
            String[] names={"front","side","top","oblique"}; double[][] angles={{0,0},{Math.PI/2,0},{0,Math.PI/2},{.7,.55}};
            String key=(slim?"slim":"wide")+"-"+(active==HumanoidArm.LEFT?"left":"right");
            for(int vi=0;vi<4;vi++) { BufferedImage view=CpuMeshRasterizer.rasterize(combined,atlas,angles[vi][0],angles[vi][1],0xffffffff,512); int count=0;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++){int p=view.getRGB(x,y);if((p>>>24)!=0){sheet.setRGB(vi*512+x,y,p);count++;}}
                assertTrue(count>0,label+" zero body coverage in "+names[vi]);
                Graphics2D g=sheet.createGraphics();g.setColor(Color.WHITE);g.drawString(names[vi],vi*512+12,532);g.dispose();
                Path rawView=out.resolve("lecter-prone-"+key+"-"+names[vi]+"-512.png"); ImageIO.write(view,"png",rawView.toFile());
                Path reviewView=out.resolve("lecter-prone-"+key+"-"+names[vi]+"-512-neutral-bg.png"); ImageIO.write(neutralBackground(view),"png",reviewView.toFile());
            }
            Path png=out.resolve("lecter-prone-"+key+".png"); ImageIO.write(sheet,"png",png.toFile());
            BufferedImage gunOnly=CpuMeshRasterizer.rasterize(gun,atlas,angles[3][0],angles[3][1],0xffffffff,512);
            Path diagnostic=out.resolve("lecter-prone-"+key+"-gun-only-oblique-512.png"); ImageIO.write(gunOnly,"png",diagnostic.toFile());
            Path diagnosticReview=out.resolve("lecter-prone-"+key+"-gun-only-oblique-512-neutral-bg.png"); ImageIO.write(neutralBackground(gunOnly),"png",diagnosticReview.toFile());
             double separation=result.primary().hand().sub(result.support().hand()).length();
             manifest.append(key).append(" actual baked skin + frozen Lecter OBJ, primary=").append(result.primary().hand())
                     .append(" support=").append(result.support().hand()).append(" separation=").append(separation).append(" supportFraction=").append(result.supportFraction())
                      .append(" supportFraction=").append(result.supportFraction()).append(" floor+reached, gun vertices=").append(gun.size()).append(": ").append(png).append(" diagnostic=").append(diagnostic).append("\nNeutral-bg files are presentation-only #808080 alpha composites; no shader/native lighting proof. Raw RGBA retained; coverage assertions use raw raster pixels.\n").append("  gun-only neutral review: ").append(diagnosticReview).append('\n');
            for(int vi=0;vi<names.length;vi++) manifest.append("  ").append(names[vi]).append(" yaw/pitch=").append(angles[vi][0]).append('/').append(angles[vi][1]).append(" raw=").append(out.resolve("lecter-prone-"+key+"-"+names[vi]+"-512.png")).append(" review=").append(out.resolve("lecter-prone-"+key+"-"+names[vi]+"-512-neutral-bg.png")).append('\n');
        }
        Files.writeString(out.resolve("manifest.txt"),manifest);
    }

    @Test void lecterNativeAnimationFrameZeroCoversAllFrozenObjFaceCentroids() throws Exception {
        BufferedImage frame=lecterFrameZero(); assertEquals(256,frame.getWidth()); assertEquals(256,frame.getHeight());
        int[] counts=objCentroidCoverage(frame); assertEquals(355,counts[0],"frozen Lecter OBJ face count");
        assertEquals(355,counts[1],"all frozen Lecter OBJ face UV centroids must sample visible native frame-0 texels");
    }

    private static BufferedImage lecterFrameZero() throws Exception {
        BufferedImage source=resourceTexture("/assets/moonstation14/textures/item/guns/weapon_rifle_lecter.png");
        var mcmeta=GunMountTransformTest.resourceJson("assets/moonstation14/textures/item/guns/weapon_rifle_lecter.png.mcmeta");
        var section=net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER
                .fromJson(mcmeta.getAsJsonObject("animation"));
        var size=section.calculateFrameSize(source.getWidth(),source.getHeight());
        assertEquals(256,size.width()); assertEquals(256,size.height());
        var first=mcmeta.getAsJsonObject("animation").getAsJsonArray("frames").get(0);
        int frame=first.isJsonPrimitive()?first.getAsInt():first.getAsJsonObject().get("index").getAsInt();
        int columns=source.getWidth()/size.width();
        int x=(frame%columns)*size.width(), y=(frame/columns)*size.height();
        return source.getSubimage(x,y,size.width(),size.height());
    }

    private static int[] objCentroidCoverage(BufferedImage texture) throws Exception {
        var uv=new ArrayList<float[]>(); int faces=0,covered=0;
        try(InputStream in=LecterPronePosePreviewTest.class.getResourceAsStream("/assets/moonstation14/models/item/guns/weapon_rifle_lecter.obj")) {
            assertNotNull(in); for(String line:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                String[] f=line.trim().split("\\s+"); if(line.startsWith("vt "))uv.add(new float[]{Float.parseFloat(f[1]),Float.parseFloat(f[2])});
                else if(line.startsWith("f ")) { String[] face=Arrays.copyOfRange(f,1,f.length); double u=0,v=0; for(String ref:face) { String[] ids=ref.split("/");float[] point=uv.get(Integer.parseInt(ids[1])-1);u+=point[0]/face.length;v+=point[1]/face.length; }
                    faces++; int tx=Math.max(0,Math.min(texture.getWidth()-1,(int)(u*texture.getWidth()))),ty=Math.max(0,Math.min(texture.getHeight()-1,(int)(v*texture.getHeight())));
                    if((texture.getRGB(tx,ty)>>>24)!=0)covered++;
                }
            }
        } return new int[]{faces,covered};
    }

    private static BufferedImage skin(boolean slim) throws Exception {
        String path="/assets/minecraft/textures/entity/player/"+(slim?"slim":"wide")+"/steve.png";
        try(InputStream in=LecterPronePosePreviewTest.class.getResourceAsStream(path)) { if(in!=null)return ImageIO.read(in); }
        Path root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();
        Path jar=root.resolve("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(jar.toFile())) { var entry=zip.getEntry(path.substring(1)); assertNotNull(entry,path); try(InputStream in=zip.getInputStream(entry)){return ImageIO.read(in);} }
    }
    private static BufferedImage resourceTexture(String path) throws Exception {
        try(InputStream in=LecterPronePosePreviewTest.class.getResourceAsStream(path)){assertNotNull(in,path);return ImageIO.read(in);}
    }
    private static BufferedImage neutralBackground(BufferedImage raw) {
        BufferedImage result=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=result.createGraphics();g.setColor(new Color(0x80,0x80,0x80));g.fillRect(0,0,result.getWidth(),result.getHeight());g.drawImage(raw,0,0,null);g.dispose();return result;
    }
    private static List<ClosedEffectMesh.Vertex> readLecterObj(Matrix4f world,int gunWidth,int gunYOffset,int gunHeight,int atlasWidth,int atlasHeight) throws Exception {
        var positions=new ArrayList<Vector3f>(); var uvs=new ArrayList<float[]>(); var out=new ArrayList<ClosedEffectMesh.Vertex>();
        try(InputStream in=LecterPronePosePreviewTest.class.getResourceAsStream("/assets/moonstation14/models/item/guns/weapon_rifle_lecter.obj")) {
            assertNotNull(in,"Lecter frozen OBJ"); for(String line:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                String[] f=line.trim().split("\\s+"); if(line.startsWith("v ")) positions.add(new Vector3f(Float.parseFloat(f[1]),Float.parseFloat(f[2]),Float.parseFloat(f[3])));
                else if(line.startsWith("vt ")) uvs.add(new float[]{Float.parseFloat(f[1]),Float.parseFloat(f[2])});
                else if(line.startsWith("f ")) { String[] face=Arrays.copyOfRange(f,1,f.length); for(int j=1;j+1<face.length;j++) for(String ref:new String[]{face[0],face[j],face[j+1]}) {
                    String[] ix=ref.split("/"); Vector3f p=world.transformPosition(new Vector3f(positions.get(Integer.parseInt(ix[0])-1))); float[] uv=uvs.get(Integer.parseInt(ix[1])-1);
                    out.add(new ClosedEffectMesh.Vertex(new net.minecraft.world.phys.Vec3(p.x,p.y,p.z),(float)(uv[0]*gunWidth/atlasWidth),(float)((gunYOffset+uv[1]*gunHeight)/(double)atlasHeight)));
                }}
            }
        } return out;
    }
    private static final class SkinCapture implements VertexConsumer {
        record V(net.minecraft.world.phys.Vec3 pos,float u,float v) {}
        final java.util.List<V> vertices=new ArrayList<>(); net.minecraft.world.phys.Vec3 pending;
        @Override public VertexConsumer addVertex(float x,float y,float z){pending=new net.minecraft.world.phys.Vec3(x,y,z);vertices.add(new V(pending,0,0));return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){if(!vertices.isEmpty())vertices.set(vertices.size()-1,new V(pending,u,v));return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;} @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
