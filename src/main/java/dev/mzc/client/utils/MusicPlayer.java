package dev.mzc.client.utils;

import dev.mzc.client.manager.Managers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {
    public static final MusicPlayer INSTANCE = new MusicPlayer();
    
    private final List<MusicTrack> tracks = new ArrayList<>();
    private int currentTrackIndex = -1;
    private SoundInstance currentSound;
    private boolean playing = false;
    
    // Track info
    private long startTime;
    private float durationSeconds = 180f; // Default estimation for UI
    
    private MusicPlayer() {
        // Updated based on SoundManager.java music registrations
        addTrack("DJ Ming Long", "lzxh");
        addTrack("Wings of Liberty", "wings_of_liberty");
        addTrack("Romances Terminus", "romances_terminus");
        addTrack("Tears", "tears");
        addTrack("Glass Heart", "glass_heart");
        addTrack("Gummy Bear", "gummybear");
        addTrack("Feint Weavers", "feint_weavers");
        addTrack("NKDDW", "nkddw");
        addTrack("UwU", "uwu");
        addTrack("Seasons", "seasons");
        addTrack("NN", "nn");
    }
    
    private void addTrack(String name, String soundId) {
        tracks.add(new MusicTrack(name, soundId));
    }
    
    public void play() {
        if (tracks.isEmpty()) return;
        if (currentTrackIndex == -1) currentTrackIndex = 0;
        playTrack(tracks.get(currentTrackIndex));
    }

    public void play(int index) {
        if (index < 0 || index >= tracks.size()) return;
        currentTrackIndex = index;
        playTrack(tracks.get(currentTrackIndex));
    }
    
    public void pause() {
        if (currentSound != null) {
            MinecraftClient.getInstance().getSoundManager().stop(currentSound);
            playing = false;
        }
    }
    
    public void next() {
        if (tracks.isEmpty()) return;
        currentTrackIndex = (currentTrackIndex + 1) % tracks.size();
        playTrack(tracks.get(currentTrackIndex));
    }
    
    public void previous() {
        if (tracks.isEmpty()) return;
        currentTrackIndex--;
        if (currentTrackIndex < 0) currentTrackIndex = tracks.size() - 1;
        playTrack(tracks.get(currentTrackIndex));
    }
    
    private void playTrack(MusicTrack track) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
        }
        
        SoundEvent sound = Managers.SOUND.getMusicSound(track.soundId);
        if (sound == null) return;

        currentSound = PositionedSoundInstance.master(sound, 1.0f, 1.0f);
        mc.getSoundManager().play(currentSound);
        playing = true;
        startTime = System.currentTimeMillis();
        
        // Custom durations for better progress bar accuracy
        if (track.soundId.equals("nn")) durationSeconds = 210f;
        else if (track.soundId.equals("lzxh")) durationSeconds = 240f;
        else durationSeconds = 180f;
    }
    
    public void update() {
        if (playing && currentSound != null) {
            if (!MinecraftClient.getInstance().getSoundManager().isPlaying(currentSound)) {
                next(); // Auto-play next song
            }
        }
    }
    
    public String getCurrentSongName() {
        if (currentTrackIndex == -1 || tracks.isEmpty()) return "No Song Playing";
        return tracks.get(currentTrackIndex).name;
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }
    
    public boolean isPlaying() {
        return playing && currentSound != null && MinecraftClient.getInstance().getSoundManager().isPlaying(currentSound);
    }
    
    public float getProgress() {
        if (!playing || currentTrackIndex == -1) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        float prog = (elapsed / 1000f) / durationSeconds;
        return Math.min(prog, 1.0f);
    }

    public List<MusicTrack> getTracks() {
        return tracks;
    }
    
    public static class MusicTrack {
        public String name;
        public String soundId;
        
        MusicTrack(String name, String soundId) {
            this.name = name;
            this.soundId = soundId;
        }
    }
}
