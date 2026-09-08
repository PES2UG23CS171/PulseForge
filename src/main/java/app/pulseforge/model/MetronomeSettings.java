package app.pulseforge.model;

import java.util.ArrayList;
import java.util.List;

public record MetronomeSettings(
        double bpm, int beatsPerBar, int beatUnit, double volume, SoundType sound,
        double swing, String mixerName, String customSamplePath,
        List<Accent> accents, List<BeatPattern> patterns
) {
    public static final List<Integer> NOTE_VALUES = List.of(1, 2, 4, 8, 16, 32);

    public MetronomeSettings {
        bpm = Double.isFinite(bpm) ? Math.clamp(bpm, 20, 300) : 120;
        beatsPerBar = Math.clamp(beatsPerBar, 1, 12);
        beatUnit = NOTE_VALUES.contains(beatUnit) ? beatUnit : 4;
        volume = Double.isFinite(volume) ? Math.clamp(volume, 0, 1) : .72;
        swing = Double.isFinite(swing) ? Math.clamp(swing, .5, .75) : .5;
        sound = sound == null ? SoundType.STUDIO : sound;
        mixerName = mixerName == null ? "System Default" : mixerName;
        customSamplePath = customSamplePath == null ? "" : customSamplePath;
        var safeAccents = new ArrayList<Accent>();
        var safePatterns = new ArrayList<BeatPattern>();
        for (int i = 0; i < beatsPerBar; i++) {
            safeAccents.add(accents != null && i < accents.size() && accents.get(i) != null
                    ? accents.get(i) : i == 0 ? Accent.STRONG : Accent.NORMAL);
            safePatterns.add(patterns != null && i < patterns.size() && patterns.get(i) != null
                    ? patterns.get(i) : BeatPattern.straight());
        }
        accents = List.copyOf(safeAccents);
        patterns = List.copyOf(safePatterns);
    }

    // BPM counts the selected time-signature note, not an implicit quarter note.
    public double beatFrames(int sampleRate) { return sampleRate * 60.0 / bpm; }

    public static String noteName(int unit) {
        return switch (unit) {
            case 1 -> "Whole note";
            case 2 -> "Half note";
            case 8 -> "Eighth note";
            case 16 -> "Sixteenth note";
            case 32 -> "Thirty-second note";
            default -> "Quarter note";
        };
    }
}
