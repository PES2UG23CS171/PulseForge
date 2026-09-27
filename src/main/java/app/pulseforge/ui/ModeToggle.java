package app.pulseforge.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Segmented Metronome / Tuner switch. */
final class ModeToggle extends JPanel {
    enum Mode {
        METRONOME("Metronome"), TUNER("Tuner");
        final String label;
        Mode(String label) { this.label = label; }
    }

    private final Map<Mode, JButton> segments = new EnumMap<>(Mode.class);
    private Mode mode = Mode.METRONOME;
    private Consumer<Mode> listener = value -> {};

    ModeToggle() {
        super(new GridLayout(1, 2, 2, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(2, 2, 2, 2));
        for (var value : Mode.values()) {
            var segment = Theme.button(value.label);
            segment.setName("mode." + value.name().toLowerCase());
            segment.setFont(new Font("SansSerif", Font.PLAIN, 11));
            segment.setBorder(new EmptyBorder(4, 11, 4, 11));
            segment.setFocusable(false);
            segment.setToolTipText(value == Mode.TUNER ? "Tune your guitar with the microphone" : "Metronome");
            segment.addActionListener(e -> select(value, true));
            segments.put(value, segment);
            add(segment);
        }
        paintSelection();
    }

    Mode mode() { return mode; }
    void setListener(Consumer<Mode> listener) { this.listener = listener; }
    void setMode(Mode value) { select(value, false); }

    private void select(Mode value, boolean notify) {
        boolean changed = value != mode;
        mode = value;
        paintSelection();
        if (notify && changed) listener.accept(value);
    }

    private void paintSelection() {
        segments.forEach((value, segment) -> {
            boolean selected = value == mode;
            segment.setBackground(selected ? Theme.ACCENT_2 : Theme.PANEL);
            segment.setForeground(selected ? Theme.BACKGROUND : Theme.MUTED);
        });
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.PANEL);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g.dispose();
        super.paintComponent(graphics);
    }
}
