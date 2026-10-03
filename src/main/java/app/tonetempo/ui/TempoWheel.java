package app.tonetempo.ui;

import app.tonetempo.audio.TransportState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.function.DoubleConsumer;

/** Tempo knob: the tick ring turns half a tick per BPM, easing towards the current tempo. */
final class TempoWheel extends JComponent {
    private static final int TICKS = 56;
    private static final double RADIANS_PER_BPM = 2 * Math.PI / TICKS / 2;
    private int bpm;
    private DoubleConsumer changeListener = value -> {};
    private Runnable transportAction = () -> {};
    private TransportState transportState = TransportState.STOPPED;
    private int dragY;
    private double dragBpm;
    private double scrollRemainder;
    private boolean draggingDial;
    private double targetAngle;
    private double dialAngle;
    private final Timer spin = new Timer(16, e -> animate());

    TempoWheel(double bpm) {
        setBpm(bpm);
        dialAngle = targetAngle;
        spin.stop();
        setPreferredSize(new Dimension(218, 200));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setFocusable(true);
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
    void setBpm(double value) {
        bpm = (int) Math.round(Math.clamp(value, 20, 300));
        targetAngle = bpm * RADIANS_PER_BPM;
        if (!spin.isRunning()) spin.start();
        repaint();
    }

    private void animate() {
        double remaining = targetAngle - dialAngle;
        if (Math.abs(remaining) < 5e-4) {
            dialAngle = targetAngle;
            spin.stop();
        } else {
            dialAngle += remaining * .3;
        }
        repaint();
    }

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

        var face = new Ellipse2D.Double(x + 5, y + 5, size - 10, size - 10);
        g.setColor(Theme.PANEL);
        g.fill(face);
        // Fixed lighting on the face, so the turning ticks read as a knob rotating under a lamp.
        g.setPaint(new RadialGradientPaint((float) (cx - size * .22), (float) (cy - size * .28), (float) (size * .75),
                new float[] {0f, 1f}, new Color[] {new Color(255, 255, 255, 22), new Color(255, 255, 255, 0)}));
        g.fill(face);
        g.setStroke(new BasicStroke(4));
        g.setColor(Theme.ACCENT_2);
        g.draw(new Ellipse2D.Double(x + 8, y + 8, size - 16, size - 16));

        for (int i = 0; i < TICKS; i++) {
            double angle = dialAngle + 2 * Math.PI * i / TICKS;
            boolean major = i % 4 == 0;
            double inner = radius - (major ? 13 : 9);
            g.setStroke(new BasicStroke(major ? 2f : 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(major ? Theme.MUTED : Theme.BORDER);
            g.draw(new Line2D.Double(cx + Math.sin(angle) * inner, cy - Math.cos(angle) * inner,
                    cx + Math.sin(angle) * radius, cy - Math.cos(angle) * radius));
        }


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
