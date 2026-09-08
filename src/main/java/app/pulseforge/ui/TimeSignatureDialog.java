package app.pulseforge.ui;

import app.pulseforge.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

final class TimeSignatureDialog extends JDialog {
    private final MetronomeState state;
    private final JSpinner beats;
    private final JComboBox<Integer> note = new JComboBox<>(MetronomeSettings.NOTE_VALUES.toArray(Integer[]::new));
    private final JPanel beatButtons = Theme.panel(new GridLayout(0, 4, 6, 6));
    private final JPanel rhythmButtons = Theme.panel(new GridLayout(2, 3, 7, 7));
    private final JPanel hitButtons = Theme.panel(new GridLayout(1, 0, 5, 0));
    private final Map<Subdivision, JButton> rhythms = new EnumMap<>(Subdivision.class);
    private final JLabel editing = Theme.label("");
    private final JLabel unitHint = Theme.label("");
    private final JCheckBox accent = new JCheckBox("Accent this beat");
    private final JCheckBox mute = new JCheckBox("Mute this beat");
    private final JSlider swing = new JSlider(50, 75);
    private final JLabel swingValue = Theme.label("");
    private int selectedBeat;
    private boolean syncing;

    TimeSignatureDialog(JFrame owner, MetronomeState state) {
        super(owner, "Time Signature", false);
        this.state = state;
        beats = new JSpinner(new SpinnerNumberModel(state.get().beatsPerBar(), 1, 12, 1));
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        var root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        setContentPane(root);

        var meter = Theme.panel(new GridLayout(1, 2, 12, 0));
        Theme.styleInput(beats);
        ((JSpinner.DefaultEditor) beats.getEditor()).getTextField().setColumns(3);
        ((JSpinner.DefaultEditor) beats.getEditor()).getTextField().setBackground(Theme.PANEL_LIGHT);
        ((JSpinner.DefaultEditor) beats.getEditor()).getTextField().setForeground(Theme.TEXT);
        Theme.styleInput(note);
        meter.add(field("Beats per bar", beats));
        meter.add(field("Note value", note));
        var top = Theme.panel(new BorderLayout(0, 10));
        top.add(meter, BorderLayout.NORTH);
        top.add(unitHint, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        var editor = Theme.panel(new BorderLayout(0, 14));
        var beatSection = Theme.panel(new BorderLayout(0, 7));
        beatSection.add(Theme.label("Choose a beat to edit"), BorderLayout.NORTH);
        beatSection.add(beatButtons, BorderLayout.CENTER);
        editor.add(beatSection, BorderLayout.NORTH);

        var detail = Theme.panel(new BorderLayout(0, 8));
        editing.setForeground(Theme.TEXT);
        editing.setFont(new Font("SansSerif", Font.BOLD, 14));
        detail.add(editing, BorderLayout.NORTH);
        for (var division : Subdivision.values()) {
            var button = Theme.button("");
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setFont(new Font("SansSerif", Font.PLAIN, 11));
            button.setBorder(new EmptyBorder(4, 4, 6, 4));
            button.setName("rhythm." + division.name());
            button.addActionListener(e -> state.setPattern(selectedBeat, division));
            rhythms.put(division, button);
            rhythmButtons.add(button);
        }
        rhythmButtons.setPreferredSize(new Dimension(414, 166));
        detail.add(rhythmButtons, BorderLayout.CENTER);

        var hits = Theme.panel(new BorderLayout(0, 8));
        hits.add(Theme.label("Click each note to turn its sound on or off"), BorderLayout.NORTH);
        hitButtons.setPreferredSize(new Dimension(414, 48));
        hits.add(hitButtons, BorderLayout.CENTER);
        var accentRow = Theme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (var check : new JCheckBox[]{accent, mute}) {
            check.setOpaque(false);
            check.setForeground(Theme.TEXT);
            check.setFont(new Font("SansSerif", Font.PLAIN, 12));
            accentRow.add(check);
        }
        hits.add(accentRow, BorderLayout.SOUTH);
        detail.add(hits, BorderLayout.SOUTH);
        editor.add(detail, BorderLayout.CENTER);
        root.add(editor, BorderLayout.CENTER);

        var bottom = Theme.panel(new BorderLayout(0, 12));
        var swingRow = Theme.panel(new BorderLayout(8, 0));
        swing.setOpaque(false);
        swing.setPreferredSize(new Dimension(130, 24));
        swingRow.add(Theme.label("Swing (paired notes)"), BorderLayout.WEST);
        swingRow.add(swing, BorderLayout.CENTER);
        swingRow.add(swingValue, BorderLayout.EAST);
        bottom.add(swingRow, BorderLayout.NORTH);
        var done = Theme.button("Done");
        done.setBackground(Theme.ACCENT);
        done.setForeground(Theme.BACKGROUND);
        done.addActionListener(e -> setVisible(false));
        bottom.add(done, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        beats.addChangeListener(e -> { if (!syncing) state.setMeter((int) beats.getValue(), (int) note.getSelectedItem()); });
        note.addActionListener(e -> { if (!syncing) state.setMeter((int) beats.getValue(), (int) note.getSelectedItem()); });
        accent.addActionListener(e -> {
            if (!syncing) state.setAccent(selectedBeat, accent.isSelected() ? Accent.STRONG : Accent.NORMAL);
        });
        mute.addActionListener(e -> {
            if (!syncing) state.setAccent(selectedBeat, mute.isSelected() ? Accent.MUTED : Accent.NORMAL);
        });
        swing.addChangeListener(e -> { if (!syncing) state.setSwing(swing.getValue() / 100.0); });
        state.addListener(settings -> SwingUtilities.invokeLater(() -> sync(settings)));
        sync(state.get());
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    void open(int beat) {
        selectedBeat = beat;
        sync(state.get());
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private void sync(MetronomeSettings settings) {
        syncing = true;
        selectedBeat = Math.clamp(selectedBeat, 0, settings.beatsPerBar() - 1);
        beats.setValue(settings.beatsPerBar());
        note.setSelectedItem(settings.beatUnit());
        unitHint.setText("Each beat is a " + MetronomeSettings.noteName(settings.beatUnit()).toLowerCase()
                + ". Tempo counts these beats.");
        beatButtons.removeAll();
        for (int i = 0; i < settings.beatsPerBar(); i++) {
            int index = i;
            var pattern = settings.patterns().get(i);
            String summary = pattern.division().label(settings.beatUnit());
            if (summary.length() > 12) summary = pattern.steps() + (pattern.steps() == 1 ? " note" : " notes");
            var button = Theme.button("<html><center>Beat " + (i + 1) + "<br><span style='font-size:9px'>"
                    + summary + "</span></center></html>");
            button.setName("beat." + i);
            button.setBorder(new EmptyBorder(6, 2, 6, 2));
            boolean selected = i == selectedBeat;
            button.setBackground(selected ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
            button.setForeground(selected ? Theme.BACKGROUND : Theme.TEXT);
            button.addActionListener(e -> { selectedBeat = index; sync(state.get()); });
            beatButtons.add(button);
        }
        int rows = (settings.beatsPerBar() + 3) / 4;
        beatButtons.setPreferredSize(new Dimension(414, rows * 43 + (rows - 1) * 6));
        editing.setText("Beat " + (selectedBeat + 1) + " · Note pattern");
        var selected = settings.patterns().get(selectedBeat);
        rhythms.forEach((division, button) -> {
            button.setIcon(new NoteIcon(division.steps(), settings.beatUnit(), 96));
            button.setText(division.label(settings.beatUnit()));
            button.setToolTipText(division.steps() + (division.steps() == 1 ? " note" : " notes") + " in this beat");
            boolean chosen = division == selected.division();
            button.setBackground(chosen ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
            button.setForeground(chosen ? Theme.BACKGROUND : Theme.TEXT);
            button.getAccessibleContext().setAccessibleDescription(chosen ? "Selected" : "Not selected");
        });
        hitButtons.removeAll();
        for (int i = 0; i < selected.steps(); i++) {
            int hit = i;
            boolean enabled = selected.hits().get(i);
            String count = selected.division().count(selectedBeat, i);
            var button = Theme.button("<html><center>" + count
                    + "<br><span style='font-size:9px'>" + (enabled ? "Sound" : "Rest") + "</span></center></html>");
            button.setName("hit." + i);
            button.setBorder(new EmptyBorder(4, 1, 4, 1));
            button.setBackground(enabled ? Theme.ACCENT : Theme.PANEL_LIGHT);
            button.setForeground(enabled ? Theme.BACKGROUND : Theme.MUTED);
            button.addActionListener(e -> state.toggleHit(selectedBeat, hit));
            button.setEnabled(settings.accents().get(selectedBeat) != Accent.MUTED);
            hitButtons.add(button);
        }
        accent.setSelected(settings.accents().get(selectedBeat) == Accent.STRONG);
        mute.setSelected(settings.accents().get(selectedBeat) == Accent.MUTED);
        accent.setEnabled(!mute.isSelected());
        swing.setValue((int) Math.round(settings.swing() * 100));
        swingValue.setText(swing.getValue() == 50 ? "Straight" : swing.getValue() + "%");
        syncing = false;
        getContentPane().setPreferredSize(new Dimension(468, 610 + (rows - 1) * 49));
        pack();
        revalidate();
        repaint();
    }

    private static JPanel field(String label, JComponent input) {
        var panel = Theme.panel(new BorderLayout(0, 6));
        panel.add(Theme.label(label), BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }
}
