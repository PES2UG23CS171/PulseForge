package app.tonetempo.audio;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Name of the macOS default input device. Java Sound's "Default Audio Device" mixer only ever exposes one
 * channel, so multi-input interfaces chosen as the system default must be opened by their own name.
 */
final class MacDefaultInput {
    private static final long CACHE_NANOS = 10_000_000_000L;
    private static final Pattern ITEM = Pattern.compile("\"_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static String cached = "";
    private static long cachedAt;

    private MacDefaultInput() {}

    /** Device name, or an empty string when it cannot be determined. */
    static synchronized String name() {
        long now = System.nanoTime();
        if (cachedAt != 0 && now - cachedAt < CACHE_NANOS) return cached;
        cached = lookup();
        cachedAt = now;
        return cached;
    }

    private static String lookup() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return "";
        try {
            var process = new ProcessBuilder("/usr/sbin/system_profiler", "SPAudioDataType", "-json")
                    .redirectErrorStream(true).start();
            String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroy(); return ""; }
            return parse(json);
        } catch (Exception error) {
            return "";
        }
    }

    /** Each device is a flat object whose keys start with "_name"; the default input carries a "spaudio_yes" flag. */
    static String parse(String json) {
        var matcher = ITEM.matcher(json);
        int previousEnd = -1;
        String previousName = "";
        while (matcher.find()) {
            if (previousEnd >= 0 && isDefaultInput(json.substring(previousEnd, matcher.start()))) return previousName;
            previousName = matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
            previousEnd = matcher.end();
        }
        return previousEnd >= 0 && isDefaultInput(json.substring(previousEnd)) ? previousName : "";
    }

    private static boolean isDefaultInput(String item) {
        return item.matches("(?s).*\"coreaudio_default_audio_input_device\"\\s*:\\s*\"spaudio_yes\".*");
    }
}
