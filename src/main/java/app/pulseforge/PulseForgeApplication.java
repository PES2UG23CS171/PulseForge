package app.pulseforge;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.MetronomeState;
import app.pulseforge.ui.MainWindow;
import app.pulseforge.ui.AppIcon;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.swing.*;
import java.awt.*;

public final class PulseForgeApplication {
    private PulseForgeApplication() {}

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "PulseForge");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        if (Taskbar.isTaskbarSupported()) {
            try { Taskbar.getTaskbar().setIconImage(AppIcon.create(512)); } catch (Exception ignored) {}
        }
        SwingUtilities.invokeLater(() -> {
            configureLookAndFeel();
            var context = new AnnotationConfigApplicationContext(AppConfig.class);
            var window = context.getBean(MainWindow.class);
            window.setIconImage(AppIcon.create(256));
            window.setVisible(true);
            window.toFront();
        });
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The custom-painted controls remain usable with the cross-platform LAF.
        }
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Button.arc", 12);
    }

    @Configuration
    static class AppConfig {
        @Bean MetronomeState metronomeState() { return new MetronomeState(); }
        @Bean AccurateAudioEngine audioEngine(MetronomeState state) { return new AccurateAudioEngine(state); }
        @Bean MainWindow mainWindow(MetronomeState state, AccurateAudioEngine engine) {
            return new MainWindow(state, engine);
        }
    }
}
