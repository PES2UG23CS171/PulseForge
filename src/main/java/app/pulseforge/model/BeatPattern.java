package app.pulseforge.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BeatPattern(Subdivision division, List<Boolean> hits) {
    public BeatPattern {
        division = division == null ? Subdivision.QUARTER : division;
        var safe = new ArrayList<Boolean>();
        for (int i = 0; i < division.steps(); i++)
            safe.add(hits == null || i >= hits.size() || !Boolean.FALSE.equals(hits.get(i)));
        hits = List.copyOf(safe);
    }
    public static BeatPattern straight() { return all(Subdivision.QUARTER); }
    public static BeatPattern all(Subdivision division) {
        return new BeatPattern(division, Collections.nCopies(division.steps(), true));
    }
    public BeatPattern toggle(int hit) {
        if (hit < 0 || hit >= hits.size()) return this;
        var next = new ArrayList<>(hits);
        next.set(hit, !next.get(hit));
        return new BeatPattern(division, next);
    }
    public int steps() { return division.steps(); }
}
