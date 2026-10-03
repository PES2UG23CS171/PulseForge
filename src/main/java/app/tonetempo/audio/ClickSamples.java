package app.tonetempo.audio;

import app.tonetempo.model.SoundType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Generated click and drum sounds. Every accent peaks at full scale; the beat and subdivision variants carry
 * their quieter levels so the render loop mixes all sounds the same way.
 */
final class ClickSamples {
    private static final int RATE = AccurateAudioEngine.SAMPLE_RATE;
    static final float BEAT_LEVEL = .62f;
    static final float SUBDIVISION_LEVEL = .36f;
    private final Map<SoundType, SampleSet> generated = new EnumMap<>(SoundType.class);

    ClickSamples() {
        var random = new Random(314159);
        generated.put(SoundType.STUDIO, set(click(1900, .009, .35, random), click(1250, .009, .35, random),
                click(830, .007, .35, random)));
        generated.put(SoundType.DIGITAL, set(beep(2600, .008), beep(1800, .008), beep(1100, .006)));
        generated.put(SoundType.SOFT, set(click(1050, .014, .06, random), click(820, .014, .06, random),
                click(610, .011, .06, random)));
        generated.put(SoundType.WOOD, set(wood(1760, .014, random), wood(1180, .014, random), wood(790, .011, random)));
        float[] kick = kick(random);
        float[] snare = snare(random);
        float[] closedHat = hiHat(.04, random);
        generated.put(SoundType.DRUM_KIT, new SampleSet(normalize(kick, 1), normalize(snare, .85f),
                normalize(closedHat, .45f)));
        generated.put(SoundType.KICK, set(kick, kick, shorten(kick, .06)));
        generated.put(SoundType.SNARE, set(snare, snare, shorten(snare, .05)));
        generated.put(SoundType.HI_HAT, set(hiHat(.12, random), closedHat, hiHat(.022, random)));
        generated.put(SoundType.RIMSHOT, set(rimshot(880, random), rimshot(760, random), rimshot(660, random)));
        generated.put(SoundType.COWBELL, set(cowbell(.12), cowbell(.09), cowbell(.05)));
        generated.put(SoundType.CLAVE, set(clave(2500, .012), clave(2350, .010), clave(2200, .007)));
    }

    SampleSet get(SoundType type) { return generated.getOrDefault(type, generated.get(SoundType.STUDIO)); }

    /** Accent at full scale, beat and subdivision at their fixed levels. */
    private static SampleSet set(float[] accent, float[] beat, float[] subdivision) {
        return new SampleSet(normalize(accent, 1), normalize(beat, BEAT_LEVEL), normalize(subdivision, SUBDIVISION_LEVEL));
    }

    /** Scales to the level and fades the last 8 ms so a truncated tail never clicks. */
    private static float[] normalize(float[] source, float level) {
        float peak = 1e-6f;
        for (float value : source) peak = Math.max(peak, Math.abs(value));
        float[] result = new float[source.length];
        int fade = Math.min(source.length, (int) (RATE * .008));
        for (int i = 0; i < source.length; i++) {
            int remaining = source.length - i;
            double tail = remaining < fade ? (double) remaining / fade : 1;
            result[i] = (float) (source[i] / peak * level * tail);
        }
        return result;
    }

    /** Extra exponential fade for a tighter subdivision variant. */
    private static float[] shorten(float[] source, double tau) {
        int length = Math.min(source.length, (int) (RATE * tau * 6));
        float[] result = new float[length];
        for (int i = 0; i < length; i++) result[i] = (float) (source[i] * Math.exp(-i / (RATE * tau)));
        return result;
    }

    private static double attack(int i, double samples) { return Math.min(1, i / samples); }

    /** Sine body with a broadband tick on the attack. */
    private static float[] click(double hz, double tau, double tick, Random random) {
        int length = (int) (RATE * tau * 6) + 96;
        float[] data = new float[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double body = Math.sin(phase) * Math.exp(-t / tau);
            double transientPart = tick * random.nextGaussian() * Math.exp(-t / .0012);
            data[i] = (float) ((body + transientPart) * attack(i, 8));
            phase += 2 * Math.PI * hz / RATE;
        }
        return data;
    }

    /** Square-ish beep: fundamental plus a third harmonic. */
    private static float[] beep(double hz, double tau) {
        int length = (int) (RATE * tau * 6) + 64;
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double wave = Math.sin(2 * Math.PI * hz * t) + Math.sin(2 * Math.PI * 3 * hz * t) / 3;
            data[i] = (float) (wave * Math.exp(-t / tau) * attack(i, 4));
        }
        return data;
    }

    private static float[] wood(double hz, double tau, Random random) {
        int length = (int) (RATE * tau * 6);
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double body = Math.sin(2 * Math.PI * hz * t) * .65 + Math.sin(2 * Math.PI * hz * 1.71 * t) * .22
                    + Math.sin(2 * Math.PI * hz * .53 * t) * .18;
            double knock = random.nextGaussian() * .3 * Math.exp(-t / .0025);
            data[i] = (float) ((body * Math.exp(-t / tau) + knock) * attack(i, 6));
        }
        return data;
    }

    /** Kick: a sine sweeping down from 170 Hz to 50 Hz with a soft-clipped attack. */
    private static float[] kick(Random random) {
        int length = (int) (RATE * .55);
        float[] data = new float[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double hz = 50 + 120 * Math.exp(-t / .03);
            phase += 2 * Math.PI * hz / RATE;
            double body = Math.sin(phase) * Math.exp(-t / .13);
            double click = random.nextGaussian() * .35 * Math.exp(-t / .003);
            data[i] = (float) (Math.tanh(2.2 * body + click) * attack(i, 3));
        }
        return data;
    }

    /** Snare: shell modes plus band-passed noise for the wires. */
    private static float[] snare(Random random) {
        int length = (int) (RATE * .32);
        float[] data = new float[length];
        double aHigh = 1 / (1 + 2 * Math.PI * 1800 / RATE);
        double kLow = 1 - Math.exp(-2 * Math.PI * 5500 / RATE);
        double highPass = 0;
        double lowPass = 0;
        double previous = 0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double shell = (Math.sin(2 * Math.PI * 184 * t) * .55 + Math.sin(2 * Math.PI * 331 * t) * .35
                    + Math.sin(2 * Math.PI * 476 * t) * .1) * Math.exp(-t / .05);
            double white = random.nextGaussian();
            highPass = aHigh * (highPass + white - previous);
            previous = white;
            lowPass += (highPass - lowPass) * kLow;
            double wires = lowPass * Math.exp(-t / .10) * .7;
            double crack = white * Math.exp(-t / .002) * .5;
            data[i] = (float) (Math.tanh(1.6 * (shell + wires + crack)) * attack(i, 3));
        }
        return data;
    }

    /** Hi-hat: six square waves in the classic inharmonic ratios, high-passed twice at 7 kHz. */
    private static float[] hiHat(double tau, Random random) {
        double[] partials = {205.3, 304.4, 369.6, 522.7, 540, 800};
        int length = (int) (RATE * tau * 5) + 48;
        float[] data = new float[length];
        double a = 1 / (1 + 2 * Math.PI * 7000 / RATE);
        double y1 = 0, x1 = 0, y2 = 0, x2 = 0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double metal = 0;
            for (double hz : partials) metal += Math.signum(Math.sin(2 * Math.PI * hz * t));
            metal = metal / partials.length + random.nextGaussian() * .15;
            y1 = a * (y1 + metal - x1);
            x1 = metal;
            y2 = a * (y2 + y1 - x2);
            x2 = y1;
            data[i] = (float) (y2 * Math.exp(-t / tau) * attack(i, 2));
        }
        return data;
    }

    /** Rimshot: a crack with a short woody ring. */
    private static float[] rimshot(double hz, Random random) {
        int length = (int) (RATE * .07);
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double crack = random.nextGaussian() * Math.exp(-t / .0015) * .8;
            double ring = Math.sin(2 * Math.PI * hz * t) * Math.exp(-t / .018) * .7
                    + Math.sin(2 * Math.PI * hz * 2.4 * t) * Math.exp(-t / .006) * .4;
            data[i] = (float) (Math.tanh(1.5 * (crack + ring)) * attack(i, 2));
        }
        return data;
    }

    /** Cowbell: two square waves at 587 and 845 Hz through a band-pass. */
    private static float[] cowbell(double tau) {
        int length = (int) (RATE * tau * 5);
        float[] data = new float[length];
        double lowPass = 0;
        double kLow = 1 - Math.exp(-2 * Math.PI * 2600 / RATE);
        double aHigh = 1 / (1 + 2 * Math.PI * 450 / RATE);
        double highPass = 0;
        double previous = 0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double raw = Math.signum(Math.sin(2 * Math.PI * 587 * t)) + Math.signum(Math.sin(2 * Math.PI * 845 * t));
            lowPass += (raw - lowPass) * kLow;
            highPass = aHigh * (highPass + lowPass - previous);
            previous = lowPass;
            double envelope = Math.exp(-t / .012) * .6 + Math.exp(-t / tau) * .5;
            data[i] = (float) (highPass * envelope * attack(i, 2));
        }
        return data;
    }

    /** Clave: a bright resonant ping. */
    private static float[] clave(double hz, double tau) {
        int length = (int) (RATE * tau * 6) + 32;
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double wave = Math.sin(2 * Math.PI * hz * t) * Math.exp(-t / tau)
                    + Math.sin(2 * Math.PI * hz * 2.02 * t) * Math.exp(-t / (tau / 2)) * .3;
            data[i] = (float) (wave * attack(i, 1));
        }
        return data;
    }

    record SampleSet(float[] accent, float[] normal, float[] subdivision) {}
}
