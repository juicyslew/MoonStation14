package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import java.util.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.disposal.world.*;
import com.juicyslew.moonstation14.ms14.hands.live.*;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.world.entity.LivingEntity;

public final class DisposalBuildMenuService {
    private static final Map<DisposalBuildMenu,Long> REQUESTS=new WeakHashMap<>();
     private record Pending(DisposalBuildMenu menu, HandActorAuthority.Snapshot authority, ServerPlayer actor, LivingEntity body, net.minecraft.world.phys.Vec3 bodyPos, String hand, String token, long revision, BlockPos host, BlockPos target, boolean sealed, String prototype, long planRevision, com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame, long started, net.minecraft.world.level.block.state.BlockState hostState, java.util.UUID hostGeneration) {}
     private static final Map<java.util.UUID,Pending> PENDING=new HashMap<>();
     static { DisposalConstructionMode.setInvalidationListener(state->{var p=PENDING.get(state.bodyUUID());if(p!=null)cancel(p,"Construction cancelled");}); }
    private DisposalBuildMenuService(){}
     public static boolean openEdit(ServerPlayer actor, BodyBlockUseRequest request,
             com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.BlockUseAdmission admitted) {
         if (actor == null || request == null || admitted == null
                 || request.intent() != BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                 || !request.target().equals(admitted.hit().pos()) || request.face() != admitted.hit().face()) return false;
         var authority = HandActorAuthority.resolve(actor).orElse(null);
         if (authority == null || !authority.equals(admitted.authority()) || !HandActorAuthority.revalidate(actor, authority)
                 || !(authority.body().level() instanceof ServerLevel level)
                 || admitted.hands() != authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                  || DisposalTubeWorldData.existing(level).isEmpty()) return false;
         var data = DisposalTubeWorldData.existing(level).orElseThrow();
         var lookup = data.lookup(level, request.target());
         if (lookup.kind() != DisposalTubeWorldData.LookupKind.PRESENT || lookup.record() == null) return false;
         var tube = lookup.record();
         UUID session = UUID.randomUUID();
          actor.openMenu(new SimpleMenuProvider((id, inv, player) -> new DisposalBuildMenu(id, request.target(),
                   session, tube.id(), tube.revision(), tube.frame(), tube.anchored(), tube.routingTag(), tube.routingFilter(), level, authority, request),
                 Component.literal("Disposal Tube")), buf -> {
              buf.writeBlockPos(request.target()); buf.writeUUID(session); buf.writeBoolean(true); buf.writeUUID(tube.id());
               buf.writeVarLong(tube.revision()); buf.writeEnum(tube.frame().forward()); buf.writeEnum(tube.frame().up()); buf.writeBoolean(tube.anchored());
              buf.writeUtf(tube.routingTag(),64); buf.writeUtf(tube.routingFilter(),128);
         });
         if (actor.containerMenu instanceof DisposalBuildMenu menu && menu.session().equals(session)) {
             REQUESTS.put(menu, 0L);
             return true;
         }
         return false;
     }
       public static boolean select(ServerPlayer actor,DisposalBuildSelection request){
         if(!(actor.containerMenu instanceof DisposalBuildMenu menu)||!menu.stillValid(actor)||request.containerId()!=menu.containerId||!request.session().equals(menu.session())||request.requestId()<=REQUESTS.getOrDefault(menu,-1L))return false;
          if (!menu.editing()) return false;
          return rotate(actor, menu, request);
       }

       public static boolean useSelected(ServerPlayer actor, BodyBlockUseRequest request,
              com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.BlockUseAdmission admitted) {
          var authority=admitted==null?null:admitted.authority();
           var plan=DisposalConstructionMode.activePlan(authority).orElse(null);
           if(plan==null||request.intent()!=BodyBlockUseRequest.Intent.USE||!request.target().equals(admitted.hit().pos())
                   ||request.face()!=admitted.hit().face()||admitted.hands()!=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()))return false;
           return startPlacement(actor,request,authority,plan);
       }

       /** Dispatch predicate shared by the live held-item route and placement validation. */
       public static boolean matchesActiveMaterial(HandActorAuthority.Snapshot authority,ItemStack held){
           var plan=DisposalConstructionMode.activePlan(authority).orElse(null);
           if(plan==null||held==null||held.isEmpty())return false;
           var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(plan.prototype()));
           return resolved!=null&&DisposalConstructionPlacementPolicy.matchesMaterial(resolved.construction(),held.getItem());
       }

      private static boolean startPlacement(ServerPlayer actor, BodyBlockUseRequest request,
               HandActorAuthority.Snapshot authority, DisposalConstructionMode.Plan plan) {
           String prototype=plan.prototype();
          if(actor==null||request==null||authority==null||!(authority.body().level() instanceof ServerLevel level)
                  ||!HandActorAuthority.revalidate(actor,authority))return false;
          if(PENDING.containsKey(authority.body().getUUID()))return false;
          var hands=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
           var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(prototype));
           if(resolved==null||resolved.construction()==null||!DisposalConstructionPlacementPolicy.matchesMaterial(resolved.construction(),hands==null?null:hands.stackCopy(request.handId()).map(ItemStack::getItem).orElse(null)))return false;
           if(hands==null||!request.handId().equals(hands.activeHand())||request.expectedRevision()!=hands.revision()
                   ||!authority.handIds().contains(request.handId())
                    ||!hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                    ||hands.stackCopy(request.handId()).filter(s->DisposalConstructionPlacementPolicy.enoughMaterial(resolved.construction(),s.getItem(),s.getCount())).isEmpty())return false;
          BlockPos host=request.target().immutable(); var state=level.getBlockState(host);
            boolean eligibleSteel=com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility.defaults().eligible(level,host);
            boolean liveTube=com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.existing(level)
                    .map(data -> data.lookup(level,host).kind()==com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.LookupKind.PRESENT).orElse(false);
            boolean sealed=DisposalConstructionTargetPolicy.sealed(eligibleSteel,liveTube,resolved.tube().kind());
            if(!eligibleHost(level,host,sealed))return false;
           BlockPos target=sealed?host:host.relative(request.face());
           var chunk=level.getChunkSource().getChunk(target.getX()>>4,target.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
           if(!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)||(!sealed&&!level.getBlockState(target).isAir()))return false;
           var look=authority.body().getLookAngle();
           var bodyLook=Direction.getNearest(look.x,look.y,look.z);
           var preferred=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation.placement(request.face(),bodyLook);
           var candidates=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientationCandidates.candidates(resolved.tube(),preferred,reciprocalNeighbours(level,target));
           if(candidates.isEmpty())return false;
           var frame=candidates.get(Math.floorMod(plan.rotationOffset(),candidates.size()));
          if(!collisionFree(level,authority.body(),target,prototype,frame,sealed))return false;
          var chunkAtHost=level.getChunkSource().getChunk(host.getX()>>4,host.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
          if(!(chunkAtHost instanceof net.minecraft.world.level.chunk.LevelChunk hostChunk))return false;
          var generations=hostChunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
          if(sealed&&generations==null)generations=hostChunk.getData(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
          var generation=sealed&&generations!=null?generations.active(host.asLong()).orElse(null):null;
          if(sealed&&generation==null){
              if(generations.hasRecord(host.asLong())||!generations.registerIfAbsent(host.asLong(),java.util.UUID.randomUUID()))return false;
              generation=generations.active(host.asLong()).orElseThrow();
              hostChunk.setUnsaved(true);
          }
           var pending=new Pending(null,authority,actor,authority.body(),authority.body().position(),request.handId(),request.expectedToken(),hands.revision(),host,target,sealed,prototype,plan.revision(),frame,level.getGameTime(),state,generation);
           PENDING.put(authority.body().getUUID(),pending);
           // Zero-time recipes are deliberately synchronous: there is no tick-sized
           // pending-action barrier for the ordinary pipe placement adaptation.
           if (DisposalConstructionPlacementPolicy.requiredTicks(resolved.construction()) == 0)
               finish(pending, level);
           return true;
      }

      private static boolean collisionFree(ServerLevel level, LivingEntity body, BlockPos pos, String prototype,
              com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame, boolean sealed) {
           var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(prototype));
           if(resolved==null||resolved.tube()==null||resolved.construction()==null)return false;
          var boxes=resolved.geometry().boxes(resolved.tube(),frame);
          var bodyBox=body.getBoundingBox();
          for(var local:boxes){
              var world=local.move(pos);
              if(world.intersects(bodyBox))return false;
              for(var entity:level.getEntities(body,world.inflate(1.0e-7))) if(entity.getBoundingBox().intersects(world))return false;
              int minX=(int)Math.floor(world.minX),maxX=(int)Math.floor(Math.nextDown(world.maxX));
              int minY=(int)Math.floor(world.minY),maxY=(int)Math.floor(Math.nextDown(world.maxY));
              int minZ=(int)Math.floor(world.minZ),maxZ=(int)Math.floor(Math.nextDown(world.maxZ));
              for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
                  BlockPos cell=new BlockPos(x,y,z);
                  if(cell.equals(pos)&&sealed)continue;
                  var chunk=level.getChunkSource().getChunk(x>>4,z>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
                  if(!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk))return false;
                  var state=level.getBlockState(cell);
                  for(var shapeBox:state.getCollisionShape(level,cell,net.minecraft.world.phys.shapes.CollisionContext.empty()).toAabbs())
                      if(shapeBox.move(cell).intersects(world))return false;
              }
          }
           return true;
       }
       private static boolean eligibleHost(ServerLevel level,BlockPos host,boolean sealed){
           var state=level.getBlockState(host);
           if(state.is(net.minecraft.tags.BlockTags.DOORS)||state.is(net.minecraft.tags.BlockTags.TRAPDOORS)
                   ||state.is(net.minecraft.tags.BlockTags.FENCES)||state.is(net.minecraft.tags.BlockTags.FENCE_GATES))return false;
           var entity=level.getBlockEntity(host);
           if(entity!=null&&!state.is(ModBlocks.DISPOSAL_TUBE.get()))return false;
           if(sealed)return true;
           return state.is(ModBlocks.DISPOSAL_TUBE.get())||state.isFaceSturdy(level,host,Direction.UP)
                   ||state.getCollisionShape(level,host).isEmpty()==false;
       }
       private static Set<Direction> reciprocalNeighbours(ServerLevel level,BlockPos target){
           var result=EnumSet.noneOf(Direction.class);var data=DisposalTubeWorldData.existing(level).orElse(null);if(data==null)return result;
           for(Direction face:Direction.values()){
               BlockPos neighbour=target.relative(face);var chunk=level.getChunkSource().getChunkNow(neighbour.getX()>>4,neighbour.getZ()>>4);
               if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL))continue;
               var record=data.lookup(level,neighbour).record();if(record==null)continue;
               var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(record.prototypeId()));
               if(resolved==null||resolved.tube()==null)continue;
               if(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting.connections(resolved.tube(),record.frame())
                       .contains(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection.fromMC(face.getOpposite())))result.add(face);
           }
           return result;
       }
      public static boolean configure(ServerPlayer actor, DisposalTubeConfigureRequest request) {
          if (!(actor.containerMenu instanceof DisposalBuildMenu menu) || !menu.editing() || !menu.stillValid(actor)
                  || request.containerId()!=menu.containerId || !request.session().equals(menu.session())
                  || request.requestId()<=REQUESTS.getOrDefault(menu,-1L)
                  || !request.tubeId().equals(menu.tubeId()) || request.revision()!=menu.tubeRevision()) return false;
          REQUESTS.put(menu, request.requestId());
          if (request.tag().length()>64 || request.filter().length()>128
                  || !DisposalTubeRouting.validTaggerInput(request.tag())
                  || !DisposalTubeRouting.validTagExpression(request.filter())) {
              menu.result("Invalid tag or filter"); return false;
          }
          ServerLevel level=(ServerLevel)actor.level();
          var data=DisposalTubeWorldData.existing(level).orElse(null);
          var exact=data==null?null:data.lookup(level,menu.pos()).record();
          if(!mayConfigure(exact, request.tubeId(), request.revision())) { menu.result("Target changed"); return false; }
          if(!data.configure(level,exact,request.tag(),request.filter(),pos->exposed(level,pos))){menu.result("Target changed");return false;}
          var updated=data.lookup(level,menu.pos()).record();
          if(updated==null||!updated.id().equals(exact.id())||updated.revision()!=exact.revision()+1){menu.result("Target changed");return false;}
          menu.routingUpdated(updated.id(),updated.revision(),updated.routingTag(),updated.routingFilter());
          menu.result("Routing updated"); return true;
      }
      static boolean routingConfigurable(String prototype) {
          return prototype != null && (prototype.endsWith(":disposal_tagger") || prototype.endsWith(":disposal_router")
                  || prototype.endsWith(":disposal_router_flipped"));
      }
      static boolean mayConfigure(DisposalTubeRecord exact, UUID id, long revision) {
          return exact != null && exact.id().equals(id) && exact.revision() == revision
                  && routingConfigurable(exact.prototypeId());
      }
       private static boolean rotate(ServerPlayer actor, DisposalBuildMenu menu, DisposalBuildSelection request) {
          ServerLevel level = (ServerLevel) actor.level();
          var data = DisposalTubeWorldData.existing(level).orElse(null);
          if (data == null) { menu.result("Target changed"); return false; }
         var lookup = data.lookup(level, menu.pos());
         var exact = lookup.record();
         if (lookup.kind() != DisposalTubeWorldData.LookupKind.PRESENT || exact == null
                 || !exact.id().equals(menu.tubeId()) || exact.revision() != menu.tubeRevision()) {
             menu.result("Target changed"); return false;
          }
           if (!mayRotate(exact)) { menu.result("Tube must be unanchored"); return false; }
           if(!HandActorAuthority.revalidateInMenu(actor,menu.authority(),menu)||!menu.stillValid(actor))return false;
           var candidateIndex=rotationCandidateIndex(request.prototypeId());
           if(candidateIndex.isEmpty())return false;
           var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(exact.prototypeId()));
           if(resolved==null||resolved.tube()==null)return false;
           var candidates=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientationCandidates.candidates(
                   resolved.tube(),exact.frame(),reciprocalNeighbours(level,exact.pos()));
           int index=candidateIndex.getAsInt();
           if(index>=candidates.size())return false;
           var next=candidates.get(index);
           if(exact.frame().equals(next))return false;
           REQUESTS.put(menu,request.requestId());
           boolean begun=com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeRotationUse
                   .beginConfirmedRotation(actor,menu,exact,next);
           if(begun)menu.result("Construction started");
           return begun;
      }

      static OptionalInt rotationCandidateIndex(String command){
          if(command==null||!command.startsWith("@rotate_confirm:"))return OptionalInt.empty();
          String suffix=command.substring("@rotate_confirm:".length());
          if(suffix.isEmpty()||suffix.length()>2||!suffix.chars().allMatch(Character::isDigit))return OptionalInt.empty();
          try{int index=Integer.parseInt(suffix);return index>=0&&index<24?OptionalInt.of(index):OptionalInt.empty();}
          catch(NumberFormatException ignored){return OptionalInt.empty();}
      }

      static Optional<Direction> rotationTarget(Direction current, String command) {
         if (current == null || !current.getAxis().isHorizontal()) return Optional.empty();
         return switch (command) {
             case "@rotate_cw" -> Optional.of(current.getClockWise());
             case "@rotate_ccw" -> Optional.of(current.getCounterClockWise());
             case "@rotate_reset" -> Optional.of(Direction.NORTH);
             default -> Optional.empty();
         };
     }

     static boolean mayRotate(DisposalTubeRecord tube) { return tube != null && !tube.anchored(); }
      private static HandActorAuthority.Snapshot menuAuthority(DisposalBuildMenu menu) { return menu.authority(); }
      private static BodyBlockUseRequest menuOpening(DisposalBuildMenu menu) { return menu.openingRequest(); }
    public static void playerTick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;
         for(var entry:new ArrayList<>(PENDING.entrySet())) { Pending p=entry.getValue(); if(p.actor()!=actor)continue;
              var plan=DisposalConstructionMode.activePlan(p.authority()).orElse(null);
              if(!HandActorAuthority.revalidate(actor,p.authority())||!DisposalConstructionPlacementPolicy.samePlan(plan,p.prototype(),p.planRevision())
                      ||p.body().level()!=actor.level()||!p.body().position().equals(p.bodyPos())||!sourceMatches(p)) { cancel(p,"Construction cancelled"); continue; }
              ServerLevel level=(ServerLevel)actor.level(); if(!hostUnchanged(level,p)){cancel(p,"Construction cancelled");continue;}
              var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.prototype()));
              if(resolved==null||resolved.construction()==null||level.getGameTime()-p.started()<DisposalConstructionPlacementPolicy.requiredTicks(resolved.construction()))continue;
             finish(p,level);
         }
      }

      static Optional<com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation> rotationFrameTarget(
              com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation current,String command) {
          if(current==null)return Optional.empty();
          return switch(command) {
              case "@rotate_cw" -> Optional.of(new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(
                      current.world(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection.EAST),current.up()));
              case "@rotate_ccw" -> Optional.of(new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(
                      current.world(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection.WEST),current.up()));
              case "@rotate_reset" -> Optional.of(new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(
                      current.up().getAxis()==Direction.Axis.Z?Direction.EAST:Direction.NORTH,current.up()));
              default -> Optional.empty();
          };
      }
     private static void cancel(Pending pending,String message){PENDING.remove(pending.body().getUUID(),pending);if(pending.menu()!=null)pending.menu().result(message);}
     private static boolean sourceMatches(Pending p) {
         var auth=p.authority(); if(auth==null||!auth.body().equals(p.body())||!HandActorAuthority.revalidate(p.actor(),auth))return false;
         var h=p.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
         var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.prototype()));
         if(resolved==null||resolved.construction()==null)return false;
         return h!=null&&h.revision()==p.revision()&&p.hand().equals(h.activeHand())&&h.token(p.hand()).filter(p.token()::equals).isPresent()&&h.stackCopy(p.hand()).map(s->DisposalConstructionPlacementPolicy.enoughMaterial(resolved.construction(),s.getItem(),s.getCount())).orElse(false);
    }
    private static void finish(Pending p,ServerLevel level) {
         var menu=p.menu(); var auth=p.authority(); boolean recipe=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.prototype()))!=null;
           var plan=auth==null?null:DisposalConstructionMode.activePlan(auth).orElse(null);
           var recipeData=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.prototype()));
           long required=recipeData==null?Long.MAX_VALUE:DisposalConstructionPlacementPolicy.requiredTicks(recipeData.construction());
           if(auth==null||!DisposalConstructionPlacementPolicy.samePlan(plan,p.prototype(),p.planRevision())
                   ||!DisposalBuildActionPolicy.mayComplete(level.getGameTime()-p.started(),required,true,p.body().position().equals(p.bodyPos()),validPlacement(level,p),recipe,sourceMatches(p))||!HandActorAuthority.revalidate(p.actor(),auth)){cancel(p,"Construction failed");return;}
         var data=DisposalTubeWorldData.forMutation(level); if(data.lookup(level,p.target()).kind()!=DisposalTubeWorldData.LookupKind.ABSENT){cancel(p,"Target unavailable");return;}
          if(!validPlacement(level,p)||!collisionFree(level,p.body(),p.target(),p.prototype(),p.frame(),p.sealed())||!sourceMatches(p)){cancel(p,"Construction cancelled");return;}
        var hands=auth.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
         if(hands==null){cancel(p,"Materials changed");return;}
         var resolved=PrototypeRuntime.serverDisposals().get(net.minecraft.resources.ResourceLocation.tryParse(p.prototype()));
          if(resolved==null||resolved.construction()==null||DisposalConstructionPlacementPolicy.requiredMaterial(resolved.construction()).isEmpty()){cancel(p,"Target unavailable");return;}
         int cost=resolved.construction().steelCount();
           var before=hands.stackCopy(p.hand()).orElse(ItemStack.EMPTY); if(!DisposalConstructionPlacementPolicy.enoughMaterial(resolved.construction(),before.getItem(),before.getCount())){cancel(p,"Need "+cost+" material");return;}
         ItemStack after=before.copy(); after.shrink(cost);
          boolean hostWall=p.sealed();
          if(hostWall && !p.prototype().equals(ModDisposals.id("disposal_pipe").toString())) { cancel(p,"Target unavailable"); return; }
           if(resolved==null){cancel(p,"Target unavailable");return;}
          var record=new DisposalTubeRecord(UUID.randomUUID(),p.target(),p.prototype(),p.frame(),resolved.anchored(),"","",0,
                  DisposalTubeRecord.HostKind.EXPOSED,"",null);
          boolean installed=hostWall ? data.installSealed(level,p.host(),p.frame(),p.prototype())
                  : data.installExposed(level,record);
          if(!installed) { cancel(p,"Target changed"); return; }
          if(hostWall) record=data.lookup(level,p.host()).record();
         var committed=BodyHeldItemMutation.commit(p.actor(),auth,hands,p.hand(),p.token(),p.revision(),before,after);
         var materials = observeMaterials(auth.body(), level, p.hand(), p.token(), p.revision(), before, after);
         var decision = DisposalBuildCommitPolicy.decide(committed, materials);
         if (decision == DisposalBuildCommitPolicy.Decision.ROLLBACK_TUBE) {
              boolean rolledBack = data.remove(level, record, pos -> true);
              if (rolledBack && record.hostKind() == DisposalTubeRecord.HostKind.EXPOSED
                      && level.getBlockState(record.pos()).is(ModBlocks.DISPOSAL_TUBE.get()))
                  level.setBlock(record.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
              cancel(p, rolledBack ? "Construction failed" : "Construction recovery required");
             return;
         }
         if (decision == DisposalBuildCommitPolicy.Decision.KEEP_TUBE_RECOVERY) {
              if(menu!=null)menu.result("Construction recovery required");
              PENDING.remove(p.body().getUUID(),p);
             return;
         }
          if(menu!=null)menu.result("Construction complete"); PENDING.remove(p.body().getUUID(),p);
     }
     private static DisposalBuildCommitPolicy.Materials observeMaterials(LivingEntity body, ServerLevel level,
             String hand, String token, long revision, ItemStack before, ItemStack after) {
         if (body == null || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                 || level.getEntity(body.getUUID()) != body) return DisposalBuildCommitPolicy.Materials.UNCERTAIN;
         var current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
         if (current == null || !current.compatible(body)) return DisposalBuildCommitPolicy.Materials.UNCERTAIN;
         var source = current.stackCopy(hand).orElse(ItemStack.EMPTY);
         var currentToken = current.token(hand).orElse(null);
         if (token.equals(currentToken) && source.getCount() == before.getCount()
                 && ItemStack.isSameItemSameComponents(source, before)) return DisposalBuildCommitPolicy.Materials.UNCHANGED;
         boolean expectedAfter = after.isEmpty()
                 ? source.isEmpty() && currentToken == null && !containsToken(body, current, token)
                 : token.equals(currentToken) && source.getCount() == after.getCount()
                 && ItemStack.isSameItemSameComponents(source, after);
         if (expectedAfter && revision < Long.MAX_VALUE && current.revision() == revision + 1)
             return DisposalBuildCommitPolicy.Materials.CONSUMED;
         return DisposalBuildCommitPolicy.Materials.UNCERTAIN;
     }
     private static boolean containsToken(LivingEntity body,
             com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, String token) {
         if (hands.handIds().stream().anyMatch(hand -> hands.token(hand).filter(token::equals).isPresent())) return true;
         return LiveHands.equipmentSnapshot(body).stream().flatMap(snapshot -> snapshot.slots().stream())
                 .anyMatch(slot -> slot.token().filter(token::equals).isPresent());
     }
       private static boolean exposed(ServerLevel level,BlockPos pos){
           var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
           return chunk!=null&&chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                   &&DisposalTubeWorldData.existing(level).map(data->data.lookup(level,pos).kind()==DisposalTubeWorldData.LookupKind.PRESENT).orElse(false);
       }
       private static boolean hostUnchanged(ServerLevel level,Pending pending) {
           if(!pending.sealed())return true;
           if(!com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility.defaults().eligible(level,pending.host())
                   ||!level.getBlockState(pending.host()).equals(pending.hostState()))return false;
           var chunk=level.getChunkSource().getChunk(pending.host().getX()>>4,pending.host().getZ()>>4,
                   net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
           if(!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk full))return false;
           var generations=full.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
           var current=generations==null?null:generations.active(pending.host().asLong()).orElse(null);
           return java.util.Objects.equals(current,pending.hostGeneration());
       }
       private static boolean validPlacement(ServerLevel level, Pending pending) {
           BlockPos target=pending.target();
           var chunk=level.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4);
           if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL))return false;
           if(pending.sealed())return hostUnchanged(level,pending);
           return chunk.getBlockState(target).isAir();
       }
}
