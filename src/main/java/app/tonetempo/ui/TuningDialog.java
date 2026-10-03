package app.tonetempo.ui;

import app.tonetempo.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Sets the target note of every string; presets fill all strings at once. */
final class TuningDialog extends JDialog {
    private final TunerState state;
    private final JComboBox<String> preset = new JComboBox<>();
    private final JPanel rows = Theme.panel(new GridLayout(0, 1, 0, 6));
    private final List<JComboBox<String>> notes = new ArrayList<>();
    private final JLabel summary = Theme.label("");
    private boolean syncing;

    TuningDialog(JFrame owner, TunerState state) {
        super(owner, "Tuning", false);
        this.state = state;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        var root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        setContentPane(root);

        Theme.styleInput(preset);
        preset.setName("tuningPreset");
        for (var tuning : Tuning.PRESETS) preset.addItem(tuning.label());
        preset.addItem(Tuning.CUSTOM);
        var top = Theme.panel(new BorderLayout(0, 6));
        top.add(Theme.label("Start from a preset, then change any string"), BorderLayout.NORTH);
        top.add(preset, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        root.add(rows, BorderLayout.CENTER);

        var bottom = Theme.panel(new BorderLayout(0, 10));
        summary.setForeground(Theme.TEXT);
        summary.setFont(new Font("SansSerif", Font.BOLD, 13));
        bottom.add(summary, BorderLayout.NORTH);
        var done = Theme.button("Done");
        done.setBackground(Theme.ACCENT);
        done.setForeground(Theme.BACKGROUND);
        done.addActionListener(e -> setVisible(false));
        bottom.add(done, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        preset.addActionListener(e -> {
            if (syncing) return;
            int index = preset.getSelectedIndex();
            if (index >= 0 && index < Tuning.PRESETS.size()) state.setTuning(Tuning.PRESETS.get(index));
        });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        sync(state.get());
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    void open() {
        sync(state.get());
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private void sync(TunerSettings settings) {
        syncing = true;
        var tuning = settings.tuning();
        int index = Tuning.PRESETS.indexOf(tuning);
        preset.setSelectedIndex(index >= 0 ? index : Tuning.PRESETS.size());
        if (notes.size() != tuning.strings()) rebuildRows(tuning.strings());
        for (int i = 0; i < tuning.strings(); i++) notes.get(i).setSelectedIndex(tuning.note(i) - Tuning.LOWEST);
        summary.setText(tuning.name() + "  ·  " + tuning.letters());
        syncing = false;
        pack();
    }

    private void rebuildRows(int strings) {
        rows.removeAll();
        notes.clear();
        for (int i = 0; i < strings; i++) {
            int string = i;
            var combo = new JComboBox<String>();
            for (int midi = Tuning.LOWEST; midi <= Tuning.HIGHEST; midi++) combo.addItem(Pitch.name(midi));
            Theme.styleInput(combo);
            combo.setName("note." + i);
            combo.addActionListener(e -> { if (!syncing) state.setNote(string, Tuning.LOWEST + combo.getSelectedIndex()); });
            notes.add(combo);
            var row = Theme.panel(new BorderLayout(12, 0));
            var label = Theme.label(Tuning.ordinal(strings - i) + " string");
            label.setPreferredSize(new Dimension(84, 26));
            row.add(label, BorderLayout.WEST);
            row.add(combo, BorderLayout.CENTER);
            rows.add(row);
        }
        rows.setPreferredSize(new Dimension(300, strings * 32));
        rows.revalidate();
    }
}
