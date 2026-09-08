package app.pulseforge.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class Theme {
    static final Color BACKGROUND = new Color(12, 15, 21);
    static final Color PANEL = new Color(22, 27, 36);
    static final Color PANEL_LIGHT = new Color(31, 37, 48);
    static final Color TEXT = new Color(242, 245, 249);
    static final Color MUTED = new Color(139, 149, 165);
    static final Color ACCENT = new Color(77, 224, 186);
    static final Color ACCENT_2 = new Color(91, 141, 239);
    static final Color WARNING = new Color(255, 181, 72);
    static final Color BORDER = new Color(49, 57, 72);

    private Theme() {}

    static JLabel label(String text) {
        var label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        return label;
    }

    static JButton button(String text) {
        var button = new JButton(text);
        button.setForeground(TEXT);
        button.setBackground(PANEL_LIGHT);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 13, 8, 13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static void styleInput(JComponent input) {
        input.setForeground(TEXT);
        input.setBackground(PANEL_LIGHT);
        input.setFont(new Font("SansSerif", Font.PLAIN, 12));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(5, 8, 5, 8)));
    }
}
