package app.pulseforge.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class MetronomeState {
    private final Preferences prefs;
    private final AtomicReference<MetronomeSettings> settings;
    private final List<Consumer<MetronomeSettings>> listeners = new CopyOnWriteArrayList<>();

    public MetronomeState() { this(Preferences.userNodeForPackage(MetronomeState.class)); }

    public MetronomeState(Preferences prefs) {
        this.prefs = prefs;
        settings = new AtomicReference<>(load());
    }

    public MetronomeSettings get() { return settings.get(); }
    public void addListener(Consumer<MetronomeSettings> listener) { listeners.add(listener); }

    private void publish(MetronomeSettings next) {
        settings.set(next);
        save(next);
        listeners.forEach(listener -> listener.accept(next));
    }

    public void setBpm(double bpm) {
        var s = get();
        publish(new MetronomeSettings(bpm, s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setMeter(int beats, int unit) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), beats, unit, s.volume(), s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setPattern(int beat, Subdivision division) {
        var s = get();
        if (beat < 0 || beat >= s.beatsPerBar()) return;
        var patterns = new ArrayList<>(s.patterns());
        patterns.set(beat, BeatPattern.all(division));
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), patterns));
    }

    public void toggleHit(int beat, int hit) {
        var s = get();
        if (beat < 0 || beat >= s.beatsPerBar()) return;
        var patterns = new ArrayList<>(s.patterns());
        patterns.set(beat, patterns.get(beat).toggle(hit));
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), patterns));
    }

    public void setAccent(int beat, Accent accent) {
        var s = get();
        if (beat < 0 || beat >= s.beatsPerBar()) return;
        var accents = new ArrayList<>(s.accents());
        accents.set(beat, accent);
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), accents, s.patterns()));
    }

    public void setVolume(double volume) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), volume, s.sound(),
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setSound(SoundType sound) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), sound,
                s.swing(), s.mixerName(), s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setSwing(double swing) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                swing, s.mixerName(), s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setMixerName(String name) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), s.sound(),
                s.swing(), name, s.customSamplePath(), s.accents(), s.patterns()));
    }

    public void setCustomSamplePath(String path) {
        var s = get();
        publish(new MetronomeSettings(s.bpm(), s.beatsPerBar(), s.beatUnit(), s.volume(), SoundType.CUSTOM,
                s.swing(), s.mixerName(), path, s.accents(), s.patterns()));
    }

    private MetronomeSettings load() {
        int beats = Math.clamp(prefs.getInt("beats", 4), 1, 12);
        var accents = new ArrayList<Accent>();
        var patterns = new ArrayList<BeatPattern>();
        String savedAccents = prefs.get("accents", "SNNN");
        var legacy = enumValue(Subdivision.class, prefs.get("subdivision", "QUARTER"), Subdivision.QUARTER);
        for (int i = 0; i < beats; i++) {
            char code = i < savedAccents.length() ? savedAccents.charAt(i) : (i == 0 ? 'S' : 'N');
            accents.add(code == 'M' ? Accent.MUTED : code == 'S' ? Accent.STRONG : Accent.NORMAL);
            var division = enumValue(Subdivision.class, prefs.get("beat." + i + ".division", legacy.name()), legacy);
            String mask = prefs.get("beat." + i + ".hits", "1".repeat(division.steps()));
            var hits = new ArrayList<Boolean>();
            for (int j = 0; j < division.steps(); j++) hits.add(j >= mask.length() || mask.charAt(j) != '0');
            patterns.add(new BeatPattern(division, hits));
        }
        return new MetronomeSettings(prefs.getDouble("bpm", 120), beats, prefs.getInt("unit", 4),
                prefs.getDouble("volume", .72),
                enumValue(SoundType.class, prefs.get("sound", "STUDIO"), SoundType.STUDIO),
                prefs.getDouble("swing", .5), prefs.get("mixer", "System Default"),
                prefs.get("customSample", ""), accents, patterns);
    }

    private void save(MetronomeSettings s) {
        prefs.putDouble("bpm", s.bpm());
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
        for (int i = 0; i < s.beatsPerBar(); i++) {
            var pattern = s.patterns().get(i);
            prefs.put("beat." + i + ".division", pattern.division().name());
            var mask = new StringBuilder();
            pattern.hits().forEach(hit -> mask.append(hit ? '1' : '0'));
            prefs.put("beat." + i + ".hits", mask.toString());
        }
        // Do not resurrect removed beats if the meter is expanded after a restart.
        for (int i = s.beatsPerBar(); i < 12; i++) {
            prefs.remove("beat." + i + ".division");
            prefs.remove("beat." + i + ".hits");
        }
        prefs.remove("subdivision");
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); } catch (Exception ignored) { return fallback; }
    }
}
