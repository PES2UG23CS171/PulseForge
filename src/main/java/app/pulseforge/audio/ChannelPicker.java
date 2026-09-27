package app.pulseforge.audio;

/**
 * Follows the input channel carrying the strongest signal, so a guitar on input 2 of an interface is heard
 * without configuration. A pinned channel is always used.
 */
final class ChannelPicker {
    /** Another channel must be this much louder (6 dB) before the picker moves to it. */
    private static final double SWITCH_RATIO = 2;
    private static final double DECAY = .85;
    private static final double SILENCE = 1e-4;
    private final int channels;
    private final int pinned;
    private final double[] levels;
    private int current;

    ChannelPicker(int channels, int pinned) {
        this.channels = Math.max(1, channels);
        this.pinned = pinned >= 1 && pinned <= this.channels ? pinned : 0;
        levels = new double[this.channels];
        current = this.pinned > 0 ? this.pinned - 1 : 0;
    }

    /** Updates the channel levels from an interleaved block and returns the 0-based channel to use. */
    int pick(float[] interleaved, int frames) {
        for (int c = 0; c < channels; c++) {
            double sum = 0;
            for (int i = 0; i < frames; i++) {
                double value = interleaved[i * channels + c];
                sum += value * value;
            }
            levels[c] = Math.max(Math.sqrt(sum / Math.max(1, frames)), levels[c] * DECAY);
        }
        if (pinned > 0) return pinned - 1;
        int loudest = 0;
        for (int c = 1; c < channels; c++) if (levels[c] > levels[loudest]) loudest = c;
        if (loudest != current && levels[loudest] > Math.max(levels[current] * SWITCH_RATIO, SILENCE)) current = loudest;
        return current;
    }

    /** 1-based channel in use. */
    int channel() { return current + 1; }
}
