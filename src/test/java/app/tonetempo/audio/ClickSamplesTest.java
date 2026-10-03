package app.tonetempo.audio;

import app.tonetempo.model.SoundType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClickSamplesTest {
    private static final int RATE = AccurateAudioEngine.SAMPLE_RATE;
    private final ClickSamples samples = new ClickSamples();

    @Test void everySoundPeaksAtFullScaleWithQuieterBeatsAndSubdivisions() {
        for (var type : SoundType.values()) {
            var set = samples.get(type);
            assertEquals(1, peak(set.accent()), .001, type + " accent peak");
            assertTrue(peak(set.normal()) >= .5 && peak(set.normal()) <= .9, type + " beat peak " + peak(set.normal()));
            assertTrue(peak(set.subdivision()) < peak(set.normal()), type + " subdivision quieter than beat");
            for (float[] hit : new float[][] {set.accent(), set.normal(), set.subdivision()}) {
                assertTrue(hit.length >= RATE * .02, type + " too short: " + hit.length);
                assertTrue(hit.length <= RATE * .85, type + " too long: " + hit.length);
                assertTrue(Math.abs(hit[hit.length - 1]) < .05, type + " does not fade out");
            }
        }
    }

    @Test void clicksCarryRealEnergyNotJustAPeak() {
        for (var type : new SoundType[] {SoundType.STUDIO, SoundType.DIGITAL, SoundType.SOFT, SoundType.WOOD}) {
            double rms = rms(samples.get(type).accent(), .05);
            assertTrue(rms > .12, type + " accent RMS over 50 ms is only " + rms);
        }
        assertTrue(rms(samples.get(SoundType.KICK).accent(), .1) > .3);
    }

    @Test void drumsHaveTheirCharacteristicSpectra() {
        assertTrue(centroid(samples.get(SoundType.KICK).accent()) < 250, "kick is low");
        assertTrue(centroid(samples.get(SoundType.HI_HAT).normal()) > 6000, "hi-hat sizzles");
        double snare = centroid(samples.get(SoundType.SNARE).accent());
        assertTrue(snare > 1000 && snare < 8000, "snare mixes shell and wires: " + snare);
        double cowbell = centroid(samples.get(SoundType.COWBELL).accent());
        assertTrue(cowbell > 500 && cowbell < 2500, "cowbell rings mid: " + cowbell);
        var kit = samples.get(SoundType.DRUM_KIT);
        assertTrue(centroid(kit.accent()) < 250 && centroid(kit.subdivision()) > 6000, "kit: kick accent, hi-hat subdivisions");
        assertTrue(kit.normal().length > RATE * .2, "kit beat is a snare");
        assertTrue(samples.get(SoundType.HI_HAT).accent().length > samples.get(SoundType.HI_HAT).normal().length,
                "accent hi-hat rings longer than the closed one");
    }

    @Test void mixShapeReachesFullScaleWithoutWrappingAndStaysLoudAtTheDefaultVolume() {
        assertEquals(1, AccurateAudioEngine.shape(1, 1), 1e-9);
        assertEquals(-1, AccurateAudioEngine.shape(-1, 1), 1e-9);
        assertEquals(0, AccurateAudioEngine.shape(1, 0), 1e-9);
        assertTrue(AccurateAudioEngine.shape(2.5, 1) <= 1);
        assertTrue(AccurateAudioEngine.shape(1, .72) > .75, "default volume is loud");
        assertTrue(AccurateAudioEngine.shape(1, .1) < .1, "low volume is quiet");
        double previous = 0;
        for (double volume = .05; volume <= 1; volume += .05) {
            double level = AccurateAudioEngine.shape(1, volume);
            assertTrue(level > previous, "volume is monotonic");
            previous = level;
        }
        assertTrue(AccurateAudioEngine.shape(ClickSamples.BEAT_LEVEL, 1) < .9, "beats stay below accents");
        assertTrue(AccurateAudioEngine.shape(ClickSamples.SUBDIVISION_LEVEL, 1) < .65, "subdivisions stay below beats");
    }

    private static double peak(float[] data) {
        double peak = 0;
        for (float value : data) peak = Math.max(peak, Math.abs(value));
        return peak;
    }

    private static double rms(float[] data, double seconds) {
        int count = (int) (RATE * seconds);
        double sum = 0;
        for (int i = 0; i < count; i++) sum += i < data.length ? data[i] * data[i] : 0;
        return Math.sqrt(sum / count);
    }

    /** Power-weighted mean frequency of the first 4096 samples. */
    private static double centroid(float[] data) {
        int n = 8192;
        double[] re = new double[n];
        double[] im = new double[n];
        for (int i = 0; i < Math.min(4096, data.length); i++) re[i] = data[i];
        new Fft(n).transform(re, im, false);
        double weighted = 0;
        double total = 0;
        for (int i = 1; i < n / 2; i++) {
            double power = re[i] * re[i] + im[i] * im[i];
            weighted += power * i * RATE / (double) n;
            total += power;
        }
        return weighted / total;
    }
}
