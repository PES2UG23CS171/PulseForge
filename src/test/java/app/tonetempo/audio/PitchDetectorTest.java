package app.tonetempo.audio;

import app.tonetempo.model.Pitch;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PitchDetectorTest {
    private static final int WINDOW = 4096;
    private static final int BUFFER = WINDOW * PitchDetector.HISTORY_WINDOWS;
    private static final List<Integer> STANDARD = List.of(40, 45, 50, 55, 59, 64);

    @Test void openStringsReadWithinHalfACentAtBothCommonRates() {
        for (int rate : new int[] {48_000, 44_100}) {
            var detector = new PitchDetector(rate, WINDOW);
            for (int midi : STANDARD) {
                for (double detune : new double[] {-30, -7, 0, 3, 25}) {
                    double target = Pitch.frequency(midi, 440) * Math.pow(2, detune / 1200);
                    var voice = midi < 55 ? SyntheticGuitar.Voice.wound(target) : SyntheticGuitar.Voice.clean(target);
                    for (double start : new double[] {.05, .3, .8}) {
                        var result = detector.detect(SyntheticGuitar.tone(rate, voice, BUFFER, start));
                        assertTrue(result.voiced(), "Unvoiced at " + midi + " " + detune + "c " + rate);
                        double error = Pitch.cents(result.frequency(), target);
                        assertTrue(Math.abs(error) <= .5, String.format(
                                "MIDI %d detuned %.0fc at %d Hz from %.2fs: error %.3f cents", midi, detune, rate, start, error));
                        assertTrue(result.clarity() > .9, "Low clarity " + result.clarity());
                    }
                }
            }
        }
    }

    @Test void weakFundamentalsReadWithinACentWithoutOctaveErrors() {
        var detector = new PitchDetector(48_000, WINDOW);
        for (int midi : new int[] {33, 35, 38, 40, 45, 50, 59, 64}) {
            double target = Pitch.frequency(midi, 440);
            for (double start : new double[] {.05, .4, 1.1}) {
                var result = detector.detect(SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.thin(target), BUFFER, start));
                assertTrue(result.voiced());
                double error = Pitch.cents(result.frequency(), target);
                assertTrue(Math.abs(error) <= 1, String.format("MIDI %d from %.2fs: error %.3f cents", midi, start, error));
            }
        }
    }

    @Test void fundamentalIsReportedEvenWhenStiffPartialsRingSharp() {
        var detector = new PitchDetector(48_000, WINDOW);
        for (int midi : new int[] {40, 59}) {
            double target = Pitch.frequency(midi, 440);
            var stiff = new SyntheticGuitar.Voice(target, .5, 8e-4, .005, 3);
            var result = detector.detect(SyntheticGuitar.tone(48_000, stiff, BUFFER, .05));
            double error = Pitch.cents(result.frequency(), target);
            double coarseError = Pitch.cents(48_000 / detector.trace().coarse(), target);
            assertTrue(coarseError > 3, "Test tone should pull the plain autocorrelation sharp; got " + coarseError);
            assertTrue(Math.abs(error) <= .5, "Stiff string error " + error + " cents");
        }
    }

    @Test void silenceAndNoiseAreUnvoiced() {
        var detector = new PitchDetector(48_000, WINDOW);
        assertFalse(detector.detect(new float[BUFFER]).voiced());
        var random = new Random(5);
        float[] noise = new float[BUFFER];
        for (int i = 0; i < BUFFER; i++) noise[i] = (float) (random.nextGaussian() * .1);
        var result = detector.detect(noise);
        assertFalse(result.voiced(), "Noise read as " + result.frequency() + " Hz with clarity " + result.clarity());
        assertTrue(result.level() > -40);
    }

    @Test void analysisUsesTheNewestWindowOfTheBuffer() {
        var detector = new PitchDetector(48_000, WINDOW);
        double target = 220;
        float[] buffer = SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.clean(target), BUFFER, .1);
        for (int i = 0; i < BUFFER - WINDOW; i++) buffer[i] = 0;
        var result = detector.detect(buffer);
        assertTrue(result.voiced());
        assertTrue(Math.abs(Pitch.cents(result.frequency(), target)) <= 2, "Error " + Pitch.cents(result.frequency(), target));
        var exact = detector.detect(SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.clean(target), WINDOW, .1));
        assertTrue(exact.voiced(), "A single window must still be analyzable");
    }

    @Test void mainsHumIsIgnoredButStringsNearItAreNot() {
        var detector = new PitchDetector(48_000, WINDOW);
        for (double mains : new double[] {50.03, 59.97}) {
            float[] hum = new float[BUFFER];
            for (int i = 0; i < BUFFER; i++) hum[i] = (float) (Math.sin(2 * Math.PI * mains * i / 48_000) * .02);
            var result = detector.detect(hum);
            assertFalse(result.voiced(), "Hum at " + mains + " Hz read as " + result.frequency());
            assertTrue(result.level() < -50, "Hum should be notched out; level " + result.level());
        }
        double lowB = Pitch.frequency(35, 440);
        float[] string = SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.wound(lowB), BUFFER, .1);
        for (int i = 0; i < BUFFER; i++) string[i] += (float) (Math.sin(2 * Math.PI * 50.03 * i / 48_000) * .02);
        var result = detector.detect(string);
        assertTrue(result.voiced(), "A low B next to hum must still read");
        assertTrue(Math.abs(Pitch.cents(result.frequency(), lowB)) <= 1, "Low B with hum: " + Pitch.cents(result.frequency(), lowB));
        float[] quiet = SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.clean(220), BUFFER, .1);
        for (int i = 0; i < BUFFER; i++) quiet[i] *= .001f;
        assertFalse(detector.detect(quiet).voiced(), "Signals below -60 dBFS are silence");
    }

    @Test void detectionIsFastEnoughForLiveUse() {
        var detector = new PitchDetector(48_000, WINDOW);
        float[] tone = SyntheticGuitar.tone(48_000, SyntheticGuitar.Voice.wound(82.4), BUFFER, .1);
        detector.detect(tone);
        long started = System.nanoTime();
        for (int i = 0; i < 50; i++) detector.detect(tone);
        double millisPerFrame = (System.nanoTime() - started) / 50 / 1e6;
        assertTrue(millisPerFrame < 15, "Detection took " + millisPerFrame + " ms per frame");
    }
}
