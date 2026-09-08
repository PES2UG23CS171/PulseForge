package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.model.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Manual macOS UI check: run with an output directory; uses no real user preferences. */
public final class UiVerification {
    private static MainWindow window;
    private static MetronomeState state;
    private static AccurateAudioEngine engine;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception error) { throw new RuntimeException(error); }
                state = new MetronomeState(new MemoryPreferences());
                engine = new AccurateAudioEngine(state);
                window = new MainWindow(state, engine);
                window.setVisible(true);
                find(window, "timeSignature").doClick();
            });
            SwingUtilities.invokeAndWait(() -> {
                var dialog = signatureDialog();
                find(dialog, "rhythm.TRIPLET").doClick();
            });
            flush();
            SwingUtilities.invokeAndWait(() -> find(signatureDialog(), "beat.2").doClick());
            SwingUtilities.invokeAndWait(() -> find(signatureDialog(), "rhythm.SIXTEENTH").doClick());
            flush();
            if (state.get().patterns().get(0).steps() != 3 || state.get().patterns().get(2).steps() != 4)
                throw new AssertionError("Per-beat rhythm selection failed");
            SwingUtilities.invokeAndWait(() -> {
                snapshot(window.getContentPane(), output.resolve("main.png"));
                snapshot(signatureDialog().getContentPane(), output.resolve("time-signature.png"));
                find(signatureDialog(), "hit.1").doClick();
            });
            flush();
            if (state.get().patterns().get(2).hits().get(1)) throw new AssertionError("Sound toggle failed");
            SwingUtilities.invokeAndWait(() -> {
                state.setMeter(12, 32);
            });
            flush();
            SwingUtilities.invokeAndWait(() -> {
                snapshot(signatureDialog().getContentPane(), output.resolve("twelve-beats.png"));
                signatureDialog().setVisible(false);
                find(window, "soundSettings").doClick();
            });
            flush();
            SwingUtilities.invokeAndWait(() -> {
                for (Window owned : window.getOwnedWindows())
                    if (owned instanceof SettingsDialog dialog) snapshot(dialog.getContentPane(), output.resolve("sound.png"));
            });
            System.out.println("UI verified: Time Signature opens; independent rhythms and hit toggles work; all views rendered.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (window != null) window.dispose(); });
        }
        System.exit(0);
    }

    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
    private static TimeSignatureDialog signatureDialog() {
        for (Window owned : window.getOwnedWindows()) if (owned instanceof TimeSignatureDialog dialog) return dialog;
        throw new AssertionError("Time Signature dialog missing");
    }
    private static AbstractButton find(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button && name.equals(button.getName())) return button;
            if (component instanceof Container nested) {
                try { return find(nested, name); } catch (IllegalArgumentException ignored) {}
            }
        }
        throw new IllegalArgumentException(name);
    }
    private static void snapshot(Container root, Path path) {
        var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.scale(2, 2);
        root.printAll(graphics);
        graphics.dispose();
        try { ImageIO.write(image, "png", path.toFile()); } catch (Exception error) { throw new RuntimeException(error); }
    }
}
