package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end native creature melee after the public operator possession transition. */
@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle", "native-creature-combat", "character-identity-transfer"})
public final class NativeCreatureCombatGameTests {
    private NativeCreatureCombatGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=100)
    public static void koboldCombat(GameTestHelper helper) { exercise(helper, "moonstation14:kobold"); }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=100)
    public static void handlessCreatureCombat(GameTestHelper helper) { exercise(helper, "moonstation14:bat"); }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=100)
    @GameTestTags({"animal-speech"})
    public static void koboldTypedSpeechUsesReplacementAndCurrentBodyName(GameTestHelper helper) {
        exercise(helper, "moonstation14:kobold", true);
    }

    private static void exercise(GameTestHelper helper, String id) {
        exercise(helper, id, false);
    }

    private static void exercise(GameTestHelper helper, String id, boolean verifySpeech) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, id.endsWith("kobold") ? "NativeKobold" : "NativeBat");
        var opEntry = new ServerOpListEntry(fixture.player().getGameProfile(), 4, true);
        level.getServer().getPlayerList().getOps().add(opEntry);
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        if (type == null) throw new GameTestAssertException("missing entity type " + id);
        var entity = type.create(level);
        if (!(entity instanceof Mob body)) throw new GameTestAssertException("not a Mob: " + id);
        body.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3)).getCenter());
        if (!level.addFreshEntity(body)) throw new GameTestAssertException("creature spawn failed");
        var targetRef = new net.minecraft.world.entity.LivingEntity[1];
        var humanBodyRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                try {
                    if (targetRef[0] != null && !targetRef[0].isRemoved()) targetRef[0].discard();
                    if (humanBodyRef[0] != null && humanBodyRef[0] != fixture.player() && !humanBodyRef[0].isRemoved()) humanBodyRef[0].discard();
                    if (!body.isRemoved()) body.discard();
                    fixture.close();
                    level.getServer().getPlayerList().getOps().remove(opEntry);
                } catch (Throwable failure) {
                    if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo, net.minecraft.gametest.framework.GameTestInfo newInfo, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(oldInfo); }
        });
        var character = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())).orElseThrow();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(body, level, character);
        helper.runAtTickTime(2, () -> {
                var humanActive = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                humanBodyRef[0] = humanActive.body();
                var oldBinding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(humanActive.body());
                if (oldBinding == null) throw new GameTestAssertException("ready human missing character binding");
                String preAdoptionBodyName = null;
                if (id.endsWith("kobold")) {
                    var npc = com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData
                            .forFirstEnrollment(level.getServer().overworld())
                            .allocateNpcDurably(level.getServer().overworld(), body.getUUID(), "Kobold");
                    preAdoptionBodyName = npc.name();
                    if (!preAdoptionBodyName.equals(com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(body).getString()))
                        throw new GameTestAssertException("pre-adoption Kobold name did not resolve from NPC identity");
                }
                final String originalBodyName = preAdoptionBodyName;
                var oldName = com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(humanActive.body());
                com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(humanActive.body(),
                        java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true);
                helper.runAfterDelay(3, () -> {
                try {
                var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(fixture.player());
                if (corpse == null || !com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.requestGhost(fixture.player(), corpse.epoch()))
                    throw new GameTestAssertException("ghost entry failed: corpse=" + corpse);
                var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(fixture.player());
                if (ghost == null || !RegisteredPlayerReadyDriver.readyGhost(fixture, ghost.authorityGeneration()))
                    throw new GameTestAssertException("ghost ready failed");
                long oldObserverEpoch = ghost.epoch();
                var player = fixture.player();
                try {
                    player.getServer().getCommands().getDispatcher().execute(
                            player.getServer().getCommands().getDispatcher().parse("ms14dev possess " + body.getUUID(),
                                    player.createCommandSourceStack()));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                    throw new GameTestAssertException("development possession command failed: " + failure);
                }
                com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer offer = null;
                Object packet;
                while ((packet = fixture.pollOutbound()) != null)
                    if (packet instanceof ClientboundCustomPayloadPacket p && p.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer o) offer = o;
                if (offer == null) throw new GameTestAssertException("development command emitted no role offer; ghost=" + ghost);
                RegisteredPlayerReadyDriver.acceptRoleOffer(fixture, offer);
                if (com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(fixture.player()) != null
                        || com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.requestReturn(fixture.player(), oldObserverEpoch))
                    throw new GameTestAssertException("adopted character retained old observer session");
                var active = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow();
                if (active.body() != body) throw new GameTestAssertException("active native body mismatch");
                var targetBinding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(body);
                if (targetBinding == null || !targetBinding.profileKey().equals(
                        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding.profileKeyForBody(body.getUUID()))
                        || targetBinding.profileKey().equals(oldBinding.profileKey())
                        || !targetBinding.accountId().equals(oldBinding.accountId())
                        || !targetBinding.mindId().equals(oldBinding.mindId()))
                    throw new GameTestAssertException("native adoption did not rotate profile while retaining account/mind");
                if (!oldBinding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(humanBodyRef[0]))
                        || !oldName.equals(com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(humanBodyRef[0])))
                    throw new GameTestAssertException("adoption changed corpse binding or public name");
                var identity = com.juicyslew.moonstation14.ms14.character.CharacterPublicName.savedIdentity(body)
                        .orElseThrow(() -> new GameTestAssertException("adopted body has no saved personal identity"));
                var newName = com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(body);
                if (originalBodyName != null && !originalBodyName.equals(newName.getString()))
                    throw new GameTestAssertException("adoption changed durable Kobold identity: " + originalBodyName + " -> " + newName.getString());
                if (identity.name().equals(oldName.getString()) || !newName.getString().equals(identity.name()))
                    throw new GameTestAssertException("adopted body public name did not resolve from its new saved identity");
                if (verifySpeech) {
                    body.setCustomName(net.minecraft.network.chat.Component.literal("Kobold"));
                    String raw = "This message must not be understood";
                    var event = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.ServerChatEvent(fixture.player(), raw,
                                    net.minecraft.network.chat.Component.literal(raw)));
                    if (!event.isCanceled()) throw new GameTestAssertException("native vanilla chat was not canceled");
                    int speechPackets = 0;
                    Object sent;
                    while ((sent = fixture.pollOutbound()) != null) {
                        if (!(sent instanceof ClientboundCustomPayloadPacket speechPacket)
                                || !(speechPacket.payload() instanceof com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload speech)
                                || !speech.speakerUuid().equals(body.getUUID())) continue;
                        speechPackets++;
                        boolean replacement = false;
                        for (int i = 0; i < 9; i++) replacement |= speech.text().equals(
                                com.juicyslew.moonstation14.ms14.chat.server.SpeechAccentPolicy.replacement("kobold", i));
                        if (!replacement || speech.text().equals(raw) || !speech.actualName().equals("Kobold")
                                || !speech.soundCue().equals("Lizard") || speech.speakerEntityId() != body.getId()
                                || !speech.dimension().equals(level.dimension().location()))
                            throw new GameTestAssertException("speech payload lost Kobold identity/accent/body ownership");
                    }
                    if (speechPackets == 0) throw new GameTestAssertException("no current-body LocalSpeechPayload delivered");
                    if (LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow().body() != body)
                        throw new GameTestAssertException("speech changed current body ownership");
                    helper.succeed();
                    return;
                }
                var emote = com.juicyslew.moonstation14.ms14.effect.EmoteEffect.plan("scream", true, body);
                if (emote == null || !(emote.chat().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents line)
                        || line.getArgs().length == 0 || !(line.getArgs()[0] instanceof net.minecraft.network.chat.Component emoteName)
                        || !emoteName.equals(newName))
                    throw new GameTestAssertException("emote plan did not use adopted body's current public name");
                body.setNoAi(true);
                body.setYRot(0f);
                body.setXRot(0f);
                body.setYHeadRot(0f);
                body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                        .orElseThrow(() -> new GameTestAssertException("native melee selection missing"));
                boolean handless = id.endsWith("bat");
                if (handless && (body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != null
                        || selection.handRevision() != 0 || selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED
                        || selection.itemToken().isPresent() || !selection.activeHand().isEmpty()))
                    throw new GameTestAssertException("bat selection was not truly handless/unarmed revision 0");
                var target = EntityType.VILLAGER.create(level);
                if (target == null) throw new GameTestAssertException("target spawn failed");
                target.setNoAi(true);
                var targetCharacter = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())).orElseThrow();
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(target, level, targetCharacter);
                target.setPos(body.getX(), body.getY(), body.getZ() + .8);
                if (!level.addFreshEntity(target)) throw new GameTestAssertException("target load failed");
                targetRef[0] = target;
                target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var combat = com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(
                        fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                if (combat.status() != com.juicyslew.moonstation14.ms14.combat.CombatModeRequestResult.Status.ACCEPTED)
                    throw new GameTestAssertException("combat mode activation rejected: " + combat.status());
                String damageType = selection.weapon().damage().keySet().stream().findFirst()
                        .orElseThrow(() -> new GameTestAssertException("resolved weapon has no damage types"));
                float before = typed(target, damageType);
                float healthBefore = target.getHealth();
                var stale = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch() + 1, 1,
                                selection.kind(), selection.handRevision(), selection.activeHand(),
                                selection.itemToken().orElse(""), target.getUUID()));
                if (stale.status() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT
                        || Math.abs(typed(target, damageType) - before) > .011f || Math.abs(target.getHealth() - healthBefore) > .011f)
                    throw new GameTestAssertException("stale epoch attack changed target damage/health: " + stale.status());
                var hit = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 2,
                                selection.kind(), selection.handRevision(), selection.activeHand(),
                                selection.itemToken().orElse(""), target.getUUID()));
                if (hit.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT
                        || !(typed(target, damageType) > before) || !(target.getHealth() < healthBefore))
                    throw new GameTestAssertException("native creature attack did not damage target: " + hit.status()
                             + " " + damageType + "=" + (typed(target, damageType) - before) + " health=" + (healthBefore - target.getHealth()));
                if (handless) {
                    float typedAfterHit = typed(target, damageType);
                    float healthAfterHit = target.getHealth();
                    if (body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != null)
                        throw new GameTestAssertException("handless attack fabricated LIVE_HANDS");
                    var cooldown = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
                    if (cooldown == null) throw new GameTestAssertException("unarmed cooldown was not committed");
                    var committed = cooldown.data();
                    var second = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),
                            new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 3,
                                    selection.kind(), selection.handRevision(), selection.activeHand(),
                                    selection.itemToken().orElse(""), target.getUUID()));
                    var cooldownAfterSecond = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
                    if (second.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED
                            || second.cooldownConsumed() || second.cooldownRemainingTicks() <= 0
                            || Math.abs(typed(target, damageType) - typedAfterHit) > .011f || Math.abs(target.getHealth() - healthAfterHit) > .011f
                            || cooldownAfterSecond == null || !cooldownAfterSecond.data().equals(committed)
                            || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != null)
                        throw new GameTestAssertException("immediate handless repeat was not cooldown-rejected without mutation: " + second.status());
                    int ownBodyArcs = 0;
                    Object outbound;
                    while ((outbound = fixture.pollOutbound()) != null) {
                        if (!(outbound instanceof ClientboundCustomPayloadPacket arcPacket)
                                || !(arcPacket.payload() instanceof com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload arc)
                                || !arc.bodyUUID().equals(body.getUUID())) continue;
                        ownBodyArcs++;
                        if (arc.bodyEntityId() != body.getId() || !arc.dimension().equals(level.dimension().location())
                                || !arc.activeHand().isEmpty() || arc.copiedWeapon() != null)
                            throw new GameTestAssertException("handless observer arc lost body-relative empty-hand presentation contract");
                    }
                    if (ownBodyArcs != 1) throw new GameTestAssertException("expected exactly one handless own-body observer arc, got " + ownBodyArcs);
                }
                helper.succeed();
                } catch (RuntimeException | Error e) { throw e; }
                });
        });
    }

    private static float typed(net.minecraft.world.entity.LivingEntity target, String key) {
        var damage = target.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault(key, 0f);
    }
}
