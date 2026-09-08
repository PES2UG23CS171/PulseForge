package app.pulseforge.audio;

import app.pulseforge.model.*;
import java.util.ArrayList;
import java.util.List;

/** Places unequal per-beat patterns on one continuous sample timeline. */
public final class BeatClock {
    public record Position(int beat, double phase) {
        public Position {
            beat = Math.max(0, beat);
            phase = Math.clamp(phase, 0, Math.nextDown(1.0));
        }
        public static Position beginning() { return new Position(0, 0); }
    }
    public record Event(long frame, int beat, int step, double beatStart,
                        double beatFrames, boolean audible, Accent accent) {
        public Position positionAt(long playedFrame) {
            return new Position(beat, (playedFrame - beatStart) / beatFrames);
        }
    }

    private final int sampleRate;
    private long cursor;
    private int beat;
    private int step;
    private double beatStart;
    private double beatFrames;
    private BeatPattern pattern;
    private Accent accent;
    private double swing;

    public BeatClock(int sampleRate, MetronomeSettings settings, Position start) {
        this.sampleRate = sampleRate;
        beat = Math.min(start.beat(), settings.beatsPerBar() - 1);
        readBeat(settings);
        beatStart = -start.phase() * beatFrames;
        while (step < pattern.steps() && eventFrame() < 0) step++;
    }

    public Event anchor() {
        return new Event(0, beat, Math.max(0, step - 1), beatStart, beatFrames, false, accent);
    }

    public long cursor() { return cursor; }

    public List<Event> advance(int frames, MetronomeSettings settings) {
        var events = new ArrayList<Event>();
        long end = cursor + frames;
        if (beat >= settings.beatsPerBar()) {
            beat = 0; step = 0; beatStart = cursor; readBeat(settings);
        }
        while (true) {
            if (step == pattern.steps()) {
                double boundary = beatStart + beatFrames;
                if (Math.round(boundary) >= end) break;
                beatStart = boundary;
                beat = (beat + 1) % settings.beatsPerBar();
                step = 0;
                readBeat(settings);
            }
            long frame = Math.round(eventFrame());
            if (frame >= end) break;
            events.add(new Event(frame, beat, step, beatStart, beatFrames,
                    accent != Accent.MUTED && pattern.hits().get(step), accent));
            step++;
        }
        cursor = end;
        return events;
    }

    private void readBeat(MetronomeSettings settings) {
        beatFrames = settings.beatFrames(sampleRate);
        pattern = settings.patterns().get(beat);
        accent = settings.accents().get(beat);
        swing = settings.swing();
    }

    private double eventFrame() {
        double fraction = (double) step / pattern.steps();
        if (pattern.steps() % 2 == 0 && step % 2 == 1)
            fraction = (step - 1 + 2 * swing) / pattern.steps();
        return beatStart + fraction * beatFrames;
    }
}

