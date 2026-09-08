package app.pulseforge.model;

import java.util.List;

public record MetronomeSettings(
        double bpm,
        Subdivision subdivision,
        int beatsPerBar,
        int beatUnit,
        double volume,
        SoundType sound,
        double swing,
        String mixerName,
        String customSamplePath,
        List<Accent> accents
) {
    public MetronomeSettings {
        bpm = Math.max(20.0, Math.min(300.0, bpm));
        beatsPerBar = Math.max(1, Math.min(12, beatsPerBar));
        beatUnit = beatUnit == 8 ? 8 : 4;
        volume = Math.max(0.0, Math.min(1.0, volume));
        swing = Math.max(0.5, Math.min(0.75, swing));
        subdivision = subdivision == null ? Subdivision.QUARTER : subdivision;
        sound = sound == null ? SoundType.STUDIO : sound;
        mixerName = mixerName == null ? "System Default" : mixerName;
        customSamplePath = customSamplePath == null ? "" : customSamplePath;
        accents = List.copyOf(accents);
    }

    public double quarterNoteBpm() {
        return bpm;
    }
}
