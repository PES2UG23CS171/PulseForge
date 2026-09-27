package app.pulseforge.audio;

import app.pulseforge.model.TunerSettings;
import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

/** Captures every channel the device offers and hands the tuner the one carrying the signal. */
final class MicrophoneSource implements AudioSource {
    private static final int[] RATES = {48_000, 44_100, 96_000, 32_000, 22_050};
    private static final int[] CHANNEL_COUNTS = {8, 6, 4, 2, 1};
    /** Java Sound's name for the macOS default input device. */
    private static final String MAC_DEFAULT_MIXER = "Default Audio Device";
    private final TargetDataLine line;
    private final int sampleRate;
    private final int channels;
    private final String name;
    private final ChannelPicker picker;
    private byte[] bytes = new byte[0];
    private float[] interleaved = new float[0];

    private MicrophoneSource(TargetDataLine line, int sampleRate, int channels, String name, int pinnedChannel) {
        this.line = line;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.name = name;
        picker = new ChannelPicker(channels, pinnedChannel);
    }

    static MicrophoneSource open(String requested, int pinnedChannel) throws LineUnavailableException {
        Mixer mixer = resolve(requested);
        LineUnavailableException failure = null;
        for (int channels : mixer == null ? new int[] {1} : CHANNEL_COUNTS) {
            for (int rate : RATES) {
                var format = new AudioFormat(rate, 16, channels, true, false);
                var info = new DataLine.Info(TargetDataLine.class, format);
                try {
                    TargetDataLine line;
                    if (mixer != null) {
                        if (!mixer.isLineSupported(info)) continue;
                        line = (TargetDataLine) mixer.getLine(info);
                    } else {
                        if (!AudioSystem.isLineSupported(info)) continue;
                        line = (TargetDataLine) AudioSystem.getLine(info);
                    }
                    line.open(format, TunerEngine.HOP * 2 * channels * 4);
                    line.start();
                    return new MicrophoneSource(line, rate, channels, displayName(requested), pinnedChannel);
                } catch (LineUnavailableException | IllegalArgumentException error) {
                    failure = error instanceof LineUnavailableException unavailable ? unavailable
                            : new LineUnavailableException(error.getMessage());
                }
            }
        }
        throw failure != null ? failure : new LineUnavailableException("No microphone input is available.");
    }

    /** Highest channel count the device captures; 1 when unknown. */
    static int channelCount(String requested) {
        try {
            Mixer mixer = resolve(requested);
            if (mixer == null) return 1;
            for (int channels : CHANNEL_COUNTS) {
                for (int rate : new int[] {48_000, 44_100}) {
                    if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class,
                            new AudioFormat(rate, 16, channels, true, false)))) return channels;
                }
            }
        } catch (Exception ignored) {
            // Disconnected devices simply offer one channel in the settings.
        }
        return 1;
    }

    static List<String> names() {
        var names = new ArrayList<String>();
        names.add(TunerSettings.DEFAULT_INPUT);
        for (var mixerInfo : AudioSystem.getMixerInfo()) {
            try { if (supportsInput(mixerInfo)) names.add(mixerInfo.getName()); } catch (Exception ignored) { }
        }
        return names;
    }

    /**
     * The named mixer; for the default choice, the system default device by name (so all of its channels are
     * captured), else the macOS default-device mixer, else null to let Java Sound pick a mono line. Asking Java
     * Sound for a stereo line without naming a mixer could open some other device.
     */
    private static Mixer resolve(String requested) throws LineUnavailableException {
        if (!TunerSettings.DEFAULT_INPUT.equals(requested)) {
            Mixer named = mixerNamed(requested);
            if (named == null) throw new LineUnavailableException("Selected input is disconnected. Choose an input in Settings.");
            return named;
        }
        String systemDefault = MacDefaultInput.name();
        Mixer mixer = systemDefault.isBlank() ? null : mixerNamed(systemDefault);
        return mixer != null ? mixer : mixerNamed(MAC_DEFAULT_MIXER);
    }

    private static Mixer mixerNamed(String name) {
        for (var mixerInfo : AudioSystem.getMixerInfo()) {
            if (mixerInfo.getName().equals(name) && supportsInput(mixerInfo)) return AudioSystem.getMixer(mixerInfo);
        }
        return null;
    }

    /** Label for the status line: names the resolved device behind the default choice. */
    private static String displayName(String requested) {
        if (!TunerSettings.DEFAULT_INPUT.equals(requested)) return requested;
        String systemDefault = MacDefaultInput.name();
        return systemDefault.isBlank() ? "System default input" : "System default · " + systemDefault;
    }

    private static boolean supportsInput(Mixer.Info mixerInfo) {
        var mixer = AudioSystem.getMixer(mixerInfo);
        for (int channels : CHANNEL_COUNTS) {
            for (int rate : new int[] {48_000, 44_100}) {
                if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class,
                        new AudioFormat(rate, 16, channels, true, false)))) return true;
            }
        }
        return false;
    }

    @Override public int sampleRate() { return sampleRate; }
    @Override public String name() { return name; }
    @Override public int channel() { return picker.channel(); }
    @Override public int channelCount() { return channels; }

    @Override public void read(float[] buffer) throws LineUnavailableException {
        int frames = buffer.length;
        int needed = frames * channels * 2;
        if (bytes.length < needed) {
            bytes = new byte[needed];
            interleaved = new float[frames * channels];
        }
        int total = 0;
        while (total < needed) {
            int count = line.read(bytes, total, needed - total);
            if (count <= 0) throw new LineUnavailableException("The input stopped delivering audio.");
            total += count;
        }
        for (int i = 0; i < frames * channels; i++)
            interleaved[i] = (short) ((bytes[i * 2] & 0xff) | (bytes[i * 2 + 1] << 8)) / 32768f;
        int selected = picker.pick(interleaved, frames);
        for (int i = 0; i < frames; i++) buffer[i] = interleaved[i * channels + selected];
    }

    @Override public void close() {
        line.stop();
        line.flush();
        line.close();
    }
}
