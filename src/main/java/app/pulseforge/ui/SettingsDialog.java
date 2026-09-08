package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;

final class SettingsDialog extends JDialog {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final JComboBox<String> output = new JComboBox<>();
    private final JSlider volume = new JSlider(0, 100);
    private final JLabel volumeValue = Theme.label("");
    private final JComboBox<SoundType> sound = new JComboBox<>(SoundType.values());
    private final JLabel sampleStatus = Theme.label("");
    private final JButton importButton = Theme.button("Import GarageBand audio…");
    private boolean syncing;

    SettingsDialog(JFrame owner, MetronomeState state, AccurateAudioEngine engine) {
        super(owner, "Sound", false);
        this.state = state;
        this.engine = engine;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        var root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        setContentPane(root);
        Theme.styleInput(output);
        Theme.styleInput(sound);
        volume.setOpaque(false);
        var volumeRow = Theme.panel(new BorderLayout(8, 0));
        volumeRow.add(volume, BorderLayout.CENTER);
        volumeRow.add(volumeValue, BorderLayout.EAST);
        row(root, 0, "Output device", output);
        row(root, 1, "Volume", volumeRow);
        row(root, 2, "Click sound", sound);
        row(root, 3, "", importButton);
        sampleStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));
        row(root, 4, "", sampleStatus);
        var done = Theme.button("Done");
        done.addActionListener(e -> setVisible(false));
        row(root, 5, "", done);
        root.setPreferredSize(new Dimension(432, 270));
        pack();

        output.addActionListener(e -> {
            if (!syncing && output.getSelectedItem() != null) {
                state.setMixerName(output.getSelectedItem().toString());
                engine.restartForOutputChange();
            }
        });
        volume.addChangeListener(e -> { if (!syncing) state.setVolume(volume.getValue() / 100.0); });
        sound.addActionListener(e -> {
            if (syncing) return;
            var selected = (SoundType) sound.getSelectedItem();
            if (selected == SoundType.CUSTOM && state.get().customSamplePath().isBlank()) importSample();
            else if (selected != null) {
                if (selected == SoundType.CUSTOM) loadSample(new File(state.get().customSamplePath()));
                else state.setSound(selected);
            }
        });
        importButton.addActionListener(e -> importSample());
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    void open() {
        syncing = true;
        output.setModel(new DefaultComboBoxModel<>(AccurateAudioEngine.outputMixerNames().toArray(String[]::new)));
        sync(state.get());
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private void sync(MetronomeSettings settings) {
        syncing = true;
        output.setSelectedItem(settings.mixerName());
        volume.setValue((int) Math.round(settings.volume() * 100));
        volumeValue.setText(volume.getValue() + "%");
        sound.setSelectedItem(settings.sound());
        String file = settings.customSamplePath().isBlank() ? "WAV / AIFF recordings"
                : new File(settings.customSamplePath()).getName();
        sampleStatus.setText(file.length() > 38 ? file.substring(0, 35) + "…" : file);
        sampleStatus.setToolTipText(file);
        syncing = false;
    }

    private void importSample() {
        var music = new File(System.getProperty("user.home"), "Music");
        var start = new File(music, "Garageband projects ");
        if (!state.get().customSamplePath().isBlank()) start = new File(state.get().customSamplePath()).getParentFile();
        if (start == null || !start.isDirectory()) start = music;
        var chooser = new JFileChooser(start);
        chooser.setDialogTitle("Choose a drum recording");
        chooser.setFileFilter(new FileNameExtensionFilter("WAV and AIFF audio", "wav", "aiff", "aif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadSample(chooser.getSelectedFile());
        else sync(state.get());
    }

    private void loadSample(File file) {
        sampleStatus.setText("Loading recording…");
        importButton.setEnabled(false);
        sound.setEnabled(false);
        CompletableFuture.runAsync(() -> engine.prepareCustomSample(file.getAbsolutePath()))
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    importButton.setEnabled(true);
                    sound.setEnabled(true);
                    if (error == null && engine.customSampleLoaded()) {
                        state.setCustomSamplePath(file.getAbsolutePath());
                    } else {
                        sync(state.get());
                        sampleStatus.setText("Unable to load this recording");
                        JOptionPane.showMessageDialog(this, "Choose a readable PCM WAV or AIFF recording.",
                                "Audio import", JOptionPane.WARNING_MESSAGE);
                    }
                }));
    }

    private static void row(JPanel root, int row, String label, JComponent control) {
        var c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 12);
        root.add(Theme.label(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 6, 0);
        root.add(control, c);
    }
}
