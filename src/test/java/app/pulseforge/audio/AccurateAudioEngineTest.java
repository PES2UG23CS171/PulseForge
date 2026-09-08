package app.pulseforge.audio;

import app.pulseforge.model.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccurateAudioEngineTest {
    private static final int RATE = 48000;

    @Test void mixedBarPlacesTripletCrotchetSemiquaversAndCrotchetAtExactFrames() {
        var settings = mixed(120);
        var clock = new BeatClock(RATE, settings, BeatClock.Position.beginning());
        var events = new ArrayList<BeatClock.Event>();
        for (int i = 0; i < 375; i++) events.addAll(clock.advance(256, settings));
        assertEquals(List.of(0L, 8000L, 16000L, 24000L, 48000L, 54000L, 60000L, 66000L, 72000L),
                events.stream().map(BeatClock.Event::frame).toList());
        assertEquals(List.of(0, 0, 0, 1, 2, 2, 2, 2, 3),
                events.stream().map(BeatClock.Event::beat).toList());
        assertEquals(96000, clock.advance(256, settings).getFirst().frame());
    }

    @Test void restsAndMutedBeatsKeepTheirTimeWithoutProducingClicks() {
        var patterns = new ArrayList<>(mixed(120).patterns());
        patterns.set(0, patterns.get(0).toggle(1));
        var s = new MetronomeSettings(120, 4, 4, .7, SoundType.STUDIO, .5, "", "",
                List.of(Accent.STRONG, Accent.MUTED, Accent.NORMAL, Accent.NORMAL), patterns);
        var events = new BeatClock(RATE, s, BeatClock.Position.beginning()).advance(96000, s);
        assertFalse(events.get(1).audible());
        assertFalse(events.get(3).audible());
        assertEquals(48000, events.get(4).frame());
        assertTrue(events.get(4).audible());
    }

    @Test void fractionalSampleRoundingDoesNotAccumulateOverAnHour() {
        var s = mixed(137.3);
        var clock = new BeatClock(RATE, s, BeatClock.Position.beginning());
        long hour = RATE * 3600L;
        long beatNumber = 0;
        double maximumError = 0;
        while (clock.cursor() < hour) {
            for (var event : clock.advance(8192, s)) {
                if (event.step() == 0) {
                    double ideal = beatNumber++ * RATE * 60.0 / s.bpm();
                    maximumError = Math.max(maximumError, Math.abs(event.frame() - ideal));
                }
            }
        }
        assertTrue(maximumError <= .501, "Sample-grid error: " + maximumError);
        assertTrue(beatNumber > 8000);
    }

    @Test void pauseResumeRetainsRemainingTimeInTheBeat() {
        var s = mixed(120);
        var clock = new BeatClock(RATE, s, new BeatClock.Position(2, .30));
        var events = clock.advance(24000, s);
        assertEquals(4800, events.get(0).frame());
        assertEquals(2, events.get(0).step());
        assertEquals(10800, events.get(1).frame());
        assertEquals(16800, events.get(2).frame());
        assertEquals(3, events.get(2).beat());
    }

    @Test void allTimeSignatureNoteValuesUseTheExplicitBeatTempo() {
        for (int unit : MetronomeSettings.NOTE_VALUES) {
            var s = new MetronomeSettings(120, 4, unit, .7, SoundType.STUDIO, .5, "", "", null, null);
            var events = new BeatClock(RATE, s, BeatClock.Position.beginning()).advance(96000, s);
            assertEquals(List.of(0L, 24000L, 48000L, 72000L), events.stream().map(BeatClock.Event::frame).toList());
        }
    }

    @Test void swingKeepsBeatBoundariesAndTempoChangesTakeEffectAtNextBeat() {
        var s = new MetronomeSettings(120, 1, 4, .7, SoundType.STUDIO, .60, "", "",
                null, List.of(BeatPattern.all(Subdivision.SIXTEENTH)));
        var clock = new BeatClock(RATE, s, BeatClock.Position.beginning());
        assertEquals(List.of(0L, 7200L, 12000L, 19200L),
                clock.advance(24000, s).stream().map(BeatClock.Event::frame).toList());
        var faster = new MetronomeSettings(240, 1, 4, .7, SoundType.STUDIO, .5, "", "",
                null, List.of(BeatPattern.straight()));
        var events = clock.advance(24000, faster);
        assertEquals(List.of(24000L, 36000L), events.stream().map(BeatClock.Event::frame).toList());
    }

    private static MetronomeSettings mixed(double bpm) {
        return new MetronomeSettings(bpm, 4, 4, .7, SoundType.STUDIO, .5, "System Default", "", null,
                List.of(BeatPattern.all(Subdivision.TRIPLET), BeatPattern.straight(),
                        BeatPattern.all(Subdivision.SIXTEENTH), BeatPattern.straight()));
    }
}
