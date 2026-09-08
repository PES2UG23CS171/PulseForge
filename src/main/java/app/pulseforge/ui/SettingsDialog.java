package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.MetronomeSettings;
import app.pulseforge.model.MetronomeState;
import app.pulseforge.model.SoundType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;

final class SettingsDialog extends JDialog {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final JSpinner beats;
    private final JComboBox<Integer> unit = new JComboBox<>(new Integer[]{4, 8});
    private final AccentPattern accentPattern = new AccentPattern();
    private final JSlider swing = new JSlider(50, 75);
    private final JLabel swingValue = new JLabel();
    private final JComboBox<SoundType> sound = new JComboBox<>(SoundType.values());
    private final JComboBox<String> output = new JComboBox<>(AccurateAudioEngine.outputMixerNames().toArray(String[]::new));
    private final JSlider volume = new JSlider(0, 100);
    private final JLabel sampleStatus = new JLabel(" ");
    private boolean syncing;

    SettingsDialog(JFrame owner, MetronomeState state, AccurateAudioEngine engine) {
        super(owner, "Metronome Settings", false);
        this.state = state;
        this.engine = engine;
        this.beats = new JSpinner(new SpinnerNumberModel(state.get().beatsPerBar(), 1, 12, 1));
        build();
        wire();
        sync(state.get());
    }

    void open() {
        sync(state.get());
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private void build() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setResizable(false);
        var root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(16, 18, 14, 18));
        setContentPane(root);

        styleSpinner(beats);
        Theme.styleInput(unit);
        Theme.styleInput(sound);
        Theme.styleInput(output);
        swing.setOpaque(false);
        volume.setOpaque(false);
        swingValue.setForeground(Theme.MUTED);
        swingValue.setFont(new Font("Monospaced", Font.BOLD, 11));
        sampleStatus.setForeground(Theme.MUTED);
        sampleStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));

        var c = new GridBagConstraints();
        c.insets = new Insets(6, 5, 6, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;

        addLabel(root, c, "TIME SIGNATURE");
        var meter = transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        meter.add(beats);
        var slash = new JLabel("/"); slash.setForeground(Theme.MUTED); meter.add(slash);
        meter.add(unit);
        addControl(root, c, meter);

        c.gridy++;
        addLabel(root, c, "BEAT ACCENTS");
        addControl(root, c, accentPattern);

        c.gridy++;
        addLabel(root, c, "SWING");
        var swingRow = transparent(new BorderLayout(8, 0));
        swingRow.add(swing, BorderLayout.CENTER);
        swingRow.add(swingValue, BorderLayout.EAST);
        addControl(root, c, swingRow);

        c.gridy++;
        addLabel(root, c, "CLICK SOUND");
        var soundRow = transparent(new BorderLayout(7, 0));
        soundRow.add(sound, BorderLayout.CENTER);
        var importButton = Theme.button("IMPORT AUDIO…");
        importButton.setFont(new Font("SansSerif", Font.BOLD, 10));
        importButton.addActionListener(event -> importSample());
        soundRow.add(importButton, BorderLayout.EAST);
        addControl(root, c, soundRow);

        c.gridy++;
        c.gridx = 1;
        root.add(sampleStatus, c);

        c.gridy++;
        addLabel(root, c, "AUDIO OUTPUT");
        addControl(root, c, output);

        c.gridy++;
        addLabel(root, c, "VOLUME");
        addControl(root, c, volume);

        c.gridy++;
        c.gridx = 0; c.gridwidth = 2;
        var done = Theme.button("DONE");
        done.setBackground(Theme.ACCENT);
        done.setForeground(new Color(7, 26, 22));
        done.addActionListener(event -> setVisible(false));
        root.add(done, c);

        setSize(440, 365);
    }

    private void wire() {
        accentPattern.setClickListener(state::cycleAccent);
        beats.addChangeListener(event -> {
            if (!syncing) state.setMeter((int) beats.getValue(), (int) unit.getSelectedItem());
        });
        unit.addActionListener(event -> {
            if (!syncing) state.setMeter((int) beats.getValue(), (int) unit.getSelectedItem());
        });
        swing.addChangeListener(event -> {
            if (!syncing) state.setSwing(swing.getValue() / 100.0);
        });
        sound.addActionListener(event -> {
            if (syncing) return;
            var selected = (SoundType) sound.getSelectedItem();
            if (selected == SoundType.CUSTOM && state.get().customSamplePath().isBlank()) importSample();
            else if (selected != null) state.setSound(selected);
        });
        output.addActionListener(event -> {
            if (!syncing && output.getSelectedItem() != null) {
                state.setMixerName(output.getSelectedItem().toString());
                engine.restartForOutputChange();
            }
        });
        volume.addChangeListener(event -> {
            if (!syncing) state.setVolume(volume.getValue() / 100.0);
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
    }

    private void sync(MetronomeSettings settings) {
        syncing = true;
        beats.setValue(settings.beatsPerBar());
        unit.setSelectedItem(settings.beatUnit());
        accentPattern.setAccents(settings.accents());
        swing.setValue((int) Math.round(settings.swing() * 100));
        swing.setEnabled(settings.subdivision().stepsPerQuarter() > 1
                && settings.subdivision().stepsPerQuarter() % 2 == 0);
        swingValue.setText((int) Math.round(settings.swing() * 100) + "%");
        sound.setSelectedItem(settings.sound());
        if (!settings.mixerName().equals(output.getSelectedItem())) output.setSelectedItem(settings.mixerName());
        volume.setValue((int) Math.round(settings.volume() * 100));
        sampleStatus.setText(settings.customSamplePath().isBlank()
                ? "GarageBand WAV and AIFF recordings are supported"
                : "Sample: " + new File(settings.customSamplePath()).getName());
        syncing = false;
    }

    private void importSample() {
        var start = new File("/Users/dhrus/Music/Garageband projects ");
        if (!start.isDirectory()) start = new File(System.getProperty("user.home"), "Music");
        var chooser = new JFileChooser(start);
        chooser.setDialogTitle("Choose a GarageBand drum recording");
        chooser.setFileFilter(new FileNameExtensionFilter("Audio files (WAV, AIFF, AIF)", "wav", "aiff", "aif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var file = chooser.getSelectedFile();
        sampleStatus.setText("Analyzing the recording…");
        CompletableFuture.runAsync(() -> engine.prepareCustomSample(file.getAbsolutePath()))
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (engine.customSampleLoaded()) {
                        state.setCustomSamplePath(file.getAbsolutePath());
                        if (engine.isPlaying()) engine.restartForOutputChange();
                    } else {
                        sampleStatus.setText("Could not decode that recording");
                        JOptionPane.showMessageDialog(this,
                                "That recording could not be decoded. Try a PCM WAV or AIFF file.",
                                "Could not import sample", JOptionPane.WARNING_MESSAGE);
                        sync(state.get());
                    }
                }));
    }

    private static void addLabel(JPanel panel, GridBagConstraints c, String text) {
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        panel.add(Theme.label(text), c);
    }

    private static void addControl(JPanel panel, GridBagConstraints c, JComponent component) {
        c.gridx = 1; c.gridwidth = 1; c.weightx = 1;
        panel.add(component, c);
    }

    private static JPanel transparent(LayoutManager layout) {
        var panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }

    private static void styleSpinner(JSpinner spinner) {
        Theme.styleInput(spinner);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setForeground(Theme.TEXT);
            editor.getTextField().setBackground(Theme.PANEL_LIGHT);
            editor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        }
    }
}
