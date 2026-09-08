package app.pulseforge.ui;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/** Small vector notation; independent of installed music fonts. */
final class NoteIcon implements Icon {
    private final int count;
    private final int unit;
    private final int width;
    private final boolean tuplet;
    NoteIcon(int count, int unit, int width) {
        this.count = count;
        this.unit = unit;
        this.width = width;
        this.tuplet = count == 3 || count == 6;
    }
    public int getIconWidth() { return width; }
    public int getIconHeight() { return 40; }

    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c.getForeground());
        g.setStroke(new BasicStroke(1.6f));
        int noteUnit = unit * (tuplet ? (count == 3 ? 2 : 4) : count);
        int beams = noteUnit < 8 ? 0 : Math.min(5, Integer.numberOfTrailingZeros(noteUnit) - 2);
        int span = Math.min(width - 18, Math.max(16, (count - 1) * 17));
        int start = (width - span) / 2;
        int firstStem = start + 8;
        int lastStem = firstStem;
        for (int i = 0; i < count; i++) {
            int nx = count == 1 ? width / 2 - 5 : start + i * span / (count - 1);
            var head = new Ellipse2D.Double(nx, 29, 9, 6);
            if (noteUnit <= 2 && !tuplet) g.draw(head); else g.fill(head);
            if (noteUnit != 1 || tuplet) g.drawLine(nx + 8, 31, nx + 8, 12);
            if (i == 0) firstStem = nx + 8;
            lastStem = nx + 8;
        }
        if (beams > 0) {
            for (int b = 0; b < beams; b++) {
                int by = 12 + b * 4;
                if (count == 1) g.drawArc(firstStem - 4, by, 13, 12, -30, 130);
                else g.fillRect(firstStem, by, Math.max(1, lastStem - firstStem), 3);
            }
        }
        if (tuplet) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String number = Integer.toString(count);
            g.drawString(number, width / 2 - 3, 9);
            g.drawLine(firstStem, 5, width / 2 - 7, 5);
            g.drawLine(width / 2 + 7, 5, lastStem, 5);
        }
        g.dispose();
    }
}

