package app.pulseforge.model;

public enum Accent {
    STRONG, NORMAL, MUTED;

    public Accent next() {
        return switch (this) {
            case STRONG -> NORMAL;
            case NORMAL -> MUTED;
            case MUTED -> STRONG;
        };
    }
}
