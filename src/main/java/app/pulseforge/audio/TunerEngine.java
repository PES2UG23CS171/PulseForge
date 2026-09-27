package app.pulseforge.audio;

import app.pulseforge.model.TunerState;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Streams the selected input through the pitch detector while the tuner is showing. */
public final class TunerEngine implements AutoCloseable {
    public static final int WINDOW = 4096;
    public static final int HOP = 1024;

    public record Reading(double frequency, double clarity, double level, long nanoTime) {
        public static final Reading SILENT = new Reading(0, 0, -160, 0);
        public boolean voiced() { return frequency > 0; }
    }

    private final TunerState state;
    private final AudioSource.Opener opener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "pulseforge-tuner");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        return thread;
    });
    private volatile Reading reading = Reading.SILENT;
    private volatile boolean running;
    private volatile long generation;
    private volatile String errorMessage = "";
    private volatile String deviceName = "";
    private volatile int channel = 1;
    private volatile int channelCount = 1;
    private boolean closed;

    public TunerEngine(TunerState state) { this(state, MicrophoneSource::open); }

    public TunerEngine(TunerState state, AudioSource.Opener opener) {
        this.state = state;
        this.opener = opener;
    }

    public Reading reading() { return reading; }
    public boolean isRunning() { return running; }
    public String errorMessage() { return errorMessage; }
    public String deviceName() { return deviceName; }
    /** 1-based input channel being analyzed. */
    public int inputChannel() { return channel; }
    public int inputChannelCount() { return channelCount; }
    public static List<String> inputMixerNames() { return MicrophoneSource.names(); }
    public static int inputChannelCount(String deviceName) { return MicrophoneSource.channelCount(deviceName); }

    public synchronized void start() {
        if (closed || running) return;
        running = true;
        errorMessage = "";
        reading = Reading.SILENT;
        long token = ++generation;
        worker.submit(() -> capture(token));
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        generation++;
        reading = Reading.SILENT;
    }

    public synchronized void restartForInputChange() {
        if (running) { stop(); start(); }
    }

    private boolean current(long token) { return running && generation == token; }

    private void capture(long token) {
        AudioSource source = null;
        try {
            if (!current(token)) return;
            var settings = state.get();
            source = opener.open(settings.inputName(), settings.inputChannel());
            deviceName = source.name();
            channelCount = source.channelCount();
            channel = source.channel();
            var detector = new PitchDetector(source.sampleRate(), WINDOW);
            // The detector settles its filters on the older windows and measures the newest one.
            float[] frame = new float[WINDOW * PitchDetector.HISTORY_WINDOWS];
            float[] hop = new float[HOP];
            int filled = 0;
            while (current(token)) {
                source.read(hop);
                channel = source.channel();
                System.arraycopy(frame, HOP, frame, 0, frame.length - HOP);
                System.arraycopy(hop, 0, frame, frame.length - HOP, HOP);
                filled = Math.min(frame.length, filled + HOP);
                if (filled < frame.length) continue;
                var result = detector.detect(frame);
                if (current(token))
                    reading = new Reading(result.frequency(), result.clarity(), result.level(), System.nanoTime());
            }
        } catch (Exception error) {
            synchronized (this) {
                if (current(token)) {
                    errorMessage = error.getMessage() == null || error.getMessage().isBlank()
                            ? "Microphone input unavailable" : error.getMessage();
                    running = false;
                    reading = Reading.SILENT;
                }
            }
        } finally {
            if (source != null) source.close();
        }
    }

    @Override public synchronized void close() {
        stop();
        closed = true;
        worker.shutdown();
    }
}
