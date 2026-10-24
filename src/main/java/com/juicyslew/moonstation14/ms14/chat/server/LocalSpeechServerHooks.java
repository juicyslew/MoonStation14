package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechNetworking;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.CommittedSpectatorGuard;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;

import java.util.Optional;
import java.util.UUID;

/** Routes only exact committed CHARACTER speech, never the carrier's signed/global chat. */
public final class LocalSpeechServerHooks {
    private static final LocalSpeechPolicy.AttemptLimiter<ServerPlayer> ATTEMPTS = new LocalSpeechPolicy.AttemptLimiter<>();
    private LocalSpeechServerHooks() { }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        // Earliest bus priority; other mods at the same priority may still run first.
        bus.addListener(EventPriority.HIGHEST, false, LocalSpeechServerHooks::onChat);
        bus.addListener(LocalSpeechServerHooks::onLogout);
        bus.addListener(LocalSpeechServerHooks::onServerStopped);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ATTEMPTS.remove(player);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ATTEMPTS.clear();
    }

    private static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        // A committed ghost or debug session must also be cancelled, even if its body is unavailable.
        CommittedSpectatorGuard.Ownership character = LifecycleCharacterSessionControl.committedControlSnapshot(player);
        CommittedSpectatorGuard.Ownership ghost = LifecycleGhostSessionControl.committedControlSnapshot(player);
        CommittedSpectatorGuard.Ownership debug = GhostMobHarnessControl.committedControlSnapshot(player);
        boolean committed = character.committed() || ghost.committed() || debug.committed();
        if (!committed) return; // Uncommitted players retain vanilla chat unchanged.
        event.setCanceled(true); // Cancel before any fallible body, prototype, or saved-data lookup.
        if (player instanceof FakePlayer || player.isRemoved()
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        // Canceled chat bypasses vanilla's detectRateSpam() in 1.21.1. Charge every
        // authenticated committed attempt, even if the body/identity checks below drop it.
        if (!ATTEMPTS.admit(player, System.nanoTime())) return;
        String text = event.getRawText();
        SpeechModePolicy.Result parsed = SpeechModePolicy.parse(text);
        if (parsed.mode() == SpeechModePolicy.Mode.INVALID) return;
        Speaker speaker = speaker(player, level, character, ghost, debug);
        if (speaker == null) return;
        Entity body = speaker.body();
        if (body instanceof net.minecraft.world.entity.LivingEntity living
                && !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(living)) return;
        UUID speakerUuid = body.getUUID();
        if (body.getId() <= 0 || speakerUuid == null || LocalSpeechPayload.NO_UUID.equals(speakerUuid)) return;
        Vec3 position = body.position();
        if (!LocalSpeechPolicy.validPosition(position.x, position.y, position.z)) return;
        SpeechModePolicy.Mode localMode = localMode(parsed.mode());
        LocalSpeechPayload.Mode mode = switch (localMode) {
            case SAY -> LocalSpeechPayload.Mode.SAY;
            case SHOUT -> LocalSpeechPayload.Mode.SHOUT;
            case WHISPER -> LocalSpeechPayload.Mode.WHISPER;
            default -> throw new IllegalStateException("Unroutable speech mode");
        };
        var speech = body instanceof net.minecraft.world.entity.LivingEntity living
                ? CharacterIdentitySystem.resolveForActor(living).flatMap(data -> data.component(SpeechComponent.class)).orElse(null)
                : null;
        String spokenText = speech == null ? parsed.body() : SpeechAccentPolicy.replace(speech.accent(), parsed.body());
        String soundCue = speech != null && com.juicyslew.moonstation14.sounds.ModSounds.hasSpeechSound(speech.speechSound())
                ? speech.speechSound() : "";
        if (body instanceof Mob sourceMob)
            ParrotMemorySystem.listenToApprovedSpeech(level, sourceMob, spokenText, parsed.mode());
        LocalSpeechPayload clear = new LocalSpeechPayload(body.getId(), speakerUuid, speaker.name(), speaker.rgb(),
                spokenText, position.x, position.y, position.z, body.level().dimension().location(), mode,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.DistanceTier.NONE, soundCue);
        deliver(level, body, clear, parsed.body(), localMode, player);
    }

    /** Replay through the same local packet/recipient path; never allocates or invents NPC identities. */
    static void dispatchNpcSpeech(ServerLevel level, Mob body, String phrase) {
        if (level == null || body == null || body.level() != level || !body.isAlive() || body.isRemoved()
                || level.getServer() == null || !level.getServer().isSameThread()
                || body.isNoAi() || body.isPassenger()
                || !LocalSpeechPolicy.validText(phrase)) return;
        var character = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        var speech = character == null ? null : character.component(SpeechComponent.class).orElse(null);
        if (speech == null || character.component(com.juicyslew.moonstation14.ms14.character.components.ParrotMemoryComponent.class).isEmpty()) return;
        var overworld = level.getServer().overworld();
        var identities = ChatIdentitySavedData.existing(overworld).orElse(null);
        var identity = identities == null ? null : identities.npc(body.getUUID()).orElse(null);
        if (identity == null) {
            String displayType = character.component(com.juicyslew.moonstation14.ms14.character.components.SpeciesComponent.class)
                    .map(com.juicyslew.moonstation14.ms14.character.components.SpeciesComponent::label).orElse(null);
            if (displayType == null) return;
            try {
                identity = ChatIdentitySavedData.forFirstEnrollment(overworld)
                        .allocateNpcDurably(overworld, body.getUUID(), displayType);
            } catch (RuntimeException failure) {
                MoonStation14.LOGGER.warn("Could not establish saved identity for autonomous parrot speech", failure);
                return;
            }
        }
        String spoken = SpeechAccentPolicy.replace(speech.accent(), phrase);
        String soundCue = com.juicyslew.moonstation14.sounds.ModSounds.hasSpeechSound(speech.speechSound())
                ? speech.speechSound() : "";
        var pos = body.position();
        if (!LocalSpeechPolicy.validPosition(pos.x, pos.y, pos.z)) return;
        var payload = new LocalSpeechPayload(body.getId(), body.getUUID(), identity.name(), identity.rgb(), spoken,
                pos.x, pos.y, pos.z, level.dimension().location(), LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                LocalSpeechPayload.DistanceTier.NONE, soundCue);
        deliver(level, body, payload, phrase, SpeechModePolicy.Mode.SAY, null);
    }

    private static void deliver(ServerLevel level, Entity body, LocalSpeechPayload clear, String sourceText,
                                SpeechModePolicy.Mode mode, ServerPlayer sender) {
        String masked = null;
        for (ServerPlayer recipient : level.getServer().getPlayerList().getPlayers()) {
            if (recipient.isRemoved() || recipient instanceof FakePlayer) continue;
            Entity listener = recipient.getCamera();
            if (listener == null) continue;
            SpeechRecipientPolicy.Delivery delivery = SpeechRecipientPolicy.classify(mode, recipient == sender,
                    recipient.level() == level && listener.level() == level,
                    clear.x(), clear.y(), clear.z(), listener.getX(), listener.getY(), listener.getZ());
            if (delivery == SpeechRecipientPolicy.Delivery.NONE) continue;
            if (recipient != sender && !LocalSpeechOcclusion.audible(level,
                    body.getEyePosition(), listener.getEyePosition())) continue;
            try {
                LocalSpeechPayload outgoing = clear;
                if (delivery == SpeechRecipientPolicy.Delivery.MUFFLED) {
                    if (masked == null) masked = SpeechRecipientPolicy.muffle(sourceText);
                    var bearing = SpeechRecipientPolicy.bearing(clear.x(), clear.y(), clear.z(),
                            listener.getX(), listener.getY(), listener.getZ());
                    var distanceTier = SpeechRecipientPolicy.distanceTier(clear.x(), clear.y(), clear.z(),
                            listener.getX(), listener.getY(), listener.getZ());
                    outgoing = new LocalSpeechPayload(body.getId(), clear.speakerUuid(), clear.actualName(),
                            LocalSpeechPayload.ANONYMOUS_RGB, masked, 0, 0, 0, clear.dimension(),
                            LocalSpeechPayload.Mode.W_MUFFLED, bearing.azimuthSector(), bearing.verticalBand(), distanceTier);
                }
                LocalSpeechNetworking.send(recipient, outgoing);
            } catch (RuntimeException failure) {
                MoonStation14.LOGGER.warn("Could not deliver local speech to nearby listener", failure);
            }
        }
    }

    /** Radio has no authorized remote delivery yet. Future radio delivery must leave this local
     * whisper in place exactly once, regardless of whether the radio transmission succeeds. */
    static SpeechModePolicy.Mode localMode(SpeechModePolicy.Mode attempted) {
        return attempted == SpeechModePolicy.Mode.RADIO_ATTEMPT ? SpeechModePolicy.Mode.WHISPER : attempted;
    }

    public record Speaker(Entity body, String name, int rgb) { }

    /** Resolve the same committed-body authority used by local chat for other authenticated actions. */
    public static Speaker authenticatedSpeaker(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || player.isRemoved()
                || player instanceof FakePlayer || level.getServer() == null
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return null;
        var character = LifecycleCharacterSessionControl.committedControlSnapshot(player);
        var ghost = LifecycleGhostSessionControl.committedControlSnapshot(player);
        var debug = GhostMobHarnessControl.committedControlSnapshot(player);
        return speaker(player, level, character, ghost, debug);
    }

    /** Only one committed owner may speak; identity lookups never enroll or allocate. */
    private static Speaker speaker(ServerPlayer player, ServerLevel level,
                                   CommittedSpectatorGuard.Ownership character,
                                   CommittedSpectatorGuard.Ownership ghost,
                                   CommittedSpectatorGuard.Ownership debug) {
        if (ghost.committed() || character.committed() == debug.committed()) return null;
        if (character.committed()) {
            if (!(character.owned() instanceof Mob body)
                    || body.level() != level || !body.isAlive() || body.isRemoved() || player.getCamera() != body)
                return null;
            var snapshot = LifecycleCharacterSessionControl.activeCharacterBody(player).orElse(null);
            if (snapshot == null || snapshot.body() != body
                    || !LifecycleCharacterSessionControl.roleSpeechAllowed(player, body, snapshot.epoch())) return null;
            var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(body);
            if (binding == null || !binding.accountId().equals(player.getUUID())) return null;
            var boundIdentity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
            if (boundIdentity == null || !boundIdentity.isBound()
                    || !lifecycleSpeechAllowed(boundIdentity.characterId(), CharacterIdentitySystem.resolve(body)))
                return null;
            var identity = ChatIdentitySavedData.existing(level.getServer().overworld())
                    .flatMap(data -> data.character(new ChatIdentityRegistry.CharacterKey(
                            player.getUUID(), binding.profileKey()))).orElse(null);
            return identity == null ? null : new Speaker(body, identity.name(), identity.rgb());
        }
        if (!(debug.owned() instanceof Mob body) || body.level() != level || !body.isAlive()
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || player.getCamera() != body || !body.getPersistentData().getBoolean(GroundedHarnessLease.CONFIGURED_MARKER))
            return null;
        var harness = GhostMobHarnessControl.activeHarness(player).orElse(null);
        if (harness == null || harness.kind() != MobHarnessKind.CHARACTER || harness.entity() != body
                || !harness.harnessId().value().equals(body.getUUID())
                || !CharacterIdentitySystem.resolveForHost(body).filter(data -> data.canSpeakText()).isPresent())
            return null;
        var identity = ChatIdentitySavedData.existing(level.getServer().overworld())
                .flatMap(data -> data.npc(body.getUUID())).orElse(null);
        return identity == null ? null : new Speaker(body, identity.name(), identity.rgb());
    }

    /** Lifecycle-only admission: use the bound character prototype, without global host mapping. */
    static boolean lifecycleSpeechAllowed(ResourceLocation boundId, Optional<CharacterData> current) {
        return boundId != null && current.filter(CharacterData::canSpeakText).isPresent();
    }
}
