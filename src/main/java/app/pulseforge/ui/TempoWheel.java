package app.pulseforge.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.function.DoubleConsumer;

final class TempoWheel extends JComponent {
    private double bpm;
    private DoubleConsumer changeListener = value -> {};
    private int dragY;
    private double dragBpm;

    TempoWheel(double bpm) {
        this.bpm = bpm;
        setPreferredSize(new Dimension(240, 215));
        setMinimumSize(getPreferredSize());
        setFocusable(true);
        setToolTipText("Drag vertically or scroll to change tempo");
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow(); dragY = event.getY(); dragBpm = TempoWheel.this.bpm;
            }
            @Override public void mouseDragged(MouseEvent event) {
                setFromUser(dragBpm + (dragY - event.getY()) * .35);
            }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                setFromUser(TempoWheel.this.bpm - event.getPreciseWheelRotation());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "down");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "down");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "up");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "up");
        getActionMap().put("down", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { setFromUser(TempoWheel.this.bpm - 1); }});
        getActionMap().put("up", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { setFromUser(TempoWheel.this.bpm + 1); }});
    }

    void setChangeListener(DoubleConsumer listener) { changeListener = listener; }
    void setBpm(double value) { bpm = Math.max(20, Math.min(300, value)); repaint(); }

    private void setFromUser(double value) {
        setBpm(Math.round(Math.max(20, Math.min(300, value)) * 10) / 10.0);
        changeListener.accept(bpm);
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.min(getWidth() - 30, getHeight() - 8);
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        double progress = (bpm - 20) / 280.0;

        g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.PANEL_LIGHT);
        g.draw(new Arc2D.Double(x + 8, y + 8, size - 16, size - 16, 225, -270, Arc2D.OPEN));
        g.setColor(Theme.ACCENT);
        g.draw(new Arc2D.Double(x + 8, y + 8, size - 16, size - 16, 225, -270 * progress, Arc2D.OPEN));

        double angle = Math.toRadians(225 - 270 * progress);
        double radius = (size - 16) / 2.0;
        double cx = x + size / 2.0, cy = y + size / 2.0;
        g.setColor(Theme.TEXT);
        g.fill(new Ellipse2D.Double(cx + Math.cos(angle) * radius - 4,
                cy - Math.sin(angle) * radius - 4, 8, 8));

        g.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 28; i++) {
            double tickAngle = Math.toRadians(225 - 270 * i / 28.0);
            double r1 = radius - 18, r2 = radius - (i % 4 == 0 ? 10 : 13);
            g.setColor(i / 28.0 <= progress ? Theme.ACCENT : Theme.BORDER);
            g.draw(new Line2D.Double(cx + Math.cos(tickAngle) * r1, cy - Math.sin(tickAngle) * r1,
                    cx + Math.cos(tickAngle) * r2, cy - Math.sin(tickAngle) * r2));
        }

        String value = bpm == Math.rint(bpm) ? Integer.toString((int) bpm) : String.format("%.1f", bpm);
        g.setColor(Theme.TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 50));
        drawCentered(g, value, cy - 4);
        g.setColor(Theme.MUTED);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        drawCentered(g, "BPM  ·  " + tempoName(bpm).toUpperCase(), cy + 22);
        g.dispose();
    }

    private void drawCentered(Graphics2D g, String text, double baseline) {
        var bounds = g.getFontMetrics().getStringBounds(text, g);
        g.drawString(text, (float) ((getWidth() - bounds.getWidth()) / 2), (float) baseline);
    }

    private static String tempoName(double bpm) {
        if (bpm < 40) return "Grave";
        if (bpm < 60) return "Largo";
        if (bpm < 76) return "Adagio";
        if (bpm < 108) return "Andante";
        if (bpm < 120) return "Moderato";
        if (bpm < 168) return "Allegro";
        if (bpm < 200) return "Presto";
        return "Prestissimo";
    }
}
