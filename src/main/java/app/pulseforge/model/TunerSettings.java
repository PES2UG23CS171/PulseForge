package app.pulseforge.model;

/** @param inputChannel 1-based input channel to listen to, or 0 to follow whichever channel carries signal */
public record TunerSettings(String inputName, int inputChannel, Tuning tuning, double referencePitch) {
    public static final String DEFAULT_INPUT = "System Default";
    public static final int AUTO_CHANNEL = 0;
    public static final int MAX_CHANNELS = 8;

    public TunerSettings {
        inputName = inputName == null || inputName.isBlank() ? DEFAULT_INPUT : inputName;
        inputChannel = Math.clamp(inputChannel, AUTO_CHANNEL, MAX_CHANNELS);
        tuning = tuning == null ? Tuning.STANDARD : Tuning.of(tuning.notes());
        referencePitch = Double.isFinite(referencePitch)
                ? Math.round(Math.clamp(referencePitch, 400, 480) * 10) / 10.0 : Pitch.DEFAULT_A4;
    }
}
