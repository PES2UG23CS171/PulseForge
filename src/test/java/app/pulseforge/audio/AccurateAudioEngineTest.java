package app.pulseforge.audio;

import app.pulseforge.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccurateAudioEngineTest {
    @Test
    void quarterNoteAt120BpmIsExactlyHalfASecond() {
        var settings = settings(120, Subdivision.QUARTER, .5);
        assertEquals(24_000, AccurateAudioEngine.intervalFrames(settings, 0), 0.000_001);
    }

    @Test
    void sixteenthSubdivisionDoesNotAccumulateIntegerRounding() {
        var settings = settings(137, Subdivision.SIXTEENTH, .5);
        double exact = 48_000 * 60.0 / 137 / 4;
        assertEquals(exact, AccurateAudioEngine.intervalFrames(settings, 9), 0.000_001);
    }

    @Test
    void swingPairsAlwaysAddToTwoStraightIntervals() {
        var settings = settings(120, Subdivision.SIXTEENTH, .60);
        double longPart = AccurateAudioEngine.intervalFrames(settings, 0);
        double shortPart = AccurateAudioEngine.intervalFrames(settings, 1);
        assertEquals(12_000, longPart + shortPart, 0.000_001);
        assertEquals(7_200, longPart, 0.000_001);
        assertEquals(4_800, shortPart, 0.000_001);
    }

    private static MetronomeSettings settings(double bpm, Subdivision subdivision, double swing) {
        return new MetronomeSettings(bpm, subdivision, 4, 4, .7, SoundType.STUDIO, swing,
                "System Default", "", List.of(Accent.STRONG, Accent.NORMAL, Accent.NORMAL, Accent.NORMAL));
    }
}
