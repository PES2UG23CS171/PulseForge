package app.pulseforge.model;

public enum SoundType {
    STUDIO("Studio click"), WOOD("Wood block"), DIGITAL("Digital"), SOFT("Soft tick"),
    CUSTOM("GarageBand sample");

    private final String label;
    SoundType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
