package io.openpulsechecker.service;

import java.net.URI;
import java.util.regex.Pattern;

final class PingTargetParser {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern HOST_LABEL_PATTERN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");

    private PingTargetParser() {
    }

    static String validateForConfiguration(String rawTarget) {
        String target = normalize(rawTarget);
        if (target.contains("://")) {
            throw new IllegalArgumentException("PING target must be a hostname or IP address without URL scheme (e.g. example.com or 1.1.1.1).");
        }
        if (containsUnsupportedDelimiters(target)) {
            throw new IllegalArgumentException("PING target must be a hostname or IP address without path, query, fragment, or port.");
        }
        if (target.contains(":")) {
            throw new IllegalArgumentException("PING target must not include a port. Use hostname/IP only.");
        }
        if (!isValidHostname(target) && !isValidIpv4(target)) {
            throw new IllegalArgumentException("PING target must be a valid hostname or IPv4 address.");
        }
        return target;
    }

    static String resolveHostForExecution(String rawTarget) {
        String target = normalize(rawTarget);
        if (target.contains("://")) {
            URI uri = URI.create(target);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("PING target URL must include a host.");
            }
            return host;
        }
        return validateForConfiguration(target);
    }

    private static String normalize(String rawTarget) {
        if (rawTarget == null) {
            throw new IllegalArgumentException("PING target is required.");
        }
        String target = rawTarget.trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("PING target is required.");
        }
        if (target.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("PING target must not contain whitespace.");
        }
        return target;
    }

    private static boolean containsUnsupportedDelimiters(String target) {
        return target.contains("/") || target.contains("?") || target.contains("#");
    }

    private static boolean isValidHostname(String target) {
        if (target.length() > 253 || target.startsWith(".") || target.endsWith(".")) {
            return false;
        }

        String[] labels = target.split("\\.");
        if (labels.length == 0) {
            return false;
        }

        for (String label : labels) {
            if (!HOST_LABEL_PATTERN.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIpv4(String target) {
        if (!IPV4_PATTERN.matcher(target).matches()) {
            return false;
        }

        String[] octets = target.split("\\.");
        for (String octet : octets) {
            int value = Integer.parseInt(octet);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }
}
