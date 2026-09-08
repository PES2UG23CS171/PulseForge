package app.pulseforge.model;

public enum Subdivision {
    QUARTER("Quarter", "♩", 1),
    EIGHTH("Eighths", "♪", 2),
    TRIPLET("Triplets", "3", 3),
    SIXTEENTH("Sixteenths", "♬", 4),
    SEXTUPLET("Sextuplets", "6", 6);

    private final String label;
    private final String symbol;
    private final int stepsPerQuarter;

    Subdivision(String label, String symbol, int stepsPerQuarter) {
        this.label = label;
        this.symbol = symbol;
        this.stepsPerQuarter = stepsPerQuarter;
    }

    public String label() { return label; }
    public String symbol() { return symbol; }
    public int stepsPerQuarter() { return stepsPerQuarter; }
    @Override public String toString() { return label; }
}
