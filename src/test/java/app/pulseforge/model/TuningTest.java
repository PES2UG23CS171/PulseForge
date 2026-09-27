package app.pulseforge.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TuningTest {
    @Test void namesAndFrequenciesFollowEqualTemperament() {
        assertEquals("E2", Pitch.name(40));
        assertEquals("F♯3", Pitch.name(54));
        assertEquals("A4", Pitch.name(69));
        assertEquals(440, Pitch.frequency(69, 440), 1e-9);
        assertEquals(82.4069, Pitch.frequency(40, 440), 1e-4);
        assertEquals(83.1560, Pitch.frequency(40, 444), 1e-4);
        assertEquals(100, Pitch.cents(Pitch.frequency(41, 440), Pitch.frequency(40, 440)), 1e-9);
        assertEquals(-50, Pitch.cents(Pitch.frequency(40, 440) * Math.pow(2, -50 / 1200.0), Pitch.frequency(40, 440)), 1e-9);
    }

    @Test void nearestStringAndCentsGuideTowardsTheTarget() {
        var standard = Tuning.STANDARD;
        assertEquals("Standard  ·  E A D G B E", standard.label());
        assertEquals("6th string", standard.stringLabel(0));
        assertEquals("1st string", standard.stringLabel(5));
        var flatLowE = standard.match(80.5, 440);
        assertEquals(0, flatLowE.string());
        assertEquals(40, flatLowE.note());
        assertEquals(-40.6, flatLowE.cents(), .1);
        assertFalse(flatLowE.inTune(2));
        var sharpG = standard.match(197, 440);
        assertEquals(3, sharpG.string());
        assertEquals(8.8, sharpG.cents(), .1);
        assertTrue(standard.match(329.9, 440).inTune(2));
        assertEquals(1, standard.nearest(Pitch.frequency(43, 440), 440));
        assertEquals(5, standard.nearest(Pitch.frequency(66, 440), 440));
        assertEquals("7th string", Tuning.preset("7-string standard").stringLabel(0));
    }

    @Test void presetsResolveByNotesAndCustomEditsKeepTheirName() {
        assertSame(Tuning.STANDARD, Tuning.of(List.of(40, 45, 50, 55, 59, 64)));
        var dropD = Tuning.STANDARD.withNote(0, 38);
        assertEquals("Drop D", dropD.name());
        assertTrue(dropD.isPreset());
        var custom = dropD.withNote(5, 62).withNote(4, 60);
        assertEquals(Tuning.CUSTOM, custom.name());
        assertFalse(custom.isPreset());
        assertEquals(List.of(38, 45, 50, 55, 60, 62), custom.notes());
        assertEquals(Tuning.STANDARD, Tuning.preset("unknown"));
        assertEquals(List.of(33, 69, 40), new Tuning(null, List.of(10, 100, 40)).notes());
        assertEquals(Tuning.STANDARD.notes(), new Tuning("x", List.of()).notes());
        assertEquals("11th", Tuning.ordinal(11));
        assertEquals("2nd", Tuning.ordinal(2));
    }

    @Test void tunerSettingsSurviveReloadAndAreNormalized() {
        var prefs = new MemoryPreferences();
        var state = new TunerState(prefs);
        assertEquals(Tuning.STANDARD, state.get().tuning());
        assertEquals(TunerSettings.DEFAULT_INPUT, state.get().inputName());
        state.setTuning(Tuning.preset("Open G"));
        state.setNote(5, 64);
        state.setReferencePitch(442.26);
        state.setInputName("USB Interface");
        state.setInputChannel(2);
        state.setNote(9, 40);
        var loaded = new TunerState(prefs).get();
        assertEquals(List.of(38, 43, 50, 55, 59, 64), loaded.tuning().notes());
        assertEquals(Tuning.CUSTOM, loaded.tuning().name());
        assertEquals(442.3, loaded.referencePitch(), 1e-9);
        assertEquals("USB Interface", loaded.inputName());
        assertEquals(2, loaded.inputChannel());
        var wild = new TunerSettings("", -3, null, Double.NaN);
        assertEquals(TunerSettings.AUTO_CHANNEL, wild.inputChannel());
        assertEquals(Tuning.STANDARD, wild.tuning());
        assertEquals(440, wild.referencePitch());
        assertEquals(480, new TunerSettings(null, 99, null, 900).referencePitch());
        assertEquals(TunerSettings.MAX_CHANNELS, new TunerSettings(null, 99, null, 900).inputChannel());
        assertEquals("Open G", new TunerSettings(null, 0, new Tuning("misnamed", List.of(38, 43, 50, 55, 59, 62)), 440).tuning().name());
    }
}
