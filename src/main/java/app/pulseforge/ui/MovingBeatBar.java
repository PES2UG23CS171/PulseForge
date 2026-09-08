package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.IntConsumer;

final class MovingBeatBar extends JComponent {
    private final MetronomeState state;
    private final AccurateAudioEngine engine;
    private IntConsumer beatClick = beat -> {};

    MovingBeatBar(MetronomeState state, AccurateAudioEngine engine) {
        this.state = state;
        this.engine = engine;
        setToolTipText("Click a beat to edit its notes");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (getWidth() > 0) beatClick.accept(Math.clamp(e.getX() * state.get().beatsPerBar() / getWidth(),
                        0, state.get().beatsPerBar() - 1));
            }
        });
    }
    void setBeatClick(IntConsumer action) { beatClick = action; }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var settings = state.get();
        var position = engine.position();
        int beat = Math.min(position.beat(), settings.beatsPerBar() - 1);
        double width = (double) getWidth() / settings.beatsPerBar();
        for (int i = 0; i < settings.beatsPerBar(); i++) {
            int x = (int) (i * width);
            int cellWidth = Math.max(4, (int) width - 3);
            boolean active = engine.isPlaying() && beat == i;
            g.setColor(active ? Theme.ACCENT_2 : Theme.PANEL_LIGHT);
            g.fillRoundRect(x, 3, cellWidth, 38, 7, 7);
            g.setColor(active ? Theme.BACKGROUND : Theme.TEXT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String number = Integer.toString(i + 1);
            g.drawString(number, x + cellWidth / 2 - g.getFontMetrics().stringWidth(number) / 2, 17);
            var pattern = settings.patterns().get(i);
            for (int j = 0; j < pattern.steps(); j++) {
                int dotX = x + (int) ((j + .5) * cellWidth / pattern.steps());
                g.setColor(pattern.hits().get(j) && settings.accents().get(i) != Accent.MUTED
                        ? active ? Theme.BACKGROUND : Theme.ACCENT : Theme.BORDER);
                g.fillOval(dotX - 2, 27, 4, 4);
            }
        }
        int y = 47;
        g.setColor(Theme.PANEL_LIGHT);
        g.fillRoundRect(0, y, getWidth(), 9, 9, 9);
        double progress = (beat + position.phase()) / settings.beatsPerBar();
        int playhead = (int) (progress * (getWidth() - 4));
        g.setColor(engine.isPlaying() ? Theme.ACCENT : Theme.MUTED);
        g.fillRoundRect(playhead, y - 2, 4, 13, 4, 4);
        g.dispose();
    }
}

