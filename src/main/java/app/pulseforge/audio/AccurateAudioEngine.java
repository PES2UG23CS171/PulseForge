package app.pulseforge.audio;

import app.pulseforge.model.Accent;
import app.pulseforge.model.MetronomeSettings;
import app.pulseforge.model.MetronomeState;

import javax.sound.sampled.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AccurateAudioEngine implements AutoCloseable {
    public static final int SAMPLE_RATE = 48_000;
    private static final int CHUNK_FRAMES = 128;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final MetronomeState state;
    private final ClickSamples samples = new ClickSamples();
    private final AtomicBoolean playing = new AtomicBoolean();
    private final List<Consumer<BeatPulse>> pulseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Boolean>> playListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TransportState>> transportListeners = new CopyOnWriteArrayList<>();
    private volatile Thread audioThread;
    private volatile String errorMessage = "";
    private volatile TransportState transportState = TransportState.STOPPED;
    private volatile long resumeSequence;
    private volatile long startSequence;

    public AccurateAudioEngine(MetronomeState state) { this.state = state; }

    public boolean isPlaying() { return playing.get(); }
    public boolean isPaused() { return transportState == TransportState.PAUSED; }
    public TransportState transportState() { return transportState; }
    public String errorMessage() { return errorMessage; }
    public boolean customSampleLoaded() { return samples.customSampleLoaded(); }
    public void prepareCustomSample(String path) { samples.load(path); }
    public void addPulseListener(Consumer<BeatPulse> listener) { pulseListeners.add(listener); }
    public void addPlayListener(Consumer<Boolean> listener) { playListeners.add(listener); }
    public void addTransportListener(Consumer<TransportState> listener) { transportListeners.add(listener); }

    public synchronized void play() {
        if (!playing.compareAndSet(false, true)) return;
        errorMessage = "";
        startSequence = transportState == TransportState.PAUSED ? resumeSequence : 0;
        transportState = TransportState.PLAYING;
        audioThread = new Thread(this::renderLoop, "pulseforge-realtime-audio");
        audioThread.setDaemon(true);
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();
        notifyTransport(TransportState.PLAYING);
    }

    public synchronized void pause() {
        if (!playing.compareAndSet(true, false)) return;
        transportState = TransportState.PAUSED;
        var thread = audioThread;
        if (thread != null) thread.interrupt();
        notifyTransport(TransportState.PAUSED);
    }

    public synchronized void stop() {
        playing.set(false);
        transportState = TransportState.STOPPED;
        resumeSequence = 0;
        var thread = audioThread;
        if (thread != null) thread.interrupt();
        notifyTransport(TransportState.STOPPED);
    }

    public void start() { play(); }
    public void toggle() { togglePlayPause(); }
    public void togglePlayPause() { if (isPlaying()) pause(); else play(); }

    public void restartForOutputChange() {
        if (!isPlaying()) return;
        stop();
        var timer = new Timer(80, event -> play());
        timer.setRepeats(false);
        timer.start();
    }

    public static List<String> outputMixerNames() {
        var names = new ArrayList<String>();
        names.add("System Default");
        var info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (var mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(info)) names.add(mixerInfo.getName());
            } catch (Exception ignored) {}
        }
        return names;
    }

    private void renderLoop() {
        SourceDataLine line = null;
        try {
            line = openLine(state.get().mixerName());
            line.start();
            long streamFrame = 0;
            double nextEventFrame = 0;
            long sequence = startSequence;
            long seenVersion = state.structureVersion();
            var voices = new ArrayList<Voice>();
            byte[] pcm = new byte[CHUNK_FRAMES * 2];

            while (playing.get() && !Thread.currentThread().isInterrupted()) {
                var settings = state.get();
                long version = state.structureVersion();
                if (version != seenVersion) {
                    sequence = 0;
                    resumeSequence = 0;
                    nextEventFrame = streamFrame;
                    voices.clear();
                    seenVersion = version;
                }

                Arrays.fill(pcm, (byte) 0);
                double chunkEnd = streamFrame + CHUNK_FRAMES;
                while (nextEventFrame < chunkEnd) {
                    int offset = Math.max(0, (int) Math.round(nextEventFrame - streamFrame));
                    scheduleVoice(settings, sequence, offset, voices);
                    notifyPulse(settings, sequence, line);
                    nextEventFrame += intervalFrames(settings, sequence);
                    sequence++;
                    resumeSequence = sequence;
                }
                mixVoices(pcm, voices, settings.volume());
                int written = 0;
                while (written < pcm.length && playing.get()) {
                    written += line.write(pcm, written, pcm.length - written);
                }
                streamFrame += CHUNK_FRAMES;
            }
        } catch (Exception exception) {
            errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            playing.set(false);
            transportState = TransportState.STOPPED;
            resumeSequence = 0;
            notifyTransport(TransportState.STOPPED);
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
                if (mixerInfo.getName().equals(selected)) {
                    var mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) line = (SourceDataLine) mixer.getLine(info);
                    break;
                }
            }
        }
        if (line == null) line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, CHUNK_FRAMES * 2 * 8);
        return line;
    }

    private void scheduleVoice(MetronomeSettings settings, long sequence, int offset, List<Voice> voices) {
        int steps = settings.subdivision().stepsPerQuarter();
        int sub = (int) (sequence % steps);
        int beat = (int) ((sequence / steps) % settings.beatsPerBar());
        Accent accent = settings.accents().get(beat);
        if (sub == 0 && accent == Accent.MUTED) return;
        var set = samples.get(settings.sound(), settings.customSamplePath());
        float[] data = sub != 0 ? set.subdivision() : accent == Accent.STRONG ? set.accent() : set.normal();
        float gain = sub != 0 ? .48f : accent == Accent.STRONG ? 1f : .76f;
        voices.add(new Voice(data, -offset, gain));
    }

    static double intervalFrames(MetronomeSettings settings, long sequence) {
        int steps = settings.subdivision().stepsPerQuarter();
        double nominal = SAMPLE_RATE * 60.0 / settings.quarterNoteBpm() / steps;
        if (steps > 1 && steps % 2 == 0 && settings.swing() > .5001) {
            return nominal * 2 * (sequence % 2 == 0 ? settings.swing() : 1.0 - settings.swing());
        }
        return nominal;
    }

    private static void mixVoices(byte[] pcm, List<Voice> voices, double volume) {
        for (int frame = 0; frame < CHUNK_FRAMES; frame++) {
            double mixed = 0;
            for (var voice : voices) {
                if (voice.position >= 0 && voice.position < voice.data.length)
                    mixed += voice.data[voice.position] * voice.gain;
                voice.position++;
            }
            mixed = Math.tanh(mixed * volume * 1.15);
            short value = (short) Math.round(mixed * 32767);
            pcm[frame * 2] = (byte) (value & 0xff);
            pcm[frame * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        voices.removeIf(voice -> voice.position >= voice.data.length);
    }

    private void notifyPulse(MetronomeSettings settings, long sequence, SourceDataLine line) {
        int steps = settings.subdivision().stepsPerQuarter();
        int sub = (int) (sequence % steps);
        int beat = (int) ((sequence / steps) % settings.beatsPerBar());
        int queuedFrames = Math.max(0, line.getBufferSize() / 2 - line.available() / 2);
        int delayMs = Math.max(0, (int) Math.round(queuedFrames * 1000.0 / SAMPLE_RATE));
        var pulse = new BeatPulse(beat, sub, beat == 0 && sub == 0,
                System.nanoTime() + queuedFrames * 1_000_000_000L / SAMPLE_RATE);
        var timer = new Timer(delayMs, event -> pulseListeners.forEach(listener -> listener.accept(pulse)));
        timer.setRepeats(false);
        timer.start();
    }

    private void notifyTransport(TransportState value) {
        SwingUtilities.invokeLater(() -> {
            transportListeners.forEach(listener -> listener.accept(value));
            playListeners.forEach(listener -> listener.accept(value == TransportState.PLAYING));
        });
    }

    @Override public void close() { stop(); }

    private static final class Voice {
        private final float[] data;
        private int position;
        private final float gain;
        private Voice(float[] data, int position, float gain) {
            this.data = data; this.position = position; this.gain = gain;
        }
    }
}
