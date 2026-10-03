package app.tonetempo.audio;

/** Mono samples in [-1, 1] from a microphone, one channel of an audio interface, or a synthetic generator. */
public interface AudioSource extends AutoCloseable {
    int sampleRate();
    String name();

    /** 1-based input channel currently delivered; 1 for mono sources. */
    default int channel() { return 1; }
    default int channelCount() { return 1; }

    /** Blocks until the buffer is full. */
    void read(float[] buffer) throws Exception;

    @Override void close();

    @FunctionalInterface
    interface Opener {
        /** @param channel 1-based input channel to pin, or 0 to follow whichever channel carries signal */
        AudioSource open(String deviceName, int channel) throws Exception;
    }
}
