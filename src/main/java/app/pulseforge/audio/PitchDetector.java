package app.pulseforge.audio;

import java.util.Arrays;

/**
 * Pitch estimation for one guitar string.
 * <p>
 * Stage one finds the period with McLeod's normalized square difference function and its first strong key
 * maximum, which resists octave errors on notes whose fundamental is weak. Stage two passes only the
 * fundamental through a resonant band-pass and measures that component's period across many cycles, so the
 * stiff-string partials that ring slightly sharp cannot pull the reading. Filters run over several earlier
 * windows so their start-up transients never fall inside the samples being measured.
 */
public final class PitchDetector {
    public static final double MIN_HZ = 50;
    public static final double MAX_HZ = 1400;
    /** Key maxima at least this fraction of the strongest one are candidates; the earliest wins. */
    private static final double KEY_MAXIMUM_RATIO = .9;
    private static final double MIN_CLARITY = .6;
    /** Windows quieter than -60 dBFS are treated as silence. */
    private static final double MIN_RMS = 1e-3;
    /** Mains hum is notched out before analysis; a reading this close to mains is leftover hum, not a string. */
    private static final double[] MAINS_HZ = {50, 60};
    private static final double MAINS_TOLERANCE_HZ = .8;
    private static final double NOTCH_Q = 10;
    private static final double BAND_Q = 4;
    /** Filters settle over the older windows before the newest one is measured. */
    public static final int HISTORY_WINDOWS = 4;
    private final int sampleRate;
    private final int window;
    private final int minLag;
    private final int maxLag;
    private final Fft fft;
    private final double[] history;
    private final double[] scratch;
    private final double[] signal;
    private final double[] filtered;
    private final double[] re;
    private final double[] im;
    private final double[] nsdf;
    private final double[] band;
    private final int[] lobeLags;
    private final double[] lobeValues;
    private Trace trace = new Trace(0, 0, 0);

    public record Result(double frequency, double clarity, double level) {
        public boolean voiced() { return frequency > 0; }
    }

    /** Periods in samples after each stage of the last analysis; for diagnostics and tests. */
    record Trace(double coarse, double refined, double fundamental) {}

    public PitchDetector(int sampleRate, int window) {
        if (window < 1024 || Integer.bitCount(window) != 1)
            throw new IllegalArgumentException("Window must be a power of two of at least 1024 samples");
        this.sampleRate = sampleRate;
        this.window = window;
        minLag = Math.max(2, (int) Math.floor(sampleRate / MAX_HZ));
        maxLag = Math.min(window / 2, (int) Math.ceil(sampleRate / MIN_HZ));
        fft = new Fft(window * 2);
        history = new double[window * HISTORY_WINDOWS];
        scratch = new double[history.length];
        signal = new double[window];
        filtered = new double[window];
        nsdf = new double[window];
        band = new double[window];
        re = new double[window * 2];
        im = new double[window * 2];
        lobeLags = new int[window];
        lobeValues = new double[window];
    }

    public int window() { return window; }
    public int sampleRate() { return sampleRate; }
    Trace trace() { return trace; }

    /**
     * Analyzes the newest {@link #window()} samples. Passing up to {@link #HISTORY_WINDOWS} windows lets the
     * filters settle on the older samples first, which is how the tuner engine calls it.
     */
    public Result detect(float[] samples) {
        if (samples.length < window) throw new IllegalArgumentException("Need at least " + window + " samples");
        double energy = highPass(samples);
        double rms = Math.sqrt(energy / window);
        double level = 20 * Math.log10(Math.max(rms, 1e-9));
        if (rms < MIN_RMS) return new Result(0, 0, level);
        autocorrelate(signal);
        normalize(signal, energy, nsdf);
        int lag = keyMaximum();
        if (lag < 0) return new Result(0, 0, level);
        double[] peak = interpolate(nsdf, lag);
        double clarity = Math.min(1, peak[1]);
        if (clarity < MIN_CLARITY) return new Result(0, clarity, level);
        double refined = refine(nsdf, peak[0], .6 * clarity);
        double period = refineFundamental(refined);
        trace = new Trace(peak[0], refined, period);
        double frequency = sampleRate / period;
        for (double mains : MAINS_HZ) if (Math.abs(frequency - mains) < MAINS_TOLERANCE_HZ) return new Result(0, clarity, level);
        return new Result(frequency, clarity, level);
    }

    /** Removes offset, rumble and mains hum over the whole history; returns the energy of the analysis window. */
    private double highPass(float[] samples) {
        int available = Math.min(samples.length, history.length);
        int start = samples.length - available;
        double mean = 0;
        for (int i = samples.length - window; i < samples.length; i++) mean += samples[i];
        mean /= window;
        double a = 1 / (1 + 2 * Math.PI * 25 / sampleRate);
        double previousIn = 0;
        double previousOut = 0;
        Arrays.fill(history, 0, history.length - available, 0);
        for (int i = 0; i < available; i++) {
            double in = samples[start + i] - mean;
            double out = a * (previousOut + in - previousIn);
            previousIn = in;
            previousOut = out;
            history[history.length - available + i] = out;
        }
        for (double mains : MAINS_HZ) notch(history, mains);
        System.arraycopy(history, history.length - window, signal, 0, window);
        double energy = 0;
        for (double value : signal) energy += value * value;
        return energy;
    }

    /** Leaves the linear autocorrelation of the source in re[]. */
    private void autocorrelate(double[] source) {
        int n = window * 2;
        System.arraycopy(source, 0, re, 0, window);
        Arrays.fill(re, window, n, 0);
        Arrays.fill(im, 0);
        fft.transform(re, im, false);
        for (int i = 0; i < n; i++) {
            re[i] = re[i] * re[i] + im[i] * im[i];
            im[i] = 0;
        }
        fft.transform(re, im, true);
    }

    /** McLeod's normalization, which is exact for any slowly varying amplitude envelope. */
    private void normalize(double[] source, double energy, double[] out) {
        double m = 2 * energy;
        out[0] = 1;
        for (int tau = 1; tau < window; tau++) {
            m -= source[tau - 1] * source[tau - 1] + source[window - tau] * source[window - tau];
            out[tau] = m > 1e-12 ? 2 * re[tau] / m : 0;
        }
    }

    private int keyMaximum() {
        int tau = 1;
        while (tau < maxLag && nsdf[tau] > 0) tau++;
        int count = 0;
        double strongest = 0;
        while (tau < maxLag) {
            while (tau < maxLag && nsdf[tau] <= 0) tau++;
            int bestLag = -1;
            double best = 0;
            while (tau < maxLag && nsdf[tau] > 0) {
                if (nsdf[tau] > best) { best = nsdf[tau]; bestLag = tau; }
                tau++;
            }
            if (bestLag >= minLag) {
                lobeLags[count] = bestLag;
                lobeValues[count++] = best;
                strongest = Math.max(strongest, best);
            }
        }
        for (int i = 0; i < count; i++) if (lobeValues[i] >= KEY_MAXIMUM_RATIO * strongest) return lobeLags[i];
        return -1;
    }

    /** Parabolic vertex through three neighbouring lags: {position, value}. */
    private static double[] interpolate(double[] values, int lag) {
        double y0 = values[lag - 1];
        double y1 = values[lag];
        double y2 = values[lag + 1];
        double a = (y0 + y2) / 2 - y1;
        double b = (y2 - y0) / 2;
        if (a >= 0) return new double[] {lag, y1};
        double delta = Math.clamp(-b / (2 * a), -.5, .5);
        return new double[] {lag + delta, y1 + b * delta + a * delta * delta};
    }

    /** Measures the period across k cycles, which divides the interpolation error by k. */
    private double refine(double[] values, double period, double floor) {
        double refined = period;
        int limit = window / 2;
        for (int k = 2; k * refined <= limit; k++) {
            int best = localMaximum(values, (int) Math.round(k * refined), 2);
            if (best < 0 || values[best] < floor) break;
            double candidate = interpolate(values, best)[0] / k;
            if (Math.abs(candidate - refined) > .75) break;
            refined = candidate;
        }
        return refined;
    }

    private int localMaximum(double[] values, int center, int spread) {
        int best = -1;
        for (int lag = center - spread; lag <= center + spread; lag++) {
            if (lag < 1 || lag >= window - 1) continue;
            if (values[lag] >= values[lag - 1] && values[lag] >= values[lag + 1]
                    && (best < 0 || values[lag] > values[best])) best = lag;
        }
        return best;
    }

    /**
     * Isolates the fundamental with a two-stage resonant band-pass and measures its period. Autocorrelation
     * ignores the filter's phase response, so a causal filter is enough and leaves no transient in the
     * analysis window. Keeps the full-band period when the fundamental is too weak to trust.
     */
    private double refineFundamental(double period) {
        double energy = bandPass(sampleRate / period);
        if (energy <= 1e-12) return period;
        autocorrelate(filtered);
        normalize(filtered, energy, band);
        int best = localMaximum(band, (int) Math.round(period), Math.max(2, (int) (period / 4)));
        if (best < 0 || band[best] < .7) return period;
        double[] peak = interpolate(band, best);
        if (Math.abs(peak[0] - period) > Math.max(1, period * .025)) return period;
        return refine(band, peak[0], .6 * peak[1]);
    }

    /** Second-order notch in place; its transient settles within the warm-up windows. */
    private void notch(double[] data, double centre) {
        double omega = 2 * Math.PI * centre / sampleRate;
        double alpha = Math.sin(omega) / (2 * NOTCH_Q);
        double a0 = 1 + alpha;
        double b0 = 1 / a0;
        double b1 = -2 * Math.cos(omega) / a0;
        double a2 = (1 - alpha) / a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < data.length; i++) {
            double x0 = data[i];
            double y0 = b0 * x0 + b1 * x1 + b0 * x2 - b1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x0;
            y2 = y1;
            y1 = y0;
            data[i] = y0;
        }
    }

    /** Two cascaded second-order band-passes over the whole history; returns the analysis window's energy. */
    private double bandPass(double centre) {
        double omega = 2 * Math.PI * centre / sampleRate;
        double alpha = Math.sin(omega) / (2 * BAND_Q);
        double a0 = 1 + alpha;
        double b0 = alpha / a0;
        double a1 = -2 * Math.cos(omega) / a0;
        double a2 = (1 - alpha) / a0;
        System.arraycopy(history, 0, scratch, 0, history.length);
        for (int pass = 0; pass < 2; pass++) {
            double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
            for (int i = 0; i < history.length; i++) {
                double x0 = scratch[i];
                double y0 = b0 * (x0 - x2) - a1 * y1 - a2 * y2;
                x2 = x1;
                x1 = x0;
                y2 = y1;
                y1 = y0;
                scratch[i] = y0;
            }
        }
        System.arraycopy(scratch, history.length - window, filtered, 0, window);
        double energy = 0;
        for (double value : filtered) energy += value * value;
        return energy;
    }
}
