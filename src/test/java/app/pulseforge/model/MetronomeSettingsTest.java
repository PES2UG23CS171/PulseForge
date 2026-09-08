package app.pulseforge.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MetronomeSettingsTest {
    @Test void valuesAndPatternLengthsAreNormalized() {
        var s = new MetronomeSettings(900, 50, 3, -2, SoundType.STUDIO, .99,
                null, null, List.of(Accent.MUTED), List.of(BeatPattern.all(Subdivision.TRIPLET)));
        assertEquals(300, s.bpm());
        assertEquals(12, s.beatsPerBar());
        assertEquals(4, s.beatUnit());
        assertEquals(0, s.volume());
        assertEquals(.75, s.swing());
        assertEquals(12, s.accents().size());
        assertEquals(12, s.patterns().size());
        assertEquals(Accent.MUTED, s.accents().getFirst());
        assertEquals(Subdivision.TRIPLET, s.patterns().getFirst().division());
        assertEquals(Subdivision.QUARTER, s.patterns().getLast().division());
    }

    @Test void beatEditsAreIndependentAndSurviveReload() {
        var prefs = new MemoryPreferences();
        var state = new MetronomeState(prefs);
        state.setPattern(0, Subdivision.TRIPLET);
        state.setPattern(2, Subdivision.SIXTEENTH);
        state.toggleHit(2, 1);
        state.setMeter(4, 32);
        var loaded = new MetronomeState(prefs).get();
        assertEquals(32, loaded.beatUnit());
        assertEquals(Subdivision.TRIPLET, loaded.patterns().get(0).division());
        assertEquals(Subdivision.QUARTER, loaded.patterns().get(1).division());
        assertEquals(List.of(true, false, true, true), loaded.patterns().get(2).hits());
        assertEquals(Subdivision.QUARTER, loaded.patterns().get(3).division());
    }

    @Test void shrinkingThenExpandingKeepsSurvivorsAndCreatesStraightNewBeats() {
        var state = new MetronomeState(new MemoryPreferences());
        state.setPattern(0, Subdivision.TRIPLET);
        state.setPattern(3, Subdivision.SEXTUPLET);
        state.setMeter(2, 16);
        state.setMeter(6, 2);
        assertEquals(Subdivision.TRIPLET, state.get().patterns().getFirst().division());
        assertEquals(Subdivision.QUARTER, state.get().patterns().get(3).division());
        assertEquals(6, state.get().accents().size());
    }

    @Test void oldGlobalRhythmPreferencesMigrateToEachBeat() {
        var prefs = new MemoryPreferences();
        prefs.putDouble("bpm", 120.7);
        prefs.put("subdivision", "EIGHTH");
        prefs.putInt("unit", 8);
        var state = new MetronomeState(prefs);
        assertEquals(121, state.get().bpm());
        assertTrue(state.get().patterns().stream().allMatch(p -> p.division() == Subdivision.EIGHTH));
        state.setPattern(0, Subdivision.TRIPLET);
        state.setBpm(119.2);
        assertEquals(119, new MetronomeState(prefs).get().bpm());
        assertEquals(119, prefs.getInt("bpm", 0));
        assertEquals(Subdivision.TRIPLET, new MetronomeState(prefs).get().patterns().getFirst().division());
    }
}
