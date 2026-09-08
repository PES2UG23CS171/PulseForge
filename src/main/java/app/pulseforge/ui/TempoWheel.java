package app.pulseforge.ui;

import app.pulseforge.audio.TransportState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.function.DoubleConsumer;

final class TempoWheel extends JComponent {
    private int bpm;
    private DoubleConsumer changeListener = value -> {};
    private Runnable transportAction = () -> {};
    private TransportState transportState = TransportState.STOPPED;
    private int dragY;
    private double dragBpm;
    private double scrollRemainder;
    private boolean draggingDial;

    TempoWheel(double bpm) {
        setBpm(bpm);
        setPreferredSize(new Dimension(218, 200));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setFocusable(true);
        setToolTipText("Drag the outer wheel or scroll to change tempo; click the center to play or pause");
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                draggingDial = !insideTransport(event.getX(), event.getY());
                dragY = event.getY();
                dragBpm = TempoWheel.this.bpm;
                scrollRemainder = 0;
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (!draggingDial && insideTransport(event.getX(), event.getY())) transportAction.run();
                draggingDial = false;
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (draggingDial) setFromUser(dragBpm + (dragY - event.getY()) * .35);
            }

            @Override public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(insideTransport(event.getX(), event.getY())
                        ? Cursor.HAND_CURSOR : Cursor.N_RESIZE_CURSOR));
            }

            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                scrollRemainder -= event.getPreciseWheelRotation();
                int steps = (int) scrollRemainder;
                scrollRemainder -= steps;
                if (steps != 0) setFromUser(TempoWheel.this.bpm + steps);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "down");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "down");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "up");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "up");
        getActionMap().put("down", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { setFromUser(TempoWheel.this.bpm - 1); }
        });
        getActionMap().put("up", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { setFromUser(TempoWheel.this.bpm + 1); }
        });
    }

    void setChangeListener(DoubleConsumer listener) { changeListener = listener; }
    void setTransportAction(Runnable action) { transportAction = action; }
    void setTransportState(TransportState value) { transportState = value; repaint(); }
    void setBpm(double value) { bpm = (int) Math.round(Math.clamp(value, 20, 300)); repaint(); }

    private void setFromUser(double value) {
        int previous = bpm;
        setBpm(value);
        if (bpm != previous) changeListener.accept(bpm);
    }

    private boolean insideTransport(int mouseX, int mouseY) {
        double dx = mouseX - getWidth() / 2.0;
        double dy = mouseY - getHeight() / 2.0;
        return dx * dx + dy * dy <= 49 * 49;
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.min(getWidth() - 18, getHeight() - 8);
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;
        double radius = size / 2.0 - 8;

        g.setColor(Theme.PANEL);
        g.fill(new Ellipse2D.Double(x + 5, y + 5, size - 10, size - 10));
        g.setStroke(new BasicStroke(4));
        g.setColor(Theme.ACCENT_2);
        g.draw(new Ellipse2D.Double(x + 8, y + 8, size - 16, size - 16));

        for (int i = 0; i < 56; i++) {
            double angle = 2 * Math.PI * i / 56.0;
            double inner = radius - (i % 4 == 0 ? 13 : 9);
            g.setStroke(new BasicStroke(i % 4 == 0 ? 2f : 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(i % 4 == 0 ? Theme.MUTED : Theme.BORDER);
            g.draw(new Line2D.Double(cx + Math.sin(angle) * inner, cy - Math.cos(angle) * inner,
                    cx + Math.sin(angle) * radius, cy - Math.cos(angle) * radius));
        }

        double progress = (bpm - 20) / 280.0;
        double pointerAngle = Math.toRadians(-135 + progress * 270);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.ACCENT);
        g.draw(new Line2D.Double(cx + Math.sin(pointerAngle) * (radius - 28),
                cy - Math.cos(pointerAngle) * (radius - 28),
                cx + Math.sin(pointerAngle) * (radius - 13),
                cy - Math.cos(pointerAngle) * (radius - 13)));

        double buttonSize = 96;
        g.setColor(transportState == TransportState.PLAYING ? Theme.WARNING : Theme.PANEL_LIGHT);
        g.fill(new Ellipse2D.Double(cx - buttonSize / 2, cy - buttonSize / 2, buttonSize, buttonSize));
        g.setStroke(new BasicStroke(2));
        g.setColor(transportState == TransportState.PLAYING ? Theme.WARNING.brighter() : Theme.BORDER);
        g.draw(new Ellipse2D.Double(cx - buttonSize / 2, cy - buttonSize / 2, buttonSize, buttonSize));

        g.setColor(transportState == TransportState.PLAYING ? new Color(45, 32, 8) : Theme.ACCENT);
        if (transportState == TransportState.PLAYING) {
            g.fillRoundRect((int) cx - 14, (int) cy - 18, 10, 30, 3, 3);
            g.fillRoundRect((int) cx + 5, (int) cy - 18, 10, 30, 3, 3);
        } else {
            var triangle = new Path2D.Double();
            triangle.moveTo(cx - 10, cy - 21);
            triangle.lineTo(cx + 23, cy - 3);
            triangle.lineTo(cx - 10, cy + 15);
            triangle.closePath();
            g.fill(triangle);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        String action = transportState == TransportState.PLAYING ? "PAUSE"
                : transportState == TransportState.PAUSED ? "RESUME" : "PLAY";
        var bounds = g.getFontMetrics().getStringBounds(action, g);
        g.drawString(action, (float) (cx - bounds.getWidth() / 2), (float) cy + 31);
        g.dispose();
    }
}
