package de.lundlucenany9.velochat;

/**
 * Helpers for selecting configured or fallback message templates.
 */
public final class MessagesUtil {
    private MessagesUtil() {
    }

    public static String template(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
