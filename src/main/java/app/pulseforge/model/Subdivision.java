package app.pulseforge.model;

/** Division of one time-signature beat; each beat can choose a different division. */
public enum Subdivision {
    QUARTER(1), EIGHTH(2), TRIPLET(3), SIXTEENTH(4), SEXTUPLET(6), THIRTY_SECOND(8);

    private final int steps;
    Subdivision(int steps) { this.steps = steps; }
    public int steps() { return steps; }

    public String label(int beatUnit) {
        if (this == TRIPLET) return "Triplet";
        if (this == SEXTUPLET) return "Sextuplet";
        return switch (beatUnit * steps) {
            case 1 -> "Semibreve";
            case 2 -> "Minim";
            case 4 -> "Crotchet";
            case 8 -> "Quavers";
            case 16 -> "Semiquavers";
            case 32 -> "Demisemiquavers";
            default -> steps + " even notes";
        };
    }

    public String count(int beat, int step) {
        String number = Integer.toString(beat + 1);
        return switch (this) {
            case QUARTER -> number;
            case EIGHTH -> step == 0 ? number : "&";
            case TRIPLET -> new String[]{number, "trip", "let"}[step];
            case SIXTEENTH -> new String[]{number, "e", "&", "a"}[step];
            case SEXTUPLET -> new String[]{number, "trip", "let", "&", "trip", "let"}[step];
            case THIRTY_SECOND -> step == 0 ? number : Integer.toString(step + 1);
        };
    }
}
