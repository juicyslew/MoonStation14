package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.ParrotListenerComponent;
import com.juicyslew.moonstation14.ms14.character.components.ParrotMemoryComponent;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Typed-prototype parrot listener/memory consumer; phrase state is saved on its owning entity. */
public final class ParrotMemorySystem {
    private static final String MEMORY = "MoonStation14ParrotSpeechMemory";
    private static final String NEXT_LEARN = "MoonStation14ParrotNextLearn";
    private static final String NEXT_REPLAY = "MoonStation14ParrotNextReplay";
    private ParrotMemorySystem() { }

    public static void listenToApprovedSpeech(ServerLevel level, Mob source, String message,
                                              SpeechModePolicy.Mode mode) {
        if (mode == SpeechModePolicy.Mode.RADIO_ATTEMPT) return;
        var sourcePos = source.position();
        var bounds = new AABB(sourcePos, sourcePos).inflate(10);
        for (Mob listener : level.getEntitiesOfClass(Mob.class, bounds, mob -> mob != source && !mob.isRemoved())) {
            var data = CharacterIdentitySystem.resolveForActor(listener).orElse(null);
            if (data == null) continue;
            var listenerPolicy = data.component(ParrotListenerComponent.class).orElse(null);
            var memoryPolicy = data.component(ParrotMemoryComponent.class).orElse(null);
            if (listenerPolicy == null || memoryPolicy == null || !eligible(listener)) continue;
            double distanceSquared = listener.position().distanceToSqr(sourcePos);
            if (distanceSquared > listenerPolicy.hearingRange() * listenerPolicy.hearingRange()) continue;
            SpeechRecipientPolicy.Delivery delivery = SpeechRecipientPolicy.classify(mode, false,
                    listener.level() == source.level(), sourcePos.x, sourcePos.y, sourcePos.z,
                    listener.getX(), listener.getY(), listener.getZ());
            if (delivery != SpeechRecipientPolicy.Delivery.CLEAR
                    || !LocalSpeechOcclusion.audible(level, source.getEyePosition(), listener.getEyePosition())) continue;
            remember(listener, memoryPolicy, message, distanceSquared, listenerPolicy.hearingRange(), level.getGameTime());
        }
    }

    public static void tickReplay(Mob mob, ServerLevel level) {
        var data = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        if (data == null) return;
        var policy = data.component(ParrotMemoryComponent.class).orElse(null);
        var listener = data.component(ParrotListenerComponent.class).orElse(null);
        if (policy == null || listener == null) return;

        long now = level.getGameTime();
        var tag = mob.getPersistentData();
        if (!eligible(mob)) {
            if (!tag.contains(NEXT_REPLAY) || now >= tag.getLong(NEXT_REPLAY))
                tag.putLong(NEXT_REPLAY, nextReplayAt(mob, policy, now));
            return;
        }
        if (!tag.contains(NEXT_REPLAY)) {
            tag.putLong(NEXT_REPLAY, nextReplayAt(mob, policy, now));
            return;
        }
        if (now < tag.getLong(NEXT_REPLAY)) return;
        tag.putLong(NEXT_REPLAY, nextReplayAt(mob, policy, now));
        List<String> phrases = phrases(mob, policy);
        if (!ParrotMemoryPolicy.shouldReplay(true, mob.isAlive(), true, false, phrases.size(),
                now, now)) return;
        String phrase = phrases.get(mob.getRandom().nextInt(phrases.size()));
        LocalSpeechServerHooks.dispatchNpcSpeech(level, mob, phrase);
    }

    private static boolean eligible(Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && !mob.isNoAi() && !mob.isPassenger()
                && !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(mob)
                && !(mob instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob controlled
                && controlled.moonstation14$isMovementOwned());
    }

    private static void remember(Mob mob, ParrotMemoryComponent policy, String message,
                                 double distanceSquared, double hearingRange, long now) {
        var tag = mob.getPersistentData();
        long nextLearn = tag.getLong(NEXT_LEARN);
        String trimmed = message == null ? "" : message.trim();
        if (!ParrotMemoryPolicy.canLearn(true, true, mob.isAlive(), eligible(mob), mob.isPassenger(), false,
                SpeechRecipientPolicy.Delivery.CLEAR, distanceSquared, hearingRange, trimmed,
                policy.minEntryLength(), policy.maxEntryLength(), now, nextLearn)) return;
        tag.putLong(NEXT_LEARN, Math.addExact(now, policy.learnCooldownTicks()));
        if (mob.getRandom().nextDouble() >= policy.learnChance()) return;

        ListTag memories = readMemoryTag(tag.getList(MEMORY, net.minecraft.nbt.Tag.TAG_STRING), policy.maxEntries());
        int replace = ParrotMemoryPolicy.replacementIndex(memories.size(), policy.maxEntries(),
                memories.isEmpty() ? 0 : mob.getRandom().nextInt(memories.size()));
        StringTag learned = StringTag.valueOf(trimmed);
        if (replace < 0) memories.add(learned);
        else memories.set(replace, learned);
        tag.put(MEMORY, memories);
    }

    private static List<String> phrases(Mob mob, ParrotMemoryComponent policy) {
        ListTag memories = mob.getPersistentData().getList(MEMORY, net.minecraft.nbt.Tag.TAG_STRING);
        return readMemoryTag(memories, policy.maxEntries()).stream().filter(StringTag.class::isInstance)
                .map(tag -> ((StringTag) tag).getAsString()).toList();
    }

    private static ListTag readMemoryTag(ListTag source, int capacity) {
        ListTag result = new ListTag();
        for (int i = Math.max(0, source.size() - capacity); i < source.size(); i++) {
            if (source.get(i) instanceof StringTag text && !text.getAsString().isBlank()) result.add(text.copy());
        }
        return result;
    }

    private static long nextReplayAt(Mob mob, ParrotMemoryComponent policy, long now) {
        long span = policy.maximumReplayIntervalTicks() - policy.minimumReplayIntervalTicks();
        int offset = span == 0 ? 0 : mob.getRandom().nextInt((int) span);
        return ParrotMemoryPolicy.nextInterval(now, policy.minimumReplayIntervalTicks(),
                policy.maximumReplayIntervalTicks(), offset);
    }

    static int memorySizeForTest(Mob mob) {
        return mob.getPersistentData().getList(MEMORY, net.minecraft.nbt.Tag.TAG_STRING).size();
    }
}
