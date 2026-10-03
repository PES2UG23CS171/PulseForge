package app.tonetempo.audio;

import app.tonetempo.model.MemoryPreferences;
import app.tonetempo.model.Pitch;
import app.tonetempo.model.TunerState;
import org.junit.jupiter.api.Test;
import javax.sound.sampled.LineUnavailableException;
import static org.junit.jupiter.api.Assertions.*;

class TunerEngineTest {
    @Test void streamsReadingsFromTheSelectedSourceUntilStopped() throws Exception {
        var state = new TunerState(new MemoryPreferences());
        double target = Pitch.frequency(45, 440) * Math.pow(2, -12 / 1200.0);
        try (var engine = new TunerEngine(state, (name, channel) ->
                new SyntheticGuitar.Source(48_000, SyntheticGuitar.Voice.wound(target)))) {
            engine.start();
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (!engine.reading().voiced() && System.nanoTime() < deadline) Thread.sleep(10);
            var reading = engine.reading();
            assertTrue(reading.voiced(), "No reading; error: " + engine.errorMessage());
            assertEquals("Synthetic guitar", engine.deviceName());
            assertTrue(Math.abs(Pitch.cents(reading.frequency(), target)) <= 1, "Read " + reading.frequency());
            engine.stop();
            assertFalse(engine.isRunning());
            Thread.sleep(80);
            assertFalse(engine.reading().voiced());
        }
    }

    @Test void reportsInputFailuresInsteadOfRunning() throws Exception {
        var state = new TunerState(new MemoryPreferences());
        try (var engine = new TunerEngine(state, (name, channel) -> { throw new LineUnavailableException("Selected input is disconnected."); })) {
            engine.start();
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (engine.isRunning() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(engine.isRunning());
            assertEquals("Selected input is disconnected.", engine.errorMessage());
        }
    }
}
