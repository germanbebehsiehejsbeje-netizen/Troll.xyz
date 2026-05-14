package dev.mzc.client.manager.impl;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.mzc.client.Sakura.mc;

public class SoundManager {
    private final Set<String> REGISTERED_SOUND_FILES = new HashSet<>();
    private final Map<String, SoundEvent> musicSounds = new HashMap<>();

    public SoundEvent ENABLE = registerSound("enable");
    public SoundEvent DISABLE = registerSound("disable");
    public SoundEvent JELLO_ENABLE = registerSound("activate");
    public SoundEvent JELLO_DISABLE = registerSound("deactivate");

    public SoundManager() {
        registerMusic("lzxh");
        registerMusic("wings_of_liberty");
        registerMusic("romances_terminus");
        registerMusic("tears");
        registerMusic("glass_heart");
        registerMusic("gummybear");
        registerMusic("feint_weavers");
        registerMusic("nkddw");
        registerMusic("uwu");
        registerMusic("seasons");
        registerMusic("nn");
    }

    private SoundEvent registerSound(String name) {
        registerSoundFile(name + ".ogg");
        Identifier id = Identifier.of("sakura", name);
        if (Registries.SOUND_EVENT.containsId(id)) {
            return Registries.SOUND_EVENT.get(id);
        }
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    private SoundEvent registerMusic(String soundId) {
        registerSoundFile("music/" + soundId + ".ogg");
        Identifier id = Identifier.of("sakura", "music." + soundId);
        if (Registries.SOUND_EVENT.containsId(id)) {
            SoundEvent existing = Registries.SOUND_EVENT.get(id);
            musicSounds.put(soundId, existing);
            return existing;
        }
        SoundEvent sound = Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
        musicSounds.put(soundId, sound);
        return sound;
    }

    private void registerSoundFile(String soundFile) {
        REGISTERED_SOUND_FILES.add(soundFile);
    }

    public void playSound(SoundEvent sound) {
        playSound(sound, 1.2f, 0.75f);
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (sound == null || mc.player == null) return;
        mc.executeSync(() -> mc.player.playSound(sound, volume, pitch));
    }

    public Set<String> getRegisteredSoundFiles() {
        return new HashSet<>(REGISTERED_SOUND_FILES);
    }

    public SoundEvent getMusicSound(String soundId) {
        if (soundId == null || soundId.isBlank()) return null;
        SoundEvent sound = musicSounds.get(soundId);
        if (sound != null) return sound;
        Identifier id = Identifier.of("sakura", "music." + soundId);
        if (Registries.SOUND_EVENT.containsId(id)) {
            return Registries.SOUND_EVENT.get(id);
        }
        if (soundId.equalsIgnoreCase("nn")) {
            Identifier nnId = Identifier.of("sakura", "music.nn");
            if (Registries.SOUND_EVENT.containsId(nnId)) return Registries.SOUND_EVENT.get(nnId);
        }
        return SoundEvent.of(id);
    }
}