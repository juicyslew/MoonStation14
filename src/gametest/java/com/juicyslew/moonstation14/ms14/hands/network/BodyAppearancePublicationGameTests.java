package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;

@GameTestTags({"equipment-render"})
public final class BodyAppearancePublicationGameTests {
    private BodyAppearancePublicationGameTests() { }
    public static void gatewayBatchPublishesFinalUnownedBodyAppearanceOnce(GameTestHelper helper) {
        Villager body=EntityType.VILLAGER.create(helper.getLevel());
        if(body==null) throw new AssertionError("human body created");
        body.setNoAi(true); body.setPos(helper.absolutePos(new BlockPos(2,1,2)).getCenter());
        if(!helper.getLevel().addFreshEntity(body)||!CharacterIdentitySystem.enroll(body,helper.getLevel(),ModCharacters.HUMAN_ID)) throw new AssertionError("human enrolled");
        LiveHands initial=LiveHands.initialize(body).orElseThrow();
        var published=new ArrayList<BodyAppearanceSnapshot>();
        BodyHandNetworking.setPublicationTestSink(published::add);
        try {
            LiveHands one=initial.placeCraftOutput(initial.revision(),new ItemStack(Items.APPLE),"held").orElseThrow();
            if(!LiveHandsCommitGateway.commitTrusted(body,initial,one)) throw new AssertionError("first trusted commit");
            LiveHands two=one.placeCraftOutput(one.revision(),new ItemStack(Items.STICK),"held2").orElseThrow();
            LiveHands three=two.placeCraftOutput(two.revision(),new ItemStack(Items.DIAMOND),"held3").orElseThrow();
            LiveHandsCommitGateway.batch(() -> {
                if(!LiveHandsCommitGateway.commitTrusted(body,one,two)||!LiveHandsCommitGateway.commitTrusted(body,two,three)) throw new AssertionError("batched commits");
                return null;
            });
            if(published.size()!=2 || published.get(1).revision()!=three.revision()
                    || published.get(1).hands().stream().filter(e->e.id().equals("left")).findFirst().orElseThrow().item()==null)
                throw new AssertionError("one final public state per commit/batch");
        } finally { BodyHandNetworking.setPublicationTestSink(null); }
        helper.succeed();
    }
}
