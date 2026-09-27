package app.pulseforge.audio;

import java.util.Random;

/** Plucked-string test signals: stiff-string partials that ring slightly sharp, decay and noise. */
public final class SyntheticGuitar {
    private static final int PARTIALS = 12;

    /**
     * @param fundamentalGain 1 for a balanced tone; small values imitate a microphone that loses the low fundamental
     * @param inharmonicity   string stiffness coefficient B; wound bass strings sit near 3e-4
     * @param noise           white-noise amplitude relative to a unit partial
     */
    public record Voice(double frequency, double fundamentalGain, double inharmonicity, double noise, long seed) {
        public static Voice clean(double frequency) { return new Voice(frequency, 1, 1e-4, .003, 7); }
        public static Voice wound(double frequency) { return new Voice(frequency, .6, 3e-4, .01, 11); }
        public static Voice thin(double frequency) { return new Voice(frequency, .15, 3e-4, .01, 13); }
    }

    private SyntheticGuitar() {}

    public static float[] tone(int rate, Voice voice, int length, double startSeconds) {
        var generator = new Generator(rate, voice);
        generator.position = Math.round(startSeconds * rate);
        float[] out = new float[length];
        generator.fill(out);
        return out;
    }

    /** Renders like a microphone: paced in real time and re-plucked every two seconds. */
    public static final class Source implements AudioSource {
        private final Generator generator;
        private final int rate;
        public Source(int rate, Voice voice) { this.rate = rate; generator = new Generator(rate, voice); }
        @Override public int sampleRate() { return rate; }
        @Override public String name() { return "Synthetic guitar"; }
        @Override public void read(float[] buffer) throws InterruptedException {
            generator.fill(buffer);
            Thread.sleep(Math.max(1, buffer.length * 1000L / rate));
        }
        @Override public void close() {}
    }

    private static final class Generator {
        private final int rate;
        private final Voice voice;
        private final double[] phases = new double[PARTIALS];
        private final Random random;
        private long position;

        Generator(int rate, Voice voice) {
            this.rate = rate;
            this.voice = voice;
            random = new Random(voice.seed());
            for (int k = 0; k < PARTIALS; k++) phases[k] = random.nextDouble() * 2 * Math.PI;
        }

        void fill(float[] out) {
            for (int i = 0; i < out.length; i++) {
                double t = (double) position / rate;
                double age = t % 2.0;
                double sum = 0;
                for (int k = 1; k <= PARTIALS; k++) {
                    double amplitude = (k == 1 ? voice.fundamentalGain() : 1) / Math.pow(k, .9)
                            * Math.exp(-age * (.4 + .25 * k));
                    // Partials of a stiff string ring sharp; the fundamental itself sits exactly at frequency.
                    double frequency = k * voice.frequency()
                            * Math.sqrt((1 + voice.inharmonicity() * k * k) / (1 + voice.inharmonicity()));
                    sum += amplitude * Math.sin(2 * Math.PI * frequency * t + phases[k - 1]);
                }
                sum += voice.noise() * random.nextGaussian();
                out[i] = (float) (sum * .3);
                position++;
            }
        }
    }
}
