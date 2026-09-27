package app.pulseforge.model;

public enum SoundType {
    STUDIO("Studio click"), WOOD("Wood block"), DIGITAL("Digital"), SOFT("Soft tick"),
    DRUM_KIT("Drum kit"), KICK("Kick drum"), SNARE("Snare"), HI_HAT("Hi-hat"), RIMSHOT("Rimshot"),
    COWBELL("Cowbell"), CLAVE("Clave");

    private final String label;
    SoundType(String label) { this.label = label; }
    public boolean isDrum() { return ordinal() >= DRUM_KIT.ordinal() && ordinal() <= CLAVE.ordinal(); }
    @Override public String toString() { return label; }
}
