package com.campx.logger.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise PII (Personally Identifiable Information) and sensitive credential masking utility.
 * <p>
 * Sanitizes sensitive telemetry data before writing to logs, disk archives, or external aggregators:
 * <ul>
 *   <li>HTTP Bearer and OAuth authorization tokens</li>
 *   <li>Raw user passwords and password configuration hashes</li>
 *   <li>API keys, secrets, and shared signing tokens</li>
 *   <li>16-digit credit/debit card PANs (while preserving 13-digit epoch millisecond timestamps)</li>
 *   <li>12-digit national identification numbers (e.g. Aadhaar), masking leading 8 digits</li>
 * </ul>
 */
public final class SecurityMasker {

    private static final List<PatternRule> RULES = new ArrayList<>();

    static {
        // 1. Bearer tokens: Bearer <token>
        RULES.add(new PatternRule(
                Pattern.compile("(?i)(Bearer\\s+)[a-zA-Z0-9_\\-\\.]+"),
                "$1******"
        ));

        // 2. Password fields: password=..., "password": "..."
        RULES.add(new PatternRule(
                Pattern.compile("(?i)(\"?password\"?\\s*[:=]\\s*\"?)([^\"&,\\s]+)(\"?)"),
                "$1******$3"
        ));

        // 3. Secret / ApiKey
        RULES.add(new PatternRule(
                Pattern.compile("(?i)(\"?(?:secret|apiKey|api_key)\"?\\s*[:=]\\s*\"?)([^\"&,\\s]+)(\"?)"),
                "$1******$3"
        ));

        // 4. Token / Authorization (non-Bearer)
        RULES.add(new PatternRule(
                Pattern.compile("(?i)(\"?(?:authorization|token)\"?\\s*[:=]\\s*\"?)(?!Bearer\\s+)([^\"&,\\s]+)(\"?)"),
                "$1******$3"
        ));

        // 5. 16-digit credit/debit cards: 1234-5678-9012-3456 or 16 continuous digits (prevents masking 13-digit epoch timestamps)
        RULES.add(new PatternRule(
                Pattern.compile("\\b(?:\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}|\\d{16})\\b"),
                "****-****-****-****"
        ));

        // 6. 12-digit Aadhaar / National ID: 1234 5678 9012 or 1234-5678-9012
        RULES.add(new PatternRule(
                Pattern.compile("\\b(\\d{4})[ -](\\d{4})[ -](\\d{4})\\b"),
                "XXXX-XXXX-$3"
        ));
    }

    private SecurityMasker() {}

    /**
     * Sanitizes sensitive data patterns in the given message string using predefined regex filters.
     *
     * @param input the raw string message
     * @return sanitized string with credentials and sensitive identifiers redacted
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;
        for (PatternRule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(rule.replacement);
            }
        }
        return result;
    }

    /**
     * Internal rule container holding a compiled regular expression and replacement template.
     */
    private static class PatternRule {
        final Pattern pattern;
        final String replacement;

        PatternRule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
