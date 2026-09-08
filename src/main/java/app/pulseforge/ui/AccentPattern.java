package app.pulseforge.ui;

import app.pulseforge.model.Accent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.IntConsumer;

final class AccentPattern extends JComponent {
    private List<Accent> accents = List.of();
    private IntConsumer clickListener = beat -> {};

    AccentPattern() {
        setPreferredSize(new Dimension(205, 34));
        setToolTipText("Click beats to cycle strong, normal, and muted");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                int beat = beatAt(event.getX());
                if (beat >= 0) clickListener.accept(beat);
            }
        });
    }

    void setAccents(List<Accent> accents) { this.accents = accents; repaint(); }
    void setClickListener(IntConsumer listener) { clickListener = listener; }

    private int beatAt(int x) {
        if (accents.isEmpty()) return -1;
        int total = accents.size() * 24;
        int start = (getWidth() - total) / 2;
        int index = (x - start) / 24;
        return x >= start && index >= 0 && index < accents.size() ? index : -1;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int total = accents.size() * 24;
        int start = (getWidth() - total) / 2;
        for (int i = 0; i < accents.size(); i++) {
            var accent = accents.get(i);
            int size = accent == Accent.STRONG ? 15 : 11;
            int x = start + i * 24 + (24 - size) / 2;
            int y = (getHeight() - size) / 2;
            g.setColor(accent == Accent.STRONG ? Theme.WARNING : accent == Accent.NORMAL ? Theme.ACCENT : Theme.BORDER);
            if (accent == Accent.MUTED) {
                g.setStroke(new BasicStroke(2));
                g.draw(new Ellipse2D.Double(x, y, size, size));
                g.drawLine(x + 2, y + 2, x + size - 2, y + size - 2);
            } else g.fill(new Ellipse2D.Double(x, y, size, size));
        }
        g.dispose();
    }
}
