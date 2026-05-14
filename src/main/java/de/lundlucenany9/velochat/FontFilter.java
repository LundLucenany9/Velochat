package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects and blocks alternate unicode fonts based on config.
 */
public final class FontFilter {
    private static final Set<Character.UnicodeBlock> allowedBlocks = new HashSet<>();
    static {
        allowedBlocks.add(Character.UnicodeBlock.BASIC_LATIN);
        allowedBlocks.add(Character.UnicodeBlock.LATIN_1_SUPPLEMENT);
        allowedBlocks.add(Character.UnicodeBlock.GENERAL_PUNCTUATION);
        allowedBlocks.add(Character.UnicodeBlock.CURRENCY_SYMBOLS);
    }

    private FontFilter() {}

    public static boolean shouldBlock(Player player, String message, Config config) {
        if (message == null || message.isBlank() || config == null) {
            return false;
        }
        if (!config.isBanAlternateFonts()) {
            return false;
        }
        String bypassPermission = config.getBanAlternateFontsPermission();
        if (player != null && bypassPermission != null && !bypassPermission.isBlank()
                && player.hasPermission(bypassPermission)) {
            return false;
        }
        return containsAlternateFonts(message);
    }

    static boolean containsAlternateFonts(String message) {
        int length = message.length();
        for (int i = 0; i < length; ) {
            int codePoint = message.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isAlternateFontCodePoint(codePoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlternateFontCodePoint(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return !allowedBlocks.contains(block);
    }
}
