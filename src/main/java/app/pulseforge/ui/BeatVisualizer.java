package app.pulseforge.ui;

import app.pulseforge.audio.BeatPulse;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

final class BeatVisualizer extends JComponent {
    private int beats = 4;
    private int activeBeat = -1;
    private int subdivision;
    private boolean playing;
    private long pulseNanos;
    private long beatPeriodNanos = 500_000_000L;

    BeatVisualizer() {
        setPreferredSize(new Dimension(380, 78));
        var timer = new Timer(16, event -> { if (playing) repaint(); });
        timer.start();
    }

    void setBeatCount(int beats) { this.beats = beats; repaint(); }
    void setPlaying(boolean playing) { this.playing = playing; if (!playing) activeBeat = -1; repaint(); }
    void setBeatPeriod(double bpm) { beatPeriodNanos = (long) (60_000_000_000.0 / bpm); }
    void pulse(BeatPulse pulse) {
        activeBeat = pulse.beatIndex(); subdivision = pulse.subdivisionIndex(); pulseNanos = pulse.nanoTime(); repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), center = w / 2;
        double elapsed = Math.max(0, System.nanoTime() - pulseNanos);
        double phase = Math.min(1, elapsed / (double) beatPeriodNanos);
        double direction = activeBeat % 2 == 0 ? -1 : 1;
        double angle = playing ? direction * (.72 - phase * 1.44) : 0;
        int pivotY = 12, length = 35;
        int ballX = center + (int) (Math.sin(angle) * length);
        int ballY = pivotY + (int) (Math.cos(angle) * length);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(playing ? Theme.ACCENT_2 : Theme.BORDER);
        g.draw(new Line2D.Double(center, pivotY, ballX, ballY));
        g.fill(new Ellipse2D.Double(ballX - 5, ballY - 5, 10, 10));

        int maxWidth = Math.min(330, w - 24);
        int gap = 7;
        int dot = Math.max(8, Math.min(16, (maxWidth - (beats - 1) * gap) / beats));
        int total = beats * dot + (beats - 1) * gap;
        int start = (w - total) / 2;
        for (int i = 0; i < beats; i++) {
            boolean active = playing && i == activeBeat;
            int d = active && subdivision == 0 ? dot + 4 : dot;
            int dx = start + i * (dot + gap) + (dot - d) / 2;
            g.setColor(active ? (i == 0 ? Theme.WARNING : Theme.ACCENT) : Theme.BORDER);
            g.fill(new Ellipse2D.Double(dx, 61 - d / 2.0, d, d));
        }
        g.dispose();
    }
}
