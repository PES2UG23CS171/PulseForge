package app.pulseforge.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Target note, a ±50 cent scale with needle, the measured frequency and guidance. */
final class TunerMeter extends JComponent {
    static final double RANGE = 50;

    record View(String letter, String octave, String detail, boolean hasSignal, boolean inTune, double cents,
                double frequency, String hint, Color hintColor) {}

    private View view = new View("—", "", "", false, false, 0, 0, "Play a string", Theme.MUTED);

    TunerMeter() {
        setPreferredSize(new Dimension(328, 236));
        setMinimumSize(getPreferredSize());
    }

    void setView(View next) {
        view = next;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(Theme.PANEL);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 18, 18));

        var noteFont = new Font("SansSerif", Font.BOLD, 60);
        var octaveFont = new Font("SansSerif", Font.BOLD, 22);
        g.setFont(noteFont);
        int letterWidth = g.getFontMetrics().stringWidth(view.letter());
        int octaveWidth = view.octave().isEmpty() ? 0 : g.getFontMetrics(octaveFont).stringWidth(view.octave()) + 5;
        int noteX = (w - letterWidth - octaveWidth) / 2;
        int noteY = (int) (h * .30);
        int axisY = (int) (h * .59);
        int readoutY = (int) (h * .86);
        g.setColor(view.inTune() ? Theme.ACCENT : view.hasSignal() ? Theme.TEXT : Theme.MUTED);
        g.drawString(view.letter(), noteX, noteY);
        if (!view.octave().isEmpty()) {
            g.setFont(octaveFont);
            g.drawString(view.octave(), noteX + letterWidth + 5, noteY);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(Theme.MUTED);
        centered(g, view.detail(), w / 2, noteY + 21);

        int left = 30;
        int right = w - 30;
        int center = (left + right) / 2;
        double half = (right - left) / 2.0;
        g.setColor(Theme.PANEL_LIGHT);
        g.fillRoundRect(left - 8, axisY - 3, right - left + 16, 6, 6, 6);
        int zone = (int) Math.round(2 / RANGE * half);
        g.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 60));
        g.fillRoundRect(center - zone, axisY - 16, zone * 2, 32, 4, 4);
        for (int c = -50; c <= 50; c += 5) {
            int tx = center + (int) Math.round(c / RANGE * half);
            int length = c % 25 == 0 ? 12 : c % 10 == 0 ? 8 : 4;
            g.setColor(c == 0 ? Theme.TEXT : c % 25 == 0 ? Theme.MUTED : Theme.BORDER);
            g.setStroke(new BasicStroke(c == 0 ? 2f : 1f));
            g.drawLine(tx, axisY + 6, tx, axisY + 6 + length);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(Theme.MUTED);
        for (int c = -50; c <= 50; c += 25)
            centered(g, (c > 0 ? "+" : "") + c, center + (int) Math.round(c / RANGE * half), axisY + 32);
        if (view.hasSignal()) {
            int nx = center + (int) Math.round(Math.clamp(view.cents(), -RANGE, RANGE) / RANGE * half);
            g.setColor(view.inTune() ? Theme.ACCENT : Theme.WARNING);
            g.fillRoundRect(nx - 3, axisY - 26, 6, 36, 6, 6);
        }

        if (view.hasSignal()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(Theme.TEXT);
            g.drawString(String.format("%.2f Hz", view.frequency()), left, readoutY);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(view.inTune() ? Theme.ACCENT : Theme.WARNING);
            String cents = String.format("%+.1f¢", view.cents());
            g.drawString(cents, right - g.getFontMetrics().stringWidth(cents), readoutY);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(view.hintColor());
        centered(g, view.hint(), w / 2, readoutY);
        g.dispose();
    }

    private static void centered(Graphics2D g, String text, int cx, int baseline) {
        if (text == null || text.isEmpty()) return;
        g.drawString(text, cx - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }
}
