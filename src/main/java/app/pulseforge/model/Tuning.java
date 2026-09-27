package app.pulseforge.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Open-string targets from the lowest to the highest string, as MIDI note numbers. */
public record Tuning(String name, List<Integer> notes) {
    public static final int LOWEST = 33;   // A1: seven-string drop A
    public static final int HIGHEST = 69;  // A4
    public static final String CUSTOM = "Custom";
    public static final List<Tuning> PRESETS = List.of(
            new Tuning("Standard", List.of(40, 45, 50, 55, 59, 64)),
            new Tuning("Drop D", List.of(38, 45, 50, 55, 59, 64)),
            new Tuning("Double drop D", List.of(38, 45, 50, 55, 59, 62)),
            new Tuning("Half step down", List.of(39, 44, 49, 54, 58, 63)),
            new Tuning("Whole step down", List.of(38, 43, 48, 53, 57, 62)),
            new Tuning("Drop C", List.of(36, 43, 48, 53, 57, 62)),
            new Tuning("DADGAD", List.of(38, 45, 50, 55, 57, 62)),
            new Tuning("Open D", List.of(38, 45, 50, 54, 57, 62)),
            new Tuning("Open E", List.of(40, 47, 52, 56, 59, 64)),
            new Tuning("Open G", List.of(38, 43, 50, 55, 59, 62)),
            new Tuning("Open A", List.of(40, 45, 52, 57, 61, 64)),
            new Tuning("7-string standard", List.of(35, 40, 45, 50, 55, 59, 64)),
            new Tuning("7-string drop A", List.of(33, 40, 45, 50, 55, 59, 64)));
    public static final Tuning STANDARD = PRESETS.getFirst();

    public Tuning {
        var safe = new ArrayList<Integer>();
        if (notes != null) for (var note : notes) if (note != null) safe.add(Math.clamp(note, LOWEST, HIGHEST));
        if (safe.isEmpty()) safe.addAll(List.of(40, 45, 50, 55, 59, 64));
        while (safe.size() > 8) safe.removeLast();
        notes = List.copyOf(safe);
        name = name == null || name.isBlank() ? CUSTOM : name;
    }

    /** The preset with these notes, or a custom tuning. */
    public static Tuning of(List<Integer> notes) {
        var candidate = new Tuning(CUSTOM, notes);
        for (var preset : PRESETS) if (preset.notes().equals(candidate.notes())) return preset;
        return candidate;
    }

    public static Tuning preset(String name) {
        for (var preset : PRESETS) if (preset.name().equals(name)) return preset;
        return STANDARD;
    }

    public boolean isPreset() { return !CUSTOM.equals(name); }
    public int strings() { return notes.size(); }
    public int note(int string) { return notes.get(string); }
    public String letters() { return notes.stream().map(Pitch::letter).collect(Collectors.joining(" ")); }
    public String label() { return name + "  ·  " + letters(); }
    public Tuning withNote(int string, int midi) {
        var next = new ArrayList<>(notes);
        next.set(string, midi);
        return of(next);
    }

    /** "6th string" is the lowest string of a six-string guitar. */
    public String stringLabel(int string) { return ordinal(notes.size() - string) + " string"; }

    public static String ordinal(int number) {
        int tens = number % 100;
        if (tens >= 11 && tens <= 13) return number + "th";
        return number + switch (number % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    public double frequency(int string, double a4) { return Pitch.frequency(notes.get(string), a4); }

    /** Index of the string whose target is closest in pitch, measured in cents. */
    public int nearest(double frequency, double a4) {
        int best = 0;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            double cents = Math.abs(Pitch.cents(frequency, frequency(i, a4)));
            if (cents < distance) { distance = cents; best = i; }
        }
        return best;
    }

    public Match match(double frequency, double a4) { return match(nearest(frequency, a4), frequency, a4); }

    public Match match(int string, double frequency, double a4) {
        double target = frequency(string, a4);
        return new Match(string, notes.get(string), target, Pitch.cents(frequency, target));
    }

    public record Match(int string, int note, double target, double cents) {
        public boolean inTune(double tolerance) { return Math.abs(cents) <= tolerance; }
    }
}
