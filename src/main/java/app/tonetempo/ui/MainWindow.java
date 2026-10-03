package app.tonetempo.ui;

import app.tonetempo.audio.AccurateAudioEngine;
import app.tonetempo.audio.TransportState;
import app.tonetempo.audio.TunerEngine;
import app.tonetempo.model.MetronomeSettings;
import app.tonetempo.model.MetronomeState;
import app.tonetempo.model.TunerState;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

public final class MainWindow extends JFrame {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final TunerEngine tunerEngine;
    private final TempoWheel wheel;
    private final JButton tempo = Theme.button("");
    private final JButton signature = Theme.button("");
    private final MovingBeatBar beatBar;
    private final TimeSignatureDialog signatureDialog;
    private final SettingsDialog settingsDialog;
    private final ModeToggle modeToggle = new ModeToggle();
    private final JPanel cards = new JPanel(new CardLayout());
    private final TunerPanel tunerPanel;
    private final Deque<Long> taps = new ArrayDeque<>();
    private final Timer animation;
    private ModeToggle.Mode mode = ModeToggle.Mode.METRONOME;

    public MainWindow(MetronomeState state, AccurateAudioEngine engine, TunerState tunerState, TunerEngine tunerEngine) {
        super("T&T Pro");
        this.state = state;
        this.engine = engine;
        this.tunerEngine = tunerEngine;
        wheel = new TempoWheel(state.get().bpm());
        beatBar = new MovingBeatBar(state, engine);
        signatureDialog = new TimeSignatureDialog(this, state);
        settingsDialog = new SettingsDialog(this, state, engine, tunerState, tunerEngine);
        tunerPanel = new TunerPanel(this, tunerState, tunerEngine);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLocationByPlatform(true);
        var root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(10, 16, 16, 16));
        setContentPane(root);

        var header = Theme.panel(new BorderLayout(8, 0));
        header.add(modeToggle, BorderLayout.WEST);
        var gear = Theme.button("");
        gear.setName("settings");
        gear.setToolTipText("Settings: output, volume, click sound and microphone");
        gear.setIcon(new GearIcon());
        gear.setBorder(new EmptyBorder(5, 8, 5, 8));
        gear.addActionListener(e -> settingsDialog.open());
        var settingsRow = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        settingsRow.add(gear);
        header.add(settingsRow, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        var metronome = Theme.panel(new BorderLayout(0, 8));
        var readout = Theme.panel(new GridLayout(1, 2, 8, 0));
        tempo.setName("tempo");
        signature.setName("timeSignature");
        tempo.setBackground(Theme.PANEL);
        signature.setBackground(Theme.PANEL);
        tempo.setToolTipText("Enter a tempo");
        signature.setToolTipText("Edit the time signature and each beat's rhythm");
        tempo.addActionListener(e -> editTempo());
        signature.addActionListener(e -> signatureDialog.open(0));
        readout.add(tempo);
        readout.add(signature);
        readout.setPreferredSize(new Dimension(328, 78));
        metronome.add(readout, BorderLayout.NORTH);

        var middle = Theme.panel(new BorderLayout(0, 8));
        beatBar.setPreferredSize(new Dimension(328, 65));
        beatBar.setBeatClick(beat -> signatureDialog.open(beat));
        middle.add(beatBar, BorderLayout.NORTH);
        middle.add(wheel, BorderLayout.CENTER);
        metronome.add(middle, BorderLayout.CENTER);

        var controls = Theme.panel(new BorderLayout(8, 0));
        var minus = Theme.button("−");
        minus.setToolTipText("Decrease tempo by 1 BPM");
        minus.setName("tempoDown");
        var plus = Theme.button("+");
        plus.setToolTipText("Increase tempo by 1 BPM");
        plus.setName("tempoUp");
        var tap = Theme.button("Tap tempo");
        tap.setName("tapTempo");
        tap.setForeground(Theme.ACCENT);
        minus.addActionListener(e -> state.setBpm(state.get().bpm() - 1));
        plus.addActionListener(e -> state.setBpm(state.get().bpm() + 1));
        tap.addActionListener(e -> tapTempo());
        controls.add(minus, BorderLayout.WEST);
        controls.add(tap, BorderLayout.CENTER);
        controls.add(plus, BorderLayout.EAST);
        metronome.add(controls, BorderLayout.SOUTH);

        cards.setOpaque(false);
        cards.add(metronome, ModeToggle.Mode.METRONOME.name());
        cards.add(tunerPanel, ModeToggle.Mode.TUNER.name());
        root.add(cards, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(360, 442));

        modeToggle.setListener(this::setMode);
        wheel.setChangeListener(state::setBpm);
        wheel.setTransportAction(engine::togglePlayPause);
        engine.addTransportListener(value -> {
            wheel.setTransportState(value);
            beatBar.repaint();
            if (value == TransportState.STOPPED && !engine.errorMessage().isBlank())
                JOptionPane.showMessageDialog(this, engine.errorMessage(), "Audio output", JOptionPane.WARNING_MESSAGE);
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        bind("SPACE", "playPause", () -> { if (isMetronome()) engine.togglePlayPause(); else tunerPanel.toggleAuto(); });
        bind("pressed T", "tap", () -> { if (isMetronome()) tapTempo(); });
        bind("LEFT", "slower", () -> { if (isMetronome()) state.setBpm(state.get().bpm() - 1); else tunerPanel.selectNeighbour(-1); });
        bind("RIGHT", "faster", () -> { if (isMetronome()) state.setBpm(state.get().bpm() + 1); else tunerPanel.selectNeighbour(1); });
        bind("meta 1", "showMetronome", () -> setMode(ModeToggle.Mode.METRONOME));
        bind("meta 2", "showTuner", () -> setMode(ModeToggle.Mode.TUNER));
        animation = new Timer(16, e -> { if (engine.isPlaying()) beatBar.repaint(); });
        animation.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                animation.stop();
                tunerPanel.deactivate();
                tunerPanel.dispose();
                signatureDialog.dispose();
                settingsDialog.dispose();
                engine.close();
                tunerEngine.close();
            }
        });
        sync(state.get());
        pack();
    }

    ModeToggle.Mode mode() { return mode; }
    TunerPanel tunerPanel() { return tunerPanel; }
    private boolean isMetronome() { return mode == ModeToggle.Mode.METRONOME; }

    /** Shows the metronome or the tuner; the microphone is open only while the tuner is showing. */
    void setMode(ModeToggle.Mode next) {
        if (mode == next) return;
        mode = next;
        modeToggle.setMode(next);
        ((CardLayout) cards.getLayout()).show(cards, next.name());
        if (next == ModeToggle.Mode.TUNER) {
            if (engine.isPlaying()) engine.pause();
            tunerPanel.activate();
        } else {
            tunerPanel.deactivate();
            wheel.requestFocusInWindow();
        }
    }

    private void sync(MetronomeSettings settings) {
        wheel.setBpm(settings.bpm());
        String bpm = Integer.toString((int) settings.bpm());
        tempo.setText("<html><center><span style='font-size:10px'>Tempo</span><br><span style='font-size:29px'>" + bpm + "</span></center></html>");
        signature.setText("<html><center><span style='font-size:10px'>Time Signature</span><br><span style='font-size:29px'>"
                + settings.beatsPerBar() + "/" + settings.beatUnit() + "</span></center></html>");
        beatBar.repaint();
    }

    private void editTempo() {
        var input = new JSpinner(new SpinnerNumberModel((int) state.get().bpm(), 20, 300, 1));
        input.setEditor(new JSpinner.NumberEditor(input, "0"));
        if (JOptionPane.showConfirmDialog(this, input, "Tempo (BPM)", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                input.commitEdit();
                state.setBpm(((Number) input.getValue()).intValue());
            } catch (java.text.ParseException ignored) { }
        }
    }

    private void tapTempo() {
        long now = System.nanoTime();
        if (!taps.isEmpty() && now - taps.getLast() > 3_100_000_000L) taps.clear();
        taps.addLast(now);
        while (taps.size() > 7) taps.removeFirst();
        if (taps.size() > 1) state.setBpm(Math.round(60_000_000_000.0 * (taps.size() - 1)
                / (taps.getLast() - taps.getFirst())));
    }

    private void bind(String key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private static final class GearIcon implements Icon {
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.getForeground());
            var gear = new Area(new Ellipse2D.Double(2.8, 2.8, 10.4, 10.4));
            for (int i = 0; i < 8; i++) {
                var tooth = new Area(new RoundRectangle2D.Double(6.5, .4, 3, 4.2, 1.6, 1.6));
                tooth.transform(AffineTransform.getRotateInstance(i * Math.PI / 4, 8, 8));
                gear.add(tooth);
            }
            gear.subtract(new Area(new Ellipse2D.Double(5.7, 5.7, 4.6, 4.6)));
            g.fill(gear);
            g.dispose();
        }
    }
}
