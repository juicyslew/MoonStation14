package com.juicyslew.moonstation14.ms14.construction.inventory;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyCraftPublicationTest {
    private static LiveHands state(long revision) {
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":"+revision+"}")).getOrThrow();
    }
    private static final class Owner implements BodyCraftInventoryService.Publication {
        LiveHands body; ItemStack world; boolean discarded; String fault;
        Owner(LiveHands body,ItemStack world,String fault){this.body=body;this.world=world;this.fault=fault;}
        public LiveHands body(){return body;}
        public void body(LiveHands value){
            if("before".equals(fault)){fault="done";throw new IllegalStateException();}
            body="diverge".equals(fault)?state(77):value;
            if("after".equals(fault)){fault="done";throw new IllegalStateException();}
            if("diverge".equals(fault)){fault="done";throw new IllegalStateException();}
        }
        public ItemStack world(){return world;}
        public void world(ItemStack value){
            if("worldAfter".equals(fault)){world=value;fault="done";throw new IllegalStateException();}
            if("worldBody".equals(fault)){body=state(77);world=value;fault="done";return;}
            world=value;
        }
        public void discardWorld(){
            if("discardBefore".equals(fault)){fault="done";throw new IllegalStateException();}
            discarded=true;world=null;
            if("discardAfter".equals(fault)){fault="done";throw new IllegalStateException();}
        }
    }
    @Test void publicationThrowBeforeApplyRestoresPartialSourceAndKeepsBody() {
        LiveHands before=state(1),staged=state(2); ItemStack source=new ItemStack(Items.IRON_INGOT,3),rem=source.copy(); rem.shrink(1);
        Owner owner=new Owner(before,source,"before");
        assertEquals(BodyCraftInventoryService.Result.DENIED,BodyCraftInventoryService.publishValidated(owner,before,staged,source,rem));
        assertSame(before,owner.body); assertTrue(ItemStack.matches(source,owner.world)); assertFalse(owner.discarded);
    }
    @Test void publicationThrowAfterExactApplyRestoresFullAndPartialTransactions() {
        for(boolean full:new boolean[]{false,true}) {
            LiveHands before=state(1),staged=state(2); ItemStack source=new ItemStack(Items.IRON_INGOT,full?1:3);
            ItemStack rem=source.copy(); rem.shrink(1); Owner owner=new Owner(before,source,"after");
            assertEquals(BodyCraftInventoryService.Result.DENIED,BodyCraftInventoryService.publishValidated(owner,before,staged,source,rem));
            assertSame(before,owner.body); assertTrue(ItemStack.matches(source,owner.world)); assertFalse(owner.discarded);
        }
    }
    @Test void divergentBodyAfterThrowIsNotOverwritten() {
        LiveHands before=state(1),staged=state(2); ItemStack source=new ItemStack(Items.IRON_INGOT,2),rem=source.copy(); rem.shrink(1);
        Owner owner=new Owner(before,source,"diverge");
        assertEquals(BodyCraftInventoryService.Result.RECOVERY_REQUIRED,BodyCraftInventoryService.publishValidated(owner,before,staged,source,rem));
        assertEquals(77,owner.body.revision()); assertTrue(ItemStack.matches(rem,owner.world));
    }
    @Test void worldSetterThrowAfterExactRemainderRestoresOnlyWhileBodyStillOriginal() {
        LiveHands before=state(1),staged=state(2); ItemStack source=new ItemStack(Items.IRON_INGOT,2),rem=source.copy(); rem.shrink(1);
        Owner owner=new Owner(before,source,"worldAfter");
        assertEquals(BodyCraftInventoryService.Result.DENIED,BodyCraftInventoryService.publishValidated(owner,before,staged,source,rem));
        assertSame(before,owner.body); assertTrue(ItemStack.matches(source,owner.world));
        Owner diverged=new Owner(before,source,"worldBody");
        assertEquals(BodyCraftInventoryService.Result.RECOVERY_REQUIRED,BodyCraftInventoryService.publishValidated(diverged,before,staged,source,rem));
        assertEquals(77,diverged.body.revision()); assertTrue(ItemStack.matches(rem,diverged.world));
    }
    @Test void fullDiscardThrowBeforeRestoresBodyButThrowAfterRemovalIsSuccess() {
        LiveHands before=state(1),staged=state(2); ItemStack source=new ItemStack(Items.IRON_INGOT);
        Owner beforeThrow=new Owner(before,source,"discardBefore");
        assertEquals(BodyCraftInventoryService.Result.DENIED,BodyCraftInventoryService.publishValidated(beforeThrow,before,staged,source,ItemStack.EMPTY));
        assertSame(before,beforeThrow.body); assertFalse(beforeThrow.discarded); assertTrue(ItemStack.matches(source,beforeThrow.world));
        Owner afterThrow=new Owner(before,source,"discardAfter");
        assertEquals(BodyCraftInventoryService.Result.SUCCESS,BodyCraftInventoryService.publishValidated(afterThrow,before,staged,source,ItemStack.EMPTY));
        assertSame(staged,afterThrow.body); assertTrue(afterThrow.discarded);
    }
    @Test void successPublishesBodyBeforeDiscardAndPartialLeavesRemainder() {
        LiveHands before=state(1),staged=state(2); ItemStack full=new ItemStack(Items.IRON_INGOT);
        Owner owner=new Owner(before,full,"none");
        assertEquals(BodyCraftInventoryService.Result.SUCCESS,BodyCraftInventoryService.publishValidated(owner,before,staged,full,ItemStack.EMPTY));
        assertSame(staged,owner.body); assertTrue(owner.discarded);
        ItemStack source=new ItemStack(Items.IRON_INGOT,2),rem=source.copy(); rem.shrink(1); Owner partial=new Owner(before,source,"none");
        assertEquals(BodyCraftInventoryService.Result.SUCCESS,BodyCraftInventoryService.publishValidated(partial,before,staged,source,rem));
        assertSame(staged,partial.body); assertTrue(ItemStack.matches(rem,partial.world)); assertFalse(partial.discarded);
    }
}
