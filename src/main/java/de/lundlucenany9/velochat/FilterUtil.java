package de.lundlucenany9.velochat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies word filter rules against player messages.
 */
public final class FilterUtil {
    private FilterUtil() {
    }

    public static FilterResult applyFilter(String message, Config config) {
        if (message == null || !config.isFilterEnabled()) {
            return FilterResult.allow(message);
        }
        List<String> words = config.getFilterWords();
        if (words.isEmpty()) {
            return FilterResult.allow(message);
        }

        boolean leet = config.isFilterLeetspeak();
        String result = message;
        boolean matched = false;
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            Pattern pattern = buildPattern(word, leet);
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                matched = true;
                if ("block".equalsIgnoreCase(config.getFilterMode())) {
                    return FilterResult.block();
                }
                String censorChar = config.getFilterChar();
                if (censorChar == null || censorChar.isEmpty()) {
                    censorChar = "*";
                }
                result = censorMatches(result, pattern, censorChar);
            }
        }
        return matched ? FilterResult.censored(result) : FilterResult.allow(message);
    }

    private static String censorMatches(String input, Pattern pattern, String censorChar) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            int length = matcher.end() - matcher.start();
            String replacement = censorChar.repeat(Math.max(1, length));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static Pattern buildPattern(String word, boolean leetspeak) {
        String normalized = word.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = new ArrayList<>();
        for (char ch : normalized.toCharArray()) {
            tokens.add(charPattern(ch, leetspeak));
        }
        StringBuilder pattern = new StringBuilder();
        for (String token : tokens) {
            pattern.append(token);
        }
        return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static String charPattern(char ch, boolean leetspeak) {
        if (!leetspeak) {
            return Pattern.quote(String.valueOf(ch));
        }
        return switch (ch) {
            case 'a' -> "[a4@]";
            case 'e' -> "[e3]";
            case 'i' -> "[i1!|]";
            case 'o' -> "[o0]";
            case 's' -> "[s5$]";
            case 't' -> "[t7]";
            default -> Pattern.quote(String.valueOf(ch));
        };
    }

    /**
     * Result of evaluating the filter for a single message.
     */
    public record FilterResult(boolean blocked, boolean modified, String message) {
        public static FilterResult allow(String message) {
            return new FilterResult(false, false, message);
        }

        public static FilterResult censored(String message) {
            return new FilterResult(false, true, message);
        }

        public static FilterResult block() {
            return new FilterResult(true, false, null);
        }
    }
}
