package app.pulseforge.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetronomeSettingsTest {
    @Test
    void valuesAreClampedToSafeProfessionalRanges() {
        var settings = new MetronomeSettings(900, Subdivision.QUARTER, 50, 3, -2,
                SoundType.STUDIO, .99, null, null, List.of(Accent.STRONG));
        assertEquals(300, settings.bpm());
        assertEquals(12, settings.beatsPerBar());
        assertEquals(4, settings.beatUnit());
        assertEquals(0, settings.volume());
        assertEquals(.75, settings.swing());
        assertEquals("System Default", settings.mixerName());
        assertEquals("", settings.customSamplePath());
    }
}
