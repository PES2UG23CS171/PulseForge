package app.pulseforge.audio;

import app.pulseforge.model.SoundType;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

final class ClickSamples {
    private static final int RATE = AccurateAudioEngine.SAMPLE_RATE;
    private final Map<SoundType, SampleSet> generated = new EnumMap<>(SoundType.class);
    private volatile String loadedPath = "";
    private volatile SampleSet custom;

    ClickSamples() {
        generated.put(SoundType.STUDIO, tones(1900, 1250, 830, .036, .992));
        generated.put(SoundType.DIGITAL, tones(2600, 1800, 1100, .022, .987));
        generated.put(SoundType.WOOD, wood());
        generated.put(SoundType.SOFT, tones(1050, 820, 610, .025, .982));
    }

    SampleSet get(SoundType type, String path) {
        if (type == SoundType.CUSTOM && path != null && !path.isBlank()) {
            if (!path.equals(loadedPath)) load(path);
            if (custom != null) return custom;
        }
        return generated.getOrDefault(type, generated.get(SoundType.STUDIO));
    }

    synchronized void load(String path) {
        if (path.equals(loadedPath)) return;
        loadedPath = path;
        custom = null;
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new File(path))) {
            var targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RATE, 16, 1, 2, RATE, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(targetFormat, source)) {
                byte[] bytes = readLimited(pcm, RATE * 2 * 45);
                float[] all = decode(bytes);
                float[] hit = extractTransient(all);
                custom = new SampleSet(scalePitch(hit, 1.10), hit, scalePitch(hit, .86));
            }
        } catch (Exception ignored) {
            // UI displays a fallback indicator through customSampleLoaded().
        }
    }

    boolean customSampleLoaded() { return custom != null; }

    private static byte[] readLimited(AudioInputStream in, int limit) throws Exception {
        var out = new ByteArrayOutputStream(Math.min(limit, RATE * 10));
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < limit) {
            int count = in.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (count < 0) break;
            out.write(buffer, 0, count);
            total += count;
        }
        return out.toByteArray();
    }

    private static float[] decode(byte[] bytes) {
        float[] result = new float[bytes.length / 2];
        for (int i = 0; i < result.length; i++) {
            int lo = bytes[i * 2] & 0xff;
            int hi = bytes[i * 2 + 1];
            result[i] = (short) ((hi << 8) | lo) / 32768f;
        }
        return result;
    }

    private static float[] extractTransient(float[] source) {
        int scanStart = Math.min(source.length, RATE / 20);
        int scanEnd = Math.min(source.length, RATE * 45);
        int peak = scanStart;
        double best = 0;
        int window = 128;
        for (int i = scanStart; i + window < scanEnd; i += 64) {
            double energy = 0;
            for (int j = 0; j < window; j++) energy += Math.abs(source[i + j]);
            if (energy > best) { best = energy; peak = i; }
        }
        double threshold = best / window * .18;
        int start = peak;
        int earliest = Math.max(0, peak - RATE / 5);
        while (start > earliest && Math.abs(source[start]) > threshold) start--;
        int length = Math.min((int) (RATE * .16), source.length - start);
        if (length <= 0 || best < .001) return new float[] {0};
        float[] result = new float[length];
        float max = .001f;
        for (int i = 0; i < length; i++) max = Math.max(max, Math.abs(source[start + i]));
        for (int i = 0; i < length; i++) {
            double fade = i < 64 ? i / 64.0 : Math.pow(1.0 - (double) i / length, .45);
            result[i] = (float) (source[start + i] / max * .88 * fade);
        }
        return result;
    }

    private static SampleSet tones(double accent, double normal, double sub, double seconds, double decay) {
        return new SampleSet(tone(accent, seconds, decay), tone(normal, seconds, decay),
                tone(sub, seconds * .78, decay * .997));
    }

    private static SampleSet wood() {
        var random = new Random(314159);
        return new SampleSet(woodHit(1760, random), woodHit(1180, random), woodHit(790, random));
    }

    private static float[] tone(double hz, double seconds, double decay) {
        int length = (int) (RATE * seconds);
        float[] data = new float[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double attack = Math.min(1, i / 12.0);
            data[i] = (float) (Math.sin(phase) * Math.pow(decay, i) * attack * .88);
            phase += 2 * Math.PI * hz / RATE;
        }
        return data;
    }

    private static float[] woodHit(double hz, Random random) {
        int length = (int) (RATE * .045);
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            double body = Math.sin(2 * Math.PI * hz * i / RATE) * .65
                    + Math.sin(2 * Math.PI * hz * 1.71 * i / RATE) * .22;
            double noise = (random.nextDouble() * 2 - 1) * .25;
            data[i] = (float) ((body + noise) * Math.exp(-i / (RATE * .009)));
        }
        return data;
    }

    private static float[] scalePitch(float[] source, double factor) {
        int length = Math.max(1, (int) (source.length / factor));
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            double pos = i * factor;
            int at = Math.min(source.length - 1, (int) pos);
            int next = Math.min(source.length - 1, at + 1);
            result[i] = (float) (source[at] * (1 - (pos - at)) + source[next] * (pos - at));
        }
        return result;
    }

    record SampleSet(float[] accent, float[] normal, float[] subdivision) {}
}
