package app.pulseforge.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.prefs.Preferences;

public final class MetronomeState {
    private final Preferences prefs = Preferences.userNodeForPackage(MetronomeState.class);
    private final AtomicReference<MetronomeSettings> settings = new AtomicReference<>(load());
    private final AtomicLong structureVersion = new AtomicLong();
    private final List<Consumer<MetronomeSettings>> listeners = new CopyOnWriteArrayList<>();

    public MetronomeSettings get() { return settings.get(); }
    public long structureVersion() { return structureVersion.get(); }
    public void addListener(Consumer<MetronomeSettings> listener) { listeners.add(listener); }

    public void update(UnaryOperator<MetronomeSettings> updater) { update(updater, false); }

    public void update(UnaryOperator<MetronomeSettings> updater, boolean structural) {
        var next = settings.updateAndGet(updater);
        if (structural) structureVersion.incrementAndGet();
        save(next);
        listeners.forEach(listener -> listener.accept(next));
    }

    public void setBpm(double bpm) {
        update(s -> copy(s, bpm, s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                s.sound(), s.swing(), s.mixerName(), s.customSamplePath(), s.accents()));
    }

    public void setSubdivision(Subdivision subdivision) {
        update(s -> copy(s, s.bpm(), subdivision, s.beatsPerBar(), s.beatUnit(), s.volume(),
                s.sound(), s.swing(), s.mixerName(), s.customSamplePath(), s.accents()), true);
    }

    public void setMeter(int beats, int unit) {
        update(s -> {
            var accents = resizedAccents(s.accents(), beats);
            return copy(s, s.bpm(), s.subdivision(), beats, unit, s.volume(), s.sound(),
                    s.swing(), s.mixerName(), s.customSamplePath(), accents);
        }, true);
    }

    public void setVolume(double volume) {
        update(s -> copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), volume,
                s.sound(), s.swing(), s.mixerName(), s.customSamplePath(), s.accents()));
    }

    public void setSound(SoundType sound) {
        update(s -> copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                sound, s.swing(), s.mixerName(), s.customSamplePath(), s.accents()));
    }

    public void setSwing(double swing) {
        update(s -> copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                s.sound(), swing, s.mixerName(), s.customSamplePath(), s.accents()));
    }

    public void setMixerName(String name) {
        update(s -> copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                s.sound(), s.swing(), name, s.customSamplePath(), s.accents()), true);
    }

    public void setCustomSamplePath(String path) {
        update(s -> copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                SoundType.CUSTOM, s.swing(), s.mixerName(), path, s.accents()), true);
    }

    public void cycleAccent(int beat) {
        update(s -> {
            var accents = new ArrayList<>(s.accents());
            accents.set(beat, accents.get(beat).next());
            return copy(s, s.bpm(), s.subdivision(), s.beatsPerBar(), s.beatUnit(), s.volume(),
                    s.sound(), s.swing(), s.mixerName(), s.customSamplePath(), accents);
        });
    }

    private MetronomeSettings load() {
        int beats = clamp(prefs.getInt("beats", 4), 1, 12);
        var accents = new ArrayList<Accent>();
        String savedAccents = prefs.get("accents", "SNNN");
        for (int i = 0; i < beats; i++) {
            char code = i < savedAccents.length() ? savedAccents.charAt(i) : (i == 0 ? 'S' : 'N');
            accents.add(code == 'M' ? Accent.MUTED : code == 'S' ? Accent.STRONG : Accent.NORMAL);
        }
        return new MetronomeSettings(
                prefs.getDouble("bpm", 120),
                enumValue(Subdivision.class, prefs.get("subdivision", "QUARTER"), Subdivision.QUARTER),
                beats,
                prefs.getInt("unit", 4),
                prefs.getDouble("volume", .72),
                enumValue(SoundType.class, prefs.get("sound", "STUDIO"), SoundType.STUDIO),
                prefs.getDouble("swing", .5),
                prefs.get("mixer", "System Default"),
                prefs.get("customSample", ""),
                accents);
    }

    private void save(MetronomeSettings s) {
        prefs.putDouble("bpm", s.bpm());
        prefs.put("subdivision", s.subdivision().name());
        prefs.putInt("beats", s.beatsPerBar());
        prefs.putInt("unit", s.beatUnit());
        prefs.putDouble("volume", s.volume());
        prefs.put("sound", s.sound().name());
        prefs.putDouble("swing", s.swing());
        prefs.put("mixer", s.mixerName());
        prefs.put("customSample", s.customSamplePath());
        var codes = new StringBuilder();
        s.accents().forEach(a -> codes.append(a == Accent.STRONG ? 'S' : a == Accent.MUTED ? 'M' : 'N'));
        prefs.put("accents", codes.toString());
    }

    private static List<Accent> resizedAccents(List<Accent> current, int size) {
        var result = new ArrayList<Accent>();
        for (int i = 0; i < size; i++) result.add(i < current.size() ? current.get(i) : Accent.NORMAL);
        if (!result.isEmpty() && result.stream().noneMatch(a -> a == Accent.STRONG)) result.set(0, Accent.STRONG);
        return result;
    }

    private static MetronomeSettings copy(MetronomeSettings s, double bpm, Subdivision subdivision,
                                            int beats, int unit, double volume, SoundType sound,
                                            double swing, String mixer, String customSample,
                                            List<Accent> accents) {
        return new MetronomeSettings(bpm, subdivision, beats, unit, volume, sound, swing, mixer,
                customSample, accents);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); } catch (Exception ignored) { return fallback; }
    }
}
