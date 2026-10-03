package app.tonetempo.audio;

import app.tonetempo.model.Accent;
import app.tonetempo.model.MetronomeState;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class AccurateAudioEngine implements AutoCloseable {
    public static final int SAMPLE_RATE = 48_000;
    private static final int CHUNK_FRAMES = 256;
    /** Soft saturation: a full-scale hit at full volume reaches 0 dBFS; overlapping hits compress instead of clipping. */
    private static final double DRIVE = 1.5;
    private static final double SATURATION = Math.tanh(DRIVE);
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private final MetronomeState state;
    private final ClickSamples samples = new ClickSamples();
    // A single worker serializes device close/open even during rapid play/pause clicks.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "tt-pro-audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    });
    private final List<Consumer<TransportState>> listeners = new CopyOnWriteArrayList<>();
    private volatile TransportState transport = TransportState.STOPPED;
    private volatile long generation;
    private volatile String errorMessage = "";
    private volatile Session session;
    private BeatClock.Position paused = BeatClock.Position.beginning();
    private boolean closed;

    public AccurateAudioEngine(MetronomeState state) { this.state = state; }
    public boolean isPlaying() { return transport == TransportState.PLAYING; }
    public boolean isPaused() { return transport == TransportState.PAUSED; }
    public TransportState transportState() { return transport; }
    public String errorMessage() { return errorMessage; }
    public void addTransportListener(Consumer<TransportState> listener) { listeners.add(listener); }

    public synchronized void play() {
        if (closed || isPlaying()) return;
        long token = ++generation;
        var start = paused;
        session = null;
        errorMessage = "";
        transport = TransportState.PLAYING;
        notifyTransport(token);
        worker.submit(() -> renderLoop(token, start));
    }

    public synchronized void pause() {
        if (!isPlaying()) return;
        paused = position();
        transport = TransportState.PAUSED;
        long token = ++generation;
        notifyTransport(token);
    }

    public synchronized void stop() {
        paused = BeatClock.Position.beginning();
        transport = TransportState.STOPPED;
        long token = ++generation;
        notifyTransport(token);
    }

    public void togglePlayPause() { if (isPlaying()) pause(); else play(); }

    /** Plays the current sound's accent, beat and two subdivisions once; ignored while the metronome plays. */
    public synchronized void preview() {
        if (closed || isPlaying()) return;
        long token = generation;
        worker.submit(() -> playPreview(token));
    }

    public synchronized void restartForOutputChange() {
        if (isPlaying()) { pause(); play(); }
    }

    /** Uses the device's consumed-frame counter, never the render thread's wall clock. */
    public synchronized BeatClock.Position position() {
        var active = session;
        if (!isPlaying() || active == null || active.token != generation) return paused;
        long frame = active.line.getLongFramePosition();
        return active.position(frame);
    }

    public static List<String> outputMixerNames() {
        var names = new ArrayList<String>();
        names.add("System Default");
        var info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (var mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(info)) names.add(mixerInfo.getName());
            } catch (Exception ignored) { }
        }
        return names;
    }

    private boolean current(long token) { return generation == token && isPlaying(); }

    private void renderLoop(long token, BeatClock.Position start) {
        SourceDataLine line = null;
        try {
            if (!current(token)) return;
            var settings = state.get();
            line = openLine(settings.mixerName());
            var clock = new BeatClock(SAMPLE_RATE, settings, start);
            var active = new Session(token, line, clock.anchor());
            if (!current(token)) return;
            session = active;
            var voices = new ArrayList<Voice>();
            byte[] pcm = new byte[CHUNK_FRAMES * 2];
            boolean started = false;
            while (current(token)) {
                settings = state.get();
                long frameStart = clock.cursor();
                var bank = samples.get(settings.sound());
                for (var event : clock.advance(CHUNK_FRAMES, settings)) {
                    active.add(event);
                    if (event.audible()) {
                        float[] data = event.step() > 0 ? bank.subdivision()
                                : event.accent() == Accent.STRONG ? bank.accent() : bank.normal();
                        voices.add(new Voice(data, -(int) (event.frame() - frameStart)));
                    }
                }
                // Drain consumed events even when the window is hidden.
                active.position(line.getLongFramePosition());
                mixVoices(pcm, voices, settings.volume());
                int written = 0;
                while (written < pcm.length && current(token)) {
                    int count = line.write(pcm, written, pcm.length - written);
                    if (count <= 0) throw new LineUnavailableException("The audio output stopped accepting data.");
                    written += count;
                    if (!started) { line.start(); started = true; }
                }
            }
        } catch (Exception error) {
            synchronized (this) {
                if (current(token)) {
                    errorMessage = error.getMessage() == null ? "Audio output unavailable" : error.getMessage();
                    transport = TransportState.STOPPED;
                    paused = BeatClock.Position.beginning();
                    notifyTransport(token);
                }
            }
        } finally {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
        }
    }

    private SourceDataLine openLine(String selected) throws LineUnavailableException {
        var info = new DataLine.Info(SourceDataLine.class, FORMAT);
        SourceDataLine line = null;
        if (!"System Default".equals(selected)) {
            for (var mixerInfo : AudioSystem.getMixerInfo()) {
                if (mixerInfo.getName().equals(selected) && AudioSystem.getMixer(mixerInfo).isLineSupported(info)) {
                    line = (SourceDataLine) AudioSystem.getMixer(mixerInfo).getLine(info);
                    break;
                }
            }
            if (line == null) throw new LineUnavailableException("Selected output is disconnected. Choose an output in Sound settings.");
        }
        if (line == null) line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, CHUNK_FRAMES * 2 * 8);
        return line;
    }

    private void playPreview(long token) {
        SourceDataLine line = null;
        try {
            var settings = state.get();
            var bank = samples.get(settings.sound());
            line = openLine(settings.mixerName());
            long[] starts = {0, Math.round(SAMPLE_RATE * .35), Math.round(SAMPLE_RATE * .70), Math.round(SAMPLE_RATE * .875)};
            float[][] hits = {bank.accent(), bank.normal(), bank.subdivision(), bank.subdivision()};
            var voices = new ArrayList<Voice>();
            byte[] pcm = new byte[CHUNK_FRAMES * 2];
            boolean started = false;
            for (long frame = 0; frame < SAMPLE_RATE * 1.4 && generation == token; frame += CHUNK_FRAMES) {
                for (int i = 0; i < starts.length; i++)
                    if (starts[i] >= frame && starts[i] < frame + CHUNK_FRAMES)
                        voices.add(new Voice(hits[i], -(int) (starts[i] - frame)));
                mixVoices(pcm, voices, state.get().volume());
                line.write(pcm, 0, pcm.length);
                if (!started) { line.start(); started = true; }
            }
            if (generation == token) line.drain();
        } catch (Exception ignored) {
            // Best effort: output problems are reported when the metronome itself starts.
        } finally {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
        }
    }

    private static void mixVoices(byte[] pcm, List<Voice> voices, double volume) {
        for (int frame = 0; frame < CHUNK_FRAMES; frame++) {
            double mixed = 0;
            for (var voice : voices) {
                if (voice.position >= 0 && voice.position < voice.data.length) mixed += voice.data[voice.position];
                voice.position++;
            }
            short value = (short) Math.round(shape(mixed, volume) * 32767);
            pcm[frame * 2] = (byte) value;
            pcm[frame * 2 + 1] = (byte) (value >>> 8);
        }
        voices.removeIf(voice -> voice.position >= voice.data.length);
    }

    /** Volume follows a power law for a usable range; the saturator lets full volume reach full scale. */
    static double shape(double mixed, double volume) {
        double driven = mixed * Math.pow(Math.clamp(volume, 0, 1), 1.5) * DRIVE;
        return Math.clamp(Math.tanh(driven) / SATURATION, -1, 1);
    }

    private void notifyTransport(long token) {
        SwingUtilities.invokeLater(() -> {
            if (generation == token) listeners.forEach(listener -> listener.accept(transport));
        });
    }

    @Override public synchronized void close() {
        stop();
        closed = true;
        worker.shutdown();
    }

    private static final class Session {
        final long token;
        final SourceDataLine line;
        final ArrayDeque<BeatClock.Event> events = new ArrayDeque<>();
        BeatClock.Event anchor;
        Session(long token, SourceDataLine line, BeatClock.Event anchor) {
            this.token = token; this.line = line; this.anchor = anchor;
        }
        synchronized void add(BeatClock.Event event) { events.add(event); }
        synchronized BeatClock.Position position(long frame) {
            while (!events.isEmpty() && events.peek().frame() <= frame) anchor = events.remove();
            return anchor.positionAt(frame);
        }
    }

    private static final class Voice {
        final float[] data;
        int position;
        Voice(float[] data, int position) {
            this.data = data;
            this.position = position;
        }
    }
}
