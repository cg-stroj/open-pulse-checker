package io.openpulsechecker.alerting;

import java.util.regex.Pattern;

final class SecretSanitizer {

    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern TOKEN_QUERY = Pattern.compile("(?i)(token=)[^&\\s]+");
    private static final Pattern BOT_TOKEN = Pattern.compile("(?i)(bot)[0-9]{6,}:[A-Za-z0-9_-]{20,}");

    private SecretSanitizer() {
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        String sanitized = BEARER.matcher(value).replaceAll("Bearer ***REDACTED***");
        sanitized = TOKEN_QUERY.matcher(sanitized).replaceAll("$1***REDACTED***");
        sanitized = BOT_TOKEN.matcher(sanitized).replaceAll("bot***REDACTED***");
        if (sanitized.length() > 512) {
            return sanitized.substring(0, 512);
        }
        return sanitized;
    }
}
