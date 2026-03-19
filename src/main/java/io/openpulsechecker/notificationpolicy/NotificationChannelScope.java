package io.openpulsechecker.notificationpolicy;

import java.util.EnumSet;
import java.util.Set;

/**
 * Minimal release scope lock: only EMAIL channel is active.
 *
 * Keep enum values and notifier implementations intact so future multi-channel
 * rollout can extend this policy without large refactors.
 */
public final class NotificationChannelScope {

    private static final EnumSet<NotificationChannel> ACTIVE_CHANNELS = EnumSet.of(NotificationChannel.EMAIL);

    private NotificationChannelScope() {
    }

    public static EnumSet<NotificationChannel> activeChannels() {
        return EnumSet.copyOf(ACTIVE_CHANNELS);
    }

    public static EnumSet<NotificationChannel> filterToActive(Set<NotificationChannel> channels) {
        EnumSet<NotificationChannel> filtered = EnumSet.noneOf(NotificationChannel.class);
        if (channels != null) {
            filtered.addAll(channels);
            filtered.retainAll(ACTIVE_CHANNELS);
        }
        return filtered;
    }

    public static void assertOnlyActive(Set<NotificationChannel> channels, String fieldName) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain at least one channel");
        }
        EnumSet<NotificationChannel> unsupported = EnumSet.copyOf(channels);
        unsupported.removeAll(ACTIVE_CHANNELS);
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported channels for minimal release: " + unsupported);
        }
    }
}
