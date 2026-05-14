package dev.mzc.client.module.impl.misc;

import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public class MusicPlayer extends Module {
    public enum PlaybackMode {
        Loop(),
        Once(),
        Sequential();
        PlaybackMode() {
        }

    }

    public enum SongSelection {
        LANGZIXIANHUA("DJ名龙", "lzxh"),
        NKDDW("黄勇&任书怀", "nkddw"),
        SEASONS("", "seasons"),
        WINGS_OF_LIBERTY("Himmel", "wings_of_liberty"),
        ROMANCES_TERMINUS("void", "romances_terminus"),
        TEARS_IN_THE_VOID("ZerøFetish", "tears"),
        GLASS_HEART("初音ミク", "glass_heart"),
        GUMMY_BEAR("Syrex", "gummybear"),
        WEAVERS("Feint", "feint_weavers"),
        UWU("", "uwu");

        private final String displayName;
        private final String artist;
        private final String soundId;

        SongSelection(String displayName, String soundId) {
            this.displayName = displayName;
            this.artist = "";
            this.soundId = soundId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getArtist() {
            return artist;
        }

        public String getSoundId() {
            return soundId;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final EnumValue<SongSelection> song = new EnumValue<>("Song", SongSelection.LANGZIXIANHUA, SongSelection.class);
    private final EnumValue<PlaybackMode> mode = new EnumValue<>("Mode", PlaybackMode.Loop, PlaybackMode.class);
    private final NumberValue<Double> volume = new NumberValue<>("Volume", 1.0, 0.0, 2.0, 0.05);
    private final NumberValue<Double> pitch = new NumberValue<>("Pitch", 1.0, 0.5, 2.0, 0.05);
    private SoundInstance currentInstance;
    private SongSelection currentSong;

    public String getNowPlayingTitle() {
        if (currentSong == null) return "";
        return currentSong.getDisplayName();
    }

    public float getVolume01() {
        float v = volume.get().floatValue();
        return Math.max(0f, Math.min(1f, v / 2f));
    }

    public boolean isPlayingNow() {
        if (!isEnabled()) return false;
        if (currentSong == null) return false;
        if (currentInstance == null) return false;
        return isInstancePlaying(currentInstance);
    }

    public MusicPlayer() {
        super("MusicPlayer", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onEnable() {
        if (nullCheck()) return;
        currentSong = song.get();
        ChatUtil.addChatMessage("§aNow Playing: §f" + currentSong.getDisplayName() + " §7- " + currentSong.getArtist());
        playMusic(currentSong);
    }

    @Override
    protected void onDisable() {
        stopCurrent();
        currentSong = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isEnabled()) return;
        if (currentInstance == null) return;
        if (isInstancePlaying(currentInstance)) return;

        currentInstance = null;

        if (currentSong == null) return;

        if (mode.is(PlaybackMode.Once)) {
            setState(false);
            return;
        }

        if (mode.is(PlaybackMode.Loop)) {
            playMusic(currentSong);
            return;
        }

        SongSelection[] songs = SongSelection.values();
        int nextIndex = (currentSong.ordinal() + 1) % songs.length;
        currentSong = songs[nextIndex];
        song.set(currentSong);
        playMusic(currentSong);
    }

    private void playMusic(SongSelection selection) {
        if (nullCheck()) return;
        stopCurrent();
        String soundId = selection.getSoundId();
        Identifier eventId = Identifier.of("sakura", "music." + soundId);
        Identifier fileId = Identifier.of("sakura", "sounds/music/" + soundId + ".ogg");

        boolean eventRegistered = Registries.SOUND_EVENT.containsId(eventId);
        Optional<Resource> fileRes;
        try {
            fileRes = mc.getResourceManager().getResource(fileId);
        } catch (Throwable t) {
            fileRes = Optional.empty();
        }

        if (!eventRegistered || fileRes.isEmpty()) {
            ChatUtil.addChatMessage("§cMusicPlayer加载失败: §7event=" + eventId + " registered=" + eventRegistered + " file=" + fileId + " exists=" + fileRes.isPresent());
            return;
        }

        try (InputStream in = fileRes.get().getInputStream()) {
            byte[] head = in.readNBytes(65536);
            if (head.length < 32) {
                ChatUtil.addChatMessage("§cMusicPlayer音频过短/为空: §7" + fileId);
                return;
            }

            String probe = new String(head, StandardCharsets.ISO_8859_1);
            if (probe.contains("OpusHead")) {
                ChatUtil.addChatMessage("§cMusicPlayer: 检测到Ogg Opus编码，Minecraft不支持，请转为Ogg Vorbis。");
                return;
            }
            if (probe.contains("ID3") || probe.contains("ftyp")) {
                ChatUtil.addChatMessage("§cMusicPlayer: 音频格式不正确（可能是mp3/mp4改后缀），请使用Ogg Vorbis。");
                return;
            }
            if (!probe.contains("vorbis")) {
                ChatUtil.addChatMessage("§eMusicPlayer: 未检测到Vorbis标识，若仍无声请确认编码为Ogg Vorbis。");
            }
        } catch (Throwable t) {
            ChatUtil.addChatMessage("§cMusicPlayer无法读取音频: §7" + fileId);
            return;
        }

        SoundEvent sound = Managers.SOUND.getMusicSound(soundId);
        if (sound == null) {
            ChatUtil.addChatMessage("§cMusicPlayer获取SoundEvent失败: §7" + eventId);
            return;
        }

        boolean mappingFound = hasSoundMapping(eventId);
        if (!mappingFound) {
            float masterVol = mc.options.getSoundVolume(net.minecraft.sound.SoundCategory.MASTER);
            ChatUtil.addChatMessage("§cMusicPlayer: SoundEvent存在但无映射: §7" + eventId + " §c(masterVol=" + masterVol + ")");
        }

        currentInstance = PositionedSoundInstance.master(
                sound,
                volume.get().floatValue(),
                pitch.get().floatValue()
        );
        mc.getSoundManager().play(currentInstance);
    }

    private boolean hasSoundMapping(Identifier eventId) {
        try {
            Object sm = mc.getSoundManager();
            for (Field f : sm.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(sm);
                if (!(v instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object k = m.keySet().stream().findFirst().orElse(null);
                if (!(k instanceof Identifier)) continue;
                if (m.containsKey(eventId)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void stopCurrent() {
        if (currentInstance == null) return;
        try {
            Object sm = mc.getSoundManager();
            Method stop = sm.getClass().getMethod("stop", SoundInstance.class);
            stop.invoke(sm, currentInstance);
        } catch (Throwable ignored) {
        }
        currentInstance = null;
    }

    private boolean isInstancePlaying(SoundInstance instance) {
        try {
            Object sm = mc.getSoundManager();
            Method isPlaying = sm.getClass().getMethod("isPlaying", SoundInstance.class);
            Object r = isPlaying.invoke(sm, instance);
            if (r instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }

        try {
            Object sm = mc.getSoundManager();
            for (Field f : sm.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(sm);
                if (!(v instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object k = m.keySet().stream().findFirst().orElse(null);
                if (!(k instanceof SoundInstance)) continue;
                if (m.containsKey(instance)) return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
