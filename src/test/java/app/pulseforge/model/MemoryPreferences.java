package app.pulseforge.model;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/** Isolates tests and UI previews from the user's saved settings. */
public final class MemoryPreferences extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();
    public MemoryPreferences() { super(null, ""); }
    protected void putSpi(String key, String value) { values.put(key, value); }
    protected String getSpi(String key) { return values.get(key); }
    protected void removeSpi(String key) { values.remove(key); }
    protected void removeNodeSpi() { values.clear(); }
    protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
    protected String[] childrenNamesSpi() { return new String[0]; }
    protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
    protected void syncSpi() {}
    protected void flushSpi() {}
}
