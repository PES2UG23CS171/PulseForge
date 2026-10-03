package app.tonetempo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public final class TunerState {
    private final Preferences prefs;
    private final AtomicReference<TunerSettings> settings;
    private final List<Consumer<TunerSettings>> listeners = new CopyOnWriteArrayList<>();

    public TunerState() { this(Preferences.userNodeForPackage(TunerState.class)); }

    public TunerState(Preferences prefs) {
        this.prefs = prefs;
        settings = new AtomicReference<>(load());
    }

    public TunerSettings get() { return settings.get(); }
    public void addListener(Consumer<TunerSettings> listener) { listeners.add(listener); }

    private void publish(TunerSettings next) {
        settings.set(next);
        save(next);
        listeners.forEach(listener -> listener.accept(next));
    }

    public void setInputName(String name) {
        var s = get();
        publish(new TunerSettings(name, s.inputChannel(), s.tuning(), s.referencePitch()));
    }

    /** @param channel 1-based input channel, or {@link TunerSettings#AUTO_CHANNEL} to follow the signal */
    public void setInputChannel(int channel) {
        var s = get();
        publish(new TunerSettings(s.inputName(), channel, s.tuning(), s.referencePitch()));
    }

    public void setTuning(Tuning tuning) {
        var s = get();
        publish(new TunerSettings(s.inputName(), s.inputChannel(), tuning, s.referencePitch()));
    }

    public void setNote(int string, int midi) {
        var s = get();
        if (string < 0 || string >= s.tuning().strings()) return;
        publish(new TunerSettings(s.inputName(), s.inputChannel(), s.tuning().withNote(string, midi), s.referencePitch()));
    }

    public void setReferencePitch(double hz) {
        var s = get();
        publish(new TunerSettings(s.inputName(), s.inputChannel(), s.tuning(), hz));
    }

    private TunerSettings load() {
        var notes = new ArrayList<Integer>();
        for (var part : prefs.get("tuner.strings", "").split(",")) {
            try { notes.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) { }
        }
        var tuning = notes.isEmpty() ? Tuning.STANDARD : Tuning.of(notes);
        return new TunerSettings(prefs.get("tuner.input", TunerSettings.DEFAULT_INPUT),
                prefs.getInt("tuner.channel", TunerSettings.AUTO_CHANNEL), tuning,
                prefs.getDouble("tuner.a4", Pitch.DEFAULT_A4));
    }

    private void save(TunerSettings s) {
        prefs.put("tuner.input", s.inputName());
        prefs.putInt("tuner.channel", s.inputChannel());
        prefs.put("tuner.strings", s.tuning().notes().stream().map(String::valueOf).collect(Collectors.joining(",")));
        prefs.putDouble("tuner.a4", s.referencePitch());
    }
}
