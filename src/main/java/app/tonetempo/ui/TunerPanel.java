package app.tonetempo.ui;

import app.tonetempo.audio.TunerEngine;
import app.tonetempo.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Guitar tuner: hears which string is played and guides it to the chosen tuning. */
final class TunerPanel extends JPanel {
    static final double IN_TUNE_CENTS = 2;
    private static final double MIN_CLARITY = .8;
    private static final long HOLD_NANOS = 400_000_000L;
    private static final long SMOOTHING_NANOS = 300_000_000L;
    private static final long TUNED_NANOS = 700_000_000L;
    private static final long SILENCE_WARNING_NANOS = 3_000_000_000L;
    private final TunerState state;
    private final TunerEngine engine;
    private final JComboBox<String> presets = new JComboBox<>();
    private final JButton reference = Theme.button("");
    private final TunerMeter meter = new TunerMeter();
    private final JPanel strings = Theme.panel(new GridLayout(1, 0, 5, 0));
    private final JButton autoButton = Theme.button("Auto");
    private final JLabel status = Theme.label("");
    private final LevelBar levelBar = new LevelBar();
    private final TuningDialog tuningDialog;
    private final Timer poll = new Timer(33, e -> poll());
    private final Deque<TunerEngine.Reading> recent = new ArrayDeque<>();
    private final Set<Integer> tuned = new HashSet<>();
    private Tuning shownTuning;
    private long lastReadingTime;
    private long lastVoiced;
    private long inTuneSince;
    private long lastSound;
    private int target = -1;
    private int pending = -1;
    private int pendingCount;
    private boolean auto = true;
    private boolean syncing;
    private boolean live;
    private boolean inTune;
    private double frequency;
    private double cents;
    private double level;
    private String buttonState = "";

    TunerPanel(JFrame owner, TunerState state, TunerEngine engine) {
        super(new BorderLayout(0, 8));
        this.state = state;
        this.engine = engine;
        setOpaque(false);
        tuningDialog = new TuningDialog(owner, state);

        var top = Theme.panel(new BorderLayout(8, 0));
        Theme.styleInput(presets);
        presets.setName("tuningPresets");
        presets.setToolTipText("Tuning to guide each string to");
        top.add(presets, BorderLayout.CENTER);
        reference.setName("referencePitch");
        reference.setFont(new Font("SansSerif", Font.PLAIN, 11));
        reference.setBorder(new EmptyBorder(6, 9, 6, 9));
        reference.setToolTipText("Reference pitch for A4");
        reference.addActionListener(e -> editReference());
        top.add(reference, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(meter, BorderLayout.CENTER);

        var bottom = Theme.panel(new BorderLayout(0, 6));
        strings.setPreferredSize(new Dimension(328, 46));
        bottom.add(strings, BorderLayout.NORTH);
        status.setFont(new Font("SansSerif", Font.PLAIN, 10));
        var statusRow = Theme.panel(new BorderLayout(10, 0));
        statusRow.add(status, BorderLayout.CENTER);
        levelBar.setToolTipText("Input level");
        statusRow.add(levelBar, BorderLayout.EAST);
        bottom.add(statusRow, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        autoButton.setName("autoString");
        autoButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        autoButton.setBorder(new EmptyBorder(4, 2, 4, 2));
        autoButton.setToolTipText("Detect which string is being played");
        autoButton.addActionListener(e -> setAuto(true));

        presets.addActionListener(e -> {
            if (syncing || presets.getSelectedIndex() < 0) return;
            if (presets.getSelectedIndex() < Tuning.PRESETS.size()) state.setTuning(Tuning.PRESETS.get(presets.getSelectedIndex()));
            else { tuningDialog.open(); sync(state.get()); }
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        sync(state.get());
    }

    void activate() {
        recent.clear();
        tuned.clear();
        if (auto) target = -1;
        pending = -1;
        pendingCount = 0;
        live = false;
        inTune = false;
        inTuneSince = 0;
        lastSound = System.nanoTime();
        engine.start();
        poll.start();
        render();
    }

    void deactivate() {
        poll.stop();
        engine.stop();
        live = false;
        inTune = false;
        render();
    }

    boolean isActive() { return poll.isRunning(); }
    boolean isAuto() { return auto; }
    boolean isLive() { return live; }
    int target() { return target; }
    double cents() { return cents; }
    double frequency() { return frequency; }

    void toggleAuto() { setAuto(!auto); }

    void selectNeighbour(int delta) {
        int count = state.get().tuning().strings();
        int from = target < 0 ? (delta > 0 ? -1 : 0) : target;
        auto = false;
        target = Math.floorMod(from + delta, count);
        inTuneSince = 0;
        render();
    }

    void dispose() { tuningDialog.dispose(); }

    private void setAuto(boolean value) {
        auto = value;
        if (!auto && target < 0) target = 0;
        pending = -1;
        pendingCount = 0;
        render();
    }

    private void lock(int string) {
        if (!auto && target == string) { setAuto(true); return; }
        auto = false;
        target = string;
        inTuneSince = 0;
        render();
    }

    private void poll() {
        long now = System.nanoTime();
        var reading = engine.reading();
        if (reading.nanoTime() != lastReadingTime) {
            lastReadingTime = reading.nanoTime();
            level = Math.clamp((reading.level() + 60) / 60, 0, 1);
            if (reading.level() > -80) lastSound = now;
            if (reading.voiced() && reading.clarity() >= MIN_CLARITY) {
                recent.addLast(reading);
                lastVoiced = now;
            }
        }
        while (!recent.isEmpty() && now - recent.peekFirst().nanoTime() > SMOOTHING_NANOS) recent.removeFirst();
        var settings = state.get();
        var tuning = settings.tuning();
        live = now - lastVoiced < HOLD_NANOS && !recent.isEmpty();
        if (live) {
            frequency = median();
            if (auto) {
                int nearest = tuning.nearest(frequency, settings.referencePitch());
                if (nearest == pending) pendingCount++;
                else { pending = nearest; pendingCount = 1; }
                if (target < 0 || pendingCount >= 3) target = nearest;
            }
            target = Math.min(target, tuning.strings() - 1);
            cents = tuning.match(target, frequency, settings.referencePitch()).cents();
            inTune = Math.abs(cents) <= IN_TUNE_CENTS;
            if (!inTune) inTuneSince = 0;
            else if (inTuneSince == 0) inTuneSince = now;
            else if (now - inTuneSince >= TUNED_NANOS) tuned.add(target);
        } else {
            inTune = false;
            inTuneSince = 0;
        }
        render();
    }

    private double median() {
        double[] values = recent.stream().mapToDouble(TunerEngine.Reading::frequency).sorted().toArray();
        int n = values.length;
        return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2;
    }

    private void render() {
        var settings = state.get();
        var tuning = settings.tuning();
        String error = engine.errorMessage();
        int shown = target >= 0 && target < tuning.strings() ? target : -1;
        String letter = shown < 0 ? "—" : Pitch.letter(tuning.note(shown));
        String octave = shown < 0 ? "" : Integer.toString(Pitch.octave(tuning.note(shown)));
        String detail = shown < 0 ? (auto ? "Play any string" : "")
                : tuning.stringLabel(shown) + "  ·  target " + String.format("%.2f Hz", tuning.frequency(shown, settings.referencePitch()));
        String hint;
        Color hintColor = Theme.MUTED;
        if (!error.isBlank()) { hint = "Microphone unavailable"; hintColor = Theme.WARNING; }
        else if (live && inTune) { hint = "IN TUNE  ✓"; hintColor = Theme.ACCENT; }
        else if (live) { hint = cents < 0 ? "TUNE UP  ▲" : "TUNE DOWN  ▼"; hintColor = Theme.WARNING; }
        else if (!engine.isRunning()) hint = "Microphone off";
        else if (shown >= 0 && !auto) hint = "Play the " + tuning.stringLabel(shown);
        else hint = "Play a string";
        meter.setView(new TunerMeter.View(letter, octave, detail, live, live && inTune, cents, frequency, hint, hintColor));
        levelBar.set(engine.isRunning() ? level : 0, live);

        String device = engine.deviceName().isBlank() ? settings.inputName() : engine.deviceName();
        if (engine.inputChannelCount() > 1) device += "  ·  input " + engine.inputChannel();
        if (!error.isBlank()) status.setText("⚠ " + error);
        else if (!engine.isRunning()) status.setText("The microphone is used only while the tuner is showing.");
        else if (System.nanoTime() - lastSound > SILENCE_WARNING_NANOS)
            status.setText("No signal from " + device + " — check its gain, the input channel or the microphone permission.");
        else status.setText("Listening on " + device);
        status.setForeground(error.isBlank() ? Theme.MUTED : Theme.WARNING);

        String key = auto + "/" + shown + "/" + tuned + "/" + tuning.notes() + "/" + settings.referencePitch();
        if (!key.equals(buttonState)) {
            buttonState = key;
            paintStrings(tuning, shown, settings.referencePitch());
        }
    }

    private void paintStrings(Tuning tuning, int shown, double a4) {
        strings.removeAll();
        autoButton.setBackground(auto ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
        autoButton.setForeground(auto ? Theme.BACKGROUND : Theme.MUTED);
        strings.add(autoButton);
        for (int i = 0; i < tuning.strings(); i++) {
            int string = i;
            boolean done = tuned.contains(i);
            var button = Theme.button("<html><center><span style='font-size:12px'>" + Pitch.name(tuning.note(i))
                    + "</span><br><span style='font-size:8px'>" + (done ? "✓ " : "")
                    + Tuning.ordinal(tuning.strings() - i) + "</span></center></html>");
            button.setName("string." + i);
            button.setBorder(new EmptyBorder(3, 1, 3, 1));
            button.setToolTipText(tuning.stringLabel(i) + " · " + String.format("%.2f Hz", tuning.frequency(i, a4))
                    + " · click to tune only this string");
            button.setBackground(i == shown ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
            button.setForeground(i == shown ? Theme.BACKGROUND : done ? Theme.ACCENT : Theme.TEXT);
            button.addActionListener(e -> lock(string));
            strings.add(button);
        }
        strings.revalidate();
        strings.repaint();
    }

    private void sync(TunerSettings settings) {
        syncing = true;
        var tuning = settings.tuning();
        var model = new DefaultComboBoxModel<String>();
        for (var preset : Tuning.PRESETS) model.addElement(preset.label());
        model.addElement(tuning.isPreset() ? "Custom tuning…" : tuning.label());
        presets.setModel(model);
        int index = Tuning.PRESETS.indexOf(tuning);
        presets.setSelectedIndex(index >= 0 ? index : Tuning.PRESETS.size());
        reference.setText("A4 " + formatHz(settings.referencePitch()));
        if (shownTuning != null && !shownTuning.notes().equals(tuning.notes())) {
            tuned.clear();
            if (auto) target = -1;
        }
        shownTuning = tuning;
        if (target >= tuning.strings()) target = tuning.strings() - 1;
        syncing = false;
        // A new input device or any other change is a reason to retry after a microphone failure.
        if (isActive() && !engine.isRunning()) engine.start();
        render();
    }

    private void editReference() {
        var input = new JSpinner(new SpinnerNumberModel(state.get().referencePitch(), 400.0, 480.0, .5));
        input.setEditor(new JSpinner.NumberEditor(input, "0.0"));
        if (JOptionPane.showConfirmDialog(this, input, "Reference pitch for A4 (Hz)", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                input.commitEdit();
                state.setReferencePitch(((Number) input.getValue()).doubleValue());
            } catch (java.text.ParseException ignored) { }
        }
    }

    private static String formatHz(double hz) {
        return (hz == Math.rint(hz) ? String.format("%.0f", hz) : String.format("%.1f", hz)) + " Hz";
    }

    /** Input level from -60 dBFS to full scale. */
    private static final class LevelBar extends JComponent {
        private double level;
        private boolean active;
        LevelBar() { setPreferredSize(new Dimension(64, 14)); }
        void set(double value, boolean isActive) {
            if (Math.abs(value - level) < .01 && isActive == active) return;
            level = value;
            active = isActive;
            repaint();
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = getHeight() / 2 - 2;
            g.setColor(Theme.PANEL_LIGHT);
            g.fillRoundRect(0, y, getWidth(), 5, 5, 5);
            int fill = (int) Math.round(Math.clamp(level, 0, 1) * getWidth());
            if (fill > 0) {
                g.setColor(active ? Theme.ACCENT_2 : Theme.BORDER);
                g.fillRoundRect(0, y, fill, 5, 5, 5);
            }
            g.dispose();
        }
    }
}
