package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.audio.TunerEngine;
import app.pulseforge.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Output, volume, metronome sound and the tuner's microphone input. */
final class SettingsDialog extends JDialog {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private final TunerState tunerState;
    private final TunerEngine tunerEngine;
    private final JComboBox<String> output = new JComboBox<>();
    private final JComboBox<String> input = new JComboBox<>();
    private final JComboBox<String> inputChannel = new JComboBox<>();
    private final JSlider volume = new JSlider(0, 100);
    private final JLabel volumeValue = Theme.label("");
    private final JComboBox<SoundType> sound = new JComboBox<>(SoundType.values());
    private final JButton previewButton = Theme.button("▶");
    private boolean syncing;

    SettingsDialog(JFrame owner, MetronomeState state, AccurateAudioEngine engine,
                   TunerState tunerState, TunerEngine tunerEngine) {
        super(owner, "Settings", false);
        this.state = state;
        this.engine = engine;
        this.tunerState = tunerState;
        this.tunerEngine = tunerEngine;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        var root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        setContentPane(root);
        Theme.styleInput(output);
        Theme.styleInput(input);
        Theme.styleInput(inputChannel);
        Theme.styleInput(sound);
        output.setName("outputDevice");
        input.setName("inputDevice");
        inputChannel.setName("inputChannel");
        input.setToolTipText("Microphone or audio interface the tuner listens to");
        inputChannel.setToolTipText("Auto follows whichever input of the interface carries signal");
        volume.setOpaque(false);
        var volumeRow = Theme.panel(new BorderLayout(8, 0));
        volumeRow.add(volume, BorderLayout.CENTER);
        volumeRow.add(volumeValue, BorderLayout.EAST);
        var soundRow = Theme.panel(new BorderLayout(8, 0));
        soundRow.add(sound, BorderLayout.CENTER);
        previewButton.setName("previewSound");
        previewButton.setToolTipText("Play the selected sound");
        previewButton.setBorder(new EmptyBorder(4, 12, 4, 12));
        previewButton.addActionListener(e -> engine.preview());
        soundRow.add(previewButton, BorderLayout.EAST);
        row(root, 0, "Output device", output);
        row(root, 1, "Volume", volumeRow);
        row(root, 2, "Sound", soundRow);
        row(root, 3, "Input device", input);
        row(root, 4, "Input channel", inputChannel);
        var inputHint = Theme.label("Auto follows the input that carries signal.");
        inputHint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        row(root, 5, "", inputHint);
        var done = Theme.button("Done");
        done.addActionListener(e -> setVisible(false));
        row(root, 6, "", done);
        root.setPreferredSize(new Dimension(432, 300));
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
            if (selected != null) {
                state.setSound(selected);
                engine.preview();
            }
        });
        input.addActionListener(e -> {
            if (!syncing && input.getSelectedItem() != null) {
                tunerState.setInputName(input.getSelectedItem().toString());
                refreshChannels();
                tunerEngine.restartForInputChange();
            }
        });
        inputChannel.addActionListener(e -> {
            if (!syncing && inputChannel.getSelectedIndex() >= 0) {
                tunerState.setInputChannel(inputChannel.getSelectedIndex());
                tunerEngine.restartForInputChange();
            }
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        tunerState.addListener(settings -> SwingUtilities.invokeLater(() -> syncInput(settings)));
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    void open() {
        syncing = true;
        output.setModel(new DefaultComboBoxModel<>(AccurateAudioEngine.outputMixerNames().toArray(String[]::new)));
        input.setModel(new DefaultComboBoxModel<>(TunerEngine.inputMixerNames().toArray(String[]::new)));
        refreshChannels();
        sync(state.get());
        syncInput(tunerState.get());
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
        syncing = false;
    }

    private void syncInput(TunerSettings settings) {
        syncing = true;
        input.setSelectedItem(settings.inputName());
        inputChannel.setSelectedIndex(Math.min(settings.inputChannel(), inputChannel.getItemCount() - 1));
        syncing = false;
    }

    /** Offers Auto plus one entry per channel of the selected input. */
    private void refreshChannels() {
        boolean wasSyncing = syncing;
        syncing = true;
        int channels = TunerEngine.inputChannelCount(tunerState.get().inputName());
        var model = new DefaultComboBoxModel<String>();
        model.addElement("Auto");
        for (int i = 1; i <= channels; i++) model.addElement("Input " + i);
        inputChannel.setModel(model);
        inputChannel.setSelectedIndex(Math.min(tunerState.get().inputChannel(), channels));
        inputChannel.setEnabled(channels > 1);
        syncing = wasSyncing;
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
