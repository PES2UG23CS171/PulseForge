package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.audio.TransportState;
import app.pulseforge.model.MetronomeSettings;
import app.pulseforge.model.MetronomeState;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.Deque;

public final class MainWindow extends JFrame {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final TempoWheel wheel;
    private final JButton tempo = Theme.button("");
    private final JButton signature = Theme.button("");
    private final JLabel beatUnit = Theme.label("");
    private final MovingBeatBar beatBar;
    private final TimeSignatureDialog signatureDialog;
    private final SettingsDialog soundDialog;
    private final Deque<Long> taps = new ArrayDeque<>();
    private final Timer animation;

    public MainWindow(MetronomeState state, AccurateAudioEngine engine) {
        super("PulseForge");
        this.state = state;
        this.engine = engine;
        wheel = new TempoWheel(state.get().bpm());
        beatBar = new MovingBeatBar(state, engine);
        signatureDialog = new TimeSignatureDialog(this, state);
        soundDialog = new SettingsDialog(this, state, engine);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLocationByPlatform(true);
        var root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(10, 16, 16, 16));
        setContentPane(root);

        var top = Theme.panel(new BorderLayout(0, 6));
        var soundRow = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        var sound = Theme.button("Sound");
        sound.setName("soundSettings");
        sound.setToolTipText("Output device, volume and click sound");
        sound.setIcon(new SpeakerIcon());
        sound.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sound.setBorder(new EmptyBorder(5, 9, 5, 9));
        sound.addActionListener(e -> soundDialog.open());
        soundRow.add(sound);
        top.add(soundRow, BorderLayout.NORTH);
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
        top.add(readout, BorderLayout.CENTER);
        beatUnit.setHorizontalAlignment(SwingConstants.CENTER);
        beatUnit.setFont(new Font("SansSerif", Font.PLAIN, 10));
        top.add(beatUnit, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        var middle = Theme.panel(new BorderLayout(0, 8));
        beatBar.setPreferredSize(new Dimension(328, 65));
        beatBar.setBeatClick(beat -> signatureDialog.open(beat));
        middle.add(beatBar, BorderLayout.NORTH);
        middle.add(wheel, BorderLayout.CENTER);
        root.add(middle, BorderLayout.CENTER);

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
        root.add(controls, BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(360, 440));

        wheel.setChangeListener(state::setBpm);
        wheel.setTransportAction(engine::togglePlayPause);
        engine.addTransportListener(value -> {
            wheel.setTransportState(value);
            beatBar.repaint();
            if (value == TransportState.STOPPED && !engine.errorMessage().isBlank())
                JOptionPane.showMessageDialog(this, engine.errorMessage(), "Audio output", JOptionPane.WARNING_MESSAGE);
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        bind("SPACE", "playPause", engine::togglePlayPause);
        bind("pressed T", "tap", this::tapTempo);
        bind("LEFT", "slower", () -> state.setBpm(state.get().bpm() - 1));
        bind("RIGHT", "faster", () -> state.setBpm(state.get().bpm() + 1));
        animation = new Timer(16, e -> { if (engine.isPlaying()) beatBar.repaint(); });
        animation.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                animation.stop();
                signatureDialog.dispose();
                soundDialog.dispose();
                engine.close();
            }
        });
        sync(state.get());
        pack();
    }

    private void sync(MetronomeSettings settings) {
        wheel.setBpm(settings.bpm());
        String bpm = settings.bpm() == Math.rint(settings.bpm())
                ? Integer.toString((int) settings.bpm()) : String.format("%.1f", settings.bpm());
        tempo.setText("<html><center><span style='font-size:10px'>Tempo</span><br><span style='font-size:29px'>" + bpm + "</span></center></html>");
        signature.setText("<html><center><span style='font-size:10px'>Time Signature</span><br><span style='font-size:29px'>"
                + settings.beatsPerBar() + "/" + settings.beatUnit() + "</span></center></html>");
        beatUnit.setText(MetronomeSettings.noteName(settings.beatUnit()) + " = " + bpm + " BPM");
        beatBar.repaint();
    }

    private void editTempo() {
        var input = new JSpinner(new SpinnerNumberModel(state.get().bpm(), 20, 300, .1));
        if (JOptionPane.showConfirmDialog(this, input, "Tempo (BPM)", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                input.commitEdit();
                state.setBpm(((Number) input.getValue()).doubleValue());
            } catch (java.text.ParseException ignored) { }
        }
    }

    private void tapTempo() {
        long now = System.nanoTime();
        if (!taps.isEmpty() && now - taps.getLast() > 3_100_000_000L) taps.clear();
        taps.addLast(now);
        while (taps.size() > 7) taps.removeFirst();
        if (taps.size() > 1) state.setBpm(Math.round(600_000_000_000.0 * (taps.size() - 1)
                / (taps.getLast() - taps.getFirst())) / 10.0);
    }

    private void bind(String key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private static final class SpeakerIcon implements Icon {
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.getForeground());
            g.fillPolygon(new int[]{2, 5, 9, 9, 5, 2}, new int[]{6, 6, 3, 13, 10, 10}, 6);
            g.setStroke(new BasicStroke(1.3f));
            g.drawArc(7, 4, 7, 8, -65, 130);
            g.dispose();
        }
    }
}
