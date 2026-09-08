package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.audio.TransportState;
import app.pulseforge.model.MetronomeSettings;
import app.pulseforge.model.MetronomeState;
import app.pulseforge.model.Subdivision;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

public final class MainWindow extends JFrame {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final TempoWheel tempoWheel;
    private final BeatVisualizer visualizer = new BeatVisualizer();
    private final SettingsDialog settingsDialog;
    private final JLabel bpmValue = valueLabel(29);
    private final JLabel meterValue = valueLabel(18);
    private final JLabel subdivisionValue = valueLabel(15);
    private final JLabel tempoName = new JLabel();
    private final JLabel status = new JLabel("READY");
    private final JLabel settingsSummary = new JLabel();
    private final JButton stopButton = Theme.button("■  STOP");
    private final Map<Subdivision, JToggleButton> subdivisionButtons = new EnumMap<>(Subdivision.class);
    private final Deque<Long> taps = new ArrayDeque<>();

    public MainWindow(MetronomeState state, AccurateAudioEngine engine) {
        super("PulseForge");
        this.state = state;
        this.engine = engine;
        this.tempoWheel = new TempoWheel(state.get().bpm());
        this.settingsDialog = new SettingsDialog(this, state, engine);
        buildWindow();
        wireEvents();
        syncFromState(state.get());
        updateTransport(TransportState.STOPPED);
    }

    private void buildWindow() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        setLocationByPlatform(true);
        setBackground(Theme.BACKGROUND);

        var root = new JPanel();
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(13, 16, 14, 16));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setPreferredSize(new Dimension(420, 590));
        setContentPane(root);

        root.add(header());
        root.add(Box.createVerticalStrut(7));
        root.add(readout());
        root.add(Box.createVerticalStrut(2));
        center(root, tempoWheel);
        root.add(transportControls());
        root.add(Box.createVerticalStrut(5));
        center(root, visualizer);
        root.add(Box.createVerticalStrut(3));
        root.add(subdivisionPanel());
        root.add(Box.createVerticalStrut(8));
        root.add(footer());
        pack();
    }

    private JComponent header() {
        var panel = transparent(new BorderLayout());
        var title = new JLabel("●  PULSEFORGE");
        title.setForeground(Theme.TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        var pin = smallButton("PIN");
        pin.setToolTipText("Keep PulseForge above other apps");
        pin.addActionListener(event -> {
            setAlwaysOnTop(!isAlwaysOnTop());
            pin.setText(isAlwaysOnTop() ? "PINNED" : "PIN");
            pin.setForeground(isAlwaysOnTop() ? Theme.ACCENT : Theme.TEXT);
        });
        status.setForeground(Theme.MUTED);
        status.setFont(new Font("SansSerif", Font.BOLD, 9));
        var right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(status);
        right.add(pin);
        panel.add(title, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        return panel;
    }

    private JComponent readout() {
        var panel = new JPanel(new GridLayout(1, 3));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(7, 3, 6, 3)));
        panel.add(readoutCell("TEMPO", bpmValue, tempoName));
        panel.add(readoutCell("METER", meterValue, mutedLabel("TIME SIGNATURE")));
        panel.add(readoutCell("RHYTHM", subdivisionValue, mutedLabel("CLICKS PER BEAT")));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 67));
        return panel;
    }

    private JComponent readoutCell(String heading, JLabel value, JLabel detail) {
        var cell = transparent();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        var label = Theme.label(heading);
        label.setForeground(Theme.ACCENT_2);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        cell.add(label);
        cell.add(Box.createVerticalStrut(2));
        cell.add(value);
        cell.add(detail);
        return cell;
    }

    private JComponent transportControls() {
        var row = transparent(new FlowLayout(FlowLayout.CENTER, 7, 0));
        var minus = Theme.button("− 1");
        var tap = Theme.button("TAP TEMPO");
        var plus = Theme.button("+ 1");
        tap.setForeground(Theme.ACCENT);
        stopButton.setForeground(Theme.WARNING);
        stopButton.setToolTipText("Stop and reset to beat one (S)");
        minus.addActionListener(event -> state.setBpm(state.get().bpm() - 1));
        plus.addActionListener(event -> state.setBpm(state.get().bpm() + 1));
        tap.addActionListener(event -> tapTempo());
        stopButton.addActionListener(event -> engine.stop());
        row.add(minus);
        row.add(tap);
        row.add(stopButton);
        row.add(plus);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return row;
    }

    private JComponent subdivisionPanel() {
        var panel = transparent(new BorderLayout(0, 5));
        var title = new JLabel("RHYTHM SUBDIVISION  —  choose clicks per beat");
        title.setForeground(Theme.MUTED);
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        panel.add(title, BorderLayout.NORTH);
        var row = transparent(new GridLayout(1, Subdivision.values().length, 5, 0));
        var group = new ButtonGroup();
        for (var subdivision : Subdivision.values()) {
            int clicks = subdivision.stepsPerQuarter();
            String count = clicks + "×";
            String label = switch (subdivision) {
                case QUARTER -> "Quarter";
                case EIGHTH -> "Eighths";
                case TRIPLET -> "Triplets";
                case SIXTEENTH -> "16ths";
                case SEXTUPLET -> "Sextuplets";
            };
            var button = new JToggleButton("<html><center><b>" + count + "</b><br><span style='font-size:8px'>" + label + "</span></center></html>");
            button.setToolTipText(clicks + (clicks == 1 ? " click" : " clicks") + " per beat — " + subdivision.label());
            button.setFocusPainted(false);
            button.setForeground(Theme.TEXT);
            button.setBackground(Theme.PANEL_LIGHT);
            button.setFont(new Font("SansSerif", Font.PLAIN, 11));
            button.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(event -> state.setSubdivision(subdivision));
            subdivisionButtons.put(subdivision, button);
            group.add(button);
            row.add(button);
        }
        panel.add(row, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        return panel;
    }

    private JComponent footer() {
        var panel = transparent(new BorderLayout(8, 0));
        settingsSummary.setForeground(Theme.MUTED);
        settingsSummary.setFont(new Font("SansSerif", Font.PLAIN, 10));
        var settingsButton = Theme.button("⚙  SOUND & SETTINGS");
        settingsButton.setFont(new Font("SansSerif", Font.BOLD, 10));
        settingsButton.addActionListener(event -> settingsDialog.open());
        panel.add(settingsSummary, BorderLayout.CENTER);
        panel.add(settingsButton, BorderLayout.EAST);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return panel;
    }

    private void wireEvents() {
        tempoWheel.setChangeListener(state::setBpm);
        tempoWheel.setTransportAction(engine::togglePlayPause);
        engine.addPulseListener(visualizer::pulse);
        engine.addTransportListener(this::updateTransport);
        state.addListener(settings -> SwingUtilities.invokeLater(() -> syncFromState(settings)));

        var root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "playPause");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('T'), "tap");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('S'), "stop");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "slower");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "faster");
        root.getActionMap().put("playPause", action(engine::togglePlayPause));
        root.getActionMap().put("tap", action(this::tapTempo));
        root.getActionMap().put("stop", action(engine::stop));
        root.getActionMap().put("slower", action(() -> state.setBpm(state.get().bpm() - 1)));
        root.getActionMap().put("faster", action(() -> state.setBpm(state.get().bpm() + 1)));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                settingsDialog.dispose();
                engine.close();
            }
        });
    }

    private void syncFromState(MetronomeSettings settings) {
        tempoWheel.setBpm(settings.bpm());
        visualizer.setBeatCount(settings.beatsPerBar());
        visualizer.setBeatPeriod(settings.quarterNoteBpm());
        bpmValue.setText(formatBpm(settings.bpm()));
        tempoName.setText(tempoName(settings.bpm()).toUpperCase());
        tempoName.setForeground(Theme.MUTED);
        tempoName.setFont(new Font("SansSerif", Font.BOLD, 8));
        meterValue.setText(settings.beatsPerBar() + "/" + settings.beatUnit());
        subdivisionValue.setText(settings.subdivision().stepsPerQuarter() + "× " + shortSubdivision(settings.subdivision()));
        settingsSummary.setText(settings.sound() + "   ·   Volume " + (int) Math.round(settings.volume() * 100) + "%");
        subdivisionButtons.forEach((subdivision, button) -> {
            boolean selected = subdivision == settings.subdivision();
            button.setSelected(selected);
            button.setBackground(selected ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
            button.setForeground(selected ? Color.WHITE : Theme.TEXT);
        });
    }

    private void updateTransport(TransportState value) {
        tempoWheel.setTransportState(value);
        visualizer.setPlaying(value == TransportState.PLAYING);
        stopButton.setEnabled(value != TransportState.STOPPED);
        status.setText(switch (value) {
            case PLAYING -> "PLAYING";
            case PAUSED -> "PAUSED";
            case STOPPED -> engine.errorMessage().isBlank() ? "READY" : "AUDIO ERROR";
        });
        status.setForeground(switch (value) {
            case PLAYING -> Theme.ACCENT;
            case PAUSED -> Theme.WARNING;
            case STOPPED -> engine.errorMessage().isBlank() ? Theme.MUTED : Theme.WARNING;
        });
    }

    private void tapTempo() {
        long now = System.nanoTime();
        if (!taps.isEmpty() && now - taps.getLast() > 2_000_000_000L) taps.clear();
        taps.addLast(now);
        while (taps.size() > 7) taps.removeFirst();
        if (taps.size() >= 2) {
            long duration = taps.getLast() - taps.getFirst();
            double bpm = 60_000_000_000.0 * (taps.size() - 1) / duration;
            state.setBpm(Math.round(Math.max(20, Math.min(300, bpm)) * 10) / 10.0);
        }
    }

    private static String formatBpm(double bpm) {
        return bpm == Math.rint(bpm) ? Integer.toString((int) bpm) : String.format("%.1f", bpm);
    }

    private static String shortSubdivision(Subdivision subdivision) {
        return switch (subdivision) {
            case QUARTER -> "Quarter";
            case EIGHTH -> "Eighths";
            case TRIPLET -> "Triplets";
            case SIXTEENTH -> "16ths";
            case SEXTUPLET -> "Sextuplets";
        };
    }

    private static String tempoName(double bpm) {
        if (bpm < 40) return "Grave";
        if (bpm < 60) return "Largo";
        if (bpm < 76) return "Adagio";
        if (bpm < 108) return "Andante";
        if (bpm < 120) return "Moderato";
        if (bpm < 168) return "Allegro";
        if (bpm < 200) return "Presto";
        return "Prestissimo";
    }

    private static JLabel valueLabel(int size) {
        var label = new JLabel();
        label.setForeground(Theme.TEXT);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        return label;
    }

    private static JLabel mutedLabel(String text) {
        var label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 8));
        return label;
    }

    private static JButton smallButton(String text) {
        var button = Theme.button(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 10));
        button.setBorder(new EmptyBorder(6, 10, 6, 10));
        return button;
    }

    private static JPanel transparent() { return transparent(new FlowLayout()); }
    private static JPanel transparent(LayoutManager layout) {
        var panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static void center(JPanel parent, JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        parent.add(component);
    }

    private static Action action(Runnable runnable) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { runnable.run(); }
        };
    }
}
