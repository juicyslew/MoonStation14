package com.juicyslew.moonstation14.ms14.ui.preview;

import com.juicyslew.moonstation14.ms14.combat.*;
import com.juicyslew.moonstation14.ms14.hands.client.*;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HandAmmoHudPreviewTest {
 @Test void handCountersPreviewAtBothSizesAndOrders() throws Exception {
  var root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();
   var out=root.resolve(System.getProperty("ms14.handAmmoPreviewDir", "build/guns-sprint/owner-feedback-round4/hand-ammo-preview"));Files.createDirectories(out);
  var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
   manager.register(ModGunProfiles.TYPE);manager.register(ModAmmoContainerProfiles.TYPE);manager.register(ModBatteryProfiles.TYPE);
   var guns=new LinkedHashMap<ResourceLocation,GunProfile>();var containers=new LinkedHashMap<ResourceLocation,AmmoContainerProfile>();var batteries=new LinkedHashMap<ResourceLocation,BatteryProfile>();
   manager.stage(Map.of(ModGunProfiles.TYPE,com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResourcesAll("gun_profile"),ModAmmoContainerProfiles.TYPE,com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResourcesAll("ammo_container_profile"),ModBatteryProfiles.TYPE,com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResourcesAll("battery_profile")),cats->{
    cats.get(ModGunProfiles.TYPE.typeId()).forEach((k,v)->guns.put(k,GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
    cats.get(ModAmmoContainerProfiles.TYPE.typeId()).forEach((k,v)->containers.put(k,AmmoContainerProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
    cats.get(ModBatteryProfiles.TYPE.typeId()).forEach((k,v)->batteries.put(k,BatteryProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
  });
  var gunProfile=Objects.requireNonNull(guns.get(ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_pistol_mk58")));
  var actualContainer=Objects.requireNonNull(containers.get(ResourceLocation.fromNamespaceAndPath("moonstation14","magazine_pistol")));
  var byPaper=Map.of(ResourceLocation.withDefaultNamespace("paper"),actualContainer);
  var body=UUID.fromString("00000000-0000-0000-0000-000000000100");var account=UUID.fromString("00000000-0000-0000-0000-000000000101");
  var manifest=new ArrayList<String>();var font=MinecraftBitmapFont.load(resources());
  for(int[] size:new int[][]{{320,240},{640,360}}) for(boolean reverse:new boolean[]{false,true}) {
   int w=size[0],h=size[1];String[] ids=reverse?new String[]{"right","left"}:new String[]{"left","right"};
   var gun=new ItemStack(Items.IRON_HOE);var gunMag=new ItemStack(Items.PAPER);gunMag.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(List.of(new ItemStack(Items.ARROW,7))));gun.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new MagazineChamberState(gunMag,ItemStack.EMPTY,true));
   var mag=new ItemStack(Items.PAPER);mag.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(List.of(new ItemStack(Items.ARROW,3))));
   var hands=new ArrayList<BodyHandStateSnapshot.Hand>();
   for(String id:ids){ItemStack stack=id.equals("left")?gun:mag;String itemId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();hands.add(new BodyHandStateSnapshot.Hand(id,"token-"+id,itemId,1,false,null,null,0,null,ItemPresentation.fromStack(stack),null));}
   var snapshot=new BodyHandStateSnapshot(4,1,BodyHandStateSnapshot.Reason.OK,account,body,7,2,"left",hands,List.of(),List.of(),false,null,10,null);
   var selection=new GunWeaponResolver.Selection(body,2,"left","token-left",gunProfile);
   var entries=HandAmmoHudProjection.project(snapshot,selection,gun,byPaper,Map.of());
   assertEquals(2,entries.size());assertEquals("left",entries.stream().filter(e->e.profile()!=null).findFirst().orElseThrow().handId());
   assertTrue(entries.stream().anyMatch(e->e.counter().text().equals("7/10")));assertTrue(entries.stream().anyMatch(e->e.counter().text().equals("3/10")));
   var bar=HandBarLayout.calculate(w,h,2);var info=HandInfoLayout.calculate(w,h,bar,entries.stream().anyMatch(e->e.handIndex()==0),entries.stream().anyMatch(e->e.handIndex()==1));assertNotNull(info);
   var paint=new CpuOrderedPaint(w,h,font);paint.fill(0,0,w,h,0xff5a1b7c);
   for(var e:entries){var tile=e.handIndex()==0?info.left():info.right();var hand=e.handIndex()==0?bar.leftHand():bar.rightHand();assertEquals(27,tile.height());paint.fill(tile.x(),tile.y(),tile.x()+tile.width(),tile.y()+tile.height(),0xff24343c);paint.fill(tile.x(),tile.y(),tile.x()+tile.width(),tile.y()+1,0xffffffff);paint.text(net.minecraft.network.chat.Component.literal(e.handId().equals("left")?"GUN":"MAG"),tile.x()+3,tile.y()+2,0xffffffff,false);paint.fill(hand.x(),hand.y(),hand.x()+hand.width(),hand.y()+hand.height(),0xff737d83);paint.text(net.minecraft.network.chat.Component.literal(e.handId().equals("left")?"G":"M"),hand.x()+5,hand.y()+5,0xffffffff,false);int x=tile.x()+3,y=tile.y()+14,bw=tile.width()-6,bh=tile.height()-14;assertTrue(bh>0);GunAmmoCounterPainter.paint(paint,e.counter(),x,y,bw,bh);assertTrue(tile.contains(x,y));}
   assertEquals(2,paint.paintedText().stream().filter(t->t.equals("7/10")||t.equals("3/10")).count());
   var file=out.resolve("hand-ammo-"+w+"x"+h+"-"+(reverse?"right-left":"left-right")+".png");javax.imageio.ImageIO.write(paint.image(),"PNG",file.toFile());manifest.add(file.getFileName().toString()+" "+w+"x"+h+" order="+String.join(",",ids));
  }
   Files.write(out.resolve("manifest.txt"),manifest);
   var cellId=ResourceLocation.fromNamespaceAndPath("moonstation14","power_cell_small");
   var cellProfile=Objects.requireNonNull(batteries.get(cellId));assertEquals(360,cellProfile.capacity());
   for(int[] size:new int[][]{{320,240},{640,360}}) for(boolean reverse:new boolean[]{false,true}) {
    int w=size[0],h=size[1];String[] ids=reverse?new String[]{"right","left"}:new String[]{"left","right"};
    var gun=new ItemStack(Items.IRON_HOE);var gunMag=new ItemStack(Items.PAPER);gunMag.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(List.of(new ItemStack(Items.ARROW,7))));gun.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new MagazineChamberState(gunMag,ItemStack.EMPTY,true));
    var cell=new ItemStack(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get());cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(180));
    var hands=new ArrayList<BodyHandStateSnapshot.Hand>();for(String id:ids){ItemStack stack=id.equals("left")?gun:cell;String itemId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();hands.add(new BodyHandStateSnapshot.Hand(id,"token-"+id,itemId,1,false,null,null,0,null,ItemPresentation.fromStack(stack),null));}
    var snapshot=new BodyHandStateSnapshot(4,1,BodyHandStateSnapshot.Reason.OK,account,body,7,2,"left",hands,List.of(),List.of(),false,null,10,null);
    var selection=new GunWeaponResolver.Selection(body,2,"left","token-left",gunProfile);
    var entries=HandAmmoHudProjection.project(snapshot,selection,gun,byPaper,batteries);
    assertEquals(2,entries.size());var cellEntry=entries.stream().filter(e->e.counter().kind()==GunAmmoCounterProjection.Kind.CELL_CHARGE).findFirst().orElseThrow();var gunEntry=entries.stream().filter(e->e.profile()!=null).findFirst().orElseThrow();assertEquals("right",cellEntry.handId());assertEquals("left",gunEntry.handId());assertTrue(cellEntry.counter().text().contains("180/360 J"));assertTrue(cellEntry.counter().text().contains("50%"));assertTrue(gunEntry.counter().capacity()>0);
    var bar=HandBarLayout.calculate(w,h,2);var info=HandInfoLayout.calculate(w,h,bar,true,true);assertNotNull(info);assertTrue(info.left().x()+info.left().width()<=info.right().x()||info.right().x()+info.right().width()<=info.left().x());var paint=new CpuOrderedPaint(w,h,font);paint.fill(0,0,w,h,0xff5a1b7c);
    for(var e:entries){var tile=e.handIndex()==0?info.left():info.right();paint.fill(tile.x(),tile.y(),tile.x()+tile.width(),tile.y()+tile.height(),0xff24343c);int x=tile.x()+3,y=tile.y()+14;GunAmmoCounterPainter.paint(paint,e.counter(),x,y,tile.width()-6,tile.height()-14);}
     assertTrue(paint.paintedText().stream().anyMatch(t->t.contains("50%")&&t.contains("180/360 J")),"charge text must reach the CPU HUD painter");
      var chargeDraw=paint.textDraws().stream().filter(t->t.text().contains("50%")&&t.text().contains("180/360 J")).findFirst().orElseThrow();
      assertEquals(HandInfoLayout.DETAIL_TEXT_SCALE,chargeDraw.scaleX(),0.001f);assertEquals(HandInfoLayout.DETAIL_TEXT_SCALE,chargeDraw.scaleY(),0.001f);
      var chargeTile=cellEntry.handIndex()==0?info.left():info.right();
      assertEquals(HandInfoLayout.DETAIL_MUTED_COLOR,chargeDraw.color());assertTrue(chargeTile.height()-14>=Math.round(paint.lineHeight()*HandInfoLayout.DETAIL_TEXT_SCALE));
    assertTrue(paint.paintedText().stream().anyMatch(t->t.equals(gunEntry.counter().text())),"gun ammo counter must coexist with standalone-cell readout");
    var file=out.resolve("hand-cell-"+w+"x"+h+"-"+(reverse?"right-left":"left-right")+".png");javax.imageio.ImageIO.write(paint.image(),"PNG",file.toFile());manifest.add(file.getFileName()+" "+w+"x"+h+" order="+String.join(",",ids)+" cell=180/360J 50%");
   }
   Files.write(out.resolve("manifest.txt"),manifest);
 }
 private static Path resources(){Path p=Path.of("").toAbsolutePath();while(p!=null){Path jar=p.resolve("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");if(Files.isRegularFile(jar))return jar;p=p.getParent();}return Path.of("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");}
}
