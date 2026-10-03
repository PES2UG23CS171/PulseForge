package app.tonetempo;

import app.tonetempo.audio.AccurateAudioEngine;
import app.tonetempo.audio.TunerEngine;
import app.tonetempo.model.MetronomeState;
import app.tonetempo.model.TunerState;
import app.tonetempo.ui.MainWindow;
import app.tonetempo.ui.AppIcon;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** T&T Pro (Tone & Tempo Pro): metronome and guitar tuner. */
public final class ToneTempoApplication {
    public static final String NAME = "T&T Pro";
    /** Preferences node of the app's previous name, PulseForge. */
    private static final String LEGACY_PREFERENCES = "/app/pulseforge/model";

    private ToneTempoApplication() {}

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", NAME);
        migrateLegacyPreferences();
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

    /** Carries saved settings over from the PulseForge days, once, when the new node is still empty. */
    static void migrateLegacyPreferences() {
        try {
            var root = Preferences.userRoot();
            if (!root.nodeExists(LEGACY_PREFERENCES)) return;
            var legacy = root.node(LEGACY_PREFERENCES);
            var current = Preferences.userNodeForPackage(MetronomeState.class);
            if (current.keys().length > 0) return;
            for (String key : legacy.keys()) current.put(key, legacy.get(key, ""));
            current.flush();
        } catch (BackingStoreException | SecurityException ignored) {
            // Settings simply start from defaults.
        }
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
        @Bean TunerState tunerState() { return new TunerState(); }
        @Bean TunerEngine tunerEngine(TunerState state) { return new TunerEngine(state); }
        @Bean MainWindow mainWindow(MetronomeState state, AccurateAudioEngine engine,
                                    TunerState tunerState, TunerEngine tunerEngine) {
            return new MainWindow(state, engine, tunerState, tunerEngine);
        }
    }
}
