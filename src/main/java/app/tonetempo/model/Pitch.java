package app.tonetempo.model;

/** Equal-temperament conversions around an adjustable A4 reference. */
public final class Pitch {
    public static final double DEFAULT_A4 = 440;
    private static final String[] LETTERS = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};

    private Pitch() {}

    public static double frequency(int midi, double a4) { return a4 * Math.pow(2, (midi - 69) / 12.0); }

    /** Fractional MIDI number of a frequency; 69.5 is a quarter tone above A4. */
    public static double midi(double frequency, double a4) { return 69 + 12 * log2(frequency / a4); }

    /** Signed distance in cents from the target; positive means sharp. */
    public static double cents(double frequency, double target) { return 1200 * log2(frequency / target); }

    public static String letter(int midi) { return LETTERS[Math.floorMod(midi, 12)]; }
    public static int octave(int midi) { return Math.floorDiv(midi, 12) - 1; }
    public static String name(int midi) { return letter(midi) + octave(midi); }

    private static double log2(double value) { return Math.log(value) / Math.log(2); }
}
