package io.github.abhishekkaisar.glassos.personal;

import java.util.Locale;

/** Plain-Java rules for the Home Assistant lamp button, kept free of Android so the JVM checks can run them. */
public final class HaLogic {
    private HaLogic() {}

    /** Domains that answer homeassistant toggle with a simple on/off state. */
    private static final String[] DOMAINS = {"light", "switch", "input_boolean", "fan"};
    /** Words that put lamps first in the picker (Portuguese and English). */
    private static final String[] LAMP_WORDS = {"candeeiro", "lâmpada", "lampada", "luz", "lamp", "light"};

    /** "192.168.1.5:8123/" or "http://ha.local:8123/api" become "http://…:8123"; blank stays blank. */
    public static String normalizeUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/api")) s = s.substring(0, s.length() - 4);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String domain(String entityId) {
        if (entityId == null) return "";
        int dot = entityId.indexOf('.');
        return dot > 0 ? entityId.substring(0, dot) : "";
    }

    public static boolean isToggleable(String entityId) {
        String d = domain(entityId);
        if (d.isEmpty() || entityId.length() <= d.length() + 1) return false;
        for (String x : DOMAINS) if (x.equals(d)) return true;
        return false;
    }

    /** TRUE for "on", FALSE for "off", null for unavailable, unknown or anything else. */
    public static Boolean isOn(String state) {
        if ("on".equals(state)) return Boolean.TRUE;
        if ("off".equals(state)) return Boolean.FALSE;
        return null;
    }

    /** Picker order: things that look like lamps first, then lights, switches, helpers and fans. */
    public static int rank(String entityId, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int lamp = 10;
        for (String w : LAMP_WORDS) if (n.contains(w)) { lamp = 0; break; }
        String d = domain(entityId);
        for (int i = 0; i < DOMAINS.length; i++) if (DOMAINS[i].equals(d)) return lamp + i;
        return lamp + DOMAINS.length;
    }
}
