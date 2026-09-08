package app.pulseforge.audio;

public record BeatPulse(int beatIndex, int subdivisionIndex, boolean downbeat, long nanoTime) {}
