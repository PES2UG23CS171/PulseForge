package app.pulseforge.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class Theme {
    static final Color BACKGROUND = new Color(18, 21, 27);
    static final Color PANEL = new Color(27, 32, 40);
    static final Color PANEL_LIGHT = new Color(37, 44, 54);
    static final Color TEXT = new Color(237, 242, 247);
    static final Color MUTED = new Color(159, 169, 181);
    static final Color ACCENT = new Color(111, 221, 202);
    static final Color ACCENT_2 = new Color(124, 180, 229);
    static final Color WARNING = new Color(239, 193, 114);
    static final Color BORDER = new Color(65, 76, 91);

    private Theme() {}

    static JLabel label(String text) {
        var label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return label;
    }

    static JButton button(String text) {
        var button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getBackground();
                if (getModel().isPressed()) fill = fill.brighter();
                else if (getModel().isRollover()) fill = new Color(
                        Math.min(255, fill.getRed() + 7), Math.min(255, fill.getGreen() + 7), Math.min(255, fill.getBlue() + 7));
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (hasFocus()) {
                    g.setColor(ACCENT_2);
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(TEXT);
        button.setBackground(PANEL_LIGHT);
        button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 13, 8, 13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static void styleInput(JComponent input) {
        input.setForeground(TEXT);
        input.setBackground(PANEL_LIGHT);
        input.setFont(new Font("SansSerif", Font.PLAIN, 13));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(4, 6, 4, 6)));
    }

    static JPanel panel(LayoutManager layout) {
        var panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
}
