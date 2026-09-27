package app.pulseforge.ui;

import app.pulseforge.audio.AccurateAudioEngine;
import app.pulseforge.audio.SyntheticGuitar;
import app.pulseforge.audio.TunerEngine;
import app.pulseforge.model.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Manual macOS UI check: run with an output directory; uses no real user preferences or microphone. */
public final class UiVerification {
    private static MainWindow window;
    private static MetronomeState state;
    private static AccurateAudioEngine engine;
    private static TunerEngine tunerEngine;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        double flatLowE = Pitch.frequency(40, 440) * Math.pow(2, -9 / 1200.0);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception error) { throw new RuntimeException(error); }
                state = new MetronomeState(new MemoryPreferences());
                engine = new AccurateAudioEngine(state);
                var tunerState = new TunerState(new MemoryPreferences());
                tunerEngine = new TunerEngine(tunerState, (name, channel) ->
                        new SyntheticGuitar.Source(48_000, SyntheticGuitar.Voice.wound(flatLowE)));
                window = new MainWindow(state, engine, tunerState, tunerEngine);
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
            SwingUtilities.invokeAndWait(() -> state.setMeter(12, 32));
            flush();
            SwingUtilities.invokeAndWait(() -> {
                snapshot(signatureDialog().getContentPane(), output.resolve("twelve-beats.png"));
                signatureDialog().setVisible(false);
                find(window, "settings").doClick();
            });
            flush();
            SwingUtilities.invokeAndWait(() -> {
                for (Window owned : window.getOwnedWindows())
                    if (owned instanceof SettingsDialog dialog) {
                        snapshot(dialog.getContentPane(), output.resolve("settings.png"));
                        dialog.setVisible(false);
                    }
                find(window, "mode.tuner").doClick();
            });
            flush();
            if (window.mode() != ModeToggle.Mode.TUNER || !tunerEngine.isRunning())
                throw new AssertionError("Tuner mode did not start listening: " + tunerEngine.errorMessage());
            long deadline = System.nanoTime() + 6_000_000_000L;
            while (System.nanoTime() < deadline
                    && !(window.tunerPanel().isLive() && window.tunerPanel().target() == 0)) Thread.sleep(50);
            Thread.sleep(400);
            flush();
            var tuner = window.tunerPanel();
            if (!tuner.isLive() || tuner.target() != 0)
                throw new AssertionError("Tuner did not identify the low E string: " + tunerEngine.errorMessage());
            if (Math.abs(tuner.cents() + 9) > 1)
                throw new AssertionError("Tuner read " + tuner.cents() + " cents instead of about -9");
            SwingUtilities.invokeAndWait(() -> snapshot(window.getContentPane(), output.resolve("tuner.png")));
            SwingUtilities.invokeAndWait(() -> find(window, "string.3").doClick());
            flush();
            if (tuner.isAuto() || tuner.target() != 3) throw new AssertionError("Manual string selection failed");
            SwingUtilities.invokeAndWait(() -> find(window, "autoString").doClick());
            flush();
            if (!tuner.isAuto()) throw new AssertionError("Auto detection did not resume");
            SwingUtilities.invokeAndWait(() -> find(window, "mode.metronome").doClick());
            flush();
            if (tunerEngine.isRunning()) throw new AssertionError("Tuner kept the microphone after switching back");
            System.out.println("UI verified: Time Signature opens; independent rhythms and hit toggles work; "
                    + "the tuner hears a flat low E, allows manual string choice, and releases the microphone; all views rendered.");
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
