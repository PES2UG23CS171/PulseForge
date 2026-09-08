package app.pulseforge.audio;

import app.pulseforge.model.*;
import java.util.function.BooleanSupplier;

/** Optional local device smoke test. Uses zero volume and isolated preferences. */
public final class AudioVerification {
    public static void main(String[] args) throws Exception {
        var state = new MetronomeState(new MemoryPreferences());
        state.setVolume(0);
        state.setPattern(0, Subdivision.TRIPLET);
        state.setPattern(2, Subdivision.SIXTEENTH);
        try (var engine = new AccurateAudioEngine(state)) {
            engine.play();
            await(() -> engine.position().phase() > .15, engine);
            engine.pause();
            var paused = engine.position();
            Thread.sleep(100);
            if (!paused.equals(engine.position())) throw new AssertionError("Paused cursor moved");
            engine.play();
            await(() -> !engine.position().equals(paused), engine);
            for (int i = 0; i < 12; i++) {
                engine.pause();
                engine.play();
            }
            await(() -> engine.position().phase() > .3, engine);
            engine.pause();
            if (!engine.errorMessage().isBlank()) throw new AssertionError(engine.errorMessage());
            engine.stop();
            if (!engine.position().equals(BeatClock.Position.beginning())) throw new AssertionError("Stop did not reset");
            System.out.println("Audio device verified: playback advances; pause freezes; resume and rapid toggles succeed.");
        }
        System.exit(0);
    }

    private static void await(BooleanSupplier condition, AccurateAudioEngine engine) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            if (!engine.errorMessage().isBlank()) throw new AssertionError(engine.errorMessage());
            Thread.sleep(15);
        }
        if (!condition.getAsBoolean()) throw new AssertionError("Audio cursor did not advance");
    }
}

