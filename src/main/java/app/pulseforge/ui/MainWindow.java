package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

public final class MainWindow extends JFrame {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final TempoWheel tempoWheel;
    private final BeatVisualizer visualizer = new BeatVisualizer();
    private final AccentPattern accentPattern = new AccentPattern();
    private final JButton playButton = Theme.button("▶  START");
    private final JLabel status = new JLabel("SPACE TO START");
    private final JSlider swingSlider = new JSlider(50, 75);
    private final JLabel swingValue = new JLabel();
    private final JComboBox<SoundType> soundBox = new JComboBox<>(SoundType.values());
    private final JButton importButton = Theme.button("IMPORT");
    private final Deque<Long> taps = new ArrayDeque<>();
    private boolean syncing;

    public MainWindow(MetronomeState state, AccurateAudioEngine engine) {
        super("PulseForge");
        this.state = state;
        this.engine = engine;
        this.tempoWheel = new TempoWheel(state.get().bpm());
        buildWindow();
        wireEvents();
        syncFromState(state.get());
    }

    private void buildWindow() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(438, 690);
        setMinimumSize(getSize());
        setLocationByPlatform(true);
        setBackground(Theme.BACKGROUND);

        var root = new JPanel();
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(14, 18, 16, 18));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        setContentPane(root);

        root.add(header());
        root.add(Box.createVerticalStrut(2));
        center(root, tempoWheel);
        root.add(tempoButtons());
        root.add(Box.createVerticalStrut(13));
        root.add(subdivisionPanel());
        root.add(Box.createVerticalStrut(5));
        center(root, visualizer);
        root.add(Box.createVerticalStrut(5));
        root.add(settingsPanel());
        root.add(Box.createVerticalGlue());
        root.add(playButton);

        playButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        playButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        playButton.setPreferredSize(new Dimension(400, 48));
        playButton.setBackground(Theme.ACCENT);
        playButton.setForeground(new Color(7, 26, 22));
        playButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    }

    private JComponent header() {
        var panel = transparent(new BorderLayout());
        var title = new JLabel("●  PULSEFORGE");
        title.setForeground(Theme.TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        var pin = Theme.button("PIN");
        pin.setToolTipText("Keep this compact window above other apps");
        pin.setFont(new Font("SansSerif", Font.BOLD, 10));
        pin.setBorder(new EmptyBorder(6, 10, 6, 10));
        pin.addActionListener(event -> {
            setAlwaysOnTop(!isAlwaysOnTop());
            pin.setText(isAlwaysOnTop() ? "PINNED" : "PIN");
            pin.setForeground(isAlwaysOnTop() ? Theme.ACCENT : Theme.TEXT);
        });
        status.setForeground(Theme.MUTED);
        status.setFont(new Font("SansSerif", Font.BOLD, 9));
        var right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(status); right.add(pin);
        panel.add(title, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return panel;
    }

    private JComponent tempoButtons() {
        var panel = transparent(new FlowLayout(FlowLayout.CENTER, 8, 0));
        var minus = Theme.button("− 1");
        var tap = Theme.button("TAP");
        var plus = Theme.button("+ 1");
        tap.setForeground(Theme.ACCENT);
        minus.addActionListener(event -> state.setBpm(state.get().bpm() - 1));
        plus.addActionListener(event -> state.setBpm(state.get().bpm() + 1));
        tap.addActionListener(event -> tapTempo());
        panel.add(minus); panel.add(tap); panel.add(plus);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return panel;
    }

    private JComponent subdivisionPanel() {
        var outer = transparent(new BorderLayout(0, 6));
        outer.add(Theme.label("SUBDIVISION"), BorderLayout.NORTH);
        var row = transparent(new GridLayout(1, Subdivision.values().length, 5, 0));
        var group = new ButtonGroup();
        for (var subdivision : Subdivision.values()) {
            var button = new JToggleButton(subdivision.symbol());
            button.setName(subdivision.name());
            button.setToolTipText(subdivision.label());
            button.setFocusPainted(false);
            button.setForeground(Theme.TEXT);
            button.setBackground(Theme.PANEL_LIGHT);
            button.setFont(new Font("SansSerif", Font.BOLD, subdivision.symbol().length() == 1 ? 17 : 13));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(6, 8, 6, 8)));
            button.addActionListener(event -> state.setSubdivision(subdivision));
            group.add(button); row.add(button);
        }
        outer.add(row, BorderLayout.CENTER);
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        return outer;
    }

    private JComponent settingsPanel() {
        var panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 10, 8, 10)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        var c = new GridBagConstraints();
        c.gridy = 0; c.insets = new Insets(3, 4, 3, 4); c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; panel.add(Theme.label("METER"), c);
        var beats = new JSpinner(new SpinnerNumberModel(state.get().beatsPerBar(), 1, 12, 1));
        beats.setName("beats"); styleSpinner(beats);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = .22; panel.add(beats, c);
        var unit = new JComboBox<>(new Integer[]{4, 8});
        unit.setName("unit"); Theme.styleInput(unit);
        c.gridx = 2; c.weightx = .18; panel.add(unit, c);
        c.gridx = 3; c.weightx = .60; c.gridwidth = 2; panel.add(accentPattern, c);

        c.gridy++; c.gridwidth = 1; c.weightx = 0; c.gridx = 0; panel.add(Theme.label("SWING"), c);
        swingSlider.setOpaque(false);
        swingSlider.setForeground(Theme.ACCENT_2);
        swingSlider.setToolTipText("50% is straight; up to 75% adds swing");
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; panel.add(swingSlider, c);
        swingValue.setForeground(Theme.MUTED);
        swingValue.setFont(new Font("Monospaced", Font.BOLD, 11));
        c.gridx = 4; c.gridwidth = 1; c.weightx = 0; panel.add(swingValue, c);

        c.gridy++; c.gridx = 0; panel.add(Theme.label("SOUND"), c);
        Theme.styleInput(soundBox);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; panel.add(soundBox, c);
        importButton.setFont(new Font("SansSerif", Font.BOLD, 9));
        importButton.setBorder(new EmptyBorder(7, 9, 7, 9));
        c.gridx = 4; c.gridwidth = 1; c.weightx = 0; panel.add(importButton, c);

        c.gridy++; c.gridx = 0; panel.add(Theme.label("OUTPUT"), c);
        var output = new JComboBox<>(AccurateAudioEngine.outputMixerNames().toArray(String[]::new));
        output.setName("output"); Theme.styleInput(output);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; panel.add(output, c);
        var volume = new JSlider(0, 100, (int) (state.get().volume() * 100));
        volume.setName("volume"); volume.setOpaque(false); volume.setToolTipText("Output volume");
        c.gridx = 4; c.gridwidth = 1; c.weightx = .35; panel.add(volume, c);

        beats.addChangeListener(event -> {
            if (!syncing) state.setMeter((int) beats.getValue(), (int) unit.getSelectedItem());
        });
        unit.addActionListener(event -> {
            if (!syncing) state.setMeter((int) beats.getValue(), (int) unit.getSelectedItem());
        });
        output.addActionListener(event -> {
            if (!syncing && output.getSelectedItem() != null) {
                state.setMixerName(output.getSelectedItem().toString());
                engine.restartForOutputChange();
            }
        });
        volume.addChangeListener(event -> { if (!syncing) state.setVolume(volume.getValue() / 100.0); });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> {
            syncing = true;
            beats.setValue(settings.beatsPerBar());
            unit.setSelectedItem(settings.beatUnit());
            if (!settings.mixerName().equals(output.getSelectedItem())) output.setSelectedItem(settings.mixerName());
            volume.setValue((int) Math.round(settings.volume() * 100));
            syncing = false;
        }));
        return panel;
    }

    private void wireEvents() {
        tempoWheel.setChangeListener(state::setBpm);
        accentPattern.setClickListener(state::cycleAccent);
        playButton.addActionListener(event -> engine.toggle());
        swingSlider.addChangeListener(event -> {
            if (!syncing) state.setSwing(swingSlider.getValue() / 100.0);
        });
        soundBox.addActionListener(event -> {
            if (syncing) return;
            var selected = (SoundType) soundBox.getSelectedItem();
            if (selected == SoundType.CUSTOM && state.get().customSamplePath().isBlank()) importSample();
            else if (selected != null) state.setSound(selected);
        });
        importButton.addActionListener(event -> importSample());
        engine.addPulseListener(visualizer::pulse);
        engine.addPlayListener(playing -> {
            visualizer.setPlaying(playing);
            playButton.setText(playing ? "■  STOP" : "▶  START");
            playButton.setBackground(playing ? Theme.WARNING : Theme.ACCENT);
            status.setText(playing ? "LOCKED" : engine.errorMessage().isBlank() ? "SPACE TO START" : "AUDIO ERROR");
            status.setForeground(playing ? Theme.ACCENT : engine.errorMessage().isBlank() ? Theme.MUTED : Theme.WARNING);
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> syncFromState(settings)));

        var root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "toggle");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('T'), "tap");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "slower");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "faster");
        root.getActionMap().put("toggle", action(() -> engine.toggle()));
        root.getActionMap().put("tap", action(this::tapTempo));
        root.getActionMap().put("slower", action(() -> state.setBpm(state.get().bpm() - 1)));
        root.getActionMap().put("faster", action(() -> state.setBpm(state.get().bpm() + 1)));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { engine.close(); }
        });
    }

    private void syncFromState(MetronomeSettings settings) {
        syncing = true;
        tempoWheel.setBpm(settings.bpm());
        visualizer.setBeatCount(settings.beatsPerBar());
        visualizer.setBeatPeriod(settings.quarterNoteBpm());
        accentPattern.setAccents(settings.accents());
        swingSlider.setValue((int) Math.round(settings.swing() * 100));
        swingSlider.setEnabled(settings.subdivision().stepsPerQuarter() > 1
                && settings.subdivision().stepsPerQuarter() % 2 == 0);
        swingValue.setText((int) Math.round(settings.swing() * 100) + "%");
        soundBox.setSelectedItem(settings.sound());
        importButton.setForeground(settings.sound() == SoundType.CUSTOM ? Theme.ACCENT : Theme.TEXT);
        findButtons(getContentPane(), settings.subdivision());
        syncing = false;
    }

    private static void findButtons(Container parent, Subdivision selected) {
        for (var component : parent.getComponents()) {
            if (component instanceof JToggleButton button && selected.name().equals(button.getName())) {
                button.setSelected(true);
                button.setBackground(Theme.ACCENT_2);
            } else if (component instanceof JToggleButton button) button.setBackground(Theme.PANEL_LIGHT);
            if (component instanceof Container container) findButtons(container, selected);
        }
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

    private void importSample() {
        var start = new File("/Users/dhrus/Music/Garageband projects ");
        if (!start.isDirectory()) start = new File(System.getProperty("user.home"), "Music");
        var chooser = new JFileChooser(start);
        chooser.setDialogTitle("Choose a GarageBand drum recording");
        chooser.setFileFilter(new FileNameExtensionFilter("Audio files (WAV, AIFF, AIF)", "wav", "aiff", "aif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var file = chooser.getSelectedFile();
        status.setText("ANALYZING SAMPLE…");
        CompletableFuture.runAsync(() -> engine.prepareCustomSample(file.getAbsolutePath()))
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (engine.customSampleLoaded()) {
                        state.setCustomSamplePath(file.getAbsolutePath());
                        status.setText("SAMPLE READY");
                        if (engine.isPlaying()) engine.restartForOutputChange();
                    } else {
                        status.setText("SAMPLE ERROR");
                        JOptionPane.showMessageDialog(this,
                                "That recording could not be decoded. Try a PCM WAV or AIFF file.",
                                "Could not import sample", JOptionPane.WARNING_MESSAGE);
                        syncFromState(state.get());
                    }
                }));
    }

    private static JPanel transparent(LayoutManager layout) {
        var panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }

    private static void center(JPanel parent, JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT); parent.add(component);
    }

    private static void styleSpinner(JSpinner spinner) {
        Theme.styleInput(spinner);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setForeground(Theme.TEXT);
            editor.getTextField().setBackground(Theme.PANEL_LIGHT);
            editor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    private static Action action(Runnable runnable) {
        return new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { runnable.run(); }};
    }
}
